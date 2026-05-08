package com.shredcoach.app.domain.bodymesh

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Pipeline d'extraction des features mesh à partir d'une photo corporelle.
 *
 * **Stack** :
 *  - ML Kit Pose Detection (accurate) → 33 keypoints anatomiques
 *  - ML Kit Selfie Segmentation → masque de confiance 1-canal
 *  - Marching-squares maison → polyligne contour silhouette
 *
 * **Performance** : ~150-300ms total sur mid-range (Pixel 4a 5G testé). En
 * dev sur Pixel 8 : ~80ms. Acceptable pour un one-shot après photo prise,
 * pas pour un live stream (mais on n'en a pas besoin).
 *
 * **Threading** : tout tourne sur Dispatchers.Default (CPU-bound côté
 * marching squares, l'inference ML Kit dispatch elle-même sur ses threads
 * internes). Pas d'IO disque ici → pas de Dispatchers.IO requis.
 *
 * **Cycle de vie des detectors** : ML Kit recommande de close() les
 * detectors quand on n'en a plus besoin (libère mémoire native). Comme on
 * est singleton + appel rare, on garde les instances vivantes pour
 * amortir le startup cost (~50ms à la 1ère création). Si la mémoire
 * devient un problème (peu probable, ~30 MB), on bascule vers une
 * instance per-call avec close().
 */
@Singleton
class BodyMeshExtractor @Inject constructor() {

    private val poseDetector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            // Raw size mask = même résolution que l'input (pas le 256x256
            // par défaut). Plus précis pour le contour, mais plus de RAM.
            // Pour une photo 1080×1920 → mask 1080×1920 floats = ~8 MB.
            // Acceptable car éphémère (libéré après marching squares).
            .enableRawSizeMask()
            .build()
        Segmentation.getClient(options)
    }

    /**
     * Extrait les features depuis un bitmap.
     *
     * **Ne mute pas le bitmap source** : on l'envoie tel quel à ML Kit qui
     * fait sa propre conversion interne. La rotation EXIF doit être
     * pre-corrigée par l'appelant (BodyScannerScreen le fait via
     * `ImageDecoder` qui applique les EXIF orientation automatiquement).
     */
    suspend fun extract(bitmap: Bitmap): Result<MeshFeatures> = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, /* rotationDegrees */ 0)

            // ─── 1. Pose detection ───
            val pose = poseDetector.process(image).await()
            val landmarks = pose.allPoseLandmarks.map { it.toDomain() }
            if (landmarks.isEmpty() || !poseLooksValid(pose.allPoseLandmarks)) {
                throw IllegalStateException("NO_POSE_DETECTED")
            }

            // ─── 2. Segmentation ───
            val segMask = segmenter.process(image).await()
            val contour = extractSilhouetteContour(
                buffer = segMask.buffer,
                width = segMask.width,
                height = segMask.height,
            )

            // ─── 3. Analytics ───
            val analytics = MeshAnalytics.compute(landmarks)

            MeshFeatures(
                version = MeshFeatures.CURRENT_VERSION,
                sourceImageWidth = bitmap.width,
                sourceImageHeight = bitmap.height,
                landmarks = landmarks,
                silhouetteContour = contour,
                analytics = analytics,
                capturedAtMs = System.currentTimeMillis(),
            )
        }.onFailure { Log.e(TAG, "extract() failed", it) }
    }

    /**
     * Heuristique de validité : on veut au minimum les épaules + hanches +
     * tête détectées avec une confidence raisonnable. Sinon le mesh sera
     * inexploitable et on renvoie une erreur claire à l'utilisateur plutôt
     * que d'afficher un squelette aléatoire.
     */
    private fun poseLooksValid(allLandmarks: List<PoseLandmark>): Boolean {
        val byType = allLandmarks.associateBy { it.landmarkType }
        val critical = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.NOSE,
        )
        return critical.all { type ->
            (byType[type]?.inFrameLikelihood ?: 0f) > MIN_CRITICAL_CONFIDENCE
        }
    }

    private fun PoseLandmark.toDomain(): Landmark = Landmark(
        type = mlKitToOrdinal(landmarkType),
        x = position.x,
        y = position.y,
        inFrameLikelihood = inFrameLikelihood,
    )

    /**
     * ML Kit utilise des constantes int (NOSE=0, LEFT_EYE_INNER=1, ...) pour
     * [PoseLandmark.landmarkType]. Notre [PoseLandmarkType] enum a le même
     * ordre par construction → mapping identité. On encapsule pour ne pas
     * dépendre de l'implémentation interne de ML Kit (au cas où ils
     * réordonnent dans une version future).
     */
    private fun mlKitToOrdinal(mlKitType: Int): Int = mlKitType

    /**
     * Extrait le contour de la silhouette depuis le confidence mask de la
     * segmentation. Le buffer est un FloatBuffer de size width×height où
     * chaque float est dans [0..1] (1 = pixel certain corps, 0 = certain
     * arrière-plan).
     *
     * **Algorithme** : threshold à 0.5 → masque binaire. Marching squares
     * simplifié : on parcourt les pixels, on trace les transitions
     * binaire/binaire à la frontière. Pour un rendu Canvas propre, on
     * sous-échantillonne et on simplifie en ~150-300 points (Douglas-Peucker
     * léger).
     *
     * **Pas de full marching-squares** : on extrait uniquement le contour
     * extérieur (pas les trous internes), ce qui suffit pour le rendu
     * silhouette néon. Pour un mesh polygonal Delaunay (V1.5), on échantillonnera
     * aussi des points à l'intérieur du masque.
     */
    private fun extractSilhouetteContour(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
    ): List<Point> {
        // Le ConfidenceMask est un FloatBuffer (4 octets par pixel). On lit
        // direct depuis le ByteBuffer en accédant aux floats.
        val floatBuffer = buffer.asFloatBuffer()
        val mask = ByteArray(width * height)
        for (i in 0 until width * height) {
            mask[i] = if (floatBuffer.get(i) > BODY_THRESHOLD) 1 else 0
        }

        // Boundary tracing : on suit la frontière du masque depuis le
        // premier pixel "corps" trouvé en parcourant top→bottom, left→right.
        // Direction-of-arrival pour Moore-Neighbor tracing simplifié.
        val startIdx = mask.indexOfFirst { it.toInt() == 1 }
        if (startIdx < 0) return emptyList()
        val startX = startIdx % width
        val startY = startIdx / width

        // Moore-Neighbor : 8 directions, on tourne autour du précédent voisin.
        val dx = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        val dy = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)

        val raw = mutableListOf<Point>()
        var cx = startX
        var cy = startY
        var prevDir = 6 // West entry (we approached from the right)
        raw += Point(cx.toFloat(), cy.toFloat())

        // Limite de sécurité : un contour ne peut pas excéder le périmètre
        // total de l'image, on cape à 4× pour autoriser des silhouettes
        // complexes (cheveux, gestes).
        val maxIterations = (width + height) * 4
        var iterations = 0
        while (iterations < maxIterations) {
            iterations++
            // Cherche le prochain pixel "corps" en tournant à partir du
            // précédent voisin connu (Moore-Neighbor).
            var found = false
            for (k in 0 until 8) {
                val dir = (prevDir + 1 + k) % 8
                val nx = cx + dx[dir]
                val ny = cy + dy[dir]
                if (nx in 0 until width && ny in 0 until height &&
                    mask[ny * width + nx].toInt() == 1
                ) {
                    cx = nx
                    cy = ny
                    // On se "réoriente" vers la direction opposée pour le
                    // prochain check (back-tracking standard).
                    prevDir = (dir + 4) % 8
                    raw += Point(cx.toFloat(), cy.toFloat())
                    found = true
                    break
                }
            }
            if (!found) break // pixel isolé, abort
            if (cx == startX && cy == startY && raw.size > 2) break // boucle fermée
        }

        // Simplification : keep 1 point sur N pour réduire la densité (le
        // marching squares produit ~5000-15000 pts qu'on render en ~250).
        val targetPoints = 250
        val step = max(1, raw.size / targetPoints)
        val simplified = raw.filterIndexed { idx, _ -> idx % step == 0 }
        // Force la fermeture exacte de la polyligne pour le rendu Canvas.
        return if (simplified.isNotEmpty() && simplified.first() != simplified.last()) {
            simplified + simplified.first()
        } else simplified
    }

    companion object {
        private const val TAG = "BodyMeshExtractor"
        /** Seuil de confidence sur le mask segmentation. > seuil = pixel "corps". */
        private const val BODY_THRESHOLD = 0.5f
        /** Confidence minimum pour les keypoints critiques (épaules/hanches/tête). */
        private const val MIN_CRITICAL_CONFIDENCE = 0.4f
    }
}
