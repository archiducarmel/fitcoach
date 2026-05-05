package com.shredcoach.app.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shredcoach.app.data.local.entity.AppNotificationEntity
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.repository.AppNotificationRepository
import com.shredcoach.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée unique pour poster une notification :
 *   1. Sauvegarde en DB (visible dans l'inbox)
 *   2. Poste la push system avec deeplink + actions optionnels
 *
 * Tous les Workers passent par ici pour garantir que l'inbox reste synchronisée
 * avec les notifications réellement envoyées.
 *
 * **Deeplinks** : si [dispatch] reçoit un `deeplink` non null, le tap sur la
 * notif ouvre directement la route Compose correspondante (ex: "workout_session/42")
 * via [MainActivity] qui lit [EXTRA_DEEPLINK_ROUTE]. Sinon, fallback sur l'inbox.
 *
 * **Actions** : jusqu'à 3 boutons via [NotificationAction]. Android limite à 3
 * actions visibles avant collapse. Chaque action ouvre une route comme un
 * deeplink — pas de BroadcastReceiver, ce qui garde l'app comme source de vérité
 * pour la navigation et évite des classes Receiver à enregistrer au manifest.
 */
@Singleton
class AppNotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppNotificationRepository
) {

    /**
     * Une action affichée sur la notification (bouton).
     * @param label Texte du bouton (max ~25 chars affichés selon launcher)
     * @param deeplinkRoute Route Compose ouverte au tap (ex: "workout_generator")
     * @param iconRes Drawable optionnel ; null = pas d'icône (système met le
     *   placeholder par défaut, OK sur Android 12+).
     */
    data class NotificationAction(
        val label: String,
        val deeplinkRoute: String,
        val iconRes: Int? = null,
    )

    /**
     * Sauvegarde + poste une notification.
     */
    suspend fun dispatch(
        type: NotifType,
        title: String,
        body: String,
        channelId: String,
        source: String = "local",
        deeplink: String? = null,
        actions: List<NotificationAction> = emptyList(),
    ) {
        // 1. Sauver en DB (→ inbox)
        val entity = AppNotificationEntity(
            type = type.name,
            title = title,
            body = body,
            source = source,
            deeplink = deeplink
        )
        val id = repository.insert(entity)

        // 2. Poster la push system (avec le rowId comme notif id pour unicité)
        postSystemNotification(
            channelId = channelId,
            notifId = (id.toInt().coerceAtLeast(1)).let { it + BASE_NOTIF_ID },
            title = title,
            body = body,
            deeplink = deeplink,
            actions = actions,
        )
    }

    private fun postSystemNotification(
        channelId: String,
        notifId: Int,
        title: String,
        body: String,
        deeplink: String?,
        actions: List<NotificationAction>,
    ) {
        // Vérifier la permission POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildContentIntent(notifId, deeplink))
            .setAutoCancel(true)

        // Ajouter les boutons d'action (max 3 sont affichés par Android avant collapse)
        actions.take(3).forEachIndexed { index, action ->
            builder.addAction(
                action.iconRes ?: 0,
                action.label,
                buildActionPendingIntent(notifId, index, action.deeplinkRoute),
            )
        }

        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    /**
     * Intent du tap principal — soit deeplink direct, soit fallback inbox.
     * Le requestCode = notifId garantit l'unicité (sinon Android réutiliserait
     * le PendingIntent et le `extra` ne serait pas mis à jour).
     */
    private fun buildContentIntent(notifId: Int, deeplink: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deeplink != null) {
                putExtra(EXTRA_DEEPLINK_ROUTE, deeplink)
            } else {
                putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
            }
        }
        return PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Intent d'un bouton d'action — chaque bouton ouvre une route distincte.
     * RequestCode unique par (notifId, index) pour ne pas écraser les autres boutons.
     */
    private fun buildActionPendingIntent(notifId: Int, actionIndex: Int, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEPLINK_ROUTE, route)
        }
        // requestCode unique : notifId * 10 + actionIndex permet 10 actions max
        // par notif (largement plus que les 3 visibles).
        val requestCode = notifId * 10 + actionIndex
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        /** Route Compose à ouvrir au tap (notif principale ou bouton d'action). */
        const val EXTRA_DEEPLINK_ROUTE = "deeplink_route"
        /** Offset pour éviter les collisions avec les IDs hardcodés de l'ancien worker (1001-1010). */
        private const val BASE_NOTIF_ID = 2000
    }
}
