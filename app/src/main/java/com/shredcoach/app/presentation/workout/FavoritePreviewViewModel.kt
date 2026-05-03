package com.shredcoach.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class FavoritePreviewState(
    val workout: WorkoutEntity? = null,
    val exercises: List<ExerciseEntity> = emptyList(),
    val overrides: Map<Long, ExerciseOverride> = emptyMap(),
    val isLoading: Boolean = true,
    val launchedLogId: Long? = null
)

@HiltViewModel
class FavoritePreviewViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritePreviewState())
    val state: StateFlow<FavoritePreviewState> = _state.asStateFlow()

    private val workoutId: Long = savedStateHandle.get<String>("workoutId")?.toLongOrNull() ?: 0L

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val workout = workoutRepository.getWorkoutById(workoutId) ?: return@launch
            val workoutExercises = workoutRepository.getWorkoutExercises(workoutId)
            val exercises = workoutExercises.sortedBy { it.orderIndex }.mapNotNull { we ->
                workoutRepository.getExercisesForWorkoutId(we.exerciseId)?.let { exo ->
                    // Appliquer les overrides déjà sauvegardés dans le template
                    exo.copy(
                        series = we.customSeries ?: exo.series,
                        repsMin = we.customRepsMin ?: exo.repsMin,
                        repsMax = we.customRepsMax ?: exo.repsMax,
                        restSeconds = we.customRestSeconds ?: exo.restSeconds,
                        startingWeight = we.customStartWeight ?: exo.startingWeight
                    )
                }
            }
            _state.update { it.copy(workout = workout, exercises = exercises, isLoading = false) }
        }
    }

    fun updateOverride(exerciseId: Long, update: (ExerciseOverride) -> ExerciseOverride) {
        val overrides = _state.value.overrides.toMutableMap()
        overrides[exerciseId] = update(overrides[exerciseId] ?: ExerciseOverride())
        _state.update { it.copy(overrides = overrides) }
    }

    fun resolvedSeries(ex: ExerciseEntity): Int = _state.value.overrides[ex.id]?.series ?: ex.series
    fun resolvedRepsMin(ex: ExerciseEntity): Int = _state.value.overrides[ex.id]?.repsMin ?: ex.repsMin
    fun resolvedRepsMax(ex: ExerciseEntity): Int = _state.value.overrides[ex.id]?.repsMax ?: ex.repsMax
    fun resolvedRest(ex: ExerciseEntity): Int = _state.value.overrides[ex.id]?.restSeconds ?: ex.restSeconds

    fun launchSession() {
        val s = _state.value
        val workout = s.workout ?: return
        viewModelScope.launch {
            // Sauver les overrides dans le template pour les prochaines fois
            val workoutExercises = workoutRepository.getWorkoutExercises(workout.id)
            val overrides = s.overrides
            if (overrides.isNotEmpty()) {
                workoutRepository.deleteWorkoutExercises(workout.id)
                val updated = s.exercises.mapIndexed { i, exo ->
                    val ov = overrides[exo.id]
                    WorkoutExerciseEntity(
                        workoutId = workout.id, exerciseId = exo.id, orderIndex = i,
                        customSeries = ov?.series,
                        customRepsMin = ov?.repsMin,
                        customRepsMax = ov?.repsMax,
                        customRestSeconds = ov?.restSeconds,
                        customStartWeight = ov?.startWeight
                    )
                }
                workoutRepository.insertWorkoutExercises(updated)
            }

            val now = LocalDateTime.now()
            val log = WorkoutLogEntity(
                workoutId = workout.id, date = now, startTime = now,
                durationMinutes = workout.durationMinutes, completed = false
            )
            val logId = workoutRepository.insertWorkoutLog(log)
            _state.update { it.copy(launchedLogId = logId) }
        }
    }
}
