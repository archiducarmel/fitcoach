package com.shredcoach.app.presentation.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import com.shredcoach.app.R
import com.shredcoach.app.domain.training.SetMetricFormatter
import com.shredcoach.app.domain.training.SetMetricFormatter.ExerciseKind
import com.shredcoach.app.presentation.common.AnimatedCounter
import com.shredcoach.app.presentation.common.tabularNum
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val nutritionStats by viewModel.nutritionStats.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    var showShareStats by remember { mutableStateOf(false) }
    var showExportStats by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showShareStats) {
        // Génère la share card en fonction du tab sélectionné
        com.shredcoach.app.presentation.share.ShareSheet(
            data = if (selectedTab == 0) buildWorkoutStatsShareData(context, state) else buildNutritionStatsShareData(context, nutritionStats),
            onDismiss = { showShareStats = false },
        )
    }
    if (showExportStats) {
        val exportTitle = if (selectedTab == 0) stringResource(R.string.dashboard_export_workouts_title)
            else stringResource(R.string.dashboard_export_nutrition_title)
        com.shredcoach.app.presentation.share.ExportSheet(
            title = exportTitle,
            onPick = { format ->
                showExportStats = false
                scope.launch {
                    val payload = if (selectedTab == 0) buildWorkoutStatsExportPayload(context, state)
                    else buildNutritionStatsExportPayload(context, nutritionStats)
                    val content = com.shredcoach.app.presentation.share.DataExporter.render(payload, format)
                    val uri = com.shredcoach.app.presentation.share.DataExporter.saveToCache(
                        context, content, format,
                        baseFilename = if (selectedTab == 0) "shredcoach_stats_seances" else "shredcoach_stats_nutrition",
                    )
                    com.shredcoach.app.presentation.share.DataExporter.launchShareIntent(
                        context, uri, format, subject = payload.title,
                    )
                }
            },
            onDismiss = { showExportStats = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showShareStats = true }) {
                        Icon(Icons.Default.Share, stringResource(R.string.dashboard_share_cd))
                    }
                    IconButton(onClick = { showExportStats = true }) {
                        Icon(Icons.Default.FileDownload, stringResource(R.string.dashboard_export_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->

    Column(Modifier.fillMaxSize().padding(pad)) {
        // ─── Tab selector ───
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                Triple(0, stringResource(R.string.dashboard_tab_workouts), Icons.Default.FitnessCenter),
                Triple(1, stringResource(R.string.dashboard_tab_nutrition), Icons.Default.Restaurant)
            ).forEach { (idx, label, icon) ->
                val sel = selectedTab == idx
                Surface(
                    onClick = { selectedTab = idx },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) MaterialTheme.colorScheme.surface else Color.Transparent,
                    tonalElevation = if (sel) 2.dp else 0.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(18.dp), tint = if (sel) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.width(6.dp))
                        Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        if (selectedTab == 1) {
            // ═══ NUTRITION STATS ═══
            NutritionDashboard(nutritionStats, viewModel)
        } else {
        // ═══ SPORT STATS (existant) ═══
      com.shredcoach.app.presentation.common.PullToRefreshBox(
        onRefresh = { viewModel.refresh() }
      ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (state.allTimeWorkouts == 0) {
            Box(Modifier.fillMaxSize()) {
                com.shredcoach.app.presentation.common.EmptyState(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    title = stringResource(R.string.dashboard_empty_title),
                    description = stringResource(R.string.dashboard_empty_desc),
                    ctaLabel = stringResource(R.string.dashboard_empty_cta),
                    ctaIcon = Icons.Default.FitnessCenter,
                    onCtaClick = { navController.navigateUp() }
                )
            }
        } else {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // Index des sections pour scroll rapide
            val sectionIndices = remember { mutableMapOf<String, Int>() }

            // Labels chips — résolus AVANT le LazyColumn (lambda non-@Composable).
            val labelSummary = stringResource(R.string.dashboard_chip_summary)
            val labelRecords = stringResource(R.string.dashboard_chip_records)
            val labelCharts = stringResource(R.string.dashboard_chip_charts)
            val labelTrends = stringResource(R.string.dashboard_chip_trends)
            val labelFreq = stringResource(R.string.dashboard_chip_frequency)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Filtre periode
                item { PeriodFilter(state.selectedPeriod) { viewModel.selectPeriod(it) } }

                // Chips de navigation rapide
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val chips = listOf(labelSummary, labelRecords, labelCharts, labelTrends, labelFreq)
                        items(chips) { label ->
                            Surface(
                                onClick = {
                                    sectionIndices[label]?.let { idx ->
                                        scope.launch { listState.animateScrollToItem(idx) }
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = OrangeVibrant.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.3f))
                            ) {
                                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = OrangeVibrant)
                            }
                        }
                    }
                }

                // ── Résumé ──
                stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_summary)) }
                item { sectionIndices[labelSummary] = 3; SummarySection(state) }
                if (state.comparison != null) {
                    item { ComparisonSection(state.comparison!!) }
                }

                // ── Records ──
                if (state.personalRecords.isNotEmpty()) {
                    stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_records)) }
                    item { sectionIndices[labelRecords] = sectionIndices.size + 4; PersonalRecordsSection(state.personalRecords) }
                }

                // ── 1RM + plateau par exercice ──
                if (state.exerciseProgressions.isNotEmpty()) {
                    stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_progressions)) }
                    item { ExerciseProgressionsSection(state.exerciseProgressions) }
                }

                // ── #16 Body scan timeline (auto-masquée si <2 scans) ──
                item {
                    com.shredcoach.app.presentation.stats.components.BodyScanTimelineCard(
                        onClick = {
                            navController.navigate(
                                com.shredcoach.app.presentation.navigation.Screen.BodyScanner.route
                            )
                        },
                    )
                }

                // ── Graphiques ──
                if (state.weightProgression.isNotEmpty() || state.exercises.isNotEmpty() || state.weeklyVolume.isNotEmpty() || state.muscleDistribution.isNotEmpty()) {
                    stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_charts)) }
                    if (state.weightProgression.isNotEmpty() || state.exercises.isNotEmpty()) {
                        item { sectionIndices[labelCharts] = sectionIndices.size + 5; WeightProgressionSection(state, viewModel) }
                    }
                    if (state.weeklyVolume.isNotEmpty()) {
                        item { WeeklyVolumeSection(state) }
                    }
                    if (state.muscleDistribution.isNotEmpty()) {
                        item { MuscleDistributionSection(state.muscleDistribution) }
                    }
                    if (state.routineBreakdown.isNotEmpty()) {
                        item { RoutineBreakdownSection(state.routineBreakdown, state.selectedPeriod) }
                    }
                }

                // ── Tendances ──
                if (state.trend != null) {
                    stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_trends)) }
                    item { sectionIndices[labelTrends] = sectionIndices.size + 6; TrendSection(state.trend!!) }
                }

                // ── Fréquence ──
                stickyHeader { StickyTitle(stringResource(R.string.dashboard_section_frequency)) }
                item { sectionIndices[labelFreq] = sectionIndices.size + 7; TrainingFrequencySection(state) }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
      }
        } // fin else (sport tab)
    } // fin Column
    }
}

