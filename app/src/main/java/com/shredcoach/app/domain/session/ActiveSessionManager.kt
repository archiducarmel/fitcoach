package com.shredcoach.app.domain.session

import android.content.Context
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Singleton qui gère l'état de la séance active.
 *
 * **Persistance & robustesse** : le chrono est ancré à l'horloge murale via
 * [ActiveSession.startedAt]. Cela garantit qu'une séance reste reprenable même
 * après un kill du process Android (ce que la simple coroutine in-memory ne
 * permettait pas — cf. bug "session reset après plusieurs minutes en arrière-plan").
 *
 * À l'app start, [tryRestoreFromDb] consulte le dernier log non-complété (<24h)
 * et recrée la session active automatiquement → la bannière "séance en cours"
 * et la card "Reprendre" reflètent fidèlement l'état persisté.
 */
@Singleton
class ActiveSessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    /**
     * Provider pour casser le cycle d'init (le repo dépend du DAO qui dépend de la
     * DB qui est provided dans le même graph Singleton). Le Provider permet de
     * différer la résolution au premier appel de [tryRestoreFromDb].
     */
    private val workoutRepositoryProvider: Provider<WorkoutRepository>
) {

    data class ActiveSession(
        val workoutLogId: Long = 0,
        /** Horloge murale du démarrage — ancre durable du chrono à travers process death. */
        val startedAt: LocalDateTime = LocalDateTime.now(),
        val globalChronoSeconds: Long = 0,
        val isRunning: Boolean = false,
        val currentExerciseName: String = "",
        val currentExerciseIndex: Int = 0,
        /**
         * Horloge murale du début de l'exo courant. Re-stampée par
         * [updateExerciseInfo] uniquement quand on change d'exo (mêmes name+index
         * → on garde le timestamp). Permet au chrono d'exo de survivre aux
         * navigations (sortir+revenir sur l'écran session) et au process death
         * (persistée dans `workout_logs.currentExerciseStartedAt`).
         */
        val currentExerciseStartedAt: LocalDateTime = LocalDateTime.now(),
        val currentExerciseSeconds: Long = 0,
        val totalExercises: Int = 0,
        /** True si l'état a été reconstruit depuis la DB après un cold-start. */
        val restoredFromDb: Boolean = false,
        // ─── Série en cours ──────────────────────────────────────────────
        /**
         * Wall-clock du `Démarrer la série`. Null = aucune série en cours.
         * Persistée en DB → survit au cold-start. Utilisée pour calculer
         * [currentSetSeconds] et [currentSetTimedRemaining] via le tick.
         */
        val currentSetStartedAt: LocalDateTime? = null,
        /** Durée cible pour les exos chronométrés (gainage, etc.). 0 = pas timed. */
        val currentSetTimedTotalSeconds: Int = 0,
        /** Élapsed depuis le démarrage de la série (0 si pas en cours). */
        val currentSetSeconds: Long = 0,
        /** Pour les sets timed : secondes restantes avant auto-validation. */
        val currentSetTimedRemaining: Int = 0,
        // ─── Repos entre séries ───────────────────────────────────────────
        /**
         * Wall-clock cible de fin du repos. Null = pas de repos en cours.
         * Persisté en DB → le décompte continue correctement après navigation
         * et cold-start (`remaining = max(0, endsAt - now)`).
         */
        val currentRestEndsAt: LocalDateTime? = null,
        /** Durée totale du repos (pour calculer elapsed = total - remaining). */
        val currentRestTotalSeconds: Int = 0,
        /** Secondes restantes du décompte de repos (computed via tick). */
        val currentRestRemaining: Int = 0,
        /** Secondes écoulées du repos (= total - remaining, clamp ≥ 0). */
        val currentRestElapsed: Int = 0
    ) {
        val isSetInProgress: Boolean get() = currentSetStartedAt != null
        val isRestInProgress: Boolean get() = currentRestEndsAt != null
    }

    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var chronoJob: Job? = null

    /**
     * Garde anti-restauration multiple : on ne tente le restore qu'une fois
     * par lifecycle de process (sinon un re-collect du flow recréerait la
     * session après un stop).
     */
    @Volatile private var restoreAttempted: Boolean = false

    val isSessionActive: Boolean get() = _session.value?.isRunning == true

    /**
     * Démarre/restaure une session en spécifiant explicitement l'ancre temporelle
     * et l'élapsed initial. Utilisé par [tryRestoreFromDb] pour reprendre une
     * séance après process death sans perdre le temps écoulé.
     */
    private fun startSession(
        workoutLogId: Long,
        totalExercises: Int,
        startedAt: LocalDateTime,
        elapsedSeconds: Long,
        currentExerciseName: String,
        currentExerciseIndex: Int,
        currentExerciseStartedAt: LocalDateTime,
        currentSetStartedAt: LocalDateTime?,
        currentSetTimedTotalSeconds: Int,
        currentRestEndsAt: LocalDateTime?,
        currentRestTotalSeconds: Int,
        restoredFromDb: Boolean
    ) {
        val now = LocalDateTime.now()
        val initialSetSeconds = currentSetStartedAt?.let {
            Duration.between(it, now).seconds.coerceAtLeast(0)
        } ?: 0L
        val initialSetRemaining = if (currentSetTimedTotalSeconds > 0 && currentSetStartedAt != null) {
            (currentSetTimedTotalSeconds - initialSetSeconds.toInt()).coerceAtLeast(0)
        } else 0
        val initialRestRemaining = currentRestEndsAt?.let {
            Duration.between(now, it).seconds.toInt().coerceAtLeast(0)
        } ?: 0
        val initialRestElapsed = if (currentRestEndsAt != null && currentRestTotalSeconds > 0) {
            (currentRestTotalSeconds - initialRestRemaining).coerceAtLeast(0)
        } else 0
        _session.value = ActiveSession(
            workoutLogId = workoutLogId,
            startedAt = startedAt,
            globalChronoSeconds = elapsedSeconds,
            isRunning = true,
            currentExerciseName = currentExerciseName,
            currentExerciseIndex = currentExerciseIndex,
            currentExerciseStartedAt = currentExerciseStartedAt,
            currentExerciseSeconds = Duration.between(currentExerciseStartedAt, now).seconds.coerceAtLeast(0),
            totalExercises = totalExercises,
            restoredFromDb = restoredFromDb,
            currentSetStartedAt = currentSetStartedAt,
            currentSetTimedTotalSeconds = currentSetTimedTotalSeconds,
            currentSetSeconds = initialSetSeconds,
            currentSetTimedRemaining = initialSetRemaining,
            // Si le repos a déjà expiré au moment du restore (remaining=0), on
            // clear endsAt — sinon le tick continuerait à le voir actif et
            // l'auto-start spam ne serait jamais armé. Permet aussi à l'UI de
            // cacher proprement le décompte.
            currentRestEndsAt = if (initialRestRemaining > 0) currentRestEndsAt else null,
            currentRestTotalSeconds = if (initialRestRemaining > 0) currentRestTotalSeconds else 0,
            currentRestRemaining = initialRestRemaining,
            currentRestElapsed = initialRestElapsed
        )
        startChrono()
        // Démarre le foreground service qui prend le relais en background pour
        // le coach vocal + countdown audible écran éteint. Idempotent : si déjà
        // démarré, startForegroundService() ne re-crée pas le service.
        WorkoutSessionService.start(appContext)
    }

    /** Surcharge "fresh start" — démarrage neuf depuis Preview/Generator. */
    fun startSession(workoutLogId: Long, totalExercises: Int) {
        val now = LocalDateTime.now()
        startSession(
            workoutLogId = workoutLogId,
            totalExercises = totalExercises,
            startedAt = now,
            elapsedSeconds = 0,
            currentExerciseName = "",
            currentExerciseIndex = 0,
            currentExerciseStartedAt = now,
            currentSetStartedAt = null,
            currentSetTimedTotalSeconds = 0,
            currentRestEndsAt = null,
            currentRestTotalSeconds = 0,
            restoredFromDb = false
        )
        // Persister l'ancre de l'exo courant pour cold-start (clear set + rest).
        persistExerciseStartedAt(workoutLogId, now)
        persistSetState(workoutLogId, null, 0)
        persistRestState(workoutLogId, null, 0)
    }

    /**
     * Met à jour les infos d'exo courant. Re-stampe `currentExerciseStartedAt`
     * UNIQUEMENT si l'exo change (index ou nom différent) — sinon, idempotent
     * pour préserver le chrono d'exo lors des allers-retours sur l'écran session.
     * Sur changement d'exo, persiste le nouvel ancre en DB et clear l'état set.
     */
    fun updateExerciseInfo(name: String, index: Int) {
        val current = _session.value ?: return
        val isNewExercise = current.currentExerciseIndex != index ||
            current.currentExerciseName != name
        val now = LocalDateTime.now()
        val newStartedAt = if (isNewExercise) now else current.currentExerciseStartedAt
        _session.value = current.copy(
            currentExerciseName = name,
            currentExerciseIndex = index,
            currentExerciseStartedAt = newStartedAt,
            currentExerciseSeconds = if (isNewExercise) 0 else current.currentExerciseSeconds,
            // Une transition d'exo annule toute série/repos en cours (cas exotique :
            // user skip un exo en pleine série). Sécurise l'invariant set ⊂ exo.
            currentSetStartedAt = if (isNewExercise) null else current.currentSetStartedAt,
            currentSetTimedTotalSeconds = if (isNewExercise) 0 else current.currentSetTimedTotalSeconds,
            currentSetSeconds = if (isNewExercise) 0 else current.currentSetSeconds,
            currentSetTimedRemaining = if (isNewExercise) 0 else current.currentSetTimedRemaining,
            currentRestEndsAt = if (isNewExercise) null else current.currentRestEndsAt,
            currentRestTotalSeconds = if (isNewExercise) 0 else current.currentRestTotalSeconds,
            currentRestRemaining = if (isNewExercise) 0 else current.currentRestRemaining,
            currentRestElapsed = if (isNewExercise) 0 else current.currentRestElapsed
        )
        if (isNewExercise) {
            persistExerciseStartedAt(current.workoutLogId, newStartedAt)
            persistSetState(current.workoutLogId, null, 0)
            persistRestState(current.workoutLogId, null, 0)
        }
    }

    /**
     * L'utilisateur a tapé `Démarrer la série`. Stamp wall-clock + durée cible
     * pour les sets timed. Persiste en DB pour survie cold-start.
     */
    fun markSetStarted(timedTotalSeconds: Int) {
        val current = _session.value ?: return
        val now = LocalDateTime.now()
        _session.value = current.copy(
            currentSetStartedAt = now,
            currentSetTimedTotalSeconds = timedTotalSeconds,
            currentSetSeconds = 0,
            currentSetTimedRemaining = timedTotalSeconds
        )
        persistSetState(current.workoutLogId, now, timedTotalSeconds)
    }

    /**
     * Série terminée / skippée / annulée. Clear l'état set et persiste.
     */
    fun markSetCompleted() {
        val current = _session.value ?: return
        if (current.currentSetStartedAt == null) return // déjà clean
        _session.value = current.copy(
            currentSetStartedAt = null,
            currentSetTimedTotalSeconds = 0,
            currentSetSeconds = 0,
            currentSetTimedRemaining = 0
        )
        persistSetState(current.workoutLogId, null, 0)
    }

    /**
     * Démarre/redémarre le décompte de repos avec [durationSec] secondes.
     * Stamp wall-clock + persiste pour survie navigation/cold-start. Le tick
     * mettra à jour `currentRestRemaining` chaque seconde, et le caller détecte
     * le passage à 0 pour déclencher l'auto-start de la série suivante.
     */
    fun markRestStarted(durationSec: Int) {
        val current = _session.value ?: return
        val now = LocalDateTime.now()
        val endsAt = now.plusSeconds(durationSec.toLong())
        _session.value = current.copy(
            currentRestEndsAt = endsAt,
            currentRestTotalSeconds = durationSec,
            currentRestRemaining = durationSec,
            currentRestElapsed = 0
        )
        persistRestState(current.workoutLogId, endsAt, durationSec)
    }

    /**
     * Repos terminé / skippé / annulé. Clear l'état rest et persiste.
     */
    fun markRestCompleted() {
        val current = _session.value ?: return
        if (current.currentRestEndsAt == null) return
        _session.value = current.copy(
            currentRestEndsAt = null,
            currentRestTotalSeconds = 0,
            currentRestRemaining = 0,
            currentRestElapsed = 0
        )
        persistRestState(current.workoutLogId, null, 0)
    }

    private fun persistExerciseStartedAt(logId: Long, startedAt: LocalDateTime?) {
        if (logId <= 0) return
        scope.launch {
            try {
                workoutRepositoryProvider.get().updateCurrentExerciseStartedAt(logId, startedAt)
            } catch (t: Throwable) {
                android.util.Log.w("ActiveSessionManager", "persistExerciseStartedAt failed", t)
            }
        }
    }

    private fun persistSetState(logId: Long, startedAt: LocalDateTime?, timedTotal: Int) {
        if (logId <= 0) return
        scope.launch {
            try {
                workoutRepositoryProvider.get().updateCurrentSetState(logId, startedAt, timedTotal)
            } catch (t: Throwable) {
                android.util.Log.w("ActiveSessionManager", "persistSetState failed", t)
            }
        }
    }

    private fun persistRestState(logId: Long, endsAt: LocalDateTime?, totalSec: Int) {
        if (logId <= 0) return
        scope.launch {
            try {
                workoutRepositoryProvider.get().updateCurrentRestState(logId, endsAt, totalSec)
            } catch (t: Throwable) {
                android.util.Log.w("ActiveSessionManager", "persistRestState failed", t)
            }
        }
    }

    fun updateTotalExercises(total: Int) {
        _session.value = _session.value?.copy(totalExercises = total)
    }

    fun pauseChrono() {
        chronoJob?.cancel()
        _session.value = _session.value?.copy(isRunning = false)
    }

    fun resumeChrono() {
        if (_session.value != null && _session.value?.isRunning != true) {
            _session.value = _session.value?.copy(isRunning = true)
            startChrono()
        }
    }

    fun stopSession() {
        chronoJob?.cancel()
        lastSessionDuration = _session.value?.globalChronoSeconds ?: 0
        _session.value = null
        // Une fois la session terminée, autoriser une nouvelle tentative de
        // restore au prochain cycle (cas où l'utilisateur démarre une 2e séance
        // après avoir fermé la 1re sans process death).
        restoreAttempted = false
        // Le service observe sessionFlow et stopSelf à la valeur null, donc on
        // pourrait s'en passer ; mais on appelle explicitement stop() pour
        // garantir l'arrêt immédiat de la notif (sinon délai d'un cycle de
        // collect avant que la notif disparaisse).
        WorkoutSessionService.stop(appContext)
    }

    /** Stats finales de la dernière séance (pour le Summary) */
    var lastSessionDuration: Long = 0; private set
    var lastSessionVolume: Double = 0.0; private set
    var lastSessionSets: Int = 0; private set
    var lastSessionReps: Int = 0; private set
    var lastSessionRestSeconds: Long = 0; private set
    var lastSessionSkipped: Int = 0; private set
    var lastSessionWorkoutLogId: Long = 0; private set
    /** Nb d'exercices de la dernière séance — utilisé pour la share card finale. */
    var lastSessionExerciseCount: Int = 0; private set
    /**
     * Noms ordonnés des exercices de la dernière séance + index des exos
     * skippés + résumé métrique par index ("4×10 · 80kg"). Capturés au
     * moment du `saveSessionStats` car le state du ViewModel est détruit
     * juste après. Utilisé par WorkoutSummaryScreen pour rendre la liste
     * détaillée dans la share card finale.
     */
    var lastSessionExerciseNames: List<String> = emptyList(); private set
    var lastSessionSkippedIndices: Set<Int> = emptySet(); private set
    var lastSessionExerciseMetrics: Map<Int, String> = emptyMap(); private set
    var lastShreddyMessage: String = ""

    fun saveSessionStats(
        duration: Long, volume: Double, sets: Int, reps: Int,
        restSeconds: Long, skipped: Int, workoutLogId: Long,
        exerciseCount: Int = 0,
        exerciseNames: List<String> = emptyList(),
        skippedIndices: Set<Int> = emptySet(),
        exerciseMetrics: Map<Int, String> = emptyMap(),
    ) {
        lastSessionDuration = duration
        lastSessionVolume = volume
        lastSessionSets = sets
        lastSessionReps = reps
        lastSessionRestSeconds = restSeconds
        lastSessionSkipped = skipped
        lastSessionWorkoutLogId = workoutLogId
        lastSessionExerciseCount = exerciseCount
        lastSessionExerciseNames = exerciseNames
        lastSessionSkippedIndices = skippedIndices
        lastSessionExerciseMetrics = exerciseMetrics
    }

    fun getCurrentSeconds(): Long = _session.value?.globalChronoSeconds ?: 0

    /**
     * Au cold-start de l'app, restaure une éventuelle séance non-complétée depuis
     * la DB. Idempotent : appel multiple → no-op après la 1re tentative dans le
     * même process. Doit être appelé depuis un `CoroutineScope` (typiquement le
     * scope de l'app/process — cf. [ShredCoachApplication]).
     *
     * Filtres :
     * - log.completed == false
     * - elapsed depuis log.startTime < 24h (au-delà : abandon implicite)
     * - aucune session en cours en RAM (sinon on respecte la session courante)
     */
    suspend fun tryRestoreFromDb() {
        if (restoreAttempted) return
        restoreAttempted = true
        if (_session.value != null) return // une session est déjà active en RAM

        try {
            val repo = workoutRepositoryProvider.get()
            val log = repo.observeLatestUncompletedLog().first() ?: return
            val now = LocalDateTime.now()
            val elapsed = Duration.between(log.startTime, now)
            // Coupure 24h : au-delà, on considère la séance abandonnée.
            if (elapsed.toHours() >= 24 || elapsed.isNegative) return

            val workoutId = log.workoutId
            val exercises = if (workoutId != null) repo.getExercisesForWorkoutLog(log.id) else emptyList()
            val sets = repo.getWorkoutSets(log.id)

            // Reconstruire l'index de l'exo courant : 1er exo dont le nb de sets
            // (complétés OU skippés) < série prévue. Si tous remplis → dernier exo.
            val currentIndex = computeCurrentExerciseIndex(exercises, sets)
            val currentName = exercises.getOrNull(currentIndex)?.name ?: ""

            // Wall-clock de l'exo courant : on lit la valeur persistée dans le
            // log si elle est cohérente, sinon `now` (best-effort fallback).
            // Cohérence : on accepte la valeur DB tant qu'elle est <= now et
            // postérieure au démarrage de la séance.
            val exoStartedAt = log.currentExerciseStartedAt
                ?.takeIf { !it.isAfter(now) && !it.isBefore(log.startTime) }
                ?: now

            // Set state : on lit ce qui était en cours avant le kill. Filtre
            // de sécurité : si setStartedAt est trop ancien (>30 min, ce qui
            // dépasse toute durée raisonnable de série), on considère que c'est
            // une lecture stale et on clear.
            val setStartedAt = log.currentSetStartedAt?.takeIf {
                !it.isAfter(now) && Duration.between(it, now).toMinutes() < 30
            }
            val setTimedTotal = if (setStartedAt != null) log.currentSetTimedTotalSeconds else 0

            // Repos en cours : on accepte la valeur DB si endsAt est dans une
            // fenêtre raisonnable (pas dans le futur lointain, pas trop ancien).
            // Si endsAt est passé (remaining ≤ 0), on traite comme "repos
            // terminé" et on ne propage pas — l'auto-start sera cancellé.
            val restEndsAt = log.currentRestEndsAt?.takeIf { endsAt ->
                val remaining = Duration.between(now, endsAt).seconds
                // Garde-fou : un endsAt > 10 min dans le futur depuis startTime
                // est probablement un état corrompu. La fenêtre des reps utiles
                // est typiquement 15-300s.
                remaining > 0 && remaining < 600
            }
            val restTotalSec = if (restEndsAt != null) log.currentRestTotalSeconds else 0

            startSession(
                workoutLogId = log.id,
                totalExercises = exercises.size,
                startedAt = log.startTime,
                elapsedSeconds = elapsed.seconds.coerceAtLeast(0),
                currentExerciseName = currentName,
                currentExerciseIndex = currentIndex,
                currentExerciseStartedAt = exoStartedAt,
                currentSetStartedAt = setStartedAt,
                currentSetTimedTotalSeconds = setTimedTotal,
                currentRestEndsAt = restEndsAt,
                currentRestTotalSeconds = restTotalSec,
                restoredFromDb = true
            )
        } catch (t: Throwable) {
            // Restore best-effort : on ne bloque jamais le démarrage de l'app sur
            // une erreur de DB (ex : DB pas encore migrée, cache I/O slow…).
            // On reset le flag pour permettre une nouvelle tentative au prochain
            // appel (ex : MainActivity recréée après config change) — sinon une
            // erreur transitoire au démarrage bloquerait définitivement le restore
            // jusqu'au prochain process kill.
            restoreAttempted = false
            android.util.Log.w("ActiveSessionManager", "tryRestoreFromDb failed", t)
        }
    }

    private fun computeCurrentExerciseIndex(
        exercises: List<com.shredcoach.app.data.local.entity.ExerciseEntity>,
        sets: List<com.shredcoach.app.data.local.entity.WorkoutSetEntity>
    ): Int {
        if (exercises.isEmpty()) return 0
        val countByExo = sets.groupingBy { it.exerciseId }.eachCount()
        for ((idx, exo) in exercises.withIndex()) {
            val done = countByExo[exo.id] ?: 0
            if (done < exo.series) return idx
        }
        return (exercises.size - 1).coerceAtLeast(0)
    }

    private fun startChrono() {
        chronoJob?.cancel()
        chronoJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _session.value ?: break
                if (!current.isRunning) break
                // Tick wall-clock : on recompute tous les chronos depuis leur
                // ancre temporelle pour résister aux suspensions de coroutine
                // (background, doze, process death + restore).
                val now = LocalDateTime.now()
                val elapsed = Duration.between(current.startedAt, now)
                    .seconds.coerceAtLeast(current.globalChronoSeconds)
                val exoElapsed = Duration.between(current.currentExerciseStartedAt, now)
                    .seconds.coerceAtLeast(0)
                val setSeconds = current.currentSetStartedAt?.let {
                    Duration.between(it, now).seconds.coerceAtLeast(0)
                } ?: 0L
                val setTimedRemaining = if (current.currentSetTimedTotalSeconds > 0 &&
                    current.currentSetStartedAt != null
                ) {
                    (current.currentSetTimedTotalSeconds - setSeconds.toInt()).coerceAtLeast(0)
                } else 0
                val (restRemaining, restElapsed) = current.currentRestEndsAt?.let { endsAt ->
                    val remaining = Duration.between(now, endsAt).seconds.toInt().coerceAtLeast(0)
                    val elapsedRest = (current.currentRestTotalSeconds - remaining).coerceAtLeast(0)
                    remaining to elapsedRest
                } ?: (0 to 0)
                _session.value = current.copy(
                    globalChronoSeconds = elapsed,
                    currentExerciseSeconds = exoElapsed,
                    currentSetSeconds = setSeconds,
                    currentSetTimedRemaining = setTimedRemaining,
                    currentRestRemaining = restRemaining,
                    currentRestElapsed = restElapsed
                )
            }
        }
    }
}
