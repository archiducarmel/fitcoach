package com.shredcoach.app.domain.session

import com.shredcoach.app.data.repository.WorkoutRepository
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
         * navigations (sortir+revenir sur l'écran session) et au process death.
         */
        val currentExerciseStartedAt: LocalDateTime = LocalDateTime.now(),
        val currentExerciseSeconds: Long = 0,
        val totalExercises: Int = 0,
        /** True si l'état a été reconstruit depuis la DB après un cold-start. */
        val restoredFromDb: Boolean = false
    )

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

    fun startSession(workoutLogId: Long, totalExercises: Int) {
        startSession(
            workoutLogId = workoutLogId,
            totalExercises = totalExercises,
            startedAt = LocalDateTime.now(),
            elapsedSeconds = 0,
            currentExerciseName = "",
            currentExerciseIndex = 0,
            restoredFromDb = false
        )
    }

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
        restoredFromDb: Boolean
    ) {
        val now = LocalDateTime.now()
        _session.value = ActiveSession(
            workoutLogId = workoutLogId,
            startedAt = startedAt,
            globalChronoSeconds = elapsedSeconds,
            isRunning = true,
            currentExerciseName = currentExerciseName,
            currentExerciseIndex = currentExerciseIndex,
            // Sur restore depuis DB on n'a pas le vrai startedAt de l'exo courant
            // (la DB ne stampe pas le démarrage par exo) → on prend `now` : le
            // chrono d'exo redémarre à 0 sur cold-start, ce qui est acceptable
            // (le chrono global reste correct, lui).
            currentExerciseStartedAt = now,
            currentExerciseSeconds = 0,
            totalExercises = totalExercises,
            restoredFromDb = restoredFromDb
        )
        startChrono()
    }

    /**
     * Met à jour les infos d'exo courant. Re-stampe `currentExerciseStartedAt`
     * UNIQUEMENT si l'exo change (index ou nom différent) — sinon, idempotent
     * pour préserver le chrono d'exo lors des allers-retours sur l'écran session.
     */
    fun updateExerciseInfo(name: String, index: Int) {
        val current = _session.value ?: return
        val isNewExercise = current.currentExerciseIndex != index ||
            current.currentExerciseName != name
        _session.value = current.copy(
            currentExerciseName = name,
            currentExerciseIndex = index,
            currentExerciseStartedAt = if (isNewExercise) LocalDateTime.now()
                else current.currentExerciseStartedAt,
            currentExerciseSeconds = if (isNewExercise) 0 else current.currentExerciseSeconds
        )
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
    }

    /** Stats finales de la dernière séance (pour le Summary) */
    var lastSessionDuration: Long = 0; private set
    var lastSessionVolume: Double = 0.0; private set
    var lastSessionSets: Int = 0; private set
    var lastSessionReps: Int = 0; private set
    var lastSessionRestSeconds: Long = 0; private set
    var lastSessionSkipped: Int = 0; private set
    var lastSessionWorkoutLogId: Long = 0; private set
    var lastShreddyMessage: String = ""

    fun saveSessionStats(
        duration: Long, volume: Double, sets: Int, reps: Int,
        restSeconds: Long, skipped: Int, workoutLogId: Long
    ) {
        lastSessionDuration = duration
        lastSessionVolume = volume
        lastSessionSets = sets
        lastSessionReps = reps
        lastSessionRestSeconds = restSeconds
        lastSessionSkipped = skipped
        lastSessionWorkoutLogId = workoutLogId
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

            startSession(
                workoutLogId = log.id,
                totalExercises = exercises.size,
                startedAt = log.startTime,
                elapsedSeconds = elapsed.seconds.coerceAtLeast(0),
                currentExerciseName = currentName,
                currentExerciseIndex = currentIndex,
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
                // Tick wall-clock : on recompute toujours depuis startedAt pour
                // résister aux suspensions de la coroutine (background, doze, etc.)
                val now = LocalDateTime.now()
                val elapsed = Duration.between(current.startedAt, now)
                    .seconds.coerceAtLeast(current.globalChronoSeconds)
                val exoElapsed = Duration.between(current.currentExerciseStartedAt, now)
                    .seconds.coerceAtLeast(0)
                _session.value = current.copy(
                    globalChronoSeconds = elapsed,
                    currentExerciseSeconds = exoElapsed
                )
            }
        }
    }
}
