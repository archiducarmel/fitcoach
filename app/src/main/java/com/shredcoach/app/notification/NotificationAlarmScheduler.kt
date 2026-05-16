package com.shredcoach.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shredcoach.app.data.local.entity.UserProfileEntity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Planifie les notifications quotidiennes via [AlarmManager], pas WorkManager.
 *
 * **Pourquoi pas WorkManager `PeriodicWorkRequest`** :
 *  - Périodes 24h dérivent de plusieurs heures sous Doze (la plateforme batche
 *    les jobs pour économiser la batterie) → un rappel "déjeuner à 12h30" peut
 *    arriver à 14h ou 15h, ce qui le rend inutilisable.
 *  - Si l'app est force-killed ou le device reboot, les périodic workers
 *    deviennent orphelins jusqu'à la prochaine ouverture de l'app → effet
 *    "rafale de notifs en retard à l'ouverture".
 *  - Pas de garantie d'horodatage exact, c'est by-design pour du background sync.
 *
 * **AlarmManager `setAndAllowWhileIdle`** :
 *  - Bypasse Doze → l'alarme se déclenche même device verrouillé/idle
 *  - Inexact (max ~9 min de drift en deep idle) — acceptable pour un rappel
 *    repas (12h30 → max 12h39, jamais des heures de retard)
 *  - Pas de permission spéciale requise (vs `setExactAndAllowWhileIdle` qui
 *    nécessite SCHEDULE_EXACT_ALARM en runtime sur Android 12+, denied par
 *    défaut sur 13+ pour les apps non-calendrier)
 *  - Survit aux reboots SI on les re-schedule via [BootReceiver]
 *
 * **Cycle de vie d'une alarme** :
 *  1. [scheduleAll] cancel toutes les alarmes existantes puis programme une
 *     alarme par type activé dans le profil utilisateur
 *  2. À l'heure cible : [NotificationAlarmReceiver] reçoit le broadcast
 *  3. Le receiver enqueue un [ShredCoachNotificationWorker] qui poste la notif
 *  4. Le receiver re-schedule la même alarme à T+24h (sinon one-shot)
 *  5. Sur reboot device : [BootReceiver] rappelle [scheduleAll]
 *  6. Sur cold-start app : [com.shredcoach.app.ShredCoachApplication.onCreate]
 *     rappelle [scheduleAll] (idempotent car cancel-then-reschedule)
 */
object NotificationAlarmScheduler {

    /**
     * Programme toutes les alarmes journalières en fonction du profil.
     * Cancel d'abord toute alarme existante (même requestCode → remplacement
     * atomique côté AlarmManager via `FLAG_UPDATE_CURRENT`).
     */
    fun scheduleAll(context: Context, profile: UserProfileEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, am)

        if (!profile.notificationsEnabled) return

