package com.shredcoach.app.presentation.nutrition.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.domain.nutrition.GICategory
import com.shredcoach.app.domain.nutrition.GIConfidence
import com.shredcoach.app.domain.nutrition.GLCategory
import com.shredcoach.app.domain.nutrition.GlycemicMath

/**
 * Badge premium FAANG-grade pour l'affichage de l'indice glycémique (IG) et
 * de la charge glycémique (GL) d'un repas scanné.
 *
 * **3 variantes** :
 *  - [GlycemicIndexBadge.Compact]   — pill horizontale, à côté du Nutri-Score
 *  - [GlycemicIndexBadge.Inline]    — version mini (history list, score à droite)
 *  - [GlycemicIndexBadge.Hero]      — card complète (MealScanDetail, full info)
 *
 * **Couleurs** :
 *  - LOW    → vert (#34A853)  | IG bas, glucose stable
 *  - MEDIUM → orange (#F59E0B)| IG modéré, pic modéré
 *  - HIGH   → rouge (#EF4444) | IG élevé, pic important
 *  - UNKNOWN→ gris discret    | données non disponibles (legacy / LLM incertain)
 *
 * **Confidence** : module l'opacité du badge.
 *  - HIGH  : opacité 1.0
 *  - MEDIUM: opacité 0.85
 *  - LOW   : opacité 0.70 + indicateur "~" devant la valeur
 *  - UNKNOWN: pas d'IG affiché (placeholder "—")
 */
object GlycemicIndexBadge {

    // ─── Palette ─────────────────────────────────────────────────────────────
    private val ColorLow = Color(0xFF34A853)
    private val ColorMedium = Color(0xFFF59E0B)
    private val ColorHigh = Color(0xFFEF4444)
    private val ColorUnknown = Color(0xFF9CA3AF)

    private fun colorFor(category: GICategory): Color = when (category) {
        GICategory.LOW -> ColorLow
        GICategory.MEDIUM -> ColorMedium
        GICategory.HIGH -> ColorHigh
        GICategory.UNKNOWN -> ColorUnknown
    }

