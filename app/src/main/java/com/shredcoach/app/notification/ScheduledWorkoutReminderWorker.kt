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
import com.shredcoach.app.data.repository.ScheduledWorkoutRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.workout.RoutineCatalog
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
    private val dispatcher: AppNotificationDispatcher,
    private val llmResolver: com.shredcoach.app.domain.llm.AssistantLlmResolver,
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
            ?: context.getString(R.string.notif_session_fallback_default)
        val timeStr = scheduled.time?.toString()?.substring(0, 5) ?: ""
        val firstName = profile.firstName.ifBlank { context.getString(R.string.coach_first_name_fallback) }

        // Enrichissement routine-aware : si la séance est un split (Push/Pull/…),
        // on l'ajoute en préfixe au nom + on l'injecte dans le contexte LLM pour
        // que le coach puisse la mentionner naturellement. Skip pour Full Body
        // (historique de l'app — éviterait un doublon "Full Body : Full Body").
        val routine = RoutineCatalog.byId(scheduled.routineId)
        val isSplit = routine.id != RoutineCatalog.Default.id
        val routineLabel = if (isSplit) routine.displayName else null
        // Le titre push sera "Push · Squat 90min" plutôt que juste "Squat 90min"
        // si la routine est un split. Sinon, on garde le nom brut.
        val displayName = if (routineLabel != null && !workoutName.contains(routineLabel, ignoreCase = true)) {
            "$routineLabel · $workoutName"
        } else workoutName

        // ─── Construire le prompt LLM selon le type ───
        val en = com.shredcoach.app.domain.i18n.PromptLocale.isEn()
        val (systemPrompt, userPrompt, fallback) = when (type) {
            TYPE_SHAKER -> Triple(
                SHAKER_SYSTEM_PROMPT,
                buildString {
                    if (en) {
                        append("Session '$displayName' planned at $timeStr (in 2h).")
                        if (routineLabel != null) append(" Session type: $routineLabel.")
                        append(" Suggest to $firstName a pre-workout shake or snack to be on point.")
                    } else {
                        append("Séance '$displayName' prévue à $timeStr (dans 2h).")
                        if (routineLabel != null) append(" Type de séance : $routineLabel.")
                        append(" Propose à $firstName un shaker ou collation pré-training pour être au top.")
                    }
                },
                context.getString(R.string.notif_shaker_in_2h, routineLabel ?: workoutName)
            )
            TYPE_START -> Triple(
                START_SYSTEM_PROMPT,
                buildString {
                    if (en) {
                        append("Session '$displayName' planned at $timeStr (in 30 min).")
                        if (routineLabel != null) append(" Session type: $routineLabel.")
                        append(" Motivate $firstName so they get ready right now.")
                    } else {
                        append("Séance '$displayName' prévue à $timeStr (dans 30 min).")
                        if (routineLabel != null) append(" Type de séance : $routineLabel.")
                        append(" Motive $firstName pour qu'il se prépare maintenant.")
                    }
                },
                context.getString(R.string.notif_start_in_30min, routineLabel ?: workoutName)
            )
            else -> return Result.failure()
        }

        // ─── Appel LLM avec fallback ───
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val llmMessage = if (apiKey.isNotBlank()) {
            try {
                // Resolver per-assistant : SCHEDULED_REMINDER configurable via Settings.
                val llmConfig = llmResolver.resolveWithProfile(com.shredcoach.app.domain.llm.AiAssistant.SCHEDULED_REMINDER, profile)
                val provider = llmConfig.provider
                val model: String? = llmConfig.modelId
                withTimeout(15_000) {
                    chatRepository.quickCoachMessage(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        assistant = com.shredcoach.app.domain.llm.AiAssistant.SCHEDULED_REMINDER,
                    )
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        } else null

        val body = llmMessage ?: fallback
        val title = when (type) {
            TYPE_SHAKER -> context.getString(R.string.notif_shaker_pretraining_title)
            TYPE_START -> context.getString(R.string.notif_workout_start_title)
            else -> context.getString(R.string.notif_workout_upcoming_title)
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

        private val SHAKER_SYSTEM_PROMPT_FR = """
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

        private val SHAKER_SYSTEM_PROMPT_EN = """
You are Shreddy, AI sport coach. You write a short push notification (180 chars max, 1-2 sentences)
to remind the user about a pre-workout shake/snack 2h before a session.

RULES:
- English, direct address (you), first name when provided
- Humorous and motivating tone, never guilt-tripping
- Max 180 characters, max 2 sentences
- Suggest a precise food/shake (whey, banana, oats, etc.)
- No greetings, no emojis (max 1 at the end)
- Direct answer, no fluff
        """.trimIndent()

        private val START_SYSTEM_PROMPT_FR = """
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

        private val START_SYSTEM_PROMPT_EN = """
You are Shreddy, AI sport coach. You write a short push notification (180 chars max, 1-2 sentences)
to nudge the user to get ready 30min before their session.

RULES:
- English, direct address (you), first name when provided
- Energetic and motivating tone, not aggressive
- Max 180 characters, max 2 sentences
- Mention the session + invite them to prep right now
- No greetings
- Direct answer, max 1 emoji at the end
        """.trimIndent()

        val SHAKER_SYSTEM_PROMPT: String
            get() = com.shredcoach.app.domain.i18n.PromptLocale.pick(
                fr = SHAKER_SYSTEM_PROMPT_FR, en = SHAKER_SYSTEM_PROMPT_EN
            )

        val START_SYSTEM_PROMPT: String
            get() = com.shredcoach.app.domain.i18n.PromptLocale.pick(
                fr = START_SYSTEM_PROMPT_FR, en = START_SYSTEM_PROMPT_EN
            )
    }
}
