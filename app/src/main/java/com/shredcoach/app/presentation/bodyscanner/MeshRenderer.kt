package com.shredcoach.app.presentation.bodyscanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.bodymesh.HAND_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.Landmark
import com.shredcoach.app.domain.bodymesh.MeshAnalytics
import com.shredcoach.app.domain.bodymesh.MeshFeatures
import com.shredcoach.app.domain.bodymesh.POSE_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.PoseLandmarkType
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rendu Canvas du mesh corporel à partir de [MeshFeatures]. C'est le
 * composable central qui remplace l'ancien `Image(BitmapFactory.decodeFile)`
 * issu de Gemini.
 *
 * **Architecture rendu** (couches du fond vers l'avant) :
 *  1. Halo radial cyan derrière la silhouette (depth + glow).
 *  2. Polyligne silhouette : contour stroke 2px néon.
 *  3. Connexions pose (squelette) : lignes 2.5px avec halo blur émulé via
 *     2 passes (large alpha + fine pleine).
 *  4. Connexions main : 1.5px pour ne pas dominer visuellement.
 *  5. Keypoints : cercles pulsants (4-8px) sur les landmarks confidence > 0.6.
 *  6. Scan-line horizontale qui sweep verticalement la silhouette.
 *
 * **Scaling letterbox** : on garde le ratio source de l'image en centrant
 * dans le viewport. Évite les distorsions si la canvas est plus large que
 * l'image source ou inversement.
 *
 * **Animations** : tout est dans un `infiniteTransition` au sein du
 * composable. Pas d'allocation par frame (offsets/Path remember-isés par
 * `features`).
 */
/**
 * Seuil au-delà duquel une asymétrie G/D bascule en couleur "alert" sur les
 * bones du membre. Calibré pour qu'un ratio normal (jusqu'à ~3-5%) reste
 * en couleur calme, et qu'une vraie asymétrie posturale soit signalée.
 */
private const val LIMB_ASYMMETRY_ALERT_PCT = 5f

/**
 * Seuil pour les guides de posture (#14) : au-delà, on dessine une ligne
 * d'écart du keypoint vers la position "idéale" pour matérialiser le tilt.
 */
private const val POSTURE_TILT_GUIDE_THRESHOLD_DEG = 1.5f

