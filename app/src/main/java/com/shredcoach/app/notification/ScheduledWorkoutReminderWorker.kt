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
import com.shredcoach.app.data.repository.ScheduledWorkoutRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeout

/**
 * Worker qui déclenche les rappels liés à une séance planifiée.
 *
 * 2 modes (via inputData `type`) :
 *  - "shaker_2h"   → 2h avant la séance : "Pense à ton shaker pré-training"
 *  - "start_30min" → 30min avant : "C'est l'heure ! Prépare-toi"
 *
 * Si la séance est déjà COMPLETED/SKIPPED/CANCELED au moment du run, skip silencieux.
 * Message généré par LLM (avec fallback local si pas de clé API ou timeout).
 */
@HiltWorker
class ScheduledWorkoutReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val scheduledRepo: ScheduledWorkoutRepository,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val chatRepository: ChatRepository,
    private val dispatcher: AppNotificationDispatcher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduledId = inputData.getLong(KEY_SCHEDULED_ID, -1L)
        val type = inputData.getString(KEY_TYPE) ?: return Result.failure()
        if (scheduledId <= 0) return Result.failure()

        val profile = userRepository.getUserProfileOnce() ?: return Result.success()
        if (!profile.notificationsEnabled) return Result.success()

        val scheduled = scheduledRepo.getById(scheduledId) ?: return Result.success()
        // Skip si pas PLANNED (complétée, annulée, skippée)
        if (scheduled.status != "PLANNED") return Result.success()

        // Skip si déjà envoyé (double-check anti-doublon)
        if (type == TYPE_SHAKER && scheduled.reminderShakerSent) return Result.success()
        if (type == TYPE_START && scheduled.reminderStartSent) return Result.success()

        val workoutName = scheduled.workoutId?.let { workoutRepository.getWorkoutById(it)?.name }
            ?: scheduled.title.takeIf { it.isNotBlank() }
            ?: "Séance"
        val timeStr = scheduled.time?.toString()?.substring(0, 5) ?: ""
        val firstName = profile.firstName.ifBlank { "toi" }

        // ─── Construire le prompt LLM selon le type ───
        val (systemPrompt, userPrompt, fallback) = when (type) {
            TYPE_SHAKER -> Triple(
                SHAKER_SYSTEM_PROMPT,
                "Séance '$workoutName' prévue à $timeStr (dans 2h). Propose à $firstName un shaker ou collation pré-training pour être au top.",
                "Dans 2h : $workoutName. Pense à ton shaker 🥤 (whey + banane = top pré-training)."
            )
            TYPE_START -> Triple(
                START_SYSTEM_PROMPT,
                "Séance '$workoutName' prévue à $timeStr (dans 30 min). Motive $firstName pour qu'il se prépare maintenant.",
                "Dans 30 min : $workoutName. Prépare tes affaires et allume la flamme ! 🔥"
            )
            else -> return Result.failure()
        }

        // ─── Appel LLM avec fallback ───
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val llmMessage = if (apiKey.isNotBlank()) {
            try {
                val provider = runCatching { LlmProvider.valueOf(profile.llmProvider) }.getOrDefault(LlmProvider.GROQ)
                val model = profile.llmModel.takeIf { it.isNotBlank() }
                withTimeout(15_000) {
                    chatRepository.quickCoachMessage(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        provider = provider,
                        apiKey = apiKey,
                        model = model
                    )
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        } else null

        val body = llmMessage ?: fallback
        val title = when (type) {
            TYPE_SHAKER -> "🥤 Shaker pré-training"
            TYPE_START -> "🔥 C'est l'heure !"
            else -> "Séance à venir"
        }

        dispatcher.dispatch(
            type = NotifType.WORKOUT_REMINDER,
            title = title,
            body = body,
            channelId = ShredCoachApplication.CHANNEL_WORKOUT,
            source = if (llmMessage != null) "llm" else "local"
        )

        // Marquer le reminder comme envoyé pour éviter doublons
        when (type) {
            TYPE_SHAKER -> scheduledRepo.markShakerReminderSent(scheduledId)
            TYPE_START -> scheduledRepo.markStartReminderSent(scheduledId)
        }

        return Result.success()
    }

    companion object {
        const val KEY_SCHEDULED_ID = "scheduled_id"
        const val KEY_TYPE = "reminder_type"
        const val TYPE_SHAKER = "shaker_2h"
        const val TYPE_START = "start_30min"

        val SHAKER_SYSTEM_PROMPT = """
Tu es Shreddy, coach sportif IA. Tu rédiges une notification push courte (180 chars max, 1-2 phrases)
pour rappeler un shaker/collation pré-training 2h avant une séance.

RÈGLES :
- Français, tutoiement, prénom si fourni
- Ton humoristique et motivant, jamais culpabilisant
- Max 180 caractères, max 2 phrases
- Suggère un aliment/shaker précis (whey, banane, flocons d'avoine, etc.)
- Pas de salutations, pas d'emojis (sauf 1 max en fin)
- Réponse directe, pas de blabla
        """.trimIndent()

        val START_SYSTEM_PROMPT = """
Tu es Shreddy, coach sportif IA. Tu rédiges une notification push courte (180 chars max, 1-2 phrases)
pour pousser l'utilisateur à se préparer 30min avant sa séance.

RÈGLES :
- Français, tutoiement, prénom si fourni
- Ton énergique et motivant, pas agressif
- Max 180 caractères, max 2 phrases
- Évoque la séance + invite à se préparer maintenant
- Pas de salutations
- Réponse directe, 1 emoji max en fin
        """.trimIndent()
    }
}
