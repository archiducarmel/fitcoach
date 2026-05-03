package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateUserProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET currentStreakDays = :streakDays WHERE id = 1")
    suspend fun updateStreak(streakDays: Int)

    @Query("UPDATE user_profile SET totalWorkouts = totalWorkouts + 1 WHERE id = 1")
    suspend fun incrementTotalWorkouts()

    // ── Weight Logs ──
    @Query("SELECT * FROM weight_logs ORDER BY date DESC")
    fun getAllWeightLogs(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_logs WHERE date >= :since ORDER BY date ASC")
    suspend fun getWeightLogsSince(since: LocalDate): List<WeightLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLog(log: WeightLogEntity)

    @Delete
    suspend fun deleteWeightLog(log: WeightLogEntity)

    @Query("SELECT * FROM weight_logs ORDER BY date DESC LIMIT 1")
    suspend fun getLastWeightLog(): WeightLogEntity?

    // ── Progress Photos ──
    @Query("SELECT * FROM progress_photos ORDER BY date DESC")
    fun getAllPhotos(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photos WHERE photoType = :type ORDER BY date DESC")
    suspend fun getPhotosByType(type: String): List<ProgressPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: ProgressPhotoEntity): Long

    @Delete
    suspend fun deletePhoto(photo: ProgressPhotoEntity)
}
