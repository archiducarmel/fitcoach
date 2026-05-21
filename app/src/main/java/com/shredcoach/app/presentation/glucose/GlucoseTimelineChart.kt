package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/**
 * Composant graphique unifié pour la courbe glycémique CGM 24h.
 *
 * **Utilisé par 3 surfaces** :
 *  - `MealGlucoseTimelineCard` (page Nutrition) — courbe du jour avec repas
 *  - `GlucoseAnalysisScreen` (analyse experte) — courbe annotée avec insights
 *  - `GlucoseDashboard` (Stats glycémie) — hero curve today
 *
 * **Layers de dessin** (de l'arrière vers l'avant) :
 *  1. Background — fond surface
 *  2. Target zone — bande verte claire 70-140 mg/dL
 *  3. Spike threshold — ligne pointillée rouge 180 mg/dL
 *  4. Glucose curve — gradient fill emerald + stroke
 *  5. Meal markers — dots colorés positionnés SUR la courbe à l'heure du repas,
 *     couleur dérivée du pic postprandial dans les 90 min suivantes
 *  6. Insight markers — dots plus petits sur la courbe (analyse experte)
 *  7. X-axis labels — 00 / 04 / 08 / 12 / 16 / 20 / 24 h
 *  8. Y-axis labels — 70 / 140 / 180 mg/dL en marge gauche
 *
 * **Pourquoi un seul composant** : avant ce refactor, 2 implémentations
 * divergeaient (épaisseur ligne, position markers repas, couleur target zone).
 * Une seule source de vérité = cohérence visuelle premium garantie.
 */

/** Point de courbe glycémique : timestamp + valeur. */
data class ChartGlucosePoint(val time: LocalTime, val mgdl: Double)

/**
 * Marqueur d'un repas posé sur la courbe. La couleur du dot est dérivée du
 * `responsePeak` (pic glycémique 30-90 min après le repas) :
 *  - < 140 mg/dL → vert (réponse contenue, excellente)
 *  - 140-180 mg/dL → amber (modérée, attention)
 *  - ≥ 180 mg/dL → rouge (pic notable)
 *  - null (pas calculable) → emerald translucide (point neutre)
 */
data class ChartMealMarker(
    val time: LocalTime,
    val responsePeak: Double?,
    val label: String? = null,
)

/**
 * Marqueur d'un insight Dr. Glykos (analyse experte). Plus petit qu'un repas
 * marker pour différencier visuellement les deux couches sur la courbe.
 */
data class ChartInsightMarker(
    val time: LocalTime,
    val color: Color,
)

private val Y_AXIS_WIDTH = 28.dp

@Composable
fun GlucoseTimelineChart(
    curve: List<ChartGlucosePoint>,
    modifier: Modifier = Modifier,
    meals: List<ChartMealMarker> = emptyList(),
    insights: List<ChartInsightMarker> = emptyList(),
    height: Dp = 180.dp,
    showXAxisLabels: Boolean = true,
    showYAxisLabels: Boolean = true,
    onDarkBackground: Boolean = false,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            if (showYAxisLabels) {
                YAxisLabels(onDark = onDarkBackground, height = height)
                Spacer(Modifier.width(6.dp))
            }
            Box(
                Modifier.weight(1f).height(height),
            ) {
                CurveCanvas(
                    curve = curve,
                    meals = meals,
                    insights = insights,
                )
            }
        }
        if (showXAxisLabels) {
            Spacer(Modifier.height(6.dp))
            XAxisLabels(
                onDark = onDarkBackground,
                leftPadding = if (showYAxisLabels) Y_AXIS_WIDTH + 6.dp else 0.dp,
            )
        }
    }
}

