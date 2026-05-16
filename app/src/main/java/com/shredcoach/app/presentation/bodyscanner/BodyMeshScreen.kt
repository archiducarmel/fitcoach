package com.shredcoach.app.presentation.bodyscanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R

/**
 * Page futuriste qui affiche le body mesh généré par IA.
 * Style : Tron / Ghost in the Shell / Cyberpunk 2077 medical scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMeshScreen(
    navController: NavController,
    viewModel: BodyScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    // V2 #17 — toggle 2D/3D. Default = 2D (vue principale, labels anatomiques).
    // Activé par bouton dans le header. Persisté uniquement en mémoire (pas de
    // valeur sticky en DataStore — la préférence est session-bound).
    var view3D by remember { mutableStateOf(false) }
    // V2.1 — sous-mode du 3D : silhouette (volumetric body) vs skeleton
    // (wireframe bones). Default = SILHOUETTE car c'est le sujet principal
    // ("body shape") attendu par l'utilisateur quand il toggle vers la 3D.
    var mesh3DMode by remember { mutableStateOf(Mesh3DMode.SILHOUETTE) }

    // Palette futuriste
    val voidBg = Color(0xFF000814)
    val deepBg = Color(0xFF001D3D)
    val neonCyan = Color(0xFF00E5FF)
    val neonGreen = Color(0xFF00FF9C)
    val neonPink = Color(0xFFFF00E5)
    val gridColor = neonCyan.copy(alpha = 0.12f)

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(voidBg, deepBg, voidBg)
                )
            )
    ) {
        // ═════════════════════════════════════════════
        // LAYOUT HUD PREMIUM — couches superposées :
        //   1. Background gradient (extérieur Box)
        //   2. Grid pattern animé
        //   3. Scan line verticale
        //   4. Mesh wireframe (fillMaxSize avec safe-paddings)
        //   5. MeshCornerFrames (HUD frame autour du mesh)
        //   6. Header overlay (top)
        //   7. HUD KPI corner anchors (top-left + top-right, sous header)
        //   8. Compact biometric strip (LazyRow horizontale, bottom)
        //
        // Réservation d'espace :
        //   - Top    : 88dp (status + header)
        //   - Bottom : 120dp (strip biométrique scrollable)
        //   - HxH    : 12dp partout pour respirer
        // ═════════════════════════════════════════════
        GridBackground(gridColor)
        AnimatedScanLine(neonCyan)

        val features = state.meshFeatures
        val meshTopReserve = 88.dp     // header + status
        // Bottom reserve réduit : strip rendu plus compact (suppression du
        // title row, padding réduit) + Surface alpha 0.45 pour laisser
        // respirer le mesh derrière. 88dp = chips ~52dp + padding 36dp.
        val meshBottomReserve = 88.dp
        val meshSidePad = 12.dp

        if (features != null) {
            // Calibration cm pour les anatomical labels (#11) : on parse la
            // taille saisie/déduite par le user. À 0, MeshRenderer masque
            // les labels — pas de chiffre fiable affichable.
            val heightCmInt = state.editHeightCm.toIntOrNull() ?: 0
            // V2 (#17) — bascule entre vue 2D classique et vue 3D rotatable.
            // Le 3D requiert `is3D = true` (ML Kit a renvoyé des z-coords) ;
            // sinon le toggle est masqué côté UI et on reste en 2D.
            val canShow3D = features.is3D
            if (view3D && canShow3D) {
                Mesh3DViewer(
                    features = features,
                    primaryColor = neonCyan,
                    accentColor = neonGreen,
                    mode = mesh3DMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = meshTopReserve,
                            bottom = meshBottomReserve,
                            start = meshSidePad,
                            end = meshSidePad,
                        )
                )
            } else {
                MeshRenderer(
                    features = features,
                    heightCm = heightCmInt,
                    symmetryColors = true,
                    showPostureGuides = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = meshTopReserve,
                            bottom = meshBottomReserve,
                            start = meshSidePad,
                            end = meshSidePad,
                        )
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = meshTopReserve,
                        bottom = meshBottomReserve,
                        start = meshSidePad,
                        end = meshSidePad,
                    )
            ) {
                MeshCornerFrames(neonCyan)
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = meshTopReserve, bottom = meshBottomReserve),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.GridOn, null,
                        Modifier.size(96.dp),
                        tint = neonCyan.copy(alpha = 0.3f),
                    )
                    Text(
                        stringResource(R.string.bodymesh_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = neonCyan,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        stringResource(R.string.bodymesh_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = neonCyan.copy(alpha = 0.5f),
                    )
                }
            }
        }

        // ─── Header overlay top ───
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = neonCyan)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.bodymesh_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = neonCyan,
                    letterSpacing = 3.sp,
                )
                Text(
                    stringResource(R.string.bodymesh_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = neonCyan.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                )
            }
            LiveIndicator(neonGreen)
            Spacer(Modifier.width(8.dp))
            // V2 (#17) — toggle 2D/3D. Affiché uniquement si features.is3D.
            // Sinon, l'icône 3D serait inopérante (rotation sur des keypoints
            // tous à z=0 = pas de 3D).
            if (features?.is3D == true) {
                // Sous-toggle skeleton/silhouette — visible uniquement quand
                // on est en mode 3D. Permet à l'user de switcher entre le
                // volume polygonal (sujet visuel "body shape") et le wireframe
                // squelette (structure interne, motion-capture feel).
                if (view3D) {
                    IconButton(
                        onClick = {
                            mesh3DMode = if (mesh3DMode == Mesh3DMode.SILHOUETTE)
                                Mesh3DMode.SKELETON else Mesh3DMode.SILHOUETTE
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            // AccessibilityNew = silhouette humaine pleine
                            // Polyline = wireframe filaire
                            if (mesh3DMode == Mesh3DMode.SILHOUETTE)
                                Icons.Default.AccessibilityNew
                            else
                                Icons.Default.Polyline,
                            contentDescription = stringResource(
                                if (mesh3DMode == Mesh3DMode.SILHOUETTE)
                                    R.string.bodymesh_action_view_skeleton_cd
                                else
                                    R.string.bodymesh_action_view_silhouette_cd
                            ),
                            tint = neonPink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = { view3D = !view3D },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (view3D) Icons.Default.ViewInAr else Icons.Default.GridOn,
                        contentDescription = stringResource(
                            if (view3D) R.string.bodymesh_action_view_2d_cd
                            else R.string.bodymesh_action_view_3d_cd
                        ),
                        tint = if (view3D) neonGreen else neonCyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            // Bouton "Composition corporelle" — accessible depuis le header
            // pour ne pas rajouter de boutons dans le bottom strip déjà chargé.
            IconButton(
                onClick = {
                    navController.navigate(
                        com.shredcoach.app.presentation.navigation.Screen.BodyComposition.route
                    )
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Hexagon,
                    contentDescription = stringResource(R.string.bodymesh_action_composition_cd),
                    tint = neonCyan,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }

        // ─── HUD KPI corner anchors (haut-gauche + haut-droite, sous header) ───
        // Visibles UNIQUEMENT si on a des analytics. Format chip ultra-compact.
        val analytics = features?.analytics
        if (analytics != null) {
            HudKpiAnchor(
                label = stringResource(R.string.bodymesh_analytics_posture),
                value = if (analytics.postureScore > 0) analytics.postureScore.toString() else "—",
                unit = "/100",
                accent = neonGreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 96.dp),
            )
            HudKpiAnchor(
                label = stringResource(R.string.bodymesh_analytics_vtaper),
                value = if (analytics.vTaperRatio > 0f)
                    String.format(java.util.Locale.US, "%.2f", analytics.vTaperRatio) else "—",
                unit = "",
                accent = neonCyan,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 96.dp),
            )
        }

        // ─── Compact biometric strip (bottom horizontal scroll) ───
        // Le strip + l'insight chip sont dans une Column alignée bottom.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            // ─── #15 LLM insight chip (au-dessus du strip) ───
            // Visible si :
            //  - insight cached/généré → affichage texte
            //  - en train de générer → affichage shimmer subtil
            // Sinon : chip masqué (pas d'espace gaspillé).
            if (state.meshInsight != null || state.isGeneratingInsight) {
                MeshInsightChip(
                    insight = state.meshInsight,
                    isLoading = state.isGeneratingInsight,
                    neonCyan = neonCyan,
                    neonGreen = neonGreen,
                )
            }
            BiometricStrip(
                state = state,
                analytics = analytics,
                neonCyan = neonCyan,
                neonGreen = neonGreen,
                neonPink = neonPink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ═══════════════════════════════════════
// MESH INSIGHT CHIP — LLM-generated 1-liner above the biometric strip
// ═══════════════════════════════════════

@Composable
private fun MeshInsightChip(
    insight: String?,
    isLoading: Boolean,
    neonCyan: Color,
    neonGreen: Color,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        // alpha bas (0.55) pour ne pas masquer le mesh — l'utilisateur peut
        // voir la silhouette qui glow à travers la card insight. Le border
        // neon green suffit à délimiter la card visuellement.
        color = Color(0xFF000814).copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, neonGreen.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Icône AI : luminescente, marque le côté "généré par IA"
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = neonGreen,
            )
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    CircularProgressIndicator(
                        color = neonGreen,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.bodymesh_insight_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = neonCyan.copy(alpha = 0.7f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            } else if (insight != null) {
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.weight(1f),
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// HUD KPI ANCHOR (chip flottant compact pour KPI critique)
// ═══════════════════════════════════════

@Composable
private fun HudKpiAnchor(
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // Surface ultra-compacte, semi-transparente, border accent.
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF000814).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 9.sp,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (unit.isNotBlank() && value != "—") {
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// BIOMETRIC STRIP — bottom horizontal scrollable de chips compacts
// ═══════════════════════════════════════

/** Chip data simple pour la LazyRow. Stable (data class) pour Compose. */
private data class StatChip(
    val label: String,
    val value: String,
    val unit: String,
    val accent: Color,
)

