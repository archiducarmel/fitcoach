package com.shredcoach.app.notification

import android.content.Context
import androidx.work.*
import com.shredcoach.app.data.local.entity.UserProfileEntity
import java.util.concurrent.TimeUnit

/**
 * Planifie toutes les notifications récurrentes.
 *
 * **Architecture v2** (post-bug "rafale à l'ouverture") :
 *  - **Daily reminders** (repas, shakers, coucher, motivation) : délégués à
 *    [NotificationAlarmScheduler] qui utilise `AlarmManager.setAndAllowWhileIdle`.
 *    Bypasse Doze, ne dérive pas, survit aux reboots (via [BootReceiver]).
 *  - **One-shot debriefs** (meal/workout debriefs après scan/séance) : restent
 *    sur WorkManager `OneTimeWorkRequest` — délais courts (5-90 min), pas de
 *    contrainte temporelle exacte (un débrief 5 min plus tard est OK).
 *  - **Workout reminders** (séances planifiées 2h/30min avant) : aussi
 *    WorkManager pour la même raison — l'horizon est court.
 *
 * **Ce qui a changé** : les daily reminders étaient sur `PeriodicWorkRequest`
 * 24h. Sous Doze, drift de plusieurs heures. À l'ouverture de l'app, WorkManager
 * rattrapait le retard d'un coup → "rafale de notifs". Le passage à AlarmManager
 * supprime le drift et les bursts.
 */
object NotificationScheduler {

    /**
     * Programme toutes les notifications quotidiennes via AlarmManager.
     * Idempotent : appelé depuis SettingsScreen (sur toggle change),
     * Application.onCreate (cold start), et BootReceiver (post-reboot).
     */
    fun scheduleAll(context: Context, profile: UserProfileEntity) {
        NotificationAlarmScheduler.scheduleAll(context, profile)
    }

    fun cancelAll(context: Context) {
        NotificationAlarmScheduler.cancelAll(context)
    }

    // ═══════════════════════════════════════
    // DÉBRIEFS DIFFÉRÉS (one-shot)
    // ═══════════════════════════════════════

    /** Programme un débrief repas après le scan. Délai configurable via user settings. */
    fun scheduleMealDebrief(context: Context, scanId: Long, delayMinutes: Long = MealDebriefWorker.DEFAULT_DELAY_MINUTES) {
        val safeDelay = delayMinutes.coerceIn(5L, 24 * 60L)
        val request = OneTimeWorkRequestBuilder<MealDebriefWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putLong(MealDebriefWorker.KEY_SCAN_ID, scanId).build())
            .addTag(MealDebriefWorker.uniqueTag(scanId))
            .addTag("shredcoach_debrief")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MealDebriefWorker.uniqueTag(scanId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Programme un débrief séance après la fin. Délai configurable via user settings. */
    fun scheduleWorkoutDebrief(context: Context, logId: Long, delayMinutes: Long = WorkoutDebriefWorker.DEFAULT_DELAY_MINUTES) {
        val safeDelay = delayMinutes.coerceIn(5L, 24 * 60L)
        val request = OneTimeWorkRequestBuilder<WorkoutDebriefWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putLong(WorkoutDebriefWorker.KEY_LOG_ID, logId).build())
            .addTag(WorkoutDebriefWorker.uniqueTag(logId))
            .addTag("shredcoach_debrief")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkoutDebriefWorker.uniqueTag(logId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelMealDebrief(context: Context, scanId: Long) =
        WorkManager.getInstance(context).cancelUniqueWork(MealDebriefWorker.uniqueTag(scanId))

    fun cancelWorkoutDebrief(context: Context, logId: Long) =
        WorkManager.getInstance(context).cancelUniqueWork(WorkoutDebriefWorker.uniqueTag(logId))

    // ═══════════════════════════════════════
    // RAPPELS DE SÉANCES PLANIFIÉES (Calendar feature)
    // ═══════════════════════════════════════

    /**
     * Programme les 2 rappels pour une séance planifiée :
     *  - 2h avant : shaker pré-training
     *  - 30min avant : "c'est l'heure"
     *
     * Si la séance est dans le passé ou trop proche (< 30min), skip l'enqueue.
     * Si la séance n'a pas d'heure (time == null), skip les reminders (on ne peut rien calculer).
     */
    fun scheduleWorkoutReminders(
        context: Context,
        scheduledId: Long,
        date: java.time.LocalDate,
        time: java.time.LocalTime?
    ) {
        if (time == null) return
        val now = java.time.LocalDateTime.now()
        val target = java.time.LocalDateTime.of(date, time)

        // ── Rappel shaker 2h avant ──
        val shakerTime = target.minusHours(2)
        if (shakerTime.isAfter(now)) {
            val delayMinShaker = java.time.Duration.between(now, shakerTime).toMinutes()
            val requestShaker = OneTimeWorkRequestBuilder<ScheduledWorkoutReminderWorker>()
                .setInitialDelay(delayMinShaker, TimeUnit.MINUTES)
                .setInputData(Data.Builder()
                    .putLong(ScheduledWorkoutReminderWorker.KEY_SCHEDULED_ID, scheduledId)
                    .putString(ScheduledWorkoutReminderWorker.KEY_TYPE, ScheduledWorkoutReminderWorker.TYPE_SHAKER)
                    .build())
                .addTag("workout_reminder_${scheduledId}_shaker")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "workout_reminder_${scheduledId}_shaker",
                ExistingWorkPolicy.REPLACE,
                requestShaker
            )
        }

        // ── Rappel démarrage 30min avant ──
        val startTime = target.minusMinutes(30)
        if (startTime.isAfter(now)) {
            val delayMinStart = java.time.Duration.between(now, startTime).toMinutes()
            val requestStart = OneTimeWorkRequestBuilder<ScheduledWorkoutReminderWorker>()
                .setInitialDelay(delayMinStart, TimeUnit.MINUTES)
                .setInputData(Data.Builder()
                    .putLong(ScheduledWorkoutReminderWorker.KEY_SCHEDULED_ID, scheduledId)
                    .putString(ScheduledWorkoutReminderWorker.KEY_TYPE, ScheduledWorkoutReminderWorker.TYPE_START)
                    .build())
                .addTag("workout_reminder_${scheduledId}_start")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "workout_reminder_${scheduledId}_start",
                ExistingWorkPolicy.REPLACE,
                requestStart
            )
        }
    }

    /** Annule les 2 rappels d'une séance planifiée (utilisé sur delete/cancel). */
    fun cancelWorkoutReminders(context: Context, scheduledId: Long) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("workout_reminder_${scheduledId}_shaker")
        wm.cancelUniqueWork("workout_reminder_${scheduledId}_start")
    }
}
