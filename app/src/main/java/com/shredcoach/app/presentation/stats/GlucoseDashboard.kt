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
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary
import com.shredcoach.app.presentation.glucose.GlucoseColors
import com.shredcoach.app.presentation.glucose.GlucoseHeroKpiTile
import com.shredcoach.app.presentation.glucose.GlucoseHeroSurface
import com.shredcoach.app.presentation.glucose.GlucoseHistoryViewModel
import com.shredcoach.app.presentation.glucose.GlucoseHistoryWindow
import com.shredcoach.app.presentation.glucose.GlucoseSectionHeader
import com.shredcoach.app.presentation.glucose.GlucoseStatus
import com.shredcoach.app.presentation.glucose.GlucoseStatusPill
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
            item { HeroKpiCard(summary, onOpenDrGlykos) }
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