// ═══════════════════════════════════════
// NUTRITION DASHBOARD
// ═══════════════════════════════════════

@Composable
private fun NutritionDashboard(stats: NutritionStatsData, viewModel: StatsViewModel) {
    if (stats.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ─── Tabs période 7j / 30j / 90j ───
        item {
            NutritionPeriodTabs(
                selected = stats.period,
                onSelect = { viewModel.selectNutritionPeriod(it) }
            )
        }

        // ─── Insights coaching auto-générés (top de page, premier impact) ───
        if (stats.insights.isNotEmpty()) {
            item { InsightsPanelCard(stats.insights) }
        }

        // ─── Hero calories moyenne (existant) ───
        item {
            val delta = stats.avgCalories - stats.targetCalories
            val heroColor = when { abs(delta) <= stats.targetCalories * 0.1 -> NeonGreen; else -> OrangeVibrant }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Box(Modifier.fillMaxWidth().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(heroColor.copy(alpha = 0.95f), heroColor.copy(alpha = 0.7f)))
                ).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(22.dp), tint = Color.White)
                            Text(stringResource(R.string.nutri_dashboard_avg_daily, stringResource(stats.period.labelRes)),
                                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Column {
                                Text("${stats.avgCalories}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(stringResource(R.string.nutri_dashboard_kcal_per_day), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                            }
                            Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(if (delta >= 0) "+$delta" else "$delta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(stringResource(R.string.nutri_dashboard_vs_target), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NutriHeroStat("${stats.daysTracked}/${stats.daysInPeriod}", stringResource(R.string.nutri_dashboard_days_tracked))
                            NutriHeroDivider()
                            NutriHeroStat("${stats.complianceDays}/${stats.daysInPeriod}", stringResource(R.string.nutri_dashboard_days_in_target))
                            NutriHeroDivider()
                            NutriHeroStat("${stats.totalScans}", stringResource(R.string.nutri_dashboard_total_scans))
                        }
                    }
                }
            }
        }

        // ─── Comparaison vs période précédente (NEW) ───
        item { PeriodComparisonStrip(stats) }

        // ─── Macros moyennes (existant) ───
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.nutri_dashboard_macros_avg), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NutriMacroRing(stringResource(R.string.nutri_dashboard_macro_proteins), stats.avgProteins.toDouble(), stats.targetProteins, Color(0xFF3B82F6))
                        NutriMacroRing(stringResource(R.string.nutri_dashboard_macro_carbs), stats.avgCarbs.toDouble(), 260, OrangeVibrant)
                        NutriMacroRing(stringResource(R.string.nutri_dashboard_macro_fats), stats.avgFats.toDouble(), 70, Color(0xFFEF4444))
                    }
                    // Protéines par kg
                    if (stats.protPerKg > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.nutri_dashboard_protein_per_kg_label), style = MaterialTheme.typography.bodyMedium)
                            val protColor = when { stats.protPerKg >= 1.6 -> NeonGreen; stats.protPerKg >= 1.2 -> OrangeVibrant; else -> Color(0xFFEF4444) }
                            Text(stringResource(R.string.nutri_dashboard_protein_per_kg_value, String.format(java.util.Locale.getDefault(), "%.1f", stats.protPerKg)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = protColor)
                        }
                        Text(stringResource(R.string.nutri_dashboard_protein_recommended), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // ─── Graphique calories premium (barres si 7j, courbe lissée si 30j+) ───
        if (stats.dailyCaloriesSeries.isNotEmpty()) {
            item { CaloriesPremiumChart(stats) }
        }

        // ─── Donut macro split % (NEW) ───
        item { MacroSplitDonutCard(stats) }

        // ─── Distribution Nutri-Score sur la période (NEW) ───
        if (stats.nutriTotal > 0) {
            item { NutriDistributionCard(stats) }
        }

        // ─── Timeline heures de repas (NEW) ───
        if (stats.mealsByHourBucket.values.sum() > 0) {
            item { MealHoursTimelineCard(stats.mealsByHourBucket) }
        }

        // ─── Jeûne intermittent (cadran 24h) — NEW ───
        if (!stats.fasting.isEmpty && stats.fasting.daysMeasured >= 2) {
            item { FastingWindowCard(stats.fasting) }
        }

        // ─── Score santé moyen (existant) ───
        if (stats.totalScans > 0) {
            item {
                val scoreColor = when { stats.avgHealthScore >= 8 -> NeonGreen; stats.avgHealthScore >= 5 -> OrangeVibrant; else -> Color(0xFFEF4444) }
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(shape = CircleShape, color = scoreColor.copy(alpha = 0.12f), modifier = Modifier.size(56.dp)) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("${stats.avgHealthScore}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = scoreColor)
                            }
                        }
                        Column {
                            Text(stringResource(R.string.nutri_dashboard_avg_health_score), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.nutri_dashboard_on_n_meals, stats.totalScans), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun NutriHeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun NutriHeroDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.25f)))
}

