package com.shredcoach.app.domain.coach

import android.util.Log
import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.dao.ScheduledWorkoutDao
import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.domain.streak.StreakService
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Moteur d'analyse de l'état utilisateur qui produit le meilleur [CoachTrigger]
 * pour la fenêtre courante.
 *
 * Pipeline (ordre **strict** car les filtres en aval dépendent des résultats amont) :
 * 1. **Évaluation** des règles → liste de triggers candidats (peut être vide)
 * 2. **Mute filter** → retire les catégories utilisateur-mutées
 * 3. **Cooldown filter** → retire les catégories émises trop récemment
 *    (cf. [CoachHistoryStore], cooldown défini par chaque trigger)
 * 4. **Weekly cap** → si le quota hebdo est atteint, retour vide
 * 5. **Skip-if-only-general** → si après filtres il ne reste que
 *    [CoachTrigger.GeneralMotivation], retour vide. Évite la notif "vide"
 *    quotidienne qui dégrade la valeur perçue (anti-pattern Apple/Whoop).
 * 6. **Tri** par score décroissant
 *
 * Le caller (Worker) prend `firstOrNull()` du résultat. Aucun trigger →
 * silent day, conforme aux standards FAANG.
 */
@Singleton
class CoachTriggerEngine @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val workoutLogDao: WorkoutLogDao,
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val nutritionDao: NutritionDao,
    private val exerciseDao: ExerciseDao,
    private val historyStore: CoachHistoryStore,
    private val settingsStore: CoachSettingsStore,
    private val streakService: StreakService,
) {
    /**
     * @return liste filtrée et triée. Vide = ne pas envoyer de notif aujourd'hui.
     */
    suspend fun evaluate(today: LocalDate = LocalDate.now()): List<CoachTrigger> {
        val profile = userProfileDao.getUserProfileOnce() ?: run {
            Log.i(TAG, "evaluate: skip (no profile)")
            return emptyList()
        }
        val recentLogs = workoutLogDao.getRecentWorkoutLogs(60).first()
        val settings = settingsStore.snapshot.first()
        // Snapshot historique LU UNE FOIS — économise N round-trips DataStore
        // dans le filter cooldown (avant : 1 read par trigger, soit ~10).
        val historySnap = historyStore.snapshot.first()
        val now = Instant.now()

        // Étape 0 : weekly cap atteint → silence radio
        // Le cap NE compte QUE les catégories du coach proactif. Le store
        // [CoachHistoryStore] est partagé avec d'autres notifs (meal_debrief)
        // qui ne doivent pas peser sur le quota coach.
        val emissionsThisWeek = historySnap.entries.count { (cat, ts) ->
            cat in CoachHistoryStore.COACH_CATEGORIES &&
                ts.isAfter(now.minus(Duration.ofDays(7)))
        }
        if (emissionsThisWeek >= settings.weeklyCap) {
            Log.i(TAG, "evaluate: skip (weekly cap reached: $emissionsThisWeek/${settings.weeklyCap})")
            return emptyList()
        }

        // Étape 1 : évaluation
        val raw = buildList {
            evaluateStreakAtRisk(profile, recentLogs, today)?.let { add(it) }
            evaluateMissedScheduledWorkout(today)?.let { add(it) }
            evaluatePersonalRecord(today)?.let { add(it) }
            evaluateProteinDeficit(profile, today)?.let { add(it) }
            evaluatePlateauVolume(recentLogs, today)?.let { add(it) }
            evaluateComeback(recentLogs, today)?.let { add(it) }
            evaluateBodyScanStale(profile, today)?.let { add(it) }
            evaluateGoalProximityETA(profile, today)?.let { add(it) }
            // GeneralMotivation toujours en dernier — fallback. Sa présence
            // ne suffit PAS à déclencher une notif (cf. étape 5).
            add(buildGeneralMotivation(profile, recentLogs, today))
        }

        // Étape 2 : mute filter
        val notMuted = raw.filter { it.category !in settings.mutedCategories }

        // Étape 3 : cooldown filter (lookup O(1) sur la map en mémoire)
        val notOnCooldown = notMuted.filter { trigger ->
            val last = historySnap[trigger.category] ?: return@filter true
            Duration.between(last, now) >= trigger.cooldown
        }

        // Étape 5 : skip-if-only-general
        // Note : étape 4 (weekly cap) gérée en étape 0 (gating amont)
        val withoutLoneGeneral = if (notOnCooldown.singleOrNull()?.category == "motivation_general") {
            emptyList()
        } else notOnCooldown

        // Étape 6 : tri
        val result = withoutLoneGeneral.sortedByDescending { it.score }
        Log.i(
            TAG,
            "evaluate: raw=${raw.size} muted=${notMuted.size} cooldown=${notOnCooldown.size} " +
                "winner=${result.firstOrNull()?.category ?: "none"} (cap=$emissionsThisWeek/${settings.weeklyCap})"
        )
        return result
    }

    // ───────────────────────── Évaluateurs ─────────────────────────

    private fun evaluateStreakAtRisk(
        profile: UserProfileEntity,
        recentLogs: List<WorkoutLogEntity>,
        today: LocalDate,
    ): CoachTrigger.StreakAtRisk? {
        val completedLogs = recentLogs.filter { it.completed }
        if (completedLogs.isEmpty()) return null

        // Streak via le service unique (cohérent avec HomeViewModel,
        // StreakUpdateWorker, WorkoutDebriefWorker — pas de divergence).
        val streak = streakService.compute(completedLogs, today).currentDays
        if (streak < 2) return null

        val lastWorkout = completedLogs.maxOf { it.date.toLocalDate() }
        val daysSince = ChronoUnit.DAYS.between(lastWorkout, today).toInt()
        val plannedDays = profile.workoutDays.size.coerceAtLeast(1)
        val maxGap = (7.0 / plannedDays).toInt() + 1
        if (daysSince < maxGap) return null

        return CoachTrigger.StreakAtRisk(
            streakDays = streak,
            daysSinceLastWorkout = daysSince,
            plannedWorkoutDays = plannedDays,
        )
    }

    private suspend fun evaluateMissedScheduledWorkout(today: LocalDate): CoachTrigger.MissedScheduledWorkout? {
        val from = today.minusDays(7)
        val recent = scheduledWorkoutDao.getBetweenOnce(from, today.minusDays(1))
        val missed = recent.filter { it.status == "PLANNED" }.maxByOrNull { it.date } ?: return null
        val daysSince = ChronoUnit.DAYS.between(missed.date, today).toInt()
        return CoachTrigger.MissedScheduledWorkout(
            workoutName = missed.title.ifBlank { "Séance" },
            daysSinceMissed = daysSince,
            scheduledId = missed.id,
        )
    }

    private suspend fun evaluatePersonalRecord(today: LocalDate): CoachTrigger.PersonalRecordCelebration? {
        val yesterday = today.minusDays(1)
        val allLogs = workoutLogDao.getAllWorkoutLogsOnce()
        val yesterdayLogIds = allLogs
            .filter { it.date.toLocalDate() == yesterday && it.completed }
            .map { it.id }
        if (yesterdayLogIds.isEmpty()) return null

        val allSets = workoutLogDao.getAllWorkoutSetsOnce().filter { it.completed && it.weightKg > 0 }
        val yesterdaySets = allSets.filter { it.workoutLogId in yesterdayLogIds }
        if (yesterdaySets.isEmpty()) return null

        val exerciseIds = yesterdaySets.map { it.exerciseId }.distinct()
        var bestPr: Triple<Long, Double, Double>? = null
        for (exId in exerciseIds) {
            val prevMax = allSets.filter { it.exerciseId == exId && it.workoutLogId !in yesterdayLogIds }
                .maxOfOrNull { it.weightKg } ?: 0.0
            val newMax = yesterdaySets.filter { it.exerciseId == exId }.maxOf { it.weightKg }
            // Filtre +1.25kg pour exclure le bruit (changements imperceptibles).
            if (newMax > prevMax && newMax - prevMax >= 1.25) {
                if (bestPr == null || (newMax - prevMax) > (bestPr.second - bestPr.third)) {
                    bestPr = Triple(exId, newMax, prevMax)
                }
            }
        }
        val pr = bestPr ?: return null
        val exerciseName = exerciseDao.getExerciseById(pr.first)?.name ?: return null

        // Récupère le logId du PR pour deeplink.
        val workoutLogId = yesterdayLogIds.first()
        return CoachTrigger.PersonalRecordCelebration(
            exerciseName = exerciseName,
            newWeightKg = pr.second,
            previousWeightKg = pr.third,
            workoutLogId = workoutLogId,
        )
    }

    private suspend fun evaluateProteinDeficit(
        profile: UserProfileEntity,
        today: LocalDate,
    ): CoachTrigger.ProteinDeficit? {
        if (profile.goal != com.shredcoach.app.data.local.entity.FitnessGoal.SHRED) return null
        val goal = nutritionDao.getNutritionGoalOnce() ?: return null
        if (goal.targetProteins <= 0) return null
        val yesterday = today.minusDays(1)
        val totals = nutritionDao.getDayTotals(yesterday)
        val consumed = totals.totalProteins.toInt()
        if (consumed >= goal.targetProteins * 0.7) return null
        if (consumed == 0) return null  // Pas tracké, pas de conclusion possible

        return CoachTrigger.ProteinDeficit(
            gramsConsumed = consumed,
            goalGrams = goal.targetProteins,
        )
    }

    /**
     * Plateau volume : on regarde 4 fenêtres de 7 jours (sem -1, -2, -3, -4).
     * Si chaque fenêtre récente est <= 105% de la précédente → plateau.
     */
    private fun evaluatePlateauVolume(
        recentLogs: List<WorkoutLogEntity>,
        today: LocalDate,
    ): CoachTrigger.PlateauVolume? {
        val completed = recentLogs.filter { it.completed }
        if (completed.size < 6) return null  // Pas assez d'historique → silence

        // Volume par semaine glissante depuis aujourd'hui
        val weeklyVolumes = (0..3).map { weekIdx ->
            val end = today.minusDays(weekIdx * 7L)
            val start = end.minusDays(6)
            completed.filter {
                val d = it.date.toLocalDate()
                !d.isBefore(start) && !d.isAfter(end)
            }.sumOf { it.totalVolume }
        }
        // weeklyVolumes[0] = cette semaine, [1] = -1 sem, [2] = -2 sem, [3] = -3 sem
        // Plateau = chaque vol récente <= 1.05 * vol précédente sur les 3 transitions.
        val isFlat = (0..2).all { i ->
            val newer = weeklyVolumes[i]
            val older = weeklyVolumes[i + 1]
            older > 0 && newer <= older * 1.05
        }
        if (!isFlat) return null
        if (weeklyVolumes[0] <= 0) return null  // Aucune séance cette semaine = comeback, pas plateau

        return CoachTrigger.PlateauVolume(
            weeksFlat = 3,
            recentWeeklyVolume = weeklyVolumes[0].toInt(),
        )
    }

    /**
     * Comeback : aucune séance depuis 7+ jours mais historique non vide.
     * **Mutex implicite** avec StreakAtRisk : si streak actif, ce trigger ne
     * matche pas (daysSince serait < 7).
     */
    private fun evaluateComeback(
        recentLogs: List<WorkoutLogEntity>,
        today: LocalDate,
    ): CoachTrigger.Comeback? {
        val completed = recentLogs.filter { it.completed }
        if (completed.isEmpty()) return null
        val lastDate = completed.maxOf { it.date.toLocalDate() }
        val daysSince = ChronoUnit.DAYS.between(lastDate, today).toInt()
        if (daysSince < 7) return null
        return CoachTrigger.Comeback(
            daysAway = daysSince,
            totalWorkoutsBefore = completed.size,
        )
    }

    private fun evaluateBodyScanStale(
        profile: UserProfileEntity,
        today: LocalDate,
    ): CoachTrigger.BodyScanStale? {
        // **Pourquoi pas de fallback "999 si jamais scanné"** :
        // BodyScanStale signifie "ta dernière mesure est ancienne, refais-en une".
        // Si l'user n'a JAMAIS scanné, ce trigger émet une notif mensongère
        // ("Dernière mesure il y a 999 jours") qui décrédibilise complètement
        // le coach. La feature BodyScanner se découvre via l'UI Home/nav, pas
        // via un nag passif-agressif. → Si jamais scanné, on ne déclenche rien.
        val lastScan = profile.bodyScanTimestamp ?: return null
        val daysSince = ChronoUnit.DAYS.between(lastScan.toLocalDate(), today).toInt()
        if (daysSince < 30) return null
        return CoachTrigger.BodyScanStale(daysSince = daysSince)
    }

    /**
     * Goal proximity ETA : nécessite au moins 4 weight_logs sur les 28 derniers jours
     * pour calculer une pente fiable (régression linéaire simple).
     */
    private suspend fun evaluateGoalProximityETA(
        profile: UserProfileEntity,
        today: LocalDate,
    ): CoachTrigger.GoalProximityETA? {
        if (profile.targetWeightKg <= 0) return null
        val cutoff = today.minusDays(28)
        val logs = userProfileDao.getWeightLogsSince(cutoff)
        if (logs.size < 4) return null

        // Pente moyenne kg/semaine via régression simple
        val weeklyDelta = computeWeeklyDelta(logs) ?: return null
        if (weeklyDelta.absoluteValue < 0.05) return null  // Trop plat → pas d'ETA crédible

        val gapKg = profile.currentWeightKg - profile.targetWeightKg
        // En sèche on perd (delta < 0), gap > 0 → progress dans le bon sens.
        // En bulk on prend (delta > 0), gap < 0 → idem.
        val sameDirection = (gapKg > 0 && weeklyDelta < 0) || (gapKg < 0 && weeklyDelta > 0)
        if (!sameDirection) return null  // Tendance inverse → on n'annonce pas un ETA

        val etaWeeks = (gapKg.absoluteValue / weeklyDelta.absoluteValue).toInt().coerceIn(1, 52)
        return CoachTrigger.GoalProximityETA(
            currentWeightKg = profile.currentWeightKg,
            targetWeightKg = profile.targetWeightKg,
            weeklyDeltaKg = weeklyDelta,
            etaWeeks = etaWeeks,
        )
    }

    /**
     * Régression linéaire simple : weight = a*day + b. La pente `a` (kg/jour)
     * × 7 = pente hebdomadaire. Approche moindres carrés.
     */
    private fun computeWeeklyDelta(logs: List<WeightLogEntity>): Double? {
        if (logs.size < 2) return null
        val refDay = logs.first().date.toEpochDay()
        val xs = logs.map { (it.date.toEpochDay() - refDay).toDouble() }
        val ys = logs.map { it.weightKg }
        val xMean = xs.average()
        val yMean = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - xMean) * (ys[i] - yMean)
            den += (xs[i] - xMean) * (xs[i] - xMean)
        }
        if (den == 0.0) return null
        val slopePerDay = num / den
        return slopePerDay * 7.0
    }

    private fun buildGeneralMotivation(
        profile: UserProfileEntity,
        recentLogs: List<WorkoutLogEntity>,
        today: LocalDate,
    ): CoachTrigger.GeneralMotivation {
        val lastWeek = today.minusDays(7)
        val count = recentLogs.count { it.completed && !it.date.toLocalDate().isBefore(lastWeek) }
        return CoachTrigger.GeneralMotivation(
            recentWorkoutCount = count,
            targetWorkoutCount = profile.workoutDays.size,
        )
    }

    private companion object {
        const val TAG = "CoachTriggerEngine"
    }
}
