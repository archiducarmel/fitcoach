package com.shredcoach.app.domain.bodymesh

import androidx.compose.runtime.Immutable

/**
 * Snapshot des features extraites d'une photo corporelle, suffisant pour
 * (re-)rendre le wireframe néon de manière déterministe.
 *
 * **Pourquoi des features et pas une image PNG pré-rendue** :
 *  - Animatable côté UI (scan-line, pulse keypoints, glow) sans ré-décoder
 *    un bitmap à chaque frame.
 *  - Sharp à n'importe quelle résolution (Canvas vectoriel).
 *  - Re-stylable à volonté (palette, dark/light, mode focus muscle group)
 *    sans regénération côté pipeline d'extraction.
 *  - Léger : ~10 KB JSON pour 33 keypoints + ~256 points de contour, vs
 *    ~500 KB-2 MB pour un PNG de mesh.
 *  - Le partage social peut snapshotter le Canvas à la demande, on ne paie
 *    pas le coût stockage d'un PNG inutilisé tant que l'user ne partage pas.
 *
 * **Versionning** : [version] permet d'invalider les features anciennes si
 * on évolue le schéma (ajout fields, changement convention coordonnées).
 * Lecture défensive avec fallback empty si version ne match pas.
 *
 * **Coordonnées** : tous les points sont en pixels de l'image source
 * ([sourceImageWidth] × [sourceImageHeight]). Le rendu Canvas applique le
 * scaling vers la taille d'affichage. On évite de pré-normaliser en [0..1]
 * pour ne pas perdre de précision sur les small features (doigts).
 */
@Immutable
data class MeshFeatures(
    val version: Int = CURRENT_VERSION,
    val sourceImageWidth: Int,
    val sourceImageHeight: Int,
    /** 33 landmarks ML Kit Pose. Indexés par [PoseLandmarkType.ordinal]. */
    val landmarks: List<Landmark>,
    /**
     * Polyligne fermée du contour de la silhouette, ordonnée. Issue d'un
     * marching-squares sur le masque de segmentation, simplifié en ~150-300
     * points pour un rendu fluide sans dent-de-scie pixelisée.
     */
    val silhouetteContour: List<Point>,
    /**
     * Stats anatomiques calculées à l'extraction (cf. [MeshAnalytics]).
     * On les persiste avec les features pour ne pas avoir à re-calculer
     * (déterministe mais évite l'allocation au render).
     */
    val analytics: MeshAnalyticsSnapshot,
    /** Timestamp epoch ms — sert au tri/dedup côté historique. */
    val capturedAtMs: Long,
) {
    companion object {
        /** Bumper si on change la sémantique d'un field. Lecture old → empty. */
        const val CURRENT_VERSION = 1
    }
}

/**
 * Point 2D simple en pixels image. Sépare-toi de [androidx.compose.ui.geometry.Offset]
 * pour la sérialisation Gson stable (Offset est `value class` sur un Long
 * packé — Gson pète).
 */
@Immutable
data class Point(val x: Float, val y: Float)

/**
 * Landmark ML Kit Pose : position + confiance.
 * [type] correspond à [PoseLandmarkType.ordinal] pour compatibilité Gson sans
 * mapping enum custom.
 */
@Immutable
data class Landmark(
    val type: Int,
    val x: Float,
    val y: Float,
    /** [0..1] — confiance ML Kit que ce keypoint est visible et bien détecté. */
    val inFrameLikelihood: Float,
)

/**
 * 33 landmarks ML Kit Pose, dans l'ordre exact de l'enum natif. Garde
 * l'ordinal aligné — on persiste l'index, pas le nom.
 *
 * Référence : https://developers.google.com/ml-kit/vision/pose-detection
 */
enum class PoseLandmarkType {
    NOSE,
    LEFT_EYE_INNER, LEFT_EYE, LEFT_EYE_OUTER,
    RIGHT_EYE_INNER, RIGHT_EYE, RIGHT_EYE_OUTER,
    LEFT_EAR, RIGHT_EAR,
    MOUTH_LEFT, MOUTH_RIGHT,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_PINKY, RIGHT_PINKY,
    LEFT_INDEX, RIGHT_INDEX,
    LEFT_THUMB, RIGHT_THUMB,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX,
}

/**
 * Connexions canoniques entre landmarks pour dessiner le squelette.
 * Subset des 35 lignes ML Kit officielles : on retire les sub-features
 * faciales (yeux, bouche détaillés) qui sont du bruit visuel pour un mesh
 * "body wireframe" type Tron HUD.
 *
 * **Ordre de dessin** : core → limbs. Permet d'overlayer les bras/jambes
 * par-dessus le torse pour un rendu lisible.
 */
