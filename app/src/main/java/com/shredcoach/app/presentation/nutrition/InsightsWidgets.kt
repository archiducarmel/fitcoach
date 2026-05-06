package com.shredcoach.app.presentation.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.nutrition.CategoryShare
import com.shredcoach.app.domain.nutrition.IngredientCategory
import com.shredcoach.app.domain.nutrition.IngredientStat
import com.shredcoach.app.domain.nutrition.NutriScoreDistribution
import com.shredcoach.app.domain.nutrition.NutritionInsights
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// ═══════════════════════════════════════
// INSIGHTS SECTION — orchestre les 4 widgets
// ═══════════════════════════════════════

/**
 * Section "Insights nutrition" affichée en bas de l'écran Nutrition.
 *
 * Remplace l'ancienne TopFoodsCard, qui agrégeait par foodId — incompatible
 * avec les plats scannés (chaque scan = nouveau FoodEntity, count toujours 1).
 *
 * 4 widgets premium :
 *  1. WordCloud des ingrédients consommés (taille = poids cumulé)
 *  2. Donut par catégorie diététique
 *  3. Top 5 ingrédients par poids cumulé
 *  4. Distribution Nutri-Score sur la période
 */
@Composable
fun InsightsSection(insights: NutritionInsights) {
    if (insights.isEmpty) return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InsightsHeader(insights)
        IngredientWordCloudCard(insights.topIngredients)
        CategoryDonutCard(insights.categoryShares, insights.totalGrams)
        TopIngredientsCard(insights.topIngredients)
        if (insights.nutriScoreDistribution.total > 0) {
            NutriScoreDistributionCard(insights.nutriScoreDistribution)
        }
    }
}

@Composable
private fun InsightsHeader(insights: NutritionInsights) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.size(22.dp), tint = OrangeVibrant)
        Column(Modifier.weight(1f)) {
            Text("Insights nutrition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${insights.totalScans} repas analysés · ${insights.totalUniqueIngredients} aliments distincts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "${insights.periodDays} jours",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// ═══════════════════════════════════════
// 1. WORDCLOUD
// ═══════════════════════════════════════
//
// Algorithme de placement : spirale d'Archimède (r = a·θ).
//
// Pourquoi cette approche : les wordclouds rectangulaires sont basiques ;
// la spirale produit un placement compact et organique avec le mot le plus
// "lourd" au centre — c'est ce qu'on attend visuellement.
//
// Détection de collision : axis-aligned bounding boxes (AABB) inflées d'un
// petit padding pour éviter les chevauchements visuels.
//
// fontSize : interpolation puissance 0.55 entre min/max poids — la racine
// compresse l'écart sans aplatir, ce qui évite que le 1er mot soit énorme
// et écrase tous les autres ; en même temps préserve la hiérarchie.

@Composable
private fun IngredientWordCloudCard(ingredients: List<IngredientStat>) {
    if (ingredients.size < 2) return  // pas pertinent avec 0-1 ingrédient

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Restaurant, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Ce que tu manges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "Taille = quantité totale, couleur = catégorie",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            // Hauteur fixe = ratio agréable, évite les jumps si nb de mots varie.
            WordCloudCanvas(
                ingredients = ingredients.take(25),
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
            )
            // Légende compacte des catégories réellement présentes
            val activeCategories = ingredients.take(25).map { it.category }.toSet()
            CategoryLegend(activeCategories)
        }
    }
}

@Composable
private fun WordCloudCanvas(ingredients: List<IngredientStat>, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val maxG = ingredients.firstOrNull()?.totalGrams ?: return
    val minG = ingredients.last().totalGrams

    val animatedAlpha by animateFloatAsState(
        targetValue = 1f, animationSpec = tween(700), label = "wordcloud-fade"
    )

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Place les mots du plus gros au plus petit (déjà trié par totalGrams desc).
        val placed = mutableListOf<Rect>()

        ingredients.forEachIndexed { idx, ing ->
            val range = (maxG - minG).coerceAtLeast(1)
            val ratioRaw = (ing.totalGrams - minG).toFloat() / range
            // Courbe puissance pour booster la lisibilité des "gros"
            val ratio = ratioRaw.pow(0.55f)
            val fontSize = lerpSp(12.sp, 30.sp, ratio)
            val fontWeight = when {
                idx < 3 -> FontWeight.ExtraBold
                idx < 8 -> FontWeight.Bold
                else -> FontWeight.SemiBold
            }
            val style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = ing.category.color.copy(alpha = animatedAlpha),
                textAlign = TextAlign.Center
            )

            val measured = textMeasurer.measure(ing.displayName, style)
            val tw = measured.size.width.toFloat()
            val th = measured.size.height.toFloat()

            val placement = findSpiralPlacement(
                width = tw, height = th,
                centerX = cx, centerY = cy,
                placed = placed,
                canvasSize = Size(w, h)
            )
            if (placement != null) {
                drawText(textLayoutResult = measured, topLeft = placement)
                // Padding 4px pour éviter le contact direct visuel
                placed += Rect(
                    offset = placement.copy(x = placement.x - 4f, y = placement.y - 4f),
                    size = Size(tw + 8f, th + 8f)
                )
            }
        }
    }
}

