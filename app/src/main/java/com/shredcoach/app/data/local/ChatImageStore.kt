package com.shredcoach.app.data.local

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage local des images attachees aux messages chat (Shreddy / Dr. Glykos).
 *
 * **Architecture** :
 *  - Images stockees dans `filesDir/chat_images/{uuid}.jpg` (private a l'app)
 *  - Compression JPEG 85% par defaut (~50-200 Ko pour 1024x1024)
 *  - Nettoyage manuel via [deleteImage] quand un message est supprime
 *  - Cleanup global via [cleanOrphans] (a appeler periodiquement, ex Worker
 *    bi-hebdomadaire) pour supprimer les fichiers orphelins
 *
 * **Pourquoi pas en BLOB Room** :
 *  - SQLite penalise lourdement les BLOBs >1MB (lecture/ecriture full-row)
 *  - Backup Drive devient pesant (10x plus de bytes)
 *  - Le filesystem natif est plus efficace pour acces aleatoire image
 *
 * **Securite** :
 *  - filesDir est private a l'app (sandbox Android), pas accessible aux autres apps
 *  - Pas de chiffrement at-rest (pour Premium V2 si demande)
 */
@Singleton
class ChatImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val imageDir: File by lazy {
        File(context.filesDir, IMAGES_SUBDIR).apply { if (!exists()) mkdirs() }
    }

    /**
     * Sauvegarde une [Bitmap] sur disque et retourne le chemin absolu.
     * Le fichier est nomme `{uuid}.jpg`. Compression JPEG 85% (~70-150 Ko
     * pour 1024x1024).
     *
     * @return chemin absolu du fichier (a stocker dans ChatMessageEntity.imagePath)
     */
    fun save(bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(imageDir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    /** Lit les bytes JPEG d'un fichier image. null si fichier absent/illisible. */
    fun readBytes(path: String): ByteArray? = try {
        File(path).takeIf { it.exists() }?.readBytes()
    } catch (_: Exception) {
        null
    }

    /** Supprime un fichier image. No-op si absent. */
    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).takeIf { it.exists() }?.delete()
        } catch (_: Exception) { /* best-effort */ }
    }

    /**
     * Cleanup periodique : supprime les fichiers dans le repertoire qui ne
     * sont referenced par aucun ChatMessageEntity. A appeler dans un Worker
     * bi-hebdomadaire pour eviter accumulation orphans (ex apres user delete
     * conversation mais migration plante a mi-chemin).
     *
     * @param referencedPaths set des paths actuellement utilises (lus depuis DB)
     * @return count de fichiers supprimes
     */
    fun cleanOrphans(referencedPaths: Set<String>): Int {
        if (!imageDir.exists()) return 0
        var deleted = 0
        imageDir.listFiles()?.forEach { f ->
            if (f.absolutePath !in referencedPaths) {
                if (f.delete()) deleted++
            }
        }
        return deleted
    }

    companion object {
        private const val IMAGES_SUBDIR = "chat_images"
    }
}
