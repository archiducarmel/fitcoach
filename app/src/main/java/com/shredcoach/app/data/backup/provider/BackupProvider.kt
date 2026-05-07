package com.shredcoach.app.data.backup.provider

import com.shredcoach.app.data.backup.PhotoEntry
import java.io.File
import java.time.Instant

/**
 * Abstraction du "où" stocker les archives ZIP de backup.
 *
 * **Pourquoi cette interface** : on a deux providers actuels :
 *  - [LocalSafBackupProvider] : SAF (DocumentFile), permet local OU n'importe
 *    quel cloud monté (Drive, OneDrive, Dropbox…) via le picker système.
 *  - [GoogleDriveBackupProvider] : API Drive REST directe, scope `drive.appdata`
 *    (modèle Whatsapp). Sync transparente, pas de picker, dossier caché.
 *
 * Le [com.shredcoach.app.data.backup.BackupRepository] ne sait rien du transport :
 * il packe le ZIP et délègue le `upload`/`list`/`download`/`delete` au provider
 * sélectionné par l'utilisateur via [com.shredcoach.app.data.backup.BackupSettingsStore].
 *
 * **Streaming first** : tous les uploads/downloads passent par des Stream pour
 * éviter de matérialiser des archives de plusieurs centaines de Mo en RAM
 * (power user nutrition photos). Chaque provider est libre de buffer en local
 * cache si nécessaire (ex: Drive resumable upload qui requiert un fichier).
 */
interface BackupProvider {

    /** Identifiant stable persisté en préférences. */
    val id: ProviderId

    /** True si l'utilisateur a configuré ce provider (folder pické, compte linké…). */
    suspend fun isConfigured(): Boolean

    /**
     * Upload une archive ZIP fraîchement packée.
     *
     * @param fileName Nom de fichier (ex: `shredcoach_backup_2026-05-07T03-00-00Z.zip`).
     * @param manifestJson JSON du manifest, à intégrer dans le ZIP (1ère entry).
     * @param photoFiles Liste des photos à inclure (entry meta + fichier source).
     * @return Métadonnées de l'archive uploadée (id, taille, date).
     */
    suspend fun uploadArchive(
        fileName: String,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        encryptionKey: ByteArray? = null,
    ): UploadResult

    /**
     * Liste les archives disponibles, triées par date décroissante (récente en
     * premier). Sert au restore picker + à la rétention.
     */
    suspend fun listArchives(): List<RemoteArchive>

    /**
     * Télécharge l'archive identifiée par [archive] dans un fichier de cache
     * local. Le caller doit supprimer le fichier après usage. Renvoie le
     * chemin local du ZIP téléchargé prêt à être unpack via
     * [com.shredcoach.app.data.backup.BackupArchive.unpack].
     */
    suspend fun downloadArchive(archive: RemoteArchive): File

    /** Suppression d'une archive — utilisé par la rétention (max N archives). */
    suspend fun deleteArchive(archive: RemoteArchive): Boolean

    sealed interface UploadResult {
        data class Success(
            val archiveId: String,
            val fileName: String,
            val sizeBytes: Long,
            val uploadedAt: Instant,
        ) : UploadResult
        data class Failure(val reason: String, val cause: Throwable? = null) : UploadResult
    }
}

enum class ProviderId(val storageKey: String) {
    LOCAL_SAF("local_saf"),
    GOOGLE_DRIVE("google_drive");

    companion object {
        fun fromStorageKey(key: String?): ProviderId =
            entries.firstOrNull { it.storageKey == key } ?: LOCAL_SAF
    }
}

/**
 * Référence à une archive distante. Provider-agnostic : pour SAF c'est un
 * DocumentFile uri+name ; pour Drive c'est un fileId Google.
 */
data class RemoteArchive(
    /** Id stable utilisé pour download/delete. Provider-spécifique. */
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAt: Instant,
    val provider: ProviderId,
)
