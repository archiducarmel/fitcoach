package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import javax.inject.Inject

private val GlucoseBlue = Color(0xFF0F4C75)

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
                title = { Text(stringResource(R.string.glucose_history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Window switch
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.window == GlucoseHistoryWindow.W7,
                    onClick = { viewModel.setWindow(GlucoseHistoryWindow.W7) },
                    label = { Text(stringResource(R.string.glucose_history_window_7d)) },
                )
                FilterChip(
                    selected = state.window == GlucoseHistoryWindow.W30,
                    onClick = { viewModel.setWindow(GlucoseHistoryWindow.W30) },
                    label = { Text(stringResource(R.string.glucose_history_window_30d)) },
                )
            }

            val summary = state.summary
            if (summary == null || summary.daysCovered == 0) {
                EmptyState()
            } else {
                SummaryCard(summary)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.recentLogs, key = { it.id }) { log ->
                        LogRow(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
    ) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.glucose_history_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun SummaryCard(s: GlucoseWindowSummary) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.glucose_history_days_covered, s.daysCovered),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(label = stringResource(R.string.glucose_history_kpi_avg),
                    value = s.avgMgdl?.let { "${it.toInt()}" } ?: "—")
                Stat(label = stringResource(R.string.glucose_history_kpi_tir),
                    value = s.avgTirPct?.let { "${it.toInt()}%" } ?: "—")
                Stat(label = stringResource(R.string.glucose_history_kpi_cv),
                    value = s.avgCv?.let { "${"%.1f".format(it)}%" } ?: "—")
            }
            Divider()
            Text(stringResource(R.string.glucose_history_kpi_pattern) + ": " + describePattern(s.pattern),
                style = MaterialTheme.typography.bodyMedium,
                color = GlucoseBlue,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold, color = GlucoseBlue, maxLines = 1)
    }
}

@Composable
private fun LogRow(log: GlucoseLogEntity) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(log.date.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        log.avgMgdl?.let { append("avg ${it.toInt()} · ") }
                        log.timeInRangePct?.let { append("TIR $it% · ") }
                        log.peakMgdl?.let { append("pic ${it.toInt()}") }
                        if (isEmpty()) append("Pas de métriques (image seule)")
                    }.trimEnd().trimEnd('·').trimEnd(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            log.hypoCount?.takeIf { it > 0 }?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("$it hypo",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

private fun describePattern(p: GlucosePattern): String = when (p) {
    GlucosePattern.INSUFFICIENT_DATA -> "Données insuffisantes"
    GlucosePattern.HYPO_RISK -> "⚠ Risque hypoglycémique"
    GlucosePattern.HIGH_VARIABILITY -> "⚠ Variabilité élevée"
    GlucosePattern.POSTPRANDIAL_SPIKES -> "Pics postprandiaux"
    GlucosePattern.DAWN_PHENOMENON -> "Phénomène de l'aube"
    GlucosePattern.RISING_TREND -> "Tendance haussière"
    GlucosePattern.FALLING_TREND -> "Tendance baissière"
    GlucosePattern.STABLE_OPTIMAL -> "Stable optimal"
    GlucosePattern.NORMAL -> "Normal"
}
