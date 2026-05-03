package com.shredcoach.app.domain.usecase

import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class WorkoutConfig(
    val durationMinutes: Int, // 60, 90, 120, 180
    val fitnessLevel: FitnessLevel = FitnessLevel.INTERMEDIATE,
    val equipmentType: EquipmentType = EquipmentType.FULL_GYM
)

data class GeneratedWorkout(
    val exercises: List<ExerciseEntity>, // Exercices de musculation principaux
    val warmupExercises: List<ExerciseEntity> = emptyList(), // Exercices d'échauffement
    val cardioExercises: List<ExerciseEntity> = emptyList(), // Exercices cardio
    val totalDuration: Int,
    val warmupMinutes: Int = 8,
    val cardioMinutes: Int = 0,
    val exerciseCount: Int // Nombre d'exercices muscu uniquement
)

class GenerateWorkoutUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    /**
     * Génère une séance Full Body intelligente basée sur la configuration
     *
     * Règles :
     * - 60 min : 6 exercices (échauffement 8 min + exercices 40 min + cardio 12 min)
     * - 90 min : 8 exercices (échauffement 8 min + exercices 57 min + cardio 25 min)
     * - 120 min : 10 exercices (échauffement 8 min + exercices 82 min + cardio 30 min)
     * - 180 min : 12 exercices (échauffement 10 min + exercices 130 min + cardio 40 min)
     *
     * Priorité groupes musculaires :
     * 1. QUADS (jambes essentielles)
     * 2. CHEST (pectoraux)
     * 3. BACK_WIDTH (dos largeur)
     * 4. SHOULDERS (épaules)
     * 5. BICEPS ou TRICEPS (bras)
     * 6. ABS_UPPER (abdos)
     * + Optionnels selon durée : HAMSTRINGS, CHEST_UPPER, BACK_THICKNESS, ABS_LOWER, CALVES
     */
    suspend fun execute(config: WorkoutConfig): GeneratedWorkout {
        val allExercises = exerciseRepository.getAllExercises().first()

        // Séparer les exercices de musculation des autres
        val muscuExercises = allExercises.filter {
            it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO
        }

        // Filtrer selon équipement disponible
        val availableExercises = filterByEquipment(muscuExercises, config.equipmentType)

        // Filtrer selon niveau
        val levelFilteredExercises = filterByLevel(availableExercises, config.fitnessLevel)

        // Sélectionner les groupes musculaires selon la durée
        val muscleGroups = selectMuscleGroups(config.durationMinutes)

        // Sélectionner 1 exercice par groupe musculaire
        val selectedExercises = selectExercisesForMuscleGroups(
            exercises = levelFilteredExercises,
            muscleGroups = muscleGroups,
            equipmentType = config.equipmentType
        )

        // Sélectionner exercices d'échauffement (tous)
        val warmupExercises = allExercises.filter { it.muscleGroup == MuscleGroup.WARMUP }

        // Sélectionner UN SEUL exercice cardio (pas fractionné)
        val cardioExercises = filterByEquipment(
            allExercises.filter { it.muscleGroup == MuscleGroup.CARDIO },
            config.equipmentType
        ).randomOrNull()?.let { listOf(it) } ?: emptyList()

        // Calculer la durée du cardio
        val cardioMinutes = calculateCardioTime(config.durationMinutes)

        return GeneratedWorkout(
            exercises = selectedExercises,
            warmupExercises = warmupExercises,
            cardioExercises = cardioExercises,
            totalDuration = config.durationMinutes,
            warmupMinutes = if (config.durationMinutes >= 180) 10 else 8,
            cardioMinutes = cardioMinutes,
            exerciseCount = selectedExercises.size
        )
    }

    private fun filterByEquipment(
        exercises: List<ExerciseEntity>,
        equipmentType: EquipmentType
    ): List<ExerciseEntity> {
        return when (equipmentType) {
            EquipmentType.FULL_GYM -> exercises // Tout est disponible
            EquipmentType.HOME_GYM -> exercises.filter {
                it.variant == ExerciseVariant.WEIGHTS || it.variant == ExerciseVariant.BODYWEIGHT
            }
            EquipmentType.BODYWEIGHT -> exercises.filter {
                it.variant == ExerciseVariant.BODYWEIGHT
            }
        }
    }

    private fun filterByLevel(
        exercises: List<ExerciseEntity>,
        level: FitnessLevel
    ): List<ExerciseEntity> {
        return when (level) {
            FitnessLevel.BEGINNER -> exercises.filter { it.difficulty <= 2 }
            FitnessLevel.INTERMEDIATE -> exercises.filter { it.difficulty <= 3 }
            FitnessLevel.ADVANCED -> exercises // Tous les exercices
        }
    }

    private fun selectMuscleGroups(durationMinutes: Int): List<MuscleGroup> {
        val baseGroups = listOf(
            MuscleGroup.QUADS,        // Jambes (priorité 1)
            MuscleGroup.CHEST,        // Pectoraux (priorité 2)
            MuscleGroup.BACK_WIDTH,   // Dos largeur (priorité 3)
            MuscleGroup.SHOULDERS,    // Épaules (priorité 4)
            MuscleGroup.BICEPS,       // Bras (priorité 5)
            MuscleGroup.ABS_UPPER     // Abdos (priorité 6)
        )

        return when {
            durationMinutes <= 60 -> baseGroups.take(6)
            durationMinutes <= 90 -> baseGroups + listOf(
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.ABS_LOWER
            )
            durationMinutes <= 120 -> baseGroups + listOf(
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.TRICEPS,
                MuscleGroup.ABS_LOWER,
                MuscleGroup.CHEST_UPPER,
                MuscleGroup.TRAPS
            )
            else -> baseGroups + listOf( // 180 min (séance complète)
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.TRICEPS,
                MuscleGroup.ABS_LOWER,
                MuscleGroup.CHEST_UPPER,
                MuscleGroup.BACK_THICKNESS,
                MuscleGroup.CALVES,
                MuscleGroup.ADDUCTORS,
                MuscleGroup.TRAPS,
                MuscleGroup.FOREARMS,
                MuscleGroup.LOWER_BACK
            )
        }
    }

    private fun selectExercisesForMuscleGroups(
        exercises: List<ExerciseEntity>,
        muscleGroups: List<MuscleGroup>,
        equipmentType: EquipmentType
    ): List<ExerciseEntity> {
        val selectedExercises = mutableListOf<ExerciseEntity>()

        for (muscleGroup in muscleGroups) {
            // Récupérer tous les exercices de ce groupe musculaire
            val groupExercises = exercises.filter { it.muscleGroup == muscleGroup }

            if (groupExercises.isEmpty()) continue

            // Stratégie de sélection : variante préférée + randomisation
            val selectedExercise = when (equipmentType) {
                EquipmentType.FULL_GYM -> {
                    if (muscleGroup in listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK_WIDTH)) {
                        groupExercises.filter { it.variant == ExerciseVariant.MACHINE }.randomOrNull()
                            ?: groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                            ?: groupExercises.random()
                    } else {
                        groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                            ?: groupExercises.filter { it.variant == ExerciseVariant.ISOLATION }.randomOrNull()
                            ?: groupExercises.random()
                    }
                }
                EquipmentType.HOME_GYM -> {
                    groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                        ?: groupExercises.random()
                }
                EquipmentType.BODYWEIGHT -> {
                    groupExercises.random()
                }
            }

            selectedExercises.add(selectedExercise)
        }

        return selectedExercises
    }

    private fun calculateCardioTime(totalDuration: Int): Int {
        return when (totalDuration) {
            60 -> 12
            90 -> 25
            120 -> 30
            180 -> 40
            else -> 20
        }
    }

    /**
     * Calcule le temps estimé pour les exercices
     */
    fun calculateExerciseTime(exercises: List<ExerciseEntity>): Int {
        return exercises.sumOf { exercise ->
            val setTime = 30 // Temps moyen par série (30 secondes)
            val restTime = exercise.restSeconds
            val totalTime = (setTime + restTime) * exercise.series
            totalTime / 60 // Convertir en minutes
        }
    }
}
