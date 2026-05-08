package com.shredcoach.app.presentation.bodyscanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.shredcoach.app.domain.bodymesh.HAND_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.Landmark
import com.shredcoach.app.domain.bodymesh.MeshFeatures
import com.shredcoach.app.domain.bodymesh.POSE_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.PoseLandmarkType
import kotlin.math.max
import kotlin.math.min
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
@Composable
fun MeshRenderer(
    features: MeshFeatures,
    modifier: Modifier = Modifier,
    primaryColor: Color = NEON_CYAN,
    secondaryColor: Color = NEON_GREEN,
    accentColor: Color = NEON_PINK,
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

    Canvas(modifier = modifier.fillMaxSize()) {
        if (features.sourceImageWidth <= 0 || features.sourceImageHeight <= 0) return@Canvas

        // ─── Letterbox : scale + center pour préserver le ratio image source ───
        val srcW = features.sourceImageWidth.toFloat()
        val srcH = features.sourceImageHeight.toFloat()
        val scale = min(size.width / srcW, size.height / srcH)
        val drawnW = srcW * scale
        val drawnH = srcH * scale
        val offsetX = (size.width - drawnW) / 2f
        val offsetY = (size.height - drawnH) / 2f

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

            // ─── 4. Skeleton (squelette) — 3-layer depth pour relief ───
            // Le skeleton est le SUJET principal : il doit dominer visuellement
            // par-dessus la silhouette glow. 3 couches : haze (large flou) +
            // glow (medium) + core (line nette luminescente).
            for ((from, to) in POSE_CONNECTIONS) {
                val a = landmarksByType[from.ordinal]
                val b = landmarksByType[to.ordinal]
                if (a == null || b == null) continue
                if (a.inFrameLikelihood < MIN_DRAW_CONFIDENCE && b.inFrameLikelihood < MIN_DRAW_CONFIDENCE) continue
                val pa = Offset(a.x * scale, a.y * scale)
                val pb = Offset(b.x * scale, b.y * scale)
                // Layer 1 : haze large (depth)
                drawLine(
                    color = primaryColor.copy(alpha = 0.10f),
                    start = pa, end = pb, strokeWidth = 24f, cap = StrokeCap.Round,
                )
                // Layer 2 : glow medium
                drawLine(
                    color = primaryColor.copy(alpha = 0.40f),
                    start = pa, end = pb, strokeWidth = 9f, cap = StrokeCap.Round,
                )
                // Layer 3 : core net + saturé (le bone lui-même)
                drawLine(
                    color = primaryColor,
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