@Composable
private fun NutriMacroRing(label: String, current: Double, target: Int, color: Color) {
    val finalFraction = (current / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    // Anime fraction de 0 vers la cible au mount (1.5s, ease-out)
    val animatedFraction = remember { Animatable(0f) }
    LaunchedEffect(finalFraction) {
        animatedFraction.animateTo(
            targetValue = finalFraction,
            animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing)
        )
    }
    // Description TalkBack consolidée : un seul focus pour le ring entier
    // au lieu de 3 focus séparés (chiffre central + label + objectif).
    // Format : "Protéines : 87g consommés sur 120g objectif, 73 pourcent"
    val a11yTpl = stringResource(R.string.a11y_macro_ring)
    val a11yDesc = remember(current, target, label, a11yTpl) {
        val pct = (finalFraction * 100).toInt()
        a11yTpl.format(label, current.toInt(), target, pct)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = a11yDesc
        }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color.copy(alpha = 0.12f), -90f, 360f, false, style = s)
                drawArc(color, -90f, animatedFraction.value * 360f, false, style = s)
            }
            // Le chiffre central se déroule en parallèle
            AnimatedCounter(
                targetValue = current,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = color,
                formatter = { "${it.toInt()}g" }
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(stringResource(R.string.nutri_dashboard_target_g, target), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun WeeklyCaloriesChart(data: List<Pair<String, Int>>, target: Int) {
    val maxCal = (data.maxOfOrNull { it.second } ?: target).coerceAtLeast(target).toFloat()

    // Description TalkBack du graphe entier : on lit chaque jour avec ses
    // calories, puis l'objectif. L'utilisateur entend une description
    // intelligible au lieu de "Lundi", "Mardi"... séparés par bar.
    val zeroTpl = stringResource(R.string.a11y_day_zero_cals)
    val withTpl = stringResource(R.string.a11y_day_with_cals)
    val chartTpl = stringResource(R.string.a11y_chart_calories_week)
    val chartDesc = remember(data, target, zeroTpl, withTpl, chartTpl) {
        val days = data.joinToString(", ") { (day, cal) ->
            if (cal == 0) zeroTpl.format(day) else withTpl.format(day, cal)
        }
        chartTpl.format(days, target)
    }

    Row(
        Modifier.fillMaxWidth().height(120.dp).semantics(mergeDescendants = true) {
            contentDescription = chartDesc
        },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { idx, (day, cal) ->
            val fraction = (cal / maxCal).coerceIn(0f, 1f)
            val barColor = when {
                cal == 0 -> MaterialTheme.colorScheme.surfaceVariant
                cal > target * 1.1 -> Color(0xFFEF4444)
                cal >= target * 0.9 -> NeonGreen
                else -> OrangeVibrant
            }
            // Animation d'entrée : la barre pousse depuis 0 vers sa hauteur finale,
            // avec un délai indexé pour créer une cascade gauche→droite (50ms par barre).
            val animatedHeight = remember(day, fraction) { Animatable(0f) }
            LaunchedEffect(fraction) {
                kotlinx.coroutines.delay((idx * 50).toLong())
                animatedHeight.animateTo(
                    targetValue = fraction.coerceAtLeast(0.05f),
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cal > 0) Text("$cal", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = barColor)
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
        Text(stringResource(R.string.nutri_dashboard_target_kcal, target), modifier = Modifier.align(Alignment.CenterEnd),
            style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = OrangeVibrant.copy(alpha = 0.6f))
    }
}

// ═══════════════════════════════════════
// FILTRE
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodFilter(selected: TimePeriod, onSelect: (TimePeriod) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TimePeriod.values().toList()) { p ->
            FilterChip(selected = p == selected, onClick = { onSelect(p) },
                label = { Text(stringResource(p.labelRes), fontWeight = if (p == selected) FontWeight.Bold else FontWeight.Normal) })
        }
    }
}

// ═══════════════════════════════════════
// RÉSUMÉ (3 lignes de cards)
// ═══════════════════════════════════════
@Composable
private fun SummarySection(s: StatsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Période
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.FitnessCenter, s.workoutCount, stringResource(R.string.dashboard_summary_sessions_period, stringResource(s.selectedPeriod.labelRes)), OrangeVibrant)
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.MonitorWeight, s.totalVolume, stringResource(R.string.dashboard_summary_volume), NeonGreen, formatter = { fmtVol(it.toDouble()) })
        }
        // Ce mois
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.CalendarMonth, s.monthWorkouts, stringResource(R.string.dashboard_summary_this_month), Color(0xFF8B5CF6))
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.LocalFireDepartment, s.estimatedCalories, stringResource(R.string.dashboard_summary_calories_estim), Color(0xFFEF4444))
        }
        // All time
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.EmojiEvents, s.allTimeWorkouts, stringResource(R.string.dashboard_summary_total_sessions), Color(0xFF3B82F6))
            SCard(Modifier.weight(1f).fillMaxHeight(), Icons.Default.Timer, s.allTimeDuration, stringResource(R.string.dashboard_summary_total_time), Color(0xFF14B8A6), formatter = { fmtDur(it.toLong()) })
        }
    // Extra
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (s.mostTrainedMuscle.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(OrangeVibrant))
                        Text(s.mostTrainedMuscle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(stringResource(R.string.dashboard_summary_no_data), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Text(stringResource(R.string.dashboard_summary_most_trained), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fmtVol(s.allTimeVolume), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.dashboard_summary_total_volume), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }

    // Breakdown temps par categorie (Echauffement / Musculation / Cardio)
    val totalTimeSeconds = s.warmupSeconds + s.strengthSeconds + s.cardioSeconds
    if (totalTimeSeconds > 0) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Timer, null, Modifier.size(20.dp), tint = OrangeVibrant)
                    Text(stringResource(R.string.dashboard_summary_time_breakdown), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                // Barre de progression stackee
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                    val warmupRatio = s.warmupSeconds.toFloat() / totalTimeSeconds
                    val strengthRatio = s.strengthSeconds.toFloat() / totalTimeSeconds
                    val cardioRatio = s.cardioSeconds.toFloat() / totalTimeSeconds
                    if (warmupRatio > 0f) Box(Modifier.fillMaxHeight().weight(warmupRatio).background(Color(0xFFFBBF24)))
                    if (strengthRatio > 0f) Box(Modifier.fillMaxHeight().weight(strengthRatio).background(OrangeVibrant))
                    if (cardioRatio > 0f) Box(Modifier.fillMaxHeight().weight(cardioRatio).background(NeonGreen))
                }
                // Details
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    TimeBreakdownMini(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalFireDepartment,
                        label = stringResource(R.string.dashboard_breakdown_warmup),
                        value = fmtDur(s.warmupSeconds),
                        color = Color(0xFFFBBF24)
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                    TimeBreakdownMini(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FitnessCenter,
                        label = stringResource(R.string.dashboard_breakdown_strength),
                        value = fmtDur(s.strengthSeconds),
                        color = OrangeVibrant
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                    TimeBreakdownMini(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        label = stringResource(R.string.dashboard_breakdown_cardio),
                        value = fmtDur(s.cardioSeconds),
                        color = NeonGreen
                    )
                }
            }
        }
    }
    } // Column fin
}

