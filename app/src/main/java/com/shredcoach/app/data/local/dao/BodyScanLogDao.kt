package com.shredcoach.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shredcoach.app.data.local.entity.BodyScanLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO de la table d'historique des scans corporels.
 *
 * **Patterns** :
 *  - `observeXxx()` : Flow réactif pour la UI (Dashboard timeline, History).
 *  - `getAllOnce()` : suspend snapshot pour l'export Backup (transactionnel).
 *  - `deleteById` : suppression manuelle depuis l'écran historique.
 *
 * **Pas de query par range de date** : toutes les UI consomment soit `recent N`
 * soit `all`. On filtrera par date côté Compose plutôt que de pousser plusieurs
 * variantes de query.
 */
@Dao
interface BodyScanLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BodyScanLogEntity): Long

    /**
     * Observable réactif de tous les scans, ordonnés du plus récent au plus
     * ancien. Utilisé par la timeline Dashboard et l'écran historique.
     */
    @Query("SELECT * FROM body_scan_logs ORDER BY capturedAtMs DESC")
    fun observeAll(): Flow<List<BodyScanLogEntity>>

    /**
     * Subset récent — limite la pression mémoire pour des dashboards qui ne
     * montrent que les N derniers points sans re-récupérer 200 rows à chaque
     * recomposition.
     */
    @Query("SELECT * FROM body_scan_logs ORDER BY capturedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BodyScanLogEntity>>

    /**
     * Snapshot synchrone — utilisé par [com.shredcoach.app.data.backup.RoomSnapshotExporter]
     * pour l'export ZIP. Doit être suspend (Room IO).
     */
    @Query("SELECT * FROM body_scan_logs ORDER BY capturedAtMs DESC")
    suspend fun getAllOnce(): List<BodyScanLogEntity>

    @Query("DELETE FROM body_scan_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Cleanup full (utilisé par DataPurger RGPD). Ne supprime PAS les fichiers
     * JSON / photos sur disque — c'est le rôle de DataPurger qui orchestre
     * le wipe complet.
     */
    @Query("DELETE FROM body_scan_logs")
    suspend fun clearAll()
}
