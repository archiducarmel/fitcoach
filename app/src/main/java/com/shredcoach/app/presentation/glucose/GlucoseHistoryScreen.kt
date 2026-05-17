package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

enum class GlucoseHistoryWindow(val days: Int) { W7(7), W30(30) }

data class GlucoseHistoryState(
    val window: GlucoseHistoryWindow = GlucoseHistoryWindow.W7,
    val summary: GlucoseWindowSummary? = null,
    val recentLogs: List<GlucoseLogEntity> = emptyList(),
)

@HiltViewModel
class GlucoseHistoryViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GlucoseHistoryState())
    val state: StateFlow<GlucoseHistoryState> = _state.asStateFlow()

    init { reload() }

    fun setWindow(w: GlucoseHistoryWindow) {
        _state.update { it.copy(window = w) }
        reload()
    }

    private fun reload() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val w = _state.value.window
            val summary = glucoseRepository.getWindowSummary(today, w.days)
            val from = today.minusDays((w.days - 1).toLong())
            val logs = glucoseRepository.getRange(from, today).sortedByDescending { it.date }
            _state.update { it.copy(summary = summary, recentLogs = logs) }
        }
    }
}

/**
 * Écran dédié à l'historique glycémique CGM. Design aligné sur
 * [GlucoseDashboard] : window switcher → hero summary → list raffinée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseHistoryScreen(
    navController: NavController,
    viewModel: GlucoseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    GlucoseSectionHeader(
                        icon = Icons.Default.AutoGraph,
                        title = stringResource(R.string.glucose_history_title),
                        subtitle = stringResource(R.string.glucose_history_subtitle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                WindowSwitcher(state.window) { viewModel.setWindow(it) }
            }

            val summary = state.summary
            if (summary == null || summary.daysCovered == 0) {
                item { EmptyState() }
            } else {
                item { SummaryCard(summary) }
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Text(
                        stringResource(R.string.glucose_dashboard_recent_header).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                items(state.recentLogs, key = { it.id }) { log ->
                    LogRow(log)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
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
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WindowChip(stringResource(R.string.glucose_history_window_7d),
                window == GlucoseHistoryWindow.W7, { onSelect(GlucoseHistoryWindow.W7) },
                Modifier.weight(1f))
            WindowChip(stringResource(R.string.glucose_history_window_30d),
                window == GlucoseHistoryWindow.W30, { onSelect(GlucoseHistoryWindow.W30) },
                Modifier.weight(1f))
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
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
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
        }
    }
}

@Composable
private fun SummaryCard(s: GlucoseWindowSummary) {
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
                )
                Text(
                    stringResource(R.string.glucose_history_days_covered, s.daysCovered),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
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
        // Pattern inline (sur le hero, plus discret qu'un Dashboard)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Insights, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.85f))
                Text(
                    stringResource(R.string.glucose_history_kpi_pattern) + " · " + patternLabel(s.pattern),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun LogRow(log: GlucoseLogEntity) {
    val locale = Locale.getDefault()
    val dateFormatter = remember(locale) {
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
            // Status dot — couleur du status d'avg
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(avgStatus.color)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    log.date.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    buildString {
                        log.avgMgdl?.let { append("avg ${it.toInt()} · ") }
                        log.timeInRangePct?.let { append("TIR $it% · ") }
                        log.peakMgdl?.let { append("pic ${it.toInt()}") }
                        if (isEmpty()) append(emptyMessage())
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

@Composable
private fun emptyMessage(): String = stringResource(R.string.glucose_history_log_no_metrics)

@Composable
private fun patternLabel(p: GlucosePattern): String = when (p) {
    GlucosePattern.INSUFFICIENT_DATA -> stringResource(R.string.glucose_history_empty)
    GlucosePattern.HYPO_RISK -> stringResource(R.string.glucose_pattern_hypo_risk)
    GlucosePattern.HIGH_VARIABILITY -> stringResource(R.string.glucose_pattern_variability)
    GlucosePattern.POSTPRANDIAL_SPIKES -> stringResource(R.string.glucose_pattern_spikes)
    GlucosePattern.DAWN_PHENOMENON -> stringResource(R.string.glucose_pattern_dawn)
    GlucosePattern.RISING_TREND -> stringResource(R.string.glucose_pattern_rising)
    GlucosePattern.FALLING_TREND -> stringResource(R.string.glucose_pattern_falling)
    GlucosePattern.STABLE_OPTIMAL -> stringResource(R.string.glucose_pattern_stable)
    GlucosePattern.NORMAL -> stringResource(R.string.glucose_pattern_normal)
}