@Composable
private fun TimeBreakdownMini(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SCard(
    mod: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    targetValue: Number,
    label: String,
    color: Color,
    formatter: (Float) -> String = { it.toInt().toString() }
) {
    Card(
        mod,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxSize().height(IntrinsicSize.Min)) {
            // Accent bar gauche (4dp couleur pleine)
            Box(Modifier.width(4.dp).fillMaxHeight().background(color))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), tint = color)
                    }
                }
                // Valeur animée : compteur qui se déroule de 0 vers la cible
                AnimatedCounter(
                    targetValue = targetValue,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = color,
                    formatter = formatter
                )
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ═══════════════════════════════════════
// COMPARAISON PÉRIODES
// ═══════════════════════════════════════
@Composable
private fun ComparisonSection(c: PeriodComparison) {
    SecTitle(stringResource(R.string.dashboard_comparison_title), Icons.Default.CompareArrows)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Insight
            Card(colors = CardDefaults.cardColors(containerColor = if (c.volumeDelta >= 0) NeonGreen.copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (c.volumeDelta >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.TrendingDown, null,
                        tint = if (c.volumeDelta >= 0) NeonGreen else Color(0xFFEF4444))
                    Text(c.insight, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            // Stats côte à côte
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                CompStat(stringResource(R.string.dashboard_comparison_label_sessions), "${c.previousWorkouts}", "${c.currentWorkouts}", c.workoutDelta)
                CompStat(stringResource(R.string.dashboard_comparison_label_volume), fmtVol(c.previousVolume), fmtVol(c.currentVolume), c.volumeDelta)
            }
        }
    }
}

@Composable
private fun CompStat(label: String, prev: String, curr: String, delta: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        // tnum sur les valeurs comparées : "5" vs "123" sont alignés colonne
        // par colonne. maxLines=1 + softWrap empêche un wrap si la string est
        // longue (ex : "1234.5kg").
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(prev, style = MaterialTheme.typography.bodyMedium.tabularNum(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1, softWrap = false)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Text(curr, style = MaterialTheme.typography.titleMedium.tabularNum(), fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false)
        }
        val color = if (delta >= 0) NeonGreen else Color(0xFFEF4444)
        Text("${if (delta >= 0) "+" else ""}${delta.toInt()}%", style = MaterialTheme.typography.labelMedium.tabularNum(), fontWeight = FontWeight.Bold, color = color,
            maxLines = 1, softWrap = false)
    }
}