        if (profile.notifBreakfast) schedule(context, am, AlarmType.BREAKFAST, profile.breakfastTime)
        if (profile.notifLunch) schedule(context, am, AlarmType.LUNCH, profile.lunchTime)
        if (profile.notifSnack) schedule(context, am, AlarmType.SNACK, profile.snackTime)
        if (profile.notifDinner) schedule(context, am, AlarmType.DINNER, profile.dinnerTime)
        if (profile.notifShaker) {
            schedule(context, am, AlarmType.SHAKER_MORNING, profile.shakerMorningTime)
            schedule(context, am, AlarmType.SHAKER_EVENING, profile.shakerEveningTime)
        }
        if (profile.notifBedtime && profile.bedTime != null) {
            schedule(context, am, AlarmType.BEDTIME, profile.bedTime.minusMinutes(30))
        }
        if (profile.notifMotivation) {
            schedule(context, am, AlarmType.MOTIVATION, LocalTime.of(10, 0))
            // Brief du jour à 07:00 — synthèse contextuelle (P5). Gated par le
            // même toggle que MOTIVATION car c'est conceptuellement une notif
            // "coach proactif". Pourra avoir son propre toggle plus tard.
            schedule(context, am, AlarmType.MORNING_BRIEF, LocalTime.of(7, 0))
        }
        // Glucose recap J+1 à 12h17 — analyse de la glycémie de la veille par
        // Dr. Glykos. Le builder skip s'il n'y a pas de data J-1, donc on
        // peut programmer sans coût utile : l'alarme se déclenche, le builder
        // décide. Toggle dédié pour permettre opt-out propre.
        if (profile.notifGlucoseRecap) {
            schedule(context, am, AlarmType.GLUCOSE_RECAP, LocalTime.of(12, 17))
        }
    }

    /** Programme une alarme unique pour le prochain `targetTime` (aujourd'hui ou demain). */
    fun schedule(context: Context, am: AlarmManager, type: AlarmType, targetTime: LocalTime) {
        val triggerAtMillis = nextTriggerMillis(targetTime)
        val pi = pendingIntent(context, type)
        // setAndAllowWhileIdle : bypass Doze, inexact (~9min drift max).
        // Pour passer à exact (drift < 1min) : besoin de SCHEDULE_EXACT_ALARM
        // permission en runtime sur Android 12+, c'est de la friction utilisateur
        // pour un gain marginal sur du rappel-repas. Inacceptable pour un réveil
        // matin, OK pour fitness.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    /**
     * Re-programme l'alarme [type] pour J+1 à la même heure. Appelé par le
     * receiver après dispatch, pour transformer une alarme one-shot en daily
     * recurring (AlarmManager n'a plus de "setRepeating" précis depuis API 19+).
     *
     * **On lit l'heure depuis le profil**, pas depuis l'alarme courante : si
     * l'user a changé son `lunchTime` entre 2 alarmes, on respecte la nouvelle
     * heure. Sinon on serait coincé sur l'horaire d'origine.
     */
    fun rescheduleNext(context: Context, type: AlarmType, profile: UserProfileEntity) {
        val time = type.timeFromProfile(profile) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        schedule(context, am, type, time)
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAll(context, am)
    }

    private fun cancelAll(context: Context, am: AlarmManager) {
        AlarmType.values().forEach { type ->
            am.cancel(pendingIntent(context, type))
        }
    }

    /**
     * Construit le PendingIntent canonique pour un type d'alarme. **Doit être
     * stable** entre programmation et annulation : même Intent.action + même
     * requestCode = même PendingIntent, sinon le cancel ne match pas et l'alarme
     * fuit.
     *
     * `FLAG_UPDATE_CURRENT` : si une alarme existe déjà pour ce requestCode,
     * on remplace le PendingIntent existant. `FLAG_IMMUTABLE` : Android 12+
     * requirement pour les PIs qui ne sont pas modifiés par un autre process.
     */
    private fun pendingIntent(context: Context, type: AlarmType): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TYPE, type.key)
        }
        return PendingIntent.getBroadcast(
            context,
            type.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Calcule le prochain timestamp de déclenchement pour [target] :
     *  - aujourd'hui à `target` si on n'a pas encore dépassé l'heure
     *  - demain à `target` sinon
     *
     * On utilise `ZoneId.systemDefault()` (heure locale) — l'user planifie son
     * petit-déj à 8h **local**, pas en UTC. Si l'user voyage et change de
     * timezone, le scheduler doit être réappelé (à mettre dans un futur
     * `TimezoneChangedReceiver` v2).
     */
    private fun nextTriggerMillis(target: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(target)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    enum class AlarmType(
        val key: String,
        val requestCode: Int,
        val workerType: String,
    ) {
        BREAKFAST("breakfast", 1001, ShredCoachNotificationWorker.TYPE_BREAKFAST),
        LUNCH("lunch", 1002, ShredCoachNotificationWorker.TYPE_LUNCH),
        SNACK("snack", 1003, ShredCoachNotificationWorker.TYPE_SNACK),
        DINNER("dinner", 1004, ShredCoachNotificationWorker.TYPE_DINNER),
        SHAKER_MORNING("shaker_morning", 1005, ShredCoachNotificationWorker.TYPE_SHAKER_MORNING),
        SHAKER_EVENING("shaker_evening", 1006, ShredCoachNotificationWorker.TYPE_SHAKER_EVENING),
        BEDTIME("bedtime", 1007, ShredCoachNotificationWorker.TYPE_BEDTIME),
        MOTIVATION("motivation", 1008, ShredCoachNotificationWorker.TYPE_MOTIVATION),
        MORNING_BRIEF("morning_brief", 1009, ShredCoachNotificationWorker.TYPE_MORNING_BRIEF),
        GLUCOSE_RECAP("glucose_recap", 1010, ShredCoachNotificationWorker.TYPE_GLUCOSE_RECAP);

        fun timeFromProfile(profile: UserProfileEntity): LocalTime? = when (this) {
            BREAKFAST -> profile.breakfastTime
            LUNCH -> profile.lunchTime
            SNACK -> profile.snackTime
            DINNER -> profile.dinnerTime
            SHAKER_MORNING -> profile.shakerMorningTime
            SHAKER_EVENING -> profile.shakerEveningTime
            BEDTIME -> profile.bedTime?.minusMinutes(30)
            MOTIVATION -> LocalTime.of(10, 0)
            MORNING_BRIEF -> LocalTime.of(7, 0)
            GLUCOSE_RECAP -> LocalTime.of(12, 17)
        }

        companion object {
            fun fromKey(key: String?): AlarmType? = values().firstOrNull { it.key == key }
        }
    }

    const val ACTION_FIRE = "com.shredcoach.app.notification.ALARM_FIRE"
    const val EXTRA_TYPE = "type"
}