@Composable
private fun BiometricStrip(
    state: BodyScannerState,
    analytics: com.shredcoach.app.domain.bodymesh.MeshAnalyticsSnapshot?,
    neonCyan: Color,
    neonGreen: Color,
    neonPink: Color,
    modifier: Modifier = Modifier,
) {
    // Pré-résolution des labels i18n côté Composable. Important : les
    // stringResource doivent être appelés EN DEHORS du `remember` (interdit
    // d'invoquer du Composable code dans une lambda non-@Composable).
    val labelBmi = stringResource(R.string.bodymesh_chip_bmi)
    val labelBodyFat = stringResource(R.string.bodymesh_chip_body_fat)
    val labelHeight = stringResource(R.string.bodymesh_chip_height)
    val labelWeight = stringResource(R.string.bodymesh_chip_weight)
    val labelWaist = stringResource(R.string.bodymesh_chip_waist)
    val labelChest = stringResource(R.string.bodymesh_chip_chest)
    val labelHip = stringResource(R.string.bodymesh_chip_hip)
    val labelArm = stringResource(R.string.bodymesh_chip_arm)
    val labelThigh = stringResource(R.string.bodymesh_chip_thigh)
    val labelCalf = stringResource(R.string.bodymesh_chip_calf)
    val labelTiltShoulders = stringResource(R.string.bodymesh_analytics_tilt_shoulders)
    val labelTiltHips = stringResource(R.string.bodymesh_analytics_tilt_hips)
    val labelAsymShoulders = stringResource(R.string.bodymesh_analytics_asym_shoulders)
    val labelAsymHips = stringResource(R.string.bodymesh_analytics_asym_hips)

    // Construit la liste de chips. Ordre = priorité d'attention de l'user :
    //   1. KPIs synthèse (BMI, Body Fat) — santé globale
    //   2. Analytics anatomiques (asym, tilts) — premium
    //   3. Mesures détaillées (height, weight, waist, …) — référence
    val chips = remember(state, analytics, labelBmi, labelHeight) {
        buildList {
            // ─ KPIs synthèse ─
            if (state.computedBmi > 0) {
                add(StatChip(
                    label = labelBmi,
                    value = String.format(java.util.Locale.US, "%.1f", state.computedBmi),
                    unit = "",
                    accent = neonGreen,
                ))
            }
            if (state.editBodyFatPercent.isNotBlank()) {
                add(StatChip(
                    label = labelBodyFat,
                    value = state.editBodyFatPercent,
                    unit = "%",
                    accent = neonPink,
                ))
            }
            // ─ Anatomical (premium, dérivé du mesh) ─
            if (analytics != null) {
                add(StatChip(
                    label = labelTiltShoulders,
                    value = String.format(java.util.Locale.US, "%+.1f", analytics.shoulderTiltDeg),
                    unit = "°",
                    accent = neonCyan,
                ))
                add(StatChip(
                    label = labelTiltHips,
                    value = String.format(java.util.Locale.US, "%+.1f", analytics.hipTiltDeg),
                    unit = "°",
                    accent = neonCyan,
                ))
                add(StatChip(
                    label = labelAsymShoulders,
                    value = String.format(java.util.Locale.US, "%.1f", analytics.shoulderAsymmetryPct),
                    unit = "%",
                    accent = if (analytics.shoulderAsymmetryPct > 5f) neonPink else neonCyan,
                ))
                add(StatChip(
                    label = labelAsymHips,
                    value = String.format(java.util.Locale.US, "%.1f", analytics.hipAsymmetryPct),
                    unit = "%",
                    accent = if (analytics.hipAsymmetryPct > 5f) neonPink else neonCyan,
                ))
            }
            // ─ Mesures détaillées ─
            if (state.editHeightCm.isNotBlank())
                add(StatChip(labelHeight, state.editHeightCm, "cm", neonCyan))
            if (state.editWeightKg.isNotBlank())
                add(StatChip(labelWeight, state.editWeightKg, "kg", neonCyan))
            if (state.editWaistCm.isNotBlank())
                add(StatChip(labelWaist, state.editWaistCm, "cm", neonCyan))
            if (state.editChestCm.isNotBlank())
                add(StatChip(labelChest, state.editChestCm, "cm", neonCyan))
            if (state.editHipCm.isNotBlank())
                add(StatChip(labelHip, state.editHipCm, "cm", neonCyan))
            if (state.editArmCm.isNotBlank())
                add(StatChip(labelArm, state.editArmCm, "cm", neonCyan))
            if (state.editThighCm.isNotBlank())
                add(StatChip(labelThigh, state.editThighCm, "cm", neonCyan))
            if (state.editCalfCm.isNotBlank())
                add(StatChip(labelCalf, state.editCalfCm, "cm", neonCyan))
        }
    }

    // Conteneur transparent — chaque chip a son propre fond/border. Le mesh
    // continue de glow à travers. On enveloppe juste avec un gradient haut
    // (transparent → semi-opaque) qui agit comme un "fade" lisible sans
    // masquer le mesh derrière. Top fade = 24dp, fond = alpha 0.45 max.
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF000814).copy(alpha = 0.55f),
                    ),
                ),
            ),
    ) {
        // Strip horizontal scrollable — pas de title row, les chips sont
        // self-explanatory (label + value + unit).
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips) { chip ->
                BiometricChip(chip = chip)
            }
        }
    }
}