    private fun alphaFor(confidence: GIConfidence): Float = when (confidence) {
        GIConfidence.HIGH -> 1.0f
        GIConfidence.MEDIUM -> 0.85f
        GIConfidence.LOW -> 0.70f
        GIConfidence.UNKNOWN -> 1.0f
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPACT — à côté du Nutri-Score sur les meal cards
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Badge compact horizontal. ~80dp de large, hauteur ~28dp.
     * Affiche "IG XX" + GL effectif en sous-texte.
     */
    @Composable
    fun Compact(scan: MealScanEntity, modifier: Modifier = Modifier) {
        val gi = scan.glycemicIndex
        val category = GlycemicMath.category(scan)
        val confidence = GlycemicMath.confidence(scan)
        val gl = GlycemicMath.effectiveGl(scan)

        val color = colorFor(category)
        val alpha = alphaFor(confidence)

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.12f * alpha))
                .border(1.dp, color.copy(alpha = 0.30f * alpha), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = color.copy(alpha = alpha),
                modifier = Modifier.size(12.dp),
            )
            if (gi == null) {
                // Empty state — placeholder discret
                Text(
                    "IG —",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorUnknown,
                    letterSpacing = 0.3.sp,
                )
            } else {
                Text(
                    "IG ${if (confidence == GIConfidence.LOW) "~" else ""}$gi",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = alpha),
                    letterSpacing = 0.3.sp,
                )
                if (gl != null && gl > 0.0) {
                    Text(
                        "· GL ${gl.toInt()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = color.copy(alpha = alpha * 0.75f),
                    )
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INLINE — version mini pour le tightly-packed (history list right side)
    // ═══════════════════════════════════════════════════════════════════════

    @Composable
    fun Inline(scan: MealScanEntity, modifier: Modifier = Modifier) {
        val gi = scan.glycemicIndex
        val category = GlycemicMath.category(scan)
        val confidence = GlycemicMath.confidence(scan)
        val color = colorFor(category)
        val alpha = alphaFor(confidence)

        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f * alpha))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            Text(
                text = if (gi == null) "IG —" else "IG ${if (confidence == GIConfidence.LOW) "~" else ""}$gi",
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (gi == null) ColorUnknown else color.copy(alpha = alpha),
                letterSpacing = 0.2.sp,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HERO — card complète pour MealScanDetail
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Card hero premium pour MealScanDetail. Design FAANG-grade :
     *  - Header strip avec catégorie pill
     *  - Big number IG (hero metric)
     *  - **Gauge linéaire** 0-110 avec 3 zones colorées + curseur animé (le clou)
     *  - Mini gauge GL secondaire (charge glycémique effective, portion-aware)
     *
     * Pattern inspiré Apple Health Vitals + Stripe usage dashboards : visualiser
     * la position sur un spectre est plus parlant qu'un chiffre brut isolé.
     */
    @Composable
    fun Hero(scan: MealScanEntity, modifier: Modifier = Modifier) {
        val gi = scan.glycemicIndex
        val category = GlycemicMath.category(scan)
        val confidence = GlycemicMath.confidence(scan)
        val gl = GlycemicMath.effectiveGl(scan)
        val color = colorFor(category)

        // ─── Empty state (legacy / LLM incertain) ────────────────────────────
        if (gi == null) {
            EmptyHero(modifier)
            return
        }

        // ─── Filled state — premium gauge card ───────────────────────────────
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            color.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    )
                )
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ─── Header strip ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.Speed, null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.gi_hero_label).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                if (confidence == GIConfidence.LOW) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ColorUnknown.copy(alpha = 0.15f),
                    ) {
                        Text(
                            stringResource(R.string.gi_confidence_low_hint),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // ─── Hero number + category pill ─────────────────────────────────
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${if (confidence == GIConfidence.LOW) "~" else ""}$gi",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = color,
                )
                Column(
                    Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = color.copy(alpha = 0.18f),
                    ) {
                        Text(
                            stringResource(categoryLabel(category)),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            letterSpacing = 0.3.sp,
                        )
                    }
                    Text(
                        stringResource(categoryHint(category)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }

            // ─── LINEAR GAUGE — le clou du widget ────────────────────────────
            GiLinearGauge(gi = gi, pinColor = color)

            // ─── Mini-gauge GL secondaire (si dispo) ─────────────────────────
            if (gl != null && gl > 0.0) {
                Spacer(Modifier.height(2.dp))
                GlMiniGauge(gl = gl)
            }
        }
    }

    /**
     * Gauge linéaire 0-110 avec :
     *  - 3 zones colorées (LOW vert 0-55 / MEDIUM orange 55-70 / HIGH rouge 70-110)
     *  - Tick marks aux frontières 55 et 70
     *  - Pin animé positionné selon le GI
     *  - Labels de zone sous la barre
     */
    @Composable
    private fun GiLinearGauge(gi: Int, pinColor: Color) {
        val density = LocalDensity.current
        val animatedGi by animateFloatAsState(
            targetValue = gi.toFloat().coerceIn(0f, 110f),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "gi_pin_position",
        )

        // Proportions des zones sur 110 :
        //   LOW    : 0..55  → 50% de la barre
        //   MEDIUM : 55..70 → 13.6%
        //   HIGH   : 70..110 → 36.4%
        val lowFrac = 55f / 110f       // 0.5
        val medFrac = (70f - 55f) / 110f // 0.1364
        val highFrac = (110f - 70f) / 110f // 0.3636

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ─── Barre + pin ──────────────────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
            ) {
                val w = size.width
                val barHeight = with(density) { 14.dp.toPx() }
                val barY = (size.height - barHeight) / 2f
                val barCornerRadius = barHeight / 2f

                // Clip rounded rect → puis dessiner 3 zones rectangulaires dedans
                val barPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f,
                            top = barY,
                            right = w,
                            bottom = barY + barHeight,
                            cornerRadius = CornerRadius(barCornerRadius, barCornerRadius),
                        )
                    )
                }
                clipPath(barPath) {
                    val lowW = w * lowFrac
                    val medW = w * medFrac
                    val highW = w * highFrac
                    drawRect(
                        color = ColorLow,
                        topLeft = Offset(0f, barY),
                        size = Size(lowW, barHeight),
                    )
                    drawRect(
                        color = ColorMedium,
                        topLeft = Offset(lowW, barY),
                        size = Size(medW, barHeight),
                    )
                    drawRect(
                        color = ColorHigh,
                        topLeft = Offset(lowW + medW, barY),
                        size = Size(highW, barHeight),
                    )
                    // Subtle inner separators (tick lines blanches semi-transparentes)
                    val sep = with(density) { 1.dp.toPx() }
                    drawRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(lowW - sep / 2, barY),
                        size = Size(sep, barHeight),
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.35f),
                        topLeft = Offset(lowW + medW - sep / 2, barY),
                        size = Size(sep, barHeight),
                    )
                }

                // ─── Pin : cercle blanc bordé de la couleur catégorie ──────
                val pinX = (animatedGi / 110f) * w
                val pinRadius = with(density) { 11.dp.toPx() }
                val pinCenter = Offset(pinX.coerceIn(pinRadius, w - pinRadius), size.height / 2f)

                // Halo de couleur (subtile, donne du poids visuel)
                drawCircle(
                    color = pinColor.copy(alpha = 0.20f),
                    radius = pinRadius + with(density) { 4.dp.toPx() },
                    center = pinCenter,
                )
                // Ombre portée discrète
                drawCircle(
                    color = Color.Black.copy(alpha = 0.18f),
                    radius = pinRadius,
                    center = pinCenter.copy(y = pinCenter.y + with(density) { 1.5.dp.toPx() }),
                )
                // Anneau extérieur blanc
                drawCircle(
                    color = Color.White,
                    radius = pinRadius,
                    center = pinCenter,
                )
                // Anneau bordure de couleur
                drawCircle(
                    color = pinColor,
                    radius = pinRadius,
                    center = pinCenter,
                    style = Stroke(width = with(density) { 2.5.dp.toPx() }),
                )
                // Point central rempli
                drawCircle(
                    color = pinColor,
                    radius = pinRadius - with(density) { 5.dp.toPx() },
                    center = pinCenter,
                )
            }

            // ─── Labels des zones sous la barre ──────────────────────────────
            // Positionnés proportionnellement aux fractions LOW/MED/HIGH.
            Row(Modifier.fillMaxWidth()) {
                ZoneLabel(stringResource(R.string.gi_category_low), ColorLow, weight = lowFrac)
                ZoneLabel(stringResource(R.string.gi_category_medium), ColorMedium, weight = medFrac)
                ZoneLabel(stringResource(R.string.gi_category_high), ColorHigh, weight = highFrac)
            }
            // Échelle numérique discrète
            Row(Modifier.fillMaxWidth()) {
                ScaleNumber("0", weight = 1f, align = Alignment.Start)
                ScaleNumber("55", weight = (lowFrac - 0.5f / 110f) * 2f, align = Alignment.End)
                Spacer(Modifier.weight(medFrac))
                ScaleNumber("70", weight = 0.001f, align = Alignment.Start)
                ScaleNumber("110", weight = highFrac, align = Alignment.End)
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.ZoneLabel(label: String, color: Color, weight: Float) {
        Box(
            Modifier
                .weight(weight)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 0.3.sp,
                maxLines = 1,
            )
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.ScaleNumber(text: String, weight: Float, align: Alignment.Horizontal) {
        Box(
            Modifier.weight(weight),
            contentAlignment = when (align) {
                Alignment.Start -> Alignment.CenterStart
                Alignment.End -> Alignment.CenterEnd
                else -> Alignment.Center
            },
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }

    /**
     * Mini gauge GL secondaire — barre fine 0..30 avec pin discret.
     * GL > 30 (très rare) → pin clampé à l'extrémité droite (visual saturate).
     */
    @Composable
    private fun GlMiniGauge(gl: Double) {
        val density = LocalDensity.current
        val maxScale = 30.0
        val clamped = gl.coerceIn(0.0, maxScale).toFloat()
        val animatedGl by animateFloatAsState(
            targetValue = clamped,
            animationSpec = tween(800, easing = FastOutSlowInEasing),
            label = "gl_pin",
        )
        val category = GLCategory.fromGl(gl)
        val glColor = when (category) {
            GLCategory.LOW -> ColorLow
            GLCategory.MEDIUM -> ColorMedium
            GLCategory.HIGH -> ColorHigh
            GLCategory.UNKNOWN -> ColorUnknown
        }
        val glLabel = when (category) {
            GLCategory.LOW -> stringResource(R.string.gl_label_low)
            GLCategory.MEDIUM -> stringResource(R.string.gl_label_medium)
            GLCategory.HIGH -> stringResource(R.string.gl_label_high)
            GLCategory.UNKNOWN -> ""
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.gl_effective_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        glLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = glColor,
                    )
                    Text(
                        "%.1f".format(gl),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = glColor,
                    )
                }
            }
            // Mini barre
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            ) {
                val w = size.width
                val barHeight = size.height
                val cornerRadius = barHeight / 2f
                val barPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = 0f,
                            right = w, bottom = barHeight,
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    )
                }
                // Background segments — proportions GL : LOW≤10, MED 11-19, HIGH≥20
                clipPath(barPath) {
                    val lowF = 10f / maxScale.toFloat()       // 0.333
                    val medF = 10f / maxScale.toFloat()       // 0.333 (10-20)
                    val highF = 10f / maxScale.toFloat()      // 0.333 (20-30)
                    drawRect(color = ColorLow.copy(alpha = 0.35f), topLeft = Offset(0f, 0f), size = Size(w * lowF, barHeight))
                    drawRect(color = ColorMedium.copy(alpha = 0.35f), topLeft = Offset(w * lowF, 0f), size = Size(w * medF, barHeight))
                    drawRect(color = ColorHigh.copy(alpha = 0.35f), topLeft = Offset(w * (lowF + medF), 0f), size = Size(w * highF, barHeight))
                }
                // Pin position
                val pinX = (animatedGl / maxScale.toFloat()) * w
                val pinRadius = with(density) { 6.dp.toPx() }
                val pinCenter = Offset(pinX.coerceIn(pinRadius, w - pinRadius), barHeight / 2f)

                drawCircle(color = Color.White, radius = pinRadius, center = pinCenter)
                drawCircle(color = glColor, radius = pinRadius - with(density) { 2.dp.toPx() }, center = pinCenter)
            }
        }
    }

    @Composable
    private fun EmptyHero(modifier: Modifier) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    RoundedCornerShape(16.dp),
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Speed, null,
                tint = ColorUnknown,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.gi_hero_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    letterSpacing = 0.5.sp,
                )
                Text(
                    stringResource(R.string.gi_unavailable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Text(
                "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = ColorUnknown,
            )
        }
    }

    private fun categoryLabel(category: GICategory): Int = when (category) {
        GICategory.LOW -> R.string.gi_category_low
        GICategory.MEDIUM -> R.string.gi_category_medium
        GICategory.HIGH -> R.string.gi_category_high
        GICategory.UNKNOWN -> R.string.gi_category_unknown
    }

    private fun categoryHint(category: GICategory): Int = when (category) {
        GICategory.LOW -> R.string.gi_hint_low
        GICategory.MEDIUM -> R.string.gi_hint_medium
        GICategory.HIGH -> R.string.gi_hint_high
        GICategory.UNKNOWN -> R.string.gi_unavailable_desc
    }
}
