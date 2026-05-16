package com.shredcoach.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * DAO pour les entrées glycémiques journalières (CGM).
 *
 * **Source unique** : 1 entrée par date (UNIQUE index sur `date`). Toute
 * insertion sur une date existante remplace l'ancienne — pas de DELETE
 * manuel nécessaire.
 *
 * **Pattern Flow / suspend** : aligné avec le reste du projet — Flow pour
 * les listeners UI, suspend pour les lectures one-shot (notifs, IA contexts).
 */
@Dao
interface GlucoseDao {

    @Query("SELECT * FROM glucose_logs WHERE date = :date LIMIT 1")
    fun observeForDate(date: LocalDate): Flow<GlucoseLogEntity?>

    @Query("SELECT * FROM glucose_logs WHERE date = :date LIMIT 1")
    suspend fun getForDateOnce(date: LocalDate): GlucoseLogEntity?

    @Query("SELECT * FROM glucose_logs WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<GlucoseLogEntity>>

    @Query("SELECT * FROM glucose_logs WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRangeOnce(from: LocalDate, to: LocalDate): List<GlucoseLogEntity>

    @Query("SELECT * FROM glucose_logs ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentOnce(limit: Int = 30): List<GlucoseLogEntity>

    @Query("SELECT * FROM glucose_logs WHERE date < :before ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecentBefore(before: LocalDate): GlucoseLogEntity?

    // ─── Agrégats pour fenêtres temporelles (7j / 30j) ──────────

    @Query("SELECT AVG(avgMgdl) FROM glucose_logs WHERE date BETWEEN :from AND :to AND avgMgdl IS NOT NULL")
    suspend fun getAvgMgdlOnRange(from: LocalDate, to: LocalDate): Double?

    @Query("SELECT AVG(timeInRangePct) FROM glucose_logs WHERE date BETWEEN :from AND :to AND timeInRangePct IS NOT NULL")
    suspend fun getAvgTirOnRange(from: LocalDate, to: LocalDate): Double?

    @Query("SELECT AVG(cv) FROM glucose_logs WHERE date BETWEEN :from AND :to AND cv IS NOT NULL")
    suspend fun getAvgCvOnRange(from: LocalDate, to: LocalDate): Double?

    @Query("SELECT COUNT(*) FROM glucose_logs WHERE date BETWEEN :from AND :to")
    suspend fun getCountOnRange(from: LocalDate, to: LocalDate): Int

    @Query("SELECT SUM(hypoCount) FROM glucose_logs WHERE date BETWEEN :from AND :to AND hypoCount IS NOT NULL")
    suspend fun getTotalHypoCountOnRange(from: LocalDate, to: LocalDate): Int?

    // ─── Writes ─────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: GlucoseLogEntity): Long

    @Query("DELETE FROM glucose_logs WHERE date = :date")
    suspend fun deleteForDate(date: LocalDate)

    @Query("DELETE FROM glucose_logs")
    suspend fun clearAll()

    @Query("SELECT * FROM glucose_logs ORDER BY date ASC")
    suspend fun getAllOnce(): List<GlucoseLogEntity>
}
