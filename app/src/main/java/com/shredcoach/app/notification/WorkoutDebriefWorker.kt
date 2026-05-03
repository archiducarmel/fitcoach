package com.shredcoach.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Worker déclenché 30 min après la fin d'une séance complétée.
 * Génère un débrief IA humoristique : qualité de la séance, régularité,
 * encouragement pour la prochaine session.
 */
@HiltWorker
class WorkoutDebriefWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val chatRepository: ChatRepository,
    private val dispatcher: AppNotificationDispatcher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val logId = inputData.getLong(KEY_LOG_ID, -1L)
        if (logId <= 0) return Result.failure()

        val profile = userRepository.getUserProfileOnce() ?: return Result.success()
        if (!profile.notificationsEnabled || !profile.notifWorkoutDebrief) return Result.success()

        val log = workoutRepository.getWorkoutLogById(logId) ?: return Result.success()
        if (!log.completed) return Result.success() // Séance abandonnée → pas de débrief

        // Nom de la séance
        val workoutName = log.workoutId?.let { workoutRepository.getWorkoutById(it)?.name }
            ?: "Séance libre"

        // Nombre de séances dans la semaine courante (lundi → aujourd'hui)
        val today = LocalDate.now()
        val mondayThisWeek = today.with(DayOfWeek.MONDAY)
        val recentLogs = try { workoutRepository.getRecentWorkoutLogs(20).first() } catch (_: Exception) { emptyList() }
        val workoutsThisWeek = recentLogs.count { rLog ->
            rLog.completed && !rLog.date.toLocalDate().isBefore(mondayThisWeek)
        }

        // Streak calculé depuis les logs
        val completedDates = recentLogs.filter { it.completed }.map { it.date.toLocalDate() }.toSet()
        var streak = 0
        var cursor = if (completedDates.contains(today)) today else today.minusDays(1)
        while (completedDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }

        val prompt = DebriefPrompts.buildWorkoutDebriefPrompt(
            firstName = profile.firstName.ifBlank { "toi" },
            workoutName = workoutName,
            durationMin = log.actualDurationSeconds / 60,
            exercisesCompleted = log.exercisesCompleted,
            exercisesTotal = log.exercisesCompleted + log.exercisesSkipped,
            totalSets = log.totalSets,
            totalReps = log.totalReps,
            totalVolumeKg = log.totalVolume,
            hasPR = false, // On pourrait calculer mais pas critique pour MVP
            streakDays = streak,
            workoutsThisWeek = workoutsThisWeek,
            targetWorkoutsPerWeek = profile.workoutDays.size,
            goalName = profile.goal.name
        )

        // LLM call avec timeout + fallback local
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val llmMessage = if (apiKey.isNotBlank()) {
            try {
                val provider = runCatching { LlmProvider.valueOf(profile.llmProvider) }.getOrDefault(LlmProvider.GROQ)
                val model = profile.llmModel.takeIf { it.isNotBlank() }
                val result = withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = prompt,
                        systemPrompt = DebriefPrompts.DEBRIEF_SYSTEM_PROMPT,
                        provider = provider,
                        apiKey = apiKey,
                        model = model
                    )
                }
                result.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        } else null

        val body = llmMessage ?: fallbackWorkoutMessage(log.totalVolume.toInt(), log.totalSets, workoutsThisWeek)
        val source = if (llmMessage != null) "llm" else "local"

        dispatcher.dispatch(
            type = NotifType.WORKOUT_DEBRIEF,
            title = "💪 Débrief : $workoutName",
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source
        )
        return Result.success()
    }

    private fun fallbackWorkoutMessage(volume: Int, sets: Int, weekCount: Int): String =
        "Séance terminée : ${sets} séries, ${volume}kg de volume. $weekCount séance${if (weekCount > 1) "s" else ""} cette semaine. Bonne récup !"

    companion object {
        const val KEY_LOG_ID = "log_id"
        const val DEFAULT_DELAY_MINUTES = 30L
        fun uniqueTag(logId: Long) = "workout_debrief_$logId"
    }
}
