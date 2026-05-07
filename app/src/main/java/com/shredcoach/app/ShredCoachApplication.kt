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
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.notification.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShredCoachApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var shreddyVoice: com.shredcoach.app.domain.voice.ShreddyVoice
    @Inject lateinit var userRepository: UserRepository

    /**
     * Scope long-vie pour les bootstrap tasks (rescheduling alarmes au cold-start).
     * SupervisorJob → une exception sur une tâche bootstrap ne casse pas les autres.
     */
    private val bootstrapScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        // Re-programme les alarmes de notif au cold-start. Idempotent
        // (NotificationAlarmScheduler.scheduleAll cancel-puis-reschedule). Couvre
        // 3 scénarios : (1) première install après onboarding (2) reboot device
        // déjà couvert par BootReceiver, mais ceinture+bretelles si receiver
        // refuse de tirer (ex: app non encore lancée post-reboot et user ouvre
        // l'app — alors c'est ici qu'on rattrape) (3) update Play Store (idem
        // BootReceiver via MY_PACKAGE_REPLACED, fallback ici).
        bootstrapScope.launch {
            try {
                // **Migration v1→v2 du système de notif** : avant on utilisait
                // `PeriodicWorkRequest` 24h tagué "shredcoach_notif". Sur les
                // installs existantes, ces workers vivent dans la DB WorkManager
                // et continueraient à fire EN PLUS des nouvelles alarmes →
                // duplication. On les cancel une fois pour toutes ici. C'est un
                // no-op pour les nouvelles installs, et idempotent pour les
                // upgrades (next runs : tag inexistant → cancel = no-op).
                androidx.work.WorkManager.getInstance(this@ShredCoachApplication)
                    .cancelAllWorkByTag("shredcoach_notif")
                val profile = userRepository.getUserProfileOnce() ?: return@launch
                NotificationScheduler.scheduleAll(this@ShredCoachApplication, profile)
            } catch (t: Throwable) {
                android.util.Log.e("ShredCoachApp", "Cold-start alarm reschedule failed", t)
            }
        }
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
            // Cache mémoire — bitmaps décodés. 25% de la heap = défaut Coil ;
            // explicite ici pour signaler l'intention.
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            // Cache disque — GIFs téléchargés depuis GitHub Releases (cf.
            // [GifUrlResolver]). 256MB = headroom pour ~500 GIFs × ~500KB,
            // mais Coil purge LRU automatiquement, l'usage réel reste bas.
            // Cache survit aux app restarts → premier load = 1 fetch réseau,
            // ensuite tout vient du disque (offline-capable après ouverture).
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            // Toujours cacher (mémoire + disque) — pas de raison de revalidate
            // chaque GIF (le filename = identifiant unique, immuable).
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            // Crossfade global 200ms — transitions douces sans configurer
            // chaque AsyncImage individuellement.
            .crossfade(true)
            .crossfade(200)
            // Respecter les en-têtes Cache-Control de GitHub (no-revalidate
            // par défaut, donc on évite des HEAD inutiles).
            .respectCacheHeaders(false)
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
                    .apply { description = "Débriefs personnalisés IA après repas et séances" },
                // Cloud backup : informationnel, IMPORTANCE_LOW = pas de son ni de
                // bandeau intrusif. L'user veut savoir que sa sauvegarde a réussi
                // sans être réveillé par une notif sonore à 3h du matin. En cas
                // d'échec on monte à DEFAULT pour qu'il puisse intervenir.
                NotificationChannel(CHANNEL_BACKUP, "Sauvegarde cloud", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Statut des sauvegardes Google Drive (succès et erreurs)" }
            ))
        }
    }

    companion object {
        const val CHANNEL_MEALS = "shredcoach_meals"
        const val CHANNEL_WORKOUT = "shredcoach_workout"
        const val CHANNEL_BEDTIME = "shredcoach_bedtime"
        const val CHANNEL_DEBRIEF = "shredcoach_debrief"
        const val CHANNEL_BACKUP = "shredcoach_backup"
    }
}
