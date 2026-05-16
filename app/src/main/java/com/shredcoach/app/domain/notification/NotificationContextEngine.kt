package com.shredcoach.app.domain.notification

import com.shredcoach.app.data.local.dao.GlucoseDao
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.dao.ScheduledWorkoutDao
import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.domain.glucose.GlucoseAnalyzer
import com.shredcoach.app.domain.nutrition.DailyCalorieTargetCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Service qui agrège tous les signaux contextuels (nutrition / sport / poids)
 * en un [UserContextSnapshot] consommable par les builders de notifications.
 *
 * **Architecture** : 8 queries DAO parallélisées via `coroutineScope { async }`.
 * Coût total ~30-80ms sur un device moyen avec ~30j de data.
 *
 * **Pourquoi pas de cache** : un snapshot stale (ne serait-ce que 30 minutes)
 * peut produire des notifs incorrectes (ex: déjeuner déjà loggé entre-temps).
 * Le coût de rebuild est négligeable comparé à la précision gagnée.
 *
 * **Tolérance aux données absentes** : si pas de profil → retourne null. Sinon
 * tous les champs ont des defaults sûrs (0, null, emptySet) — le snapshot est
 * toujours utilisable même chez un user neuf avec 0 logs.
 */
@Singleton
class NotificationContextEngine @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val nutritionDao: NutritionDao,
    private val workoutLogDao: WorkoutLogDao,
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val glucoseDao: GlucoseDao,
) {

    /**
     * Construit le snapshot pour la date [today] (par défaut = aujourd'hui).
     * Retourne null si le profil utilisateur n'existe pas encore (onboarding pas
     * fait) — les builders doivent SKIP dans ce cas.
     */
    suspend fun snapshot(today: LocalDate = LocalDate.now()): UserContextSnapshot? {
        val profile = userProfileDao.getUserProfileOnce() ?: return null

        val yesterday = today.minusDays(1)
        val sevenDaysAgo = today.minusDays(6) // 7-day window inclusive
        val thirtyDaysAgo = today.minusDays(29) // 30-day window inclusive

        return coroutineScope {
            // ─── Parallel DAO fetches ───
            val todayTotalsAsync = async { nutritionDao.getDayTotals(today) }
            val yesterdayTotalsAsync = async { nutritionDao.getDayTotals(yesterday) }
            val todayMealsAsync = async { nutritionDao.getMealsForDateOnce(today) }
            val yesterdayMealsAsync = async { nutritionDao.getMealsForDateOnce(yesterday) }
            val todayWorkoutsAsync = async { workoutLogDao.getCompletedLogsOnDateOnce(today) }
            val yesterdayWorkoutsAsync = async { workoutLogDao.getCompletedLogsOnDateOnce(yesterday) }
            val daily30dAsync = async { nutritionDao.getDailyMacros(thirtyDaysAgo, today) }
            val weights30dAsync = async { userProfileDao.getWeightLogsSince(thirtyDaysAgo) }
            val workoutCount7dAsync = async { workoutLogDao.getWorkoutCountInPeriod(sevenDaysAgo, today) }
            val workoutCount30dAsync = async { workoutLogDao.getWorkoutCountInPeriod(thirtyDaysAgo, today) }
            val plannedTodayAsync = async {
                scheduledWorkoutDao.getBetweenOnce(today, today)
                    .firstOrNull { it.status == "PLANNED" }
            }
            val lastCompletedLogAsync = async { workoutLogDao.getLastCompletedLogOnce() }
            // ─── CGM async ───
            val glucoseTodayAsync = async { glucoseDao.getForDateOnce(today) }
            val glucoseYesterdayAsync = async { glucoseDao.getForDateOnce(yesterday) }
            val glucose7dAsync = async { glucoseDao.getRangeOnce(sevenDaysAgo, today) }
            val glucose30dAsync = async { glucoseDao.getRangeOnce(thirtyDaysAgo, today) }

            val todayTotals = todayTotalsAsync.await()
            val yesterdayTotals = yesterdayTotalsAsync.await()
            val todayMeals = todayMealsAsync.await()
            val yesterdayMeals = yesterdayMealsAsync.await()
            val todayWorkouts = todayWorkoutsAsync.await()
            val yesterdayWorkouts = yesterdayWorkoutsAsync.await()
            val daily30d = daily30dAsync.await()
            val weights30d = weights30dAsync.await().sortedBy { it.date }
            val workoutCount7d = workoutCount7dAsync.await()
            val workoutCount30d = workoutCount30dAsync.await()
            val plannedToday = plannedTodayAsync.await()
            val lastCompletedLog = lastCompletedLogAsync.await()
            val glucoseToday = glucoseTodayAsync.await()
            val glucoseYesterday = glucoseYesterdayAsync.await()
            val glucose7d = glucose7dAsync.await()
            val glucose30d = glucose30dAsync.await()

            // ─── TODAY ───
            val todayCaloriesIn = todayTotals.totalCalories.toInt()
            val todayWorkoutDone = todayWorkouts.firstOrNull()
            val todayTarget = DailyCalorieTargetCalculator.adaptiveTarget(profile, todayWorkouts)
            val todayDelta = todayCaloriesIn - todayTarget
            val todayMealsLogged = todayMeals.map { it.mealType }.toSet()
            val remainingKcalToday = todayTarget - todayCaloriesIn

            // ─── YESTERDAY ───
            val yesterdayCaloriesIn = yesterdayTotals.totalCalories.toInt()
                .takeIf { yesterdayMeals.isNotEmpty() }
            val yesterdayTarget = yesterdayCaloriesIn?.let {
                DailyCalorieTargetCalculator.adaptiveTarget(profile, yesterdayWorkouts)
            }
            val yesterdayDelta = if (yesterdayCaloriesIn != null && yesterdayTarget != null)
                yesterdayCaloriesIn - yesterdayTarget else null
            val yesterdayMealsLogged = yesterdayMeals.map { it.mealType }.toSet()
            val yesterdayWeight = weights30d.firstOrNull { it.date == yesterday }?.weightKg

            // ─── 7-DAY & 30-DAY AGGREGATES ───
            // On reconstruit les deltas par jour : pour chaque DailyMacros sur la
            // fenêtre, on calcule un target rétroactif (basé sur profil actuel —
            // approximation acceptable, le poids varie peu sur 30j). Les jours
            // sans repas loggés ne comptent pas (pas de bruit "delta=-target").
            val baseTarget = DailyCalorieTargetCalculator.sedentaryBaseTarget(profile)

            val deltas7d = mutableListOf<Int>()
            val deltas30d = mutableListOf<Int>()
            val daysOnTarget30dList = mutableListOf<LocalDate>()

            for (day in daily30d) {
                val date = LocalDate.parse(day.date)
                if (day.totalCalories <= 0) continue
                val delta = day.totalCalories.toInt() - baseTarget
                deltas30d += delta
                if (!date.isBefore(sevenDaysAgo)) deltas7d += delta
                if (abs(delta) < 200) daysOnTarget30dList += date
            }

            val avgDelta7d = if (deltas7d.isNotEmpty()) deltas7d.average().toInt() else 0
            val avgDelta30d = if (deltas30d.isNotEmpty()) deltas30d.average().toInt() else 0
            val daysOnTarget7d = deltas7d.count { abs(it) < 200 }
            val daysOverTarget7d = deltas7d.count { it > 300 }
            val daysOnTarget30d = daysOnTarget30dList.size
            val daysOverTarget30d = deltas30d.count { it > 300 }

            // Streak on-target depuis le jour le plus récent (today si on-target,
            // sinon yesterday si on-target, etc.) — compte les jours consécutifs.
            val consecutiveOnTargetDays = computeOnTargetStreak(daily30d, baseTarget, today, todayDelta)

            // Biggest streak on-target sur 30j
            val biggestStreakOnTarget30d = computeBiggestOnTargetStreak(daysOnTarget30dList)

            // Relapse count : transitions streak ≥ 3j → dérapage
            val relapseCount30d = computeRelapseCount(daily30d, baseTarget)

            // ─── WEIGHT ───
            val weightLatest = weights30d.lastOrNull()?.weightKg
            val weightChange30d = if (weights30d.size >= 2)
                weights30d.last().weightKg - weights30d.first().weightKg else null
            val weightTrend7d = computeWeightTrendKgPerWeek(weights30d, sevenDaysAgo, today)
            val weightTrend30d = computeWeightTrendKgPerWeek(weights30d, thirtyDaysAgo, today)
            val weightGoal = profile.targetWeightKg.takeIf { it > 0 }
            val weightDistanceToGoal = if (weightLatest != null && weightGoal != null)
                weightLatest - weightGoal else null

            // ─── ACTIVITY ───
            // ⚠️ Query DAO dédiée `getLastCompletedLogOnce` (ORDER BY date DESC LIMIT 1)
            // — vs précédemment `getAllWorkoutLogsOnce.firstOrNull { completed }` qui
            // retournait la PLUS ANCIENNE (snapshot global = id ASC).
            val daysSinceLastWorkout = lastCompletedLog
                ?.let { java.time.temporal.ChronoUnit.DAYS.between(it.date.toLocalDate(), today).toInt() }
                ?: 999

            // ─── META ───
            val historyDays = daily30d.size.coerceAtMost(30)

            // ─── BUILD initial snapshot (sans pattern) puis déduit le pattern ───
            val draft = UserContextSnapshot(
                todayCaloriesIn = todayCaloriesIn,
                todayTarget = todayTarget,
                todayDelta = todayDelta,
                todayMealsLogged = todayMealsLogged,
                todayWorkoutDone = todayWorkoutDone,
                todayWorkoutPlanned = plannedToday,
                remainingKcalToday = remainingKcalToday,

                yesterdayCaloriesIn = yesterdayCaloriesIn,
                yesterdayTarget = yesterdayTarget,
                yesterdayDelta = yesterdayDelta,
                yesterdayMealsLogged = yesterdayMealsLogged,
                yesterdayWorkoutDone = yesterdayWorkouts.isNotEmpty(),
                yesterdayWeight = yesterdayWeight,

                avgDelta7d = avgDelta7d,
                daysOnTarget7d = daysOnTarget7d,
                daysOverTarget7d = daysOverTarget7d,
                consecutiveOnTargetDays = consecutiveOnTargetDays,
                workoutCount7d = workoutCount7d,
                weightTrendKgPerWeek7d = weightTrend7d,

                avgDelta30d = avgDelta30d,
                daysOnTarget30d = daysOnTarget30d,
                daysOverTarget30d = daysOverTarget30d,
                biggestStreakOnTarget30d = biggestStreakOnTarget30d,
                workoutCount30d = workoutCount30d,
                weightChange30d = weightChange30d,
                weightTrendKgPerWeek30d = weightTrend30d,
                relapseCount30d = relapseCount30d,

                weightLatest = weightLatest,
                weightGoal = weightGoal,
                weightDistanceToGoal = weightDistanceToGoal,

                daysSinceLastWorkout = daysSinceLastWorkout,

                // ─── Glycémie CGM ───
                todayGlucoseAvgMgdl = glucoseToday?.avgMgdl,
                todayTirPct = glucoseToday?.timeInRangePct,
                todayPeakMgdl = glucoseToday?.peakMgdl,
                todayHypoCount = glucoseToday?.hypoCount,
                todayGlucoseLogged = glucoseToday != null,
                yesterdayGlucoseAvgMgdl = glucoseYesterday?.avgMgdl,
                yesterdayTirPct = glucoseYesterday?.timeInRangePct,
                yesterdayPeakMgdl = glucoseYesterday?.peakMgdl,
                yesterdayHypoCount = glucoseYesterday?.hypoCount,
                yesterdayGlucoseLogged = glucoseYesterday != null,
                glucose7dAvgMgdl = GlucoseAnalyzer.avgMgdl(glucose7d),
                glucose7dAvgTir = GlucoseAnalyzer.avgTir(glucose7d),
                glucose30dTrendPerWeek = GlucoseAnalyzer.trendMgdlPerWeek(glucose30d),
                glucose30dCv = GlucoseAnalyzer.avgCv(glucose30d),
                glucose30dTotalHypo = GlucoseAnalyzer.totalHypo(glucose30d),
                glucosePattern = GlucoseAnalyzer.detectPattern(glucose30d),
                glucoseDaysCovered30d = GlucoseAnalyzer.countWithData(glucose30d),

                historyDays = historyDays,
                behaviorPattern = BehaviorPattern.NORMAL, // placeholder
            )

            draft.copy(behaviorPattern = BehaviorAnalyzer.deduce(draft))
        }
    }

    /**
     * Streak on-target consécutive en partant du jour le plus récent.
     * On parse [daily30d] (ordonné ASC par date) en sens inverse et on compte
     * tant que |delta| < 200. La date du jour est traitée à part car todayDelta
     * a déjà été calculé avec le target adaptatif (vs baseTarget pour les autres).
     */
    private fun computeOnTargetStreak(
        daily30d: List<com.shredcoach.app.data.local.dao.DailyMacros>,
        baseTarget: Int,
        today: LocalDate,
        todayDelta: Int,
    ): Int {
        // Index par date pour lookup O(1)
        val byDate = daily30d.associate { LocalDate.parse(it.date) to it.totalCalories.toInt() }

        var streak = 0
        var cursor = today

        // Jour 0 (aujourd'hui) : on-target = |todayDelta| < 200
        if (abs(todayDelta) < 200) {
            streak++
            cursor = cursor.minusDays(1)
        } else {
            // Si today est off, on cherche la dernière streak qui se termine à yesterday
            cursor = cursor.minusDays(1)
        }

        // Remonte jour par jour
        while (true) {
            val cal = byDate[cursor] ?: break  // pas de data ce jour = fin de streak
            if (cal <= 0) break
            val delta = cal - baseTarget
            if (abs(delta) >= 200) break
            streak++
            cursor = cursor.minusDays(1)
            if (java.time.temporal.ChronoUnit.DAYS.between(cursor, today) > 30) break
        }
        return streak
    }

    /** Plus longue série consécutive de jours on-target dans la fenêtre 30j. */
    private fun computeBiggestOnTargetStreak(onTargetDates: List<LocalDate>): Int {
        if (onTargetDates.isEmpty()) return 0
        val sorted = onTargetDates.sorted()
        var best = 1
        var current = 1
        for (i in 1 until sorted.size) {
            if (java.time.temporal.ChronoUnit.DAYS.between(sorted[i - 1], sorted[i]) == 1L) {
                current++
                if (current > best) best = current
            } else {
                current = 1
            }
        }
        return best
    }

    /**
     * Nb de "ruptures" sur 30j : une streak de 3+ jours on-target suivie d'un
     * jour avec delta > +400. Signal de pattern restriction/binge.
     */
    private fun computeRelapseCount(
        daily30d: List<com.shredcoach.app.data.local.dao.DailyMacros>,
        baseTarget: Int,
    ): Int {
        if (daily30d.size < 4) return 0
        val sorted = daily30d.sortedBy { LocalDate.parse(it.date) }
        var relapses = 0
        var currentStreak = 0
        for (day in sorted) {
            if (day.totalCalories <= 0) {
                currentStreak = 0
                continue
            }
            val delta = day.totalCalories.toInt() - baseTarget
            when {
                abs(delta) < 200 -> currentStreak++
                delta > 400 && currentStreak >= 3 -> {
                    relapses++
                    currentStreak = 0
                }
                else -> currentStreak = 0
            }
        }
        return relapses
    }

    /**
     * Régression linéaire simple sur les poids dans la fenêtre. Retourne la
     * slope convertie en kg/semaine (× 7). Null si < 2 mesures dans la fenêtre.
     */
    private fun computeWeightTrendKgPerWeek(
        weights: List<com.shredcoach.app.data.local.entity.WeightLogEntity>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
    ): Double? {
        val inWindow = weights.filter { !it.date.isBefore(windowStart) && !it.date.isAfter(windowEnd) }
        if (inWindow.size < 2) return null

        // y = a*x + b — least squares
        val refDay = inWindow.first().date.toEpochDay().toDouble()
        val xs = inWindow.map { it.date.toEpochDay().toDouble() - refDay }
        val ys = inWindow.map { it.weightKg }
        val meanX = xs.average()
        val meanY = ys.average()
        val num = xs.zip(ys).sumOf { (x, y) -> (x - meanX) * (y - meanY) }
        val den = xs.sumOf { (it - meanX) * (it - meanX) }
        if (den == 0.0) return null
        val slopePerDay = num / den
        return slopePerDay * 7.0
    }
}
