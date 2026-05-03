package com.shredcoach.app.presentation.bodyscanner

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.io.File

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
        // ─── Grid pattern background ───
        GridBackground(gridColor)

        // ─── Animated scan line ───
        AnimatedScanLine(neonCyan)

        Column(Modifier.fillMaxSize()) {
            // ─── Header ───
            Row(
                Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.Default.ArrowBack, "Retour", tint = neonCyan)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "BODY MESH SCAN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = neonCyan,
                        letterSpacing = 3.sp
                    )
                    Text(
                        "Powered by Shreddy AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = neonCyan.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                }
                // Indicateur live
                LiveIndicator(neonGreen)
                Spacer(Modifier.width(12.dp))
            }

            // ─── Mesh image centrée ───
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val meshPath = state.meshImagePath
                val bitmap = remember(meshPath) {
                    meshPath?.let { path ->
                        try {
                            val file = File(path)
                            if (file.exists()) BitmapFactory.decodeFile(path) else null
                        } catch (_: Exception) { null }
                    }
                }

                if (bitmap != null) {
                    // Halo glow derrière l'image
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { alpha = 0.6f }
                            .blur(40.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(neonCyan.copy(alpha = 0.3f), Color.Transparent)
                                )
                            )
                    )
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Body mesh",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    // Overlay corners
                    MeshCornerFrames(neonCyan)
                } else {
                    // Fallback : pas encore généré
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.GridOn, null,
                            Modifier.size(96.dp),
                            tint = neonCyan.copy(alpha = 0.3f)
                        )
                        Text(
                            "MESH NON GÉNÉRÉ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = neonCyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "Retourne au Body Scanner pour générer le mesh",
                            style = MaterialTheme.typography.bodySmall,
                            color = neonCyan.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // ─── Stats panel futuriste ───
            HologramStatsPanel(state, neonCyan, neonGreen, neonPink)
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

// ═══════════════════════════════════════
// HOLOGRAM STATS PANEL
// ═══════════════════════════════════════

@Composable
private fun HologramStatsPanel(
    state: BodyScannerState,
    neonCyan: Color,
    neonGreen: Color,
    neonPink: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF000814).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, neonCyan.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Analytics, null, Modifier.size(16.dp), tint = neonCyan)
                Text("BIOMETRIC READOUT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = neonCyan,
                    letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Text("SYSTEM ONLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = neonGreen.copy(alpha = 0.8f),
                    letterSpacing = 1.sp)
            }

            // Grid de stats (3 colonnes × 2 lignes)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HologramStat("HEIGHT", state.editHeightCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
                HologramStat("WEIGHT", state.editWeightKg.ifBlank { "—" }, "kg", neonCyan, Modifier.weight(1f))
                HologramStat("BMI",
                    if (state.computedBmi > 0) String.format(java.util.Locale.US, "%.1f", state.computedBmi) else "—",
                    "", neonGreen, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HologramStat("WAIST", state.editWaistCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
                HologramStat("CHEST", state.editChestCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
                HologramStat("BODY FAT", state.editBodyFatPercent.ifBlank { "—" }, "%", neonPink, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HologramStat("ARM", state.editArmCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
                HologramStat("THIGH", state.editThighCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
                HologramStat("CALF", state.editCalfCm.ifBlank { "—" }, "cm", neonCyan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HologramStat(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(
            color.copy(alpha = 0.06f),
            shape = RoundedCornerShape(8.dp)
        ).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 9.sp
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.ExtraBold
            )
            if (unit.isNotBlank() && value != "—") {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
