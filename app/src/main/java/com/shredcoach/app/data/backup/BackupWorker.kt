package com.shredcoach.app.data.backup

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
import com.shredcoach.app.data.backup.provider.ProviderId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Worker WorkManager qui exécute un backup automatique périodique.
 *
 * **Cadence** : périodique 24h, premier déclenchement à la prochaine
 * occurrence de [PREFERRED_HOUR_LOCAL]:00 locale (3h du matin par défaut).
 * Choix de 3h : utilisateur endormi → fenêtre de race minimale entre lecture
 * des tables, charge CPU non-perceptible, batterie probablement en charge la
 * nuit (constraint optionnelle).
 *
 * **Contraintes** :
 * - `BatteryNotLow` : pas de backup si batterie < 15%, attend la charge.
 * - `requiresStorageNotLow` : pas de backup si storage interne < 10%, sinon
 *   l'écriture du ZIP intermediaire pourrait échouer en cours.
 * - **Pas** de `requiresCharging` ni de `requiredNetworkType.CONNECTED` :
 *   le user a peut-être pointé son backup vers un dossier local. Dans ce cas
 *   pas besoin de réseau, et imposer le charging fragmenterait l'expérience
 *   sur les téléphones jamais chargés à 3h.
 *
 * **Backoff** : exponentiel base 30s, plafond système 5h (cf. WorkManager).
 * Si trois nuits consécutives échouent, on reste périodique 24h après ça —
 * pas de scheduling chirurgical de retry, on s'en remet à la prochaine
 * fenêtre quotidienne.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: BackupRepository,
    private val settings: BackupSettingsStore,
    private val notifier: BackupNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Honorer le toggle utilisateur même si le worker est encore enqueued
        // — évite les backups après que l'utilisateur ait désactivé le feature.
        val snap = settings.snapshot.first()
        if (!snap.autoBackupEnabled) {
            Log.d(TAG, "Auto-backup disabled — skipping run")
            return Result.success()
        }
        // Pre-flight provider-aware : SAF a besoin d'un folderUri, Drive d'un
        // compte linké (vérifié par le provider lui-même via isConfigured()).
        // Pour Drive on ne peut pas faire le check ici (besoin du repository),
        // donc on délègue : si non-configuré, runBackup() retourne Failure
        // immédiatement avec un message explicite.
        if (snap.providerId == ProviderId.LOCAL_SAF && snap.folderUri == null) {
            Log.d(TAG, "SAF auto-backup but no folder configured — skipping run")
            return Result.success()
        }

        return when (val result = repository.runBackup()) {
            is BackupRepository.BackupResult.Success -> {
                Log.i(TAG, "Backup périodique OK : ${result.fileName}")
                notifier.notifySuccess(photosCount = result.photosCount, sizeBytes = result.sizeBytes)
                Result.success()
            }
            is BackupRepository.BackupResult.Failure -> {
                Log.w(TAG, "Backup périodique échoué : ${result.message} — retry")
                // On notifie SEULEMENT si le retry est probablement vain
                // (auth expirée → l'user doit intervenir). Pour les erreurs
                // transitoires (réseau, quota momentané), on laisse le backoff
                // faire son boulot sans spam de notifs.
                if (looksUnrecoverable(result.message)) {
                    notifier.notifyFailure(result.message)
                }
                Result.retry()
            }
        }
    }

    /**
     * Hint à propos des erreurs : si le message évoque une intervention user
     * (token expiré, déconnexion), on notifie pour qu'il reconnecte. Pour les
     * erreurs réseau/quota, on suppose que le retry suivant passera.
     */
    private fun looksUnrecoverable(message: String): Boolean {
        val lower = message.lowercase()
        return "expir" in lower || "reconnec" in lower || "non config" in lower || "non configuré" in lower
    }

    companion object {
        private const val TAG = "BackupWorker"
        const val UNIQUE_NAME = "shredcoach_backup_periodic"
        private const val PREFERRED_HOUR_LOCAL = 3

        /**
         * Enqueue (ou re-enqueue) le worker périodique. Idempotent —
         * [ExistingPeriodicWorkPolicy.UPDATE] remplace la spec si elle existe
         * déjà, sans dupliquer d'instances.
         *
         * À appeler depuis :
         * - SettingsViewModel quand l'utilisateur active "auto-backup"
         * - Au démarrage de l'app si snap.autoBackupEnabled = true (re-attache
         *   le worker après une réinstallation)
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySecondsTo(PREFERRED_HOUR_LOCAL), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Annule le worker périodique. À appeler sur désactivation du feature. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /**
         * Calcule le délai (en secondes) jusqu'à la prochaine occurrence locale
         * de [hour]:00. Si on est déjà passé pour aujourd'hui → demain.
         *
         * Pourquoi pas un AlarmManager exact pour 3h pile : WorkManager
         * jitter +/- 10 minutes pour grouper les wakeups → meilleure batterie.
         * À 3h du matin, ce jitter est imperceptible.
         */
        private fun initialDelaySecondsTo(hour: Int): Long {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            var target = now.with(LocalTime.of(hour, 0))
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).seconds.coerceAtLeast(60)
        }
    }
}