val POSE_CONNECTIONS: List<Pair<PoseLandmarkType, PoseLandmarkType>> = listOf(
    // ─── Core (épaules + hanches forment le tronc) ───
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_HIP,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_HIP,
    // Diagonales tronc — donnent la 3D feel + révèlent les déséquilibres
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.LEFT_HIP,
    // ─── Bras gauche ───
    PoseLandmarkType.LEFT_SHOULDER to PoseLandmarkType.LEFT_ELBOW,
    PoseLandmarkType.LEFT_ELBOW to PoseLandmarkType.LEFT_WRIST,
    // ─── Bras droit ───
    PoseLandmarkType.RIGHT_SHOULDER to PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.RIGHT_ELBOW to PoseLandmarkType.RIGHT_WRIST,
    // ─── Jambe gauche ───
    PoseLandmarkType.LEFT_HIP to PoseLandmarkType.LEFT_KNEE,
    PoseLandmarkType.LEFT_KNEE to PoseLandmarkType.LEFT_ANKLE,
    PoseLandmarkType.LEFT_ANKLE to PoseLandmarkType.LEFT_FOOT_INDEX,
    // ─── Jambe droite ───
    PoseLandmarkType.RIGHT_HIP to PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.RIGHT_KNEE to PoseLandmarkType.RIGHT_ANKLE,
    PoseLandmarkType.RIGHT_ANKLE to PoseLandmarkType.RIGHT_FOOT_INDEX,
    // ─── Tête (cou simplifié : nose ↔ centre épaules est calculé au render) ───
    PoseLandmarkType.NOSE to PoseLandmarkType.LEFT_EAR,
    PoseLandmarkType.NOSE to PoseLandmarkType.RIGHT_EAR,
)

/**
 * Mains : connexions petites, dessinées plus fines pour ne pas surcharger
 * visuellement. Utile pour montrer la pose des doigts (poses style "guide
 * pour la photo").
 */
val HAND_CONNECTIONS: List<Pair<PoseLandmarkType, PoseLandmarkType>> = listOf(
    PoseLandmarkType.LEFT_WRIST to PoseLandmarkType.LEFT_PINKY,
    PoseLandmarkType.LEFT_WRIST to PoseLandmarkType.LEFT_INDEX,
    PoseLandmarkType.LEFT_WRIST to PoseLandmarkType.LEFT_THUMB,
    PoseLandmarkType.LEFT_PINKY to PoseLandmarkType.LEFT_INDEX,
    PoseLandmarkType.RIGHT_WRIST to PoseLandmarkType.RIGHT_PINKY,
    PoseLandmarkType.RIGHT_WRIST to PoseLandmarkType.RIGHT_INDEX,
    PoseLandmarkType.RIGHT_WRIST to PoseLandmarkType.RIGHT_THUMB,
    PoseLandmarkType.RIGHT_PINKY to PoseLandmarkType.RIGHT_INDEX,
)

/**
 * Stats anatomiques dérivées des landmarks. Tout est en unités pixels image
 * sauf les ratios et angles qui sont sans dimension. La calibration vers cm
 * réels se fait côté UI via le [profile.heightCm] connu de l'utilisateur
 * (cf. [MeshAnalytics.calibrateCm]).
 *
 * **Pourquoi cacher dans [MeshFeatures]** : c'est le contenu premium qui
 * différencie l'app du compétiteur — on l'extrait une fois, on le persiste,
 * on l'affiche dans [HologramStatsPanel] sans recalcul à chaque ouverture.
 */
@Immutable
data class MeshAnalyticsSnapshot(
    /** Asymétrie épaule G/D : différence relative en % (0 = parfait). */
    val shoulderAsymmetryPct: Float,
    /** Asymétrie hanche G/D : idem épaules. */
    val hipAsymmetryPct: Float,
    /**
     * Inclinaison épaules : angle (degrés) entre l'horizontale et la ligne
     * épaule-G ↔ épaule-D. Positif = épaule droite plus haute.
     */
    val shoulderTiltDeg: Float,
    /** Idem hanches. */
    val hipTiltDeg: Float,
    /** Largeur d'épaules en pixels (source image). */
    val shoulderWidthPx: Float,
    /** Largeur de hanches en pixels. */
    val hipWidthPx: Float,
    /**
     * Ratio V-taper (épaules/hanches). > 1.4 = silhouette en V marquée
     * (objectif sèche). 1.0 = silhouette droite.
     */
    val vTaperRatio: Float,
    /** Longueur du bras gauche en px (épaule → poignet). */
    val leftArmLengthPx: Float,
    /** Idem bras droit. */
    val rightArmLengthPx: Float,
    /** Longueur jambe gauche (hanche → cheville). */
    val leftLegLengthPx: Float,
    /** Idem jambe droite. */
    val rightLegLengthPx: Float,
    /**
     * Posture score [0..100]. 100 = aligné, axis tronc vertical, têtes/épaules/
     * hanches centrées. Heuristique simple : pénalise tilt + asymétrie.
     */
    val postureScore: Int,
)