// ═══════════════════════════════════════
// RECORDS PERSONNELS
// ═══════════════════════════════════════
@Composable
private fun PersonalRecordsSection(records: List<PRDisplay>) {
    SecTitle(stringResource(R.string.dashboard_records_title), Icons.Default.EmojiEvents)
    records.take(5).forEachIndexed { i, pr ->
        Card(colors = CardDefaults.cardColors(containerColor = if (i == 0) Color(0xFFFFD700).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(when (i) { 0 -> Color(0xFFFFD700); 1 -> Color(0xFFC0C0C0); 2 -> Color(0xFFCD7F32); else -> MaterialTheme.colorScheme.surfaceVariant }),
                    contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (i < 3) Color.White else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                // Sous-ligne contextuelle (kind-aware) :
                //  - WEIGHTED        : "1RM: 110 kg"
                //  - BODYWEIGHT_REPS : "Poids du corps" (ou "+10 kg" si lesté)
                //  - TIMED           : "Tenue maximale"
                val subtitleWeighted = pr.estimated1RM?.let { stringResource(R.string.dashboard_records_subtitle_1rm, it) } ?: ""
                val subtitleBodyweightLoaded = if (pr.weight > 0.0) stringResource(R.string.dashboard_records_subtitle_loaded, SetMetricFormatter.formatWeight(pr.weight)) else stringResource(R.string.dashboard_records_subtitle_bodyweight)
                val subtitleTimed = stringResource(R.string.dashboard_records_subtitle_max_hold)
                val subtitle = when (pr.kind) {
                    ExerciseKind.WEIGHTED -> subtitleWeighted
                    ExerciseKind.BODYWEIGHT_REPS -> subtitleBodyweightLoaded
                    ExerciseKind.TIMED -> subtitleTimed
                }
                Column(Modifier.weight(1f)) {
                    Text(pr.exerciseName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                // Bloc de droite : valeur principale + détail kind-aware
                Column(horizontalAlignment = Alignment.End) {
                    when (pr.kind) {
                        ExerciseKind.WEIGHTED -> {
                            Text("${SetMetricFormatter.formatWeight(pr.weight)} kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                            Text("× ${pr.reps}", style = MaterialTheme.typography.labelSmall)
                        }
                        ExerciseKind.BODYWEIGHT_REPS -> {
                            Text("${pr.reps}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                            Text(stringResource(R.string.dashboard_records_unit_reps), style = MaterialTheme.typography.labelSmall)
                        }
                        ExerciseKind.TIMED -> {
                            Text(SetMetricFormatter.formatDuration(pr.reps), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                            Text(stringResource(R.string.dashboard_records_unit_hold), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// PROGRESSION 1RM + PLATEAU PAR EXERCICE
// ═══════════════════════════════════════
/**
 * Carrousel horizontal des top exercices : 1RM estimé, état (progression/
 * stable/plateau), sparkline des N dernières séances, badge PR si récent.
 *
 * Le scroll horizontal est délibéré (vs grid 2 colonnes) : il met l'accent
 * sur la lecture séquentielle "comment je vais sur tel exo, puis tel autre"
 * plutôt qu'une comparaison brute. Inspiré des "Now Playing" et "Up Next"
 * d'Apple Music — efficace pour engager.
 */
@Composable
private fun ExerciseProgressionsSection(entries: List<ExerciseProgressionEntry>) {
    SecTitle(stringResource(R.string.dashboard_progression_title), Icons.AutoMirrored.Filled.TrendingUp)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(entries) { entry ->
            com.shredcoach.app.presentation.stats.components.ExerciseProgressionCard(
                exerciseName = entry.exerciseName,
                progression = entry.progression,
            )
        }
    }
}

// ═══════════════════════════════════════
// ÉVOLUTION POIDS
// ═══════════════════════════════════════
@Composable
private fun WeightProgressionSection(state: StatsState, viewModel: StatsViewModel) {
    SecTitle(stringResource(R.string.dashboard_section_weight_progression), Icons.AutoMirrored.Filled.TrendingUp)
    var expanded by remember { mutableStateOf(false) }
    @OptIn(ExperimentalMaterial3Api::class)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = state.selectedExerciseName, onValueChange = {}, readOnly = true,
            modifier = Modifier.fillMaxWidth().then(@OptIn(ExperimentalMaterial3Api::class) Modifier.menuAnchor()),
            trailingIcon = { @OptIn(ExperimentalMaterial3Api::class) ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyMedium)
        @OptIn(ExperimentalMaterial3Api::class)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.exercises.forEach { ex ->
                DropdownMenuItem(text = { Text(ex.name, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { viewModel.selectExercise(ex.id, ex.name); expanded = false })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (state.weightProgression.size >= 2) {
        LineChart(state.weightProgression.map { it.weight.toFloat() },
            state.weightProgression.map { "${it.date.dayOfMonth}/${it.date.monthValue}" },
            Modifier.fillMaxWidth().height(200.dp), OrangeVibrant)
    } else {
        EmptyChart(stringResource(R.string.dashboard_chart_no_data))
    }
}

// ═══════════════════════════════════════
// TENDANCES & PRÉDICTIONS
// ═══════════════════════════════════════
@Composable
private fun TrendSection(trend: TrendData) {
    SecTitle(stringResource(R.string.dashboard_section_trends_predictions), Icons.Default.AutoGraph)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Indicateurs
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.stats_trend_kg_per_week, "%.1f".format(trend.slope)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (trend.slope > 0) NeonGreen else if (trend.slope < -0.5) Color(0xFFEF4444) else OrangeVibrant)
                    Text(stringResource(R.string.dashboard_trend_progression), style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.0f kg".format(trend.projectedWeight4Weeks), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                    Text(stringResource(R.string.dashboard_trend_projected_4w), style = MaterialTheme.typography.labelSmall)
                }
            }
            // Alerte plateau
            if (trend.isPlateauing) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B))
                        Text(stringResource(R.string.dashboard_trend_plateau, trend.plateauWeeks), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Conseil
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(trend.suggestion, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// VOLUME HEBDOMADAIRE
// ═══════════════════════════════════════
@Composable
private fun WeeklyVolumeSection(state: StatsState) {
    SecTitle(stringResource(R.string.dashboard_section_weekly_volume), Icons.Default.BarChart)
    // Delta vs semaine précédente
    if (abs(state.volumeChangePercent) > 0.1f) {
        val color = if (state.volumeChangePercent >= 0) NeonGreen else Color(0xFFEF4444)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (state.volumeChangePercent >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.TrendingDown, null, Modifier.size(18.dp), tint = color)
            Text(stringResource(R.string.dashboard_volume_change_vs_prev_week, if (state.volumeChangePercent >= 0) "+" else "", state.volumeChangePercent.toInt()),
                style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
    BarChart(state.weeklyVolume.map { it.volume.toFloat() }, state.weeklyVolume.map { it.label }, Modifier.fillMaxWidth().height(200.dp), NeonGreen)
}

// ═══════════════════════════════════════
// RÉPARTITION MUSCULAIRE
// ═══════════════════════════════════════
@Composable
private fun MuscleDistributionSection(data: List<MuscleSlice>) {
    SecTitle(stringResource(R.string.dashboard_section_muscle_distribution), Icons.Default.PieChart)
    val colors = listOf(OrangeVibrant, NeonGreen, Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF14B8A6), Color(0xFF6366F1), Color(0xFFEF4444), Color(0xFF10B981), Color(0xFF6B7280), Color(0xFFF97316))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(140.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                var startAngle = -90f
                data.forEachIndexed { i, slice ->
                    val sweep = slice.percentage * 360f
                    drawArc(colors[i % colors.size], startAngle, sweep, false, style = Stroke(28f, cap = StrokeCap.Butt),
                        topLeft = Offset(14f, 14f), size = Size(size.width - 28f, size.height - 28f))
                    startAngle += sweep
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            data.take(8).forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Text(s.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(s.percentage * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// VOLUME PAR ROUTINE (Push, Pull, Legs, …)
// ═══════════════════════════════════════
/**
 * Stack-bar horizontale + légende. Premium FAANG : tabular nums sur les
 * pourcentages, ellipsis sur les noms longs, hauteur fixe pour shimmer-safe.
 *
 * Affiche jusqu'à 6 routines max dans la légende — au-delà c'est rare et la
 * stack-bar sature visuellement, donc on regroupe le reste en "Autres".
 */
@Composable
private fun RoutineBreakdownSection(data: List<RoutineSlice>, period: TimePeriod) {
    val totalSessions = data.sumOf { it.sessionCount }
    val totalVolumeKg = data.sumOf { it.volume }
    SecTitle(stringResource(R.string.dashboard_section_routine_breakdown, stringResource(period.labelRes)), Icons.Default.Whatshot)
    val colors = listOf(OrangeVibrant, Color(0xFF3B82F6), NeonGreen, Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF14B8A6), Color(0xFF6366F1))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Hero numérique : total séances + volume cumulé
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.dashboard_freq_sessions_count, totalSessions), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(stringResource(R.string.dashboard_freq_total_volume_kg, totalVolumeKg.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.10f)) {
                    Text(if (data.size > 1) stringResource(R.string.dashboard_freq_types_plural, data.size) else stringResource(R.string.dashboard_freq_types_singular, data.size),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = OrangeVibrant)
                }
            }

            // Stack bar (8dp height, rounded)
            Row(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                data.forEachIndexed { i, slice ->
                    if (slice.percentage > 0f) {
                        Box(
                            Modifier
                                .weight(slice.percentage.coerceAtLeast(0.5f))
                                .fillMaxHeight()
                                .background(colors[i % colors.size])
                        )
                    }
                }
            }

            // Légende — top 6 routines
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.take(6).forEachIndexed { i, slice ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                        Text(slice.icon, fontSize = 14.sp)
                        Text(
                            slice.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${slice.sessionCount}× · ${slice.volume.toInt()} kg",
                            style = MaterialTheme.typography.labelSmall.tabularNum(),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                        Text(
                            "${slice.percentage.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.tabularNum(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                }
                if (data.size > 6) {
                    val extra = data.drop(6)
                    val extraVol = extra.sumOf { it.volume }.toInt()
                    val extraSessions = extra.sumOf { it.sessionCount }
                    val extraPct = extra.sumOf { it.percentage.toDouble() }.toInt()
                    Text(
                        stringResource(R.string.dashboard_freq_more_routines, extra.size, extraSessions, extraVol, extraPct),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// FRÉQUENCE & HEATMAP
// ═══════════════════════════════════════
@Composable
private fun TrainingFrequencySection(state: StatsState) {
    SecTitle(stringResource(R.string.dashboard_section_training_frequency), Icons.Default.CalendarMonth)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FrequencyCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = Icons.Default.LocalFireDepartment,
            value = "${state.currentStreak}",
            unit = stringResource(if (state.currentStreak <= 1) R.string.dashboard_freq_unit_day else R.string.dashboard_freq_unit_days),
            label = stringResource(R.string.dashboard_freq_label_current_streak),
            color = OrangeVibrant
        )
        FrequencyCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = Icons.Default.EmojiEvents,
            value = "${state.longestStreak}",
            unit = stringResource(if (state.longestStreak <= 1) R.string.dashboard_freq_unit_day else R.string.dashboard_freq_unit_days),
            label = stringResource(R.string.dashboard_freq_label_record),
            color = NeonGreen
        )
        FrequencyCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            value = "${state.weeklyCompliance.toInt()}",
            unit = "%",
            label = stringResource(R.string.dashboard_freq_label_compliance),
            color = Color(0xFF3B82F6)
        )
    }
    Spacer(Modifier.height(8.dp))
    HeatmapChart(state.trainingDays, 8)
}

@Composable
private fun FrequencyCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    unit: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = color)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                androidx.compose.animation.Crossfade(targetState = value, animationSpec = androidx.compose.animation.core.tween(400), label = "freq") { v ->
                    Text(v, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
                }
                Text(unit, style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ═══════════════════════════════════════
// GRAPHIQUES CUSTOM
// ═══════════════════════════════════════
@Composable
private fun LineChart(points: List<Float>, labels: List<String>, modifier: Modifier, lineColor: Color) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp)) {
        if (points.size < 2) return@Canvas
        val maxVal = points.maxOrNull() ?: return@Canvas; val minVal = points.minOrNull() ?: return@Canvas
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val pL = 50f; val pB = 30f; val pT = 20f; val pR = 16f
        val cW = size.width - pL - pR; val cH = size.height - pB - pT
        val stepX = cW / (points.size - 1).coerceAtLeast(1)
        // Grid
        for (i in 0..3) {
            val y = pT + cH * (1f - i / 3f)
            drawLine(Color.White.copy(alpha = 0.1f), Offset(pL, y), Offset(size.width - pR, y))
            drawContext.canvas.nativeCanvas.drawText("%.0f".format(minVal + range * i / 3f), 4f, y + 5f,
                android.graphics.Paint().apply { color = 0x99FFFFFF.toInt(); textSize = 22f })
        }
        // Line
        val path = Path()
        points.forEachIndexed { i, v -> val x = pL + i * stepX; val y = pT + cH * (1f - (v - minVal) / range); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, lineColor, style = Stroke(4f, cap = StrokeCap.Round))
        // Trend line (dashed)
        if (points.size >= 3) {
            val first = pT + cH * (1f - (points.first() - minVal) / range)
            val last = pT + cH * (1f - (points.last() - minVal) / range)
            drawLine(lineColor.copy(alpha = 0.3f), Offset(pL, first), Offset(pL + (points.size - 1) * stepX, last),
                strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
        // Points
        points.forEachIndexed { i, v -> val x = pL + i * stepX; val y = pT + cH * (1f - (v - minVal) / range)
            drawCircle(lineColor, 6f, Offset(x, y)); drawCircle(Color.White, 3f, Offset(x, y)) }
    }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier, barColor: Color) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val pL = 16f; val pB = 30f; val pT = 20f; val pR = 16f
        val cW = size.width - pL - pR; val cH = size.height - pB - pT
        val bW = (cW / values.size) * 0.7f; val gap = (cW / values.size) * 0.3f
        values.forEachIndexed { i, v ->
            val barH = cH * (v / maxVal); val x = pL + i * (bW + gap); val y = pT + cH - barH
            drawRoundRect(barColor.copy(alpha = 0.15f), Offset(x, pT), Size(bW, cH), CornerRadius(8f))
            drawRoundRect(barColor, Offset(x, y), Size(bW, barH), CornerRadius(8f))
            if (v > 0) drawContext.canvas.nativeCanvas.drawText(fmtVolShort(v), x + bW / 2, y - 8f,
                android.graphics.Paint().apply { color = 0xCCFFFFFF.toInt(); textSize = 20f; textAlign = android.graphics.Paint.Align.CENTER })
        }
    }
}

@Composable
private fun HeatmapChart(days: Map<LocalDate, Int>, weeks: Int) {
    val today = LocalDate.now(); val start = today.minusWeeks(weeks.toLong())
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        var cur = start
        while (cur <= today) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { d ->
                    val date = cur.plusDays(d.toLong())
                    if (date <= today) {
                        val count = days[date] ?: 0
                        val intensity = when (count) { 0 -> 0f; 1 -> 0.35f; 2 -> 0.65f; else -> 1f }
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (count > 0) NeonGreen.copy(alpha = intensity) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)))
                    } else Spacer(Modifier.size(16.dp))
                }
            }
            cur = cur.plusWeeks(1)
        }
    }
}

@Composable
private fun EmptyChart(msg: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

@Composable
private fun StickyTitle(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = OrangeVibrant
        )
    }
}

// ═══════════════════════════════════════
// UTILITAIRES
// ═══════════════════════════════════════
@Composable
private fun SecTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = OrangeVibrant)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

private fun fmtVol(v: Double): String = when { v >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM",v / 1_000_000); v >= 1_000 -> String.format(java.util.Locale.US, "%.1fk",v / 1_000); else -> "%.0f kg".format(v) }
private fun fmtDur(s: Long): String { val h = s / 3600; return if (h > 0) "${h}h" else "${s / 60}min" }
private fun fmtVolShort(v: Float): String = when { v >= 1000 -> "%.0fk".format(v / 1000); else -> "%.0f".format(v) }

// ──────────────────────────────────────────────────────────
// Builders : transforment les états ViewModel en payloads share/export
// ──────────────────────────────────────────────────────────

private fun periodLabel(context: android.content.Context, period: TimePeriod): String = when (period) {
    TimePeriod.WEEK -> context.getString(R.string.period_label_week)
    TimePeriod.MONTH -> context.getString(R.string.period_label_month)
    TimePeriod.QUARTER -> context.getString(R.string.period_label_quarter)
    TimePeriod.YEAR -> context.getString(R.string.period_label_year)
    TimePeriod.ALL -> context.getString(R.string.period_label_all)
}

private fun buildWorkoutStatsShareData(
    context: android.content.Context,
    state: StatsState,
): com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate {
    return com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate(
        title = context.getString(R.string.share_workouts_title),
        subtitle = periodLabel(context, state.selectedPeriod),
        accentEmoji = "💪",
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_sessions),
                value = state.workoutCount.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_volume),
                value = state.totalVolume.toInt().toString(),
                unit = context.getString(R.string.share_unit_kg),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_reps),
                value = state.totalReps.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_duration),
                value = (state.totalDuration / 60).toString(),
                unit = context.getString(R.string.share_unit_min),
            ),
        ),
    )
}

private fun buildNutritionStatsShareData(
    context: android.content.Context,
    stats: NutritionStatsData,
): com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate {
    return com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate(
        title = context.getString(R.string.share_nutrition_title),
        subtitle = periodLabel(context, stats.period),
        accentEmoji = "🥗",
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_kcal_per_day),
                value = stats.avgCalories.toString(),
                unit = context.getString(R.string.share_unit_kcal),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_proteins),
                value = stats.avgProteins.toString(),
                unit = context.getString(R.string.share_unit_g),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_compliance),
                value = "${stats.complianceDays}/${stats.daysInPeriod}",
                unit = context.getString(R.string.share_unit_days_short),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = context.getString(R.string.share_metric_health_score),
                value = stats.avgHealthScore.toString(),
                unit = context.getString(R.string.share_unit_per_100),
            ),
        ),
    )
}

