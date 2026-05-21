package com.shredcoach.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shredcoach.app.data.local.entity.GlucoseAnalysisEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO de l'analyse experte de glycémie quotidienne.
 *
 * **Pattern UPSERT** : un INSERT REPLACE par `date` (unique index) → l'user
 * peut re-déclencher l'analyse depuis l'UI ("Re-analyser") sans surplus de
 * rows, et le worker quotidien overwrite proprement les analyses du jour.
 *
 * **Flow** : l'écran observe `observeForDate` → mises à jour live quand le
 * worker complète en background.
 */
@Dao
interface GlucoseAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GlucoseAnalysisEntity): Long

    @Query("SELECT * FROM glucose_analyses WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: LocalDate): GlucoseAnalysisEntity?

    @Query("SELECT * FROM glucose_analyses WHERE date = :date LIMIT 1")
    fun observeForDate(date: LocalDate): Flow<GlucoseAnalysisEntity?>

    @Query("SELECT * FROM glucose_analyses ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<GlucoseAnalysisEntity>

    @Query("DELETE FROM glucose_analyses WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM glucose_analyses")
    suspend fun deleteAll()
}
