package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    fun getAllExercises(): Flow<List<ExerciseEntity>> =
        exerciseDao.getAllExercises()

    suspend fun getExerciseById(id: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(id)

    fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<ExerciseEntity>> =
        exerciseDao.getExercisesByMuscleGroup(muscleGroup)

    suspend fun getExerciseByMuscleGroupAndVariant(
        muscleGroup: MuscleGroup,
        variant: ExerciseVariant
    ): ExerciseEntity? =
        exerciseDao.getExerciseByMuscleGroupAndVariant(muscleGroup, variant)

    fun getExercisesByVariant(variant: ExerciseVariant): Flow<List<ExerciseEntity>> =
        exerciseDao.getExercisesByVariant(variant)

    suspend fun insertExercise(exercise: ExerciseEntity): Long =
        exerciseDao.insertExercise(exercise)

    suspend fun insertExercises(exercises: List<ExerciseEntity>) =
        exerciseDao.insertExercises(exercises)

    suspend fun updateExercise(exercise: ExerciseEntity) =
        exerciseDao.updateExercise(exercise)

    suspend fun deleteExercise(exercise: ExerciseEntity) =
        exerciseDao.deleteExercise(exercise)

    suspend fun getExerciseCount(): Int =
        exerciseDao.getExerciseCount()
}
