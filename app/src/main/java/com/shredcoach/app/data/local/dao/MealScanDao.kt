package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.MealScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MealScanDao {
    @Query("SELECT * FROM meal_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<MealScanEntity>>

    @Query("SELECT * FROM meal_scans WHERE id = :id")
    suspend fun getScanById(id: Long): MealScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: MealScanEntity): Long

    @Update
    suspend fun updateScan(scan: MealScanEntity)

    @Delete
    suspend fun deleteScan(scan: MealScanEntity)

    @Query("DELETE FROM meal_scans WHERE id = :id")
    suspend fun deleteScanById(id: Long)

    @Query("SELECT SUM(totalCalories) FROM meal_scans WHERE date(timestamp) = date(:date) AND addedToTracking = 1")
    suspend fun getDayCalories(date: String): Int?

    @Query("SELECT * FROM meal_scans WHERE date(timestamp) = date(:date) AND addedToTracking = 1 ORDER BY timestamp ASC")
    suspend fun getScansForDate(date: String): List<MealScanEntity>

    /**
     * Récupère tous les scans postérieurs à [since] (format ISO date `YYYY-MM-DD`).
     * Utilisé par l'agrégation des insights nutrition (fenêtre glissante 30 j).
     */
    @Query("SELECT * FROM meal_scans WHERE date(timestamp) >= date(:since) ORDER BY timestamp DESC")
    suspend fun getScansSince(since: String): List<MealScanEntity>
}
