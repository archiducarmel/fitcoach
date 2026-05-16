package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
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
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val GlucoseBlue = Color(0xFF0F4C75)
private val GlucoseBlueSoft = Color(0xFFE3F2FD)

/**
 * ViewModel léger qui observe le log glucose du jour pour la card HomeScreen.
 * Cycle de vie scoped au callsite (HomeScreen) — pas de partage avec
 * GlucoseEntryViewModel pour rester découplé.
 */
@HiltViewModel
class TodayGlucoseCardViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {
    private val _log = MutableStateFlow<GlucoseLogEntity?>(null)
    val log: StateFlow<GlucoseLogEntity?> = _log.asStateFlow()

    init {
        // Observe : on resync à chaque ouverture de Home.
        viewModelScope.launch {
            glucoseRepository.observeForDate(LocalDate.now()).collect { _log.value = it }
        }
    }
}

/**
 * Card sur Home affichant l'état glycémique du jour. Deux états :
 *  - LOGGED : affiche avg / TIR / pic + CTA "Voir analyse Dr. Glykos"
 *  - EMPTY  : CTA "Upload ta CGM du jour" → GlucoseEntryScreen
 */
@Composable
fun TodayGlucoseCard(
    onUploadClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    viewModel: TodayGlucoseCardViewModel = hiltViewModel(),
) {
    val log by viewModel.log.collectAsState()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseBlueSoft),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = GlucoseBlue.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MedicalServices, null,
                            Modifier.size(18.dp), tint = GlucoseBlue)
                    }
                }
                Column {
                    Text(stringResource(R.string.home_glucose_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GlucoseBlue)
                    Text(LocalDate.now().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

            val current = log
            if (current == null || !current.hasAnyMetric()) {
                Text(stringResource(R.string.home_glucose_card_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                Button(
                    onClick = onUploadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_glucose_card_cta_upload),
                        fontWeight = FontWeight.Bold)
                }
            } else {
                // KPIs compacts
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    current.avgMgdl?.let {
                        MiniKpi(
                            label = stringResource(R.string.home_glucose_card_kpi_avg),
                            value = "${it.toInt()}",
                            unit = "mg/dL",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    current.timeInRangePct?.let {
                        MiniKpi(
                            label = stringResource(R.string.home_glucose_card_kpi_tir),
                            value = "$it",
                            unit = "%",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    current.peakMgdl?.let {
                        MiniKpi(
                            label = stringResource(R.string.home_glucose_card_kpi_peak),
                            value = "${it.toInt()}",
                            unit = "mg/dL",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Button(
                    onClick = onAnalyzeClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_glucose_card_cta_analyze),
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun GlucoseLogEntity.hasAnyMetric(): Boolean =
    avgMgdl != null || peakMgdl != null || timeInRangePct != null

@Composable
private fun MiniKpi(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = GlucoseBlue,
                maxLines = 1)
            Spacer(Modifier.width(2.dp))
            Text(unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1)
        }
    }
}
