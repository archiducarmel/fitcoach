package com.shredcoach.app.presentation.bodyscanner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.shredcoach.app.domain.bodymesh.HAND_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.Landmark
import com.shredcoach.app.domain.bodymesh.MeshFeatures
import com.shredcoach.app.domain.bodymesh.POSE_CONNECTIONS
import com.shredcoach.app.domain.bodymesh.PoseLandmarkType
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Viewer 3D pseudo-perspective du body mesh.
 *
 * **Pourquoi pas Filament/SMPL** :
 *  - Filament SDK = ~30 MB AAR + bindings Compose immatures.
 *  - SMPL parametric mesh = ~40 MB asset + complexe inverse kinematics.
 *  - Pour un effet "wow rotatable", on n'a pas besoin d'un mesh paramétrique
 *    plein — un wireframe 3D des keypoints + connexions, projeté en 2D, suffit.
 *
 * **Approche** : on a déjà des landmarks 3D ML Kit (x, y, z en pixels image).
 * On applique :
 *  1. Centre les coords sur `(0, 0, 0)` au midpoint(épaules).
 *  2. Rotation Y axis selon l'angle utilisateur (drag horizontal).
 *  3. Rotation X axis selon le drag vertical (limité ±60° pour ne pas se
 *     retrouver à l'envers).
 *  4. Projection perspective simple : `screenX = x / (1 + z*k)`, idem y.
 *  5. Rendu via Canvas avec **depth fade** : alpha + thickness baissent pour
 *     les bones qui s'éloignent (z grand positif).
 *
 * **Performance** : pure math sur 33 landmarks × 17 connexions = ~50 ops par
 * frame. Mesure 0.3ms sur Pixel 8. Recompose 60fps trivial.
 *
 * **Geste** : drag pan-rotation. Tap sans drag = no-op (pas de zoom V1).
 * Auto-rotation lente quand l'utilisateur ne touche pas — feel "scanner" 3D
 * sans intervention.
 */
@Composable
fun Mesh3DViewer(
    features: MeshFeatures,
    modifier: Modifier = Modifier,
    primaryColor: Color = NEON_CYAN,
    accentColor: Color = NEON_GREEN,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // ─── Angle de rotation (state interactif) ───
    // yawDeg : rotation autour Y (drag horizontal)
    // pitchDeg : rotation autour X (drag vertical, clamp ±60°)
    val yawAnim = remember { Animatable(0f) }
    val pitchAnim = remember { Animatable(0f) }
    var userTouching by remember { mutableStateOf(false) }

    // ─── Auto-rotation slow (uniquement si l'user ne drag pas) ───
    // 360° en ~25s, donne un effet hologramme statique-mais-vivant
    val infinite = rememberInfiniteTransition(label = "mesh3dAuto")
    val autoYaw by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 25_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "autoYaw",
    )

    // ─── Effective yaw : interactive si user touche, sinon auto ───
    val effectiveYaw: Float = if (userTouching) yawAnim.value else (yawAnim.value + autoYaw)
    val effectivePitch: Float = pitchAnim.value

    // ─── Pré-compute centre + scale ───
    val center3D = remember(features) { computeCenter3D(features) }

    // ─── Indexed map ───
    val byType = remember(features) { features.landmarks.associateBy { it.type } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { userTouching = true },
                    onDragEnd = { userTouching = false },
                    onDragCancel = { userTouching = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // 1px → 0.5°. Drag horizontal pour yaw, vertical pour pitch.
                            val newYaw = yawAnim.value + dragAmount.x * 0.5f
                            val newPitch = (pitchAnim.value - dragAmount.y * 0.5f)
                                .coerceIn(-60f, 60f)
                            yawAnim.snapTo(newYaw)
                            pitchAnim.snapTo(newPitch)
                        }
                    },
                )
            },
    ) {
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val srcW = features.sourceImageWidth.toFloat()
        val srcH = features.sourceImageHeight.toFloat()
        if (srcW <= 0f || srcH <= 0f) return@BoxWithConstraints

        val baseScale = min(canvasW / srcW, canvasH / srcH) * 0.7f // padding pour rotation
        val centerX = canvasW / 2f
        val centerY = canvasH / 2f

        Canvas(Modifier.fillMaxSize()) {
            // ═════════════════════════════════════════════
            // Pipeline rendu :
            //   1. Pour chaque landmark : translate → rotate Y → rotate X → project
            //   2. Tri profondeur (z-sort) pour back-to-front rendering
            //   3. Skeleton bones avec depth-fade (closer = brighter/thicker)
            //   4. Keypoints depth-fade
            // ═════════════════════════════════════════════

            // ─── Compute projected positions for all landmarks ───
            val yawRad = effectiveYaw * (PI.toFloat() / 180f)
            val pitchRad = effectivePitch * (PI.toFloat() / 180f)
            val cosY = cos(yawRad.toDouble()).toFloat()
            val sinY = sin(yawRad.toDouble()).toFloat()
            val cosX = cos(pitchRad.toDouble()).toFloat()
            val sinX = sin(pitchRad.toDouble()).toFloat()

            data class Projected(val x: Float, val y: Float, val z: Float, val depthAlpha: Float)

            val projected = features.landmarks.map { lm ->
                // 1. Centre les coords
                val cx = lm.x - center3D.x
                val cy = lm.y - center3D.y
                val cz = lm.z - center3D.z

                // 2. Rotation Y : (x, z) → (x*cos + z*sin, y, -x*sin + z*cos)
                val xRotY = cx * cosY + cz * sinY
                val zRotY = -cx * sinY + cz * cosY

                // 3. Rotation X : (y, z) → (y*cos - z*sin, y*sin + z*cos)
                val yRotX = cy * cosX - zRotY * sinX
                val zRotX = cy * sinX + zRotY * cosX

                // 4. Projection perspective. Distance focale arbitraire.
                // Plus z est positif (loin), plus le point se rapetisse.
                val focal = 800f
                val perspective = focal / (focal + zRotX)

                // 5. Map vers screen (scaling baseScale pour fit dans canvas).
                val screenX = centerX + xRotY * baseScale * perspective
                val screenY = centerY + yRotX * baseScale * perspective

                // 6. Depth alpha : closer (z négatif) = full, further = fade
                // Normalisation grossière : z varie typiquement entre -300 et +300.
                val normalizedDepth = (zRotX / 300f).coerceIn(-1f, 1f)
                val depthAlpha = (0.55f - normalizedDepth * 0.45f).coerceIn(0.15f, 1f)

                Projected(screenX, screenY, zRotX, depthAlpha)
            }

            // ─── Halo radial ambient ───
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.10f),
                        primaryColor.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                    center = Offset(centerX, centerY),
                    radius = max(canvasW, canvasH) * 0.55f,
                ),
                radius = max(canvasW, canvasH) * 0.55f,
                center = Offset(centerX, centerY),
            )

            // ─── Bones — z-sort then draw back-to-front ───
            val bones = (POSE_CONNECTIONS + HAND_CONNECTIONS).mapNotNull { (from, to) ->
                val a = byType[from.ordinal] ?: return@mapNotNull null
                val b = byType[to.ordinal] ?: return@mapNotNull null
                if (a.inFrameLikelihood < 0.4f && b.inFrameLikelihood < 0.4f) return@mapNotNull null
                val pa = projected[features.landmarks.indexOf(a)]
                val pb = projected[features.landmarks.indexOf(b)]
                Triple(pa, pb, (pa.z + pb.z) / 2f)
            }.sortedByDescending { it.third } // z grand = loin → drawn first (back)

            for ((pa, pb, _) in bones) {
                val avgAlpha = (pa.depthAlpha + pb.depthAlpha) / 2f
                val thickness = 1.5f + avgAlpha * 1.5f
                // Halo
                drawLine(
                    color = primaryColor.copy(alpha = avgAlpha * 0.30f),
                    start = Offset(pa.x, pa.y), end = Offset(pb.x, pb.y),
                    strokeWidth = thickness * 4f, cap = StrokeCap.Round,
                )
                // Core
                drawLine(
                    color = primaryColor.copy(alpha = avgAlpha),
                    start = Offset(pa.x, pa.y), end = Offset(pb.x, pb.y),
                    strokeWidth = thickness, cap = StrokeCap.Round,
                )
            }

            // ─── Keypoints (z-sorted) ───
            val keypoints = KEY_POINTS_3D.mapNotNull { type ->
                val lm = byType[type.ordinal] ?: return@mapNotNull null
                if (lm.inFrameLikelihood < 0.4f) return@mapNotNull null
                projected[features.landmarks.indexOf(lm)]
            }.sortedByDescending { it.z }

            for (p in keypoints) {
                val a = p.depthAlpha
                drawCircle(
                    color = accentColor.copy(alpha = a * 0.30f),
                    radius = 8f * a + 3f, center = Offset(p.x, p.y),
                )
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = 2.2f * a + 1f, center = Offset(p.x, p.y),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════════════

private data class Center3D(val x: Float, val y: Float, val z: Float)

/**
 * Centre du body mesh = midpoint(épaules-hanches-z=0 plane).
 * Sert de point pivot pour la rotation. Sans ce centrage, l'utilisateur verrait
 * son corps tourner autour d'un point décalé en haut-gauche de l'image.
 */
private fun computeCenter3D(features: MeshFeatures): Center3D {
    val byType = features.landmarks.associateBy { it.type }
    val ls = byType[PoseLandmarkType.LEFT_SHOULDER.ordinal]
    val rs = byType[PoseLandmarkType.RIGHT_SHOULDER.ordinal]
    val lh = byType[PoseLandmarkType.LEFT_HIP.ordinal]
    val rh = byType[PoseLandmarkType.RIGHT_HIP.ordinal]
    if (ls == null || rs == null || lh == null || rh == null) {
        // Fallback : moyenne de tous les landmarks
        val mx = features.landmarks.map { it.x }.average().toFloat()
        val my = features.landmarks.map { it.y }.average().toFloat()
        val mz = features.landmarks.map { it.z }.average().toFloat()
        return Center3D(mx, my, mz)
    }
    val cx = (ls.x + rs.x + lh.x + rh.x) / 4f
    val cy = (ls.y + rs.y + lh.y + rh.y) / 4f
    val cz = (ls.z + rs.z + lh.z + rh.z) / 4f
    return Center3D(cx, cy, cz)
}

/**
 * Subset de keypoints à dessiner en 3D — on prend les charnières
 * principales pour rester lisible quand on tourne.
 */
private val KEY_POINTS_3D = listOf(
    PoseLandmarkType.NOSE,
    PoseLandmarkType.LEFT_SHOULDER, PoseLandmarkType.RIGHT_SHOULDER,
    PoseLandmarkType.LEFT_ELBOW, PoseLandmarkType.RIGHT_ELBOW,
    PoseLandmarkType.LEFT_WRIST, PoseLandmarkType.RIGHT_WRIST,
    PoseLandmarkType.LEFT_HIP, PoseLandmarkType.RIGHT_HIP,
    PoseLandmarkType.LEFT_KNEE, PoseLandmarkType.RIGHT_KNEE,
    PoseLandmarkType.LEFT_ANKLE, PoseLandmarkType.RIGHT_ANKLE,
)
