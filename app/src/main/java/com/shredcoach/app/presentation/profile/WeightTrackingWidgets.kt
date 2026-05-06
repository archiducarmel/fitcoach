package com.shredcoach.app.presentation.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val ErrorRed = Color(0xFFEF4444)
private val WeightBlue = Color(0xFF3B82F6)

// ═══════════════════════════════════════
// TAB POIDS — point d'entrée
// ═══════════════════════════════════════

/**
 * Onglet "Poids" du profil. Refonte premium :
 *  - Hero card "Progression vers l'objectif" (visualisation animée).
 *  - Édition de l'objectif inline avec auto-save debounced + feedback "✓".
 *  - Graphique d'évolution lissé (Bézier) avec ligne d'objectif + tabs période.
 *  - Tendance hebdo (verdict qualitatif).
 *  - Historique compact avec deltas par pesée.
 *
 * Les actions write passent toutes par le ViewModel — aucun state local
 * critique ici, donc rotation/recompose ne perd rien.
 */
@Composable
fun WeightTrackingTab(state: ProfileState, viewModel: ProfileViewModel) {
    val profile = state.profile ?: return
    val logs = state.weightLogs

    // ── Header avec CTA pesée ──
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Suivi du poids", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${logs.size} pesée${if (logs.size > 1) "s" else ""} · objectif ${formatKg(profile.targetWeightKg)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        FilledTonalButton(onClick = { viewModel.showAddWeight() }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Peser")
        }
    }

    // ── Hero card progression ──
    WeightProgressHero(
        currentKg = profile.currentWeightKg,
        targetKg = profile.targetWeightKg,
        startKg = logs.minByOrNull { it.date }?.weightKg ?: profile.currentWeightKg,
        weeklyChange = state.weeklyChange
    )

    // ── Édition objectif (inline, auto-save) ──
    TargetWeightEditor(
        editValue = state.editTargetWeight,
        targetSaving = state.targetSaving,
        targetSavedAt = state.targetSavedAt,
        currentKg = profile.currentWeightKg,
        onChange = { viewModel.onTargetWeightChanged(it) },
        onPreset = { viewModel.setTargetWeightImmediate(it) }
    )

    // ── Graphique d'évolution ──
    if (logs.isNotEmpty()) {
        WeightChartCard(
            logs = logs,
            targetKg = profile.targetWeightKg
        )
    } else {
        EmptyHistoryCard(onAdd = { viewModel.showAddWeight() })
    }

    // ── Historique compact (5 dernières pesées avec deltas) ──
    if (logs.size >= 1) {
        WeightHistoryCard(logs = logs)
    }
}

// ═══════════════════════════════════════
// 1. HERO CARD PROGRESSION
// ═══════════════════════════════════════

/**
 * Carte hero : barre de progression visuelle entre poids initial et objectif,
 * marquée par la position actuelle. Couleur du gradient adaptée à la
 * direction (perte → vert, prise → bleu).
 *
 * Signal "wow" : la barre est animée à l'apparition (0 → ratio cible) et
 * tout changement de target déclenche aussi une animation.
 */
@Composable
private fun WeightProgressHero(
    currentKg: Double,
    targetKg: Double,
    startKg: Double,
    weeklyChange: Double
) {
    // Direction du programme : losing si target < start, gaining sinon.
    val isLosing = targetKg < startKg
    val totalDelta = abs(startKg - targetKg).coerceAtLeast(0.1)
    val achieved = abs(startKg - currentKg)
    val ratio = (achieved / totalDelta).toFloat().coerceIn(0f, 1f)

    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = tween(900),
        label = "weight-progress"
    )

    val accentColor = if (isLosing) OrangeVibrant else WeightBlue

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MonitorWeight, null, Modifier.size(20.dp), tint = accentColor)
                Text("Ta progression",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${(animatedRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor)
            }

            // Big current weight
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    String.format(java.util.Locale.US, "%.1f", currentKg),
                    style = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum", color = MaterialTheme.colorScheme.onSurface)
                )
                Text("kg", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp))
            }

            // Barre de progression custom
            ProgressBar(
                ratio = animatedRatio,
                accent = accentColor,
                startKg = startKg,
                targetKg = targetKg
            )

            // Verdict + tendance
            ProgressVerdict(
                currentKg = currentKg,
                targetKg = targetKg,
                weeklyChange = weeklyChange,
                isLosing = isLosing
            )
        }
    }
}

