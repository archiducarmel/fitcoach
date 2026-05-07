package com.shredcoach.app.data.backup.provider

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.shredcoach.app.data.auth.GoogleAuthRepository
import com.shredcoach.app.data.backup.BackupArchive
import com.shredcoach.app.data.backup.PhotoEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider Google Drive.
 *
 * **Scope** : `drive.appdata` — un dossier caché spécifique à ShredCoach,
 * invisible dans l'UI Drive de l'utilisateur. Modèle Whatsapp pour les
 * sauvegardes WA. L'utilisateur ne peut pas supprimer accidentellement ses
 * backups depuis l'UI Drive ; on est totalement maîtres du contenu.
 *
 * **Flow upload** :
 *  1. Récupérer un access token frais via [GoogleAuthRepository.getAccessTokenSilent].
 *     Si null → l'utilisateur a révoqué l'accès, on remonte une `Failure`
 *     explicite ; le caller (ViewModel ou Worker) doit déclencher la consent UI.
 *  2. Pack le ZIP vers un fichier temp en cacheDir via [BackupArchive.packToFile].
 *  3. Upload le fichier via Drive `Files.create()` avec parent = `appDataFolder`.
 *  4. Supprimer le fichier temp après succès (ou échec, peu importe).
 *
 * **Flow restore** :
 *  1. List les archives `appDataFolder` triées par modifiedTime DESC.
 *  2. Télécharge le fichier sélectionné via `Files.get(...).executeMediaAsInputStream()`.
 *  3. Sauve dans cacheDir.
 *  4. Le caller délègue à [BackupArchive.unpack] sur le fichier local.
 *
 * **Pas de Hilt-injectable Drive client cached** : on construit un nouveau
 * `Drive` à chaque opération avec un token frais. Coût : trivial (objet POJO).
 * Bénéfice : pas de gestion d'expiration de token côté state.
 */
@Singleton
class GoogleDriveBackupProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: GoogleAuthRepository,
    private val archive: BackupArchive,
) : BackupProvider {

    override val id: ProviderId = ProviderId.GOOGLE_DRIVE

    override suspend fun isConfigured(): Boolean =
        auth.currentSnapshot().isLinked

    override suspend fun uploadArchive(
        fileName: String,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        encryptionKey: ByteArray?,
    ): BackupProvider.UploadResult = withContext(Dispatchers.IO) {
        val token = auth.getAccessTokenSilent()
            ?: return@withContext BackupProvider.UploadResult.Failure(
                "Accès Google expiré. Reconnecte-toi à Google Drive."
            )

        val tempZip = File(context.cacheDir, "drive_upload_${System.currentTimeMillis()}.zip")
        try {
            // 1. Pack le ZIP local (avec encryption transparente si clé fournie)
            archive.packToFile(tempZip, manifestJson, photoFiles, encryptionKey)

            // 2. Upload via SDK Drive
            val drive = buildDriveClient(token)
            val metadata = DriveFile().apply {
                name = fileName
                parents = listOf(APP_DATA_FOLDER)
                mimeType = "application/zip"
            }
            val content = FileContent("application/zip", tempZip)
            val uploaded = drive.files().create(metadata, content)
                .setFields("id, name, size, modifiedTime, createdTime")
                .execute()

            BackupProvider.UploadResult.Success(
                archiveId = uploaded.id,
                fileName = uploaded.name ?: fileName,
                sizeBytes = uploaded.getSize() ?: tempZip.length(),
                uploadedAt = uploaded.modifiedTime?.value?.let(Instant::ofEpochMilli) ?: Instant.now(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Drive upload failed", e)
            BackupProvider.UploadResult.Failure(e.message ?: "Erreur Drive", e)
        } finally {
            runCatching { tempZip.delete() }
        }
    }

    override suspend fun listArchives(): List<RemoteArchive> = withContext(Dispatchers.IO) {
        val token = auth.getAccessTokenSilent() ?: return@withContext emptyList()
        runCatching {
            val drive = buildDriveClient(token)
            // Le scope appdata utilise un parent virtuel "appDataFolder".
            // Filtre on `name contains 'shredcoach_backup'` pour robustesse au cas
            // où d'autres apps écriraient dans appDataFolder (interdit normalement).
            val response = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains '${BackupArchive.FILE_PREFIX}' and trashed = false")
                .setFields("files(id, name, size, modifiedTime, createdTime)")
                .setOrderBy("modifiedTime desc")
                .setPageSize(100)
                .execute()

            response.files.orEmpty().map { f ->
                RemoteArchive(
                    id = f.id,
                    name = f.name ?: "",
                    sizeBytes = f.getSize() ?: 0L,
                    createdAt = f.modifiedTime?.value?.let(Instant::ofEpochMilli) ?: Instant.EPOCH,
                    provider = ProviderId.GOOGLE_DRIVE,
                )
            }
        }.getOrElse { e ->
            Log.e(TAG, "Drive list failed", e)
            emptyList()
        }
    }

    override suspend fun downloadArchive(archive: RemoteArchive): File = withContext(Dispatchers.IO) {
        val token = auth.getAccessTokenSilent()
            ?: throw IOException("Accès Google expiré — reconnecte-toi à Google Drive")
        val drive = buildDriveClient(token)
        val cacheFile = File(context.cacheDir, "drive_restore_${System.currentTimeMillis()}.zip")
        cacheFile.outputStream().use { out ->
            drive.files().get(archive.id)
                .executeMediaAndDownloadTo(out)
        }
        cacheFile
    }

    override suspend fun deleteArchive(archive: RemoteArchive): Boolean = withContext(Dispatchers.IO) {
        val token = auth.getAccessTokenSilent() ?: return@withContext false
        runCatching {
            val drive = buildDriveClient(token)
            drive.files().delete(archive.id).execute()
            true
        }.getOrElse { e ->
            Log.w(TAG, "Drive delete failed for ${archive.id}", e)
            false
        }
    }

    /**
     * Construit un client Drive ponctuel avec le token courant. À chaque op.
     *
     * **Pourquoi pas un singleton** : les access tokens expirent (~1h). Un
     * singleton avec le token initial deviendrait invalide. On préfère un
     * coût négligeable (init objet) à une logique de refresh complexe côté
     * client. L'auth refresh est délégué à `AuthorizationClient.authorize()`
     * qui répond silencieusement si toujours autorisé.
     */
    private fun buildDriveClient(accessToken: String): Drive {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val credentialInitializer = HttpRequestInitializer { request: HttpRequest ->
            request.headers.authorization = "Bearer $accessToken"
            // Timeouts généreux : un upload de 600Mo sur réseau cellulaire
            // peut prendre plusieurs minutes. Trop court = échec injuste.
            request.connectTimeout = 30_000
            request.readTimeout = 5 * 60_000
        }
        return Drive.Builder(transport, jsonFactory, credentialInitializer)
            .setApplicationName("ShredCoach")
            .build()
    }

    private companion object {
        const val TAG = "DriveProvider"
        /**
         * Identifiant magique du dossier caché app-specific (drive.appdata scope).
         * Tous les fichiers créés avec `parents = [APP_DATA_FOLDER]` sont
         * invisibles dans l'UI Drive de l'utilisateur, et listables uniquement
         * en spécifiant `spaces = "appDataFolder"`. Garanti par Google (pas
         * une convention).
         */
        const val APP_DATA_FOLDER = "appDataFolder"
    }
}
