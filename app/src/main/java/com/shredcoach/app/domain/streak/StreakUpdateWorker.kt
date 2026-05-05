package com.shredcoach.app.domain.streak

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Worker quotidien qui resynchronise [UserProfileEntity.currentStreakDays] avec
 * la vérité calculée par [StreakService] depuis les logs.
 *
 * **Pourquoi ce worker existe** : sans lui, le streak persisté divergerait de
 * la réalité dès que l'utilisateur supprime une séance, change la date d'un
 * log via le calendrier, ou laisse passer un jour. Recalculer depuis les logs
 * reste la source de vérité ; la persistance n'est qu'un cache pour les
 * surfaces qui ne peuvent pas se permettre de scanner l'historique
 * (notifications coach, debriefs séance).
 *
 * **Cadence** : 24h, ancré à 23h45 — assez tard pour capturer une séance
 * tardive du jour, assez tôt pour mettre à jour la valeur AVANT la coach
 * notif du lendemain matin (ProactiveCoachWorker tourne entre 8h et 21h30).
 *
 * **Idempotent** : recalcul + update sans side-effect notification ; un retry
 * (réseau down, batterie low) ne produira pas de double-événement.
 */
@HiltWorker
class StreakUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val workoutLogDao: WorkoutLogDao,
    private val userRepository: UserRepository,
    private val streakService: StreakService,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val profile = userRepository.getUserProfileOnce() ?: run {
            Log.i(TAG, "Skip : aucun profil utilisateur")
            return Result.success()
        }
        val logs = workoutLogDao.getAllWorkoutLogs().first().filter { it.completed }
        val state = streakService.compute(logs)

        if (profile.currentStreakDays != state.currentDays) {
            userRepository.updateStreak(state.currentDays)
            Log.i(
                TAG,
                "Streak mis à jour : ${profile.currentStreakDays} → ${state.currentDays} " +
                    "(best=${state.bestDays}, hasWorkedOutToday=${state.hasWorkedOutToday})"
            )
        } else {
            Log.i(TAG, "Streak inchangé (${state.currentDays}j) — pas d'écriture")
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "shredcoach_streak_update"
        private const val TAG = "StreakUpdateWorker"
        private val TARGET_TIME: LocalTime = LocalTime.of(23, 45)

        /**
         * Enrôle le worker en periodic 24h. Idempotent (UPDATE policy) —
         * appelable au démarrage de l'app sans risque de doublon.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<StreakUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelaySecondsToTarget(), TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun initialDelaySecondsToTarget(): Long {
            val now = LocalDateTime.now(ZoneId.systemDefault())
            var target = LocalDate.now().atTime(TARGET_TIME)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).seconds.coerceAtLeast(60)
        }
    }
}
