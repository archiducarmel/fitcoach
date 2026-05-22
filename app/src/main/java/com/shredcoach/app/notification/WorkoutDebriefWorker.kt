package com.shredcoach.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.streak.StreakService
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
    private val dispatcher: AppNotificationDispatcher,
    private val streakService: StreakService,
    private val llmResolver: com.shredcoach.app.domain.llm.AssistantLlmResolver,
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
            ?: context.getString(R.string.workout_debrief_session_fallback_name)

        // Nombre de séances dans la semaine courante (lundi → aujourd'hui)
        val today = LocalDate.now()
        val mondayThisWeek = today.with(DayOfWeek.MONDAY)
        val recentLogs = try { workoutRepository.getRecentWorkoutLogs(20).first() } catch (_: Exception) { emptyList() }
        val workoutsThisWeek = recentLogs.count { rLog ->
            rLog.completed && !rLog.date.toLocalDate().isBefore(mondayThisWeek)
        }

        // Streak via le service unique (cohérent avec HomeViewModel et CoachTriggerEngine).
        val streak = streakService.compute(recentLogs.filter { it.completed }, today).currentDays

        val prompt = DebriefPrompts.buildWorkoutDebriefPrompt(
            firstName = profile.firstName.ifBlank { context.getString(R.string.coach_first_name_fallback) },
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
                // Resolver per-assistant : WORKOUT_DEBRIEF configurable via Settings.
                val llmConfig = llmResolver.resolveWithProfile(com.shredcoach.app.domain.llm.AiAssistant.WORKOUT_DEBRIEF, profile)
                val provider = llmConfig.provider
                val model: String? = llmConfig.modelId
                val result = withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = prompt,
                        systemPrompt = DebriefPrompts.DEBRIEF_SYSTEM_PROMPT,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        assistant = com.shredcoach.app.domain.llm.AiAssistant.WORKOUT_DEBRIEF,
                        fallback = llmResolver.buildFallbackConfig(
                            com.shredcoach.app.domain.llm.AiAssistant.WORKOUT_DEBRIEF, profile, apiKey,
                        ),
                    )
                }
                result.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        } else null

        val body = llmMessage ?: fallbackWorkoutMessage(log.totalVolume.toInt(), log.totalSets, workoutsThisWeek)
        val source = if (llmMessage != null) "llm" else "local"

        dispatcher.dispatch(
            type = NotifType.WORKOUT_DEBRIEF,
            title = context.getString(R.string.workout_debrief_title, workoutName),
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source
        )
        return Result.success()
    }

    private fun fallbackWorkoutMessage(volume: Int, sets: Int, weekCount: Int): String =
        context.resources.getQuantityString(
            R.plurals.workout_debrief_fallback_local,
            weekCount,
            sets, volume, weekCount,
        )

    companion object {
        const val KEY_LOG_ID = "log_id"
        const val DEFAULT_DELAY_MINUTES = 30L
        fun uniqueTag(logId: Long) = "workout_debrief_$logId"
    }
}
