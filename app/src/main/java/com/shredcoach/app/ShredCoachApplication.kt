package com.shredcoach.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ShredCoachApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var shreddyVoice: com.shredcoach.app.domain.voice.ShreddyVoice

    override fun onCreate() {
        // StrictMode AVANT super.onCreate() pour capturer les violations
        // dès le démarrage de l'app (init Hilt, init lazy injects, etc.).
        // Activé UNIQUEMENT en debug — penaltyLog (logs uniquement, pas de
        // crash) pour ne pas bloquer le dev. À surveiller via `adb logcat
        // | grep StrictMode` pour identifier les disk I/O / network sur main
        // thread, leaked closables, leaked SQLite cursors, etc.
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        super.onCreate()
        createNotificationChannels()
        shreddyVoice.init(this)
        // Worker quotidien qui resync UserProfile.currentStreakDays avec la
        // vérité dérivée des logs. Idempotent (UPDATE policy) → safe à appeler
        // à chaque cold start.
        com.shredcoach.app.domain.streak.StreakUpdateWorker.enqueue(this)
        // Note : la migration des clés API Room → SecureKeyStore est désormais
        // faite atomiquement par la Migration v33→v34 (cf. Migrations.kt),
        // garantissant qu'aucune clé n'est perdue même si l'utilisateur
        // saute des versions intermédiaires.
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                // detectResourceMismatches — API 23+ : décodage drawable
                // sur main thread quand la couche calque est lourde.
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        detectResourceMismatches()
                    }
                }
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()      // curseurs Room non-fermés
                .detectLeakedClosableObjects()     // fichiers/streams non-fermés
                .detectLeakedRegistrationObjects() // BroadcastReceivers leakés
                .detectActivityLeaks()             // Activities non-recyclées
                // detectFileUriExposure — désactivé : Coil/MediaStore
                // génère des file:// volontairement dans certains contextes.
                .penaltyLog()
                .build()
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * ImageLoader global de Coil — Coil l'auto-découvre via l'interface
     * [ImageLoaderFactory] sur l'Application, donc tous les `AsyncImage` /
     * `SubcomposeAsyncImage` de l'app utilisent ce loader sans config locale.
     *
     * **Pourquoi central** : avant ce setup, chaque call site appelait
     * `.decoderFactory(ImageDecoderDecoder.Factory())` à la main. Mais
     * `ImageDecoderDecoder` requiert API 28+ → crash sur Android 8.0/8.1
     * (~1.5% des users en 2026). Ici on choisit le bon decoder selon l'API.
     *
     * - **API 28+ (Android 9+)** : `ImageDecoderDecoder` — décodeur natif
     *   plus rapide, support GIF + WebP animé + AVIF.
     * - **API 26-27 (Android 8.x)** : fallback `GifDecoder` — décodeur Java
     *   plus lent mais fonctionnel pour les GIFs (le seul format animé qu'on
     *   utilise dans nos assets).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
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