@Composable
private fun BiometricChip(chip: StatChip) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = chip.accent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, chip.accent.copy(alpha = 0.35f)),
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(min = 60.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                chip.label,
                style = MaterialTheme.typography.labelSmall,
                color = chip.accent.copy(alpha = 0.75f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 9.sp,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    chip.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = chip.accent,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (chip.unit.isNotBlank()) {
                    Text(
                        chip.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = chip.accent.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// GRID BACKGROUND
// ═══════════════════════════════════════

@Composable
private fun GridBackground(color: Color) {
    val inf = rememberInfiniteTransition(label = "gridShift")
    val shift by inf.animateFloat(0f, 40f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "gs")

    Canvas(Modifier.fillMaxSize()) {
        val cellSize = 40f
        val offsetX = shift
        val offsetY = shift * 0.5f
        // Vertical lines
        var x = -cellSize + offsetX % cellSize
        while (x < size.width + cellSize) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
            x += cellSize
        }
        // Horizontal lines
        var y = -cellSize + offsetY % cellSize
        while (y < size.height + cellSize) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
            y += cellSize
        }
    }
}

// ═══════════════════════════════════════
// SCAN LINE (vertical animée)
// ═══════════════════════════════════════

@Composable
private fun AnimatedScanLine(color: Color) {
    val inf = rememberInfiniteTransition(label = "scanLine")
    val progress by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Reverse), label = "sp")

    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * progress
        // Halo
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.15f), Color.Transparent),
                startY = (y - 100f).coerceAtLeast(0f), endY = y + 100f
            )
        )
        // Line
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1.5f, cap = StrokeCap.Round)
    }
}

// ═══════════════════════════════════════
// LIVE INDICATOR (cercle pulsant)
// ═══════════════════════════════════════

@Composable
private fun LiveIndicator(color: Color) {
    val inf = rememberInfiniteTransition(label = "live")
    val pulse by inf.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pl")

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulse))
        )
        Text("LIVE",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.5.sp)
    }
}

// ═══════════════════════════════════════
// CORNER FRAMES (style visée HUD)
// ═══════════════════════════════════════

@Composable
private fun MeshCornerFrames(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cs = 32f
        val sw = 2.5f
        val c = color
        drawLine(c, Offset(0f,0f), Offset(cs,0f), sw); drawLine(c, Offset(0f,0f), Offset(0f,cs), sw)
        drawLine(c, Offset(size.width,0f), Offset(size.width-cs,0f), sw); drawLine(c, Offset(size.width,0f), Offset(size.width,cs), sw)
        drawLine(c, Offset(0f,size.height), Offset(cs,size.height), sw); drawLine(c, Offset(0f,size.height), Offset(0f,size.height-cs), sw)
        drawLine(c, Offset(size.width,size.height), Offset(size.width-cs,size.height), sw); drawLine(c, Offset(size.width,size.height), Offset(size.width,size.height-cs), sw)
    }
}

