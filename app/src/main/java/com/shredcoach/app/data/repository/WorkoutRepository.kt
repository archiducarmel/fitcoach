package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.dao.WorkoutDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutLogDao: WorkoutLogDao,
    private val exerciseDao: ExerciseDao
) {
    // Workout templates
    fun getWorkoutTemplates(): Flow<List<WorkoutEntity>> =
        workoutDao.getWorkoutTemplates()

    suspend fun getWorkoutById(id: Long): WorkoutEntity? =
        workoutDao.getWorkoutById(id)

    suspend fun insertWorkout(workout: WorkoutEntity): Long =
        workoutDao.insertWorkout(workout)

    suspend fun updateWorkout(workout: WorkoutEntity) =
        workoutDao.updateWorkout(workout)

    suspend fun deleteWorkout(workout: WorkoutEntity) =
        workoutDao.deleteWorkout(workout)

    // Workout exercises
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity> =
        workoutDao.getWorkoutExercises(workoutId)

    suspend fun insertWorkoutExercise(workoutExercise: WorkoutExerciseEntity) =
        workoutDao.insertWorkoutExercise(workoutExercise)

    suspend fun insertWorkoutExercises(workoutExercises: List<WorkoutExerciseEntity>) =
        workoutDao.insertWorkoutExercises(workoutExercises)

    suspend fun deleteWorkoutExercises(workoutId: Long) =
        workoutDao.deleteWorkoutExercises(workoutId)

    // Workout logs
    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>> =
        workoutLogDao.getAllWorkoutLogs()

    suspend fun getWorkoutLogById(id: Long): WorkoutLogEntity? =
        workoutLogDao.getWorkoutLogById(id)

    fun getWorkoutLogsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkoutLogEntity>> =
        workoutLogDao.getWorkoutLogsBetween(startDate, endDate)

    /** Snapshot one-shot des séances complétées d'une date donnée. */
    suspend fun getCompletedWorkoutsOnDate(date: LocalDate): List<WorkoutLogEntity> =
        workoutLogDao.getCompletedLogsOnDateOnce(date)

    fun getRecentWorkoutLogs(limit: Int): Flow<List<WorkoutLogEntity>> =
        workoutLogDao.getRecentWorkoutLogs(limit)

    fun observeLatestUncompletedLog(): Flow<WorkoutLogEntity?> =
        workoutLogDao.observeLatestUncompletedLog()

    suspend fun getCompletedExerciseCount(logId: Long): Int =
        workoutLogDao.getCompletedExerciseCount(logId)

    suspend fun insertWorkoutLog(log: WorkoutLogEntity): Long =
        workoutLogDao.insertWorkoutLog(log)

    suspend fun updateWorkoutLog(log: WorkoutLogEntity) =
        workoutLogDao.updateWorkoutLog(log)

    suspend fun updateCurrentExerciseStartedAt(logId: Long, startedAt: LocalDateTime?) =
        workoutLogDao.updateCurrentExerciseStartedAt(logId, startedAt)

    suspend fun updateCurrentSetState(logId: Long, startedAt: LocalDateTime?, timedTotal: Int) =
        workoutLogDao.updateCurrentSetState(logId, startedAt, timedTotal)

    suspend fun updateCurrentRestState(logId: Long, endsAt: LocalDateTime?, totalSec: Int) =
        workoutLogDao.updateCurrentRestState(logId, endsAt, totalSec)

    suspend fun updateExtraSeriesJson(logId: Long, json: String) =
        workoutLogDao.updateExtraSeriesJson(logId, json)

    suspend fun deleteWorkoutLog(log: WorkoutLogEntity) =
        workoutLogDao.deleteWorkoutLog(log)

    // Workout sets
    suspend fun getWorkoutSets(workoutLogId: Long): List<WorkoutSetEntity> =
        workoutLogDao.getWorkoutSets(workoutLogId)

    suspend fun getWorkoutSetsByExercise(workoutLogId: Long, exerciseId: Long): List<WorkoutSetEntity> =
        workoutLogDao.getWorkoutSetsByExercise(workoutLogId, exerciseId)

    suspend fun getRecentSetsForExercise(exerciseId: Long): List<WorkoutSetEntity> =
        workoutLogDao.getRecentSetsForExercise(exerciseId)

    suspend fun getMaxWeightForExercise(exerciseId: Long): Double? =
        workoutLogDao.getMaxWeightForExercise(exerciseId)

    /** Record max-reps — bodyweight purs ET time-based (reps = secondes alors). */
    suspend fun getMaxRepsForExercise(exerciseId: Long): Int? =
        workoutLogDao.getMaxRepsForExercise(exerciseId)

    suspend fun insertWorkoutSet(set: WorkoutSetEntity): Long =
        workoutLogDao.insertWorkoutSet(set)

    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>) =
        workoutLogDao.insertWorkoutSets(sets)

    suspend fun updateWorkoutSet(set: WorkoutSetEntity) =
        workoutLogDao.updateWorkoutSet(set)

    suspend fun deleteWorkoutSet(set: WorkoutSetEntity) =
        workoutLogDao.deleteWorkoutSet(set)

    // Helper methods
    suspend fun getExercisesForWorkoutLog(workoutLogId: Long): List<ExerciseEntity> {
        val workoutLog = getWorkoutLogById(workoutLogId) ?: return emptyList()
        val workoutId = workoutLog.workoutId ?: return emptyList()
        val workoutExercises = getWorkoutExercises(workoutId)
        return workoutExercises.sortedBy { it.orderIndex }.mapNotNull { we ->
            exerciseDao.getExerciseById(we.exerciseId)?.let { exo ->
                // Appliquer les overrides de l'utilisateur (configurés sur la preview)
                exo.copy(
                    series = we.customSeries ?: exo.series,
                    repsMin = we.customRepsMin ?: exo.repsMin,
                    repsMax = we.customRepsMax ?: exo.repsMax,
                    restSeconds = we.customRestSeconds ?: exo.restSeconds,
                    startingWeight = we.customStartWeight ?: exo.startingWeight
                )
            }
        }
    }

    suspend fun updateWorkoutLogCompletion(workoutLogId: Long, completed: Boolean, endTime: LocalDateTime) {
        val workoutLog = getWorkoutLogById(workoutLogId) ?: return
        val updatedLog = workoutLog.copy(completed = completed, endTime = endTime)
        updateWorkoutLog(updatedLog)
    }

    // Single exercise by ID
    suspend fun getExercisesForWorkoutId(exerciseId: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(exerciseId)

    // Favoris
    fun getFavoriteWorkouts() = workoutDao.getFavoriteWorkouts()
    suspend fun setFavorite(workoutId: Long, favorite: Boolean) = workoutDao.setFavorite(workoutId, favorite)

    // Statistics
    suspend fun getTotalVolumeInPeriod(startDate: LocalDate, endDate: LocalDate): Double =
        workoutLogDao.getTotalVolumeInPeriod(startDate, endDate) ?: 0.0

    suspend fun getWorkoutCountInPeriod(startDate: LocalDate, endDate: LocalDate): Int =
        workoutLogDao.getWorkoutCountInPeriod(startDate, endDate)

    suspend fun getPersonalRecords() = workoutLogDao.getPersonalRecords()
}
