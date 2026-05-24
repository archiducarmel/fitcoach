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
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.consent.ConsentStore
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.notification.AppNotificationDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Récap hebdomadaire envoyé chaque dimanche soir.
 *
 * Different du [ProactiveCoachWorker] :
 * - **Cadence** : 7 jours (vs 24h pour le daily)
 * - **Trigger** : forge directement [CoachTrigger.WeeklyRecap] avec les stats
 *   de la semaine — pas d'évaluation par l'engine, le récap est attendu et
 *   inconditionnel (sauf gates de base : enabled, consent, notifs).
 * - **Mute possible** : si l'utilisateur a mute "weekly_recap" via Settings
 *   → on skip silencieusement.
 *
 * Pourquoi un worker séparé : le timing (dimanche 19h précis) et la cadence
 * (7 jours) ne mélangent pas avec le worker quotidien. Séparation des
 * responsabilités → testable indépendamment, débuggable séparément.
 */
@HiltWorker
class WeeklyRecapWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coachSettings: CoachSettingsStore,
    private val consentStore: ConsentStore,
    private val promptBuilder: CoachPromptBuilder,
    private val contextBuilder: CoachContextBuilder,
    private val historyStore: CoachHistoryStore,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val workoutLogDao: WorkoutLogDao,
    private val nutritionDao: NutritionDao,
    private val dispatcher: AppNotificationDispatcher,
    private val llmResolver: com.shredcoach.app.domain.llm.AssistantLlmResolver,
    private val keyResolver: com.shredcoach.app.domain.llm.LlmKeyResolver,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Gates
        val coachSnap = coachSettings.snapshot.first()
        if (!coachSnap.enabled) return Result.success()
        if ("weekly_recap" in coachSnap.mutedCategories) return Result.success()

        val consentSnap = consentStore.snapshot.first()
        if (!consentSnap.isCurrent(ConsentStore.ConsentType.LLM_CHAT)) return Result.success()

        val profile = userRepository.getUserProfileOnce() ?: return Result.success()
        if (!profile.notificationsEnabled) return Result.success()

        // Cooldown : on évite un double-recap si le worker est retry
        if (historyStore.isOnCooldown("weekly_recap", Duration.ofDays(6))) {
            return Result.success()
        }

        // Stats de la semaine écoulée (lundi → dimanche).
        // ATTENTION : `today.with(DayOfWeek.SUNDAY)` retourne le dimanche de
        // la même ISO-week, donc le DIMANCHE FUTUR si on est lundi-samedi.
        // Sur un retry lundi matin (réseau down dimanche soir), ça produirait
        // une fenêtre future avec 0 données. On force donc le dernier dimanche
        // RÉVOLU (≤ today).
        val today = LocalDate.now()
        val daysFromSunday = (today.dayOfWeek.value % 7).toLong() // Mon=1..Sat=6, Sun→0
        val sunday = today.minusDays(daysFromSunday)
        val monday = sunday.minusDays(6)

        val workoutCount = workoutLogDao.getWorkoutCountInPeriod(monday, sunday)
        val totalVolume = workoutLogDao.getTotalVolumeInPeriod(monday, sunday) ?: 0.0
        val proteinAdherence = computeProteinAdherence(monday, sunday)

        // Jeûne intermittent moyen sur la semaine (depuis MealLogEntity)
        val fasting = com.shredcoach.app.domain.nutrition.FastingWindowCalculator.aggregate(
            start = monday, end = sunday,
        ) { date -> nutritionDao.getMealsForDateOnce(date) }

        val trigger = CoachTrigger.WeeklyRecap(
            workoutsThisWeek = workoutCount,
            targetWorkouts = profile.workoutDays.size,
            totalVolumeKg = totalVolume.toInt(),
            proteinAdherence = proteinAdherence,
            avgFastingHours = fasting.averageHours,
            daysWith16hFasting = fasting.daysWith16h,
        )

        // Contexte multi-canal — important pour le récap (peut référencer
        // le top exo ou le dernier scan repas dans la formulation)
        val ctx = contextBuilder.build() ?: return Result.success()

        // LLM — BUGFIX v2026.05.24 : resolve provider AVANT fetch key.
        val llmConfig = llmResolver.resolveWithProfile(com.shredcoach.app.domain.llm.AiAssistant.WEEKLY_RECAP, profile)
        val provider = llmConfig.provider
        val model: String? = llmConfig.modelId
        val apiKey = keyResolver.keyFor(provider)
        val message = if (apiKey.isNotBlank()) {
            try {
                val systemPrompt = promptBuilder.buildSystemPrompt(coachSnap.tone)
                val userPrompt = promptBuilder.buildUserPrompt(trigger, ctx)
                withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        assistant = com.shredcoach.app.domain.llm.AiAssistant.WEEKLY_RECAP,
                        fallback = llmResolver.buildFallbackConfig(
                            com.shredcoach.app.domain.llm.AiAssistant.WEEKLY_RECAP, profile, apiKey,
                        ),
                    )
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "LLM weekly recap failed, fallback", e)
                null
            }
        } else null

        val body = message ?: fallbackRecap(trigger, ctx.firstName)
        val source = if (message != null) "llm" else "local"

        dispatcher.dispatch(
            type = NotifType.WEEKLY_RECAP,
            title = applicationContext.getString(R.string.coach_weekly_recap_title),
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source,
            deeplink = trigger.primaryDeeplink,
            actions = trigger.primaryDeeplink?.let { route ->
                trigger.primaryActionLabel?.let { label ->
                    listOf(
                        AppNotificationDispatcher.NotificationAction(
                            label = label, deeplinkRoute = route
                        )
                    )
                }
            } ?: emptyList(),
        )

        historyStore.recordEmission("weekly_recap")
        Log.i(TAG, "WeeklyRecap posté (workouts=$workoutCount, volume=${totalVolume.toInt()}kg, source=$source)")
        return Result.success()
    }

    /**
     * Adhérence protéique = % de jours de la semaine où l'apport protéine
     * a atteint au moins 70% de l'objectif. Si pas d'objectif défini → 0.
     */
    private suspend fun computeProteinAdherence(start: LocalDate, end: LocalDate): Int {
        val goal = nutritionDao.getNutritionGoalOnce()?.targetProteins ?: return 0
        if (goal <= 0) return 0
        var hitDays = 0
        var cursor = start
        while (!cursor.isAfter(end)) {
            val totals = nutritionDao.getDayTotals(cursor)
            if (totals.totalProteins >= goal * 0.7) hitDays++
            cursor = cursor.plusDays(1)
        }
        return (hitDays * 100 / 7).coerceIn(0, 100)
    }

    private fun fallbackRecap(trigger: CoachTrigger.WeeklyRecap, firstName: String): String {
        val name = if (firstName.isNotBlank()) " $firstName" else ""
        return if (trigger.avgFastingHours > 0) {
            val h = trigger.avgFastingHours.toInt()
            val m = ((trigger.avgFastingHours - h) * 60).toInt()
            val avg = if (m < 5) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
            applicationContext.getString(
                R.string.coach_weekly_recap_fallback_with_fasting,
                name, trigger.workoutsThisWeek, trigger.targetWorkouts,
                trigger.totalVolumeKg, trigger.proteinAdherence, avg,
            )
        } else {
            applicationContext.getString(
                R.string.coach_weekly_recap_fallback,
                name, trigger.workoutsThisWeek, trigger.targetWorkouts,
                trigger.totalVolumeKg, trigger.proteinAdherence,
            )
        }
    }

    companion object {
        private const val TAG = "WeeklyRecapWorker"
        const val UNIQUE_NAME = "shredcoach_weekly_recap"
        private val TARGET_TIME = LocalTime.of(19, 0)

        /**
         * Enrôle un PeriodicWorkRequest 7 jours, déclenché à la prochaine
         * occurrence de **dimanche 19h** local. Les retries éventuels
         * (réseau down, etc.) sont absorbés par le cooldown 6j dans le
         * CoachHistoryStore.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<WeeklyRecapWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySecondsToNextSundayEvening(), TimeUnit.SECONDS)
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

        private fun initialDelaySecondsToNextSundayEvening(): Long {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            var target = now.with(DayOfWeek.SUNDAY).with(TARGET_TIME)
            if (!target.isAfter(now)) target = target.plusDays(7)
            return Duration.between(now, target).seconds.coerceAtLeast(60)
        }
    }
}
