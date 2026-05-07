package com.shredcoach.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-programme toutes les alarmes de notification au boot du device.
 *
 * **Pourquoi c'est critique** : les alarmes posées via [android.app.AlarmManager]
 * sont **wipées par l'OS au reboot** (cf. doc AlarmManager : "Registered alarms
 * are retained while the device is asleep but will be cleared if it is turned
 * off and rebooted"). Sans ce receiver, après chaque redémarrage du téléphone,
 * l'utilisateur ne reçoit plus AUCUNE notif jusqu'à ce qu'il ouvre l'app.
 *
 * **Permissions** : `RECEIVE_BOOT_COMPLETED` doit être déclarée dans le manifest.
 * Le receiver doit aussi être déclaré (pas registered programmatiquement) avec
 * `android:exported="true"` car le broadcast `BOOT_COMPLETED` vient du système.
 *
 * **Edge cases couverts** :
 *  - `BOOT_COMPLETED` : reboot normal
 *  - `MY_PACKAGE_REPLACED` : update de l'app via Play Store (l'app est
 *    relancée avec le nouveau code mais les alarmes du vieux APK sont perdues)
 *  - `LOCKED_BOOT_COMPLETED` : Direct Boot mode (entre boot et premier unlock
 *    user) — non supporté ici, on n'en a pas besoin pour des reminders fitness
 *
 * **Délai** : on n'attend PAS que l'user déverrouille son téléphone. Les alarmes
 * sont reprogrammées immédiatement → premier rappel suivant arrive à l'heure
 * habituelle même si user n'a pas encore touché son tel.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var userRepository: UserRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
                -> reprogramAlarms(context)
            else -> Unit
        }
    }

    private fun reprogramAlarms(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val profile = userRepository.getUserProfileOnce()
                if (profile == null) {
                    Log.i(TAG, "Boot reschedule: pas de profil (onboarding non fait), skip")
                    return@launch
                }
                NotificationAlarmScheduler.scheduleAll(context, profile)
                Log.i(TAG, "Boot reschedule: ${profile.notifBreakfast},${profile.notifLunch}…")
            } catch (t: Throwable) {
                Log.e(TAG, "Boot reschedule failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
