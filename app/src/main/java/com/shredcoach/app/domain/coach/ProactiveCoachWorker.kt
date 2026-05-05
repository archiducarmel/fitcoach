package com.shredcoach.app.domain.coach

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shredcoach.app.data.consent.ConsentStore
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.notification.AppNotificationDispatcher
import com.shredcoach.app.ShredCoachApplication
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Worker quotidien qui envoie une notification de coaching proactif IA.
 *
 * Pipeline :
 * 1. Triple gate (settings.enabled, consent LLM_CHAT, profile.notificationsEnabled)
 * 2. Quiet hours (8h-21h30 local)
 * 3. CoachContextBuilder.build → contexte multi-canal
 * 4. CoachTriggerEngine.evaluate → liste filtrée (mute/cooldown/cap/skip-only-general)
 * 5. Top trigger ; si liste vide → silent day, exit success
 * 6. CoachPromptBuilder.buildSystemPrompt(tone) + buildUserPrompt(trigger, ctx)
 * 7. LLM call (timeout 25s) ou fallback déterministe
 * 8. Dispatcher.dispatch avec deeplink + 2 actions (Faire / Plus tard)
 * 9. CoachHistoryStore.recordEmission(category) → cooldown actif pour la prochaine fenêtre
 *
 * **Idempotent par design** : si re-run dans la même fenêtre de cooldown,
 * la catégorie sera filtrée → pas de doublon. Le worker peut donc être retry
 * sans poser de problème.
 */
