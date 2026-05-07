package com.shredcoach.app.domain.session

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.shredcoach.app.R
import com.shredcoach.app.domain.voice.ShreddyVoice
import com.shredcoach.app.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground Service qui maintient la séance ShredCoach active en background
 * et déclenche les feedbacks audio (countdown vocal + son fin de repos) +
 * vibration **même quand l'écran est éteint ou l'app en background**.
 *
 * **Pourquoi un Foreground Service** :
 * Sans ça, dès que l'Activity est détruite (écran éteint, app swipée),
 * Android peut tuer le process pour économiser la RAM. Tous les triggers
 * vocaux dans `LaunchedEffect` sont annulés. L'utilisateur en plein gainage
 * de 90s ne sait plus quand sa série se termine. **Bug critique** d'une app
 * fitness premium.
 *
 * Le Foreground Service garantit :
 *  - Process maintenu en vie (l'OS ne peut pas le killer)
 *  - Notification persistante "Séance en cours" (oblige par Android 8+)
 *  - Voix TTS audible en background (avec audio focus)
 *  - Vibration et son fonctionnels écran éteint
 *
 * **Ce qu'il observe** :
 *  - `ActiveSessionManager.session` StateFlow → reçoit les ticks chrono
 *  - Sur changement de `currentRestRemaining` : speakCountdown
 *  - Sur transition `isRestInProgress true → false` : vibration + son
 *  - Sur changement de `currentSetTimedRemaining` (set timed actif) : speakCountdown
 *  - Sur transition `currentSetTimedRemaining` → 0 (set timed terminé) : vibration + son
 *
 * **Ce qu'il NE fait PAS** :
 *  - La phrase contextuelle "C'est ta dernière série, encore 30s !" reste
 *    UI-only car elle nécessite des données ViewModel (currentSeries,
 *    totalSeriesForCurrentExercise, isWarmup…) que le service n'a pas. En
 *    background, le countdown est l'info critique ; les phrases coach
 *    contextuelles sont nice-to-have qu'on conserve pour le foreground.
 *
 * **Cycle de vie** :
 *  - Démarré par [ActiveSessionManager] quand une session démarre OU
 *    quand le restore post-cold-start trouve une session non-complétée.
 *  - Stoppé quand la session passe à null (terminée ou annulée).
 *
 * **AudioFocus** : on demande GAIN_TRANSIENT_MAY_DUCK → si l'user écoute
 * de la musique, elle baisse pendant le countdown et reprend après. Standard
 * pattern Android pour les apps de coaching audio (Strava, Nike Run Club).
 */
@AndroidEntryPoint
class WorkoutSessionService : Service() {

    @Inject lateinit var sessionManager: ActiveSessionManager
    @Inject lateinit var voice: ShreddyVoice

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundWithNotification(initialBody = "Séance active — chrono en cours")
        startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STICKY : si Android tue le service en cas de pression mémoire extrême,
        // il sera redémarré dès que possible avec un Intent null. À ce moment,
        // l'observerJob redémarre proprement et reconnect à sessionManager.
        return START_STICKY
    }

    private fun startObserving() {
        observerJob?.cancel()
        observerJob = scope.launch {
            // États précédents pour détecter les transitions (sinon on
            // re-déclenche en boucle à chaque emit du StateFlow). Les valeurs
            // initiales sont neutres : on n'émet rien tant qu'une vraie
            // transition n'a pas eu lieu.
            var prevRestRemaining = -1
            var prevWasRestActive = false
            var prevTimedRemaining = -1
            var prevTimedTotal = 0

            sessionManager.session.collect { session ->
                if (session == null) {
                    // Session terminée → on stop le service (qui retire la notif).
                    stopSelfSafely()
                    return@collect
                }
                updateNotification(session)

                // **Anti-double-fire** : si l'app est en foreground, l'UI gère
                // déjà la voix + vibration via ses LaunchedEffect. Le service
                // ne fire QUE en background pour éviter les doublons. Si l'user
                // ouvre l'app pendant un set, l'UI reprend le relais sans
                // overlap audio.
                val isAppInBackground = !ProcessLifecycleOwner.get().lifecycle
                    .currentState.isAtLeast(Lifecycle.State.STARTED)

                // ── Countdown vocal du repos (5, 3, 2, 1) ──
                val restRemaining = session.currentRestRemaining
                val isRestActive = session.isRestInProgress
                if (isAppInBackground && isRestActive && restRemaining != prevRestRemaining) {
                    requestAudioFocusBriefly()
                    voice.speakCountdown(restRemaining)
                }
                prevRestRemaining = restRemaining

                // ── Transition fin de repos : vibration ──
                if (isAppInBackground && prevWasRestActive && !isRestActive) {
                    vibrate()
                }
                prevWasRestActive = isRestActive

                // ── Countdown vocal pour les sets timed (gainage, etc.) ──
                val timedRemaining = session.currentSetTimedRemaining
                val timedTotal = session.currentSetTimedTotalSeconds
                val isTimedSet = timedTotal > 0 && session.isSetInProgress
                if (isAppInBackground && isTimedSet && timedRemaining != prevTimedRemaining) {
                    requestAudioFocusBriefly()
                    voice.speakCountdown(timedRemaining)
                }
                prevTimedRemaining = timedRemaining

                // ── Transition fin set timed : vibration ──
                val justFinishedTimed = timedTotal > 0 && timedRemaining == 0 && prevTimedTotal > 0
                if (isAppInBackground && justFinishedTimed) {
                    vibrate()
                }
                prevTimedTotal = timedTotal
            }
        }
    }

    private fun stopSelfSafely() {
        observerJob?.cancel()
        releaseAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseAudioFocus()
        super.onDestroy()
    }

    // ── Notification persistante ──────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Séance en cours",
                NotificationManager.IMPORTANCE_LOW, // pas de son, pas de heads-up
            ).apply {
                description = "Indicateur permanent quand une séance est active. Permet à ShredCoach de continuer le coach vocal en background."
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundWithNotification(initialBody: String) {
        val notif = buildNotification(initialBody)
        // Sur API 34+, on doit déclarer le foregroundServiceType côté startForeground.
        // 0 (FOREGROUND_SERVICE_TYPE_MANIFEST) lit la valeur du manifest, donc on
        // n'a rien à passer ici tant que le service est déclaré avec le bon type.
        startForeground(NOTIF_ID, notif)
    }

    private fun updateNotification(session: ActiveSessionManager.ActiveSession) {
        val body = when {
            session.isRestInProgress ->
                "Repos : ${session.currentRestRemaining}s — ${session.currentExerciseName}"
            session.currentSetTimedTotalSeconds > 0 && session.isSetInProgress ->
                "${session.currentExerciseName} — ${session.currentSetTimedRemaining}s restantes"
            session.isSetInProgress ->
                "${session.currentExerciseName} — série en cours"
            else ->
                "${session.currentExerciseName} (${session.currentExerciseIndex + 1}/${session.totalExercises})"
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIF_ID, buildNotification(body))
    }

    private fun buildNotification(body: String): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Séance ShredCoach")
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    // ── AudioFocus ──────────────────────────────────────────────────

    /**
     * Demande un audio focus transient + may-duck avant chaque speak. Permet
     * à la voix de coupler proprement avec une éventuelle musique en cours
     * (Spotify, podcast…) — la musique baisse pendant le countdown et reprend.
     *
     * **Pourquoi pas un focus permanent** : on parle 1-2s par tick, garder le
     * focus tout du long couperait l'audio musique pendant tout le repos,
     * frustrant. Le focus transient relâche dès que la voix est finie.
     */
    private fun requestAudioFocusBriefly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audioFocusRequest != null) return // un focus déjà en cours
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* no-op */ }
            .build()
        audioFocusRequest = req
        am.requestAudioFocus(req)
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // ── Vibration ──────────────────────────────────────────────────

    private fun vibrate() {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(220)
        }
    }

    companion object {
        private const val CHANNEL_ID = "shredcoach_session"
        private const val NOTIF_ID = 7_001

        /** Démarre le service. Idempotent — startForegroundService() ne lance pas un 2e onCreate. */
        fun start(context: Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkoutSessionService::class.java)
            context.stopService(intent)
        }
    }
}
