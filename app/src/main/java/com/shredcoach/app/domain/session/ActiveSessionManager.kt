package com.shredcoach.app.domain.session

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton qui gère l'état de la séance active.
 * Persiste indépendamment de la navigation — le chrono tourne
 * même quand l'utilisateur est sur Home ou ailleurs.
 */
@Singleton
class ActiveSessionManager @Inject constructor() {

    data class ActiveSession(
        val workoutLogId: Long = 0,
        val globalChronoSeconds: Long = 0,
        val isRunning: Boolean = false,
        val currentExerciseName: String = "",
        val currentExerciseIndex: Int = 0,
        val totalExercises: Int = 0
    )

    private val _session = MutableStateFlow<ActiveSession?>(null)
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var chronoJob: Job? = null

    val isSessionActive: Boolean get() = _session.value?.isRunning == true

    fun startSession(workoutLogId: Long, totalExercises: Int) {
        _session.value = ActiveSession(
            workoutLogId = workoutLogId,
            isRunning = true,
            totalExercises = totalExercises
        )
        startChrono()
    }

    fun updateExerciseInfo(name: String, index: Int) {
        _session.value = _session.value?.copy(
            currentExerciseName = name,
            currentExerciseIndex = index
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

    private fun startChrono() {
        chronoJob?.cancel()
        chronoJob = scope.launch {
            while (isActive) {
                delay(1000)
                _session.value = _session.value?.copy(
                    globalChronoSeconds = (_session.value?.globalChronoSeconds ?: 0) + 1
                )
            }
        }
    }
}