/**
 * Cherche un emplacement non chevauchant en spirale à partir du centre.
 * Retourne null si aucun emplacement trouvé après [MAX_TRIES] (le mot est
 * alors omis — en pratique seuls les derniers petits mots peuvent l'être).
 */
private fun findSpiralPlacement(
    width: Float,
    height: Float,
    centerX: Float,
    centerY: Float,
    placed: List<Rect>,
    canvasSize: Size,
): Offset? {
    val maxTries = 600
    val angleStep = 0.35f       // pas angulaire en rad — plus petit = spirale plus dense
    val radiusFactor = 1.4f     // distance moyenne par tour
    var angle = 0f

    repeat(maxTries) {
        val r = radiusFactor * angle
        val x = centerX + r * cos(angle) - width / 2f
        val y = centerY + r * sin(angle) * 0.7f - height / 2f  // 0.7 = légère ellipse (canvas plus large que haut)

        // Out of canvas → on peut tenter plus loin
        val inCanvas = x >= 0f && y >= 0f && (x + width) <= canvasSize.width && (y + height) <= canvasSize.height

        if (inCanvas) {
            val candidate = Rect(Offset(x, y), Size(width, height))
            val inflated = Rect(
                offset = Offset(x - 4f, y - 4f),
                size = Size(width + 8f, height + 8f)
            )
            if (placed.none { it.overlaps(inflated) }) {
                return candidate.topLeft
            }
        }
        angle += angleStep
    }
    return null
}

private fun lerpSp(min: TextUnit, max: TextUnit, t: Float): TextUnit =
    (min.value + (max.value - min.value) * t.coerceIn(0f, 1f)).sp

@Composable
private fun CategoryLegend(categories: Set<IngredientCategory>) {
    if (categories.isEmpty()) return
    val sorted = categories.sortedBy { it.ordinal }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // FlowRow serait mieux mais lourd à importer ; on contente d'un Row
        // qui scrolle pas — sur portrait standard, 4-5 catégories tiennent.
        sorted.take(5).forEach { cat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(cat.color))
                Text(cat.displayName, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1)
            }
        }
    }
}

// ═══════════════════════════════════════
// 2. DONUT CHART CATÉGORIEL
// ═══════════════════════════════════════
//
// Donut "lecture diététique" : montre la répartition par catégorie sur le
// poids total ingéré. Beaucoup plus parlant qu'un Top "plats" pour évaluer
// l'équilibre du régime (ex : "70% féculents, 5% légumes" = signal alarme).

