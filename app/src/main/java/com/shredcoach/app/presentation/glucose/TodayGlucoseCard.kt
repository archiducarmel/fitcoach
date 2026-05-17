package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/** Palette médicale Dr. Glykos. Aligné sur la card AiToolsSection.kt. */
private val GlucoseEmerald = Color(0xFF059669)
private val GlucoseTeal = Color(0xFF14B8A6)

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
 * Card sur Home affichant l'état glycémique du jour.
 *
 * **Design** : gradient emerald → teal (palette médicale) avec texte blanc
 * full contrast en light + dark mode. Cohérent visuellement avec la card
 * Dr. Glykos sur AiToolsSection (même gradient) — l'user associe instantanément
 * "ce vert = endocrino IA".
 *
 * **2 états** :
 *  - LOGGED : KPI compact (avg / TIR / pic) + CTA "Voir analyse Dr. Glykos"
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
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(GlucoseEmerald, GlucoseTeal))
            ),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header — texte blanc, icône blanche sur surface alpha
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.22f), modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MedicalServices, null,
                                Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_glucose_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1)
                        val dateStr = remember {
                            LocalDate.now().format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                                    .withLocale(Locale.getDefault())
                            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        }
                        Text(dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1)
                    }
                }

                val current = log
                if (current == null || !current.hasAnyMetric()) {
                    Text(stringResource(R.string.home_glucose_card_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f))
                    // CTA inversé : fond blanc + texte emerald → action premium
                    Button(
                        onClick = onUploadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = GlucoseEmerald,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_glucose_card_cta_upload),
                            fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    // KPIs compacts — texte blanc full contrast
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = GlucoseEmerald,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_glucose_card_cta_analyze),
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

private fun GlucoseLogEntity.hasAnyMetric(): Boolean =
    avgMgdl != null || peakMgdl != null || timeInRangePct != null

@Composable
private fun MiniKpi(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    // Mini-card semi-transparente blanche pour densité visuelle + contraste
    // homogène avec le gradient (la KPI a son propre micro-bg pour ne pas être
    // "perdue" dans le gradient).
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1)
                Spacer(Modifier.width(3.dp))
                Text(unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1)
            }
        }
    }
}