@Composable
fun MeshRenderer(
    features: MeshFeatures,
    modifier: Modifier = Modifier,
    primaryColor: Color = NEON_CYAN,
    secondaryColor: Color = NEON_GREEN,
    accentColor: Color = NEON_PINK,
    /**
     * Taille de l'utilisateur en cm (depuis profile.heightCm). Si > 0, on
     * calibre les longueurs pixels → cm via [MeshAnalytics.cmPerPx] et on
     * affiche les **labels anatomiques** (#11) avec valeurs cm réelles.
     * À 0, les labels sont masqués (pas de chiffre fiable à afficher).
     */
    heightCm: Int = 0,
    /**
     * #12 — Si `true`, colorise les bones G/D des membres selon l'asymétrie
     * détectée (vert calme si équilibré, pink "alert" sur le côté plus court).
     * Diagnostic visuel instantané du déséquilibre.
     */
    symmetryColors: Boolean = true,
    /**
     * #14 — Si `true`, superpose les guides de posture idéale : axe vertical
     * spine + lignes horizontales épaules/hanches. Permet de visualiser le
     * tilt par rapport à une référence parfaite.
     */
    showPostureGuides: Boolean = true,
) {
    // ─── Animations infinies ───
    val infinite = rememberInfiniteTransition(label = "mesh")
    val scanProgress by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "scan",
    )
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "pulse",
    )
    val rotatePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ), label = "rotate",
    )

    // ─── Landmarks indexés (re-builda uniquement si features changent) ───
    val landmarksByType = remember(features) {
        features.landmarks.associateBy { it.type }
    }

    // ─── Calibration cm/px (mémoïsée par features.id implicite) ───
    val cmPerPx = remember(features, heightCm) {
        if (heightCm > 0) MeshAnalytics.cmPerPx(features.landmarks, heightCm) else null
    }

    // ─── Décision colors par membre selon asymétrie (#12) ───
    // - Bras G plus court de >5% que D : bras G en pink (alert), D en primary
    // - Sinon les deux en primary
    val limbColors = remember(features, symmetryColors) {
        if (!symmetryColors) {
            LimbColors.allPrimary(primaryColor)
        } else {
            computeLimbColors(features, primaryColor, accentColor)
        }
    }

    // BoxWithConstraints donne la taille exacte du viewport, ce qui permet
    // de calculer le scale letterbox UNE fois et de le partager entre :
    //  - le Canvas (rendu skeleton/silhouette)
    //  - les overlays Compose (labels anatomiques en Text, guides)
    // Sans BoxWithConstraints, chaque Canvas/overlay devrait recalculer son
    // propre scale, avec risque de désynchro.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val srcW = features.sourceImageWidth.toFloat()
        val srcH = features.sourceImageHeight.toFloat()

        if (srcW <= 0f || srcH <= 0f) return@BoxWithConstraints

        val scale = min(canvasW / srcW, canvasH / srcH)
        val drawnW = srcW * scale
        val drawnH = srcH * scale
        val offsetX = (canvasW - drawnW) / 2f
        val offsetY = (canvasH - drawnH) / 2f

        // Helper : convertit coord src image → coord canvas
        val toCanvasX = { x: Float -> offsetX + x * scale }
        val toCanvasY = { y: Float -> offsetY + y * scale }

        Canvas(modifier = Modifier.fillMaxSize()) {

            translate(left = offsetX, top = offsetY) {
            // ═════════════════════════════════════════════
            // ARCHITECTURE VISUELLE (back → front) :
            //   1. Halo radial ambient (depth)
            //   2. Silhouette = INNER FILL semi-transparent (corps qui glow)
            //   3. Silhouette = OUTER GLOW halo (pas de stroke solide)
            //   4. Skeleton = 3-layer depth (haze 24px / glow 9px / core 2.5px)
            //   5. Keypoints = halo + inner dot pulsant
            //   6. Scan-line horizontale
            //   7. Orbital corners
            //
            // Pourquoi ce layering : la silhouette ne doit PAS être un stroke
            // solide qui rentre en conflit visuel avec le skeleton. Elle agit
            // comme une AURA qui définit le corps, par-dessus laquelle le
            // skeleton est le sujet visuel principal.
            // ═════════════════════════════════════════════

            // ─── 1. Halo radial ambient ───
            val centerX = drawnW / 2f
            val centerY = drawnH / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.14f),
                        primaryColor.copy(alpha = 0.04f),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = max(drawnW, drawnH) * 0.65f,
                ),
                radius = max(drawnW, drawnH) * 0.65f,
                center = Offset(centerX, centerY),
            )

            // ─── 2-3. Silhouette = pure aura (FILL + halo, pas de stroke solide) ───
            val scaledSilhouette = Path().apply {
                features.silhouetteContour.forEachIndexed { i, p ->
                    val sx = p.x * scale
                    val sy = p.y * scale
                    if (i == 0) moveTo(sx, sy) else lineTo(sx, sy)
                }
                close()
            }
            // Inner fill : le corps "glow" légèrement de l'intérieur. Pas
            // de stroke solide qui collisionnerait avec le skeleton.
            drawPath(
                scaledSilhouette,
                color = primaryColor.copy(alpha = 0.06f),
                style = androidx.compose.ui.graphics.drawscope.Fill,
            )
            // Outer glow : halo flou émulé par 3 strokes très transparents,
            // largeurs croissantes. Donne une "aura" autour du corps sans
            // ligne dure.
            drawPath(scaledSilhouette, primaryColor.copy(alpha = 0.05f),
                style = Stroke(width = 22f, cap = StrokeCap.Round))
            drawPath(scaledSilhouette, primaryColor.copy(alpha = 0.10f),
                style = Stroke(width = 12f, cap = StrokeCap.Round))
            drawPath(scaledSilhouette, primaryColor.copy(alpha = 0.18f),
                style = Stroke(width = 5f, cap = StrokeCap.Round))
            // Outline tènue pour bien définir le contour, mais alpha bas
            // pour ne pas rivaliser avec le skeleton.
            drawPath(scaledSilhouette, primaryColor.copy(alpha = 0.45f),
                style = Stroke(width = 1f, cap = StrokeCap.Round))

            // ─── 4. Skeleton (squelette) — 3-layer depth + symmetry coloring ───
            // Le skeleton est le SUJET principal : il doit dominer visuellement
            // par-dessus la silhouette glow. 3 couches : haze (large flou) +
            // glow (medium) + core (line nette luminescente).
            // #12 : la couleur de chaque bone dépend de son membre (gauche/
            // droit/centre) et de l'asymétrie. Membre déséquilibré → pink alert.
            for ((from, to) in POSE_CONNECTIONS) {
                val a = landmarksByType[from.ordinal]
                val b = landmarksByType[to.ordinal]
                if (a == null || b == null) continue
                if (a.inFrameLikelihood < MIN_DRAW_CONFIDENCE && b.inFrameLikelihood < MIN_DRAW_CONFIDENCE) continue
                val pa = Offset(a.x * scale, a.y * scale)
                val pb = Offset(b.x * scale, b.y * scale)
                val boneColor = limbColors.colorFor(from, to)
                // Layer 1 : haze large (depth)
                drawLine(
                    color = boneColor.copy(alpha = 0.10f),
                    start = pa, end = pb, strokeWidth = 24f, cap = StrokeCap.Round,
                )
                // Layer 2 : glow medium
                drawLine(
                    color = boneColor.copy(alpha = 0.40f),
                    start = pa, end = pb, strokeWidth = 9f, cap = StrokeCap.Round,
                )
                // Layer 3 : core net + saturé (le bone lui-même)
                drawLine(
                    color = boneColor,
                    start = pa, end = pb, strokeWidth = 2.5f, cap = StrokeCap.Round,
                )
            }

            // ─── 4b. Connexions main (subtiles, ne dominent pas) ───
            for ((from, to) in HAND_CONNECTIONS) {
                val a = landmarksByType[from.ordinal]
                val b = landmarksByType[to.ordinal]
                if (a == null || b == null) continue
                if (a.inFrameLikelihood < MIN_DRAW_CONFIDENCE_HAND ||
                    b.inFrameLikelihood < MIN_DRAW_CONFIDENCE_HAND) continue
                val pa = Offset(a.x * scale, a.y * scale)
                val pb = Offset(b.x * scale, b.y * scale)
                drawLine(
                    color = primaryColor.copy(alpha = 0.30f),
                    start = pa, end = pb, strokeWidth = 4f, cap = StrokeCap.Round,
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.85f),
                    start = pa, end = pb, strokeWidth = 1.4f, cap = StrokeCap.Round,
                )
            }

            // ─── 4c. Cou virtuel : nose ↔ centre épaules ───
            drawNeck(landmarksByType, scale, primaryColor)

            // ─── 5. Keypoints pulsants — accent secondaryColor pour pop ───
            val pulseScale = 1f + 0.35f * sin(pulsePhase).toFloat()
            for (type in KEY_POINTS) {
                val lm = landmarksByType[type.ordinal] ?: continue
                if (lm.inFrameLikelihood < MIN_DRAW_CONFIDENCE) continue
                val center = Offset(lm.x * scale, lm.y * scale)
                // Outer halo (large diffuse)
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.25f),
                    radius = 14f * pulseScale, center = center,
                )
                // Mid glow
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.55f),
                    radius = 6.5f * pulseScale, center = center,
                )
                // Core dot (luminescent)
                drawCircle(
                    color = Color.White,
                    radius = 2.2f * pulseScale, center = center,
                )
            }

            // ─── 7. Scan-line horizontale dans la bbox du corps ───
            val bbox = computeBBox(features.landmarks, scale)
            if (bbox != null) {
                val (top, bot, left, right) = bbox
                val y = top + (bot - top) * scanProgress
                // Halo gradient au-dessus/dessous de la ligne
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        startY = (y - 60f).coerceAtLeast(top),
                        endY = (y + 60f).coerceAtMost(bot),
                    ),
                    topLeft = Offset(left, (y - 60f).coerceAtLeast(top)),
                    size = androidx.compose.ui.geometry.Size(
                        width = right - left,
                        height = (((y + 60f).coerceAtMost(bot)) - ((y - 60f).coerceAtLeast(top))).coerceAtLeast(0f),
                    ),
                )
                // Scan line pleine
                drawLine(
                    color = primaryColor,
                    start = Offset(left, y), end = Offset(right, y),
                    strokeWidth = 1.6f, cap = StrokeCap.Round,
                )
                // Tick marks aux extrémités
                val tickColor = accentColor.copy(alpha = 0.8f)
                drawLine(tickColor, Offset(left - 8f, y), Offset(left, y), 2f)
                drawLine(tickColor, Offset(right, y), Offset(right + 8f, y), 2f)
            }

            // ─── 8. Indicateurs orbital corner (rotatePhase) ───
            // Petits arcs qui tournent sur les coins de la bbox — micro-touch
            // sci-fi qui ajoute du mouvement sans surcharger.
            if (bbox != null) {
                drawOrbitalCorners(bbox, rotatePhase, primaryColor.copy(alpha = 0.6f))
            }

            // ─── 9. Posture guides (#14) — référence "idéale" ───
            // Lignes pointillées :
            //  - Verticale spine : milieu(épaules) → milieu(hanches), STRICTEMENT
            //    verticale (l'idéale, peu importe le tilt actuel). On la trace
            //    en partant du keypoint épaules vers le bas, à X = midX(épaules).
            //  - Horizontale épaules : ligne horizontale parfaite (pas tiltée).
            //    Comparaison visuelle directe avec le bone L↔R réel.
            //  - Horizontale hanches : idem.
            // Si le tilt est < seuil, on ne dessine pas (corps déjà aligné, pas
            // besoin d'overlay).
            if (showPostureGuides) {
                drawPostureGuides(
                    landmarksByType = landmarksByType,
                    scale = scale,
                    analytics = features.analytics,
                    accentColor = accentColor,
                    primaryColor = primaryColor,
                )
            }
        }
        }

        // ═════════════════════════════════════════════
        // OVERLAYS COMPOSE (en dehors du Canvas, dans le Box) :
        //   - Anatomical labels (#11) : Text + lead line par anchor keypoint
        // ═════════════════════════════════════════════
        if (cmPerPx != null) {
            val labels = remember(features, cmPerPx) {
                computeAnatomicalLabels(
                    features = features,
                    cmPerPx = cmPerPx,
                )
            }

            // Lead lines : Canvas séparé qui dessine les liaisons anchor → label.
            // Doit être SOUS les labels pour que le texte soit lisible.
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (label in labels) {
                    val anchorCanvas = Offset(toCanvasX(label.anchorX), toCanvasY(label.anchorY))
                    // Position du label en coords canvas
                    val labelCx = toCanvasX(label.anchorX) + label.deltaXPx
                    val labelCy = toCanvasY(label.anchorY) + label.deltaYPx
                    // Lead line pointillée
                    drawLine(
                        color = label.color.copy(alpha = 0.55f),
                        start = anchorCanvas,
                        end = Offset(labelCx, labelCy),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f),
                        cap = StrokeCap.Round,
                    )
                    // Petit cercle au keypoint anchor (marker visuel)
                    drawCircle(
                        color = label.color,
                        radius = 3f,
                        center = anchorCanvas,
                    )
                    drawCircle(
                        color = label.color.copy(alpha = 0.35f),
                        radius = 7f,
                        center = anchorCanvas,
                    )
                }
            }

            // Labels : composables Text positionnés en absolu.
            for (label in labels) {
                val labelCx = toCanvasX(label.anchorX) + label.deltaXPx
                val labelCy = toCanvasY(label.anchorY) + label.deltaYPx
                AnatomicalLabelChip(
                    label = label,
                    modifier = Modifier
                        .offset {
                            // Décalage en pixels pour aligner le centre du chip
                            // sur (labelCx, labelCy). On centre verticalement,
                            // et on aligne par côté horizontal selon `side`.
                            val xPx = when (label.side) {
                                LabelSide.LEFT -> labelCx - LABEL_HEIGHT_PX * 4f
                                LabelSide.RIGHT -> labelCx
                            }
                            IntOffset(xPx.roundToInt(), (labelCy - LABEL_HEIGHT_PX / 2f).roundToInt())
                        },
                )
            }
        }
    }
}

