package com.shredcoach.app.presentation.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Bottom sheet création
    if (state.showScheduleSheet) {
        QuickScheduleSheet(
            state = state,
            onDismiss = { viewModel.closeScheduleSheet() },
            onConfirm = { date, time, workoutId, title, note ->
                viewModel.scheduleSession(date, time, workoutId, title, note)
            }
        )
    }

    // Bottom sheet suggestions IA
    if (state.suggestedDates.isNotEmpty() || state.isSuggesting) {
        AiSuggestionsSheet(
            isLoading = state.isSuggesting,
            suggestedDates = state.suggestedDates,
            message = state.suggestionMessage,
            onAccept = { viewModel.acceptAllSuggestions() },
            onDismiss = { viewModel.dismissSuggestions() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(22.dp), tint = OrangeVibrant)
                        Text("Calendrier", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToday() }) {
                        Icon(Icons.Default.Today, "Aujourd'hui", tint = OrangeVibrant)
                    }
                    IconButton(onClick = { viewModel.suggestNextSessions() }) {
                        Icon(Icons.Default.AutoAwesome, "Suggestion IA", tint = NeonGreen)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openScheduleSheet(state.selectedDate) },
                containerColor = OrangeVibrant,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Planifier", fontWeight = FontWeight.Bold)
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
        ) {
            // Stats header (streak + assiduité + prochaine séance)
            CalendarStatsHeader(state)

            // Navigation mois
            MonthNavigator(
                month = state.currentMonth,
                onPrev = { viewModel.goPrevMonth() },
                onNext = { viewModel.goNextMonth() }
            )

            // Grid 7x6
            MonthGrid(
                month = state.currentMonth,
                selectedDate = state.selectedDate,
                scheduled = state.monthScheduled,
                logs = state.monthLogs,
                holidays = state.holidays,
                schoolHolidays = state.schoolHolidays,
                workoutDays = state.workoutDays,
                onDayClick = { viewModel.selectDate(it) }
            )

            // Panel détails jour sélectionné
            state.selectedDate?.let { date ->
                DayDetailsPanel(
                    date = date,
                    scheduled = state.monthScheduled.filter { it.date == date },
                    logs = state.monthLogs.filter { it.date.toLocalDate() == date },
                    holiday = state.holidays[date],
                    isSchoolHoliday = date in state.schoolHolidays,
                    onAddClick = { viewModel.openScheduleSheet(date) },
                    onDeleteSchedule = { viewModel.deleteSchedule(it) },
                    onMarkSkipped = { viewModel.markSkipped(it) },
                    onStartSession = { sched ->
                        // Lancer directement via WorkoutGenerator si pas de workoutId, sinon session
                        if (sched.workoutId != null) {
                            // Note : lancer depuis un favori nécessiterait un flow dédié
                            // Pour MVP, on ouvre le preview du favori
                            navController.navigate(Screen.FavoritePreview.createRoute(sched.workoutId))
                        } else {
                            navController.navigate(Screen.WorkoutGenerator.route)
                        }
                    }
                )
            }

            Spacer(Modifier.height(100.dp)) // espace FAB
        }
    }
}

// ═══════════════════════════════════════
// HEADER STATS (streak + assiduité + prochaine)
// ═══════════════════════════════════════

@Composable
private fun CalendarStatsHeader(state: CalendarState) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(OrangeVibrant.copy(alpha = 0.95f), OrangeVibrant.copy(alpha = 0.75f))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Streak
                Column {
                    Text("Streak", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${state.streakDays}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("jours", style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                VerticalDividerLight()
                // Assiduité
                Column {
                    Text("Assiduité", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${state.adherencePercent}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("%", style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Text("${state.completedThisMonth}/${state.plannedThisMonth.coerceAtLeast(state.completedThisMonth)} ce mois",
                        style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                }
                VerticalDividerLight()
                // Prochaine séance
                Column(Modifier.weight(1f)) {
                    Text("Prochaine", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    val next = state.nextUpcoming
                    if (next != null) {
                        val dayLabel = next.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRANCE))
                        val timeLabel = next.time?.let { " · ${it.toString().substring(0, 5)}" } ?: ""
                        Text(
                            dayLabel.replaceFirstChar { it.uppercase() } + timeLabel,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 2, lineHeight = 16.sp
                        )
                    } else {
                        Text("Aucune", style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalDividerLight() {
    Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.25f)))
}

// ═══════════════════════════════════════
// NAVIGATION MOIS
// ═══════════════════════════════════════

@Composable
private fun MonthNavigator(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, "Mois précédent", tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, "Mois suivant", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ═══════════════════════════════════════
// GRILLE MENSUELLE 7×6
// ═══════════════════════════════════════

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate?,
    scheduled: List<ScheduledWorkoutEntity>,
    logs: List<com.shredcoach.app.data.local.entity.WorkoutLogEntity>,
    holidays: Map<LocalDate, String>,
    schoolHolidays: Set<LocalDate>,
    workoutDays: Set<Int>,
    onDayClick: (LocalDate) -> Unit
) {
    // En-têtes jours (L M M J V S D en commençant par lundi)
    val dayNames = listOf("L", "M", "M", "J", "V", "S", "D")
    val firstOfMonth = month.atDay(1)
    // Offset : 0 si lundi, 6 si dimanche
    val leadingEmpty = (firstOfMonth.dayOfWeek.value - 1) // DayOfWeek: Lundi=1, Dimanche=7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingEmpty + daysInMonth
    val rows = ((totalCells + 6) / 7).coerceAtLeast(5) // minimum 5 lignes pour stabilité visuelle

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        // Headers
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            dayNames.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        // Cells
        val today = LocalDate.now()
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingEmpty + 1

                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val daySchedules = scheduled.filter { it.date == date }
                        val dayLogs = logs.filter { it.date.toLocalDate() == date }
                        val isToday = date == today
                        val isSelected = date == selectedDate
                        val isHoliday = holidays.containsKey(date)
                        val isSchoolHoliday = date in schoolHolidays
                        val isWorkoutDay = (date.dayOfWeek.value in workoutDays)

                        DayCell(
                            date = date,
                            isToday = isToday,
                            isSelected = isSelected,
                            isHoliday = isHoliday,
                            isSchoolHoliday = isSchoolHoliday,
                            isWorkoutDay = isWorkoutDay,
                            scheduleCount = daySchedules.size,
                            hasCompleted = dayLogs.any { it.completed },
                            hasPlanned = daySchedules.any { it.status == "PLANNED" },
                            hasSkipped = daySchedules.any { it.status == "SKIPPED" },
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) }
                        )
                    } else {
                        Box(Modifier.weight(1f).height(54.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    isHoliday: Boolean,
    isSchoolHoliday: Boolean,
    isWorkoutDay: Boolean,
    scheduleCount: Int,
    hasCompleted: Boolean,
    hasPlanned: Boolean,
    hasSkipped: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.92f,
        animationSpec = tween(200),
        label = "selScale"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isSelected -> OrangeVibrant.copy(alpha = 0.15f)
                    isSchoolHoliday -> Color(0xFF8B5CF6).copy(alpha = 0.08f) // light purple
                    isHoliday -> OrangeVibrant.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Numéro du jour
            Box(
                Modifier
                    .size(if (isToday) 30.dp else 28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isToday -> OrangeVibrant
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = when {
                        isToday -> Color.White
                        isHoliday -> OrangeVibrant
                        isWorkoutDay && !hasCompleted && !hasPlanned -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }

            // Indicateurs : dots sous le jour
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasCompleted) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(NeonGreen))
                }
                if (hasPlanned && !hasCompleted) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(OrangeVibrant))
                }
                if (hasSkipped) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFEF4444).copy(alpha = 0.6f)))
                }
                // Si c'est un workoutDay sans rien de planifié → petit tiret discret
                if (isWorkoutDay && !hasCompleted && !hasPlanned && !hasSkipped && !isToday) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.4f)))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// PANEL DÉTAILS JOUR
