package com.shredcoach.app.presentation.stats.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.training.ExerciseProgression
import com.shredcoach.app.domain.training.ProgressStatus

/**
 * Carte synthèse de progression pour un exercice.
 *
 * **Contenu** :
 *  - Nom exercice + nb sessions analysées
 *  - 1RM estimé (gros chiffre, hero)
 *  - Badge état (En progression / Stable / Plateau N semaines / 🏆 PR récent)
 *  - Mini-courbe sparkline des N dernières séances (Canvas natif, pas de lib)
 *
 * **Pourquoi pas un BarChart classique** : la sparkline est le format adapté
 * pour montrer la tendance sans saturer visuellement l'écran. Tufte's principle :
 * "the world is more interesting than any theory of it" — moins d'encre, plus
 * d'info.
 *
 * **Performance** : Canvas natif rend ~12 points en ~0.3ms. Aucune dépendance
 * supplémentaire ajoutée pour ça (cohérent avec l'approche déjà adoptée par
 * DashboardScreen).
 */
@Composable
fun ExerciseProgressionCard(
    exerciseName: String,
    progression: ExerciseProgression,
    modifier: Modifier = Modifier,
) {
    val accent = statusColor(progression.status, progression.hasFreshPr)
    val statusLabelA11y = a11yStatusLabel(progression.status, progression.hasFreshPr)
    val cardA11y = "$exerciseName, 1RM ${formatKg(progression.estimatedOneRmKg)} kg, " +
        "$statusLabelA11y, ${progression.sessionsCount} séances analysées, " +
        "record perso ${formatKg(progression.bestOneRmKg)} kg"

    Card(
        modifier = modifier
            .widthIn(min = 240.dp, max = 280.dp)
            .height(160.dp)
            // Merge le contenu en une seule annonce TalkBack — les sous-textes
            // (badge, sparkline) sont redondants avec le label global.
            .semantics(mergeDescendants = true) { contentDescription = cardA11y },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
        ) {
            // Header — nom + badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exerciseName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = progression.status, hasFreshPr = progression.hasFreshPr)
            }

            Spacer(Modifier.height(2.dp))

            // 1RM hero
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatKg(progression.estimatedOneRmKg),
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = accent,
                )
                Text(
                    text = " kg 1RM",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${progression.sessionsCount} séances",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Sparkline + best
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sparkline(
                    points = progression.sparkline,
                    accentColor = accent,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "best",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${formatKg(progression.bestOneRmKg)} kg",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ProgressStatus, hasFreshPr: Boolean) {
    val (label, color, icon) = when {
        hasFreshPr -> Triple("🏆 PR", Color(0xFFFFB300), Icons.Filled.MilitaryTech)
        status is ProgressStatus.Progressing -> Triple(
            // %.1f : la pente est l'info clé du badge — tronquer à 0.5kg près
            // (comme formatKg) ferait afficher "+0.5kg/sem" pour une pente
            // réelle de 0.7, juste au-dessus du seuil 0.6 → confusion utilisateur.
            "+%.1fkg/sem".format(status.weeklyDeltaKg),
            Color(0xFF00C853),
            Icons.Filled.NorthEast,
        )
        status is ProgressStatus.Stable -> Triple("Stable", Color(0xFF78909C), Icons.Filled.Remove)
        status is ProgressStatus.Plateau -> Triple(
            "Plateau ${status.weeksFlat}sem",
            Color(0xFFE53935),
            Icons.Filled.SouthEast,
        )
        else -> Triple("Stable", Color(0xFF78909C), Icons.Filled.Remove)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/**
 * Sparkline simple : courbe lissée + dernier point mis en évidence.
 * Si moins de 2 points → ne dessine rien (évite Path vide).
 */
@Composable
private fun Sparkline(
    points: List<Double>,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(modifier)
        return
    }

    val min = points.min()
    val max = points.max()
    val range = (max - min).coerceAtLeast(0.5) // évite division zéro si plat

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (points.size - 1)

        val path = Path()
        points.forEachIndexed { index, value ->
            val x = stepX * index
            // Y est inversé (origine top-left). Padding 8% top/bot pour ne pas
            // que le trait touche les bords.
            val normalized = ((value - min) / range).toFloat()
            val y = h - (normalized * h * 0.84f + h * 0.08f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Glow doux dessous (gradient vers transparent)
        val fillPath = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
            ),
        )

        // Trace principale
        drawPath(
            path = path,
            color = accentColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Dernier point en relief
        val lastX = stepX * (points.size - 1)
        val lastNorm = ((points.last() - min) / range).toFloat()
        val lastY = h - (lastNorm * h * 0.84f + h * 0.08f)
        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = Offset(lastX, lastY),
        )
        drawCircle(
            color = Color.White,
            radius = 1.5.dp.toPx(),
            center = Offset(lastX, lastY),
        )
    }
}

private fun statusColor(status: ProgressStatus, hasFreshPr: Boolean): Color = when {
    hasFreshPr -> Color(0xFFFFB300)
    status is ProgressStatus.Progressing -> Color(0xFF00C853)
    status is ProgressStatus.Plateau -> Color(0xFFE53935)
    else -> Color(0xFF455A64)
}

/**
 * Formate une valeur DÉJÀ arrondie au demi-kilo par [OneRepMaxCalculator.roundToHalfKg]
 * en amont (cf. [PlateauDetector.analyze]). Affichage : "100" pour les entiers,
 * "102.5" sinon — pas d'arrondi supplémentaire ici (ce serait un double traitement).
 */
private fun formatKg(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

/**
 * Phrase TalkBack-friendly pour le statut. Évite les daltoniens / utilisateurs
 * non-voyants à dépendre du seul code couleur du badge.
 */
private fun a11yStatusLabel(status: ProgressStatus, hasFreshPr: Boolean): String = when {
    hasFreshPr -> "nouveau record personnel"
    status is ProgressStatus.Progressing ->
        "en progression de ${"%.1f".format(status.weeklyDeltaKg)} kg par semaine"
    status is ProgressStatus.Plateau ->
        "plateau depuis ${status.weeksFlat} semaines"
    else -> "stable"
}

/**
 * Données minimales acceptées par la card — utile pour les previews sans avoir
 * à instancier un domain ExerciseProgression complet.
 */
data class ExerciseProgressionCardData(
    val exerciseName: String,
    val progression: ExerciseProgression,
)
