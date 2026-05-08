package com.shredcoach.app.presentation.stats.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.R
import com.shredcoach.app.data.local.dao.BodyScanLogDao
import com.shredcoach.app.data.local.entity.BodyScanLogEntity
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * VM dédié à la card timeline. On ne gonfle pas StatsViewModel avec ce qui est
 * structurellement isolé : la card vit elle-même, peut être déplacée ailleurs
 * (Profile, Body Scanner, History) sans toucher au Dashboard.
 */
@HiltViewModel
class BodyScanTimelineViewModel @Inject constructor(
    private val bodyScanLogDao: BodyScanLogDao,
) : ViewModel() {

    /**
     * Observable des 30 derniers scans, ordonnés du plus récent au plus
     * ancien. Limite raisonnable pour 3 line-charts ; on a rarement >30
     * scans utiles à afficher d'un coup.
     */
    val recentScans: StateFlow<List<BodyScanLogEntity>> =
        bodyScanLogDao.observeRecent(30)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Card Dashboard montrant l'évolution des 3 KPI mesh dans le temps : Posture
 * score, V-Taper ratio, Body Fat %. Clickable → écran scan history (TODO V+1).
 *
 * **Affichée uniquement** si l'utilisateur a au moins 2 scans (1 point isolé
 * = pas de courbe pertinente). Sinon hint d'engagement "Scanne pour suivre
 * ta progression".
 */
@Composable
fun BodyScanTimelineCard(
    onClick: () -> Unit = {},
    viewModel: BodyScanTimelineViewModel = hiltViewModel(),
) {
    val scans by viewModel.recentScans.collectAsState()

    if (scans.isEmpty()) return // Card masquée tant qu'aucun scan

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ─── Header ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Insights, null,
                    Modifier.size(20.dp), tint = OrangeVibrant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dashboard_body_scan_timeline_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            R.string.dashboard_body_scan_timeline_subtitle,
                            scans.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            if (scans.size < 2) {
                // Hint engagement quand 1 seul scan
                Text(
                    stringResource(R.string.dashboard_body_scan_timeline_need_more),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                return@Column
            }

            // ─── 3 mini-charts en grille verticale ───
            // Reverse → ordre chronologique (gauche = ancien, droite = récent)
            val chronological = remember(scans) { scans.reversed() }
            MiniLineChart(
                title = stringResource(R.string.bodymesh_analytics_posture),
                values = chronological.map { it.postureScore.toFloat() },
                accent = NeonGreen,
                yMin = 0f,
                yMax = 100f,
                unit = "/100",
            )
            MiniLineChart(
                title = stringResource(R.string.bodymesh_analytics_vtaper),
                values = chronological.map { it.vTaperRatio },
                accent = OrangeVibrant,
                yMin = 0.8f,
                yMax = 2.0f,
                unit = "",
                decimals = 2,
            )
            // Body fat seulement si on a des valeurs > 0 (sinon ligne plate à 0
            // qui ne révèle rien — l'user n'a peut-être jamais saisi son BF%).
            val hasBodyFat = chronological.any { it.bodyFatPercent > 0.0 }
            if (hasBodyFat) {
                MiniLineChart(
                    title = stringResource(R.string.bodymesh_chip_body_fat),
                    values = chronological.map { it.bodyFatPercent.toFloat() },
                    accent = Color(0xFFFF00E5), // neon pink
                    yMin = 5f,
                    yMax = 35f,
                    unit = "%",
                )
            }
        }
    }
}

/**
 * Mini line chart compact (~64dp tall) pour Dashboard. Pas d'axes pour rester
 * lisible à cette taille — title + valeur courante + min/max range suffisent
 * pour comprendre la tendance.
 */
@Composable
private fun MiniLineChart(
    title: String,
    values: List<Float>,
    accent: Color,
    yMin: Float,
    yMax: Float,
    unit: String,
    decimals: Int = 1,
) {
    if (values.isEmpty()) return
    val current = values.last()
    val first = values.first()
    val delta = current - first
    val deltaSign = if (delta >= 0) "+" else ""
    val fmt = "%.${decimals}f"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${String.format(java.util.Locale.US, fmt, current)}$unit",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "$deltaSign${String.format(java.util.Locale.US, fmt, delta)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta >= 0) NeonGreen else accent.copy(alpha = 0.8f),
                fontSize = 10.sp,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    accent.copy(alpha = 0.04f),
                    RoundedCornerShape(8.dp),
                ),
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (values.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                val range = (yMax - yMin).takeIf { it > 0f } ?: 1f
                val stepX = w / (values.size - 1).toFloat()

                fun pointAt(i: Int): Offset {
                    val v = values[i].coerceIn(yMin, yMax)
                    val x = i * stepX
                    val y = h - ((v - yMin) / range) * h
                    return Offset(x, y)
                }

                // ─── Aire sous la courbe (gradient subtil) ───
                val areaPath = Path().apply {
                    moveTo(0f, h)
                    for (i in values.indices) {
                        val p = pointAt(i)
                        if (i == 0) lineTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(
                    areaPath,
                    brush = Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.30f),
                            accent.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                    ),
                )

                // ─── Ligne courbe ───
                for (i in 1 until values.size) {
                    val a = pointAt(i - 1)
                    val b = pointAt(i)
                    drawLine(
                        color = accent,
                        start = a, end = b,
                        strokeWidth = 2f,
                        cap = StrokeCap.Round,
                    )
                }

                // ─── Dot final (valeur courante, plus saillant) ───
                val last = pointAt(values.size - 1)
                drawCircle(accent.copy(alpha = 0.3f), radius = 6f, center = last)
                drawCircle(accent, radius = 3.2f, center = last)
            }
        }
    }
}