// ═══════════════════════════════════════

@Composable
private fun DayDetailsPanel(
    date: LocalDate,
    scheduled: List<ScheduledWorkoutEntity>,
    logs: List<com.shredcoach.app.data.local.entity.WorkoutLogEntity>,
    holiday: String?,
    isSchoolHoliday: Boolean,
    onAddClick: () -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    onMarkSkipped: (Long) -> Unit,
    onStartSession: (ScheduledWorkoutEntity) -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header du jour
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                date.format(fmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (holiday != null) {
                TagBadge(holiday, OrangeVibrant.copy(alpha = 0.15f), OrangeVibrant)
            }
            if (isSchoolHoliday) {
                TagBadge("Vacances", Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6))
            }
        }

        // Séances planifiées
        if (scheduled.isEmpty() && logs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text("Aucune séance ce jour", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onAddClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = OrangeVibrant.copy(alpha = 0.15f)
                        )
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = OrangeVibrant)
                        Spacer(Modifier.width(4.dp))
                        Text("Planifier une séance", color = OrangeVibrant, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            scheduled.forEach { sched ->
                ScheduleCard(
                    sched = sched,
                    onDelete = { onDeleteSchedule(sched.id) },
                    onSkip = { onMarkSkipped(sched.id) },
                    onStart = { onStartSession(sched) }
                )
            }
            // Logs historiques (séances réalisées ce jour)
            logs.forEach { log ->
                LogCard(log)
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    sched: ScheduledWorkoutEntity,
    onDelete: () -> Unit,
    onSkip: () -> Unit,
    onStart: () -> Unit
) {
    val statusColor = when (sched.status) {
        "COMPLETED" -> NeonGreen
        "SKIPPED" -> Color(0xFFEF4444)
        "CANCELED" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> OrangeVibrant
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (sched.status) {
                        "COMPLETED" -> Icons.Default.CheckCircle
                        "SKIPPED" -> Icons.Default.Cancel
                        else -> Icons.Default.FitnessCenter
                    },
                    null, Modifier.size(22.dp), tint = statusColor
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        sched.title.ifBlank { "Séance" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Pill routine — visible en un coup d'œil pour distinguer
                    // "Push jeudi" / "Pull samedi". Bord coloré statusColor pour
                    // rester cohérent avec le code visuel de la card.
                    val routine = RoutineCatalog.byId(sched.routineId)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(routine.icon, fontSize = 10.sp)
                            Text(
                                routine.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
                val subtitle = buildString {
                    sched.time?.let { append(it.toString().substring(0, 5)) }
                    if (sched.note.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(sched.note)
                    }
                    if (sched.status != "PLANNED") {
                        if (isNotEmpty()) append(" · ")
                        append(when (sched.status) {
                            "COMPLETED" -> "Terminée"
                            "SKIPPED" -> "Non faite"
                            else -> "Annulée"
                        })
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                // Actions uniquement si PLANNED
                if (sched.status == "PLANNED") {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Lancer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Text("Skip", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.Delete, "Supprimer la séance planifiée", Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: com.shredcoach.app.data.local.entity.WorkoutLogEntity) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = NeonGreen)
            }
            Column(Modifier.weight(1f)) {
                Text("Séance terminée", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                val duration = log.actualDurationSeconds / 60
                Text("${log.totalSets} séries · ${duration} min · ${log.totalVolume.toInt()}kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun TagBadge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
