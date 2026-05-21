package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.data.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

// Aliases vers le design system glucose pour rester cohérent avec
// TodayGlucoseCard / GlucoseEntryScreen / Dashboard / History.
private val GlucoseEmerald = GlucoseColors.Emerald600
private val GlucoseEmeraldSoft = GlucoseColors.Emerald100
private val TargetGreen = GlucoseColors.InRange
private val SpikeAmber = GlucoseColors.Warning
private val SpikeRed = GlucoseColors.Critical

/** Référence athlète strict — pour la zone "ok" sur le graphe. */
private const val TARGET_LOW = 70.0
private const val TARGET_HIGH = 140.0
private const val SPIKE_THRESHOLD = 180.0

data class GlucoseCurvePoint(val time: LocalTime, val mgdl: Double)

data class MealGlucoseTimelineState(
    val date: LocalDate = LocalDate.now(),
    val log: GlucoseLogEntity? = null,
    val curve: List<GlucoseCurvePoint> = emptyList(),
    val meals: List<MealLogEntity> = emptyList(),
)

@HiltViewModel
class MealGlucoseTimelineViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val nutritionRepository: NutritionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MealGlucoseTimelineState())
    val state: StateFlow<MealGlucoseTimelineState> = _state.asStateFlow()

    init { setDate(LocalDate.now()) }

    fun setDate(date: LocalDate) {
        viewModelScope.launch {
            val log = glucoseRepository.getForDate(date)
            val curve = log?.glucoseMgdlCurveJson?.let { parseCurve(it) } ?: emptyList()
            val meals = runCatching { nutritionRepository.getMealsForDateOnce(date) }
                .getOrDefault(emptyList())
            _state.update { it.copy(date = date, log = log, curve = curve, meals = meals) }
        }
    }

    /**
     * Parse JSON `[{"t":"HH:MM","mgdl":N},...]` produit par l'OCR Gemini.
     * Robuste : ignore les entrées malformées plutôt que crash le UI.
     */
    private fun parseCurve(json: String): List<GlucoseCurvePoint> = try {
        val arr = JsonParser.parseString(json).asJsonArray
        arr.mapNotNull { el ->
            val o = el.asJsonObject
            val tStr = o.get("t")?.asString ?: return@mapNotNull null
            val mgdl = o.get("mgdl")?.asDouble ?: return@mapNotNull null
            val time = try { LocalTime.parse(tStr.take(5)) } catch (_: Exception) { return@mapNotNull null }
            GlucoseCurvePoint(time, mgdl)
        }.sortedBy { it.time }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Card affichant la courbe glycémique 24h du jour avec marqueurs des repas
 * logués. Empty state si pas de log CGM ou pas de courbe parsée.
 *
 * **Pattern visuel** :
 *  - Zone cible 70-140 mg/dL en vert clair (background)
 *  - Courbe trait bleu épais
 *  - Marqueurs repas = points colorés sur l'axe X (couleur selon le pic dans
 *    les 90 min suivants : vert <140, jaune 140-180, rouge >180)
 *  - Tap sur la card → navigue vers Dr. Glykos pour analyse approfondie
 */
