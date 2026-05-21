package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.gson.JsonParser
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.AnalysisVerdict
import com.shredcoach.app.presentation.navigation.Screen
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Écran d'analyse experte glycémique — produite par Dr. Glykos (LLM).
 *
 * Layout :
 *  1. **Hero verdict** (gradient emerald + grand verdict + summary)
 *  2. **Courbe annotée** (canvas avec markers cliquables sur les insights)
 *  3. **Cartes insights** (ordonnées chronologiquement, status-coloré)
 *  4. **Carte global advice** (action pour demain)
 *  5. **CTA Dr. Glykos** (ouvre le chat persona avec contexte de la journée)
 *
 * Loading state : skeleton subtle.
 * Error state : illustration + retry button.
 * Empty state (pas de log) : message + redirige vers GlucoseEntry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseAnalysisScreen(
    navController: NavController,
    viewModel: GlucoseAnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    GlucoseSectionHeader(
                        icon = Icons.Default.MedicalServices,
                        title = stringResource(R.string.glucose_analysis_title),
                        subtitle = state.date.format(
                            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                .withLocale(Locale.getDefault())
                        ).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.analysis != null) {
                        IconButton(
                            onClick = { viewModel.reanalyze() },
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                stringResource(R.string.glucose_analysis_refresh_cd),
                                tint = GlucoseColors.Emerald600,
                            )
                        }
                    }
                },
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                state.isLoading && state.analysis == null -> LoadingScreen()
                state.errorMessage != null && state.analysis == null ->
                    ErrorScreen(
                        message = state.errorMessage!!,
                        onRetry = { viewModel.reanalyze() },
                        onUploadCgm = { navController.navigate(Screen.GlucoseEntry.createRoute(state.date)) },
                    )
                state.analysis != null -> AnalysisContent(
                    state = state,
                    onOpenDrGlykos = { navController.navigate(Screen.DrGlykosChat.route) },
                )
                else -> LoadingScreen()
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CONTENU PRINCIPAL
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AnalysisContent(
    state: GlucoseAnalysisState,
    onOpenDrGlykos: () -> Unit,
) {
    val analysis = state.analysis ?: return

    // Parse la curve UNE FOIS hors du LazyColumn (LazyListScope n'est pas
    // @Composable → remember y est interdit). Sécurité null/short-curve gérée
    // côté item ci-dessous.
    val curveJson = state.glucoseLog?.glucoseMgdlCurveJson
    val curve = remember(curveJson) { curveJson?.let { parseCurve(it) }.orEmpty() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HeroVerdictCard(analysis.verdict, analysis.summary) }

        // Courbe annotée — si on a au moins 2 points exploitables
        if (curve.size >= 2) {
            item {
                AnnotatedCurveCard(
                    curve = curve,
                    insights = state.insights,
                )
            }
        }

        if (state.insights.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.glucose_analysis_insights_header).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(state.insights) { insight ->
                InsightCard(insight)
            }
        }

        if (analysis.globalAdvice.isNotBlank()) {
            item { GlobalAdviceCard(analysis.globalAdvice) }
        }

        item { ConsultDrGlykosCta(onOpenDrGlykos) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HERO VERDICT
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HeroVerdictCard(verdict: AnalysisVerdict, summary: String) {
    val (label, gradient) = when (verdict) {
        AnalysisVerdict.EXCELLENT -> Pair(
            stringResource(R.string.glucose_verdict_excellent),
            listOf(GlucoseColors.Emerald900, GlucoseColors.Emerald600),
        )
        AnalysisVerdict.GOOD -> Pair(
            stringResource(R.string.glucose_verdict_good),
            listOf(GlucoseColors.Emerald800, GlucoseColors.Teal500),
        )
        AnalysisVerdict.FAIR -> Pair(
            stringResource(R.string.glucose_verdict_fair),
            listOf(Color(0xFF92400E), Color(0xFFF59E0B)),
        )
        AnalysisVerdict.CONCERN -> Pair(
            stringResource(R.string.glucose_verdict_concern),
            listOf(Color(0xFF7F1D1D), Color(0xFFEF4444)),
        )
    }

    GlucoseHeroSurface(
        modifier = Modifier.fillMaxWidth(),
        gradient = gradient,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (verdict) {
                            AnalysisVerdict.EXCELLENT, AnalysisVerdict.GOOD -> Icons.Default.CheckCircle
                            AnalysisVerdict.FAIR -> Icons.Default.Insights
                            AnalysisVerdict.CONCERN -> Icons.Default.Warning
                        },
                        null, Modifier.size(22.dp), tint = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glucose_analysis_overall_verdict).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
        }
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                lineHeight = 20.sp,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COURBE ANNOTÉE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AnnotatedCurveCard(
    curve: List<Pair<LocalTime, Double>>,
    insights: List<GlucoseInsight>,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.AutoGraph,
                    null, Modifier.size(20.dp),
                    tint = GlucoseColors.Emerald600,
                )
                Text(
                    stringResource(R.string.glucose_analysis_curve_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlucoseColors.Emerald800,
                )
            }
            // Canvas curve avec markers d'insights
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            ) {
                CurveCanvas(curve = curve, insights = insights)
            }
            // Légende
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LegendDot(GlucoseColors.InRange, stringResource(R.string.glucose_analysis_legend_positive))
                LegendDot(Color(0xFFF59E0B), stringResource(R.string.glucose_analysis_legend_neutral))
                LegendDot(GlucoseColors.Critical, stringResource(R.string.glucose_analysis_legend_concern))
            }
        }
    }
}