@Composable
private fun ProgressBar(
    ratio: Float,
    accent: Color,
    startKg: Double,
    targetKg: Double
) {
    val barHeight = 14.dp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth().height(barHeight)
                .clip(RoundedCornerShape(barHeight))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            // Fill avec gradient horizontal accent → accent vif
            Box(
                Modifier
                    .fillMaxWidth(ratio.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(barHeight))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.6f), accent)
                        )
                    )
            )
        }
        // Labels Départ | Objectif
        Row(Modifier.fillMaxWidth()) {
            Column {
                Text("Départ", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                Text(formatKg(startKg), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Flag, null, Modifier.size(11.dp), tint = NeonGreen)
                    Text("Objectif", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
                Text(formatKg(targetKg), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, color = NeonGreen)
            }
        }
    }
}

@Composable
private fun ProgressVerdict(
    currentKg: Double,
    targetKg: Double,
    weeklyChange: Double,
    isLosing: Boolean
) {
    val remaining = if (isLosing) currentKg - targetKg else targetKg - currentKg
    val reached = remaining <= 0.1
    val verdictAccent = if (reached) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (reached) NeonGreen.copy(alpha = 0.12f) else verdictAccent.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icône
            val (icon, iconColor) = when {
                reached -> Icons.Default.Check to NeonGreen
                weeklyChange < -0.05 -> Icons.Default.TrendingDown to NeonGreen
                weeklyChange > 0.05 -> Icons.AutoMirrored.Filled.TrendingUp to (if (isLosing) ErrorRed else NeonGreen)
                else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            }
            Icon(icon, null, Modifier.size(20.dp), tint = iconColor)

            Column(Modifier.weight(1f)) {
                val title = when {
                    reached -> "Objectif atteint ! 🎉"
                    isLosing && weeklyChange < -0.05 -> "Sur la bonne voie"
                    isLosing && weeklyChange > 0.05 -> "Tu reprends — ajuste"
                    !isLosing && weeklyChange > 0.05 -> "Bonne progression"
                    !isLosing && weeklyChange < -0.05 -> "Tu perds — ajuste"
                    else -> "Stabilité actuelle"
                }
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                val subtitle = when {
                    reached -> "Bravo, tu as atteint ton objectif"
                    abs(weeklyChange) < 0.05 -> "Plus que ${formatKg(remaining)} à parcourir"
                    else -> {
                        val weeksToGoal = if (weeklyChange != 0.0) abs(remaining / weeklyChange) else Double.POSITIVE_INFINITY
                        if (weeksToGoal.isFinite() && weeksToGoal in 0.5..200.0) {
                            val rounded = weeksToGoal.toInt().coerceAtLeast(1)
                            "${formatKg(remaining)} restant · ETA ~$rounded semaine${if (rounded > 1) "s" else ""}"
                        } else {
                            "${formatKg(remaining)} restant"
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

// ═══════════════════════════════════════
// 2. ÉDITEUR D'OBJECTIF (inline, auto-save)
// ═══════════════════════════════════════
//
// Fix du bug "modification objectif pas prise en compte" : auto-save 700ms
// après le dernier keystroke (cf. ProfileViewModel.scheduleTargetSave).
// Boutons preset à -2 / -5 / +0.5 / +2 / +5 kg pour les ajustements rapides.

@Composable
private fun TargetWeightEditor(
    editValue: String,
    targetSaving: Boolean,
    targetSavedAt: Long,
    currentKg: Double,
    onChange: (String) -> Unit,
    onPreset: (Double) -> Unit
) {
    // Badge "Enregistré ✓" affiché 2.5s après chaque save réussi.
    // State local : on déclenche un Effect quand targetSavedAt change pour
    // basculer showSaved à true puis le ramener à false après le délai.
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(targetSavedAt) {
        if (targetSavedAt > 0L) {
            showSaved = true
            kotlinx.coroutines.delay(2500)
            showSaved = false
        }
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Flag, null, Modifier.size(20.dp), tint = NeonGreen)
                Text("Mon objectif",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                // Indicateur de save
                AnimatedVisibility(visible = targetSaving, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = NeonGreen
                        )
                        Text("Sauvegarde…", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }
                AnimatedVisibility(visible = showSaved, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = NeonGreen)
                        Text("Enregistré", style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            OutlinedTextField(
                value = editValue,
                onValueChange = onChange,
                label = { Text("Objectif (kg)") },
                trailingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Sauvegarde automatique après saisie", style = MaterialTheme.typography.labelSmall) }
            )

            // Presets : ajustements rapides depuis le poids actuel.
            // 5 chips compacts qui tiennent en portrait standard (≥360dp).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetChip("-5 kg", Modifier.weight(1f)) { onPreset(currentKg - 5) }
                PresetChip("-2 kg", Modifier.weight(1f)) { onPreset(currentKg - 2) }
                PresetChip("=", Modifier.weight(0.6f)) { onPreset(currentKg) }
                PresetChip("+2 kg", Modifier.weight(1f)) { onPreset(currentKg + 2) }
                PresetChip("+5 kg", Modifier.weight(1f)) { onPreset(currentKg + 5) }
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = OrangeVibrant.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.3f))
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = OrangeVibrant,
                maxLines = 1)
        }
    }
}

// ═══════════════════════════════════════
// 3. GRAPHIQUE D'ÉVOLUTION (premium)
// ═══════════════════════════════════════
//
// Améliorations vs ancien WChart :
//  - Courbe lissée par splines de Catmull-Rom convertie en cubiques Bézier.
//  - Gradient sous la courbe (zone de remplissage).
//  - Ligne d'objectif horizontale en pointillé (NeonGreen).
//  - Tabs de période (7j / 30j / 90j / Tout).
//  - Échelle Y avec labels alignés droite, tabular nums.
//  - Échelle X : labels date sous le graphe (1er, dernier, médiane).
//  - Empty state stylé si pas assez de points.
//  - Affiche le dernier point comme un "halo" pulsant pour le repérer.

private enum class ChartPeriod(val label: String, val days: Int) {
    WEEK("7 j", 7),
    MONTH("30 j", 30),
    QUARTER("90 j", 90),
    ALL("Tout", Int.MAX_VALUE)
}

@Composable
private fun WeightChartCard(logs: List<WeightLogEntity>, targetKg: Double) {
    var period by remember { mutableStateOf(ChartPeriod.MONTH) }

    val today = LocalDate.now()
    val cutoff = if (period == ChartPeriod.ALL) LocalDate.MIN else today.minusDays(period.days.toLong())
    val filtered = logs.filter { it.date.isAfter(cutoff) || it.date.isEqual(cutoff) }
        .sortedBy { it.date }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShowChart, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Évolution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${filtered.size} pesée${if (filtered.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            // Tabs de période
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChartPeriod.values().forEach { p ->
                    val selected = p == period
                    Surface(
                        onClick = { period = p },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(p.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("Aucune pesée sur cette période",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                WeightChartCanvas(
                    logs = filtered,
                    targetKg = targetKg,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )

                // Mini stats sous le graphe
                ChartStatsRow(filtered)
            }
        }
    }
}

@Composable
private fun WeightChartCanvas(
    logs: List<WeightLogEntity>,
    targetKg: Double,
    modifier: Modifier
) {
    val lineColor = OrangeVibrant
    val targetColor = NeonGreen
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisLabelColor, fontFeatureSettings = "tnum")

    val animatedAlpha by animateFloatAsState(
        targetValue = 1f, animationSpec = tween(700), label = "weight-chart-fade"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Padding interne pour les labels
        val padL = 44f
        val padR = 8f
        val padT = 8f
        val padB = 24f
        val chartW = w - padL - padR
        val chartH = h - padT - padB

        // Bornes Y : on inclut target ET min/max poids pour que tout soit visible
        val weights = logs.map { it.weightKg.toFloat() }
        val rawMin = weights.min().coerceAtMost(targetKg.toFloat())
        val rawMax = weights.max().coerceAtLeast(targetKg.toFloat())
        val rangeRaw = (rawMax - rawMin).coerceAtLeast(0.5f)
        val pad = max(0.3f, rangeRaw * 0.08f)
        val yMin = rawMin - pad
        val yMax = rawMax + pad
        val yRange = (yMax - yMin).coerceAtLeast(0.5f)

        // Axe Y : 4 labels (incluant min et max)
        val gridLines = 4
        for (i in 0..gridLines) {
            val v = yMin + yRange * i / gridLines
            val y = padT + chartH * (1f - i.toFloat() / gridLines)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
            val label = String.format(java.util.Locale.US, "%.1f", v)
            val tl = textMeasurer.measure(label, labelStyle)
            drawText(tl, topLeft = Offset(padL - tl.size.width - 6f, y - tl.size.height / 2f))
        }

        // Ligne d'objectif (pointillée)
        if (targetKg in yMin.toDouble()..yMax.toDouble()) {
            val yTarget = padT + chartH * (1f - ((targetKg - yMin) / yRange)).toFloat()
            drawLine(
                color = targetColor.copy(alpha = 0.7f),
                start = Offset(padL, yTarget),
                end = Offset(w - padR, yTarget),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }

        // Position X : par index uniformément réparti (assez correct pour des
        // pesées espacées de manière régulière ; en cas de gros gaps, le
        // graphique est plus "régulier" qu'exact, ce qui évite les écrasements
        // d'axe pour une pesée dense puis 30 jours de silence).
        val n = logs.size
        if (n == 1) {
            // Cas un seul point : on le dessine au centre
            val cx = padL + chartW / 2f
            val cy = padT + chartH * (1f - ((logs[0].weightKg - yMin) / yRange)).toFloat()
            drawCircle(lineColor.copy(alpha = animatedAlpha), 14f, Offset(cx, cy))
            drawCircle(lineColor.copy(alpha = animatedAlpha), 6f, Offset(cx, cy))
            drawCircle(Color.White, 3f, Offset(cx, cy))
            return@Canvas
        }
        val stepX = chartW / (n - 1)

        val points = logs.mapIndexed { i, log ->
            val x = padL + i * stepX
            val y = padT + chartH * (1f - ((log.weightKg - yMin) / yRange)).toFloat()
            Offset(x, y)
        }

        // Courbe lissée (Catmull-Rom → Bézier cubique)
        val curvePath = buildSmoothPath(points)

        // Zone gradient sous la courbe
        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, padT + chartH)
            lineTo(points.first().x, padT + chartH)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.32f * animatedAlpha),
                    lineColor.copy(alpha = 0.0f)
                ),
                startY = padT,
                endY = padT + chartH
            )
        )

        // La courbe
        drawPath(
            path = curvePath,
            color = lineColor.copy(alpha = animatedAlpha),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )

        // Points : tous en petit, le dernier avec halo
        points.forEachIndexed { i, p ->
            val isLast = i == points.lastIndex
            if (isLast) {
                drawCircle(lineColor.copy(alpha = 0.25f * animatedAlpha), 14f, p)
                drawCircle(lineColor.copy(alpha = animatedAlpha), 6f, p)
                drawCircle(Color.White, 3f, p)
            } else {
                drawCircle(lineColor.copy(alpha = animatedAlpha), 4f, p)
                drawCircle(Color.White, 1.8f, p)
            }
        }

        // Labels axe X : premier et dernier (et milieu si n >= 5)
        val dateFmt = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.FRENCH)
        val toRender = mutableListOf<Pair<Float, String>>()
        toRender += points.first().x to logs.first().date.format(dateFmt)
        if (n >= 5) {
            val mid = n / 2
            toRender += points[mid].x to logs[mid].date.format(dateFmt)
        }
        toRender += points.last().x to logs.last().date.format(dateFmt)

        toRender.forEach { (x, label) ->
            val tl = textMeasurer.measure(label, labelStyle)
            val labelX = (x - tl.size.width / 2f).coerceIn(padL, w - padR - tl.size.width)
            drawText(tl, topLeft = Offset(labelX, h - tl.size.height - 2f))
        }
    }
}

