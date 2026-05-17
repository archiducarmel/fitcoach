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

/** Palette médicale Dr. Glykos. Aligné sur TodayGlucoseCard et AiToolsSection. */
private val GlucoseEmerald = Color(0xFF059669)
private val GlucoseEmeraldSoft = Color(0xFFD1FAE5)
private val TargetGreen = Color(0xFF22C55E)
private val SpikeAmber = Color(0xFFF59E0B)
private val SpikeRed = Color(0xFFEF4444)

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
    viewModel: MealGlucoseTimelineViewModel = hiltViewModel(),
) {
    LaunchedEffect(date) { viewModel.setDate(date) }
    val state by viewModel.state.collectAsState()

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = GlucoseEmeraldSoft, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MedicalServices, null,
                            Modifier.size(16.dp), tint = GlucoseEmerald)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nutrition_glucose_timeline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlucoseEmerald, modifier = Modifier.weight(1f))
                state.log?.avgMgdl?.let {
                    Text("${it.toInt()} ${stringResource(R.string.nutrition_glucose_timeline_axis_unit)}",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = GlucoseEmerald)
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
                        MealMarkersLegend(state.meals.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimelineBlock(onUploadCgm: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.nutrition_glucose_timeline_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        OutlinedButton(
            onClick = onUploadCgm,
            border = androidx.compose.foundation.BorderStroke(1.dp, GlucoseEmerald.copy(alpha = 0.5f)),
        ) {
            Text(stringResource(R.string.nutrition_glucose_timeline_cta),
                color = GlucoseEmerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KpiOnlyBlock(log: GlucoseLogEntity, onOpenDrGlykos: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        log.peakMgdl?.let { peak ->
            MiniChip(label = stringResource(R.string.glucose_entry_kpi_peak),
                value = "${peak.toInt()}", unit = "mg/dL",
                tint = if (peak < 140) TargetGreen else if (peak < 180) SpikeAmber else SpikeRed)
        }
        log.timeInRangePct?.let { tir ->
            MiniChip(label = stringResource(R.string.glucose_entry_kpi_tir),
                value = "$tir", unit = "%",
                tint = if (tir >= 70) TargetGreen else SpikeAmber)
        }
        log.hypoCount?.takeIf { it > 0 }?.let {
            MiniChip(label = stringResource(R.string.glucose_entry_kpi_hypo),
                value = "$it", unit = "", tint = SpikeRed)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenDrGlykos) {
            Text(stringResource(R.string.glucose_entry_open_dr_glykos),
                color = GlucoseEmerald, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MiniChip(label: String, value: String, unit: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold, color = tint, maxLines = 1)
                if (unit.isNotEmpty()) {
                    Text(unit, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GlucoseGraph(
    curve: List<GlucoseCurvePoint>,
    meals: List<MealLogEntity>,
) {
    val density = LocalDensity.current
    val minMgdl = 50.0
    val maxMgdl = (curve.maxOf { it.mgdl }.coerceAtLeast(200.0)).coerceAtMost(300.0)
    val targetLowAxisColor = TargetGreen.copy(alpha = 0.20f)

    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val yRange = (maxMgdl - minMgdl).coerceAtLeast(1.0).toFloat()

            fun y(mgdl: Double): Float = h - ((mgdl - minMgdl).toFloat() / yRange * h)
            fun x(time: LocalTime): Float = (time.toSecondOfDay().toFloat() / 86400f) * w

            // Zone cible 70-140 mg/dL
            val targetTopY = y(TARGET_HIGH)
            val targetBottomY = y(TARGET_LOW)
            drawRect(
                color = targetLowAxisColor,
                topLeft = Offset(0f, targetTopY),
                size = Size(w, targetBottomY - targetTopY),
            )

            // Ligne seuil pic (180 mg/dL) — pointillé rouge léger
            val spikeY = y(SPIKE_THRESHOLD)
            drawLine(
                color = SpikeRed.copy(alpha = 0.35f),
                start = Offset(0f, spikeY), end = Offset(w, spikeY),
                strokeWidth = with(density) { 1.dp.toPx() },
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )

            // Courbe glucose
            if (curve.size >= 2) {
                val path = Path().apply {
                    val first = curve.first()
                    moveTo(x(first.time), y(first.mgdl))
                    for (i in 1 until curve.size) {
                        val p = curve[i]
                        lineTo(x(p.time), y(p.mgdl))
                    }
                }
                drawPath(
                    path = path, color = GlucoseEmerald,
                    style = Stroke(width = with(density) { 2.5.dp.toPx() }),
                )
            }

            // Marqueurs repas
            for (meal in meals) {
                val mt = meal.time ?: continue
                val mx = x(mt)
                // Couleur selon pic dans les 30-90 min après le repas
                val responsePeak = curve
                    .filter {
                        val delta = it.time.toSecondOfDay() - mt.toSecondOfDay()
                        delta in (30 * 60)..(90 * 60)
                    }
                    .maxOfOrNull { it.mgdl }
                val markerColor = when {
                    responsePeak == null -> GlucoseEmerald.copy(alpha = 0.5f)
                    responsePeak >= SPIKE_THRESHOLD -> SpikeRed
                    responsePeak >= 140.0 -> SpikeAmber
                    else -> TargetGreen
                }
                drawCircle(
                    color = markerColor,
                    radius = with(density) { 5.dp.toPx() },
                    center = Offset(mx, h - with(density) { 4.dp.toPx() }),
                )
            }
        }
    }
}

@Composable
private fun MealMarkersLegend(mealCount: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color = TargetGreen, label = "<140")
        LegendDot(color = SpikeAmber, label = "140-180")
        LegendDot(color = SpikeRed, label = "≥180")
        Spacer(Modifier.weight(1f))
        Text("$mealCount", fontWeight = FontWeight.Bold, fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

