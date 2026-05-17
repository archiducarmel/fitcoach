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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.presentation.navigation.Screen

/** Palette médicale Dr. Glykos — aligné avec TodayGlucoseCard + AiToolsSection. */
private val GlucoseEmerald = Color(0xFF059669)
private val GlucoseEmeraldSoft = Color(0xFFD1FAE5)

/**
 * Écran d'upload du screenshot CGM journalier. Gallery → preview → OCR Gemini →
 * card résultat avec KPIs (avg, pic, TIR, hypos). L'user peut corriger
 * manuellement si l'OCR a raté, ou consulter Dr. Glykos pour l'analyse.
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = GlucoseEmeraldSoft, modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MedicalServices, null,
                                    Modifier.size(20.dp), tint = GlucoseEmerald)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.glucose_entry_title),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(stringResource(R.string.glucose_entry_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ─── Sélecteur de date (premium : flèches + tap pour DatePicker) ──
            // v45.1 : critique pour distinguer le scan J du J-1. Sans ce
            // composant, l'user qui voulait uploader sur un jour passé écrasait
            // silencieusement le log du jour courant.
            DateSelector(
                date = state.date,
                onPrev = { viewModel.setDate(state.date.minusDays(1)) },
                onNext = { viewModel.setDate(state.date.plusDays(1)) },
                onPick = { showDatePicker = true },
            )

            // ─── Upload zone ou résultat ──────────────────────
            val log = state.log
            val preview = state.previewBitmap
            when {
                state.isUploading -> UploadingCard()
                preview != null && log == null -> {
                    // L'image vient d'être sélectionnée, pas encore OCRisée
                    PreviewCard(
                        previewBitmap = preview,
                        onAnalyze = { viewModel.analyzeAndSave() },
                        onCancel = { viewModel.cancelPreview() },
                    )
                }
                log != null && log.hasAnyMetric() -> {
                    ResultCard(
                        log = log,
                        onOpenOverride = { viewModel.openManualOverride() },
                        onOpenDrGlykos = {
                            navController.navigate(Screen.DrGlykosChat.route)
                        },
                        onReplace = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onDelete = { viewModel.deleteToday() },
                    )
                }
                else -> {
                    EmptyUploadCard(
                        onPickImage = {
                            pickImage.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            }

            state.error?.let { errorMsg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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

/**
 * Sélecteur de date premium : flèches ◀ ▶ pour J-1 / J+1, tap au centre ouvre
 * un DatePicker pour aller plus loin dans le passé.
 *
 * - Plafond futur : LocalDate.now() (on n'autorise pas l'upload "pour demain").
 * - Aujourd'hui : label dédié + couleur d'accent emerald pour réassurance.
 */
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
    val label = date.format(fmt).replaceFirstChar { it.uppercase() }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = GlucoseEmeraldSoft,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, stringResource(R.string.glucose_entry_date_prev_cd),
                    tint = GlucoseEmerald)
            }
            Row(
                Modifier.weight(1f).clickable(onClick = onPick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.CalendarMonth, null,
                    Modifier.size(18.dp), tint = GlucoseEmerald)
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = GlucoseEmerald,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (isToday) {
                        Text(stringResource(R.string.glucose_entry_date_today),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlucoseEmerald.copy(alpha = 0.75f))
                    }
                }
            }
            IconButton(onClick = onNext, enabled = !isFuture && date != today) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.glucose_entry_date_next_cd),
                    tint = if (!isFuture && date != today) GlucoseEmerald
                        else GlucoseEmerald.copy(alpha = 0.3f))
            }
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
                    // Sécurité : on ne laisse jamais sélectionner une date future
                    val safe = if (picked.isAfter(today)) today else picked
                    onPick(safe)
                } ?: onDismiss()
            }) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold) }
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

@Composable
private fun EmptyUploadCard(onPickImage: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseEmeraldSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.MedicalServices, null,
                Modifier.size(56.dp), tint = GlucoseEmerald)
            Text(stringResource(R.string.glucose_entry_upload_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = GlucoseEmerald)
            Text(stringResource(R.string.glucose_entry_upload_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onPickImage,
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseEmerald),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.glucose_entry_upload_cta),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PreviewCard(
    previewBitmap: android.graphics.Bitmap,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.glucose_entry_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            androidx.compose.foundation.Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 360.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = onAnalyze,
                    colors = ButtonDefaults.buttonColors(containerColor = GlucoseEmerald),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.glucose_entry_analyze_cta),
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UploadingCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GlucoseEmeraldSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = GlucoseEmerald)
            Text(stringResource(R.string.glucose_entry_parsing),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlucoseEmerald)
            Text(stringResource(R.string.glucose_entry_parsing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ResultCard(
    log: GlucoseLogEntity,
    onOpenOverride: () -> Unit,
    onOpenDrGlykos: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MedicalServices, null,
                    Modifier.size(20.dp), tint = GlucoseEmerald)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.glucose_entry_kpi_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlucoseEmerald)
                Spacer(Modifier.weight(1f))
                if (log.manualOverride) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(stringResource(R.string.glucose_entry_manual_pill),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            log.parseConfidence?.takeIf { it < 0.7f && !log.manualOverride }?.let {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Warning, null, Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.glucose_entry_low_confidence,
                        (it * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            // ─── KPIs grid ────────────────────────────────────
            KpiRow(
                label = stringResource(R.string.glucose_entry_kpi_avg),
                value = log.avgMgdl?.let { "${it.toInt()} mg/dL" }
                    ?: stringResource(R.string.glucose_entry_kpi_na),
                ok = log.avgMgdl?.let { it in 80.0..130.0 } ?: false,
            )
            log.peakMgdl?.let { peak ->
                KpiRow(
                    label = stringResource(R.string.glucose_entry_kpi_peak),
                    value = buildString {
                        append("${peak.toInt()} mg/dL")
                        log.peakTime?.let { append(" · ${it}") }
                    },
                    ok = peak < 180,
                )
            }
            log.minMgdl?.let { min ->
                KpiRow(
                    label = stringResource(R.string.glucose_entry_kpi_min),
                    value = buildString {
                        append("${min.toInt()} mg/dL")
                        log.minTime?.let { append(" · ${it}") }
                    },
                    ok = min >= 70,
                )
            }
            log.timeInRangePct?.let {
                KpiRow(
                    label = stringResource(R.string.glucose_entry_kpi_tir),
                    value = "$it%",
                    ok = it >= 70,
                )
            }
            log.hypoCount?.let {
                KpiRow(
                    label = stringResource(R.string.glucose_entry_kpi_hypo),
                    value = it.toString(),
                    ok = it == 0,
                )
            }
            log.cv?.let {
                KpiRow(
                    label = stringResource(R.string.glucose_entry_kpi_cv),
                    value = "${"%.1f".format(it)}%",
                    ok = it < 36.0,
                )
            }

            Divider()

            // ─── Actions ──────────────────────────────────────
            Button(
                onClick = onOpenDrGlykos,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GlucoseEmerald),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.glucose_entry_open_dr_glykos),
                    fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenOverride, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.glucose_entry_manual_cta),
                        style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onReplace, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.glucose_entry_replace_cta),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.glucose_entry_delete_cta),
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun KpiRow(label: String, value: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1)
        val tint = if (ok) Color(0xFF22C55E) else Color(0xFFF59E0B)
        Icon(
            imageVector = if (ok) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
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
                Text(stringResource(R.string.common_save), color = GlucoseEmerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeManualOverride() }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        title = { Text(stringResource(R.string.glucose_entry_manual_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
