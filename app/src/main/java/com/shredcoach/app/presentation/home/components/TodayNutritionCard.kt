package com.shredcoach.app.presentation.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.local.entity.NutritionType
import com.shredcoach.app.presentation.home.NextScheduleItem
import com.shredcoach.app.presentation.home.TodayNutrition
import java.time.format.DateTimeFormatter

/**
 * Today Nutrition Card — hero secondaire de la home.
 *
 * **Contenu** (FAANG / "Whoop-style") :
 *  - Ring calories (hero, gauche) — % consommé + kcal restantes
 *  - Bar protéines (clé pour la sèche)
 *  - Chips macros (P/G/L) — info, pas hero
 *  - Prochain item planning (repas/shaker)
 *  - 2 actions rapides : scan photo + ajout manuel
 *
 * **Pourquoi pas d'hydratation** : on n'a pas (encore) de tracker d'eau dédié.
 * Les WaterSchedules existent mais comptent les *rappels*, pas les *verres bus*.
 * On préfère skipper plutôt que d'afficher un faux indicateur. À ajouter en H2+.
 *
 * **Pourquoi protéines plutôt que glucides en hero secondaire** : pour une
 * sèche (FitnessGoal=SHRED), atteindre le quota protéique préserve la masse
 * musculaire. C'est le single macro dont l'user doit obsesser au quotidien.
 */
@Composable
fun TodayNutritionCard(
    nutrition: TodayNutrition,
    onScanMeal: () -> Unit,
    onAddManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11y = remember(nutrition) { buildA11yLabel(nutrition) }

    // Pas de mergeDescendants : la card contient des boutons (Scanner/Manuel)
    // qui doivent rester focusables individuellement par TalkBack. Le résumé
    // a11y consolidé est porté par le titre "Aujourd'hui" via clearAndSetSemantics
    // (lu en premier au swipe, puis les boutons gardent leur label propre).
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ─── Header ───
            Text(
                text = "Aujourd'hui",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                // a11y consolidé porté ici (au lieu d'un mergeDescendants
                // global qui casserait la focusabilité des boutons).
                modifier = Modifier.clearAndSetSemantics { contentDescription = a11y },
            )

            // ─── Ring calories + résumé ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CaloriesRing(
                    progress = nutrition.caloriesProgress,
                    isOver = nutrition.isCaloriesOver,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // tnum sur les valeurs numériques + maxLines=1 : la card
                    // reste à hauteur stable même quand les calories passent
                    // de 150 à 2450 ou que le texte "Reste/Dépassement" varie.
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = nutrition.caloriesConsumed.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = " / ${nutrition.caloriesTarget} kcal",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = if (nutrition.isCaloriesOver) {
                            "Dépassement de ${nutrition.caloriesConsumed - nutrition.caloriesTarget} kcal"
                        } else {
                            "Reste ${nutrition.caloriesRemaining} kcal"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        color = if (nutrition.isCaloriesOver) Color(0xFFEF4444) else NutritionBlue,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            // ─── 3 mini-pies macros (Protéines / Glucides / Lipides) ───
            // weight(1f) sur chaque colonne → 3 macros de largeur égale,
            // indépendantes des grammes affichés (responsive, pas de shimmer).
            // Mots entiers (pas d'initiales P/G/L) pour clarté UX premium.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MacroPie(
                    label = "Protéines",
                    consumed = nutrition.proteinsConsumedGrams,
                    target = nutrition.proteinsTargetGrams,
                    progress = nutrition.proteinsProgress,
                    color = ProteinGreen,
                    modifier = Modifier.weight(1f),
                )
                MacroPie(
                    label = "Glucides",
                    consumed = nutrition.carbsConsumedGrams,
                    target = nutrition.carbsTargetGrams,
                    progress = nutrition.carbsProgress,
                    color = CarbsAmber,
                    modifier = Modifier.weight(1f),
                )
                MacroPie(
                    label = "Lipides",
                    consumed = nutrition.fatsConsumedGrams,
                    target = nutrition.fatsTargetGrams,
                    progress = nutrition.fatsProgress,
                    color = FatsPurple,
                    modifier = Modifier.weight(1f),
                )
            }

            // ─── Prochain repas ───
            nutrition.next?.let { NextMealHint(it) }

            // ─── Actions ───
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onScanMeal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scanner", style = MaterialTheme.typography.labelLarge)
                }
                FilledTonalButton(
                    onClick = onAddManual,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Manuel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Ring calories avec gradient sweep + animation count-up.
 * Bleu si en zone, rouge si dépassé.
 */
@Composable
private fun CaloriesRing(
    progress: Float,
    isOver: Boolean,
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "caloriesRing",
    )
    val ringColor = if (isOver) Color(0xFFEF4444) else NutritionBlue
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 8.dp.toPx()
            val sweep = 360f * animated
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress (gradient)
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(ringColor.copy(alpha = 0.7f), ringColor),
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "${(animated * 100).toInt()}%",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = ringColor,
        )
    }
}

