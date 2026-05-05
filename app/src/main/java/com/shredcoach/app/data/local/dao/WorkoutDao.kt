package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE isTemplate = 1 ORDER BY createdAt DESC")
    fun getWorkoutTemplates(): Flow<List<WorkoutEntity>>

    /** Snapshot global, utilisé par le moteur de backup (export). */
    @Query("SELECT * FROM workouts ORDER BY id ASC")
    suspend fun getAllWorkoutsOnce(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutById(id: Long): WorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    // WorkoutExercise operations
    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY orderIndex ASC")
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

    /** Snapshot global de la table de jonction, utilisé par le backup. */
    @Query("SELECT * FROM workout_exercises ORDER BY workoutId ASC, orderIndex ASC")
    suspend fun getAllWorkoutExercisesOnce(): List<WorkoutExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(workoutExercises: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteWorkoutExercises(workoutId: Long)

    // Favoris
    @Query("SELECT * FROM workouts WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteWorkouts(): Flow<List<WorkoutEntity>>

    @Query("UPDATE workouts SET isFavorite = :favorite WHERE id = :workoutId")
    suspend fun setFavorite(workoutId: Long, favorite: Boolean)
}
