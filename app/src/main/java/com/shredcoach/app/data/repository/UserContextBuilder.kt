package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.domain.workout.RoutineCatalog
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construit le contexte utilisateur pour le system prompt de Shreddy.
 * 12 blocs : profil, santé, temporel, stats, tendances, comportement,
 * régularité, déséquilibres musculaires, **routines (split coverage)**,
 * historique, PRs, nutrition. Budget total : ~1,400 tokens.
 */
@Singleton
class UserContextBuilder @Inject constructor(
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository
) {
    private val fmt = DateTimeFormatter.ofPattern("dd/MM", Locale.FRANCE)
    private fun fmtD(d: Double) = String.format(Locale.US, "%.0f", d)
    private fun fmtD1(d: Double) = String.format(Locale.US, "%.1f", d)

    suspend fun buildContext(): String {
        val profile = userRepository.getUserProfileOnce() ?: return ""
        val now = LocalDate.now()
        val sb = StringBuilder()

        sb.appendLine(buildProfileBlock(profile))
        sb.appendLine(buildHealthBlock(profile))
        // Charger les logs récents UNE SEULE FOIS (perf: 1 query au lieu de 4)
        val allRecentLogs = try {
            workoutRepository.getRecentWorkoutLogs(20).first()
        } catch (_: Exception) { emptyList() }

        sb.appendLine(buildTemporalBlock(profile, now, allRecentLogs))
        sb.appendLine(buildStatsBlock(profile, now))
        sb.appendLine(buildTrendsBlock(now))
        sb.appendLine(buildBehaviorBlock(allRecentLogs))
        sb.appendLine(buildRegularityBlock(profile, now, allRecentLogs))
        sb.appendLine(buildMuscleBalanceBlock(now, allRecentLogs))
        sb.appendLine(buildRoutineBlock(profile, now, allRecentLogs))
        sb.appendLine(buildRecentHistoryBlock(allRecentLogs))
        sb.appendLine(buildPRBlock())
        sb.appendLine(buildNutritionBlock(now))

        return sb.toString()
    }

    // ═══════════════════════════════════════
    // 1. PROFIL
    // ═══════════════════════════════════════
    private fun buildProfileBlock(p: UserProfileEntity): String {
        val goal = when (p.goal.name) { "SHRED" -> "Sèche"; "BULK" -> "Prise de masse"; "MAINTAIN" -> "Maintien"; else -> p.goal.name }
        val level = when (p.level.name) { "BEGINNER" -> "Débutant"; "INTERMEDIATE" -> "Intermédiaire"; "ADVANCED" -> "Avancé"; else -> p.level.name }
        val equip = when (p.equipment.name) { "FULL_GYM" -> "Salle complète"; "HOME_GYM" -> "Home gym"; "BODYWEIGHT" -> "Poids du corps"; else -> p.equipment.name }
        val dayNames = p.workoutDays.sorted().joinToString(", ") { d ->
            DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.FRANCE)
        }
        return """[PROFIL] ${p.firstName}, ${p.age} ans, ${if (p.sex == "M") "H" else "F"}, ${p.heightCm}cm
Poids: ${fmtD1(p.currentWeightKg)}kg → cible ${fmtD1(p.targetWeightKg)}kg (${fmtD1(p.currentWeightKg - p.targetWeightKg)}kg à ${if (p.currentWeightKg > p.targetWeightKg) "perdre" else "prendre"})
Objectif: $goal | Niveau: $level | Équipement: $equip
Durée préférée: ${p.preferredWorkoutDuration}min | Jours: $dayNames
Mensurations: taille ${p.waistCm}cm, poitrine ${p.chestCm}cm, bras ${p.armCm}cm, cuisse ${p.thighCm}cm""".trimIndent()
    }

    // ═══════════════════════════════════════
    // 2. SANTÉ / LIMITATIONS
    // ═══════════════════════════════════════
    private fun buildHealthBlock(p: UserProfileEntity): String {
        if (p.healthNotes.isBlank()) return "[SANTÉ] Aucune limitation signalée."
        return "[SANTÉ/LIMITATIONS] ${p.healthNotes}\n⚠ Adapter les exercices en conséquence."
    }

    // ═══════════════════════════════════════
    // 3. CONTEXTE TEMPOREL
    // ═══════════════════════════════════════
    private suspend fun buildTemporalBlock(p: UserProfileEntity, now: LocalDate, allLogs: List<WorkoutLogEntity>): String {
        val dayName = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)
        val hour = LocalTime.now().hour
        val moment = when { hour < 12 -> "matin"; hour < 18 -> "après-midi"; else -> "soir" }
        val isTodayWorkoutDay = now.dayOfWeek.value in p.workoutDays

        // Dernier entraînement
        val lastLog = allLogs.firstOrNull()
        val daysSinceLast = if (lastLog != null) ChronoUnit.DAYS.between(lastLog.date.toLocalDate(), now).toInt() else -1

        // Prochain jour planifié
        val nextWorkoutDay = (1..7).firstNotNullOfOrNull { offset ->
            val d = now.plusDays(offset.toLong())
            if (d.dayOfWeek.value in p.workoutDays) d else null
        }

        val sb = StringBuilder("[CONTEXTE TEMPOREL] $dayName $moment (${now.format(fmt)})")
        if (isTodayWorkoutDay) sb.append(" — JOUR D'ENTRAÎNEMENT")
        sb.appendLine()
        if (daysSinceLast >= 0) sb.appendLine("Dernière séance: il y a $daysSinceLast jour(s)")
        if (daysSinceLast >= 4) sb.appendLine("⚠ Gap de $daysSinceLast jours sans entraînement")
        nextWorkoutDay?.let { sb.appendLine("Prochain entraînement prévu: ${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE)} ${it.format(fmt)}") }

        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 4. STATS GLOBALES
    // ═══════════════════════════════════════
    private suspend fun buildStatsBlock(p: UserProfileEntity, now: LocalDate): String {
        val m = now.minusDays(30); val w = now.minusDays(7)
        val monthVol = try { workoutRepository.getTotalVolumeInPeriod(m, now) } catch (_: Exception) { 0.0 }
        val monthCount = try { workoutRepository.getWorkoutCountInPeriod(m, now) } catch (_: Exception) { 0 }
        val weekCount = try { workoutRepository.getWorkoutCountInPeriod(w, now) } catch (_: Exception) { 0 }
        return "[STATS] Total: ${p.totalWorkouts} séances | Streak: ${p.currentStreakDays}j | Mois: $monthCount séances, ${fmtD(monthVol)}kg vol | Semaine: $weekCount séances"
    }

    // ═══════════════════════════════════════
    // 5. TENDANCES / PROGRESSION (4 semaines)
    // ═══════════════════════════════════════
    private suspend fun buildTrendsBlock(now: LocalDate): String {
        val sb = StringBuilder("[TENDANCES 4 SEMAINES]\n")
        try {
            val weeks = (0..3).map { w ->
                val end = now.minusDays(w * 7L)
                val start = end.minusDays(6)
                val vol = workoutRepository.getTotalVolumeInPeriod(start, end)
                val count = workoutRepository.getWorkoutCountInPeriod(start, end)
                Triple("S-$w", vol, count)
            }.reversed() // chronologique

            weeks.forEach { (label, vol, count) -> sb.appendLine("$label: $count séances, ${fmtD(vol)}kg vol") }

            // Calcul progression
            val firstVol = weeks.firstOrNull()?.second ?: 0.0
            val lastVol = weeks.lastOrNull()?.second ?: 0.0
            if (firstVol > 0) {
                val pct = ((lastVol - firstVol) / firstVol * 100)
                sb.appendLine("Progression volume: ${if (pct > 0) "+" else ""}${fmtD(pct)}% sur 4 semaines")
            }
        } catch (_: Exception) { sb.appendLine("Données insuffisantes.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 6. COMPORTEMENT EN SÉANCE
    // ═══════════════════════════════════════
    private suspend fun buildBehaviorBlock(allLogs: List<WorkoutLogEntity>): String {
        val sb = StringBuilder("[COMPORTEMENT]\n")
        try {
            val logs = allLogs
            if (logs.isEmpty()) { sb.appendLine("Pas assez de données."); return sb.toString().trimEnd() }

            val completed = logs.count { it.completed }
            val abandoned = logs.size - completed
            sb.appendLine("Séances terminées: $completed/${logs.size} (${completed * 100 / logs.size}%) | Abandonnées: $abandoned")

            // Séries skippées vs bonus (analyse des sets)
            var totalSetsCompleted = 0; var totalSetsSkipped = 0
            var totalRestTarget = 0L; var totalRestActual = 0L; var restCount = 0
            logs.take(10).forEach { log ->
                val sets = workoutRepository.getWorkoutSets(log.id)
                totalSetsCompleted += sets.count { it.completed }
                totalSetsSkipped += sets.count { !it.completed }
                sets.forEach { s ->
                    if (s.targetRestSeconds > 0 && s.restSeconds != null) {
                        totalRestTarget += s.targetRestSeconds
                        totalRestActual += s.restSeconds!!
                        restCount++
                    }
                }
            }
            sb.appendLine("Séries complétées: $totalSetsCompleted | Skippées: $totalSetsSkipped (sur 10 dernières séances)")

            if (restCount > 0) {
                val avgRestTarget = totalRestTarget / restCount
                val avgRestActual = totalRestActual / restCount
                val restDelta = avgRestActual - avgRestTarget
                sb.appendLine("Repos moyen: ${avgRestActual}s (cible: ${avgRestTarget}s, delta: ${if (restDelta > 0) "+" else ""}${restDelta}s)")
                if (restDelta > 30) sb.appendLine("⚠ Repos significativement plus longs que la cible — fatigue possible")
                if (restDelta < -15) sb.appendLine("→ Repos plus courts que prévu — bon rythme !")
            }

            // Durée réelle vs préférée
            val avgDuration = logs.filter { it.completed }.map { it.actualDurationSeconds / 60 }.average()
            if (avgDuration > 0) sb.appendLine("Durée moyenne réelle: ${fmtD(avgDuration)} min")

        } catch (_: Exception) { sb.appendLine("Données insuffisantes.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 7. RÉGULARITÉ / COMPLIANCE
    // ═══════════════════════════════════════
    private suspend fun buildRegularityBlock(p: UserProfileEntity, now: LocalDate, allLogs: List<WorkoutLogEntity>): String {
        val sb = StringBuilder("[RÉGULARITÉ]\n")
        try {
            val plannedDaysPerWeek = p.workoutDays.size
            if (plannedDaysPerWeek == 0) { sb.appendLine("Aucun jour planifié."); return sb.toString().trimEnd() }

            // 30 derniers jours : compter les séances par semaine
            val monthCount = workoutRepository.getWorkoutCountInPeriod(now.minusDays(30), now)
            val expectedMonth = (plannedDaysPerWeek * 30.0 / 7).toInt()
            val compliance = if (expectedMonth > 0) monthCount * 100 / expectedMonth else 0
            sb.appendLine("Compliance 30j: $monthCount/$expectedMonth séances ($compliance%)")

            when {
                compliance >= 90 -> sb.appendLine("→ Excellent ! Régularité exemplaire.")
                compliance >= 70 -> sb.appendLine("→ Bien, quelques séances manquées.")
                compliance >= 50 -> sb.appendLine("⚠ Régularité moyenne — motiver pour reprendre le rythme.")
                else -> sb.appendLine("⚠ Régularité faible — comprendre les freins et adapter.")
            }

            // Gaps (jours consécutifs sans séance)
            val logs = allLogs.take(10)
            if (logs.size >= 2) {
                val maxGap = logs.zipWithNext().maxOfOrNull { (a, b) ->
                    ChronoUnit.DAYS.between(b.date.toLocalDate(), a.date.toLocalDate()).toInt()
                } ?: 0
                if (maxGap >= 4) sb.appendLine("Plus long gap récent: $maxGap jours sans séance")
            }
        } catch (_: Exception) { sb.appendLine("Données insuffisantes.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 8. DÉSÉQUILIBRES MUSCULAIRES
    // ═══════════════════════════════════════
    private suspend fun buildMuscleBalanceBlock(now: LocalDate, allLogs: List<WorkoutLogEntity>): String {
        val sb = StringBuilder("[ÉQUILIBRE MUSCULAIRE 30j]\n")
        try {
            val logs = allLogs
            val recentLogIds = logs.filter {
                ChronoUnit.DAYS.between(it.date.toLocalDate(), now) <= 30
            }.map { it.id }

            if (recentLogIds.isEmpty()) { sb.appendLine("Pas de données."); return sb.toString().trimEnd() }

            // Compter les sets par groupe musculaire
            val muscleSetCounts = mutableMapOf<String, Int>()
            recentLogIds.forEach { logId ->
                val sets = workoutRepository.getWorkoutSets(logId)
                sets.filter { it.completed }.forEach { set ->
                    val exo = workoutRepository.getExercisesForWorkoutId(set.exerciseId)
                    if (exo != null && exo.muscleGroup.name != "WARMUP" && exo.muscleGroup.name != "CARDIO") {
                        muscleSetCounts[exo.muscleGroup.displayName] =
                            (muscleSetCounts[exo.muscleGroup.displayName] ?: 0) + 1
                    }
                }
            }

            if (muscleSetCounts.isEmpty()) { sb.appendLine("Pas de données."); return sb.toString().trimEnd() }

            val total = muscleSetCounts.values.sum()
            val sorted = muscleSetCounts.entries.sortedByDescending { it.value }
            sorted.forEach { (group, count) ->
                val pct = count * 100 / total
                sb.appendLine("$group: $count sets ($pct%)")
            }

            // Alertes déséquilibres (push/pull ratio, quads/ischio ratio)
            val pushSets = (muscleSetCounts["Pectoraux"] ?: 0) + (muscleSetCounts["Pectoraux supérieurs"] ?: 0) + (muscleSetCounts["Épaules"] ?: 0) + (muscleSetCounts["Triceps"] ?: 0)
            val pullSets = (muscleSetCounts["Dos (largeur)"] ?: 0) + (muscleSetCounts["Dos (épaisseur)"] ?: 0) + (muscleSetCounts["Biceps"] ?: 0)
            if (pushSets > 0 && pullSets > 0) {
                val ratio = pushSets.toFloat() / pullSets
                sb.appendLine("Ratio push/pull: ${fmtD1(ratio.toDouble())} (idéal: ~1.0)")
                if (ratio > 1.5) sb.appendLine("⚠ Trop de push vs pull — risque déséquilibre postural")
                if (ratio < 0.7) sb.appendLine("⚠ Trop de pull vs push")
            }

            val quadSets = muscleSetCounts["Quadriceps / Fessiers"] ?: 0
            val hamSets = muscleSetCounts["Ischio-jambiers"] ?: 0
            if (quadSets > 0 && hamSets == 0) sb.appendLine("⚠ Ischio-jambiers jamais travaillés — risque blessure")

        } catch (_: Exception) { sb.appendLine("Données insuffisantes.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 8.5 ROUTINES / SPLIT COVERAGE (4 sem)
    // ═══════════════════════════════════════
    /**
     * Donne au LLM la cartographie des routines effectivement pratiquées sur
     * les 28 derniers jours + dernière routine + routine habituelle. Permet au
     * coach de suggérer la routine complémentaire (Push fait → propose Pull),
     * ou d'alerter sur une couverture déséquilibrée (5x Push / 0x Pull).
     */
    private fun buildRoutineBlock(p: UserProfileEntity, now: LocalDate, allLogs: List<WorkoutLogEntity>): String {
        val sb = StringBuilder("[ROUTINES 4 SEMAINES]\n")
        val cutoff = now.minusDays(28)
        val recent = allLogs.filter { it.date.toLocalDate() >= cutoff }
        if (recent.isEmpty()) {
            sb.appendLine("Aucune séance sur les 4 dernières semaines.")
            return sb.toString().trimEnd()
        }
        val byRoutine = recent.groupingBy { it.routineId }.eachCount()
            .entries.sortedByDescending { it.value }
        val total = recent.size
        byRoutine.forEach { (id, count) ->
            val routine = RoutineCatalog.byId(id)
            val pct = count * 100 / total
            sb.appendLine("• ${routine.displayName}: $count séances ($pct%)")
        }

        // Dernière routine pratiquée — utile pour suggérer la complémentaire.
        val last = allLogs.firstOrNull()
        if (last != null) {
            val lastRoutine = RoutineCatalog.byId(last.routineId)
            val daysAgo = ChronoUnit.DAYS.between(last.date.toLocalDate(), now).toInt()
            sb.appendLine("Dernière routine: ${lastRoutine.displayName} (il y a ${daysAgo}j)")
            lastRoutine.complementaryRoutineId?.let { compId ->
                val comp = RoutineCatalog.byId(compId)
                sb.appendLine("→ Routine complémentaire suggérée: ${comp.displayName}")
            }
        }

        // Routine habituelle (préférence persistée du dernier choix utilisateur).
        val habitual = RoutineCatalog.byId(p.lastUsedRoutineId)
        sb.appendLine("Routine habituelle (dernier choix): ${habitual.displayName}")
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 9. HISTORIQUE RÉCENT (5 dernières séances)
    // ═══════════════════════════════════════
    private suspend fun buildRecentHistoryBlock(allLogs: List<WorkoutLogEntity>): String {
        val sb = StringBuilder("[HISTORIQUE 5 DERNIÈRES SÉANCES]\n")
        try {
            val recentLogs = allLogs.take(5)
            if (recentLogs.isEmpty()) { sb.appendLine("Aucune séance."); return sb.toString() }

            recentLogs.forEach { log ->
                val name = log.workoutId?.let { workoutRepository.getWorkoutById(it)?.name } ?: "Séance libre"
                val sets = workoutRepository.getWorkoutSets(log.id)
                val exoCount = sets.map { it.exerciseId }.distinct().size
                val skipped = sets.count { !it.completed }
                val completed = if (log.completed) "✓" else "✗"

                sb.appendLine("• ${log.date.format(fmt)} $name $completed — $exoCount exos, ${sets.size} sets, ${log.totalReps} reps, ${fmtD(log.totalVolume)}kg, ${log.actualDurationSeconds / 60}min${if (skipped > 0) ", $skipped skippées" else ""}")

                // Top 3 exos par volume
                sets.groupBy { it.exerciseId }
                    .map { (id, s) -> Triple(workoutRepository.getExercisesForWorkoutId(id)?.name ?: "?", s.maxOf { it.weightKg }, s.sumOf { it.weightKg * it.reps }) }
                    .sortedByDescending { it.third }.take(3)
                    .forEach { (n, w, v) -> sb.appendLine("  → $n: max ${fmtD(w)}kg, vol ${fmtD(v)}kg") }
            }
        } catch (_: Exception) { sb.appendLine("Non disponible.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 10. RECORDS PERSONNELS
    // ═══════════════════════════════════════
    private suspend fun buildPRBlock(): String {
        val sb = StringBuilder("[RECORDS PERSONNELS]\n")
        try {
            val prs = workoutRepository.getPersonalRecords()
            if (prs.isEmpty()) { sb.appendLine("Aucun."); return sb.toString() }
            prs.take(8).forEach { pr ->
                val name = workoutRepository.getExercisesForWorkoutId(pr.exerciseId)?.name ?: "#${pr.exerciseId}"
                sb.appendLine("• $name: ${fmtD(pr.maxWeight)}kg × ${pr.reps} reps")
            }
        } catch (_: Exception) { sb.appendLine("Non disponible.") }
        return sb.toString().trimEnd()
    }

    // ═══════════════════════════════════════
    // 11. NUTRITION + COMPLIANCE
    // ═══════════════════════════════════════
    private suspend fun buildNutritionBlock(now: LocalDate): String {
        val sb = StringBuilder("[NUTRITION]\n")
        try {
            val goal = nutritionRepository.getNutritionGoalOnce()
            if (goal != null) {
                sb.appendLine("Objectifs: ${goal.targetCalories}kcal, ${goal.targetProteins}g prot, ${goal.targetCarbs}g carbs, ${goal.targetFats}g lip")

                // Moyenne 7j + compliance
                var totalCal = 0.0; var totalProt = 0.0; var totalCarbs = 0.0; var totalFats = 0.0
                var daysTracked = 0; var daysOnTarget = 0
                for (d in 0..6) {
                    val totals = nutritionRepository.getDayTotals(now.minusDays(d.toLong()))
                    if (totals.totalCalories > 0) {
                        totalCal += totals.totalCalories; totalProt += totals.totalProteins
                        totalCarbs += totals.totalCarbs; totalFats += totals.totalFats; daysTracked++
                        // ±10% de la cible = on target
                        val calorieTarget = goal.targetCalories
                        if (totals.totalCalories >= calorieTarget * 0.9 && totals.totalCalories <= calorieTarget * 1.1) daysOnTarget++
                    }
                }
                if (daysTracked > 0) {
                    val avgCal = totalCal / daysTracked; val avgProt = totalProt / daysTracked
                    val avgCarbs = totalCarbs / daysTracked; val avgFats = totalFats / daysTracked
                    sb.appendLine("Moyenne 7j: ${avgCal.toInt()}kcal, ${avgProt.toInt()}g prot, ${avgCarbs.toInt()}g gluc, ${avgFats.toInt()}g lip ($daysTracked jours trackés)")
                    sb.appendLine("Compliance calorique: $daysOnTarget/$daysTracked jours dans la cible (±10%)")
                    // Protéines par kg
                    val profile = userRepository.getUserProfileOnce()
                    if (profile != null && profile.currentWeightKg > 0) {
                        val protPerKg = avgProt / profile.currentWeightKg
                        sb.appendLine("Protéines: ${fmtD1(protPerKg)}g/kg (recommandé: 1.6-2.2g/kg en ${if (profile.goal.name == "SHRED") "sèche" else "muscu"})")
                        if (protPerKg < 1.6) sb.appendLine("⚠ Apport protéique insuffisant")
                    }
                    // Surplus/déficit réel
                    val delta = avgCal - goal.targetCalories
                    sb.appendLine("Écart moyen: ${if (delta > 0) "+" else ""}${delta.toInt()} kcal/j (${if (delta > 0) "surplus" else "déficit"})")
                }
            }
            val topFoods = nutritionRepository.getTopFoods(now.minusDays(30))
            if (topFoods.isNotEmpty()) {
                sb.appendLine("Top aliments (30j): ${topFoods.take(5).joinToString(", ") { "${it.name}(${it.count}×)" }}")
            }
        } catch (_: Exception) { sb.appendLine("Non disponible.") }
        return sb.toString().trimEnd()
    }
}