private fun buildWorkoutStatsExportPayload(
    context: android.content.Context,
    state: StatsState,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    val secondsUnit = context.getString(R.string.export_unit_seconds)
    val kgUnit = context.getString(R.string.share_unit_kg)
    val kcalUnit = context.getString(R.string.share_unit_kcal)
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = context.getString(R.string.export_workouts_title),
        description = context.getString(R.string.export_period_prefix, periodLabel(context, state.selectedPeriod)),
        columns = listOf(
            context.getString(R.string.export_columns_metric),
            context.getString(R.string.export_columns_value),
            context.getString(R.string.export_columns_unit),
        ),
        rows = listOf(
            listOf(context.getString(R.string.export_row_workouts_count), state.workoutCount.toString(), ""),
            listOf(context.getString(R.string.export_row_total_volume), state.totalVolume.toInt().toString(), kgUnit),
            listOf(context.getString(R.string.export_row_total_duration), state.totalDuration.toString(), secondsUnit),
            listOf(context.getString(R.string.export_row_total_reps), state.totalReps.toString(), ""),
            listOf(context.getString(R.string.export_row_estimated_calories), state.estimatedCalories.toString(), kcalUnit),
            listOf(context.getString(R.string.export_row_month_workouts), state.monthWorkouts.toString(), ""),
            listOf(context.getString(R.string.export_row_month_volume), state.monthVolume.toInt().toString(), kgUnit),
            listOf(context.getString(R.string.export_row_alltime_workouts), state.allTimeWorkouts.toString(), ""),
            listOf(context.getString(R.string.export_row_alltime_volume), state.allTimeVolume.toInt().toString(), kgUnit),
            listOf(context.getString(R.string.export_row_alltime_duration), state.allTimeDuration.toString(), secondsUnit),
            listOf(context.getString(R.string.export_row_most_trained_muscle), state.mostTrainedMuscle, ""),
            listOf(context.getString(R.string.export_row_most_done_exercise), state.mostDoneExercise, ""),
            listOf(context.getString(R.string.export_row_warmup_time), state.warmupSeconds.toString(), secondsUnit),
            listOf(context.getString(R.string.export_row_cardio_time), state.cardioSeconds.toString(), secondsUnit),
            listOf(context.getString(R.string.export_row_strength_time), state.strengthSeconds.toString(), secondsUnit),
        ),
        summary = listOf(
            context.getString(R.string.export_summary_period) to periodLabel(context, state.selectedPeriod),
            context.getString(R.string.export_summary_total_workouts) to state.workoutCount.toString(),
            context.getString(R.string.export_summary_total_volume) to context.getString(
                R.string.export_summary_total_volume_value, state.totalVolume.toInt()
            ),
        ),
    )
}

