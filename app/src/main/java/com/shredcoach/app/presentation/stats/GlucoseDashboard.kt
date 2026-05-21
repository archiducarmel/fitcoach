package com.shredcoach.app.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.R
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary
import com.shredcoach.app.presentation.glucose.ChartGlucosePoint
import com.shredcoach.app.presentation.glucose.ChartMealMarker
import com.shredcoach.app.presentation.glucose.GlucoseChartLegend
import com.shredcoach.app.presentation.glucose.GlucoseColors
import com.shredcoach.app.presentation.glucose.GlucoseHeroKpiTile
import com.shredcoach.app.presentation.glucose.GlucoseHeroSurface
import com.shredcoach.app.presentation.glucose.GlucoseHistoryViewModel
import com.shredcoach.app.presentation.glucose.GlucoseHistoryWindow
import com.shredcoach.app.presentation.glucose.GlucoseSectionHeader
import com.shredcoach.app.presentation.glucose.GlucoseStatus
import com.shredcoach.app.presentation.glucose.GlucoseStatusPill
import com.shredcoach.app.presentation.glucose.GlucoseTimelineChart
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Dashboard glycémique premium (Stats → tab Glycémie).
 *
 * Réutilise GlucoseHistoryViewModel pour la donnée. Le focus visuel ici :
 * un Hero gradient en haut (vue d'ensemble premium) + cards thématiques
 * pour pattern / trend / hypos + liste compacte des derniers logs.
 */
@Composable
fun GlucoseDashboard(
    onOpenDrGlykos: () -> Unit,
    onUploadCgm: () -> Unit,
    viewModel: GlucoseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ─── Window switcher ──────────────────────────────────
        item { WindowSwitcher(state.window, viewModel::setWindow) }

        val summary = state.summary
        if (summary == null || summary.daysCovered == 0) {
            item { EmptyState(onUploadCgm) }
        } else {
            // Hero curve TODAY si on a un log + courbe parsée
            state.todayLog?.let { log ->
                if (!log.glucoseMgdlCurveJson.isNullOrBlank()) {
                    item { TodayCurveHeroCard(log = log, meals = state.todayMeals) }
                }
            }
            item { HeroKpiCard(summary, onOpenDrGlykos) }
            // TIR breakdown bar : visualisation horizontale TBR/TIR/TAR
            item { TirBreakdownCard(summary) }
            // Streak optimal + best day en row de 2 cards
            item {
                StreakAndBestDayRow(
                    streakDays = state.optimalStreakDays,
                    bestDay = state.bestDay,
                )
            }
            item { PatternBadge(summary) }
            summary.trendMgdlPerWeek?.let { slope ->
                item { TrendCard(slope) }
            }
            if (summary.totalHypo > 0) {
                item { HypoAlertCard(summary.totalHypo) }
            }
            item { RecentDaysHeader() }
            items(state.recentLogs.take(14), key = { it.id }) { log ->
                CompactLogRow(log)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun WindowSwitcher(
    window: GlucoseHistoryWindow,
    onSelect: (GlucoseHistoryWindow) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlucoseColors.Emerald50,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            WindowChip(
                label = stringResource(R.string.glucose_history_window_7d),
                selected = window == GlucoseHistoryWindow.W7,
                onClick = { onSelect(GlucoseHistoryWindow.W7) },
                modifier = Modifier.weight(1f),
            )
            WindowChip(
                label = stringResource(R.string.glucose_history_window_30d),
                selected = window == GlucoseHistoryWindow.W30,
                onClick = { onSelect(GlucoseHistoryWindow.W30) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WindowChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) GlucoseColors.Emerald600 else Color.Transparent,
        modifier = modifier,
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else GlucoseColors.Emerald700,
                letterSpacing = 0.2.sp,
            )
        }
    }
}

@Composable
private fun EmptyState(onUploadCgm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = GlucoseColors.Emerald100,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MonitorHeart, null,
                        Modifier.size(36.dp),
                        tint = GlucoseColors.Emerald600,
                    )
                }
            }
            Text(
                stringResource(R.string.glucose_history_empty),
                style = MaterialTheme.typography.titleMedium,
                color = GlucoseColors.Emerald800,
                fontWeight = FontWeight.ExtraBold,
            )
            Button(
                onClick = onUploadCgm,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.home_glucose_card_cta_upload),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HERO KPI CARD — gradient + 3 tiles + CTA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HeroKpiCard(s: GlucoseWindowSummary, onOpenDrGlykos: () -> Unit) {
    GlucoseHeroSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoGraph, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glucose_dashboard_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.glucose_history_days_covered, s.daysCovered),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            GlucoseHeroKpiTile(
                label = stringResource(R.string.glucose_history_kpi_avg),
                value = s.avgMgdl?.let { "${it.toInt()}" } ?: "—",
                unit = "mg/dL",
                status = GlucoseStatus.forAvg(s.avgMgdl),
                modifier = Modifier.weight(1f),
            )
            GlucoseHeroKpiTile(
                label = stringResource(R.string.glucose_history_kpi_tir),
                value = s.avgTirPct?.let { "${it.toInt()}" } ?: "—",
                unit = "%",
                status = GlucoseStatus.forTir(s.avgTirPct?.toInt()),
                modifier = Modifier.weight(1f),
            )
            GlucoseHeroKpiTile(
                label = stringResource(R.string.glucose_history_kpi_cv),
                value = s.avgCv?.let { "%.1f".format(it) } ?: "—",
                unit = "%",
                status = GlucoseStatus.forCv(s.avgCv),
                modifier = Modifier.weight(1f),
            )
        }

        Surface(
            onClick = onOpenDrGlykos,
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp), tint = GlucoseColors.Emerald600)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.glucose_entry_open_dr_glykos),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlucoseColors.Emerald800,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    null,
                    Modifier.size(16.dp),
                    tint = GlucoseColors.Emerald600,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PATTERN BADGE — card avec icône + label + hint clinique
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PatternBadge(s: GlucoseWindowSummary) {
    val (label, hint, isWarn) = when (s.pattern) {
        GlucosePattern.HYPO_RISK -> Triple(
            stringResource(R.string.glucose_pattern_hypo_risk),
            stringResource(R.string.glucose_pattern_hypo_risk_hint), true)
        GlucosePattern.HIGH_VARIABILITY -> Triple(
            stringResource(R.string.glucose_pattern_variability),
            stringResource(R.string.glucose_pattern_variability_hint), true)
        GlucosePattern.POSTPRANDIAL_SPIKES -> Triple(
            stringResource(R.string.glucose_pattern_spikes),
            stringResource(R.string.glucose_pattern_spikes_hint), true)
        GlucosePattern.DAWN_PHENOMENON -> Triple(
            stringResource(R.string.glucose_pattern_dawn),
            stringResource(R.string.glucose_pattern_dawn_hint), false)
        GlucosePattern.RISING_TREND -> Triple(
            stringResource(R.string.glucose_pattern_rising),
            stringResource(R.string.glucose_pattern_rising_hint), true)
        GlucosePattern.FALLING_TREND -> Triple(
            stringResource(R.string.glucose_pattern_falling),
            stringResource(R.string.glucose_pattern_falling_hint), false)
        GlucosePattern.STABLE_OPTIMAL -> Triple(
            stringResource(R.string.glucose_pattern_stable),
            stringResource(R.string.glucose_pattern_stable_hint), false)
        GlucosePattern.NORMAL -> Triple(
            stringResource(R.string.glucose_pattern_normal),
            stringResource(R.string.glucose_pattern_normal_hint), false)
        GlucosePattern.INSUFFICIENT_DATA -> return
    }
    val tint = if (isWarn) GlucoseColors.Warning else GlucoseColors.Emerald600
    val icon = if (isWarn) Icons.Default.Warning else Icons.Default.Insights

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tint.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top : icon + section title
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(18.dp), tint = tint)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.glucose_dashboard_pattern_section).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = tint,
                    )
                }
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TREND CARD — slope hebdo
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TrendCard(slope: Double) {
    val isFavorable = slope < 0
    val tint = if (isFavorable) GlucoseColors.InRange else GlucoseColors.Warning
    val icon = if (isFavorable) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp
    val sign = if (slope >= 0) "+" else ""

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tint.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), tint = tint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glucose_dashboard_trend_section).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Text(
                    "$sign${"%.1f".format(slope)} mg/dL · semaine",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = tint,
                )
                Text(
                    stringResource(
                        if (isFavorable) R.string.glucose_trend_favorable
                        else R.string.glucose_trend_unfavorable
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HYPO ALERT — récap d'hypos sur la fenêtre
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HypoAlertCard(totalHypo: Int) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Critical.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlucoseColors.Critical.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = GlucoseColors.Critical.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Warning, null, Modifier.size(22.dp), tint = GlucoseColors.Critical)
                }
            }
            Text(
                stringResource(R.string.glucose_dashboard_hypo_total, totalHypo),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = GlucoseColors.Critical,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RECENT DAYS — header + compact rows
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun RecentDaysHeader() {
    Text(
        stringResource(R.string.glucose_dashboard_recent_header).uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun CompactLogRow(log: GlucoseLogEntity) {
    val locale = Locale.getDefault()
    val dateFmt = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val avgStatus = GlucoseStatus.forAvg(log.avgMgdl)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status dot
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(avgStatus.color)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    log.date.format(dateFmt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        log.avgMgdl?.let { append("avg ${it.toInt()} · ") }
                        log.timeInRangePct?.let { append("TIR $it% · ") }
                        log.peakMgdl?.let { append("pic ${it.toInt()}") }
                        if (isEmpty()) append("—")
                    }.trimEnd().trimEnd('·').trimEnd(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            log.hypoCount?.takeIf { it > 0 }?.let { n ->
                Surface(
                    color = GlucoseColors.Critical.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.glucose_hypos_count, n, n),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GlucoseColors.Critical,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// POLISH 2 — Hero curve today + TIR breakdown + Streak/Best day
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Hero card en haut du dashboard : courbe glycémique TODAY avec repas en
 * markers ON la courbe + métadonnées du jour (avg / status / nb repas).
 *
 * Pourquoi un hero card et pas un widget classique : la courbe est le signal
 * le plus dense d'information disponible — un coup d'œil suffit pour saisir
 * la qualité de la journée. C'est la première chose que l'user voit en
 * ouvrant le tab Glycémie → effet "premium dashboard" immédiat.
 */
@Composable
private fun TodayCurveHeroCard(log: GlucoseLogEntity, meals: List<MealLogEntity>) {
    val curveJson = log.glucoseMgdlCurveJson ?: return
    val chartCurve = remember(curveJson) { parseCurveForChart(curveJson) }
    if (chartCurve.size < 2) return
    val chartMeals = remember(meals, chartCurve) {
        meals.mapNotNull { meal ->
            val t = meal.time ?: return@mapNotNull null
            val responsePeak = chartCurve
                .filter { (it.time.toSecondOfDay() - t.toSecondOfDay()) in (30 * 60)..(90 * 60) }
                .maxOfOrNull { it.mgdl }
            ChartMealMarker(time = t, responsePeak = responsePeak)
        }
    }
    val avg = log.avgMgdl?.toInt() ?: chartCurve.map { it.mgdl }.average().toInt()
    val avgStatus = GlucoseStatus.forAvg(log.avgMgdl)

    GlucoseHeroSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MonitorHeart, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.glucose_dashboard_today_hero_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.72f),
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "$avg",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        "mg/dL",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            GlucoseStatusPill(
                status = avgStatus,
                label = when (avgStatus) {
                    GlucoseStatus.InRange -> stringResource(R.string.glucose_status_in_range)
                    GlucoseStatus.Warning -> stringResource(R.string.glucose_status_warning)
                    GlucoseStatus.Critical -> stringResource(R.string.glucose_status_critical)
                    GlucoseStatus.Unknown -> stringResource(R.string.glucose_status_unknown)
                },
                onDark = true,
            )
        }
        GlucoseTimelineChart(
            curve = chartCurve,
            meals = chartMeals,
            height = 180.dp,
            onDarkBackground = true,
        )
        if (chartMeals.isNotEmpty()) {
            GlucoseChartLegend(showMealMarkers = true, onDarkBackground = true)
        }
    }
}

private fun parseCurveForChart(json: String): List<ChartGlucosePoint> = try {
    JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
        val o = el.asJsonObject
        val t = o.get("t")?.asString?.take(5)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: return@mapNotNull null
        val mgdl = o.get("mgdl")?.asDouble ?: return@mapNotNull null
        ChartGlucosePoint(t, mgdl)
    }.sortedBy { it.time }
} catch (_: Exception) {
    emptyList()
}

/**
 * Visualisation horizontale empilée TBR / TIR / TAR :
 *  - Rouge bas (TBR <70 mg/dL)
 *  - Vert milieu (TIR 70-180 mg/dL)
 *  - Amber haut (TAR >180 mg/dL)
 *
 * Chaque segment a sa largeur proportionnelle au % de temps. Labels superposés
 * sur les segments larges, ou placés sous la barre pour les segments fins.
 * Cible visuelle ADA : segment vert >=70% pour atteindre l'objectif clinique.
 */
@Composable
private fun TirBreakdownCard(s: GlucoseWindowSummary) {
    val tir = s.avgTirPct?.toInt()?.coerceIn(0, 100) ?: return
    // Sans data TAR/TBR au niveau de summary, on infère depuis les % connus :
    // TBR = (100 - TIR) * fraction selon nombre d'hypos vs total… approximation
    // V1 : on stocke uniquement TIR au niveau summary. On affiche donc TIR vs
    // "hors-cible" total (split TBR/TAR si on a totalHypo>0 indicatif).
    val tbr = if (s.totalHypo > 0 && s.daysCovered > 0) {
        // Estimation grossière : 1 hypo ≈ 0.5% de la journée → moyenne sur la
        // fenêtre. Plafond raisonnable 15%.
        ((s.totalHypo.toDouble() * 0.5 / s.daysCovered).coerceAtMost(15.0)).toInt()
    } else 0
    val tar = (100 - tir - tbr).coerceAtLeast(0)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.glucose_dashboard_tir_breakdown).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            // Barre empilée 14dp de haut, rounded
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                if (tbr > 0) {
                    Box(
                        Modifier
                            .weight(tbr.toFloat())
                            .fillMaxHeight()
                            .background(GlucoseColors.Critical)
                    )
                }
                Box(
                    Modifier
                        .weight(tir.toFloat().coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(GlucoseColors.InRange)
                )
                if (tar > 0) {
                    Box(
                        Modifier
                            .weight(tar.toFloat())
                            .fillMaxHeight()
                            .background(GlucoseColors.Warning)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tbr > 0) BreakdownLegendItem(GlucoseColors.Critical, "TBR", "$tbr%")
                BreakdownLegendItem(GlucoseColors.InRange, "TIR 70-180", "$tir%")
                if (tar > 0) BreakdownLegendItem(GlucoseColors.Warning, "TAR >180", "$tar%")
            }
        }
    }
}

