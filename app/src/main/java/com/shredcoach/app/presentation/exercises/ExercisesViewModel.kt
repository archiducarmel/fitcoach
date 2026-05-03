package com.shredcoach.app.presentation.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _allExercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())

    private val _selectedMuscleGroup = MutableStateFlow<MuscleGroup?>(null)
    val selectedMuscleGroup: StateFlow<MuscleGroup?> = _selectedMuscleGroup.asStateFlow()

    private val _selectedVariant = MutableStateFlow<ExerciseVariant?>(null)
    val selectedVariant: StateFlow<ExerciseVariant?> = _selectedVariant.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredExercises = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    val filteredExercises: StateFlow<List<ExerciseEntity>> = _filteredExercises.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadExercises()
    }

    fun refresh() { loadExercises() }

    private fun loadExercises() {
        viewModelScope.launch {
            _isLoading.value = true
            exerciseRepository.getAllExercises().collect { exercises ->
                _allExercises.value = exercises
                applyFilters()
                _isLoading.value = false
            }
        }
    }

    fun selectMuscleGroup(muscleGroup: MuscleGroup?) {
        _selectedMuscleGroup.value = muscleGroup
        applyFilters()
    }

    fun selectVariant(variant: ExerciseVariant?) {
        _selectedVariant.value = variant
        applyFilters()
    }

    fun onSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun clearFilters() {
        _selectedMuscleGroup.value = null
        _selectedVariant.value = null
        _searchQuery.value = ""
        applyFilters()
    }

    private fun applyFilters() {
        val exercises = _allExercises.value
        val muscleGroup = _selectedMuscleGroup.value
        val variant = _selectedVariant.value
        val query = _searchQuery.value.lowercase().trim()

        _filteredExercises.value = exercises.filter { exercise ->
            val matchesMuscleGroup = muscleGroup == null || exercise.muscleGroup == muscleGroup
            val matchesVariant = variant == null || exercise.variant == variant
            val matchesSearch = query.isEmpty() || exercise.name.lowercase().contains(query) || exercise.muscleGroup.displayName.lowercase().contains(query)
            matchesMuscleGroup && matchesVariant && matchesSearch
        }
    }

    fun getExerciseCountByMuscleGroup(muscleGroup: MuscleGroup): Int {
        return _allExercises.value.count { it.muscleGroup == muscleGroup }
    }
}
