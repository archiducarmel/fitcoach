package com.shredcoach.app.presentation.history


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Entrée affichée dans la liste d'historique : log + nom workout + nb sets réels. */
data class HistoryListItem(
    val log: WorkoutLogEntity,
    val workoutName: String,
    val realSetsCount: Int,
    val realExercisesCount: Int
)

enum class HistoryFilter(val displayName: String) {
    ALL("Toutes"), COMPLETED("Terminées"), ABANDONED("Abandonnées")
}

@Immutable
data class WorkoutHistoryState(
    val items: List<HistoryListItem> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /**
     * Filtre par routine (Push, Pull, …). `null` = toutes les routines.
     * Indépendant de [filter] (status) — combinable.
     */
    val routineFilter: String? = null,
    val isLoading: Boolean = true,
    val totalWorkouts: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalDurationMinutes: Long = 0
)

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val mealScanDao: MealScanDao,
    private val nutritionRepository: NutritionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutHistoryState())
    val state: StateFlow<WorkoutHistoryState> = _state.asStateFlow()

    /** Scans nutritionnels pour l'onglet Nutrition. */
    val mealScans: StateFlow<List<MealScanEntity>> = mealScanDao.getAllScans()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init { load() }

    fun refresh() = load()

    fun setFilter(filter: HistoryFilter) {
        _state.update { it.copy(filter = filter) }
    }

    /** `null` = toutes les routines (filtre désactivé). */
    fun setRoutineFilter(routineId: String?) {
        _state.update { it.copy(routineFilter = routineId) }
    }

    private fun load() {
        viewModelScope.launch {
            workoutRepository.getAllWorkoutLogs().collect { logs ->
                val items = logs.map { log ->
                    val workoutName = log.workoutId?.let {
                        workoutRepository.getWorkoutById(it)?.name
                    } ?: "Séance libre"
                    val sets = workoutRepository.getWorkoutSets(log.id)
                    HistoryListItem(
                        log = log,
                        workoutName = workoutName,
                        realSetsCount = sets.size,
                        realExercisesCount = sets.map { it.exerciseId }.distinct().size
                    )
                }
                val completed = items.filter { it.log.completed }
                _state.update {
                    it.copy(
                        items = items,
                        isLoading = false,
                        totalWorkouts = completed.size,
                        totalVolumeKg = completed.sumOf { i -> i.log.totalVolume },
                        totalDurationMinutes = completed.sumOf { i -> i.log.actualDurationSeconds } / 60
                    )
                }
            }
        }
    }

    fun deleteLog(log: WorkoutLogEntity) {
        viewModelScope.launch { workoutRepository.deleteWorkoutLog(log) }
    }

    fun deleteMealScan(scan: MealScanEntity) {
        viewModelScope.launch {
            // 1. Récupérer les foodIds associés AVANT le cascade
            val foodIds = nutritionRepository.getFoodIdsByScanId(scan.id)
            // 2. Supprimer le scan → CASCADE supprime les MealLogEntity
            mealScanDao.deleteScan(scan)
            // 3. Supprimer les FoodEntity orphelins
            if (foodIds.isNotEmpty()) {
                nutritionRepository.deleteFoodsByIds(foodIds)
            }
            // 4. Supprimer la photo associée
            scan.photoPath?.let { path ->
                try { java.io.File(path).delete() } catch (_: Exception) {}
            }
        }
    }
}
