package com.shredcoach.app.presentation.settings.llm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.dao.UsageByAssistant
import com.shredcoach.app.data.local.dao.UsageByModel
import com.shredcoach.app.data.local.dao.UsageByProvider
import com.shredcoach.app.data.local.dao.UsageDayBucket
import com.shredcoach.app.data.local.dao.UsageHourBucket
import com.shredcoach.app.data.local.dao.UsageTotals
import com.shredcoach.app.domain.llm.AiAssistant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dashboard premium FAANG-grade de la consommation LLM.
 *
 * Layout en widgets verticalement empilés :
 *  1. Time scale chips (24h / 7j / 30j / All)
 *  2. Hero KPI card (calls / tokens / coût / latency)
 *  3. Daily/hourly chart (évolution)
 *  4. Hourly heatmap 24×7 (quand)
 *  5. Top assistants breakdown (qui)
 *  6. Top models breakdown (quoi)
 *  7. Reset action en footer
 *
 * Design : inspiré Apple Health, Stripe Dashboard, Datadog Lite. Compose
 * Canvas pour les charts custom — aucune lib externe nécessaire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmUsageDashboardScreen(
    navController: NavController,
    viewModel: LlmUsageDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.usage_dashboard_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.totalEventsAllTime > 0) {
                        IconButton(onClick = { showResetConfirm = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                stringResource(R.string.usage_dashboard_reset_cd),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { TimeScaleChips(state.scale, viewModel::setScale) }

            if (state.isEmpty && !state.isLoading) {
                item { EmptyState() }
            } else {
                item { HeroKpiCard(state.totals) }
                if (state.dailySeries.isNotEmpty()) {
                    item { EvolutionChart(state.dailySeries) }
                }
                if (state.hourlyHeatmap.isNotEmpty()) {
                    item { HourlyHeatmap(state.hourlyHeatmap) }
                }
                if (state.byAssistant.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.usage_section_by_assistant), Icons.Default.Insights) }
                    items(state.byAssistant.take(10), key = { it.assistantKey }) { row ->
                        AssistantRow(row, totalTokens = state.totals.totalTokens)
                    }
                }
                if (state.byModel.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.usage_section_by_model), Icons.Default.Token) }
                    items(state.byModel.take(8), key = { "${it.provider}/${it.model}" }) { row ->
                        ModelRow(row, totalTokens = state.totals.totalTokens)
                    }
                }
                if (state.byProvider.isNotEmpty()) {
                    item { ProviderBreakdown(state.byProvider, state.totals.totalTokens) }
                }
            }

            // Footer
            item {
                Spacer(Modifier.height(8.dp))
                FooterMeta(
                    totalAllTime = state.totalEventsAllTime,
                    earliestStr = state.earliestTimestamp?.format(
                        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
                    ),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.usage_dashboard_reset_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.usage_dashboard_reset_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllHistory()
                    showResetConfirm = false
                }) {
                    Text(stringResource(R.string.usage_dashboard_reset_confirm),
                        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// TIME SCALE CHIPS — segmented control style
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TimeScaleChips(
    current: UsageTimeScale,
    onSelected: (UsageTimeScale) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            UsageTimeScale.values().forEach { scale ->
                val selected = scale == current
                Surface(
                    onClick = { onSelected(scale) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(timeScaleLabel(scale)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.3.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun timeScaleLabel(scale: UsageTimeScale): Int = when (scale) {
    UsageTimeScale.DAY_24H -> R.string.usage_scale_24h
    UsageTimeScale.DAYS_7 -> R.string.usage_scale_7d
    UsageTimeScale.DAYS_30 -> R.string.usage_scale_30d
    UsageTimeScale.ALL_TIME -> R.string.usage_scale_all
}

// ═══════════════════════════════════════════════════════════════════════════
// HERO KPI CARD — gradient + 4 KPIs glassmorphic
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HeroKpiCard(totals: UsageTotals) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1E3A8A), Color(0xFF6366F1)),
                    )
                ),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Analytics, null, Modifier.size(20.dp), tint = Color.White)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.usage_hero_title).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                        Text(
                            stringResource(R.string.usage_hero_subtitle),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                }
                // KPI grid 2x2
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        icon = Icons.Default.Bolt,
                        label = stringResource(R.string.usage_kpi_calls),
                        value = totals.totalCalls.toString(),
                        unit = "",
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        icon = Icons.Default.Token,
                        label = stringResource(R.string.usage_kpi_tokens),
                        value = formatTokensCompact(totals.totalTokens),
                        unit = "tok",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiTile(
                        icon = Icons.Default.AttachMoney,
                        label = stringResource(R.string.usage_kpi_cost),
                        value = formatCost(totals.totalCostUsd),
                        unit = "USD",
                        modifier = Modifier.weight(1f),
                    )
                    KpiTile(
                        icon = Icons.Default.Schedule,
                        label = stringResource(R.string.usage_kpi_latency),
                        value = "${totals.avgLatencyMs.toInt()}",
                        unit = "ms",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.72f))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EVOLUTION CHART — daily line/area chart
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EvolutionChart(series: List<UsageDayBucket>) {
    val density = LocalDensity.current
    val maxTokens = series.maxOf { it.tokens }.coerceAtLeast(1).toFloat()

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Analytics, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.usage_chart_tokens_evolution),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val n = series.size
                    if (n == 0) return@Canvas
                    fun y(value: Int): Float = h - (value.toFloat() / maxTokens) * h
                    fun x(idx: Int): Float = if (n <= 1) w / 2 else (idx.toFloat() / (n - 1)) * w

                    // Gridlines horizontales discrètes
                    val gridColor = Color(0xFF6366F1).copy(alpha = 0.08f)
                    for (frac in listOf(0.25f, 0.5f, 0.75f)) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * frac), end = Offset(w, h * frac),
                            strokeWidth = with(density) { 0.5.dp.toPx() },
                        )
                    }

                    if (n >= 2) {
                        val stroke = Path().apply {
                            moveTo(x(0), y(series[0].tokens))
                            for (i in 1 until n) lineTo(x(i), y(series[i].tokens))
                        }
                        val fill = Path().apply {
                            addPath(stroke)
                            lineTo(x(n - 1), h)
                            lineTo(x(0), h)
                            close()
                        }
                        drawPath(
                            path = fill,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0xFF6366F1).copy(alpha = 0.32f),
                                    Color(0xFF6366F1).copy(alpha = 0.02f),
                                ),
                            ),
                        )
                        drawPath(
                            path = stroke,
                            color = Color(0xFF6366F1),
                            style = Stroke(width = with(density) { 2.5.dp.toPx() }),
                        )
                        // Data point dots
                        for (i in 0 until n) {
                            drawCircle(
                                color = Color.White,
                                radius = with(density) { 4.dp.toPx() },
                                center = Offset(x(i), y(series[i].tokens)),
                            )
                            drawCircle(
                                color = Color(0xFF6366F1),
                                radius = with(density) { 3.dp.toPx() },
                                center = Offset(x(i), y(series[i].tokens)),
                            )
                        }
                    } else {
                        // Single point : draw a dot
                        drawCircle(
                            color = Color(0xFF6366F1),
                            radius = with(density) { 5.dp.toPx() },
                            center = Offset(x(0), y(series[0].tokens)),
                        )
                    }
                }
            }
            // Légende min/max
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${formatTokensCompact(0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    "${formatTokensCompact(maxTokens.toInt())}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HOURLY HEATMAP — 24 hours × 7 days grid
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HourlyHeatmap(buckets: List<UsageHourBucket>) {
    val maxCalls = buckets.maxOfOrNull { it.calls }?.coerceAtLeast(1) ?: 1
    // Index buckets by (hour, dayOfWeek) — SQLite '%w' = 0 (Sunday) → 6 (Saturday).
    // On veut afficher Mon → Sun (style EU).
    val indexed = buckets.associateBy { it.hour to it.dayOfWeek }

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.usage_chart_when),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            // 7 jours (Mon..Sun) × 24 heures
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val daysOrder = listOf(1, 2, 3, 4, 5, 6, 0) // SQLite : 1=Mon..6=Sat, 0=Sun
                val daysLabels = stringResource(R.string.usage_days_short).split(",")
                for ((idx, dow) in daysOrder.withIndex()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            daysLabels.getOrNull(idx) ?: "?",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            modifier = Modifier.width(20.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            for (h in 0..23) {
                                val calls = indexed[h to dow]?.calls ?: 0
                                val intensity = if (maxCalls > 0) calls.toFloat() / maxCalls else 0f
                                val color = Color(0xFF6366F1).copy(alpha = (0.10f + intensity * 0.85f).coerceIn(0.10f, 0.95f))
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (calls == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            else color
                                        )
                                )
                            }
                        }
                    }
                }
                // Heures axes labels (only 00, 06, 12, 18)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Spacer(Modifier.width(20.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00", "06", "12", "18", "23").forEach {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LIST ROWS — assistant / model breakdowns
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(label: String, icon: ImageVector) {
    Row(
        Modifier.padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun AssistantRow(row: UsageByAssistant, totalTokens: Int) {
    val assistant = AiAssistant.fromKey(row.assistantKey)
    val displayName = assistant?.let { stringResource(assistantLabelRes(it)) } ?: row.assistantKey
    val fraction = if (totalTokens > 0) row.tokens.toFloat() / totalTokens else 0f
    BreakdownRow(
        title = displayName,
        subtitle = "${row.calls} ${stringResource(R.string.usage_calls_unit)} · ${formatTokensCompact(row.tokens)} tok",
        rightLabel = formatCost(row.costUsd),
        fraction = fraction,
        tint = Color(0xFF6366F1),
    )
}

@Composable
private fun ModelRow(row: UsageByModel, totalTokens: Int) {
    val modelInfo = com.shredcoach.app.domain.llm.LlmCatalog.modelInfo(row.model)
    val fraction = if (totalTokens > 0) row.tokens.toFloat() / totalTokens else 0f
    BreakdownRow(
        title = modelInfo?.displayName ?: row.model,
        subtitle = "${row.provider} · ${row.calls} ${stringResource(R.string.usage_calls_unit)} · ${formatTokensCompact(row.tokens)} tok",
        rightLabel = formatCost(row.costUsd),
        fraction = fraction,
        tint = Color(0xFF10B981),
    )
}

@Composable
private fun BreakdownRow(
    title: String,
    subtitle: String,
    rightLabel: String,
    fraction: Float,
    tint: Color,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
                Text(
                    rightLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
            // Bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint.copy(alpha = 0.12f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(tint),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PROVIDER BREAKDOWN — stacked horizontal bar
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ProviderBreakdown(providers: List<UsageByProvider>, totalTokens: Int) {
    val colors = mapOf(
        "GEMINI" to Color(0xFF34A853),
        "GROQ" to Color(0xFFF59E0B),
        "MISTRAL" to Color(0xFFFF7000),
        "OPENAI" to Color(0xFF10A37F),
        "CLAUDE" to Color(0xFFCC785C),
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Insights, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.usage_section_by_provider),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                providers.forEach { p ->
                    val fraction = if (totalTokens > 0) p.tokens.toFloat() / totalTokens else 0f
                    if (fraction > 0) {
                        Box(
                            Modifier
                                .weight(fraction.coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .background(colors[p.provider] ?: Color.Gray)
                        )
                    }
                }
            }
            // Légende
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                providers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors[p.provider] ?: Color.Gray)
                        )
                        Text(
                            p.provider,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${formatTokensCompact(p.tokens)} tok · ${formatCost(p.costUsd)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FOOTER + EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FooterMeta(totalAllTime: Int, earliestStr: String?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            buildString {
                append("$totalAllTime ${stringResource(R.string.usage_calls_unit)}")
                if (earliestStr != null) {
                    append(" ").append(stringResource(R.string.usage_since)).append(" ").append(earliestStr)
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun EmptyState() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
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
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Analytics, null,
                        Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                stringResource(R.string.usage_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                stringResource(R.string.usage_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HELPERS
// ═══════════════════════════════════════════════════════════════════════════

private fun formatTokensCompact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

private fun formatCost(usd: Double): String = when {
    usd >= 1.0 -> "$%.2f".format(usd)
    usd >= 0.01 -> "$%.3f".format(usd)
    usd > 0 -> "<$0.01"
    else -> "$0"
}

private fun assistantLabelRes(assistant: AiAssistant): Int = when (assistant) {
    AiAssistant.MEAL_SCAN_PHOTO -> R.string.assistant_meal_scan_photo
    AiAssistant.MEAL_SCAN_TEXT -> R.string.assistant_meal_scan_text
    AiAssistant.MEAL_SCAN_LEFTOVER -> R.string.assistant_meal_scan_leftover
    AiAssistant.BODY_SCAN -> R.string.assistant_body_scan
    AiAssistant.GYM_SCAN -> R.string.assistant_gym_scan
    AiAssistant.GLUCOSE_OCR -> R.string.assistant_glucose_ocr
    AiAssistant.GLUCOSE_ANALYSIS -> R.string.assistant_glucose_analysis
    AiAssistant.BODY_INSIGHT -> R.string.assistant_body_insight
    AiAssistant.WEEKLY_RECAP -> R.string.assistant_weekly_recap
    AiAssistant.CALENDAR_RECAP -> R.string.assistant_calendar_recap
    AiAssistant.CHAT_SHREDDY -> R.string.assistant_chat_shreddy
    AiAssistant.CHAT_DR_GLYKOS -> R.string.assistant_chat_dr_glykos
    AiAssistant.PROACTIVE_COACH -> R.string.assistant_proactive_coach
    AiAssistant.WORKOUT_DEBRIEF -> R.string.assistant_workout_debrief
    AiAssistant.MEAL_DEBRIEF -> R.string.assistant_meal_debrief
    AiAssistant.SCHEDULED_REMINDER -> R.string.assistant_scheduled_reminder
    AiAssistant.GYM_SCAN_RERANK -> R.string.assistant_gym_scan_rerank
    AiAssistant.INSTRUCTIONS_TRANSLATE -> R.string.assistant_instructions_translate
}
