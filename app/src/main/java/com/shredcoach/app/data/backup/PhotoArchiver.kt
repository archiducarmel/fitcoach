package com.shredcoach.app.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utilitaires pour la gestion des fichiers photo dans le pipeline de backup.
 *
 * Responsabilités :
 * - Calcul de SHA-256 sur fichier disque (intégrité + déduplication).
 * - Préparation du dossier d'extraction lors du restore.
 * - Mapping `archivePath` (dans le ZIP) → `localPath` (filesDir post-restore).
 *
 * Ne touche **pas** au ZIP directement — c'est le rôle de [BackupArchive].
 * Cette séparation permet à [BackupArchive] de streamer en mode purement
 * passe-plat (lecture/écriture entrée par entrée) sans connaître la sémantique
 * "photo" vs "manifest".
 */
@Singleton
class PhotoArchiver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Dossier dans lequel les photos sont extraites lors d'un restore.
     * Subdir de `filesDir` → automatiquement supprimé à la désinstallation,
     * et exclu du backup cloud (cf. backup_rules.xml). Pas de pollution de
     * MediaStore (les photos restaurées ne réapparaissent pas dans la galerie
     * système — elles sont privées à l'app).
     */
    fun restoredPhotosDir(): File =
        File(context.filesDir, "restored_photos").apply { mkdirs() }

    /**
     * Hash SHA-256 d'un fichier, en hex lowercase.
     *
     * Lecture par chunks de 64KB → empreinte mémoire constante même pour des
     * photos > 100 Mo. Si le fichier n'existe pas ou est illisible, retourne
     * `null` plutôt que de jeter — l'appelant traite l'absence comme "photo
     * non sauvegardable, on continue avec les autres".
     */
    fun sha256(file: File): String? {
        if (!file.exists() || !file.canRead()) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = fis.read(buffer)
                    if (n <= 0) break
                    digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    /**
     * Construit le path d'archive (dans le ZIP) pour un fichier photo donné.
     *
     * Convention : `photos/<sha256>.<ext>`. Avantages :
     * - **Déduplication automatique** : deux DB rows pointant vers le même
     *   fichier produiront la même `archivePath` → une seule entry dans le ZIP.
     * - **Idempotence** : le path ne dépend ni de l'ID DB ni du nom original
     *   (qui peut contenir des caractères incompatibles ZIP, e.g. accents
     *   sur certains providers SAF).
     * - **Pas d'info-fuite** : le nom original n'est pas exposé dans le ZIP
     *   au cas où l'utilisateur partage l'archive.
     */
    fun buildArchivePath(sha256: String, originalPath: String): String {
        val ext = originalPath.substringAfterLast('.', "jpg").lowercase()
            .takeIf { it.length in 1..5 } ?: "jpg"
        return "photos/$sha256.$ext"
    }

    /**
     * Extrait toutes les photos référencées dans [photos] vers le répertoire
     * de restauration. Retourne un mapping `originalPath` → nouveau `localPath`,
     * utilisé ensuite pour patcher les rows DB avant insertion.
     *
     * Les photos absentes du ZIP (corruption transit, archive partielle) sont
     * **ignorées** — leur DB row pointera vers un path inexistant, l'UI
     * affichera un placeholder. C'est mieux que de bloquer tout le restore
     * pour un fichier manquant.
     */
    fun targetPathFor(entry: PhotoEntry): File {
        val basename = entry.archivePath.substringAfterLast('/')
        return File(restoredPhotosDir(), basename)
    }
}
