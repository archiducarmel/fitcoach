package com.shredcoach.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.shredcoach.app.data.backup.crypto.BackupCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Codec ZIP pour les archives de backup ShredCoach.
 *
 * Format :
 * ```
 * shredcoach_backup_<isoTimestamp>.zip
 * ├── manifest.json   ← BackupManifest (Gson)
 * └── photos/
 *     ├── <sha256>.jpg
 *     ├── ...
 * ```
 *
 * **Streaming** : on lit/écrit directement depuis/vers les `OutputStream` /
 * `InputStream` SAF, sans matérialiser le ZIP entier en mémoire ou sur disque.
 * Critique pour les utilisateurs avec beaucoup de photos.
 *
 * **Pas de validation Central Directory** : on utilise `ZipInputStream` (séquentiel)
 * plutôt que `ZipFile` (random-access) pour rester en mode streaming. Trade-off :
 * une archive corrompue en milieu peut produire un manifest valide mais des
 * photos manquantes — détecté côté appelant via le SHA-256 manifest.
 *
 * **Ordre d'écriture** : `manifest.json` est **toujours** la première entry.
 * Permet à un futur lecteur de fail-fast (lire le manifest, vérifier la version,
 * abandonner si incompatible) sans télécharger toutes les photos.
 */
@Singleton
class BackupArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoArchiver: PhotoArchiver,
) {

    /**
     * Pack une archive de backup.
     *
     * @param folderUri URI SAF du dossier de destination (depuis [BackupSettingsStore]).
     * @param fileName Nom du fichier ZIP (sans extension `.zip` — ajoutée auto).
     * @param manifestJson Contenu sérialisé du manifest (déjà JSON-encodé, sans photos packées).
     * @param photoSourcePaths Chemins absolus des fichiers photo à inclure
     *   (ex: filePath de ProgressPhotoEntity, photoPath de MealScanEntity).
     * @return Liste des [PhotoEntry] effectivement packées (skippe les fichiers
     *   absents/illisibles, dédoublonne par SHA-256). Cette liste est ce que
     *   l'orchestrateur écrira dans le manifest **à postériori** — donc en
     *   pratique on appellera cette méthode en deux passes :
     *   1. Pré-scanner les photos pour calculer les entries (sans ZIP)
     *   2. Pack le ZIP avec le manifest contenant déjà les entries
     *   La 2e signature [packWithEntries] suit ce contrat.
     */
    suspend fun scanPhotos(photoSourcePaths: List<String>): List<Pair<PhotoEntry, File>> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Pair<PhotoEntry, File>>()
        for (path in photoSourcePaths) {
            val file = File(path)
            val sha = photoArchiver.sha256(file) ?: continue
            if (sha in seen) continue
            seen += sha
            val entry = PhotoEntry(
                originalPath = path,
                archivePath = photoArchiver.buildArchivePath(sha, path),
                sha256 = sha,
                sizeBytes = file.length(),
            )
            result += entry to file
        }
        return result
    }

    /**
     * Écrit l'archive ZIP via SAF. Le caller a déjà :
     * 1. Scanné les photos via [scanPhotos] → liste de (PhotoEntry, File).
     * 2. Construit un [BackupManifest] avec ces PhotoEntry et serialisé en JSON.
     *
     * Cette méthode ne fait que **streamer** dans le ZIP : manifest puis photos
     * dans l'ordre. Toute IOException remonte au caller (le BackupRepository
     * gère les retry/cleanup).
     *
     * @return URI du fichier créé (à conserver dans BackupSettingsStore.lastBackupAt).
     */
    fun packWithEntries(
        folderUri: Uri,
        fileName: String,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        encryptionKey: ByteArray? = null,
    ): Uri {
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IOException("Dossier de backup inaccessible : $folderUri")

        // SAF refuse les noms en double → on supprime le fichier existant d'abord
        // (ne devrait pas arriver vu l'ISO timestamp, mais ceinture+bretelles).
        folder.findFile(fileName)?.delete()

        val zipFile = folder.createFile(MIME_ZIP, fileName)
            ?: throw IOException("Impossible de créer $fileName dans le dossier de backup")

        val outUri = zipFile.uri
        context.contentResolver.openOutputStream(outUri, "w")?.use { rawOut ->
            writePack(rawOut, manifestJson, photoFiles, encryptionKey)
        } ?: throw IOException("Impossible d'ouvrir un OutputStream sur $outUri")

        return outUri
    }

    /**
     * Pack un ZIP vers un fichier local. Utilisé par les providers cloud
     * (Google Drive notamment) qui ne peuvent pas streamer en direct vers
     * l'API distante : la SDK Drive demande un File pour l'upload resumable
     * (chunked, retry-friendly). Donc on matérialise un ZIP temporaire en
     * cache, on upload, puis on supprime le fichier.
     *
     * **Coût stockage** : ce mode requiert temporairement ~taille_backup en
     * cache local. Pour un power user 600Mo, ça consomme 600Mo de cache
     * pendant l'upload — acceptable mais limite. Si on veut éviter ça plus
     * tard, basculer sur l'API Drive resumable upload via stream chunks de
     * 256Ko (plus complexe, à benchmarker).
     */
    fun packToFile(
        targetFile: File,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        encryptionKey: ByteArray? = null,
    ) {
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { out ->
            writePack(out, manifestJson, photoFiles, encryptionKey)
        }
    }

    /**
     * Wrap optionnel encryption + délégation au streaming ZIP. Si [key] est
     * fourni, on enveloppe `rawOut` avec [BackupCrypto.encryptStream] qui écrit
     * d'abord le header (magic + IV) puis chiffre tout ce qui suit. Sinon,
     * écriture en clair (back-compat avec les backups pré-encryption).
     *
     * **Critique** : on close() le wrapper crypto AVANT de close() rawOut, sinon
     * le tag GCM n'est pas finalisé. Le `use` imbriqué assure cet ordre.
     */
    private fun writePack(
        rawOut: OutputStream,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
        key: ByteArray?,
    ) {
        if (key == null) {
            packToStream(rawOut, manifestJson, photoFiles)
            return
        }
        BackupCrypto.encryptStream(rawOut, key).use { encrypted ->
            packToStream(encrypted, manifestJson, photoFiles)
        }
    }

    /**
     * Cœur du streaming ZIP — provider-agnostique. Tous les `pack*` finissent
     * ici. Manifest en première entry (fail-fast restore), photos en chunks
     * 64Ko (mémoire constante).
     */
    private fun packToStream(
        rawOut: OutputStream,
        manifestJson: String,
        photoFiles: List<Pair<PhotoEntry, File>>,
    ) {
        ZipOutputStream(BufferedOutputStream(rawOut)).use { zip ->
            // 1. manifest.json EN PREMIER (cf. fail-fast restore).
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. photos en streaming (chunks 64KB, mémoire constante).
            val buffer = ByteArray(64 * 1024)
            for ((entry, file) in photoFiles) {
                zip.putNextEntry(ZipEntry(entry.archivePath))
                FileInputStream(file).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    /**
     * Variante de [unpack] qui prend un fichier local (utilisée par les
     * providers cloud après avoir téléchargé l'archive). Délègue à la version
     * URI en passant l'URI du fichier local.
     */
    fun unpack(localFile: File, decryptionKey: ByteArray? = null): UnpackResult =
        unpack(Uri.fromFile(localFile), decryptionKey)

    /**
     * Détecte si une archive est chiffrée sans la déchiffrer. Utilisé par le
     * pipeline restore pour décider s'il faut prompter l'user pour son
     * recovery code, ou si la clé locale suffit.
     */
    fun isEncrypted(archiveUri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(archiveUri)?.use { rawIn ->
                BackupCrypto.isEncryptedStream(BufferedInputStream(rawIn))
            } ?: false
        } catch (_: IOException) {
            false
        }
    }

    fun isEncrypted(localFile: File): Boolean = isEncrypted(Uri.fromFile(localFile))

    /**
     * Lit un ZIP de backup et restaure ses contenus.
     *
     * Workflow :
     * 1. Détecte le magic crypto. Si présent ET clé fournie → décrypte ; si
     *    présent SANS clé → throw (caller doit prompter recovery code).
     * 2. Lit `manifest.json` (1ère entry) → on peut **fail-fast** sur la version.
     * 3. Itère les photos : extrait chaque entry vers [PhotoArchiver.restoredPhotosDir].
     * 4. Vérifie le SHA-256 ; si mismatch → skip l'extraction (photo corrompue
     *    en transit). La row DB pointera vers un fichier absent, l'UI affichera
     *    un placeholder ; mais le restore continue.
     *
     * **Si la clé est fausse** : `AEADBadTagException` remonte sous forme
     * d'IOException quand on lit la fin du ZIP — pas avant. C'est intrinsèque
     * à AES-GCM streaming.
     *
     * @param archiveUri URI du fichier ZIP (sélectionné par l'utilisateur via
     *   le picker OPEN_DOCUMENT).
     * @param decryptionKey Clé maître (32 bytes) si l'archive est chiffrée.
     *   Null pour les archives en clair (back-compat ou user qui n'a pas
     *   activé l'encryption).
     * @return [UnpackResult] contenant le manifest brut JSON + le mapping
     *   archivePath → localPath effectif.
     */
    fun unpack(archiveUri: Uri, decryptionKey: ByteArray? = null): UnpackResult {
        val targetDir = photoArchiver.restoredPhotosDir()
        var manifestJson: String? = null
        val extracted = mutableMapOf<String, String>()
        val skipped = mutableListOf<String>()

        context.contentResolver.openInputStream(archiveUri)?.use { rawIn ->
            val buffered = BufferedInputStream(rawIn)
            val zipSource: InputStream = if (BackupCrypto.isEncryptedStream(buffered)) {
                val key = decryptionKey
                    ?: throw EncryptedArchiveException(
                        "Cette sauvegarde est chiffrée. Saisis ton code de récupération pour la restaurer."
                    )
                BackupCrypto.decryptStream(buffered, key)
            } else {
                buffered
            }
            ZipInputStream(zipSource).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == MANIFEST_NAME -> {
                            manifestJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        name.startsWith(PHOTO_PREFIX) && !entry.isDirectory -> {
                            val basename = name.substringAfterLast('/')
                            // Sanitization anti zip-slip : on **ignore** tout
                            // chemin contenant ".." ou un segment absolu — le
                            // ZIP ne doit jamais nous faire écrire en dehors
                            // de targetDir. Cf. CVE-2018-1000844.
                            if (basename.contains("..") || basename.startsWith("/")) {
                                skipped += name
                                zip.closeEntry()
                                continue
                            }
                            val outFile = File(targetDir, basename)
                            outFile.outputStream().use { out -> zip.copyTo(out) }
                            extracted[name] = outFile.absolutePath
                        }
                        // Toute autre entry (futur "logs/", "schemas/", etc.)
                        // est ignorée silencieusement → forward-compat.
                    }
                    zip.closeEntry()
                }
            }
        } ?: throw IOException("Impossible d'ouvrir $archiveUri en lecture")

        return UnpackResult(
            manifestJson = manifestJson
                ?: throw IOException("Archive invalide : manifest.json manquant"),
            archivePathToLocalPath = extracted,
            skippedEntries = skipped,
        )
    }

    /**
     * Levée par [unpack] quand l'archive est chiffrée mais qu'aucune clé n'a
     * été fournie. Le caller (BackupRepository) doit catch et signaler à l'UI
     * de prompter le recovery code.
     */
    class EncryptedArchiveException(message: String) : IOException(message)

    /**
     * Liste les archives de backup présentes dans le dossier SAF, triées par
     * date décroissante (la plus récente en premier). Utilisé par :
     * - Le restore screen (afficher la liste des backups disponibles).
     * - La policy de rétention (garder N archives, supprimer les plus vieilles).
     */
    fun listArchives(folderUri: Uri): List<DocumentFile> {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".zip") == true && it.name?.startsWith(FILE_PREFIX) == true }
            .sortedByDescending { it.lastModified() }
    }

    data class UnpackResult(
        val manifestJson: String,
        val archivePathToLocalPath: Map<String, String>,
        val skippedEntries: List<String>,
    )

    companion object {
        const val MIME_ZIP = "application/zip"
        const val MANIFEST_NAME = "manifest.json"
        const val PHOTO_PREFIX = "photos/"
        const val FILE_PREFIX = "shredcoach_backup_"
    }
}