@Composable
private fun YAxisLabels(onDark: Boolean, height: Dp) {
    val color = if (onDark) Color.White.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Column(
        Modifier.width(Y_AXIS_WIDTH).height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        Text("180", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, color = color)
        Text("140", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, color = color)
        Text("70", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun XAxisLabels(onDark: Boolean, leftPadding: Dp) {
    val color = if (onDark) Color.White.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val hours = listOf("00", "04", "08", "12", "16", "20", "24")
    Row(
        Modifier.fillMaxWidth().padding(start = leftPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (h in hours) {
            Text(
                "${h}h",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun CurveCanvas(
    curve: List<ChartGlucosePoint>,
    meals: List<ChartMealMarker>,
    insights: List<ChartInsightMarker>,
) {
    val density = LocalDensity.current
    val minMgdl = 50.0
    val maxMgdl = if (curve.isEmpty()) 200.0
        else (curve.maxOf { it.mgdl }.coerceAtLeast(200.0)).coerceAtMost(300.0)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val yRange = (maxMgdl - minMgdl).coerceAtLeast(1.0).toFloat()

        fun y(mgdl: Double): Float = h - ((mgdl - minMgdl).toFloat() / yRange * h)
        fun x(time: LocalTime): Float = (time.toSecondOfDay().toFloat() / 86400f) * w

        // Interpolation linéaire pour trouver le mgdl exact à un timestamp arbitraire
        // (utilisé pour positionner les meal/insight markers SUR la courbe).
        fun glucoseAt(time: LocalTime): Double? {
            if (curve.isEmpty()) return null
            val before = curve.lastOrNull { it.time <= time }
            val after = curve.firstOrNull { it.time >= time }
            if (before == null) return after?.mgdl
            if (after == null) return before.mgdl
            if (before.time == after.time) return before.mgdl
            val tDelta = (after.time.toSecondOfDay() - before.time.toSecondOfDay()).toDouble()
            val tFrac = (time.toSecondOfDay() - before.time.toSecondOfDay()).toDouble() / tDelta
            return before.mgdl + (after.mgdl - before.mgdl) * tFrac
        }

        // Layer 1 : zone cible 70-140 mg/dL
        drawRect(
            color = GlucoseColors.InRange.copy(alpha = 0.10f),
            topLeft = Offset(0f, y(140.0)),
            size = Size(w, y(70.0) - y(140.0)),
        )

        // Layer 2 : seuil spike 180 mg/dL (pointillé rouge)
        val spikeY = y(180.0)
        drawLine(
            color = GlucoseColors.Critical.copy(alpha = 0.32f),
            start = Offset(0f, spikeY), end = Offset(w, spikeY),
            strokeWidth = with(density) { 1.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // Layer 3 : gridlines verticales discrètes aux heures clés
        val gridColor = GlucoseColors.Emerald600.copy(alpha = 0.08f)
        for (hour in listOf(4, 8, 12, 16, 20)) {
            val gx = (hour / 24f) * w
            drawLine(
                color = gridColor,
                start = Offset(gx, 0f), end = Offset(gx, h),
                strokeWidth = with(density) { 0.5.dp.toPx() },
            )
        }

        // Layer 4 : courbe + gradient fill (effet Apple Stocks)
        if (curve.size >= 2) {
            val stroke = Path().apply {
                moveTo(x(curve.first().time), y(curve.first().mgdl))
                for (i in 1 until curve.size) {
                    lineTo(x(curve[i].time), y(curve[i].mgdl))
                }
            }
            val fill = Path().apply {
                addPath(stroke)
                lineTo(x(curve.last().time), h)
                lineTo(x(curve.first().time), h)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlucoseColors.Emerald600.copy(alpha = 0.32f),
                        GlucoseColors.Emerald600.copy(alpha = 0.02f),
                    ),
                ),
            )
            drawPath(
                path = stroke,
                color = GlucoseColors.Emerald600,
                style = Stroke(width = with(density) { 2.5.dp.toPx() }),
            )
        }

        // Layer 5 : meal markers SUR la courbe (halo blanc + ring couleur + dot center blanc)
        for (meal in meals) {
            val mealMgdl = glucoseAt(meal.time) ?: continue
            val color = when {
                meal.responsePeak == null -> GlucoseColors.Emerald600
                meal.responsePeak >= 180.0 -> GlucoseColors.Critical
                meal.responsePeak >= 140.0 -> GlucoseColors.Warning
                else -> GlucoseColors.InRange
            }
            val cx = x(meal.time)
            val cy = y(mealMgdl)
            drawCircle(
                color = Color.White,
                radius = with(density) { 9.dp.toPx() },
                center = Offset(cx, cy),
            )
            drawCircle(
                color = color,
                radius = with(density) { 7.dp.toPx() },
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color.White,
                radius = with(density) { 3.dp.toPx() },
                center = Offset(cx, cy),
            )
        }

        // Layer 6 : insight markers (plus petits que repas pour différencier)
        for (insight in insights) {
            val mgdl = glucoseAt(insight.time) ?: continue
            val cx = x(insight.time)
            val cy = y(mgdl)
            drawCircle(
                color = Color.White,
                radius = with(density) { 6.dp.toPx() },
                center = Offset(cx, cy),
            )
            drawCircle(
                color = insight.color,
                radius = with(density) { 4.5.dp.toPx() },
                center = Offset(cx, cy),
            )
        }
    }
}

/** Légende sous le chart. Items affichés selon les marker types en présence. */
@Composable
fun GlucoseChartLegend(
    showMealMarkers: Boolean = true,
    showInsightMarkers: Boolean = false,
    onDarkBackground: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val labelColor = if (onDarkBackground) Color.White.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMealMarkers) {
            LegendDot(color = GlucoseColors.InRange, label = "Repas <140", textColor = labelColor)
            LegendDot(color = GlucoseColors.Warning, label = "140-180", textColor = labelColor)
            LegendDot(color = GlucoseColors.Critical, label = "≥180", textColor = labelColor)
        } else if (showInsightMarkers) {
            LegendDot(color = GlucoseColors.InRange, label = "Favorable", textColor = labelColor)
            LegendDot(color = GlucoseColors.Warning, label = "Neutre", textColor = labelColor)
            LegendDot(color = GlucoseColors.Critical, label = "À surveiller", textColor = labelColor)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}
