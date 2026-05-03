package com.shredcoach.app.notification

import android.content.Context
import androidx.work.*
import com.shredcoach.app.data.local.entity.UserProfileEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Planifie toutes les notifications récurrentes via WorkManager.
 * Chaque notification est un PeriodicWorkRequest qui se répète toutes les 24h.
 */
object NotificationScheduler {

    fun scheduleAll(context: Context, profile: UserProfileEntity) {
        val wm = WorkManager.getInstance(context)

        // Annuler tout d'abord
        wm.cancelAllWorkByTag("shredcoach_notif")

        if (!profile.notificationsEnabled) return

        // Repas
        if (profile.notifBreakfast) schedule(context, ShredCoachNotificationWorker.TYPE_BREAKFAST, profile.breakfastTime)
        if (profile.notifLunch) schedule(context, ShredCoachNotificationWorker.TYPE_LUNCH, profile.lunchTime)
        if (profile.notifSnack) schedule(context, ShredCoachNotificationWorker.TYPE_SNACK, profile.snackTime)
        if (profile.notifDinner) schedule(context, ShredCoachNotificationWorker.TYPE_DINNER, profile.dinnerTime)

        // Shakers
        if (profile.notifShaker) {
            schedule(context, ShredCoachNotificationWorker.TYPE_SHAKER_MORNING, profile.shakerMorningTime)
            schedule(context, ShredCoachNotificationWorker.TYPE_SHAKER_EVENING, profile.shakerEveningTime)
        }

        // Coucher (30 min avant)
        if (profile.notifBedtime && profile.bedTime != null) {
            val bedReminder = profile.bedTime.minusMinutes(30)
            schedule(context, ShredCoachNotificationWorker.TYPE_BEDTIME, bedReminder)
        }

        // Motivation (check quotidien à 10h)
        if (profile.notifMotivation) {
            schedule(context, ShredCoachNotificationWorker.TYPE_MOTIVATION, LocalTime.of(10, 0))
        }
    }

    private fun schedule(context: Context, type: String, targetTime: LocalTime) {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(targetTime)
        if (target.isBefore(now)) target = target.plusDays(1)
        val delayMillis = Duration.between(now, target).toMillis()

        val data = Data.Builder().putString("type", type).build()

        val request = PeriodicWorkRequestBuilder<ShredCoachNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("shredcoach_notif")
            .addTag("shredcoach_$type")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("shredcoach_$type", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("shredcoach_notif")
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
