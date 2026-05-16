package com.shredcoach.app.domain.coach

import com.shredcoach.app.data.local.dao.ChatDao
import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.dao.GlucoseDao
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.domain.glucose.GlucoseAnalyzer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construit le [CoachUserContext] à partir de la DB Room. Aggrégation **read-only**
 * de plusieurs DAOs en une seule lecture cohérente.
 *
 * Performance : appelé 1 fois par run du worker (1x/jour typique). Coût négligeable
 * vs la latence LLM qui suit (~5-25s). Pas d'optimisation prématurée.
 */
@Singleton
class CoachContextBuilder @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val workoutLogDao: WorkoutLogDao,
    private val exerciseDao: ExerciseDao,
    private val chatDao: ChatDao,
    private val mealScanDao: MealScanDao,
    private val glucoseDao: GlucoseDao,
) {
    suspend fun build(today: LocalDate = LocalDate.now()): CoachUserContext? {
        val profile = userProfileDao.getUserProfileOnce() ?: return null

        // Glycémie CGM (optionnel — null si l'user n'a jamais uploadé).
        val glucose7d = runCatching { glucoseDao.getRangeOnce(today.minusDays(6), today) }.getOrNull() ?: emptyList()
        val glucose30d = runCatching { glucoseDao.getRangeOnce(today.minusDays(29), today) }.getOrNull() ?: emptyList()
        val gAvg7d = GlucoseAnalyzer.avgMgdl(glucose7d)
        val gTir30d = GlucoseAnalyzer.avgTir(glucose30d)
        val gPattern = if (glucose30d.isNotEmpty()) GlucoseAnalyzer.detectPattern(glucose30d).name else null

        return CoachUserContext(
            firstName = profile.firstName.ifBlank { "" },
            ageYears = profile.age,
            sex = profile.sex,
            level = profile.level.name,
            goal = profile.goal.name,
            currentWeightKg = profile.currentWeightKg,
            targetWeightKg = profile.targetWeightKg,
            healthNotes = profile.healthNotes,

            recentChatSnippets = collectRecentChatSnippets(),

            lastBodyScanDaysAgo = profile.bodyScanTimestamp
                ?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today).toInt() },
            waistCm = profile.waistCm,
            bodyFatPercent = profile.bodyFatPercent,
            bodyScanNotes = profile.bodyScanNotes.take(160),  // tronqué : on n'envoie pas un essai

            topExerciseNames = collectTopExercises(limit = 3),
            lastMealScanDish = collectLastMealScanDish(),
            workoutsThisWeek = countWorkoutsThisWeek(today),
            targetWorkoutsPerWeek = profile.workoutDays.size,
            weeklyVolumeKg = computeWeeklyVolumeKg(today),

            glucose7dAvgMgdl = gAvg7d,
            glucose30dAvgTir = gTir30d,
            glucosePatternName = gPattern,
        )
    }

    /**
     * Récupère les 3 derniers messages **utilisateur** (rôle user) dans Shreddy.
     * Tronqués à 80 chars pour rester économe en tokens et éviter d'envoyer
     * des paragraphes entiers au LLM.
     */
    private suspend fun collectRecentChatSnippets(): List<String> {
        val all = chatDao.getAllMessagesOnce()
        return all.asSequence()
            .filter { it.role.equals("user", ignoreCase = true) }
            .sortedByDescending { it.timestamp }
            .take(3)
            .map { msg ->
                msg.content.replace('\n', ' ').take(80).let {
                    if (msg.content.length > 80) "$it…" else it
                }
            }
            .toList()
    }

    /**
     * Top exos par fréquence de sets complétés (90 derniers jours pour rester pertinent).
     * Si historique vide → liste vide ; le prompt builder s'adaptera.
     */
    private suspend fun collectTopExercises(limit: Int): List<String> {
        val sets = workoutLogDao.getAllWorkoutSetsOnce()
        if (sets.isEmpty()) return emptyList()
        val byCount = sets.groupingBy { it.exerciseId }.eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
        return byCount.mapNotNull { exId -> exerciseDao.getExerciseById(exId)?.name }
    }

    private suspend fun collectLastMealScanDish(): String? {
        val scans = mealScanDao.getAllScans().first()
        return scans.firstOrNull()?.dishName?.takeIf { it.isNotBlank() }
    }

    private suspend fun countWorkoutsThisWeek(today: LocalDate): Int {
        val mondayThisWeek = today.with(java.time.DayOfWeek.MONDAY)
        val recentLogs = workoutLogDao.getRecentWorkoutLogs(20).first()
        return recentLogs.count { log ->
            log.completed && !log.date.toLocalDate().isBefore(mondayThisWeek)
        }
    }

    private suspend fun computeWeeklyVolumeKg(today: LocalDate): Int {
        val mondayThisWeek = today.with(java.time.DayOfWeek.MONDAY)
        val recentLogs = workoutLogDao.getRecentWorkoutLogs(20).first()
        val thisWeek = recentLogs.filter { log ->
            log.completed && !log.date.toLocalDate().isBefore(mondayThisWeek)
        }
        return thisWeek.sumOf { it.totalVolume }.toInt()
    }
}
