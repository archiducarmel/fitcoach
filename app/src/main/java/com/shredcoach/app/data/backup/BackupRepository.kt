package com.shredcoach.app.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.shredcoach.app.data.auth.GoogleAuthRepository
import com.shredcoach.app.data.backup.crypto.BackupKeyManager
import com.shredcoach.app.data.backup.provider.BackupProvider
import com.shredcoach.app.data.backup.provider.GoogleDriveBackupProvider
import com.shredcoach.app.data.backup.provider.LocalSafBackupProvider
import com.shredcoach.app.data.backup.provider.ProviderId
import com.shredcoach.app.data.backup.provider.RemoteArchive
import com.shredcoach.app.data.local.ShredCoachDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val localSafProvider: LocalSafBackupProvider,
    private val driveProvider: GoogleDriveBackupProvider,
    private val googleAuth: GoogleAuthRepository,
    private val keyManager: BackupKeyManager,
) {

    /**
     * Sélectionne le provider courant en fonction des préférences. Pas de
     * registry à la Hilt pour rester simple : 2 providers seulement, switch
     * explicite. Si on en ajoute un 3e, refactor en map injectable.
     */
    private fun providerFor(id: ProviderId): BackupProvider = when (id) {
        ProviderId.LOCAL_SAF -> localSafProvider
        ProviderId.GOOGLE_DRIVE -> driveProvider
    }

    /**
     * Snapshot observable de l'état utilisateur. Combine SettingsStore +
     * GoogleAuthStore + dérivé `isConfigured` (provider-dependent) pour l'UI.
     */
    val state: Flow<State> = combine(
        settings.snapshot,
        googleAuth.state,
        keyManager.isEnabled,
    ) { settingsSnap, authSnap, encryptionEnabled ->
        val configured = when (settingsSnap.providerId) {
            ProviderId.LOCAL_SAF -> settingsSnap.folderUri != null
            ProviderId.GOOGLE_DRIVE -> authSnap.isLinked
        }
        State(
            providerId = settingsSnap.providerId,
            isConfigured = configured,
            folderUri = settingsSnap.folderUri,
            googleAccountEmail = authSnap.linkedEmail.takeIf { authSnap.isLinked },
            lastBackupAt = settingsSnap.lastBackupAt,
            autoBackupEnabled = settingsSnap.autoBackupEnabled,
            encryptionEnabled = encryptionEnabled,
        )
    }

    /**
     * Lance un backup complet via le provider sélectionné. Stateful : modifie
     * [BackupSettingsStore.lastBackupAt] en cas de succès. Logue uniquement la
     * **meta** (pas le contenu) — voir docstring [BackupManifest] pour la
     * justification sécurité.
     */
    suspend fun runBackup(): BackupResult {
        val snap = settings.snapshot.first()
        val provider = providerFor(snap.providerId)

        if (!provider.isConfigured()) {
            return BackupResult.Failure(
                "Sauvegarde non configurée (provider ${snap.providerId}). Va dans Paramètres → Sauvegarde."
            )
        }

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

            // 5. Upload via provider (avec clé d'encryption si activée)
            val fileName = buildFileName(Instant.now())
            val key = keyManager.keyOrNull()
            val uploadResult = provider.uploadArchive(fileName, manifestJson, scanned, key)
            when (uploadResult) {
                is BackupProvider.UploadResult.Failure -> {
                    Log.e(TAG, "Upload failed via ${snap.providerId}: ${uploadResult.reason}", uploadResult.cause)
                    return@runCatching BackupResult.Failure(uploadResult.reason)
                }
                is BackupProvider.UploadResult.Success -> {
                    // 6. Update settings
                    settings.setLastBackupAt(uploadResult.uploadedAt)

                    // 7. Cleanup rétention (best-effort, ne bloque pas le succès)
                    runCatching { applyRetention(provider) }
                        .onFailure { Log.w(TAG, "Retention cleanup failed (non-bloquant)", it) }

                    Log.i(TAG, "Backup OK [${snap.providerId}]: ${tables.workoutLogs.size} séances, ${scanned.size} photos, ${uploadResult.sizeBytes / 1024} Ko")
                    BackupResult.Success(
                        at = uploadResult.uploadedAt,
                        fileName = uploadResult.fileName,
                        photosCount = scanned.size,
                        sizeBytes = uploadResult.sizeBytes,
                    )
                }
            }
        }.getOrElse { e ->
            Log.e(TAG, "Backup failed", e)
            BackupResult.Failure(e.message ?: "Erreur inconnue")
        }
    }

    /**
     * Liste les archives disponibles pour le restore picker. Délègue au provider.
     */
    suspend fun listRemoteArchives(): List<RemoteArchive> {
        val snap = settings.snapshot.first()
        val provider = providerFor(snap.providerId)
        return runCatching { provider.listArchives() }
            .onFailure { Log.e(TAG, "List archives failed via ${snap.providerId}", it) }
            .getOrDefault(emptyList())
    }

    /**
     * Restaure une archive distante (Drive ou SAF). Télécharge si nécessaire
     * puis délègue au pipeline classique.
     *
     * @param recoveryCode Optionnel — passé tel quel à [runRestore] pour le
     *   cas "archive chiffrée + nouveau téléphone".
     */
    suspend fun runRestoreFromRemote(
        remote: RemoteArchive,
        recoveryCode: ByteArray? = null,
    ): RestoreResult {
        val provider = providerFor(remote.provider)
        val localFile = runCatching { provider.downloadArchive(remote) }
            .getOrElse { e ->
                Log.e(TAG, "Download failed for ${remote.id}", e)
                return RestoreResult.Failure("Téléchargement de l'archive impossible : ${e.message}")
            }
        return try {
            runRestore(Uri.fromFile(localFile), recoveryCode)
        } finally {
            runCatching { localFile.delete() }
        }
    }

    /**
     * Restaure une archive sélectionnée par l'utilisateur. Atomique :
     * si une étape échoue, la DB conserve son état d'avant-restore.
     *
     * @param archiveUri URI de l'archive ZIP (file:// pour les downloads cloud,
     *   content:// pour le SAF picker).
     * @param recoveryCode Code de récupération optionnel (32 bytes décodés).
     *   Fourni quand l'user paste son code via le dialog "archive chiffrée +
     *   pas de clé locale" — on l'importe puis on l'utilise pour décrypter.
     *   Sinon on essaie d'abord la clé locale (KeyManager), et si l'archive
     *   est chiffrée mais qu'on n'a pas de clé, on remonte
     *   [RestoreResult.NeedsRecoveryCode] au caller.
     */
    suspend fun runRestore(archiveUri: Uri, recoveryCode: ByteArray? = null): RestoreResult {
        return runCatching {
            // **N'importe PAS la clé tant que le restore n'a pas réussi** : si
            // l'user a tapé un code erroné, la décryption va échouer (GCM tag
            // mismatch). Persister la clé en amont écraserait la vraie clé
            // précédente. → On garde la clé en RAM pour ce restore, on la
            // persiste seulement après succès en bas de la fonction.
            val key = recoveryCode ?: keyManager.keyOrNull()
            // 1. Unpack — peut throw EncryptedArchiveException si chiffré sans clé
            val unpack = archive.unpack(archiveUri, key)

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

            // 5. **Maintenant** que le restore a réussi de bout en bout, on
            // persiste la clé saisie par l'user pour les futures ops (sinon,
            // saisir le code à chaque restore — friction inutile sur un device
            // où on vient de prouver que la clé est valide).
            if (recoveryCode != null) keyManager.importKey(recoveryCode)

            Log.i(TAG, "Restore OK: ${patchedTables.workoutLogs.size} séances, ${originalToNew.size} photos restaurées (${unpack.skippedEntries.size} skippées)")
            RestoreResult.Success(
                exportedAt = manifest.exportedAt,
                photosCount = originalToNew.size,
                skippedPhotos = unpack.skippedEntries.size,
            )
        }.getOrElse { e ->
            Log.e(TAG, "Restore failed", e)
            // Cas spécifique : archive chiffrée mais pas de clé → remonter au
            // caller pour qu'il prompte l'user. Pas une erreur logique.
            if (e is BackupArchive.EncryptedArchiveException) {
                RestoreResult.NeedsRecoveryCode(e.message ?: "Archive chiffrée — code de récupération requis")
            } else {
                RestoreResult.Failure(e.message ?: "Erreur inconnue")
            }
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
     * (permissions SAF révoquées, token Drive expiré, etc.), on log et on continue.
     *
     * Provider-agnostique : les archives sont listées et supprimées via la même
     * interface, qu'elles soient SAF ou Drive.
     */
    private suspend fun applyRetention(provider: BackupProvider) {
        val all = provider.listArchives()
        val toDelete = all.drop(MAX_ARCHIVES)
        for (rem in toDelete) {
            runCatching { provider.deleteArchive(rem) }
                .onFailure { Log.w(TAG, "Suppression archive ${rem.name} échouée", it) }
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
        val providerId: ProviderId,
        val isConfigured: Boolean,
        /** Non-null uniquement pour LOCAL_SAF. */
        val folderUri: Uri?,
        /** Non-null uniquement pour GOOGLE_DRIVE quand linké. */
        val googleAccountEmail: String?,
        val lastBackupAt: Instant?,
        val autoBackupEnabled: Boolean,
        /** True si l'encryption AES-GCM est activée (clé maître présente). */
        val encryptionEnabled: Boolean,
    )

    sealed interface BackupResult {
        data class Success(
            val at: Instant,
            val fileName: String,
            val photosCount: Int,
            val sizeBytes: Long,
        ) : BackupResult
        data class Failure(val message: String) : BackupResult
    }

    sealed interface RestoreResult {
        data class Success(val exportedAt: String, val photosCount: Int, val skippedPhotos: Int) : RestoreResult
        data class Failure(val message: String) : RestoreResult
        /**
         * Archive chiffrée mais aucune clé locale (cas restore sur un nouveau
         * téléphone). Le caller doit prompter l'user pour son code de
         * récupération, puis re-appeler [runRestore] / [runRestoreFromRemote]
         * avec le `recoveryCode`.
         */
        data class NeedsRecoveryCode(val message: String) : RestoreResult
    }

    private companion object {
        const val TAG = "BackupRepository"
        const val MAX_ARCHIVES = 30
        /**
         * Version Room **courante**, à incrémenter manuellement quand
         * [com.shredcoach.app.data.local.ShredCoachDatabase] passe à v35, v36, v37...
         * Hardcodée car la version Room n'est pas exposée à runtime de manière
         * propre — `db.openHelper.readableDatabase.version` fonctionne mais
         * c'est un round-trip SQLite à chaque export, peu utile.
         */
        const val ROOM_DB_VERSION = 38
        val ISO_FILE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
    }
}
