package com.shredcoach.app.domain.bodymesh

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Calcule les stats anatomiques d'un set de landmarks ML Kit.
 *
 * **Détermininisme** : pures fonctions math, aucune dépendance Android.
 * Testable en pure JVM (cf. `MeshAnalyticsTest`).
 *
 * **Robustesse aux landmarks manquants** : ML Kit Pose retourne toujours les
 * 33 landmarks même si occultés, mais avec [Landmark.inFrameLikelihood]
 * faible. Pour les keypoints critiques (épaules, hanches), on **n'écarte pas**
 * les low-confidence — ils sont rarement absents et l'erreur visuelle est
 * tolérable. Pour les jambes (occultées par poses assises), on retourne 0 si
 * confidence < [MIN_CONFIDENCE_LIMBS] pour éviter d'afficher "120cm de jambe"
 * qui serait du bruit.
 *
 * **Calibration cm** : les longueurs renvoyées sont en pixels image. La
 * conversion px → cm se fait via la taille connue (`heightCm` du profil) en
 * supposant que la photo prise montre bien tout le corps, head-to-toe :
 *
 *   cm/px = heightCm / distance(NOSE, FOOT_INDEX)
 *
 * Cette calibration est faite côté UI ([calibrateCm]) plutôt qu'à
 * l'extraction, parce que `heightCm` peut changer post-extraction (user édite
 * sa taille) et on veut que les stats se mettent à jour sans regénérer le
 * mesh. C'est aussi plus honnête : on ne ment pas en stockant des cm dérivés.
 */
object MeshAnalytics {

    /**
     * Confidence minimum pour considérer un keypoint de jambe comme valide.
     * En dessous, on retourne 0 pour cette mesure (l'UI affiche "—").
     */
    private const val MIN_CONFIDENCE_LIMBS = 0.5f

