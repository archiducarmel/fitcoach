package com.shredcoach.app.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.shredcoach.app.data.local.ShredCoachDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestre les opérations de backup et de restore de bout en bout.
 *
 * Pipeline backup :
 * 1. Lire le snapshot DB ([RoomSnapshotExporter])
 * 2. Énumérer les photos depuis le snapshot et les scanner ([BackupArchive.scanPhotos])
 * 3. Construire le [BackupManifest] avec versioning + tables + photos
 * 4. Sérialiser en JSON ([BackupGson])
 * 5. Pack ZIP via SAF ([BackupArchive.packWithEntries])
 * 6. Mettre à jour [BackupSettingsStore.lastBackupAt]
 * 7. Cleanup : appliquer la rétention (max [MAX_ARCHIVES] archives conservées)
 *
 * Pipeline restore :
 * 1. Unpack ZIP ([BackupArchive.unpack])
 * 2. Parser le manifest, **vérifier les versions** (refus si format ou Room
 *    version trop récents → safety net principal pour la promesse "ne pas
 *    perdre les données")
 * 3. Patcher les paths photo dans le snapshot avant insertion
 * 4. Importer atomiquement ([RoomSnapshotImporter])
 *
 * Toute exception remonte au caller (ViewModel ou Worker), qui choisit
 * la stratégie de retry. Cette couche reste **stateless** — pas de retry
 * interne, pas de cache.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ShredCoachDatabase,
    private val settings: BackupSettingsStore,
    private val exporter: RoomSnapshotExporter,
    private val importer: RoomSnapshotImporter,
    private val archive: BackupArchive,
    private val photoArchiver: PhotoArchiver,
) {
    /**
     * Snapshot observable de l'état utilisateur. Combine SettingsStore +
     * meta dérivée pour l'UI Settings (Tier 4 V3).
     */
    val state: Flow<State> = settings.snapshot.map { snap ->
        State(
            isConfigured = snap.isConfigured,
            folderUri = snap.folderUri,
            lastBackupAt = snap.lastBackupAt,
            autoBackupEnabled = snap.autoBackupEnabled,
        )
    }

    /**
     * Lance un backup complet. Stateful : modifie [BackupSettingsStore] en cas
     * de succès. Logue uniquement la **meta** (pas le contenu) — voir docstring
     * [BackupManifest] pour la justification sécurité.
     */
    suspend fun runBackup(): BackupResult {
        val snap = settings.snapshot.first()
        val folderUri = snap.folderUri
            ?: return BackupResult.Failure("Aucun dossier de backup configuré")

        return runCatching {
            // 1. Snapshot DB
            val tables = exporter.export()

            // 2. Énumérer les photos sur disque (filePath des entités).
            val photoPaths = buildList {
                tables.progressPhotos.forEach { add(it.filePath) }
                tables.mealScans.mapNotNull { it.photoPath }.forEach { add(it) }
            }
            val scanned = archive.scanPhotos(photoPaths)

            // 3. Construire le manifest
            val manifest = BackupManifest(
                backupSchemaVersion = BackupManifest.BACKUP_SCHEMA_VERSION,
                roomDbVersion = ROOM_DB_VERSION,
                appVersionCode = appVersionCode(),
                appVersionName = appVersionName(),
                exportedAt = Instant.now().toString(),
                tables = tables,
                photos = scanned.map { it.first },
            )

            // 4. Sérialisation
            val manifestJson = BackupGson.instance.toJson(manifest)

            // 5. Pack
            val fileName = buildFileName(Instant.now())
            archive.packWithEntries(folderUri, fileName, manifestJson, scanned)

            // 6. Update settings
            val now = Instant.now()
            settings.setLastBackupAt(now)

            // 7. Cleanup rétention (best-effort, ne bloque pas le succès)
            runCatching { applyRetention(folderUri) }
                .onFailure { Log.w(TAG, "Retention cleanup failed (non-bloquant)", it) }

            Log.i(TAG, "Backup OK: ${tables.workoutLogs.size} séances, ${scanned.size} photos")
            BackupResult.Success(at = now, fileName = fileName, photosCount = scanned.size)
        }.getOrElse { e ->
            Log.e(TAG, "Backup failed", e)
            BackupResult.Failure(e.message ?: "Erreur inconnue")
        }
    }

    /**
     * Restaure une archive sélectionnée par l'utilisateur. Atomique :
     * si une étape échoue, la DB conserve son état d'avant-restore.
     */
    suspend fun runRestore(archiveUri: Uri): RestoreResult {
        return runCatching {
            // 1. Unpack
            val unpack = archive.unpack(archiveUri)

            // 2. Parse + validation versions
            val manifest = BackupGson.instance.fromJson(unpack.manifestJson, BackupManifest::class.java)
                ?: throw IllegalArgumentException("Manifest illisible (JSON corrompu)")
            validateVersions(manifest)

            // 3. Patch photo paths : map originalPath → newLocalPath
            //    pour que les rows DB pointent vers les fichiers extraits.
            val originalToNew = manifest.photos.mapNotNull { entry ->
                val newPath = unpack.archivePathToLocalPath[entry.archivePath] ?: return@mapNotNull null
                entry.originalPath to newPath
            }.toMap()

            val patchedTables = manifest.tables.copy(
                progressPhotos = manifest.tables.progressPhotos.map { row ->
                    val newPath = originalToNew[row.filePath] ?: row.filePath
                    if (newPath == row.filePath) row else row.copy(filePath = newPath)
                },
                mealScans = manifest.tables.mealScans.map { row ->
                    val originalPath = row.photoPath ?: return@map row
                    val newPath = originalToNew[originalPath] ?: originalPath
                    if (newPath == originalPath) row else row.copy(photoPath = newPath)
                },
            )

            // 4. Import atomique
            importer.import(patchedTables)

            Log.i(TAG, "Restore OK: ${patchedTables.workoutLogs.size} séances, ${originalToNew.size} photos restaurées (${unpack.skippedEntries.size} skippées)")
            RestoreResult.Success(
                exportedAt = manifest.exportedAt,
                photosCount = originalToNew.size,
                skippedPhotos = unpack.skippedEntries.size,
            )
        }.getOrElse { e ->
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Failure(e.message ?: "Erreur inconnue")
        }
    }

    /**
     * Vérifie qu'on est capable de lire ce manifest sans corrompre les données.
     * Refuse si :
     * - Le format est plus récent que ce qu'on sait lire (v2 dans une app v1)
     * - Le schéma Room est plus récent que la DB courante (entités potentiellement
     *   incompatibles, NOT NULL columns absentes du JSON)
     *
     * Backups plus anciens (Room v33 dans une app Room v34) → ACCEPTÉS, en
     * comptant sur la lenient parsing de Gson : champs manquants = défauts
     * Kotlin, champs en trop = ignorés.
     */
    private fun validateVersions(manifest: BackupManifest) {
        if (manifest.backupSchemaVersion > BackupManifest.BACKUP_SCHEMA_VERSION) {
            throw IllegalStateException(
                "Backup au format v${manifest.backupSchemaVersion}, app supporte v${BackupManifest.BACKUP_SCHEMA_VERSION} max. Mets à jour ShredCoach."
            )
        }
        if (manifest.roomDbVersion > ROOM_DB_VERSION) {
            throw IllegalStateException(
                "Backup d'une version d'app plus récente (DB v${manifest.roomDbVersion} > v$ROOM_DB_VERSION). Mets à jour ShredCoach."
            )
        }
    }

    /**
     * Applique la rétention : on conserve les [MAX_ARCHIVES] archives les plus
     * récentes, on supprime les autres. Best-effort — si une suppression échoue
     * (permissions SAF révoquées, etc.), on log et on continue.
     *
     * V2 : politique simple "N most recent". V2.5 ajoutera la politique
     * 7 quotidiens + 4 hebdomadaires + 6 mensuels pour économiser le stockage
     * cloud sur les longs historiques.
     */
    private fun applyRetention(folderUri: Uri) {
        val all = archive.listArchives(folderUri)
        val toDelete = all.drop(MAX_ARCHIVES)
        for (file in toDelete) {
            runCatching { file.delete() }
                .onFailure { Log.w(TAG, "Suppression archive ${file.name} échouée", it) }
        }
        if (toDelete.isNotEmpty()) {
            Log.i(TAG, "Retention: ${toDelete.size} archives supprimées (${all.size} → ${MAX_ARCHIVES})")
        }
    }

    private fun buildFileName(at: Instant): String {
        val ts = ISO_FILE.format(at.atOffset(ZoneOffset.UTC))
        return "${BackupArchive.FILE_PREFIX}${ts}.zip"
    }

    private fun appVersionCode(): Int = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
    }.getOrDefault(0)

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    data class State(
        val isConfigured: Boolean,
        val folderUri: Uri?,
        val lastBackupAt: Instant?,
        val autoBackupEnabled: Boolean,
    )

    sealed interface BackupResult {
        data class Success(val at: Instant, val fileName: String, val photosCount: Int) : BackupResult
        data class Failure(val message: String) : BackupResult
    }

    sealed interface RestoreResult {
        data class Success(val exportedAt: String, val photosCount: Int, val skippedPhotos: Int) : RestoreResult
        data class Failure(val message: String) : RestoreResult
    }

    private companion object {
        const val TAG = "BackupRepository"
        const val MAX_ARCHIVES = 30
        /**
         * Version Room **courante**, à incrémenter manuellement quand
         * [com.shredcoach.app.data.local.ShredCoachDatabase] passe à v35, v36...
         * Hardcodée car la version Room n'est pas exposée à runtime de manière
         * propre — `db.openHelper.readableDatabase.version` fonctionne mais
         * c'est un round-trip SQLite à chaque export, peu utile.
         */
        const val ROOM_DB_VERSION = 34
        val ISO_FILE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
    }
}
