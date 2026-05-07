package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val secureKeyStore: SecureKeyStore
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

    suspend fun updateLastUsedRoutineId(routineId: String) =
        userProfileDao.updateLastUsedRoutineId(routineId)

    suspend fun updateLanguageTag(tag: String?) =
        userProfileDao.updateLanguageTag(tag)

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

    // ──────────────────────────────────────────────────────────
    // Clés API (chiffrées via SecureKeyStore — JAMAIS dans Room).
    // Les colonnes correspondantes dans UserProfileEntity sont
    // conservées le temps de la migration douce, puis retirées
    // en Phase D via Room migration v33 → v34.
    // ──────────────────────────────────────────────────────────

    fun getApiKey(provider: SecureKeyStore.Provider): String =
        secureKeyStore.getKey(provider)

    fun setApiKey(provider: SecureKeyStore.Provider, value: String) {
        secureKeyStore.setKey(provider, value)
    }

    fun hasApiKey(provider: SecureKeyStore.Provider): Boolean =
        secureKeyStore.hasKey(provider)

    fun clearApiKey(provider: SecureKeyStore.Provider) {
        secureKeyStore.clear(provider)
    }
}
