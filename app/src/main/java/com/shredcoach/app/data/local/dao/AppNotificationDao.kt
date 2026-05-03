package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT * FROM app_notifications WHERE id = :id")
    suspend fun getById(id: Long): AppNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notif: AppNotificationEntity): Long

    @Query("UPDATE app_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun deleteAll()

    /** Purge les anciennes notifications (plus de 60 jours) pour ne pas polluer la DB. */
    @Query("DELETE FROM app_notifications WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: java.time.LocalDateTime)
}
