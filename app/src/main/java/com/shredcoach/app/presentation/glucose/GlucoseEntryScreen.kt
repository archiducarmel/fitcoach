package com.shredcoach.app.presentation.glucose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.presentation.navigation.Screen

/**
 * Page d'entrée CGM glycémique : sélection de date → upload screenshot →
 * OCR Gemini → résultat avec KPI tiles + status pills + actions.
 *
 * **Design** : palette emerald 50/100/600/800 + tiles premium (GlucoseDesignSystem).
 * Aucune surface "white-on-light" → contraste WCAG AAA partout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseEntryScreen(
    navController: NavController,
    viewModel: GlucoseEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ctx = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.onImageSelected(it) } }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    GlucoseSectionHeader(
                        icon = Icons.Default.MonitorHeart,
                        title = stringResource(R.string.glucose_entry_title),
                        subtitle = stringResource(R.string.glucose_entry_subtitle),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            DateSelector(
                date = state.date,
                onPrev = { viewModel.setDate(state.date.minusDays(1)) },
                onNext = { viewModel.setDate(state.date.plusDays(1)) },
                onPick = { showDatePicker = true },
            )

            val log = state.log
            val preview = state.previewBitmap
            when {
                state.isUploading -> UploadingCard()
                preview != null && log == null -> PreviewCard(
                    previewBitmap = preview,
                    onAnalyze = { viewModel.analyzeAndSave() },
                    onCancel = { viewModel.cancelPreview() },
                )
                log != null && log.hasAnyMetric() -> ResultCard(
                    log = log,
                    onOpenOverride = { viewModel.openManualOverride() },
                    onOpenDrGlykos = { navController.navigate(Screen.DrGlykosChat.route) },
                    onReplace = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDelete = { viewModel.deleteToday() },
                )
                else -> EmptyUploadCard(
                    onPickImage = {
                        pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
            }

            state.error?.let { errorMsg ->
                ErrorBanner(message = errorMsg)
            }

            Spacer(Modifier.height(16.dp))
        }

        if (state.showManualOverride) {
            ManualOverrideDialog(state = state, viewModel = viewModel)
        }

        if (showDatePicker) {
            DatePickerSheet(
                initial = state.date,
                onPick = { picked ->
                    viewModel.setDate(picked)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DATE SELECTOR — premium pill with prev/next + tap-to-pick
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelector(
    date: java.time.LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    val today = java.time.LocalDate.now()
    val isToday = date == today
    val isFuture = date.isAfter(today)
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    val label = date.format(fmt).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    val nextEnabled = !isFuture && date != today

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = GlucoseColors.Emerald50,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateNavButton(
                icon = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.glucose_entry_date_prev_cd),
                onClick = onPrev,
                enabled = true,
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPick)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    Modifier.size(16.dp), tint = GlucoseColors.Emerald600)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = GlucoseColors.Emerald800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isToday) {
                        Text(
                            stringResource(R.string.glucose_entry_date_today).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlucoseColors.Emerald600,
                        )
                    }
                }
            }
            DateNavButton(
                icon = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.glucose_entry_date_next_cd),
                onClick = onNext,
                enabled = nextEnabled,
            )
        }
    }
}

@Composable
private fun DateNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) Color.White else Color.Transparent,
        modifier = Modifier.size(40.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                icon,
                contentDescription,
                Modifier.size(20.dp),
                tint = if (enabled) GlucoseColors.Emerald600
                    else GlucoseColors.Emerald600.copy(alpha = 0.25f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(
    initial: java.time.LocalDate,
    onPick: (java.time.LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val today = java.time.LocalDate.now()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val picked = java.time.Instant.ofEpochMilli(ms)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    val safe = if (picked.isAfter(today)) today else picked
                    onPick(safe)
                } ?: onDismiss()
            }) { Text(stringResource(R.string.common_save), color = GlucoseColors.Emerald600, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun GlucoseLogEntity.hasAnyMetric(): Boolean =
    avgMgdl != null || peakMgdl != null || timeInRangePct != null || imagePath != null

// ═══════════════════════════════════════════════════════════════════════════
// EMPTY UPLOAD — hero CTA encouraging the user to start
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyUploadCard(onPickImage: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon avec halo subtle
            Surface(
                shape = CircleShape,
                color = GlucoseColors.Emerald100,
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MonitorHeart, null,
                        Modifier.size(40.dp),
                        tint = GlucoseColors.Emerald600,
                    )
                }
            }
            Text(
                stringResource(R.string.glucose_entry_upload_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = GlucoseColors.Emerald800,
            )
            Text(
                stringResource(R.string.glucose_entry_upload_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = GlucoseColors.Emerald800.copy(alpha = 0.72f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onPickImage,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlucoseColors.Emerald600,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.glucose_entry_upload_cta),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PREVIEW — image juste sélectionnée, en attente d'analyse
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PreviewCard(
    previewBitmap: android.graphics.Bitmap,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(R.string.glucose_entry_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = GlucoseColors.Emerald800,
            )
            androidx.compose.foundation.Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = onAnalyze,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseColors.Emerald600),
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.glucose_entry_analyze_cta),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// UPLOADING — analyse en cours
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun UploadingCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseColors.Emerald50),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(28.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = GlucoseColors.Emerald600,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.glucose_entry_parsing),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = GlucoseColors.Emerald800,
            )
            Text(
                stringResource(R.string.glucose_entry_parsing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = GlucoseColors.Emerald800.copy(alpha = 0.7f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RESULT — KPIs avec status + actions
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ResultCard(
    log: GlucoseLogEntity,
    onOpenOverride: () -> Unit,
    onOpenDrGlykos: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Header avec icône + titre + manual pill éventuel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = GlucoseColors.Emerald100,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MedicalServices, null,
                            Modifier.size(20.dp),
                            tint = GlucoseColors.Emerald600,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.glucose_entry_kpi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = GlucoseColors.Emerald800,
                    modifier = Modifier.weight(1f),
                )
                if (log.manualOverride) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.glucose_entry_manual_pill),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Low confidence banner
            log.parseConfidence?.takeIf { it < 0.7f && !log.manualOverride }?.let {
                LowConfidenceBanner(percent = (it * 100).toInt())
            }

            // ─── KPI grid 2x3 ──────────────────────────────────────────────
            // Ligne 1 : Moyenne / Pic / Minimum
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_avg),
                    value = log.avgMgdl?.let { "${it.toInt()}" } ?: "—",
                    unit = "mg/dL",
                    status = GlucoseStatus.forAvg(log.avgMgdl),
                    modifier = Modifier.weight(1f),
                )
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_peak),
                    value = log.peakMgdl?.let { "${it.toInt()}" } ?: "—",
                    unit = "mg/dL",
                    status = GlucoseStatus.forPeak(log.peakMgdl),
                    modifier = Modifier.weight(1f),
                )
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_min),
                    value = log.minMgdl?.let { "${it.toInt()}" } ?: "—",
                    unit = "mg/dL",
                    status = GlucoseStatus.forMin(log.minMgdl),
                    modifier = Modifier.weight(1f),
                )
            }
            // Ligne 2 : TIR / Hypos / CV
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_tir),
                    value = log.timeInRangePct?.let { "$it" } ?: "—",
                    unit = "%",
                    status = GlucoseStatus.forTir(log.timeInRangePct),
                    modifier = Modifier.weight(1f),
                )
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_hypo),
                    value = log.hypoCount?.let { "$it" } ?: "—",
                    unit = "",
                    status = GlucoseStatus.forHypoCount(log.hypoCount),
                    modifier = Modifier.weight(1f),
                )
                GlucoseKpiTile(
                    label = stringResource(R.string.glucose_entry_kpi_cv),
                    value = log.cv?.let { "%.1f".format(it) } ?: "—",
                    unit = "%",
                    status = GlucoseStatus.forCv(log.cv),
                    modifier = Modifier.weight(1f),
                )
            }

            // ─── Action principale : Dr. Glykos ────────────────────────────
            Button(
                onClick = onOpenDrGlykos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlucoseColors.Emerald600,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.MedicalServices, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.glucose_entry_open_dr_glykos),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
            }

            // ─── Actions secondaires ────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedActionButton(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.glucose_entry_manual_cta),
                    onClick = onOpenOverride,
                    modifier = Modifier.weight(1f),
                )
                OutlinedActionButton(
                    icon = Icons.Default.Refresh,
                    label = stringResource(R.string.glucose_entry_replace_cta),
                    onClick = onReplace,
                    modifier = Modifier.weight(1f),
                )
            }
            // Bouton delete subtil
            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.glucose_entry_delete_cta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun OutlinedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlucoseColors.Emerald200.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GlucoseColors.Emerald700),
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LowConfidenceBanner(percent: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GlucoseColors.Warning.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlucoseColors.Warning.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = GlucoseColors.Warning)
            Text(
                stringResource(R.string.glucose_entry_low_confidence, percent),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlucoseColors.Warning,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Warning, null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ManualOverrideDialog(
    state: GlucoseEntryState,
    viewModel: GlucoseEntryViewModel,
) {
    AlertDialog(
        onDismissRequest = { viewModel.closeManualOverride() },
        confirmButton = {
            TextButton(onClick = { viewModel.submitManualOverride() }) {
                Text(
                    stringResource(R.string.common_save),
                    color = GlucoseColors.Emerald600,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeManualOverride() }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        title = {
            Text(
                stringResource(R.string.glucose_entry_manual_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.manualAvg, onValueChange = viewModel::setManualAvg,
                    label = { Text(stringResource(R.string.glucose_entry_kpi_avg) + " (mg/dL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.manualPeak, onValueChange = viewModel::setManualPeak,
                    label = { Text(stringResource(R.string.glucose_entry_kpi_peak) + " (mg/dL)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.manualTir, onValueChange = viewModel::setManualTir,
                    label = { Text(stringResource(R.string.glucose_entry_kpi_tir) + " (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.manualHypoCount, onValueChange = viewModel::setManualHypoCount,
                    label = { Text(stringResource(R.string.glucose_entry_kpi_hypo)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }
    )
}
