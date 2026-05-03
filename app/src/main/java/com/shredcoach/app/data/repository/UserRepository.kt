package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userProfileDao: UserProfileDao
) {
    fun getUserProfile(): Flow<UserProfileEntity?> =
        userProfileDao.getUserProfile()

    suspend fun getUserProfileOnce(): UserProfileEntity? =
        userProfileDao.getUserProfileOnce()

    suspend fun insertUserProfile(profile: UserProfileEntity) =
        userProfileDao.insertUserProfile(profile)

    suspend fun updateUserProfile(profile: UserProfileEntity) =
        userProfileDao.updateUserProfile(profile)

    suspend fun updateStreak(streakDays: Int) =
        userProfileDao.updateStreak(streakDays)

    suspend fun incrementTotalWorkouts() =
        userProfileDao.incrementTotalWorkouts()

    // Weight logs
    fun getAllWeightLogs() = userProfileDao.getAllWeightLogs()
    suspend fun getWeightLogsSince(since: LocalDate) = userProfileDao.getWeightLogsSince(since)
    suspend fun insertWeightLog(log: WeightLogEntity) = userProfileDao.insertWeightLog(log)
    suspend fun deleteWeightLog(log: WeightLogEntity) = userProfileDao.deleteWeightLog(log)
    suspend fun getLastWeightLog() = userProfileDao.getLastWeightLog()

    // Progress photos
    fun getAllPhotos() = userProfileDao.getAllPhotos()
    suspend fun getPhotosByType(type: String) = userProfileDao.getPhotosByType(type)
    suspend fun insertPhoto(photo: ProgressPhotoEntity) = userProfileDao.insertPhoto(photo)
    suspend fun deletePhoto(photo: ProgressPhotoEntity) = userProfileDao.deletePhoto(photo)
}
