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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    // MINI GAUGES — sur les meal cards (NutritionScreen + History)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Deux mini-gauges côte-à-côte (IG + GL) pour les meal cards de la
     * NutritionScreen et de l'historique. Pattern :
     *
     *   IG          55              GL          18
     *   ▓▓▓▓▓│▓│▓▓⊙▓▓▓▓             ▓▓▓⊙▓│▓▓▓▓▓
     *
     * - Pin compact (5dp) avec halo blanc + cœur coloré (mini du Hero)
     * - Barre 7dp, 3 zones colorées (mêmes seuils que Hero)
     * - Label + valeur en header inline (label gris, valeur colorée)
     * - Animation `animateFloatAsState` key-stable → ne replay PAS au scroll
     *   (target value cached par item)
     *
     * Affiche un placeholder discret `IG —` si le LLM n'a pas estimé l'IG.
     */
    @Composable
    fun MiniGauges(scan: MealScanEntity, modifier: Modifier = Modifier) {
        val gi = scan.glycemicIndex

        // ─── Empty state (scan legacy ou LLM incertain) ──────────────────────
        if (gi == null) {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(6.dp),
                color = ColorUnknown.copy(alpha = 0.10f),
            ) {
                Text(
                    "IG —",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorUnknown,
                )
            }
            return
        }

        val confidence = GlycemicMath.confidence(scan)
        val giCategory = GlycemicMath.category(scan)
        val giColor = colorFor(giCategory)
        val gl = GlycemicMath.effectiveGl(scan)
        val glCategory = GLCategory.fromGl(gl)
        val glColor = when (glCategory) {
            GLCategory.LOW -> ColorLow
            GLCategory.MEDIUM -> ColorMedium
            GLCategory.HIGH -> ColorHigh
            GLCategory.UNKNOWN -> ColorUnknown
        }

        Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StaticMiniGauge(
                label = "IG",
                value = "${if (confidence == GIConfidence.LOW) "~" else ""}$gi",
                normalizedPos = (gi / 110f).coerceIn(0f, 1f),
                zoneEndFractions = listOf(0.5f, 0.6364f, 1f),
                zoneColors = listOf(ColorLow, ColorMedium, ColorHigh),
                pinColor = giColor,
                valueColor = giColor,
                modifier = Modifier.weight(1f),
            )
            if (gl != null && gl > 0.0) {
                StaticMiniGauge(
                    label = "GL",
                    value = gl.toInt().toString(),
                    normalizedPos = (gl.toFloat() / 30f).coerceIn(0f, 1f),
                    zoneEndFractions = listOf(1f / 3f, 2f / 3f, 1f),
                    zoneColors = listOf(ColorLow, ColorMedium, ColorHigh),
                    pinColor = glColor,
                    valueColor = glColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    /**
     * Widget mini-gauge atomique HORIZONTAL : `[Label] [Bar weight=1f] [Value]`.
     *
     * **Pourquoi horizontal** : le centre vertical du widget = centre vertical
     * de la barre. Quand ce widget est `CenterVertically` aligné dans un Row
     * avec un Nutri-Score, la barre s'aligne pile au milieu du pictogramme.
     * (En layout vertical, la barre était en bas et apparaissait plus basse.)
     *
     * Optimisé pour LazyColumn : `animateFloatAsState` ne joue qu'au premier
     * mount d'un item (target value cached).
     */
    @Composable
    private fun StaticMiniGauge(
        label: String,
        value: String,
        normalizedPos: Float,
        zoneEndFractions: List<Float>,
        zoneColors: List<Color>,
        pinColor: Color,
        valueColor: Color,
        modifier: Modifier = Modifier,
    ) {
        val density = LocalDensity.current
        val animatedPos by animateFloatAsState(
            targetValue = normalizedPos.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            label = "mini_pin_$label",
        )

        Row(
            modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 0.6.sp,
            )
            // Bar avec pin animé — prend l'espace résiduel
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(7.dp),
            ) {
                val w = size.width
                val h = size.height
                val cornerRadius = h / 2f
                val barPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = 0f,
                            right = w, bottom = h,
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    )
                }
                clipPath(barPath) {
                    var startFrac = 0f
                    for ((endFrac, color) in zoneEndFractions.zip(zoneColors)) {
                        drawRect(
                            color = color.copy(alpha = 0.40f),
                            topLeft = Offset(w * startFrac, 0f),
                            size = Size(w * (endFrac - startFrac), h),
                        )
                        startFrac = endFrac
                    }
                    // Tick lines aux frontières (blanc subtil)
                    val sep = with(density) { 0.5.dp.toPx() }
                    for (i in 0 until zoneEndFractions.size - 1) {
                        val x = w * zoneEndFractions[i]
                        drawRect(
                            color = Color.White.copy(alpha = 0.6f),
                            topLeft = Offset(x - sep / 2, 0f),
                            size = Size(sep, h),
                        )
                    }
                }
                // Pin : halo blanc + cœur coloré (cohérent avec le Hero)
                val pinX = animatedPos * w
                val pinOuterR = with(density) { 5.dp.toPx() }
                val pinInnerR = with(density) { 3.dp.toPx() }
                val pinCenter = Offset(
                    pinX.coerceIn(pinOuterR, w - pinOuterR),
                    h / 2f,
                )
                drawCircle(Color.White, pinOuterR, pinCenter)
                drawCircle(pinColor, pinInnerR, pinCenter)
            }
            // Valeur — width minimale réservée pour stabilité visuelle
            // (sinon le bar "respire" entre valeurs à 1 et 3 chiffres)
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                modifier = Modifier.widthIn(min = 22.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // COMPACT — pill horizontale (legacy, conservée pour usage futur)
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
     *  - **Threshold numbers (0/55/70/110) dessinés en Canvas aux positions exactes**
     *  - **Légende découplée** : 3 items équi-larges avec dot coloré + label complet
     *    → aucune troncature possible, l'œil n'est pas dépendant de la largeur de zone
     */
    @Composable
    private fun GiLinearGauge(gi: Int, pinColor: Color) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        val animatedGi by animateFloatAsState(
            targetValue = gi.toFloat().coerceIn(0f, 110f),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "gi_pin_position",
        )

        // Proportions ISO des zones sur 110 :
        //   LOW    : 0..55  → 50.0% de la barre
        //   MEDIUM : 55..70 → 13.6%
        //   HIGH   : 70..110 → 36.4%
        val lowEnd = 55f / 110f       // 0.5
        val medEnd = 70f / 110f       // 0.6364

        val numberStyle = TextStyle(
            fontSize = 10.sp,
            color = labelColor,
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.SemiBold,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // ─── Canvas : barre + pin + threshold numbers ─────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                val w = size.width
                val barHeight = with(density) { 14.dp.toPx() }
                val barY = with(density) { 8.dp.toPx() } // marge top pour laisser space au halo du pin
                val barCornerRadius = barHeight / 2f

                // Bar avec 3 zones colorées (clip rounded rect)
                val barPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = barY,
                            right = w, bottom = barY + barHeight,
                            cornerRadius = CornerRadius(barCornerRadius, barCornerRadius),
                        )
                    )
                }
                clipPath(barPath) {
                    val lowW = w * lowEnd
                    val medW = w * (medEnd - lowEnd)
                    val highW = w * (1f - medEnd)
                    drawRect(ColorLow, Offset(0f, barY), Size(lowW, barHeight))
                    drawRect(ColorMedium, Offset(lowW, barY), Size(medW, barHeight))
                    drawRect(ColorHigh, Offset(lowW + medW, barY), Size(highW, barHeight))
                    // Tick lines aux frontières 55 et 70 (blancs subtils, marquent la séparation)
                    val sep = with(density) { 1.5.dp.toPx() }
                    drawRect(Color.White.copy(alpha = 0.45f), Offset(lowW - sep / 2, barY), Size(sep, barHeight))
                    drawRect(Color.White.copy(alpha = 0.45f), Offset(lowW + medW - sep / 2, barY), Size(sep, barHeight))
                }

                // Pin animé à la position GI
                val pinX = (animatedGi / 110f) * w
                val pinRadius = with(density) { 11.dp.toPx() }
                val pinCenterY = barY + barHeight / 2f
                val pinCenter = Offset(pinX.coerceIn(pinRadius, w - pinRadius), pinCenterY)

                drawCircle(pinColor.copy(alpha = 0.20f), pinRadius + with(density) { 4.dp.toPx() }, pinCenter)
                drawCircle(Color.Black.copy(alpha = 0.18f), pinRadius, pinCenter.copy(y = pinCenter.y + with(density) { 1.5.dp.toPx() }))
                drawCircle(Color.White, pinRadius, pinCenter)
                drawCircle(pinColor, pinRadius, pinCenter, style = Stroke(width = with(density) { 2.5.dp.toPx() }))
                drawCircle(pinColor, pinRadius - with(density) { 5.dp.toPx() }, pinCenter)

                // ─── Threshold numbers : 0 / 55 / 70 / 110 aux positions exactes ──
                // Position x cible (sur la barre) + clamp aux bords pour ne jamais
                // sortir du canvas. Centré horizontalement sur la valeur.
                val numbersY = barY + barHeight + with(density) { 6.dp.toPx() }
                val thresholds = listOf(
                    "0" to 0f,
                    "55" to w * lowEnd,
                    "70" to w * medEnd,
                    "110" to w,
                )
                for ((label, x) in thresholds) {
                    val layout = textMeasurer.measure(label, numberStyle)
                    val tw = layout.size.width.toFloat()
                    val drawX = (x - tw / 2f).coerceIn(0f, w - tw)
                    drawText(layout, topLeft = Offset(drawX, numbersY))
                }
            }

            // ─── Légende équi-largeur : dot + label complet ──────────────────
            // Découplée des proportions de la barre → "Modéré" ne sera JAMAIS
            // tronqué quelle que soit la largeur du conteneur.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LegendChip(stringResource(R.string.gi_category_low), ColorLow, Modifier.weight(1f))
                LegendChip(stringResource(R.string.gi_category_medium), ColorMedium, Modifier.weight(1f))
                LegendChip(stringResource(R.string.gi_category_high), ColorHigh, Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun LegendChip(label: String, color: Color, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 1,
                letterSpacing = 0.1.sp,
            )
        }
    }

    /**
     * Mini gauge GL secondaire — barre fine avec pin discret.
     *
     * Échelle 0..30 (3 zones équi-largeur LOW≤10 / MED 11-19 / HIGH≥20).
     * GL > 30 (très rare, repas extrême) → pin clampé à droite.
     *
     * Inclut threshold numbers 0/10/20/30 dessinés en Canvas aux positions exactes.
     */
    @Composable
    private fun GlMiniGauge(gl: Double) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
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
        val labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        val numberStyle = TextStyle(
            fontSize = 9.sp,
            color = labelColor,
            fontFeatureSettings = "tnum",
            fontWeight = FontWeight.Medium,
        )

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
            // Canvas : barre + pin + threshold numbers (0/10/20/30)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            ) {
                val w = size.width
                val barHeight = with(density) { 8.dp.toPx() }
                val barY = with(density) { 4.dp.toPx() }
                val cornerRadius = barHeight / 2f

                val barPath = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = 0f, top = barY,
                            right = w, bottom = barY + barHeight,
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        )
                    )
                }
                clipPath(barPath) {
                    // Proportions équi-largeur sur 0..30
                    val third = w / 3f
                    drawRect(ColorLow.copy(alpha = 0.40f), Offset(0f, barY), Size(third, barHeight))
                    drawRect(ColorMedium.copy(alpha = 0.40f), Offset(third, barY), Size(third, barHeight))
                    drawRect(ColorHigh.copy(alpha = 0.40f), Offset(2 * third, barY), Size(third, barHeight))
                    // Tick lines aux frontières 10 et 20
                    val sep = with(density) { 1.dp.toPx() }
                    drawRect(Color.White.copy(alpha = 0.4f), Offset(third - sep / 2, barY), Size(sep, barHeight))
                    drawRect(Color.White.copy(alpha = 0.4f), Offset(2 * third - sep / 2, barY), Size(sep, barHeight))
                }

                // Pin
                val pinX = (animatedGl / maxScale.toFloat()) * w
                val pinRadius = with(density) { 6.dp.toPx() }
                val pinCenter = Offset(pinX.coerceIn(pinRadius, w - pinRadius), barY + barHeight / 2f)
                drawCircle(Color.Black.copy(alpha = 0.15f), pinRadius, pinCenter.copy(y = pinCenter.y + with(density) { 1.dp.toPx() }))
                drawCircle(Color.White, pinRadius, pinCenter)
                drawCircle(glColor, pinRadius - with(density) { 2.dp.toPx() }, pinCenter)

                // Threshold numbers — positions exactes 0/10/20/30
                val numbersY = barY + barHeight + with(density) { 4.dp.toPx() }
                val thresholds = listOf(
                    "0" to 0f,
                    "10" to w / 3f,
                    "20" to 2f * w / 3f,
                    "30+" to w,
                )
                for ((label, x) in thresholds) {
                    val layout = textMeasurer.measure(label, numberStyle)
                    val tw = layout.size.width.toFloat()
                    val drawX = (x - tw / 2f).coerceIn(0f, w - tw)
                    drawText(layout, topLeft = Offset(drawX, numbersY))
                }
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