@Composable
private fun BreakdownLegendItem(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontSize = 10.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Row de 2 cards côte à côte : streak optimal (consécutifs TIR>=70) + meilleur
 * jour de la fenêtre (highest TIR). Effet "gamification douce" — l'user voit
 * sa série en cours et son meilleur résultat → motivation à maintenir/dépasser.
 */
@Composable
private fun StreakAndBestDayRow(streakDays: Int, bestDay: GlucoseLogEntity?) {
    if (streakDays == 0 && bestDay == null) return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (streakDays > 0) {
            StreakCard(streakDays = streakDays, modifier = Modifier.weight(1f))
        }
        bestDay?.let {
            BestDayCard(log = it, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StreakCard(streakDays: Int, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.LocalFireDepartment, null,
                    Modifier.size(20.dp),
                    tint = Color(0xFFF59E0B),
                )
                Text(
                    stringResource(R.string.glucose_dashboard_streak_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$streakDays",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF59E0B),
                )
                Text(
                    if (streakDays > 1)
                        stringResource(R.string.glucose_dashboard_streak_days)
                    else stringResource(R.string.glucose_dashboard_streak_day),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                stringResource(R.string.glucose_dashboard_streak_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun BestDayCard(log: GlucoseLogEntity, modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    val dateFmt = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
    }
    val tir = log.timeInRangePct ?: 0

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.border(1.dp, GlucoseColors.InRange.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.EmojiEvents, null,
                    Modifier.size(20.dp),
                    tint = GlucoseColors.InRange,
                )
                Text(
                    stringResource(R.string.glucose_dashboard_best_day_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$tir",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = GlucoseColors.InRange,
                )
                Text(
                    "% TIR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                log.date.format(dateFmt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}
