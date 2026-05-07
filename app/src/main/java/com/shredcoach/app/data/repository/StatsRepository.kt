package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.*
import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val workoutLogDao: WorkoutLogDao,
    private val exerciseDao: ExerciseDao
) {
    suspend fun getWeightProgression(exerciseId: Long) =
        workoutLogDao.getWeightProgressionForExercise(exerciseId)

    suspend fun getDailyVolume(since: LocalDate) =
        workoutLogDao.getDailyVolume(since)

    suspend fun getTrainingFrequency(since: LocalDate) =
        workoutLogDao.getTrainingFrequency(since)

    suspend fun getPersonalRecords() =
        workoutLogDao.getPersonalRecords()

    /**
     * PRs en "max-reps" — utile pour les exos bodyweight (max pompes/tractions)
     * et time-based (max durée tenue, le `reps` étant la durée en secondes).
     */
    suspend fun getMaxRepsRecords() =
        workoutLogDao.getMaxRepsRecords()

    suspend fun getMuscleGroupDistribution(since: LocalDate) =
        workoutLogDao.getMuscleGroupDistribution(since)

    suspend fun getTotalWorkoutCount() = workoutLogDao.getTotalWorkoutCount()
    suspend fun getTotalVolumeAllTime() = workoutLogDao.getTotalVolumeAllTime() ?: 0.0
    suspend fun getTotalDurationAllTime() = workoutLogDao.getTotalDurationAllTime() ?: 0L
    suspend fun getTotalRepsAllTime() = workoutLogDao.getTotalRepsAllTime() ?: 0

    suspend fun getDurationByMuscleGroup() = workoutLogDao.getDurationByMuscleGroup()

    /** Volume cumulé + nb séances par routine sur une période. */
    suspend fun getVolumeByRoutine(since: LocalDate) =
        workoutLogDao.getVolumeByRoutine(since)

    suspend fun getWorkoutCountInPeriod(start: LocalDate, end: LocalDate) =
        workoutLogDao.getWorkoutCountInPeriod(start, end)

    suspend fun getTotalVolumeInPeriod(start: LocalDate, end: LocalDate) =
        workoutLogDao.getTotalVolumeInPeriod(start, end) ?: 0.0

    suspend fun getExerciseById(id: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(id)

    suspend fun getAllExercisesOnce(): List<ExerciseEntity> =
        exerciseDao.getAllExercises().first()
}