    fun compute(landmarks: List<Landmark>): MeshAnalyticsSnapshot {
        if (landmarks.size < PoseLandmarkType.entries.size) {
            // Pose detection a échoué partiellement — retourne un snapshot
            // neutre. L'UI gérera l'absence de stats.
            return EMPTY
        }
        val byType = landmarks.associateBy { it.type }

        val ls = byType[PoseLandmarkType.LEFT_SHOULDER.ordinal]
        val rs = byType[PoseLandmarkType.RIGHT_SHOULDER.ordinal]
        val lh = byType[PoseLandmarkType.LEFT_HIP.ordinal]
        val rh = byType[PoseLandmarkType.RIGHT_HIP.ordinal]
        val le = byType[PoseLandmarkType.LEFT_ELBOW.ordinal]
        val re = byType[PoseLandmarkType.RIGHT_ELBOW.ordinal]
        val lw = byType[PoseLandmarkType.LEFT_WRIST.ordinal]
        val rw = byType[PoseLandmarkType.RIGHT_WRIST.ordinal]
        val lk = byType[PoseLandmarkType.LEFT_KNEE.ordinal]
        val rk = byType[PoseLandmarkType.RIGHT_KNEE.ordinal]
        val la = byType[PoseLandmarkType.LEFT_ANKLE.ordinal]
        val ra = byType[PoseLandmarkType.RIGHT_ANKLE.ordinal]

        if (ls == null || rs == null || lh == null || rh == null) return EMPTY

        // ─── Largeurs et tilts du tronc ───
        val shoulderWidthPx = hypot(rs.x - ls.x, rs.y - ls.y)
        val hipWidthPx = hypot(rh.x - lh.x, rh.y - lh.y)

        // Inclinaison : angle de la ligne L→R par rapport à l'horizontale.
        // atan2 gère le quadrant. Positif = R plus bas (image en bas),
        // mais on inverse pour que "positif = R plus haut" corresponde à
        // l'intuition utilisateur ("épaule droite tombe").
        val shoulderTiltDeg = -Math.toDegrees(
            atan2((rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble())
        ).toFloat()
        val hipTiltDeg = -Math.toDegrees(
            atan2((rh.y - lh.y).toDouble(), (rh.x - lh.x).toDouble())
        ).toFloat()

        // ─── Asymétries (axe vertical : différence de y entre G et D) ───
        // Normalisé par la largeur d'épaules pour être scale-invariant.
        val shoulderAsymPct = if (shoulderWidthPx > 1f) {
            abs(ls.y - rs.y) / shoulderWidthPx * 100f
        } else 0f
        val hipAsymPct = if (hipWidthPx > 1f) {
            abs(lh.y - rh.y) / hipWidthPx * 100f
        } else 0f

        // ─── V-taper ratio ───
        val vTaper = if (hipWidthPx > 1f) shoulderWidthPx / hipWidthPx else 0f

        // ─── Longueurs membres ───
        val leftArmLen = if (le != null && lw != null && le.inFrameLikelihood > MIN_CONFIDENCE_LIMBS) {
            hypot(ls.x - le.x, ls.y - le.y) + hypot(le.x - lw.x, le.y - lw.y)
        } else 0f
        val rightArmLen = if (re != null && rw != null && re.inFrameLikelihood > MIN_CONFIDENCE_LIMBS) {
            hypot(rs.x - re.x, rs.y - re.y) + hypot(re.x - rw.x, re.y - rw.y)
        } else 0f
        val leftLegLen = if (lk != null && la != null && lk.inFrameLikelihood > MIN_CONFIDENCE_LIMBS) {
            hypot(lh.x - lk.x, lh.y - lk.y) + hypot(lk.x - la.x, lk.y - la.y)
        } else 0f
        val rightLegLen = if (rk != null && ra != null && rk.inFrameLikelihood > MIN_CONFIDENCE_LIMBS) {
            hypot(rh.x - rk.x, rh.y - rk.y) + hypot(rk.x - ra.x, rk.y - ra.y)
        } else 0f

        // ─── Score posture ───
        // Heuristique simple : 100 - pénalités sur tilts et asymétries.
        // Pénalité tilt : 2 points par degré au-delà de 2°. Idem hip.
        // Pénalité asym : 0.5 point par % au-delà de 1%.
        val tiltPenalty = (abs(shoulderTiltDeg) - 2f).coerceAtLeast(0f) * 2f +
                (abs(hipTiltDeg) - 2f).coerceAtLeast(0f) * 2f
        val asymPenalty = (shoulderAsymPct - 1f).coerceAtLeast(0f) * 0.5f +
                (hipAsymPct - 1f).coerceAtLeast(0f) * 0.5f
        val postureScore = (100f - tiltPenalty - asymPenalty)
            .coerceIn(0f, 100f)
            .roundToInt()

        return MeshAnalyticsSnapshot(
            shoulderAsymmetryPct = round1(shoulderAsymPct),
            hipAsymmetryPct = round1(hipAsymPct),
            shoulderTiltDeg = round1(shoulderTiltDeg),
            hipTiltDeg = round1(hipTiltDeg),
            shoulderWidthPx = shoulderWidthPx,
            hipWidthPx = hipWidthPx,
            vTaperRatio = round2(vTaper),
            leftArmLengthPx = leftArmLen,
            rightArmLengthPx = rightArmLen,
            leftLegLengthPx = leftLegLen,
            rightLegLengthPx = rightLegLen,
            postureScore = postureScore,
        )
    }

    /**
     * Convertit une longueur en pixels vers cm en utilisant la taille connue
     * du profil. Fallback à null si la pose n'a pas tête + pieds détectables
     * (calibration impossible).
     *
     * @param landmarks set complet de la pose courante
     * @param heightCm taille réelle en cm de l'utilisateur
     * @return pixels par cm, ou null si non-calibrable
     */
    fun cmPerPx(landmarks: List<Landmark>, heightCm: Int): Float? {
        if (heightCm <= 0) return null
        val byType = landmarks.associateBy { it.type }
        val nose = byType[PoseLandmarkType.NOSE.ordinal] ?: return null
        // On prend le pied le plus bas (Y le plus grand en image coords) qui
        // a la confidence la plus haute, pour gérer une seule jambe visible.
        val candidates = listOfNotNull(
            byType[PoseLandmarkType.LEFT_FOOT_INDEX.ordinal],
            byType[PoseLandmarkType.RIGHT_FOOT_INDEX.ordinal],
            byType[PoseLandmarkType.LEFT_ANKLE.ordinal],
            byType[PoseLandmarkType.RIGHT_ANKLE.ordinal],
        ).filter { it.inFrameLikelihood > MIN_CONFIDENCE_LIMBS }
        val foot = candidates.maxByOrNull { it.y } ?: return null

        val pxHeight = abs(foot.y - nose.y)
        if (pxHeight < 1f) return null
        // Ajustement : la tête au-dessus du nez compte pour ~10% de la
        // hauteur réelle (proportions humaines moyennes — tête ~1/8 du corps,
        // moitié au-dessus du nez). Sans ça, on sous-estime la taille de
        // ~10% systématiquement.
        val correctedPxHeight = pxHeight * 1.10f
        return heightCm / correctedPxHeight
    }

    private val EMPTY = MeshAnalyticsSnapshot(
        shoulderAsymmetryPct = 0f, hipAsymmetryPct = 0f,
        shoulderTiltDeg = 0f, hipTiltDeg = 0f,
        shoulderWidthPx = 0f, hipWidthPx = 0f,
        vTaperRatio = 0f,
        leftArmLengthPx = 0f, rightArmLengthPx = 0f,
        leftLegLengthPx = 0f, rightLegLengthPx = 0f,
        postureScore = 0,
    )

    private fun round1(v: Float): Float = (v * 10f).roundToInt() / 10f
    private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f
}
