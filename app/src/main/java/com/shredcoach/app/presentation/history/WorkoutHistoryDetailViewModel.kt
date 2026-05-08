package com.shredcoach.app.presentation.history


import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class ExercisePerformance(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val totalVolume: Double,
    val maxWeightKg: Double,
    val totalReps: Int
)

@Immutable
data class HistoryDetailState(
    val log: WorkoutLogEntity? = null,
    val workoutName: String = "",
    val performances: List<ExercisePerformance> = emptyList(),
    val isLoading: Boolean = true,
    val relaunchedLogId: Long? = null
)

@HiltViewModel
class WorkoutHistoryDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryDetailState())
    val state: StateFlow<HistoryDetailState> = _state.asStateFlow()

    private val logId: Long = savedStateHandle.get<String>("logId")?.toLongOrNull() ?: 0L

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val log = workoutRepository.getWorkoutLogById(logId) ?: return@launch
            val workout = log.workoutId?.let { workoutRepository.getWorkoutById(it) }
            val workoutName = if (workout == null || workout.isFreestyle) {
                appContext.getString(R.string.history_freestyle_session_name)
            } else {
                workout.name
            }

            // Charger tous les sets de la séance
            val allSets = workoutRepository.getWorkoutSets(log.id)

            // Grouper par exerciseId
            val perfMap = allSets.groupBy { it.exerciseId }
            val performances = perfMap.map { (exoId, sets) ->
                val exo = workoutRepository.getExercisesForWorkoutId(exoId)
                    ?: return@map null
                ExercisePerformance(
                    exercise = exo,
                    sets = sets.sortedBy { it.setNumber },
                    totalVolume = sets.sumOf { it.weightKg * it.reps },
                    maxWeightKg = sets.maxOfOrNull { it.weightKg } ?: 0.0,
                    totalReps = sets.sumOf { it.reps }
                )
            }.filterNotNull()

            _state.update {
                it.copy(
                    log = log, workoutName = workoutName,
                    performances = performances, isLoading = false
                )
            }
        }
    }

    /** Crée un nouveau WorkoutLog pointant sur le même workoutId (template). */
    fun relaunchSession() {
        val current = _state.value.log ?: return
        val workoutId = current.workoutId ?: return
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val newLog = WorkoutLogEntity(
                workoutId = workoutId,
                date = now,
                startTime = now,
                durationMinutes = current.durationMinutes,
                completed = false
            )
            val newId = workoutRepository.insertWorkoutLog(newLog)
            _state.update { it.copy(relaunchedLogId = newId) }
        }
    }

    fun deleteLog() {
        viewModelScope.launch {
            val log = _state.value.log ?: return@launch
            workoutRepository.deleteWorkoutLog(log)
        }
    }
}
