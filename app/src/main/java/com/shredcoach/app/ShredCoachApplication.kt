package com.shredcoach.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShredCoachApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var shreddyVoice: com.shredcoach.app.domain.voice.ShreddyVoice

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        shreddyVoice.init(this)
        // Note : la migration des clés API Room → SecureKeyStore est désormais
        // faite atomiquement par la Migration v33→v34 (cf. Migrations.kt),
        // garantissant qu'aucune clé n'est perdue même si l'utilisateur
        // saute des versions intermédiaires.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(CHANNEL_MEALS, "Rappels repas", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Rappels pour les repas et shakers protéines" },
                NotificationChannel(CHANNEL_WORKOUT, "Rappels séances", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Motivation et rappels de séances" },
                NotificationChannel(CHANNEL_BEDTIME, "Rappel coucher", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Rappel pour aller dormir" },
                NotificationChannel(CHANNEL_DEBRIEF, "Débriefs Shreddy", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Débriefs personnalisés IA après repas et séances" }
            ))
        }
    }

    companion object {
        const val CHANNEL_MEALS = "shredcoach_meals"
        const val CHANNEL_WORKOUT = "shredcoach_workout"
        const val CHANNEL_BEDTIME = "shredcoach_bedtime"
        const val CHANNEL_DEBRIEF = "shredcoach_debrief"
    }
}
