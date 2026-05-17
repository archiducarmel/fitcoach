package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
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

@HiltViewModel
class TodayGlucoseCardViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {
    private val _log = MutableStateFlow<GlucoseLogEntity?>(null)
    val log: StateFlow<GlucoseLogEntity?> = _log.asStateFlow()

    init {
        viewModelScope.launch {
            glucoseRepository.observeForDate(LocalDate.now()).collect { _log.value = it }
        }
    }
}

/**
 * Card hero du suivi glycémique sur la Home. Deux états :
 *
 *  - **EMPTY** : surface emerald 50 (light bg), icône hero, message,
 *    CTA emerald 600 → upload CGM. Lisibilité parfaite, pas de blanc
 *    sur vert clair.
 *
 *  - **LOGGED** : surface gradient emerald 900→700 (foncé) + texte blanc
 *    WCAG AAA. Big number central (moyenne du jour) + status pill +
 *    3 KPI tiles glassmorphiques + CTA "Analyse Dr. Glykos".
 *
 * Le design est volontairement aligné sur AiToolsSection / Dr. Glykos
 * (palette identique) pour que l'user fasse l'association immédiate
 * "ce vert = mon endocrino IA".
 */
@Composable
fun TodayGlucoseCard(
    onUploadClick: () -> Unit,
    onAnalyzeClick: () -> Unit,
    viewModel: TodayGlucoseCardViewModel = hiltViewModel(),
) {
    val log by viewModel.log.collectAsState()
    val current = log

    if (current == null || !current.hasAnyMetric()) {
        EmptyTodayGlucoseCard(onUploadClick)
    } else {
        LoggedTodayGlucoseCard(current, onAnalyzeClick)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EMPTY STATE — fond clair, texte foncé, CTA emerald
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyTodayGlucoseCard(onUploadClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlucoseSectionHeader(
                icon = Icons.Default.MonitorHeart,
                title = stringResource(R.string.home_glucose_card_title),
                subtitle = stringResource(R.string.home_glucose_card_empty_subtitle),
            )
            Text(
                stringResource(R.string.home_glucose_card_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = GlucoseColors.Emerald800.copy(alpha = 0.78f),
            )
            Button(
                onClick = onUploadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlucoseColors.Emerald600,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.home_glucose_card_cta_upload),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LOGGED STATE — hero gradient foncé, big number, KPI tiles glassy
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun LoggedTodayGlucoseCard(log: GlucoseLogEntity, onAnalyzeClick: () -> Unit) {
    val avgStatus = GlucoseStatus.forAvg(log.avgMgdl)
    val tirStatus = GlucoseStatus.forTir(log.timeInRangePct)
    val peakStatus = GlucoseStatus.forPeak(log.peakMgdl)

    val dateStr = remember {
        LocalDate.now().format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(Locale.getDefault())
        ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    val statusLabel = when (avgStatus) {
        GlucoseStatus.InRange -> stringResource(R.string.glucose_status_in_range)
        GlucoseStatus.Warning -> stringResource(R.string.glucose_status_warning)
        GlucoseStatus.Critical -> stringResource(R.string.glucose_status_critical)
        GlucoseStatus.Unknown -> stringResource(R.string.glucose_status_unknown)
    }

    GlucoseHeroSurface(modifier = Modifier.fillMaxWidth()) {
        // ─── Header : icône + titre + date ───
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MonitorHeart, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_glucose_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            // Status pill : couleur status, fond blanc translucide pour fond foncé.
            GlucoseStatusPill(status = avgStatus, label = statusLabel, onDark = true)
        }

        // ─── Hero number : la moyenne du jour, énorme ───
        log.avgMgdl?.let { avg ->
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${avg.toInt()}",
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                )
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        "mg/dL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        stringResource(R.string.home_glucose_card_avg_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // ─── KPI tiles : TIR / Pic / Hypos ───
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            log.timeInRangePct?.let { tir ->
                GlucoseHeroKpiTile(
                    label = stringResource(R.string.home_glucose_card_kpi_tir),
                    value = "$tir",
                    unit = "%",
                    status = tirStatus,
                    modifier = Modifier.weight(1f),
                )
            }
            log.peakMgdl?.let { peak ->
                GlucoseHeroKpiTile(
                    label = stringResource(R.string.home_glucose_card_kpi_peak),
                    value = "${peak.toInt()}",
                    unit = "mg/dL",
                    status = peakStatus,
                    modifier = Modifier.weight(1f),
                )
            }
            log.hypoCount?.let { hypo ->
                GlucoseHeroKpiTile(
                    label = stringResource(R.string.home_glucose_card_kpi_hypo),
                    value = "$hypo",
                    unit = "",
                    status = GlucoseStatus.forHypoCount(hypo),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ─── CTA "Analyse Dr. Glykos" : fond blanc, texte emerald ───
        Surface(
            onClick = onAnalyzeClick,
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AutoGraph,
                    null,
                    Modifier.size(18.dp),
                    tint = GlucoseColors.Emerald600,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.home_glucose_card_cta_analyze),
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

private fun GlucoseLogEntity.hasAnyMetric(): Boolean =
    avgMgdl != null || peakMgdl != null || timeInRangePct != null