@Composable
private fun CurveCanvas(
    curve: List<Pair<LocalTime, Double>>,
    insights: List<GlucoseInsight>,
) {
    val density = LocalDensity.current
    val minMgdl = 50.0
    val maxMgdl = (curve.maxOf { it.second }.coerceAtLeast(200.0)).coerceAtMost(300.0)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val yRange = (maxMgdl - minMgdl).coerceAtLeast(1.0).toFloat()

        fun y(mgdl: Double): Float = h - ((mgdl - minMgdl).toFloat() / yRange * h)
        fun x(time: LocalTime): Float = (time.toSecondOfDay().toFloat() / 86400f) * w

        // Zone cible 70-140 (vert très clair)
        val targetTopY = y(140.0)
        val targetBottomY = y(70.0)
        drawRect(
            color = GlucoseColors.InRange.copy(alpha = 0.10f),
            topLeft = Offset(0f, targetTopY),
            size = Size(w, targetBottomY - targetTopY),
        )

        // Ligne 180 pointillée
        val spikeY = y(180.0)
        drawLine(
            color = GlucoseColors.Critical.copy(alpha = 0.35f),
            start = Offset(0f, spikeY), end = Offset(w, spikeY),
            strokeWidth = with(density) { 1.dp.toPx() },
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )

        // Courbe + gradient fill
        if (curve.size >= 2) {
            val stroke = Path().apply {
                moveTo(x(curve.first().first), y(curve.first().second))
                for (i in 1 until curve.size) {
                    lineTo(x(curve[i].first), y(curve[i].second))
                }
            }
            val fill = Path().apply {
                addPath(stroke)
                lineTo(x(curve.last().first), h)
                lineTo(x(curve.first().first), h)
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

        // Markers d'insights : dot coloré au timestamp, sur la courbe
        for (insight in insights) {
            val t = insight.time ?: continue
            val mgdl = curve.firstOrNull { it.first.hour == t.hour && kotlin.math.abs(it.first.minute - t.minute) < 30 }?.second
                ?: continue
            val color = when (insight.verdict) {
                InsightVerdict.POSITIVE -> GlucoseColors.InRange
                InsightVerdict.NEUTRAL -> Color(0xFFF59E0B)
                InsightVerdict.CONCERN -> GlucoseColors.Critical
            }
            // Halo blanc + dot couleur pour ressortir sur le gradient
            drawCircle(
                color = Color.White,
                radius = with(density) { 7.dp.toPx() },
                center = Offset(x(t), y(mgdl)),
            )
            drawCircle(
                color = color,
                radius = with(density) { 5.dp.toPx() },
                center = Offset(x(t), y(mgdl)),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// INSIGHT CARD
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun InsightCard(insight: GlucoseInsight) {
    val statusColor = when (insight.verdict) {
        InsightVerdict.POSITIVE -> GlucoseColors.InRange
        InsightVerdict.NEUTRAL -> Color(0xFFF59E0B)
        InsightVerdict.CONCERN -> GlucoseColors.Critical
    }
    val icon = categoryIcon(insight.category)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.14f), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(20.dp), tint = statusColor)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        insight.time?.let { t ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = statusColor.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    t.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            insight.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    Text(
                        insight.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                insight.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                lineHeight = 20.sp,
            )
            insight.relatedMealName?.let { meal ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(Icons.Default.Restaurant, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    Text(
                        meal,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
            }
        }
    }
}

private fun categoryIcon(c: InsightCategory): ImageVector = when (c) {
    InsightCategory.POSTPRANDIAL_PEAK -> Icons.Default.Restaurant
    InsightCategory.RECOVERY -> Icons.Default.TrendingDown
    InsightCategory.DAWN -> Icons.Default.WbSunny
    InsightCategory.CORTISOL_RISE -> Icons.Default.Bolt
    InsightCategory.STABLE_FASTING -> Icons.Default.SelfImprovement
    InsightCategory.NIGHT_FASTING -> Icons.Default.Bedtime
    InsightCategory.HYPO -> Icons.Default.Warning
    InsightCategory.SPIKE -> Icons.Default.AutoGraph
    InsightCategory.EXERCISE_RESPONSE -> Icons.Default.DirectionsRun
    InsightCategory.UNKNOWN -> Icons.Default.Insights
}

// ═══════════════════════════════════════════════════════════════════════════
// GLOBAL ADVICE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun GlobalAdviceCard(advice: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = GlucoseColors.Emerald600, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.LightMode, null, Modifier.size(18.dp), tint = Color.White)
                    }
                }
                Text(
                    stringResource(R.string.glucose_analysis_advice_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlucoseColors.Emerald600,
                )
            }
            Text(
                advice,
                style = MaterialTheme.typography.bodyMedium,
                color = GlucoseColors.Emerald800,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CTA Dr. Glykos
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ConsultDrGlykosCta(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = GlucoseColors.Emerald100, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MedicalServices, null, Modifier.size(20.dp), tint = GlucoseColors.Emerald600)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glucose_analysis_ask_dr_glykos_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlucoseColors.Emerald800,
                )
                Text(
                    stringResource(R.string.glucose_analysis_ask_dr_glykos_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, null,
                Modifier.size(16.dp), tint = GlucoseColors.Emerald600,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// STATES — loading / error / empty
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = GlucoseColors.Emerald100, modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FactCheck, null, Modifier.size(40.dp), tint = GlucoseColors.Emerald600)
            }
        }
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = GlucoseColors.Emerald600, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.glucose_analysis_loading_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = GlucoseColors.Emerald800,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.glucose_analysis_loading_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = GlucoseColors.Emerald800.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onUploadCgm: () -> Unit,
) {
    val isNoCgm = message.contains("CGM", ignoreCase = true) ||
        message.contains("log", ignoreCase = true) ||
        message.contains("courbe", ignoreCase = true)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = GlucoseColors.Emerald100, modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isNoCgm) Icons.Default.AutoGraph else Icons.Default.Warning,
                    null, Modifier.size(40.dp),
                    tint = GlucoseColors.Emerald600,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = GlucoseColors.Emerald800,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (isNoCgm) {
            Button(
                onClick = onUploadCgm,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.home_glucose_card_cta_upload), fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.glucose_analysis_retry), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun parseCurve(json: String): List<Pair<LocalTime, Double>> = try {
    JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
        val obj = el.asJsonObject
        val t = obj.get("t")?.asString?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return@mapNotNull null
        val mgdl = obj.get("mgdl")?.asDouble ?: return@mapNotNull null
        t to mgdl
    }.sortedBy { it.first }
} catch (_: Exception) {
    emptyList()
}
