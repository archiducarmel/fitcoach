package com.shredcoach.app.presentation.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val exerciseId: Long = savedStateHandle.get<String>("exerciseId")?.toLongOrNull() ?: 0L

    private val _exercise = MutableStateFlow<ExerciseEntity?>(null)
    val exercise: StateFlow<ExerciseEntity?> = _exercise.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadExercise()
    }

    private fun loadExercise() {
        viewModelScope.launch {
            _isLoading.value = true
            val exercise = exerciseRepository.getExerciseById(exerciseId)
            _exercise.value = exercise
            _isLoading.value = false
        }
    }
}
