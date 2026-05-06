package com.shredcoach.app.presentation.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlin.math.max

private val ProteinColor = Color(0xFF3B82F6)
private val CarbColor = Color(0xFFF59E0B)
private val FatColor = Color(0xFFEF4444)
private val ErrorRed = Color(0xFFEF4444)

// ═══════════════════════════════════════
// 1. PERIOD TABS (7j / 30j / 90j)
// ═══════════════════════════════════════

@Composable
fun NutritionPeriodTabs(selected: TimePeriod, onSelect: (TimePeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(TimePeriod.WEEK, TimePeriod.MONTH, TimePeriod.QUARTER).forEach { p ->
            val sel = p == selected
            Surface(
                onClick = { onSelect(p) },
                shape = RoundedCornerShape(10.dp),
                color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f).height(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// 2. INSIGHTS PANEL — coaching auto-généré
// ═══════════════════════════════════════

@Composable
fun InsightsPanelCard(insights: List<String>) {
    if (insights.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Coaching personnalisé", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            insights.forEach { insight ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        insight,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// 3. PERIOD COMPARISON — vs période précédente
// ═══════════════════════════════════════

@Composable
fun PeriodComparisonStrip(state: NutritionStatsData) {
    if (state.prevAvgCalories == 0 && state.prevAvgProteins == 0 && state.prevComplianceDays == 0) return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Évolution vs période précédente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                ComparisonStat(
                    label = "Calories",
                    current = "${state.avgCalories}",
                    delta = state.caloriesDelta.toFloat(),
                    deltaUnit = "kcal",
                    inverseColors = true  // baisse = positif si on est en sèche
                )
                ComparisonStat(
                    label = "Protéines",
                    current = "${state.avgProteins}g",
                    delta = state.proteinsDelta.toFloat(),
                    deltaUnit = "g",
                    inverseColors = false  // hausse = positif
                )
                ComparisonStat(
                    label = "Cible atteinte",
                    current = "${state.complianceDays}/${state.daysInPeriod}",
                    delta = state.complianceDelta.toFloat(),
                    deltaUnit = "j",
                    inverseColors = false
                )
            }
        }
    }
}

@Composable
private fun ComparisonStat(label: String, current: String, delta: Float, deltaUnit: String, inverseColors: Boolean) {
    val color = when {
        kotlin.math.abs(delta) < 0.5f -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        (delta > 0) xor inverseColors -> NeonGreen
        else -> ErrorRed
    }
    val icon = when {
        kotlin.math.abs(delta) < 0.5f -> Icons.AutoMirrored.Filled.TrendingFlat
        delta > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        else -> Icons.Default.TrendingDown
    }
    val sign = if (delta > 0) "+" else ""

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(current, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, null, Modifier.size(12.dp), tint = color)
            Text(
                "$sign${delta.toInt()} $deltaUnit",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

// ═══════════════════════════════════════
// 4. MACRO SPLIT DONUT — % kcal P/G/L
// ═══════════════════════════════════════

@Composable
fun MacroSplitDonutCard(state: NutritionStatsData) {
    if (state.proteinKcalPct + state.carbsKcalPct + state.fatsKcalPct < 0.5f) return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PieChart, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Répartition macros (% kcal)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Donut
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    MacroSplitDonut(
                        proteinPct = state.proteinKcalPct,
                        carbsPct = state.carbsKcalPct,
                        fatsPct = state.fatsKcalPct,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.avgCalories}",
                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.ExtraBold)
                        Text("kcal/jour", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                // Légende verticale
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroLegendRow("Protéines", state.proteinKcalPct, ProteinColor)
                    MacroLegendRow("Glucides", state.carbsKcalPct, CarbColor)
                    MacroLegendRow("Lipides", state.fatsKcalPct, FatColor)
                }
            }

            // Verdict qualitatif
            if (state.macroSplitVerdict.isNotEmpty()) {
                val color = when {
                    state.macroSplitVerdict.contains("optimal", ignoreCase = true) -> NeonGreen
                    state.macroSplitVerdict.contains("équilibre", ignoreCase = true) ||
                        state.macroSplitVerdict.contains("adapté", ignoreCase = true) ||
                        state.macroSplitVerdict.contains("correct", ignoreCase = true) -> NeonGreen
                    state.macroSplitVerdict.contains("Manque", ignoreCase = true) ||
                        state.macroSplitVerdict.contains("Pas assez", ignoreCase = true) -> ErrorRed
                    else -> OrangeVibrant
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = color.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = color)
                        Text(state.macroSplitVerdict,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroSplitDonut(proteinPct: Float, carbsPct: Float, fatsPct: Float, modifier: Modifier) {
    val animatedRange = remember { Animatable(0f) }
    LaunchedEffect(proteinPct, carbsPct, fatsPct) {
        animatedRange.snapTo(0f)
        animatedRange.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.16f
        val padding = stroke / 2f + 2f
        val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
        val topLeft = Offset(padding, padding)
        var startAngle = -90f
        val gap = 1.5f
        val anim = animatedRange.value
        listOf(
            proteinPct to ProteinColor,
            carbsPct to CarbColor,
            fatsPct to FatColor
        ).forEach { (pct, color) ->
            val sweep = pct * 360f * anim
            if (sweep <= 0f) return@forEach
            val drawSweep = (sweep - gap).coerceAtLeast(0.5f)
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = drawSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun MacroLegendRow(label: String, pct: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f), maxLines = 1)
        Text(
            "${(pct * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ═══════════════════════════════════════
// 5. NUTRI-SCORE DISTRIBUTION — A→E
// ═══════════════════════════════════════

@Composable
fun NutriDistributionCard(state: NutritionStatsData) {
    if (state.nutriTotal == 0) return
    val maxCount = max(1, listOf(state.nutriCountA, state.nutriCountB, state.nutriCountC, state.nutriCountD, state.nutriCountE).max())
    val highShare = state.nutriHighQualityShare

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Column(Modifier.weight(1f)) {
                    Text("Qualité de tes repas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val verdict = when {
                        highShare >= 0.7f -> "Excellent profil nutritionnel"
                        highShare >= 0.5f -> "Bon équilibre · marge de progression"
                        highShare >= 0.3f -> "Mitigé · vise plus de A et B"
                        else -> "À améliorer · privilégie A et B"
                    }
                    val verdictColor = when {
                        highShare >= 0.5f -> NeonGreen
                        highShare >= 0.3f -> OrangeVibrant
                        else -> ErrorRed
                    }
                    Text(verdict, style = MaterialTheme.typography.labelSmall, color = verdictColor)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = NeonGreen.copy(alpha = 0.12f)) {
                    Text(
                        "${(highShare * 100).toInt()}% A+B",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
            }

            Row(Modifier.fillMaxWidth().height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                listOf(
                    'A' to state.nutriCountA, 'B' to state.nutriCountB,
                    'C' to state.nutriCountC, 'D' to state.nutriCountD,
                    'E' to state.nutriCountE
                ).forEach { (grade, count) ->
                    NutriBar(grade = grade, count = count, ratio = count.toFloat() / maxCount, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NutriBar(grade: Char, count: Int, ratio: Float, modifier: Modifier) {
    val color = nutriColor(grade)
    val animatedRatio = remember { Animatable(0f) }
    LaunchedEffect(ratio) {
        animatedRatio.snapTo(0f)
        animatedRatio.animateTo(ratio.coerceIn(0f, 1f), tween(700))
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        Box(
            Modifier.weight(1f).fillMaxWidth(0.7f).clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(animatedRatio.value.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Surface(shape = RoundedCornerShape(4.dp), color = color, modifier = Modifier.size(width = 22.dp, height = 22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(grade.toString(), style = nutriBadgeLetterStyle)
            }
        }
    }
}

private val nutriBadgeLetterStyle: TextStyle = TextStyle(
    fontSize = 13.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.ExtraBold,
    color = Color.White,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )
)

private fun nutriColor(grade: Char): Color = when (grade) {
    'A' -> Color(0xFF038141); 'B' -> Color(0xFF85BB2F); 'C' -> Color(0xFFFECB02)
    'D' -> Color(0xFFEE8100); 'E' -> Color(0xFFE63E11); else -> Color.Gray
}

// ═══════════════════════════════════════
// 6. MEAL HOURS TIMELINE — quand l'user mange
// ═══════════════════════════════════════

@Composable
fun MealHoursTimelineCard(buckets: Map<MealHourBucket, Int>) {
    val total = buckets.values.sum()
    if (total == 0) return
    val maxCount = buckets.values.max().coerceAtLeast(1)
    val orderedBuckets = listOf(
        MealHourBucket.MORNING, MealHourBucket.LUNCH,
        MealHourBucket.AFTERNOON, MealHourBucket.DINNER, MealHourBucket.NIGHT
    )

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Quand tu manges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$total repas",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            orderedBuckets.forEach { bucket ->
                val count = buckets[bucket] ?: 0
                MealHourBucketBar(bucket = bucket, count = count, ratio = count.toFloat() / maxCount, total = total)
            }

            // Insight grignotage si NIGHT > 0
            val nightCount = buckets[MealHourBucket.NIGHT] ?: 0
            if (nightCount > 0) {
                val pct = (nightCount.toFloat() / total * 100).toInt()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OrangeVibrant.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "💡 $pct% de tes repas après 23h — module pour mieux récupérer",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = OrangeVibrant
                    )
                }
            }
        }
    }
}

@Composable
private fun MealHourBucketBar(bucket: MealHourBucket, count: Int, ratio: Float, total: Int) {
    val animatedRatio = remember { Animatable(0f) }
    LaunchedEffect(ratio) {
        animatedRatio.snapTo(0f)
        animatedRatio.animateTo(ratio.coerceIn(0f, 1f), tween(700))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(bucket.emoji, fontSize = 16.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bucket.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                val pct = if (total > 0) (count.toFloat() / total * 100).toInt() else 0
                Text(
                    "$count repas · $pct%",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(OrangeVibrant.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier.fillMaxWidth(animatedRatio.value).fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(OrangeVibrant.copy(alpha = 0.7f), OrangeVibrant))
                        )
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// 7. PREMIUM CALORIES CHART — courbe smooth si 30j+
// ═══════════════════════════════════════
//
// Pour 7j on garde les barres (lisibilité), pour 30j+ on bascule sur courbe
// lissée Catmull-Rom→Bézier avec gradient + zone optimale (90-110% target).

@Composable
fun CaloriesPremiumChart(state: NutritionStatsData) {
    if (state.dailyCaloriesSeries.isEmpty()) return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Calories sur ${state.daysInPeriod} jours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (state.daysInPeriod <= 7) {
                CaloriesBarsChart(state)
            } else {
                CaloriesSmoothChart(state)
            }
        }
    }
}

@Composable
private fun CaloriesBarsChart(state: NutritionStatsData) {
    val data = state.weeklyCalories
    val target = state.targetCalories
    val maxCal = (data.maxOfOrNull { it.second } ?: target).coerceAtLeast(target).toFloat()

    Row(
        Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { idx, (day, cal) ->
            val fraction = (cal / maxCal).coerceIn(0f, 1f)
            val barColor = when {
                cal == 0 -> MaterialTheme.colorScheme.surfaceVariant
                cal > target * 1.1 -> ErrorRed
                cal >= target * 0.9 -> NeonGreen
                else -> OrangeVibrant
            }
            val animatedHeight = remember(day, fraction) { Animatable(0f) }
            LaunchedEffect(fraction) {
                kotlinx.coroutines.delay((idx * 50).toLong())
                animatedHeight.animateTo(
                    targetValue = fraction.coerceAtLeast(0.05f),
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cal > 0) Text(
                    "$cal",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
                Box(
                    Modifier.width(28.dp).fillMaxHeight(animatedHeight.value)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(barColor)
                )
                Text(day, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
    // Ligne objectif
    Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        HorizontalDivider(color = OrangeVibrant.copy(alpha = 0.3f), thickness = 1.dp)
        Text("Objectif : $target kcal", modifier = Modifier.align(Alignment.CenterEnd),
            style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = OrangeVibrant.copy(alpha = 0.6f))
    }
}

@Composable
private fun CaloriesSmoothChart(state: NutritionStatsData) {
    val series = state.dailyCaloriesSeries.filter { it.second > 0 }
    if (series.size < 2) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("Pas assez de données pour la courbe — ajoute plus de repas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        return
    }
    val target = state.targetCalories.toFloat()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontFeatureSettings = "tnum"
    )
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val targetColor = NeonGreen
    val zoneColor = NeonGreen.copy(alpha = 0.10f)
    val lineColor = OrangeVibrant
    val animatedAlpha = remember { Animatable(0f) }
    LaunchedEffect(series) {
        animatedAlpha.snapTo(0f)
        animatedAlpha.animateTo(1f, tween(700))
    }

    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val w = size.width; val h = size.height
        val padL = 44f; val padR = 8f; val padT = 12f; val padB = 24f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        val values = series.map { it.second.toFloat() }
        val rawMin = (values.min()).coerceAtMost(target * 0.85f)
        val rawMax = (values.max()).coerceAtLeast(target * 1.15f)
        val pad = max(50f, (rawMax - rawMin) * 0.08f)
        val yMin = rawMin - pad
        val yMax = rawMax + pad
        val yRange = (yMax - yMin).coerceAtLeast(50f)

        // Grid Y + labels
        val grid = 4
        for (i in 0..grid) {
            val v = yMin + yRange * i / grid
            val y = padT + chartH * (1f - i.toFloat() / grid)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
            val lbl = "${v.toInt()}"
            val tl = textMeasurer.measure(lbl, labelStyle)
            drawText(tl, topLeft = Offset(padL - tl.size.width - 6f, y - tl.size.height / 2f))
        }

        // Zone optimale (90-110% target)
        val yLowOpt = padT + chartH * (1f - ((target * 0.9f - yMin) / yRange))
        val yHighOpt = padT + chartH * (1f - ((target * 1.1f - yMin) / yRange))
        drawRect(
            color = zoneColor,
            topLeft = Offset(padL, yHighOpt.coerceAtLeast(padT)),
            size = Size(chartW, (yLowOpt - yHighOpt).coerceAtLeast(0f))
        )

        // Ligne d'objectif
        val yTarget = padT + chartH * (1f - ((target - yMin) / yRange))
        if (yTarget in padT..(padT + chartH)) {
            drawLine(
                color = targetColor.copy(alpha = 0.7f),
                start = Offset(padL, yTarget),
                end = Offset(w - padR, yTarget),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        }

        // Points + courbe lissée
        val n = series.size
        val stepX = chartW / (n - 1).coerceAtLeast(1)
        val pts = series.mapIndexed { i, (_, cal) ->
            Offset(padL + i * stepX, padT + chartH * (1f - ((cal - yMin) / yRange)))
        }
        val curve = buildSmoothPath(pts)

        // Gradient sous la courbe
        val fillPath = Path().apply {
            addPath(curve)
            lineTo(pts.last().x, padT + chartH)
            lineTo(pts.first().x, padT + chartH)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.32f * animatedAlpha.value), lineColor.copy(alpha = 0f)),
                startY = padT, endY = padT + chartH
            )
        )
        drawPath(curve, lineColor.copy(alpha = animatedAlpha.value),
            style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Marqueur dernier point
        val last = pts.last()
        drawCircle(lineColor.copy(alpha = 0.25f * animatedAlpha.value), 12f, last)
        drawCircle(lineColor.copy(alpha = animatedAlpha.value), 5f, last)
        drawCircle(Color.White, 2.5f, last)

        // Label "Objectif" à droite
        val targetLabel = "Cible ${target.toInt()}"
        val tlT = textMeasurer.measure(targetLabel, labelStyle.copy(color = targetColor))
        drawText(tlT, topLeft = Offset(w - padR - tlT.size.width, yTarget - tlT.size.height - 2f))
    }
}

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