@Composable
fun MealGlucoseTimelineCard(
    date: LocalDate,
    onUploadCgm: () -> Unit,
    onOpenDrGlykos: () -> Unit,
    onOpenAnalysis: () -> Unit = {},
    viewModel: MealGlucoseTimelineViewModel = hiltViewModel(),
) {
    LaunchedEffect(date) { viewModel.setDate(date) }
    val state by viewModel.state.collectAsState()

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header premium : icône caissée + titre + pill avg du jour
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = GlucoseEmeraldSoft, modifier = Modifier.size(32.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MedicalServices, null,
                            Modifier.size(18.dp), tint = GlucoseEmerald)
                    }
                }
                Text(stringResource(R.string.nutrition_glucose_timeline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlucoseColors.Emerald800, modifier = Modifier.weight(1f))
                state.log?.avgMgdl?.let {
                    Surface(
                        color = GlucoseEmeraldSoft, shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${it.toInt()}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Black,
                                color = GlucoseEmerald)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(2.dp))
                            Text(stringResource(R.string.nutrition_glucose_timeline_axis_unit),
                                style = MaterialTheme.typography.labelSmall,
                                color = GlucoseEmerald.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            val curve = state.curve
            val log = state.log
            when {
                log == null -> {
                    EmptyTimelineBlock(onUploadCgm)
                }
                curve.isEmpty() -> {
                    // On a un log mais pas de courbe parsée — on affiche les KPIs seuls.
                    KpiOnlyBlock(log, onOpenDrGlykos)
                }
                else -> {
                    GlucoseGraph(curve = curve, meals = state.meals)
                    if (state.meals.isNotEmpty()) {
                        GlucoseChartLegend(showMealMarkers = true)
                    }
                }
            }
            // Bouton "Analyse experte" — visible dès qu'il y a un log glucose
            // (avec ou sans courbe). Le ViewModel analyse fera son propre
            // état (loading, error, success) avec gating sur la dispo des data.
            if (log != null) {
                ExpertAnalysisButton(onClick = onOpenAnalysis)
            }
        }
    }
}

/**
 * CTA "Analyse experte" en bas de la card. Bouton plein largeur emerald,
 * icône stéthoscope, label clair. Ouvre l'écran d'analyse LLM Dr. Glykos
 * pour la date affichée.
 */
@Composable
private fun ExpertAnalysisButton(onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = GlucoseEmerald,
            contentColor = androidx.compose.ui.graphics.Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.nutrition_glucose_open_analysis),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyTimelineBlock(onUploadCgm: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlucoseEmeraldSoft.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.nutrition_glucose_timeline_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = GlucoseColors.Emerald800.copy(alpha = 0.75f))
            androidx.compose.material3.Button(
                onClick = onUploadCgm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = GlucoseEmerald, contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.nutrition_glucose_timeline_cta),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun KpiOnlyBlock(log: GlucoseLogEntity, onOpenDrGlykos: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            log.peakMgdl?.let { peak ->
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_peak),
                    value = "${peak.toInt()}", unit = "mg/dL",
                    status = GlucoseStatus.forPeak(peak),
                    modifier = Modifier.weight(1f),
                )
            }
            log.timeInRangePct?.let { tir ->
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_tir),
                    value = "$tir", unit = "%",
                    status = GlucoseStatus.forTir(tir),
                    modifier = Modifier.weight(1f),
                )
            }
            log.hypoCount?.takeIf { it > 0 }?.let {
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_hypo),
                    value = "$it", unit = "",
                    status = GlucoseStatus.forHypoCount(it),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TextButton(
            onClick = onOpenDrGlykos,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.glucose_entry_open_dr_glykos),
                color = GlucoseEmerald, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Délègue au composant unifié [GlucoseTimelineChart] (1 source de vérité pour
 * les 3 surfaces glucose). Les meal markers sont positionnés SUR la courbe à
 * l'heure du repas, couleur dérivée du pic postprandial 30-90 min.
 */
@Composable
private fun GlucoseGraph(
    curve: List<GlucoseCurvePoint>,
    meals: List<MealLogEntity>,
) {
    val chartCurve = curve.map { ChartGlucosePoint(it.time, it.mgdl) }
    val chartMeals = meals.mapNotNull { meal ->
        val t = meal.time ?: return@mapNotNull null
        val responsePeak = curve
            .filter {
                val delta = it.time.toSecondOfDay() - t.toSecondOfDay()
                delta in (30 * 60)..(90 * 60)
            }
            .maxOfOrNull { it.mgdl }
        ChartMealMarker(time = t, responsePeak = responsePeak)
    }
    GlucoseTimelineChart(
        curve = chartCurve,
        meals = chartMeals,
        height = 180.dp,
    )
}

// MealMarkersLegend supprimé — remplacé par GlucoseChartLegend (shared).

// Conservé pour un éventuel usage local hors du chart unifié.
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

