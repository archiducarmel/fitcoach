package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.AppNotificationDao
import com.shredcoach.app.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationRepository @Inject constructor(
    private val dao: AppNotificationDao
) {
    fun getAll(): Flow<List<AppNotificationEntity>> = dao.getAll()
    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun insert(notif: AppNotificationEntity): Long = dao.insert(notif)
    suspend fun markAsRead(id: Long) = dao.markAsRead(id)
    suspend fun markAllAsRead() = dao.markAllAsRead()
    suspend fun deleteById(id: Long) = dao.deleteById(id)
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun purgeOld(keepDays: Int = 60) =
        dao.purgeOlderThan(LocalDateTime.now().minusDays(keepDays.toLong()))
}
