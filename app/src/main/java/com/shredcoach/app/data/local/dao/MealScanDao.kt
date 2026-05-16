package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.MealScanEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface MealScanDao {
    @Query("SELECT * FROM meal_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<MealScanEntity>>

    @Query("SELECT * FROM meal_scans WHERE id = :id")
    suspend fun getScanById(id: Long): MealScanEntity?

    /** Observable du scan (utile pour la page détail qui doit réagir aux modifs de modificateurs). */
    @Query("SELECT * FROM meal_scans WHERE id = :id")
    fun observeScanById(id: Long): Flow<MealScanEntity?>

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

    // ──────────────────────────────────────────────────────────────────────
    //  v45 : modificateurs de portion ("j'en ai repris" + "j'ai pas fini")
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Met à jour le multiplicateur de portion d'un scan.
     * Valeur clampée par le caller dans [0.25, 10.0] — la DAO fait confiance.
     *
     * Side effect : les agrégations quotidiennes (getDayTotals) appliquent
     * automatiquement ce facteur via JOIN. Aucune autre table à toucher.
     */
    @Query("UPDATE meal_scans SET servingMultiplier = :multiplier WHERE id = :id")
    suspend fun updateServingMultiplier(id: Long, multiplier: Float)

    /**
     * Persiste un scan de restes : photo + macros parsées par OCR Gemini.
     * Les valeurs leftover* viennent s'ajouter aux autres modifs (multiplicateur)
     * et sont déduites lors de l'agrégation.
     *
     * `leftoverResultJson` contient le MealAnalysisResult du LLM pour traçabilité
     * (pourquoi on a déduit X kcal — auditable côté détail scan).
     */
    @Query("""
        UPDATE meal_scans SET
            leftoverPhotoPath = :photoPath,
            leftoverCalories = :calories,
            leftoverProteins = :proteins,
            leftoverCarbs = :carbs,
            leftoverFats = :fats,
            leftoverFibers = :fibers,
            leftoverWeight = :weight,
            leftoverResultJson = :resultJson,
            leftoverScannedAt = :scannedAt
        WHERE id = :id
    """)
    suspend fun updateLeftover(
        id: Long,
        photoPath: String?,
        calories: Int,
        proteins: Double,
        carbs: Double,
        fats: Double,
        fibers: Double,
        weight: Int,
        resultJson: String,
        scannedAt: LocalDateTime?,
    )

    /**
     * Reset complet du scan de restes (l'user annule). Remet à l'état neutre :
     * le repas redevient "100% mangé" du point de vue de l'agrégation.
     */
    @Query("""
        UPDATE meal_scans SET
            leftoverPhotoPath = NULL,
            leftoverCalories = 0,
            leftoverProteins = 0.0,
            leftoverCarbs = 0.0,
            leftoverFats = 0.0,
            leftoverFibers = 0.0,
            leftoverWeight = 0,
            leftoverResultJson = '',
            leftoverScannedAt = NULL
        WHERE id = :id
    """)
    suspend fun clearLeftover(id: Long)
}