/**
 * Mini-pie macro : anneau circulaire (% consommé / cible) + nom complet
 * en dessous + valeur absolue. Format colonne, weight(1f) côté caller pour
 * largeur responsive uniforme.
 *
 * Pourquoi un anneau plutôt qu'une barre : le Today Nutrition utilise déjà
 * un ring pour les calories en hero. Garder le même langage visuel pour
 * les macros donne une cohérence de "constellation de progrès" — chaque
 * macro a son propre indicateur circulaire, lisible d'un coup d'œil.
 */
@Composable
private fun MacroPie(
    label: String,
    consumed: Int,
    target: Int,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "macroPie-$label",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Anneau circulaire avec valeur en grammes au centre
        Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(54.dp)) {
                val stroke = 5.dp.toPx()
                drawArc(
                    color = color.copy(alpha = 0.14f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(color.copy(alpha = 0.7f), color),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            // tnum sur la valeur centrale → la pie reste stable visuellement
            // entre 5g et 145g (chiffres de largeur uniforme).
            Text(
                text = "${consumed}g",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = color,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Nom complet (Protéines / Glucides / Lipides) — pas d'initiale.
        // maxLines=1 + softWrap pour éviter wrap "Pro-\ntéines" sur petit écran.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            softWrap = false,
            fontSize = 11.5.sp,
        )
        // Sub-line cible — discrète, ne mange pas la hiérarchie visuelle.
        Text(
            text = "/ ${target}g",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NextMealHint(item: NextScheduleItem) {
    val emoji = when (item.type) {
        NutritionType.BREAKFAST -> "🌅"
        NutritionType.LUNCH -> "☀️"
        NutritionType.DINNER -> "🌙"
        NutritionType.SNACK -> "🍎"
        NutritionType.SHAKE -> "🥤"
        NutritionType.WATER -> "💧"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NutritionBlue.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = null,
            tint = NutritionBlue,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Prochain : $emoji ${item.name}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.time.format(TIME_FORMAT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = NutritionBlue,
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

private val NutritionBlue = Color(0xFF3B82F6)
private val ProteinGreen = Color(0xFF10B981)
private val CarbsAmber = Color(0xFFF59E0B)
private val FatsPurple = Color(0xFF8B5CF6)

private fun buildA11yLabel(n: TodayNutrition): String {
    val calStatus = if (n.isCaloriesOver) {
        "${n.caloriesConsumed - n.caloriesTarget} kcal au-dessus de la cible"
    } else {
        "${n.caloriesRemaining} kcal restantes"
    }
    val nextStr = n.next?.let { ", prochain ${it.name} à ${it.time.format(TIME_FORMAT)}" } ?: ""
    return "Aujourd'hui ${n.caloriesConsumed} kcal sur ${n.caloriesTarget}, " +
        "$calStatus, ${n.proteinsConsumedGrams} grammes de protéines sur ${n.proteinsTargetGrams}$nextStr"
}
