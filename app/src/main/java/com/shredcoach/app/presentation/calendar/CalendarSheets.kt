package com.shredcoach.app.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
// QUICK SCHEDULE SHEET — Création rapide de séance
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickScheduleSheet(
    state: CalendarState,
    onDismiss: () -> Unit,
    onConfirm: (date: LocalDate, time: LocalTime?, workoutId: Long?, title: String, note: String) -> Unit
) {
    val initialDate = state.prefillDate ?: LocalDate.now()
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(LocalTime.of(18, 0)) }
    var selectedWorkout by remember { mutableStateOf<WorkoutEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var typeIndex by remember { mutableStateOf(0) } // 0 = auto/générée, 1 = favori, 2 = libre

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = selectedTime?.hour ?: 18,
        initialMinute = selectedTime?.minute ?: 0,
        is24Hour = true
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annuler") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annuler") } },
            title = { Text("Heure de la séance") },
            text = { TimePicker(state = timePickerState) }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.EventAvailable, null, Modifier.size(22.dp), tint = OrangeVibrant)
                Text("Planifier une séance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // ─── Date + heure ───
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Date picker field
                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = OrangeVibrant)
                        Column {
                            Text("Date", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            Text(
                                selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                // Time picker field
                OutlinedCard(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(18.dp), tint = OrangeVibrant)
                        Column(Modifier.weight(1f)) {
                            Text("Heure", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            Text(
                                selectedTime?.toString()?.substring(0, 5) ?: "—",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                            )
                        }
                        if (selectedTime != null) {
                            IconButton(onClick = { selectedTime = null }) {
                                Icon(Icons.Default.Close, "Effacer l'heure", Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            // Note sur les rappels si heure définie
            if (selectedTime != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(NeonGreen.copy(alpha = 0.1f)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Notifications, null, Modifier.size(16.dp), tint = NeonGreen)
                    Text("Rappels auto : shaker 2h avant + start 30min avant",
                        style = MaterialTheme.typography.labelSmall, color = NeonGreen)
                }
            }

            // ─── Type de séance ───
            Text("Type", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "🎲 Générée" to 0,
                    "⭐ Favori" to 1,
                    "⚡ Libre" to 2
                ).forEach { (label, idx) ->
                    val sel = typeIndex == idx
                    Surface(
                        onClick = { typeIndex = idx; if (idx != 1) selectedWorkout = null },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(label,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ─── Si favori : sélection depuis la liste ───
            if (typeIndex == 1) {
                if (state.favoriteWorkouts.isEmpty()) {
                    Text("Aucun favori enregistré. Utilise 'Générée' ou 'Libre'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.favoriteWorkouts.forEach { w ->
                            val sel = selectedWorkout?.id == w.id
                            Surface(
                                onClick = { selectedWorkout = w; title = w.name },
                                shape = RoundedCornerShape(10.dp),
                                color = if (sel) OrangeVibrant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = if (sel) BorderStroke(1.5.dp, OrangeVibrant) else null
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(w.name, style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.onSurface)
                                    Text("${w.durationMinutes}min · ${w.exerciseCount} exos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                                }
                            }
                        }
                    }
                }
            }

            // ─── Titre + note ───
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titre (optionnel)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optionnel)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            // ─── Bouton confirmer ───
            Button(
                onClick = {
                    val workoutId = if (typeIndex == 1) selectedWorkout?.id else null
                    val finalTitle = title.ifBlank {
                        when (typeIndex) { 1 -> selectedWorkout?.name ?: "Séance favorite"; 2 -> "Séance libre"; else -> "Séance générée" }
                    }
                    onConfirm(selectedDate, selectedTime, workoutId, finalTitle, note)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Planifier", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// AI SUGGESTIONS SHEET — Shreddy propose prochaines dates
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSuggestionsSheet(
    isLoading: Boolean,
    suggestedDates: List<LocalDate>,
    message: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header avec avatar Shreddy
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = CircleShape,
                    color = OrangeVibrant.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(22.dp), tint = OrangeVibrant)
                    }
                }
                Column {
                    Text("Suggestions Shreddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Basé sur tes habitudes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }

            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = OrangeVibrant, strokeWidth = 2.dp)
                    Text("Shreddy réfléchit…", style = MaterialTheme.typography.bodyMedium, color = OrangeVibrant)
                }
            } else {
                // Message du coach
                if (message.isNotBlank()) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))
                    ) {
                        Text(message, modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Liste des dates suggérées
                Text("Dates proposées", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                suggestedDates.forEach { date ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                .background(OrangeVibrant.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${date.dayOfMonth}", fontWeight = FontWeight.ExtraBold, color = OrangeVibrant)
                        }
                        Column {
                            Text(
                                date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRANCE)
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                            )
                            Text(
                                date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                // Actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Plus tard") }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        enabled = suggestedDates.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tout accepter", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
