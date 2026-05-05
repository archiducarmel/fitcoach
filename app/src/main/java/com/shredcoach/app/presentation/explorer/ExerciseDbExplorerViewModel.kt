package com.shredcoach.app.presentation.explorer


import androidx.compose.runtime.Immutable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.remote.ExerciseDbExercise
import com.shredcoach.app.data.remote.ExerciseDbMeta
import com.shredcoach.app.data.remote.ExerciseDbService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ExerciseDbExplorerState(
    // Filtres
    val searchQuery: String = "",
    val selectedMuscle: String? = null,
    val selectedEquipment: String? = null,
    val selectedCategory: String? = null,
    val selectedLevel: String? = null,
    // Données
    val exercises: List<ExerciseDbExercise> = emptyList(),
    val totalInDataset: Int = 0,
    // Méta (listes de filtres dynamiques)
    val meta: ExerciseDbMeta = ExerciseDbMeta(),
    // États
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExerciseDbExplorerViewModel @Inject constructor(
    private val service: ExerciseDbService
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseDbExplorerState())
    val state: StateFlow<ExerciseDbExplorerState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        Log.i(TAG_VM, "★ ExerciseDbExplorerViewModel créé")
        loadInitial()
    }

    private fun loadInitial() {
        Log.i(TAG_VM, "loadInitial → isLoading=true")
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            Log.d(TAG_VM, "loadInitial: appel service.getMeta()…")
            service.getMeta()
                .onSuccess { meta ->
                    Log.i(TAG_VM, "✓ getMeta OK : ${meta.muscles.size} muscles, ${meta.equipments.size} eq, total dataset=${service.totalCount}")
                    _state.update {
                        it.copy(
                            meta = meta,
                            totalInDataset = service.totalCount
                        )
                    }
                    applyFilters()
                }
                .onFailure { err ->
                    Log.e(TAG_VM, "✗ getMeta FAILED : ${err.javaClass.simpleName} — ${err.message}", err)
                    _state.update {
                        it.copy(isLoading = false, error = err.message ?: "Erreur réseau")
                    }
                }
        }
    }

    companion object { private const val TAG_VM = "ExoDB-VM" }

    /** Force un rechargement réseau du dataset. */
    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            service.reloadDataset()
                .onSuccess {
                    service.getMeta().onSuccess { meta ->
                        _state.update { it.copy(meta = meta, totalInDataset = service.totalCount) }
                    }
                    applyFilters()
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(isLoading = false, error = err.message ?: "Erreur réseau")
                    }
                }
        }
    }

    // ─────────────────────────────
    // Recherche & filtres (instantanés — tout est en mémoire)
    // ─────────────────────────────

    fun onSearchChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        // Petit debounce pour éviter de filtrer 873 items à chaque keystroke
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            applyFilters()
        }
    }

    fun selectMuscle(value: String?) {
        _state.update { it.copy(selectedMuscle = value) }
        applyFilters()
    }

    fun selectEquipment(value: String?) {
        _state.update { it.copy(selectedEquipment = value) }
        applyFilters()
    }

    fun selectCategory(value: String?) {
        _state.update { it.copy(selectedCategory = value) }
        applyFilters()
    }

    fun selectLevel(value: String?) {
        _state.update { it.copy(selectedLevel = value) }
        applyFilters()
    }

    fun clearAllFilters() {
        _state.update {
            it.copy(
                searchQuery = "",
                selectedMuscle = null,
                selectedEquipment = null,
                selectedCategory = null,
                selectedLevel = null
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        Log.d(TAG_VM, "applyFilters appelé (q='${s.searchQuery}', muscle=${s.selectedMuscle}, eq=${s.selectedEquipment})")
        viewModelScope.launch {
            service.filterExercises(
                search = s.searchQuery.takeIf { it.isNotBlank() },
                muscle = s.selectedMuscle,
                equipment = s.selectedEquipment,
                category = s.selectedCategory,
                level = s.selectedLevel
            ).onSuccess { list ->
                Log.i(TAG_VM, "✓ applyFilters OK : ${list.size} exos → maj state")
                _state.update { it.copy(exercises = list, isLoading = false, error = null) }
            }.onFailure { err ->
                Log.e(TAG_VM, "✗ applyFilters FAILED : ${err.message}", err)
                _state.update {
                    it.copy(isLoading = false, error = err.message ?: "Erreur de filtrage")
                }
            }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
