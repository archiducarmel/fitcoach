package com.shredcoach.app.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.usecase.GenerateWorkoutUseCase
import com.shredcoach.app.domain.usecase.GeneratedWorkout
import com.shredcoach.app.domain.usecase.WorkoutConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class WorkoutGeneratorViewModel @Inject constructor(
    private val generateWorkoutUseCase: GenerateWorkoutUseCase,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _selectedDuration = MutableStateFlow(90) // Durée par défaut : 90 min
    val selectedDuration: StateFlow<Int> = _selectedDuration.asStateFlow()

    private val _selectedLevel = MutableStateFlow(FitnessLevel.INTERMEDIATE)
    val selectedLevel: StateFlow<FitnessLevel> = _selectedLevel.asStateFlow()

    private val _selectedEquipment = MutableStateFlow(EquipmentType.FULL_GYM)
    val selectedEquipment: StateFlow<EquipmentType> = _selectedEquipment.asStateFlow()

    private val _generatedWorkout = MutableStateFlow<GeneratedWorkout?>(null)
    val generatedWorkout: StateFlow<GeneratedWorkout?> = _generatedWorkout.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Overrides utilisateur par exerciceId pour la preview. */
    private val _exerciseOverrides = MutableStateFlow<Map<Long, ExerciseOverride>>(emptyMap())
    val exerciseOverrides: StateFlow<Map<Long, ExerciseOverride>> = _exerciseOverrides.asStateFlow()

    init {
        loadUserPreferences()
    }

    private fun loadUserPreferences() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { profile ->
                profile?.let {
                    _selectedDuration.value = it.preferredWorkoutDuration
                    _selectedLevel.value = it.level
                    _selectedEquipment.value = it.equipment
                }
            }
        }
    }

    fun selectDuration(minutes: Int) {
        _selectedDuration.value = minutes
    }

    fun selectLevel(level: FitnessLevel) {
        _selectedLevel.value = level
    }

    fun selectEquipment(equipment: EquipmentType) {
        _selectedEquipment.value = equipment
    }

    fun generateWorkout() {
        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            try {
                val config = WorkoutConfig(
                    durationMinutes = _selectedDuration.value,
                    fitnessLevel = _selectedLevel.value,
                    equipmentType = _selectedEquipment.value
                )

                val workout = generateWorkoutUseCase.execute(config)
                _generatedWorkout.value = workout

            } catch (e: Exception) {
                _error.value = "Erreur lors de la génération : ${e.message}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private val _markAsFavorite = MutableStateFlow(false)
    val markAsFavorite: StateFlow<Boolean> = _markAsFavorite.asStateFlow()

    private val _savedFavoriteId = MutableStateFlow<Long?>(null)

    fun toggleFavorite() {
        val newValue = !_markAsFavorite.value
        _markAsFavorite.value = newValue

        // Sauvegarder immédiatement en DB (pas besoin de démarrer la séance)
        if (newValue) {
            saveFavoriteNow()
        } else {
            // Retirer le favori si déjà sauvé
            _savedFavoriteId.value?.let { id ->
                viewModelScope.launch { workoutRepository.setFavorite(id, false) }
                _savedFavoriteId.value = null
            }
        }
    }

    private fun saveFavoriteNow() {
        val workout = _generatedWorkout.value ?: return
        viewModelScope.launch {
            val allExos = workout.warmupExercises + workout.exercises + workout.cardioExercises
            val name = buildWorkoutName(workout)

            val entity = WorkoutEntity(
                name = name, durationMinutes = workout.totalDuration,
                exerciseCount = allExos.size, isTemplate = true, isFavorite = true
            )
            val workoutId = workoutRepository.insertWorkout(entity)
            _savedFavoriteId.value = workoutId

            val workoutExercises = allExos.mapIndexed { i, exo ->
                WorkoutExerciseEntity(workoutId = workoutId, exerciseId = exo.id, orderIndex = i)
            }
            workoutRepository.insertWorkoutExercises(workoutExercises)
        }
    }

    private fun buildWorkoutName(workout: com.shredcoach.app.domain.usecase.GeneratedWorkout): String {
        val muscles = workout.exercises.map { it.muscleGroup.displayName }.distinct().take(3)
        val date = java.time.LocalDate.now().let { "${it.dayOfMonth}/${it.monthValue}" }
        return "Full Body $date — ${muscles.joinToString(", ")}"
    }

    fun clearWorkout() {
        _generatedWorkout.value = null
        _markAsFavorite.value = false
    }

    fun replaceExercise(oldExercise: ExerciseEntity, newExercise: ExerciseEntity) {
        val currentWorkout = _generatedWorkout.value ?: return

        // Chercher dans les 3 listes et remplacer
        val updatedMuscu = currentWorkout.exercises.map {
            if (it.id == oldExercise.id) newExercise else it
        }
        val updatedWarmup = currentWorkout.warmupExercises.map {
            if (it.id == oldExercise.id) newExercise else it
        }
        val updatedCardio = currentWorkout.cardioExercises.map {
            if (it.id == oldExercise.id) newExercise else it
        }

        _generatedWorkout.value = currentWorkout.copy(
            exercises = updatedMuscu,
            warmupExercises = updatedWarmup,
            cardioExercises = updatedCardio
        )
    }

    suspend fun getAlternativeExercises(exercise: ExerciseEntity): List<ExerciseEntity> {
        return try {
            val allExercises = exerciseRepository.getAllExercises().first()
            val currentWorkout = _generatedWorkout.value

            // IDs des exercices déjà dans la séance (pour éviter les doublons)
            val usedIds = mutableSetOf<Long>()
            currentWorkout?.let {
                usedIds.addAll(it.exercises.map { e -> e.id })
                usedIds.addAll(it.warmupExercises.map { e -> e.id })
                usedIds.addAll(it.cardioExercises.map { e -> e.id })
            }

            allExercises.filter { alt ->
                alt.id != exercise.id &&
                alt.id !in usedIds &&
                alt.muscleGroup == exercise.muscleGroup
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ══════════════════════════════════════════
    // OVERRIDES PREVIEW
    // ══════════════════════════════════════════

    fun updateExerciseOverride(exerciseId: Long, update: (ExerciseOverride) -> ExerciseOverride) {
        val overrides = _exerciseOverrides.value.toMutableMap()
        val current = overrides[exerciseId] ?: ExerciseOverride()
        overrides[exerciseId] = update(current)
        _exerciseOverrides.value = overrides
    }

    /** Résout les paramètres effectifs d'un exercice (override > défaut). */
    fun resolvedSeries(ex: ExerciseEntity): Int = _exerciseOverrides.value[ex.id]?.series ?: ex.series
    fun resolvedRepsMin(ex: ExerciseEntity): Int = _exerciseOverrides.value[ex.id]?.repsMin ?: ex.repsMin
    fun resolvedRepsMax(ex: ExerciseEntity): Int = _exerciseOverrides.value[ex.id]?.repsMax ?: ex.repsMax
    fun resolvedRestSeconds(ex: ExerciseEntity): Int = _exerciseOverrides.value[ex.id]?.restSeconds ?: ex.restSeconds
    fun resolvedStartWeight(ex: ExerciseEntity): String = _exerciseOverrides.value[ex.id]?.startWeight ?: ex.startingWeight

    // ══════════════════════════════════════════
    // LANCEMENT SÉANCE
    // ══════════════════════════════════════════

    suspend fun startWorkoutSession(): Long? {
        val workout = _generatedWorkout.value ?: return null
        val overrides = _exerciseOverrides.value

        return try {
            val workoutId = _savedFavoriteId.value ?: run {
                val totalExercises = workout.warmupExercises.size + workout.exercises.size + workout.cardioExercises.size
                val workoutEntity = WorkoutEntity(
                    name = buildWorkoutName(workout),
                    durationMinutes = workout.totalDuration,
                    exerciseCount = totalExercises,
                    isTemplate = _markAsFavorite.value,
                    isFavorite = _markAsFavorite.value
                )
                val newId = workoutRepository.insertWorkout(workoutEntity)

                val allExercisesInOrder = workout.warmupExercises + workout.exercises + workout.cardioExercises
                val workoutExercises = allExercisesInOrder.mapIndexed { index, exercise ->
                    val ov = overrides[exercise.id]
                    WorkoutExerciseEntity(
                        workoutId = newId, exerciseId = exercise.id, orderIndex = index,
                        customSeries = ov?.series,
                        customRepsMin = ov?.repsMin,
                        customRepsMax = ov?.repsMax,
                        customRestSeconds = ov?.restSeconds,
                        customStartWeight = ov?.startWeight
                    )
                }
                workoutRepository.insertWorkoutExercises(workoutExercises)
                newId
            }

            val now = LocalDateTime.now()
            val workoutLog = WorkoutLogEntity(
                workoutId = workoutId, date = now, startTime = now,
                durationMinutes = workout.totalDuration, completed = false
            )
            workoutRepository.insertWorkoutLog(workoutLog)
        } catch (e: Exception) {
            _error.value = "Erreur lors du démarrage : ${e.message}"
            null
        }
    }
}

/** Paramètres overridés par l'utilisateur sur la preview. */
data class ExerciseOverride(
    val series: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val restSeconds: Int? = null,
    val startWeight: String? = null
)
