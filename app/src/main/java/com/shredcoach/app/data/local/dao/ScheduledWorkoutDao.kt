package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ScheduledWorkoutDao {

    @Query("SELECT * FROM scheduled_workouts ORDER BY date ASC, time ASC")
    fun getAll(): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workouts WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC")
    fun getBetween(start: LocalDate, end: LocalDate): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT * FROM scheduled_workouts WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC")
    suspend fun getBetweenOnce(start: LocalDate, end: LocalDate): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workouts WHERE date = :date ORDER BY time ASC")
    fun getForDate(date: LocalDate): Flow<List<ScheduledWorkoutEntity>>

    /** Prochaines séances futures non complétées, triées par date/heure ASC. */
    @Query("SELECT * FROM scheduled_workouts WHERE date >= :today AND status = 'PLANNED' ORDER BY date ASC, time ASC LIMIT :limit")
    suspend fun getUpcoming(today: LocalDate, limit: Int = 5): List<ScheduledWorkoutEntity>

    @Query("SELECT * FROM scheduled_workouts WHERE id = :id")
    suspend fun getById(id: Long): ScheduledWorkoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduledWorkoutEntity): Long

    @Update
    suspend fun update(schedule: ScheduledWorkoutEntity)

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE scheduled_workouts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE scheduled_workouts SET workoutLogId = :logId, status = 'COMPLETED' WHERE id = :id")
    suspend fun markCompleted(id: Long, logId: Long)

    @Query("UPDATE scheduled_workouts SET reminderShakerSent = 1 WHERE id = :id")
    suspend fun markShakerReminderSent(id: Long)

    @Query("UPDATE scheduled_workouts SET reminderStartSent = 1 WHERE id = :id")
    suspend fun markStartReminderSent(id: Long)

    /** Statistique : nb séances PLANIFIÉES dans une période (peu importe le statut). */
    @Query("SELECT COUNT(*) FROM scheduled_workouts WHERE date BETWEEN :start AND :end")
    suspend fun countInPeriod(start: LocalDate, end: LocalDate): Int

    /** Statistique : nb séances COMPLETED dans une période. */
    @Query("SELECT COUNT(*) FROM scheduled_workouts WHERE date BETWEEN :start AND :end AND status = 'COMPLETED'")
    suspend fun countCompletedInPeriod(start: LocalDate, end: LocalDate): Int
}
