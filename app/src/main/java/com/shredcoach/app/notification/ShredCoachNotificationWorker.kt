package com.shredcoach.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.notification.BedtimeBuilder
import com.shredcoach.app.domain.notification.BreakfastBuilder
import com.shredcoach.app.domain.notification.DinnerBuilder
import com.shredcoach.app.domain.notification.GlucoseRecapBuilder
import com.shredcoach.app.domain.notification.LunchBuilder
import com.shredcoach.app.domain.notification.MorningBriefBuilder
import com.shredcoach.app.domain.notification.MotivationBuilder
import com.shredcoach.app.domain.notification.NotifDecision
import com.shredcoach.app.domain.notification.NotificationContextEngine
import com.shredcoach.app.domain.notification.ShakerEveningBuilder
import com.shredcoach.app.domain.notification.ShakerMorningBuilder
import com.shredcoach.app.domain.notification.SnackBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Worker exécuté par [NotificationAlarmReceiver] à chaque tick d'alarme.
 *
 * **Pipeline V2 (context-aware)** :
 *  1. Vérifie le toggle global `notificationsEnabled` + le toggle par-type.
 *  2. Construit un [UserContextSnapshot] via [NotificationContextEngine].
 *  3. Délègue à un builder spécifique qui retourne [NotifDecision.Send] ou
 *     [NotifDecision.Skip] selon les règles contextuelles.
 *  4. Si SEND, dispatch via [AppNotificationDispatcher] (DB + push système).
 *
 * **Pourquoi pas de fallback sur l'ancien comportement** : la mémoïsation
 * `remember(features)` dans les builders garantit qu'on a toujours un body
 * — soit dynamique soit le default existant (rétro-compat via les strings
 * historiques utilisés en cas de match aucune règle spécifique).
 */
@HiltWorker
class ShredCoachNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val contextEngine: NotificationContextEngine,
    private val dispatcher: AppNotificationDispatcher,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.failure()
        val profile = userRepository.getUserProfileOnce()

        if (profile?.notificationsEnabled != true) return Result.success()

        // Map type → toggle profil. Si le toggle est OFF, skip avant même de
        // construire le snapshot (économise les queries DAO).
        val enabled = when (type) {
            TYPE_BREAKFAST -> profile.notifBreakfast
            TYPE_LUNCH -> profile.notifLunch
            TYPE_SNACK -> profile.notifSnack
            TYPE_DINNER -> profile.notifDinner
            TYPE_SHAKER_MORNING -> profile.notifShaker
            TYPE_SHAKER_EVENING -> profile.notifShaker
            TYPE_BEDTIME -> profile.notifBedtime
            TYPE_MOTIVATION -> profile.notifMotivation
            TYPE_MORNING_BRIEF -> profile.notifMotivation // réutilise le toggle motivation pour V1
            TYPE_GLUCOSE_RECAP -> profile.notifGlucoseRecap
            else -> false
        }
        if (!enabled) return Result.success()

        // Snapshot une seule fois — partagé par tous les builders potentiels.
        val snapshot = contextEngine.snapshot() ?: return Result.success()

        // Dispatch sur le bon builder selon type.
        val notifType = when (type) {
            TYPE_BREAKFAST, TYPE_LUNCH, TYPE_SNACK, TYPE_DINNER -> NotifType.MEAL_REMINDER
            TYPE_SHAKER_MORNING, TYPE_SHAKER_EVENING -> NotifType.SHAKER_REMINDER
            TYPE_BEDTIME -> NotifType.BEDTIME_REMINDER
            TYPE_MOTIVATION, TYPE_MORNING_BRIEF -> NotifType.MOTIVATION
            TYPE_GLUCOSE_RECAP -> NotifType.MOTIVATION // pas de NotifType dédié, réutilise MOTIVATION (catégorie "coach proactif")
            else -> return Result.success()
        }

        val decision = when (type) {
            TYPE_BREAKFAST -> BreakfastBuilder.build(context, snapshot)
            TYPE_LUNCH -> LunchBuilder.build(context, snapshot)
            TYPE_SNACK -> SnackBuilder.build(context, snapshot)
            TYPE_DINNER -> DinnerBuilder.build(context, snapshot)
            TYPE_SHAKER_MORNING -> ShakerMorningBuilder.build(context, snapshot)
            TYPE_SHAKER_EVENING -> ShakerEveningBuilder.build(context, snapshot)
            TYPE_BEDTIME -> BedtimeBuilder.build(context, snapshot)
            TYPE_MOTIVATION -> MotivationBuilder.build(context, snapshot)
            TYPE_MORNING_BRIEF -> MorningBriefBuilder.build(context, snapshot)
            TYPE_GLUCOSE_RECAP -> GlucoseRecapBuilder.build(context, snapshot)
            else -> return Result.success()
        }

        when (decision) {
            is NotifDecision.Skip -> {
                // Skip silencieux. La raison est dans `decision.reason` pour
                // debug (non poussée à l'utilisateur).
                return Result.success()
            }
            is NotifDecision.Send -> {
                dispatcher.dispatch(
                    notifType,
                    decision.title,
                    decision.body,
                    decision.channelId,
                    deeplink = decision.deeplink,
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_LUNCH = "lunch"
        const val TYPE_SNACK = "snack"
        const val TYPE_DINNER = "dinner"
        const val TYPE_SHAKER_MORNING = "shaker_morning"
        const val TYPE_SHAKER_EVENING = "shaker_evening"
        const val TYPE_BEDTIME = "bedtime"
        const val TYPE_MOTIVATION = "motivation"
        const val TYPE_MORNING_BRIEF = "morning_brief"
        const val TYPE_GLUCOSE_RECAP = "glucose_recap"
    }
}
