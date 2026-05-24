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
     * Sauvegarde une [Bitmap] sur disque et retourne (path absolu, bytes JPEG).
     * Le fichier est nomme `{uuid}.jpg`. Compression JPEG 80%.
     *
     * **Resize obligatoire** : la bitmap est downscaled a `MAX_DIMENSION_PX`
     * sur le grand cote AVANT compression. Couvre les photos modernes
     * 4032x3024 (~48 MB en RAM) qui causeraient OOM sur devices 3-4 GB.
     * Les LLM vision (GPT-4o, Gemini) downscale a 768-1024px de toute facon.
     *
     * **Output uniforme** : 1024x768 (paysage) ou 768x1024 (portrait) max,
     * JPEG 80%, ~80-200 Ko. Le ByteArray retourne est REUTILISE par le caller
     * pour l'API HTTP -> evite la double compression (file write + http body).
     *
     * @return Pair(chemin absolu, bytes JPEG) — chemin pour DB, bytes pour HTTP
     */
    fun saveAndEncode(bitmap: Bitmap): Pair<String, ByteArray> {
        val resized = resizeBitmap(bitmap, MAX_DIMENSION_PX)
        val name = "${UUID.randomUUID()}.jpg"
        val file = File(imageDir, name)
        val baos = java.io.ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        val bytes = baos.toByteArray()
        file.writeBytes(bytes)
        // Recycle si on a alloue un nouveau bitmap (resized != input)
        if (resized !== bitmap) resized.recycle()
        return file.absolutePath to bytes
    }

    /** @deprecated Utiliser [saveAndEncode] pour eviter double compression. */
    @Deprecated("Use saveAndEncode to avoid double JPEG compression",
        ReplaceWith("saveAndEncode(bitmap).first"))
    fun save(bitmap: Bitmap): String = saveAndEncode(bitmap).first

    /**
     * Resize [bitmap] pour que le plus grand cote ne depasse pas [maxDimension].
     * Preserve l'aspect ratio. Retourne le bitmap original si deja sous la limite.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val biggest = maxOf(w, h)
        if (biggest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / biggest
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
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
        /** Cote max apres resize : ~1024px = sweet spot LLM vision (Gemini/GPT-4o
         *  re-downscale a 768 de toute facon). 200-300 KB JPEG. */
        private const val MAX_DIMENSION_PX = 1024
        /** Qualite JPEG : 80 = ~30% plus petit que 85 sans perte visuelle perceptible. */
        private const val JPEG_QUALITY = 80
    }
}
