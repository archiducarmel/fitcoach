package com.shredcoach.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reçoit le broadcast déclenché par [NotificationAlarmScheduler] quand une
 * alarme se déclenche. Deux responsabilités :
 *
 * 1. **Dispatch immédiat** : enqueue un [ShredCoachNotificationWorker] qui
 *    poste la notification correspondante. On délègue à WorkManager (vs
 *    appeler `dispatcher.dispatch()` directement dans le receiver) parce que
 *    le worker contient déjà toute la logique de routing par type +
 *    vérification du toggle individuel (`profile.notifBreakfast` etc.) +
 *    lookup DB pour la motivation conditionnelle.
 *
 * 2. **Re-scheduling** : programme la même alarme à T+24h via
 *    [NotificationAlarmScheduler.rescheduleNext]. Sans ça on aurait du
 *    one-shot, plus de notif quotidienne. AlarmManager n'a plus de
 *    `setRepeating` précis depuis API 19+ (intervalle déterministe = exact
 *    + Doze-bypass impossible), donc le pattern moderne est "one-shot +
 *    re-schedule à chaque tir".
 *
 * **goAsync** : `BroadcastReceiver.onReceive` doit retourner sous 10s, et le
 * process peut être tué juste après. `goAsync()` réserve un PendingResult qui
 * empêche la mort du process tant qu'on appelle pas `finish()`. Critique ici
 * car on fait du DB I/O (lecture profile pour reschedule) qui peut prendre
 * 100-200ms en cold start.
 */
@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var userRepository: UserRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationAlarmScheduler.ACTION_FIRE) return
        val typeKey = intent.getStringExtra(NotificationAlarmScheduler.EXTRA_TYPE) ?: return
        val type = NotificationAlarmScheduler.AlarmType.fromKey(typeKey) ?: return

        val pending = goAsync()
        // Scope custom (pas viewModelScope ou applicationScope) car on est dans
        // un BroadcastReceiver — pas de lifecycle. SupervisorJob pour qu'une
        // erreur sur reschedule ne fasse pas crasher le dispatch principal.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // 1. Dispatch immédiat via worker (contient déjà toute la logique
                //    par type + check toggles individuels)
                val data = Data.Builder().putString("type", type.workerType).build()
                val request = OneTimeWorkRequestBuilder<ShredCoachNotificationWorker>()
                    .setInputData(data)
                    .addTag("shredcoach_alarm_dispatch")
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "shredcoach_dispatch_${type.key}_${System.currentTimeMillis()}",
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request,
                )

                // 2. Reschedule J+1 (en lisant le profil pour respecter un
                //    éventuel changement d'heure entre-temps)
                val profile = userRepository.getUserProfileOnce()
                if (profile != null) {
                    NotificationAlarmScheduler.rescheduleNext(context, type, profile)
                } else {
                    Log.w(TAG, "Pas de profil — alarme $type non rescheduée")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Erreur dispatch alarme $type", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "NotifAlarmReceiver"
    }
}
