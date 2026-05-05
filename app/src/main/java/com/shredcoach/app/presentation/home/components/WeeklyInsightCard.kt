package com.shredcoach.app.presentation.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.training.ProgressStatus
import com.shredcoach.app.presentation.home.InsightTone
import com.shredcoach.app.presentation.home.WeeklyInsight

/**
 * Carte "Insight de la semaine" — full-width hero entre nutrition et navigation
 * secondaire. Met en avant UN exercice avec un highlight contextuel :
 *  - PR récent : célébration (or)
 *  - Progression : "+X kg/sem" (vert)
 *  - Plateau : "N semaines sans progrès" (rouge atténué)
 *
 * Pourquoi pas le même composant que [ExerciseProgressionCard] dans Stats :
 * celui-là est calibré pour un carrousel (240×280×160dp). Ici on veut full-width
 * + sparkline plus grande pour donner de la respiration. La data source est la
 * même ([ExerciseProgression]), seul le rendu change.
 */
@Composable
fun WeeklyInsightCard(
    insight: WeeklyInsight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = remember(insight.tone) { tonePalette(insight.tone) }
    val a11y = remember(insight) { buildA11y(insight) }
    val toneTitle = remember(insight) { toneTitle(insight) }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ─── Header : titre tonifié + icône ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = palette.icon,
                        contentDescription = null,
                        tint = palette.color,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Insight de la semaine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = toneTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.color,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // ─── 1RM hero + delta best ───
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatKg(insight.progression.estimatedOneRmKg),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 36.sp,
                )
                Text(
                    text = " kg 1RM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "best",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "${formatKg(insight.progression.bestOneRmKg)} kg",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // ─── Sparkline ───
            if (insight.progression.sparkline.size >= 2) {
                LargeSparkline(
                    points = insight.progression.sparkline,
                    color = palette.color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }

            // ─── Sub-line contextuelle ───
            Text(
                text = subLabel(insight),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sparkline plus généreuse que celle de la card stats — full-width 56dp. */
@Composable
private fun LargeSparkline(points: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(modifier)
        return
    }
    val min = points.min()
    val max = points.max()
    val range = (max - min).coerceAtLeast(0.5)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = stepX * index
            val normalized = ((value - min) / range).toFloat()
            val y = h - (normalized * h * 0.84f + h * 0.08f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Gradient sous la courbe
        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.32f), Color.Transparent),
            ),
        )

        // Trace
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        // Dernier point en surbrillance
        val lastX = stepX * (points.size - 1)
        val lastNorm = ((points.last() - min) / range).toFloat()
        val lastY = h - (lastNorm * h * 0.84f + h * 0.08f)
        drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
    }
}

private data class TonePalette(val color: Color, val icon: ImageVector)

private fun tonePalette(tone: InsightTone): TonePalette = when (tone) {
    InsightTone.PR -> TonePalette(Color(0xFFFFB300), Icons.Filled.MilitaryTech)
    InsightTone.PROGRESS -> TonePalette(Color(0xFF10B981), Icons.Filled.NorthEast)
    InsightTone.PLATEAU -> TonePalette(Color(0xFFEF4444), Icons.Filled.SouthEast)
}

private fun toneTitle(insight: WeeklyInsight): String = when (insight.tone) {
    InsightTone.PR -> "🏆 PR sur ${insight.exerciseName}"
    InsightTone.PROGRESS -> "📈 Progression sur ${insight.exerciseName}"
    InsightTone.PLATEAU -> "⚠️ Plateau sur ${insight.exerciseName}"
}

private fun subLabel(insight: WeeklyInsight): String = when (insight.tone) {
    InsightTone.PR -> {
        val prev = insight.progression.previousBestKg
        if (prev != null) "Tu viens de battre ton record (${formatKg(prev)} kg). Continue !"
        else "Tu viens d'établir ton record perso. Continue sur cette lancée !"
    }
    InsightTone.PROGRESS -> {
        val slope = insight.progression.weeklySlopeKg
        "+${"%.1f".format(slope)} kg/semaine sur les ${insight.progression.sessionsCount} dernières séances"
    }
    InsightTone.PLATEAU -> {
        val weeks = (insight.progression.status as? ProgressStatus.Plateau)?.weeksFlat ?: 3
        "$weeks semaines sans nouveau best. Essaie de varier reps, repos ou tempo."
    }
}

private fun formatKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun buildA11y(insight: WeeklyInsight): String {
    val title = toneTitle(insight).replace(Regex("[^\\p{L}\\p{N} ]"), "").trim()
    return "$title, " +
        "1RM estimé ${formatKg(insight.progression.estimatedOneRmKg)} kilogrammes, " +
        "record ${formatKg(insight.progression.bestOneRmKg)} kilogrammes. " +
        subLabel(insight)
}
