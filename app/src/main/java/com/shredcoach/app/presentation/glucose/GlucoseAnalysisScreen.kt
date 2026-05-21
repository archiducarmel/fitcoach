package com.shredcoach.app.presentation.glucose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import com.shredcoach.app.domain.glucose.GlucoseAnalysisEngine
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
                state.isLoading && state.analysis == null -> AnalysisSkeleton()
                state.errorReason != null && state.analysis == null ->
                    TypedErrorScreen(
                        reason = state.errorReason!!,
                        message = state.errorMessage.orEmpty(),
                        onRetry = { viewModel.reanalyze() },
                        onUploadCgm = { navController.navigate(Screen.GlucoseEntry.createRoute(state.date)) },
                        onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    )
                state.analysis != null -> AnalysisContent(
                    state = state,
                    onOpenDrGlykos = { navController.navigate(Screen.DrGlykosChat.route) },
                )
                else -> AnalysisSkeleton()
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
                    meals = state.meals,
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
    meals: List<com.shredcoach.app.data.local.entity.MealLogEntity>,
) {
    val chartCurve = remember(curve) {
        curve.map { ChartGlucosePoint(it.first, it.second) }
    }
    // Meal markers : couleur dérivée du pic postprandial 30-90 min
    val chartMeals = remember(meals, curve) {
        meals.mapNotNull { meal ->
            val t = meal.time ?: return@mapNotNull null
            val responsePeak = curve
                .filter { (time, _) ->
                    val delta = time.toSecondOfDay() - t.toSecondOfDay()
                    delta in (30 * 60)..(90 * 60)
                }
                .maxOfOrNull { it.second }
            ChartMealMarker(time = t, responsePeak = responsePeak)
        }
    }
    // Insight markers : couleur dérivée du verdict
    val chartInsights = remember(insights) {
        insights.mapNotNull { ins ->
            val t = ins.time ?: return@mapNotNull null
            val color = when (ins.verdict) {
                InsightVerdict.POSITIVE -> GlucoseColors.InRange
                InsightVerdict.NEUTRAL -> GlucoseColors.Warning
                InsightVerdict.CONCERN -> GlucoseColors.Critical
            }
            ChartInsightMarker(time = t, color = color)
        }
    }

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
            // Chart unifié : courbe + meal markers + insight markers + x-axis hours
            GlucoseTimelineChart(
                curve = chartCurve,
                meals = chartMeals,
                insights = chartInsights,
                height = 200.dp,
            )
            // Légende : si on a des repas, on montre les couleurs repas. Sinon
            // on bascule sur la légende d'insights (verdict).
            GlucoseChartLegend(
                showMealMarkers = chartMeals.isNotEmpty(),
                showInsightMarkers = chartMeals.isEmpty() && chartInsights.isNotEmpty(),
            )
        }
    }
}

// CurveCanvas et LegendDot supprimés — remplacés par GlucoseTimelineChart
// + GlucoseChartLegend (shared via GlucoseTimelineChart.kt).

@Suppress("unused")
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

/**
 * Skeleton loader mimant la structure du contenu final (hero verdict +
 * 3 insight cards placeholder + advice). Animation pulsation alpha 0.4↔0.8
 * pour effet "shimmer" léger, plus premium qu'un spinner générique.
 *
 * **Pourquoi un skeleton plutôt qu'un spinner** : un skeleton donne
 * l'impression que l'app "se prépare à afficher quelque chose", alors qu'un
 * spinner suggère "attente passive". L'UX perçue est ~20% plus rapide pour
 * la même durée réelle (effet placebo bien documenté).
 */
@Composable
private fun AnalysisSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Skeleton hero verdict (gradient + 2 lignes de placeholder)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald100.copy(alpha = pulseAlpha)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonLine(width = 120.dp, height = 14.dp, pulseAlpha = pulseAlpha)
                SkeletonLine(width = 200.dp, height = 28.dp, pulseAlpha = pulseAlpha)
                Spacer(Modifier.height(2.dp))
                SkeletonLine(width = null, height = 12.dp, pulseAlpha = pulseAlpha)
                SkeletonLine(width = 240.dp, height = 12.dp, pulseAlpha = pulseAlpha)
            }
        }
        // Skeleton 3 insight cards
        repeat(3) { idx ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        GlucoseColors.Emerald200.copy(alpha = 0.3f * pulseAlpha),
                        RoundedCornerShape(18.dp),
                    ),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlucoseColors.Emerald100.copy(alpha = pulseAlpha))
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonLine(width = 60.dp, height = 10.dp, pulseAlpha = pulseAlpha)
                        SkeletonLine(width = 180.dp, height = 14.dp, pulseAlpha = pulseAlpha)
                        SkeletonLine(width = null, height = 10.dp, pulseAlpha = pulseAlpha)
                        SkeletonLine(width = 220.dp, height = 10.dp, pulseAlpha = pulseAlpha)
                    }
                }
            }
            if (idx < 2) Spacer(Modifier.height(0.dp))  // gap géré par verticalArrangement
        }
        Spacer(Modifier.weight(1f))
        // Label en bas pour communiquer que c'est intentionnel ("Dr. Glykos analyse…")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = GlucoseColors.Emerald600,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.glucose_analysis_loading_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlucoseColors.Emerald800,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkeletonLine(
    width: androidx.compose.ui.unit.Dp?,
    height: androidx.compose.ui.unit.Dp,
    pulseAlpha: Float,
) {
    val mod = if (width == null) Modifier.fillMaxWidth() else Modifier.width(width)
    Box(
        mod
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(GlucoseColors.Emerald100.copy(alpha = pulseAlpha))
    )
}