/**
 * Construit un Path lissé via Catmull-Rom convertie en Bézier cubique.
 * tension=0.5 → spline standard, équivalente "naturelle" à l'œil.
 */
private fun buildSmoothPath(pts: List<Offset>): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    if (pts.size < 3) {
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }
    path.moveTo(pts[0].x, pts[0].y)
    val tension = 0.5f
    for (i in 0 until pts.size - 1) {
        val p0 = if (i == 0) pts[i] else pts[i - 1]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = if (i + 2 < pts.size) pts[i + 2] else p2

        val c1x = p1.x + (p2.x - p0.x) * tension / 3f
        val c1y = p1.y + (p2.y - p0.y) * tension / 3f
        val c2x = p2.x - (p3.x - p1.x) * tension / 3f
        val c2y = p2.y - (p3.y - p1.y) * tension / 3f
        path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
    }
    return path
}

@Composable
private fun ChartStatsRow(logs: List<WeightLogEntity>) {
    if (logs.size < 2) return
    val sorted = logs.sortedBy { it.date }
    val first = sorted.first(); val last = sorted.last()
    val delta = last.weightKg - first.weightKg
    val days = ChronoUnit.DAYS.between(first.date, last.date).coerceAtLeast(1)
    val perWeek = delta / days * 7

    val deltaColor = when {
        abs(delta) < 0.05 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        delta < 0 -> NeonGreen
        else -> ErrorRed
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        ChartStat(
            label = "Variation",
            value = (if (delta >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f kg", delta),
            color = deltaColor
        )
        ChartStat(
            label = "Min",
            value = formatKg(logs.minOf { it.weightKg }),
            color = MaterialTheme.colorScheme.onSurface
        )
        ChartStat(
            label = "Max",
            value = formatKg(logs.maxOf { it.weightKg }),
            color = MaterialTheme.colorScheme.onSurface
        )
        ChartStat(
            label = "/ semaine",
            value = (if (perWeek >= 0) "+" else "") + String.format(java.util.Locale.US, "%.2f", perWeek),
            color = deltaColor
        )
    }
}

@Composable
private fun ChartStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

// ═══════════════════════════════════════
// 4. EMPTY STATE
// ═══════════════════════════════════════

@Composable
private fun EmptyHistoryCard(onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.06f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeVibrant.copy(alpha = 0.2f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MonitorWeight, null, Modifier.size(28.dp), tint = OrangeVibrant)
            }
            Text("Démarre ton suivi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Pèse-toi le matin à jeun, même horaire chaque fois pour des données fiables.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            FilledTonalButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ma première pesée", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════════
// 5. HISTORIQUE COMPACT (avec deltas)
// ═══════════════════════════════════════

@Composable
private fun WeightHistoryCard(logs: List<WeightLogEntity>) {
    val sorted = remember(logs) { logs.sortedByDescending { it.date } }
    val display = sorted.take(8)

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Tes pesées", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (sorted.size > display.size) {
                    Text("${sorted.size} au total", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            display.forEachIndexed { i, log ->
                val previous = display.getOrNull(i + 1)?.weightKg
                WeightHistoryRow(log = log, previous = previous)
            }
        }
    }
}

@Composable
private fun WeightHistoryRow(log: WeightLogEntity, previous: Double?) {
    val delta = previous?.let { log.weightKg - it }
    val deltaColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        abs(delta) < 0.05 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        delta < 0 -> NeonGreen
        else -> ErrorRed
    }
    val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.FRENCH)

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Indicateur date avec pastille colorée selon delta
        Box(Modifier.size(8.dp).clip(CircleShape).background(deltaColor))
        Text(
            log.date.format(dateFmt).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            String.format(java.util.Locale.US, "%.1f kg", log.weightKg),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = OrangeVibrant
        )
        if (delta != null) {
            Surface(shape = RoundedCornerShape(6.dp), color = deltaColor.copy(alpha = 0.12f)) {
                Text(
                    (if (delta >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", delta),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = deltaColor
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════

private fun formatKg(kg: Double): String = String.format(java.util.Locale.US, "%.1f kg", kg)
