package com.shredcoach.app.data.backup.provider

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.shredcoach.app.data.backup.BackupArchive
import com.shredcoach.app.data.backup.BackupSettingsStore
import com.shredcoach.app.data.backup.PhotoEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider "local + SAF". Wrapper transparent autour du système existant —
 * ne casse rien, garde la logique historique pour les utilisateurs qui veulent
 * choisir leur dossier (interne, carte SD, ou un cloud monté en SAF type
 * Drive/OneDrive/Dropbox).
 */
@Singleton
class LocalSafBackupProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val archive: BackupArchive,
    private val settings: BackupSettingsStore,
) : BackupProvider {

    override val id: ProviderId = ProviderId.LOCAL_SAF

    override suspend fun isConfigured(): Boolean =
        settings.snapshot.first().folderUri != null

    override suspend fun uploadArchive(
        fileName: String,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        encryptionKey: ByteArray?,
    ): BackupProvider.UploadResult {
        val folderUri = settings.snapshot.first().folderUri
            ?: return BackupProvider.UploadResult.Failure("Aucun dossier SAF configuré")
        return runCatching {
            val createdUri = archive.packWithEntries(folderUri, fileName, manifestJson, photoFiles, encryptionKey)
            // SAF ne donne pas la taille fiablement post-write, on lit a posteriori
            val size = DocumentFile.fromSingleUri(context, createdUri)?.length() ?: 0L
            BackupProvider.UploadResult.Success(
                archiveId = createdUri.toString(),
                fileName = fileName,
                sizeBytes = size,
                uploadedAt = Instant.now(),
            )
        }.getOrElse { e ->
            Log.e(TAG, "SAF upload failed", e)
            BackupProvider.UploadResult.Failure(e.message ?: "Erreur SAF", e)
        }
    }

    override suspend fun listArchives(): List<RemoteArchive> {
        val folderUri = settings.snapshot.first().folderUri ?: return emptyList()
        return archive.listArchives(folderUri).map { doc ->
            RemoteArchive(
                id = doc.uri.toString(),
                name = doc.name ?: "",
                sizeBytes = doc.length(),
                createdAt = Instant.ofEpochMilli(doc.lastModified()),
                provider = ProviderId.LOCAL_SAF,
            )
        }
    }

    /**
     * Pour SAF, "downloader" = juste retourner le fichier accessible via
     * l'URI. Comme `BackupArchive.unpack` accepte une URI directement, on
     * copie le contenu en cache local pour respecter le contrat de l'interface
     * (le caller aura un File à lui).
     */
    override suspend fun downloadArchive(archive: RemoteArchive): File {
        val uri = Uri.parse(archive.id)
        val cacheFile = File(context.cacheDir, "saf_archive_${System.currentTimeMillis()}.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IOException("Impossible d'ouvrir $uri en lecture")
        return cacheFile
    }

    override suspend fun deleteArchive(archive: RemoteArchive): Boolean {
        val doc = DocumentFile.fromSingleUri(context, Uri.parse(archive.id)) ?: return false
        return runCatching { doc.delete() }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "LocalSafProvider"
    }
}