/**
 * Écran d'erreur typé. La VM expose un [GlucoseAnalysisEngine.ErrorReason]
 * structuré → on switch dessus pour choisir l'icône, le titre, et le CTA
 * approprié. Plus robuste que de parser le message d'erreur.
 *
 * Cas couverts :
 *  - NO_GLUCOSE_LOG → "Pas de CGM" + CTA upload
 *  - NO_CURVE_DATA → "Image seule, pas de courbe" + CTA upload nouvelle
 *  - NO_API_KEY → "Configure ta clé Gemini" + CTA Settings
 *  - LLM_FAILURE → "Réseau / Gemini down" + CTA retry
 *  - PARSE_FAILURE → "Réponse malformée" + CTA retry
 */
@Composable
private fun TypedErrorScreen(
    reason: GlucoseAnalysisEngine.ErrorReason,
    message: String,
    onRetry: () -> Unit,
    onUploadCgm: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val icon = when (reason) {
        GlucoseAnalysisEngine.ErrorReason.NO_GLUCOSE_LOG -> Icons.Default.AutoGraph
        GlucoseAnalysisEngine.ErrorReason.NO_CURVE_DATA -> Icons.Default.AutoGraph
        GlucoseAnalysisEngine.ErrorReason.NO_API_KEY -> Icons.Default.MedicalServices
        GlucoseAnalysisEngine.ErrorReason.LLM_FAILURE -> Icons.Default.Refresh
        GlucoseAnalysisEngine.ErrorReason.PARSE_FAILURE -> Icons.Default.Warning
    }
    val titleRes = when (reason) {
        GlucoseAnalysisEngine.ErrorReason.NO_GLUCOSE_LOG -> R.string.glucose_analysis_error_no_log_title
        GlucoseAnalysisEngine.ErrorReason.NO_CURVE_DATA -> R.string.glucose_analysis_error_no_curve_title
        GlucoseAnalysisEngine.ErrorReason.NO_API_KEY -> R.string.glucose_analysis_error_no_api_key_title
        GlucoseAnalysisEngine.ErrorReason.LLM_FAILURE -> R.string.glucose_analysis_error_llm_title
        GlucoseAnalysisEngine.ErrorReason.PARSE_FAILURE -> R.string.glucose_analysis_error_parse_title
    }
    val descRes = when (reason) {
        GlucoseAnalysisEngine.ErrorReason.NO_GLUCOSE_LOG -> R.string.glucose_analysis_error_no_log_desc
        GlucoseAnalysisEngine.ErrorReason.NO_CURVE_DATA -> R.string.glucose_analysis_error_no_curve_desc
        GlucoseAnalysisEngine.ErrorReason.NO_API_KEY -> R.string.glucose_analysis_error_no_api_key_desc
        GlucoseAnalysisEngine.ErrorReason.LLM_FAILURE -> R.string.glucose_analysis_error_llm_desc
        GlucoseAnalysisEngine.ErrorReason.PARSE_FAILURE -> R.string.glucose_analysis_error_parse_desc
    }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = GlucoseColors.Emerald100, modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(40.dp), tint = GlucoseColors.Emerald600)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = GlucoseColors.Emerald800,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = GlucoseColors.Emerald800.copy(alpha = 0.75f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
        )
        // Message technique en mode debug uniquement — utile pour diagnostiquer.
        if (com.shredcoach.app.BuildConfig.DEBUG && message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Debug: $message",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        // CTA principal selon le type d'erreur
        when (reason) {
            GlucoseAnalysisEngine.ErrorReason.NO_GLUCOSE_LOG,
            GlucoseAnalysisEngine.ErrorReason.NO_CURVE_DATA -> {
                Button(
                    onClick = onUploadCgm,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Default.AutoGraph, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_glucose_card_cta_upload), fontWeight = FontWeight.Bold)
                }
            }
            GlucoseAnalysisEngine.ErrorReason.NO_API_KEY -> {
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(R.string.glucose_analysis_error_no_api_key_cta), fontWeight = FontWeight.Bold)
                }
            }
            GlucoseAnalysisEngine.ErrorReason.LLM_FAILURE,
            GlucoseAnalysisEngine.ErrorReason.PARSE_FAILURE -> {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.glucose_analysis_retry), fontWeight = FontWeight.Bold)
                }
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
