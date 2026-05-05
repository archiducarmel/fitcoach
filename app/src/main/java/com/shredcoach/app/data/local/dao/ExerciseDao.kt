package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup")
    fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup AND variant = :variant")
    suspend fun getExerciseByMuscleGroupAndVariant(
        muscleGroup: MuscleGroup,
        variant: ExerciseVariant
    ): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE variant = :variant")
    fun getExercisesByVariant(variant: ExerciseVariant): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Delete
    suspend fun deleteExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCount(): Int

    /**
     * Liste des noms d'exos déjà en base. Utilisé par la sync idempotente du
     * catalogue (cf. [com.shredcoach.app.di.DatabaseModule.seedDatabase]) pour
     * insérer uniquement les exos manquants quand SeedData s'enrichit entre
     * deux versions de l'app — sans dupliquer les exos existants ni casser
     * les FK des `workout_sets` qui réfèrent un exo par `id`.
     */
    @Query("SELECT name FROM exercises")
    suspend fun getAllExerciseNames(): List<String>
}
