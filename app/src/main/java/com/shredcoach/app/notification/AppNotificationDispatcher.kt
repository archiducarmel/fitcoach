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
 *   2. Poste la push system
 *
 * Tous les Workers passent par ici pour garantir que l'inbox reste synchronisée
 * avec les notifications réellement envoyées.
 */
@Singleton
class AppNotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppNotificationRepository
) {

    /**
     * Sauvegarde + poste une notification.
     * @param type Type de notification (pour le canal + icône + groupement)
     * @param title Titre affiché
     * @param body Corps du message
     * @param channelId ID du canal Android (voir ShredCoachApplication.CHANNEL_*)
     * @param source "llm" si généré par IA, "local" sinon
     */
    suspend fun dispatch(
        type: NotifType,
        title: String,
        body: String,
        channelId: String,
        source: String = "local",
        deeplink: String? = null
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
            body = body
        )
    }

    private fun postSystemNotification(channelId: String, notifId: Int, title: String, body: String) {
        // Vérifier la permission POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return
        }

        // Intent qui ouvre MainActivity → route Notifications
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    companion object {
        const val EXTRA_OPEN_NOTIFICATIONS = "open_notifications"
        /** Offset pour éviter les collisions avec les IDs hardcodés de l'ancien worker (1001-1010). */
        private const val BASE_NOTIF_ID = 2000
    }
}
