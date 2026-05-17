package com.shredcoach.app.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary
import com.shredcoach.app.presentation.glucose.GlucoseHistoryViewModel
import com.shredcoach.app.presentation.glucose.GlucoseHistoryWindow

/** Palette médicale Dr. Glykos. Aligné sur TodayGlucoseCard et AiToolsSection. */
private val GlucoseEmerald = Color(0xFF059669)
private val GlucoseEmeraldSoft = Color(0xFFD1FAE5)

/**
 * 3ème onglet du Dashboard, dédié au suivi glycémique CGM.
 *
 * **Réutilise** [GlucoseHistoryViewModel] pour ne pas dupliquer la logique
 * de fetch / agrégation (déjà testée en Phase 2). Le composable ajoute juste
 * une couche visuelle premium :
 *  - Hero KPIs (avg, TIR, CV)
 *  - Pattern badge avec hint clinique
 *  - CTA "Consulter Dr. Glykos" prééminent
 *  - Trend slope sur 30j (si dispo)
 *  - Liste compacte des derniers jours
 *
 * **Empty state** : si pas de data CGM, CTA upload + explication.
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ─── Window switcher ──────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.window == GlucoseHistoryWindow.W7,
                    onClick = { viewModel.setWindow(GlucoseHistoryWindow.W7) },
                    label = { Text(stringResource(R.string.glucose_history_window_7d)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GlucoseEmerald.copy(alpha = 0.18f),
                        selectedLabelColor = GlucoseEmerald,
                    ),
                )
                FilterChip(
                    selected = state.window == GlucoseHistoryWindow.W30,
                    onClick = { viewModel.setWindow(GlucoseHistoryWindow.W30) },
                    label = { Text(stringResource(R.string.glucose_history_window_30d)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GlucoseEmerald.copy(alpha = 0.18f),
                        selectedLabelColor = GlucoseEmerald,
                    ),
                )
            }
        }

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
private fun EmptyState(onUploadCgm: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseEmeraldSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.MedicalServices, null, Modifier.size(48.dp), tint = GlucoseEmerald)
            Text(stringResource(R.string.glucose_history_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                fontWeight = FontWeight.SemiBold)
            Button(
                onClick = onUploadCgm,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseEmerald),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_glucose_card_cta_upload),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HeroKpiCard(s: GlucoseWindowSummary, onOpenDrGlykos: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MedicalServices, null, Modifier.size(20.dp), tint = GlucoseEmerald)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.glucose_dashboard_hero_title),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = GlucoseEmerald, modifier = Modifier.weight(1f))
                Surface(
                    color = GlucoseEmeraldSoft, shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.glucose_history_days_covered, s.daysCovered),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlucoseEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BigKpi(label = stringResource(R.string.glucose_history_kpi_avg),
                    value = s.avgMgdl?.let { "${it.toInt()}" } ?: "—",
                    unit = "mg/dL",
                    modifier = Modifier.weight(1f))
                BigKpi(label = stringResource(R.string.glucose_history_kpi_tir),
                    value = s.avgTirPct?.let { "${it.toInt()}" } ?: "—",
                    unit = "%",
                    modifier = Modifier.weight(1f))
                BigKpi(label = stringResource(R.string.glucose_history_kpi_cv),
                    value = s.avgCv?.let { "%.1f".format(it) } ?: "—",
                    unit = "%",
                    modifier = Modifier.weight(1f))
            }
            Button(
                onClick = onOpenDrGlykos,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseEmerald),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.glucose_entry_open_dr_glykos),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BigKpi(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value,
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Black, color = GlucoseEmerald, maxLines = 1)
            Spacer(Modifier.width(2.dp))
            Text(unit, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

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
    val tint = if (isWarn) Color(0xFFD97706) else GlucoseEmerald
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isWarn) Icons.Default.Warning else Icons.Default.MedicalServices,
                null, Modifier.size(22.dp), tint = tint,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontWeight = FontWeight.Bold, color = tint,
                    style = MaterialTheme.typography.titleSmall)
                Text(hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun TrendCard(slope: Double) {
    val isFavorable = slope < 0
    val tint = if (isFavorable) Color(0xFF22C55E) else Color(0xFFD97706)
    val sign = if (slope >= 0) "+" else ""
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${sign}${"%.1f".format(slope)} mg/dL / sem",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.weight(1f),
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

@Composable
private fun HypoAlertCard(totalHypo: Int) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.glucose_dashboard_hypo_total, totalHypo),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun RecentDaysHeader() {
    Text(stringResource(R.string.glucose_dashboard_recent_header),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CompactLogRow(log: GlucoseLogEntity) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Date pill
            Surface(
                color = GlucoseEmeraldSoft, shape = RoundedCornerShape(8.dp),
            ) {
                Text(log.date.toString().takeLast(5), // MM-DD
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold, color = GlucoseEmerald,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        log.avgMgdl?.let { append("avg ${it.toInt()} · ") }
                        log.timeInRangePct?.let { append("TIR $it% · ") }
                        log.peakMgdl?.let { append("pic ${it.toInt()}") }
                        if (isEmpty()) append("—")
                    }.trimEnd().trimEnd('·').trimEnd(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
            log.hypoCount?.takeIf { it > 0 }?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("$it",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}
