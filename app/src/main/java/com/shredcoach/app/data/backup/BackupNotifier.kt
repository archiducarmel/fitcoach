package com.shredcoach.app.data.backup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.notification.AppNotificationDispatcher
import com.shredcoach.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Émet les notifications de statut backup (succès silencieux + échec actionnable).
 *
 * **Politique** :
 *  - Succès → channel CHANNEL_BACKUP (IMPORTANCE_LOW) → pas de son, pas de
 *    bandeau heads-up. L'user verra la confirmation s'il ouvre la barre de
 *    notification, mais on ne le réveille pas à 3h du matin.
 *  - Échec → même channel mais titre "Sauvegarde impossible" + body
 *    actionnable ("Reconnecte ton compte Google"). Tap → ouvre l'app sur
 *    Settings → Sauvegarde via deeplink (à câbler côté MainActivity routing
 *    si on veut un atterrissage précis ; pour le MVP on ouvre juste l'app).
 *
 * **POST_NOTIFICATIONS** : Android 13+ exige une permission runtime. On check
 * silencieusement → si refusée, on log mais on n'crash pas (le backup a réussi
 * de toute façon, on ne veut pas casser ce signal pour une notif manquée).
 */
@Singleton
class BackupNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notifySuccess(photosCount: Int, sizeBytes: Long) {
        val sizeText = formatBytes(sizeBytes)
        post(
            id = NOTIF_ID_BACKUP,
            title = "Sauvegarde réussie",
            body = "$photosCount photos · $sizeText synchronisés sur Google Drive",
            ongoing = false,
        )
    }

    fun notifyFailure(reason: String) {
        post(
            id = NOTIF_ID_BACKUP,
            title = "Sauvegarde impossible",
            body = "$reason — touche pour vérifier",
            ongoing = false,
        )
    }

    private fun post(id: Int, title: String, body: String, ongoing: Boolean) {
        if (!hasNotificationPermission()) return
        // Tap notif → MainActivity reçoit EXTRA_DEEPLINK_ROUTE = "settings",
        // le NavHost route alors directement sur l'onglet Réglages où la
        // section Sauvegarde est immédiatement visible. Le requestCode est
        // unique (NOTIF_ID_BACKUP) pour éviter qu'il collide avec d'autres
        // PendingIntents de l'app (qui utilisent souvent 0).
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(AppNotificationDispatcher.EXTRA_DEEPLINK_ROUTE, "settings")
        }
        val openApp = PendingIntent.getActivity(
            context,
            NOTIF_ID_BACKUP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, ShredCoachApplication.CHANNEL_BACKUP)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        NotificationManagerCompat.from(context).notify(id, notif)
    }

    private fun hasNotificationPermission(): Boolean {
        // Pré-Android 13, permission accordée at install time.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes} o"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.0f Ko".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f Mo".format(mb)
        return "%.2f Go".format(mb / 1024.0)
    }

    private companion object {
        // ID stable → les notifs successives écrasent la précédente plutôt
        // que d'empiler. L'user voit toujours le dernier statut.
        const val NOTIF_ID_BACKUP = 9_001
    }
}
