package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.ScheduledWorkoutDao
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledWorkoutRepository @Inject constructor(
    private val dao: ScheduledWorkoutDao
) {
    fun getAll(): Flow<List<ScheduledWorkoutEntity>> = dao.getAll()
    fun getBetween(start: LocalDate, end: LocalDate) = dao.getBetween(start, end)
    fun getForDate(date: LocalDate) = dao.getForDate(date)
    suspend fun getBetweenOnce(start: LocalDate, end: LocalDate) = dao.getBetweenOnce(start, end)
    suspend fun getUpcoming(today: LocalDate = LocalDate.now(), limit: Int = 5) = dao.getUpcoming(today, limit)
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun insert(schedule: ScheduledWorkoutEntity): Long = dao.insert(schedule)
    suspend fun update(schedule: ScheduledWorkoutEntity) = dao.update(schedule)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun markCompleted(id: Long, logId: Long) = dao.markCompleted(id, logId)
    suspend fun markSkipped(id: Long) = dao.updateStatus(id, "SKIPPED")
    suspend fun markCanceled(id: Long) = dao.updateStatus(id, "CANCELED")
    suspend fun markShakerReminderSent(id: Long) = dao.markShakerReminderSent(id)
    suspend fun markStartReminderSent(id: Long) = dao.markStartReminderSent(id)
    suspend fun countInPeriod(start: LocalDate, end: LocalDate) = dao.countInPeriod(start, end)
    suspend fun countCompletedInPeriod(start: LocalDate, end: LocalDate) = dao.countCompletedInPeriod(start, end)
}