@HiltWorker
class ProactiveCoachWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coachSettings: CoachSettingsStore,
    private val consentStore: ConsentStore,
    private val triggerEngine: CoachTriggerEngine,
    private val promptBuilder: CoachPromptBuilder,
    private val contextBuilder: CoachContextBuilder,
    private val historyStore: CoachHistoryStore,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val dispatcher: AppNotificationDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Triple gate
        val coachSnap = coachSettings.snapshot.first()
        if (!coachSnap.enabled) {
            Log.d(TAG, "Skip — coach proactif désactivé")
            return Result.success()
        }
        val consentSnap = consentStore.snapshot.first()
        if (!consentSnap.isCurrent(ConsentStore.ConsentType.LLM_CHAT)) {
            Log.d(TAG, "Skip — consentement LLM non accordé")
            return Result.success()
        }
        val profile = userRepository.getUserProfileOnce()
        if (profile == null || !profile.notificationsEnabled) {
            Log.d(TAG, "Skip — notifications désactivées")
            return Result.success()
        }

        val now = LocalTime.now(ZoneId.systemDefault())
        if (now.isBefore(QUIET_START) || now.isAfter(QUIET_END)) {
            Log.d(TAG, "Skip — hors plage horaire ($now)")
            return Result.success()
        }

        // Contexte multi-canal
        val ctx = contextBuilder.build() ?: run {
            Log.d(TAG, "Skip — pas de profil pour bâtir le contexte")
            return Result.success()
        }

        // Top trigger
        val triggers = runCatching { triggerEngine.evaluate() }
            .onFailure { Log.w(TAG, "Trigger engine failed", it) }
            .getOrDefault(emptyList())
        val top = triggers.firstOrNull() ?: run {
            Log.d(TAG, "Aucun trigger émissible — silent day")
            return Result.success()
        }

        // LLM
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val message = if (apiKey.isNotBlank()) {
            try {
                val provider = runCatching { LlmProvider.valueOf(profile.llmProvider) }
                    .getOrDefault(LlmProvider.GROQ)
                val model = profile.llmModel.takeIf { it.isNotBlank() }
                val systemPrompt = promptBuilder.buildSystemPrompt(coachSnap.tone)
                val userPrompt = promptBuilder.buildUserPrompt(top, ctx)
                withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                    )
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "LLM call failed, fallback déterministe", e)
                null
            }
        } else null

        val body = message ?: fallbackMessage(top, ctx.firstName)
        val source = if (message != null) "llm" else "local"

        // Actions : 1 bouton primaire si l'action existe, +1 "Plus tard" générique.
        val actions = buildList {
            top.primaryDeeplink?.let { route ->
                top.primaryActionLabel?.let { label ->
                    add(AppNotificationDispatcher.NotificationAction(label = label, deeplinkRoute = route))
                }
            }
            // Pas de bouton "Plus tard" pour l'instant — pas de mécanique de snooze
            // côté UX (réenrôler le worker à H+4 dépend d'une logique métier non
            // triviale : ne pas spammer la même catégorie). À ajouter en V5.4.
        }

        dispatcher.dispatch(
            type = NotifType.COACH_PROACTIVE,
            title = "🧠 Shreddy",
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source,
            deeplink = top.primaryDeeplink,
            actions = actions,
        )

        // Marquer l'émission → cooldown actif
        historyStore.recordEmission(top.category)

        Log.i(TAG, "Coach proactif posté (trigger=${top.category}, source=$source, deeplink=${top.primaryDeeplink})")
        return Result.success()
    }

    /**
     * Fallback déterministe en français — si le LLM tombe ou pas de clé API.
     * Volontairement basique : le LLM apporte la vraie valeur de personnalisation.
     * Couvre tous les types de triggers (sealed exhaustif).
     */
    private fun fallbackMessage(trigger: CoachTrigger, firstName: String): String {
        val name = if (firstName.isNotBlank()) " $firstName" else ""
        return when (trigger) {
            is CoachTrigger.StreakAtRisk ->
                "Streak de ${trigger.streakDays} jours en jeu$name. Une séance courte aujourd'hui le préserve."
            is CoachTrigger.MissedScheduledWorkout ->
                "T'avais prévu '${trigger.workoutName}'$name. On la recale cette semaine ?"
            is CoachTrigger.PersonalRecordCelebration ->
                "Nouveau record sur ${trigger.exerciseName} : ${trigger.newWeightKg.toInt()}kg$name. On vise plus la prochaine fois !"
            is CoachTrigger.ProteinDeficit ->
                "Hier ${trigger.gramsConsumed}g de prot vs objectif ${trigger.goalGrams}g$name. Un shaker aujourd'hui aide."
            is CoachTrigger.PlateauVolume ->
                "Volume stable depuis ${trigger.weeksFlat} sem$name. On change un exo ou on monte les charges ?"
            is CoachTrigger.Comeback ->
                "Pas de séance depuis ${trigger.daysAway} jours$name. Une session courte de 30 min recrée le rythme."
            is CoachTrigger.BodyScanStale ->
                "Dernière mesure il y a ${trigger.daysSince} jours$name. 30s pour scanner ton corps et ajuster le suivi."
            is CoachTrigger.GoalProximityETA ->
                "Plus que ${"%.1f".format(kotlin.math.abs(trigger.currentWeightKg - trigger.targetWeightKg))}kg vers l'objectif$name. ETA ~${trigger.etaWeeks} sem au rythme actuel."
            is CoachTrigger.WeeklyRecap ->
                "Bilan semaine : ${trigger.workoutsThisWeek}/${trigger.targetWorkouts} séances, ${trigger.totalVolumeKg}kg cumulés$name. Cap sur la suivante."
            is CoachTrigger.GeneralMotivation ->
                "${trigger.recentWorkoutCount} séances cette semaine$name. Petit objectif du jour ?"
        }
    }

    companion object {
        private const val TAG = "ProactiveCoachWorker"
        const val UNIQUE_NAME = "shredcoach_proactive_coach"
        private val QUIET_START = LocalTime.of(8, 0)
        private val QUIET_END = LocalTime.of(21, 30)

        fun enqueue(context: Context, preferredHourLocal: Int = CoachSettingsStore.DEFAULT_HOUR) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ProactiveCoachWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySecondsTo(preferredHourLocal), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun initialDelaySecondsTo(hour: Int): Long {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            var target = now.with(LocalTime.of(hour.coerceIn(6, 22), 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).seconds.coerceAtLeast(60)
        }
    }
}