@Composable
private fun CategoryDonutCard(shares: List<CategoryShare>, totalGrams: Int) {
    if (shares.isEmpty() || totalGrams == 0) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PieChart, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Répartition par catégorie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Donut
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    DonutCanvas(shares = shares, modifier = Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatGrams(totalGrams),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold
                        )
                        Text("ingéré",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                // Légende verticale avec %
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shares.take(6).forEach { share ->
                        DonutLegendRow(share)
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutCanvas(shares: List<CategoryShare>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.16f
        val padding = stroke / 2f + 2f
        val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
        val topLeft = Offset(padding, padding)

        var startAngle = -90f
        for (share in shares) {
            val sweep = share.percentage * 360f
            if (sweep <= 0f) continue
            // Petit gap entre arcs : on retire 1° du sweep
            val gap = if (shares.size > 1) 1.5f else 0f
            val drawSweep = (sweep - gap).coerceAtLeast(0.5f)
            drawArc(
                color = share.category.color,
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
private fun DonutLegendRow(share: CategoryShare) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(share.category.color))
        Text(
            share.category.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            formatPct(share.percentage),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = share.category.color
        )
    }
}

// ═══════════════════════════════════════
// 3. TOP 5 INGRÉDIENTS
// ═══════════════════════════════════════
//
// Le vrai "top aliments" : trié par poids cumulé (= ce que tu manges
// vraiment le plus en quantité, pas en nombre de scans). Utilise les noms
// normalisés/lemmatisés, donc "haricot vert" agrège "haricots verts cuits"
// et "haricots verts frais".

@Composable
private fun TopIngredientsCard(ingredients: List<IngredientStat>) {
    val top = ingredients.take(5)
    if (top.isEmpty()) return
    val maxG = top.first().totalGrams

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Text("Tes aliments stars", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            top.forEachIndexed { idx, ing ->
                val medal = when (idx) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> null }
                val fraction = ing.totalGrams.toFloat() / maxG.coerceAtLeast(1)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Rang
                    if (medal != null) Text(medal, fontSize = 18.sp)
                    else Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    }

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        // Ligne 1 : nom + emoji catégorie + stats
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(ing.category.emoji, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                ing.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (idx < 3) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                formatGrams(ing.totalGrams),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold,
                                color = ing.category.color
                            )
                        }
                        // Sous-ligne stats
                        Text(
                            "Présent dans ${ing.scanCount} repas · ${ing.totalCalories} kcal apportés",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        // Barre proportionnelle
                        Box(
                            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                .background(ing.category.color.copy(alpha = 0.12f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(fraction.coerceIn(0.04f, 1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(ing.category.color.copy(alpha = if (idx < 3) 1f else 0.7f))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// 4. DISTRIBUTION NUTRI-SCORE
// ═══════════════════════════════════════
//
// 5 barres A→E avec couleurs officielles. Verdict qualitatif au-dessus
// (% A+B sur la période). Donne une lecture immédiate de la qualité
// globale du régime.

@Composable
private fun NutriScoreDistributionCard(dist: NutriScoreDistribution) {
    val maxCount = max(1, listOf(dist.countA, dist.countB, dist.countC, dist.countD, dist.countE).max())

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header + verdict
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Star, null, Modifier.size(20.dp), tint = OrangeVibrant)
                Column(Modifier.weight(1f)) {
                    Text("Qualité de tes repas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val verdict = nutriVerdict(dist.highQualityShare)
                    Text(verdict.text, style = MaterialTheme.typography.labelSmall, color = verdict.color)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = NeonGreen.copy(alpha = 0.12f)) {
                    Text(
                        "${(dist.highQualityShare * 100).toInt()}% A+B",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
            }

            // Barres A → E
            Row(Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                listOf('A', 'B', 'C', 'D', 'E').forEach { grade ->
                    val count = dist.count(grade)
                    val ratio = count.toFloat() / maxCount
                    NutriBar(grade = grade, count = count, ratio = ratio, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NutriBar(grade: Char, count: Int, ratio: Float, modifier: Modifier) {
    val color = nutriColor(grade)
    val animatedRatio by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "nutri-bar-$grade"
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Compte au-dessus de la barre
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        // Barre verticale
        Box(
            Modifier.weight(1f).fillMaxWidth(0.7f).clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(animatedRatio.coerceAtLeast(0.02f))
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        // Lettre — style avec includeFontPadding=false + LineHeightStyle.Trim.Both
        // pour garantir le centrage optique du glyphe (cf. NutriScoreCalculator).
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
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
    )
)

private data class NutriVerdict(val text: String, val color: Color)

/**
 * Composable car `OrangeVibrant` est un getter @Composable
 * (résolu depuis la palette de thème active).
 */
@Composable
private fun nutriVerdict(highQualityShare: Float): NutriVerdict = when {
    highQualityShare >= 0.7f -> NutriVerdict("Excellent profil nutritionnel", NeonGreen)
    highQualityShare >= 0.5f -> NutriVerdict("Bon équilibre · marge de progression", NeonGreen.copy(alpha = 0.85f))
    highQualityShare >= 0.3f -> NutriVerdict("Mitigé · vise plus de A et B", OrangeVibrant)
    else -> NutriVerdict("À améliorer · privilégie les A et B", Color(0xFFEF4444))
}

private fun nutriColor(grade: Char): Color = when (grade) {
    'A' -> Color(0xFF038141); 'B' -> Color(0xFF85BB2F); 'C' -> Color(0xFFFECB02)
    'D' -> Color(0xFFEE8100); 'E' -> Color(0xFFE63E11); else -> Color.Gray
}

// ═══════════════════════════════════════
// HELPERS FORMAT
// ═══════════════════════════════════════

private fun formatGrams(g: Int): String = when {
    g >= 1000 -> "${"%.1f".format(g / 1000.0)} kg"
    else -> "${g} g"
}

private fun formatPct(p: Float): String = "${(p * 100).toInt()}%"