private fun buildNutritionStatsExportPayload(
    context: android.content.Context,
    stats: NutritionStatsData,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    val gUnit = context.getString(R.string.share_unit_g)
    val kcalUnit = context.getString(R.string.share_unit_kcal)
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = context.getString(R.string.export_nutrition_title),
        description = context.getString(R.string.export_period_prefix, periodLabel(context, stats.period)),
        columns = listOf(
            context.getString(R.string.export_columns_metric),
            context.getString(R.string.export_columns_value),
            context.getString(R.string.export_columns_unit),
        ),
        rows = listOf(
            listOf(context.getString(R.string.export_row_avg_calories), stats.avgCalories.toString(), kcalUnit),
            listOf(context.getString(R.string.export_row_avg_proteins), stats.avgProteins.toString(), gUnit),
            listOf(context.getString(R.string.export_row_avg_carbs), stats.avgCarbs.toString(), gUnit),
            listOf(context.getString(R.string.export_row_avg_fats), stats.avgFats.toString(), gUnit),
            listOf(context.getString(R.string.export_row_days_tracked), stats.daysTracked.toString(), "/${stats.daysInPeriod}"),
            listOf(context.getString(R.string.export_row_target_calories), stats.targetCalories.toString(), kcalUnit),
            listOf(context.getString(R.string.export_row_target_proteins), stats.targetProteins.toString(), gUnit),
            listOf(context.getString(R.string.export_row_compliance_days), stats.complianceDays.toString(), ""),
            listOf(context.getString(R.string.export_row_total_scans), stats.totalScans.toString(), ""),
            listOf(context.getString(R.string.export_row_avg_health_score), stats.avgHealthScore.toString(), "/100"),
            listOf(context.getString(R.string.export_row_protein_per_kg), "%.2f".format(stats.protPerKg), "g/kg"),
            listOf(context.getString(R.string.export_row_kcal_proteins_pct), "%.0f".format(stats.proteinKcalPct), "%"),
            listOf(context.getString(R.string.export_row_kcal_carbs_pct), "%.0f".format(stats.carbsKcalPct), "%"),
            listOf(context.getString(R.string.export_row_kcal_fats_pct), "%.0f".format(stats.fatsKcalPct), "%"),
            listOf(context.getString(R.string.export_row_nutri_a), stats.nutriCountA.toString(), ""),
            listOf(context.getString(R.string.export_row_nutri_b), stats.nutriCountB.toString(), ""),
            listOf(context.getString(R.string.export_row_nutri_c), stats.nutriCountC.toString(), ""),
            listOf(context.getString(R.string.export_row_nutri_d), stats.nutriCountD.toString(), ""),
            listOf(context.getString(R.string.export_row_nutri_e), stats.nutriCountE.toString(), ""),
        ),
        summary = listOf(
            context.getString(R.string.export_summary_period) to periodLabel(context, stats.period),
            context.getString(R.string.export_summary_kcal_per_day) to context.getString(
                R.string.export_summary_kcal_per_day_value, stats.avgCalories
            ),
            context.getString(R.string.export_summary_compliance) to context.getString(
                R.string.export_summary_compliance_value, stats.complianceDays, stats.daysInPeriod
            ),
        ),
    )
}