/**
 * Dessine la liaison nose ↔ midpoint(épaules) pour fermer le segment cou,
 * absent de POSE_CONNECTIONS car midpoint n'est pas un landmark.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeck(
    landmarksByType: Map<Int, Landmark>,
    scale: Float,
    color: Color,
) {
    val nose = landmarksByType[PoseLandmarkType.NOSE.ordinal] ?: return
    val ls = landmarksByType[PoseLandmarkType.LEFT_SHOULDER.ordinal] ?: return
    val rs = landmarksByType[PoseLandmarkType.RIGHT_SHOULDER.ordinal] ?: return
    if (nose.inFrameLikelihood < 0.5f) return
    val mid = Offset(
        ((ls.x + rs.x) / 2f) * scale,
        ((ls.y + rs.y) / 2f) * scale,
    )
    val nosePt = Offset(nose.x * scale, nose.y * scale)
    drawLine(color = color.copy(alpha = 0.18f), start = nosePt, end = mid,
        strokeWidth = 9f, cap = StrokeCap.Round)
    drawLine(color = color, start = nosePt, end = mid,
        strokeWidth = 2.2f, cap = StrokeCap.Round)
}

/**
 * Petits arcs orbitaux aux 4 coins de la bbox. Tournent en continu via
 * [phase]. Donne le feel "scan en cours" sans alourdir.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrbitalCorners(
    bbox: BBox,
    phase: Float,
    color: Color,
) {
    val (top, bot, left, right) = bbox
    val r = 14f
    val sweep = 60f
    val baseAngle = Math.toDegrees(phase.toDouble()).toFloat()
    val corners = listOf(
        Offset(left, top), Offset(right, top),
        Offset(left, bot), Offset(right, bot),
    )
    for ((i, c) in corners.withIndex()) {
        drawArc(
            color = color,
            startAngle = baseAngle + i * 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round),
        )
    }
}

/** Bounding box du squelette en coordonnées canvas (post-scale). */
private fun computeBBox(landmarks: List<Landmark>, scale: Float): BBox? {
    val visible = landmarks.filter { it.inFrameLikelihood > 0.3f }
    if (visible.isEmpty()) return null
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (lm in visible) {
        val x = lm.x * scale
        val y = lm.y * scale
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    // Padding pour que la scan-line dépasse un peu du corps (esthétique).
    val padX = (maxX - minX) * 0.08f
    val padY = (maxY - minY) * 0.05f
    return BBox(
        top = minY - padY,
        bottom = maxY + padY,
        left = minX - padX,
        right = maxX + padX,
    )
}

private data class BBox(val top: Float, val bottom: Float, val left: Float, val right: Float)

// ═══════════════════════════════════════
// Constantes visuelles
// ═══════════════════════════════════════

/** Couleurs néon par défaut. Override possible via paramètres composable. */
val NEON_CYAN = Color(0xFF00E5FF)
val NEON_GREEN = Color(0xFF00FF9C)
val NEON_PINK = Color(0xFFFF00E5)

/** Confidence minimum pour dessiner une connexion ou un keypoint. */
private const val MIN_DRAW_CONFIDENCE = 0.4f
/** Mains plus tolérantes (souvent partiellement visibles). */
private const val MIN_DRAW_CONFIDENCE_HAND = 0.3f

/**
 * Subset de keypoints à afficher comme cercles pulsants. On n'affiche pas les
 * 33 — les sub-features faciales (yeux, oreilles internes, etc.) seraient du
 * bruit visuel pour un mesh "body wireframe".
 */
private val KEY_POINTS = listOf(
    PoseLandmarkType.NOSE,
    PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST,
    PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE,
)

// ═══════════════════════════════════════════════════════════════════════
// #12 — SYMMETRY COLOR-CASCADE
// ═══════════════════════════════════════════════════════════════════════

/**
 * Mapping bone → couleur, calculé une fois par features et réutilisé pendant
 * tout le rendu. Évite des branches conditionnelles dans la boucle de draw
 * (~17 bones × 3 layers).
 */
private data class LimbColors(
    val torso: Color,
    val leftArm: Color,
    val rightArm: Color,
    val leftLeg: Color,
    val rightLeg: Color,
    val head: Color,
) {
    /** Retourne la couleur appropriée pour un bone donné par ses 2 endpoints. */
    fun colorFor(from: PoseLandmarkType, to: PoseLandmarkType): Color {
        // Heuristique : un bone qui touche un keypoint LEFT_X est "leftArm" /
        // "leftLeg" / etc. On favorise le côté car c'est le diagnostic visé.
        val involvesLeft = from.name.startsWith("LEFT_") || to.name.startsWith("LEFT_")
        val involvesRight = from.name.startsWith("RIGHT_") || to.name.startsWith("RIGHT_")
        val involvesArm = from.name.contains("SHOULDER") || from.name.contains("ELBOW") ||
                from.name.contains("WRIST") || to.name.contains("SHOULDER") ||
                to.name.contains("ELBOW") || to.name.contains("WRIST")
        val involvesLeg = from.name.contains("HIP") || from.name.contains("KNEE") ||
                from.name.contains("ANKLE") || from.name.contains("FOOT") ||
                to.name.contains("HIP") || to.name.contains("KNEE") ||
                to.name.contains("ANKLE") || to.name.contains("FOOT")
        return when {
            // Tronc : connexions épaule↔épaule, hanche↔hanche, ou diagonales
            // qui touchent les 2 côtés (LEFT_X ↔ RIGHT_Y)
            involvesLeft && involvesRight -> torso
            // Bras
            involvesLeft && involvesArm && !involvesLeg -> leftArm
            involvesRight && involvesArm && !involvesLeg -> rightArm
            // Jambes
            involvesLeft && involvesLeg && !involvesArm -> leftLeg
            involvesRight && involvesLeg && !involvesArm -> rightLeg
            // Tête (NOSE ↔ EAR)
            from.name.contains("NOSE") || to.name.contains("NOSE") ||
                    from.name.contains("EAR") || to.name.contains("EAR") -> head
            else -> torso
        }
    }

    companion object {
        fun allPrimary(c: Color) = LimbColors(c, c, c, c, c, c)
    }
}

/**
 * Calcule les couleurs des membres selon l'asymétrie L/R des longueurs.
 *
 *  - Si écart relatif (|L-R| / mean) > [LIMB_ASYMMETRY_ALERT_PCT] sur les bras,
 *    le bras le PLUS COURT passe en `accentColor` (pink alert) — il est sous-
 *    développé ou la posture le tire. L'autre reste en `primaryColor`.
 *  - Idem jambes.
 *  - Tronc/tête restent toujours en primary (l'asymétrie de tronc est captée
 *    via shoulderTilt / hipTilt et matérialisée par les guides #14).
 */
private fun computeLimbColors(
    features: MeshFeatures,
    primaryColor: Color,
    accentColor: Color,
): LimbColors {
    val a = features.analytics

    // Bras : prend les longueurs précalculées, calcule le ratio
    val armColors = if (a.leftArmLengthPx > 0f && a.rightArmLengthPx > 0f) {
        val mean = (a.leftArmLengthPx + a.rightArmLengthPx) / 2f
        val diff = abs(a.leftArmLengthPx - a.rightArmLengthPx)
        val pct = if (mean > 1f) diff / mean * 100f else 0f
        if (pct > LIMB_ASYMMETRY_ALERT_PCT) {
            // Le plus court est "alert" — c'est lui qui est dans la déviation
            if (a.leftArmLengthPx < a.rightArmLengthPx)
                accentColor to primaryColor
            else
                primaryColor to accentColor
        } else primaryColor to primaryColor
    } else primaryColor to primaryColor

    // Jambes
    val legColors = if (a.leftLegLengthPx > 0f && a.rightLegLengthPx > 0f) {
        val mean = (a.leftLegLengthPx + a.rightLegLengthPx) / 2f
        val diff = abs(a.leftLegLengthPx - a.rightLegLengthPx)
        val pct = if (mean > 1f) diff / mean * 100f else 0f
        if (pct > LIMB_ASYMMETRY_ALERT_PCT) {
            if (a.leftLegLengthPx < a.rightLegLengthPx)
                accentColor to primaryColor
            else
                primaryColor to accentColor
        } else primaryColor to primaryColor
    } else primaryColor to primaryColor

    return LimbColors(
        torso = primaryColor,
        leftArm = armColors.first,
        rightArm = armColors.second,
        leftLeg = legColors.first,
        rightLeg = legColors.second,
        head = primaryColor,
    )
}

// ═══════════════════════════════════════════════════════════════════════
// #14 — POSTURE GUIDES (lignes de référence idéale)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Trace les guides de posture si les tilts sont au-delà du seuil :
 *  - Axe spine vertical idéal (pointillé)
 *  - Lignes horizontales épaules + hanches idéales (pointillé)
 *
 * Sert à révéler visuellement la déviation du corps par rapport au "droit
 * parfait" — l'utilisateur voit immédiatement où il est désaligné.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPostureGuides(
    landmarksByType: Map<Int, Landmark>,
    scale: Float,
    analytics: com.shredcoach.app.domain.bodymesh.MeshAnalyticsSnapshot,
    accentColor: Color,
    primaryColor: Color,
) {
    val ls = landmarksByType[PoseLandmarkType.LEFT_SHOULDER.ordinal] ?: return
    val rs = landmarksByType[PoseLandmarkType.RIGHT_SHOULDER.ordinal] ?: return
    val lh = landmarksByType[PoseLandmarkType.LEFT_HIP.ordinal] ?: return
    val rh = landmarksByType[PoseLandmarkType.RIGHT_HIP.ordinal] ?: return

    val shouldersTilted = abs(analytics.shoulderTiltDeg) > POSTURE_TILT_GUIDE_THRESHOLD_DEG
    val hipsTilted = abs(analytics.hipTiltDeg) > POSTURE_TILT_GUIDE_THRESHOLD_DEG
    if (!shouldersTilted && !hipsTilted) return

    val shoulderMidX = (ls.x + rs.x) / 2f * scale
    val shoulderMidY = (ls.y + rs.y) / 2f * scale
    val hipMidX = (lh.x + rh.x) / 2f * scale
    val hipMidY = (lh.y + rh.y) / 2f * scale

    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

    // ─── Axe spine vertical idéal : ligne strictement verticale au midX(épaules)
    // ─── Spine moyen entre haut et bas (compromise) pour que la déviation soit visible
    val spineX = (shoulderMidX + hipMidX) / 2f
    val spineTopY = shoulderMidY - (hipMidY - shoulderMidY) * 0.15f
    val spineBotY = hipMidY + (hipMidY - shoulderMidY) * 0.10f
    drawLine(
        color = primaryColor.copy(alpha = 0.35f),
        start = Offset(spineX, spineTopY),
        end = Offset(spineX, spineBotY),
        strokeWidth = 1.4f,
        pathEffect = dash,
        cap = StrokeCap.Round,
    )

    // ─── Ligne horizontale épaules idéale ───
    if (shouldersTilted) {
        val shoulderHalfWidth = abs(rs.x - ls.x) / 2f * scale
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(shoulderMidX - shoulderHalfWidth * 1.15f, shoulderMidY),
            end = Offset(shoulderMidX + shoulderHalfWidth * 1.15f, shoulderMidY),
            strokeWidth = 1.4f,
            pathEffect = dash,
            cap = StrokeCap.Round,
        )
    }

    // ─── Ligne horizontale hanches idéale ───
    if (hipsTilted) {
        val hipHalfWidth = abs(rh.x - lh.x) / 2f * scale
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(hipMidX - hipHalfWidth * 1.15f, hipMidY),
            end = Offset(hipMidX + hipHalfWidth * 1.15f, hipMidY),
            strokeWidth = 1.4f,
            pathEffect = dash,
            cap = StrokeCap.Round,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// #11 — ANATOMICAL LABELS (Compose overlay avec lead lines)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Côté du corps où placer le label par rapport à son anchor — détermine la
 * direction de la lead line + l'alignement du chip.
 */
private enum class LabelSide { LEFT, RIGHT }

/**
 * Description d'un label anatomique à afficher.
 *
 * @param anchorX/Y position en pixels source image du keypoint d'ancrage
 * @param deltaXPx/deltaYPx offset du label par rapport à l'anchor en px canvas
 * @param side LEFT/RIGHT — alignement horizontal du chip et direction lead line
 * @param title label court (ex: "BICEPS L")
 * @param value chiffre à afficher (ex: "38")
 * @param unit unité (ex: "cm")
 * @param color teinte du chip + lead line
 */
private data class AnatomicalLabel(
    val anchorX: Float,
    val anchorY: Float,
    val deltaXPx: Float,
    val deltaYPx: Float,
    val side: LabelSide,
    val title: String,
    val value: String,
    val unit: String,
    val color: Color,
)

/** Hauteur approximative du chip en px — utilisée pour le centrage vertical. */
private const val LABEL_HEIGHT_PX = 36f

/**
 * Construit les labels à partir des features + calibration cm/px. Les longueurs
 * sont en cm RÉELS (calibrées via heightCm), pas en px.
 *
 * Stratégie de placement : les labels sont placés du côté EXTÉRIEUR du membre
 * (left arm → côté gauche du corps, right arm → côté droit), à une distance
 * constante de l'anchor pour rester lisibles à toutes les tailles de viewport.
 */
private fun computeAnatomicalLabels(
    features: MeshFeatures,
    cmPerPx: Float,
): List<AnatomicalLabel> {
    val byType = features.landmarks.associateBy { it.type }

    fun lm(t: PoseLandmarkType) = byType[t.ordinal]

    val labels = mutableListOf<AnatomicalLabel>()
    val color = NEON_CYAN

    // ─── BICEPS L : anchor = midpoint(épaule G, coude G), placé à GAUCHE ───
    val ls = lm(PoseLandmarkType.LEFT_SHOULDER)
    val le = lm(PoseLandmarkType.LEFT_ELBOW)
    if (ls != null && le != null && ls.inFrameLikelihood > 0.5f && le.inFrameLikelihood > 0.5f) {
        val len = hypot(ls.x - le.x, ls.y - le.y) * cmPerPx
        if (len > 0f) {
            labels += AnatomicalLabel(
                anchorX = (ls.x + le.x) / 2f,
                anchorY = (ls.y + le.y) / 2f,
                deltaXPx = -90f, // offset gauche (canvas px)
                deltaYPx = 0f,
                side = LabelSide.LEFT,
                title = "BICEPS L",
                value = len.roundToInt().toString(),
                unit = "cm",
                color = color,
            )
        }
    }

    // ─── BICEPS R : anchor = midpoint(épaule D, coude D), placé à DROITE ───
    val rs = lm(PoseLandmarkType.RIGHT_SHOULDER)
    val re = lm(PoseLandmarkType.RIGHT_ELBOW)
    if (rs != null && re != null && rs.inFrameLikelihood > 0.5f && re.inFrameLikelihood > 0.5f) {
        val len = hypot(rs.x - re.x, rs.y - re.y) * cmPerPx
        if (len > 0f) {
            labels += AnatomicalLabel(
                anchorX = (rs.x + re.x) / 2f,
                anchorY = (rs.y + re.y) / 2f,
                deltaXPx = 90f,
                deltaYPx = 0f,
                side = LabelSide.RIGHT,
                title = "BICEPS R",
                value = len.roundToInt().toString(),
                unit = "cm",
                color = color,
            )
        }
    }

    // ─── TORSO : midpoint(épaules) → midpoint(hanches), placé à GAUCHE ───
    val lh = lm(PoseLandmarkType.LEFT_HIP)
    val rh = lm(PoseLandmarkType.RIGHT_HIP)
    if (ls != null && rs != null && lh != null && rh != null) {
        val sx = (ls.x + rs.x) / 2f
        val sy = (ls.y + rs.y) / 2f
        val hx = (lh.x + rh.x) / 2f
        val hy = (lh.y + rh.y) / 2f
        val len = hypot(hx - sx, hy - sy) * cmPerPx
        if (len > 0f) {
            labels += AnatomicalLabel(
                anchorX = (sx + hx) / 2f,
                anchorY = (sy + hy) / 2f,
                deltaXPx = -110f, // un peu plus loin pour le tronc (centre)
                deltaYPx = 0f,
                side = LabelSide.LEFT,
                title = "TORSO",
                value = len.roundToInt().toString(),
                unit = "cm",
                color = color,
            )
        }
    }

    // ─── THIGH : hanche D → genou D, placé à DROITE ───
    val rk = lm(PoseLandmarkType.RIGHT_KNEE)
    if (rh != null && rk != null && rk.inFrameLikelihood > 0.5f) {
        val len = hypot(rh.x - rk.x, rh.y - rk.y) * cmPerPx
        if (len > 0f) {
            labels += AnatomicalLabel(
                anchorX = (rh.x + rk.x) / 2f,
                anchorY = (rh.y + rk.y) / 2f,
                deltaXPx = 100f,
                deltaYPx = 0f,
                side = LabelSide.RIGHT,
                title = "THIGH",
                value = len.roundToInt().toString(),
                unit = "cm",
                color = color,
            )
        }
    }

    // ─── SHOULDERS WIDTH : épaule G → épaule D, placé EN HAUT (offset Y -)
    if (ls != null && rs != null) {
        val len = hypot(ls.x - rs.x, ls.y - rs.y) * cmPerPx
        if (len > 0f) {
            labels += AnatomicalLabel(
                anchorX = (ls.x + rs.x) / 2f,
                anchorY = (ls.y + rs.y) / 2f,
                deltaXPx = 110f,
                deltaYPx = -50f,
                side = LabelSide.RIGHT,
                title = "SHOULDERS",
                value = len.roundToInt().toString(),
                unit = "cm",
                color = color,
            )
        }
    }

    return labels
}

@Composable
private fun AnatomicalLabelChip(
    label: AnatomicalLabel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF000814).copy(alpha = 0.85f),
        border = BorderStroke(0.8.dp, label.color.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                label.title,
                color = label.color.copy(alpha = 0.75f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    label.value,
                    color = label.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    label.unit,
                    color = label.color.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                )
            }
        }
    }
}

