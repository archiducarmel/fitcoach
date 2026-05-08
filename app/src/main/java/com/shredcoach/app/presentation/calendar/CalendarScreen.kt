package com.shredcoach.app.presentation.calendar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
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

private val PurpleSchool = Color(0xFF8B5CF6)
private val ErrorRed = Color(0xFFEF4444)
private val InfoBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navController: NavController,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    if (state.showScheduleSheet) {
        QuickScheduleSheet(
            state = state,
            onDismiss = { viewModel.closeScheduleSheet() },
            onConfirm = { date, time, workoutId, title, note ->
                viewModel.scheduleSession(date, time, workoutId, title, note)
            }
        )
    }

    if (state.suggestedDates.isNotEmpty() || state.isSuggesting) {
        AiSuggestionsSheet(
            isLoading = state.isSuggesting,
            suggestedDates = state.suggestedDates,
            message = state.suggestionMessage,
            onAccept = { viewModel.acceptAllSuggestions() },
            onDismiss = { viewModel.dismissSuggestions() }
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(22.dp), tint = OrangeVibrant)
                        Text(stringResource(R.string.calendar_title), fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToday() }) {
                        Icon(Icons.Default.Today, stringResource(R.string.calendar_today_cd), tint = OrangeVibrant)
                    }
                    IconButton(onClick = { viewModel.suggestNextSessions() }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            stringResource(R.string.calendar_ai_suggestion_cd),
                            tint = NeonGreen,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openScheduleSheet(state.selectedDate) },
                containerColor = OrangeVibrant,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calendar_fab_schedule), fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ─── HERO : prochaine séance avec countdown ───
            item {
                NextSessionHeroCard(
                    next = state.nextUpcoming,
                    isSuggesting = state.isSuggesting,
                    onPlan = { viewModel.openScheduleSheet() },
                    onAi = { viewModel.suggestNextSessions() },
                    onStart = { sched ->
                        if (sched.workoutId != null) {
                            navController.navigate(Screen.FavoritePreview.createRoute(sched.workoutId))
                        } else {
                            navController.navigate(Screen.WorkoutGenerator.route)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ─── Métriques : adherence ring + streak + completed ───
            item {
                MonthMetricsCard(
                    adherencePercent = state.adherencePercent,
                    completedThisMonth = state.completedThisMonth,
                    plannedThisMonth = state.plannedThisMonth.coerceAtLeast(state.completedThisMonth),
                    streakDays = state.streakDays,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ─── Mois courant : navigateur + grille ───
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    MonthNavigator(
                        month = state.currentMonth,
                        onPrev = { viewModel.goPrevMonth() },
                        onNext = { viewModel.goNextMonth() },
                    )
                    MonthGrid(
                        month = state.currentMonth,
                        selectedDate = state.selectedDate,
                        scheduled = state.monthScheduled,
                        logs = state.monthLogs,
                        holidays = state.holidays,
                        schoolHolidays = state.schoolHolidays,
                        workoutDays = state.workoutDays,
                        onDayClick = { viewModel.selectDate(it) },
                    )
                    LegendStrip(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            // ─── Détails du jour sélectionné (pliable) ───
            state.selectedDate?.let { date ->
                item {
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
                            if (sched.workoutId != null) {
                                navController.navigate(Screen.FavoritePreview.createRoute(sched.workoutId))
                            } else {
                                navController.navigate(Screen.WorkoutGenerator.route)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            // ─── Timeline À venir (toutes périodes) ───
            item {
                UpcomingTimeline(
                    upcoming = state.upcomingSessions,
                    onClickDate = {
                        viewModel.selectDate(it)
                        // Si la date est hors mois courant, on jump
                        val ym = YearMonth.from(it)
                        if (ym != state.currentMonth) {
                            // VM gère via loadMonth
                        }
                    },
                    onPlan = { viewModel.openScheduleSheet() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// HERO : prochaine séance avec countdown ergonomique
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun NextSessionHeroCard(
    next: ScheduledWorkoutEntity?,
    isSuggesting: Boolean,
    onPlan: () -> Unit,
    onAi: () -> Unit,
    onStart: (ScheduledWorkoutEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        if (next == null) {
            // ── Empty state premium : appel à l'action en 2 voies ──
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                OrangeVibrant.copy(alpha = 0.18f),
                                OrangeVibrant.copy(alpha = 0.04f),
                            )
                        )
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(OrangeVibrant.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.EventBusy, null, Modifier.size(22.dp), tint = OrangeVibrant) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.calendar_hero_no_session_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.calendar_hero_no_session_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPlan,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.calendar_hero_action_plan),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = onAi,
                        enabled = !isSuggesting,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = NeonGreen)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.calendar_hero_action_ai),
                            color = NeonGreen,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            return@Card
        }

        // ── Cas next != null : countdown + détails + start ──
        val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, next.date).toInt()
        val timeStr = next.time?.toString()?.substring(0, 5)
        val countdownLabel = when {
            daysUntil == 0 -> if (timeStr != null)
                stringResource(R.string.calendar_hero_today_at, timeStr)
                else stringResource(R.string.calendar_hero_today)
            daysUntil == 1 -> if (timeStr != null)
                stringResource(R.string.calendar_hero_tomorrow_at, timeStr)
                else stringResource(R.string.calendar_hero_tomorrow)
            else -> if (timeStr != null)
                stringResource(R.string.calendar_hero_in_days_at, daysUntil, timeStr)
                else stringResource(R.string.calendar_hero_in_days, daysUntil)
        }
        val routine = RoutineCatalog.byId(next.routineId)
        val titleStr = next.title.ifBlank { stringResource(R.string.calendar_sched_default_title) }
        val dayDateLabel = next.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
        val isStartable = daysUntil == 0

        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            OrangeVibrant,
                            Color(0xFFE65100),
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header : "Prochaine séance" + countdown chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.calendar_hero_next_session),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.22f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(13.dp), tint = Color.White)
                        Text(
                            countdownLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Hero : routine icon + title + date
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(routine.icon, fontSize = 28.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        titleStr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                    )
                    Text(
                        "${routine.displayName} · $dayDateLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                    )
                }
            }

            // Action : Start (today) / Plan otherwise
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isStartable) {
                    Button(
                        onClick = { onStart(next) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = OrangeVibrant,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.calendar_hero_action_start),
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onPlan,
                    modifier = Modifier
                        .let { if (isStartable) it.weight(1f) else it.fillMaxWidth() }
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.calendar_hero_action_plan),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MÉTRIQUES : ring d'assiduité + streak + faites
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MonthMetricsCard(
    adherencePercent: Int,
    completedThisMonth: Int,
    plannedThisMonth: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Ring d'assiduité (gauche)
            AdherenceRing(
                percent = adherencePercent,
                modifier = Modifier.size(72.dp),
            )
            // Mini-tiles (droite, en colonne)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricTile(
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = OrangeVibrant,
                    label = stringResource(R.string.calendar_metric_streak_label),
                    value = "${streakDays}j",
                    accent = OrangeVibrant,
                )
                MetricTile(
                    icon = Icons.Default.CheckCircle,
                    iconTint = NeonGreen,
                    label = stringResource(R.string.calendar_metric_completed_label),
                    value = "$completedThisMonth/$plannedThisMonth",
                    accent = NeonGreen,
                )
            }
        }
    }
}

@Composable
private fun AdherenceRing(percent: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "adherenceRing",
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = when {
        percent >= 80 -> NeonGreen
        percent >= 50 -> OrangeVibrant
        else -> ErrorRed
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.13f
            val padding = stroke / 2f
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val topLeft = Offset(padding, padding)
            // track
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                size = arcSize,
                topLeft = topLeft,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // value
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                size = arcSize,
                topLeft = topLeft,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$percent",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = onSurface,
                fontSize = 22.sp,
            )
            Text(
                "%",
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = iconTint)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.ExtraBold,
            color = accent,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// NAVIGATEUR DE MOIS
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MonthNavigator(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, stringResource(R.string.calendar_nav_prev_month_cd))
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, stringResource(R.string.calendar_nav_next_month_cd))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// GRILLE MENSUELLE — cellules + heatmap
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate?,
    scheduled: List<ScheduledWorkoutEntity>,
    logs: List<WorkoutLogEntity>,
    holidays: Map<LocalDate, String>,
    schoolHolidays: Set<LocalDate>,
    workoutDays: Set<Int>,
    onDayClick: (LocalDate) -> Unit,
) {
    val dayNames = remember {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            .map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3).uppercase() }
    }
    val firstOfMonth = month.atDay(1)
    val leadingEmpty = (firstOfMonth.dayOfWeek.value - 1)
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingEmpty + daysInMonth
    val rows = ((totalCells + 6) / 7).coerceAtLeast(5)

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        // Headers (Lun → Dim, 3 lettres pour la lisibilité)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            dayNames.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }

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
                        DayCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isHoliday = holidays.containsKey(date),
                            isSchoolHoliday = date in schoolHolidays,
                            isWorkoutDay = (date.dayOfWeek.value in workoutDays),
                            scheduleCount = daySchedules.size,
                            hasCompleted = dayLogs.any { it.completed },
                            hasPlanned = daySchedules.any { it.status == "PLANNED" },
                            hasSkipped = daySchedules.any { it.status == "SKIPPED" },
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) },
                        )
                    } else {
                        Box(Modifier.weight(1f).height(60.dp))
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
    onClick: () -> Unit,
) {
    // **Hierarchie visuelle** :
    //  1. Cellule sélectionnée  → bord OrangeVibrant épais
    //  2. Aujourd'hui            → halo OrangeVibrant subtil
    //  3. Séance terminée        → disque NeonGreen plein derrière le numéro
    //  4. Séance planifiée       → ring OrangeVibrant
    //  5. Séance skippée         → barre rouge en dessous
    //  6. Jour habituel inactif  → numéro en orange clair (pour repérer le pattern)
    val cellBg = when {
        isSchoolHoliday -> PurpleSchool.copy(alpha = 0.06f)
        isHoliday -> OrangeVibrant.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> BorderStroke(2.dp, OrangeVibrant)
        isToday -> BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.5f))
        else -> null
    }
    val numberBgColor = when {
        hasCompleted -> NeonGreen
        hasPlanned -> OrangeVibrant.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val numberRing = if (hasPlanned && !hasCompleted) {
        BorderStroke(1.5.dp, OrangeVibrant)
    } else null
    val numberColor = when {
        hasCompleted -> Color.White
        hasPlanned -> OrangeVibrant
        isToday -> OrangeVibrant
        isHoliday -> OrangeVibrant.copy(alpha = 0.85f)
        isWorkoutDay -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cellBg)
            .let {
                if (border != null) it.border(border, RoundedCornerShape(12.dp)) else it
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                Modifier
                    .size(if (hasCompleted || hasPlanned) 32.dp else 28.dp)
                    .clip(CircleShape)
                    .background(numberBgColor)
                    .let {
                        if (numberRing != null) it.border(numberRing, CircleShape) else it
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfMonth}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || isSelected || hasCompleted || hasPlanned) FontWeight.ExtraBold else FontWeight.Medium,
                    color = numberColor,
                )
            }
            // Indicateur secondaire : skip ou multi-séances ou jour habituel inactif
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasSkipped) {
                    Box(Modifier.size(width = 14.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(ErrorRed.copy(alpha = 0.7f)))
                } else if (scheduleCount > 1) {
                    repeat((scheduleCount - 1).coerceAtMost(2)) {
                        Box(Modifier.size(4.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.7f)))
                    }
                } else if (isWorkoutDay && !hasCompleted && !hasPlanned && !isToday) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun LegendStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(NeonGreen, stringResource(R.string.calendar_legend_completed), filled = true)
        LegendDot(OrangeVibrant, stringResource(R.string.calendar_legend_planned), filled = false)
        LegendDot(ErrorRed.copy(alpha = 0.7f), stringResource(R.string.calendar_legend_skipped), filled = true, isBar = true)
        LegendDot(OrangeVibrant.copy(alpha = 0.45f), stringResource(R.string.calendar_legend_workout_day), filled = true, small = true)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, filled: Boolean, small: Boolean = false, isBar: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isBar) {
            Box(Modifier.size(width = 12.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        } else if (filled) {
            Box(Modifier.size(if (small) 5.dp else 9.dp).clip(CircleShape).background(color))
        } else {
            Box(
                Modifier.size(9.dp).clip(CircleShape).border(BorderStroke(1.5.dp, color), CircleShape)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// TIMELINE "À VENIR"
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun UpcomingTimeline(
    upcoming: List<ScheduledWorkoutEntity>,
    onClickDate: (LocalDate) -> Unit,
    onPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TrendingUp, null, Modifier.size(18.dp), tint = OrangeVibrant)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.calendar_upcoming_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.weight(1f))
            if (upcoming.isNotEmpty()) {
                val countText = if (upcoming.size == 1)
                    stringResource(R.string.calendar_upcoming_count_one)
                else stringResource(R.string.calendar_upcoming_count_many, upcoming.size)
                Text(
                    countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }

        if (upcoming.isEmpty()) {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.calendar_upcoming_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        onClick = onPlan,
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.calendar_fab_schedule),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            upcoming.forEach { sched ->
                UpcomingRow(sched = sched, onClick = { onClickDate(sched.date) })
            }
        }
    }
}

@Composable
private fun UpcomingRow(sched: ScheduledWorkoutEntity, onClick: () -> Unit) {
    val today = LocalDate.now()
    val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, sched.date).toInt()
    val routine = RoutineCatalog.byId(sched.routineId)
    val titleStr = sched.title.ifBlank { stringResource(R.string.calendar_sched_default_title) }
    val timeStr = sched.time?.toString()?.substring(0, 5)
    val context = LocalContext.current
    val countdown = remember(daysUntil, timeStr, context) {
        when {
            daysUntil == 0 -> if (timeStr != null)
                context.getString(R.string.calendar_hero_today_at, timeStr)
                else context.getString(R.string.calendar_hero_today)
            daysUntil == 1 -> if (timeStr != null)
                context.getString(R.string.calendar_hero_tomorrow_at, timeStr)
                else context.getString(R.string.calendar_hero_tomorrow)
            else -> if (timeStr != null)
                context.getString(R.string.calendar_hero_in_days_at, daysUntil, timeStr)
                else context.getString(R.string.calendar_hero_in_days, daysUntil)
        }
    }
    val dateLabel = sched.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        .replaceFirstChar { it.uppercase() }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Date "tile" : jour numérique + jour semaine
            Column(
                Modifier.size(width = 48.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OrangeVibrant.copy(alpha = 0.12f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "${sched.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeVibrant,
                )
                Text(
                    sched.date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                        .uppercase().take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant.copy(alpha = 0.85f),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(routine.icon, fontSize = 14.sp)
                    Text(
                        titleStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    "$dateLabel · ${routine.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OrangeVibrant.copy(alpha = 0.10f),
            ) {
                Text(
                    countdown,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// PANEL DÉTAILS JOUR (jour sélectionné)
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun DayDetailsPanel(
    date: LocalDate,
    scheduled: List<ScheduledWorkoutEntity>,
    logs: List<WorkoutLogEntity>,
    holiday: String?,
    isSchoolHoliday: Boolean,
    onAddClick: () -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    onMarkSkipped: (Long) -> Unit,
    onStartSession: (ScheduledWorkoutEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                date.format(fmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            holiday?.let { TagBadge(it, OrangeVibrant.copy(alpha = 0.15f), OrangeVibrant) }
            if (isSchoolHoliday) {
                TagBadge(stringResource(R.string.calendar_school_holiday_badge), PurpleSchool.copy(alpha = 0.15f), PurpleSchool)
            }
        }
        if (scheduled.isEmpty() && logs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.calendar_day_no_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onAddClick,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = OrangeVibrant.copy(alpha = 0.15f),
                        ),
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp), tint = OrangeVibrant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.calendar_day_schedule_cta),
                            color = OrangeVibrant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            scheduled.forEach { sched ->
                ScheduleCard(
                    sched = sched,
                    onDelete = { onDeleteSchedule(sched.id) },
                    onSkip = { onMarkSkipped(sched.id) },
                    onStart = { onStartSession(sched) },
                )
            }
            logs.forEach { log -> LogCard(log) }
        }
    }
}

@Composable
private fun ScheduleCard(
    sched: ScheduledWorkoutEntity,
    onDelete: () -> Unit,
    onSkip: () -> Unit,
    onStart: () -> Unit,
) {
    val statusColor = when (sched.status) {
        "COMPLETED" -> NeonGreen
        "SKIPPED" -> ErrorRed
        "CANCELED" -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        else -> OrangeVibrant
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (sched.status) {
                        "COMPLETED" -> Icons.Default.CheckCircle
                        "SKIPPED" -> Icons.Default.Cancel
                        else -> Icons.Default.FitnessCenter
                    },
                    null, Modifier.size(22.dp), tint = statusColor,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val defaultSchedTitle = stringResource(R.string.calendar_sched_default_title)
                    Text(
                        sched.title.ifBlank { defaultSchedTitle },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val routine = RoutineCatalog.byId(sched.routineId)
                    Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.10f)) {
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
                val statusCompletedLabel = stringResource(R.string.calendar_sched_status_completed)
                val statusSkippedLabel = stringResource(R.string.calendar_sched_status_skipped)
                val statusCanceledLabel = stringResource(R.string.calendar_sched_status_canceled)
                val subtitle = buildString {
                    sched.time?.let { append(it.toString().substring(0, 5)) }
                    if (sched.note.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(sched.note)
                    }
                    if (sched.status != "PLANNED") {
                        if (isNotEmpty()) append(" · ")
                        append(when (sched.status) {
                            "COMPLETED" -> statusCompletedLabel
                            "SKIPPED" -> statusSkippedLabel
                            else -> statusCanceledLabel
                        })
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                if (sched.status == "PLANNED") {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                stringResource(R.string.calendar_sched_action_start),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        ) {
                            Text(stringResource(R.string.calendar_sched_action_skip), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.calendar_sched_action_delete_cd),
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: WorkoutLogEntity) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = NeonGreen) }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.calendar_log_completed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                val duration = log.actualDurationSeconds / 60
                Text(
                    stringResource(R.string.calendar_log_subtitle, log.totalSets, duration, log.totalVolume.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
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
            color = fg,
        )
    }
}

