package com.shredcoach.app.presentation.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.domain.training.SetMetricFormatter
import com.shredcoach.app.domain.training.SetMetricFormatter.ExerciseKind
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryDetailScreen(
    navController: NavController,
    viewModel: WorkoutHistoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }

    if (showShare) {
        // Build share data from the loaded state — same WorkoutFinished
        // variante that l'écran de fin de séance utilise. Cohérence parfaite :
        // même rendu visuel pour la séance qu'on vient de finir et pour une
        // séance ouverte depuis l'historique.
        val log = state.log
        if (log != null) {
            val setsOnlyFmt = stringResource(R.string.history_detail_share_sets_only)
            val items = state.performances.map { perf ->
                val maxWeight = perf.maxWeightKg
                val nonSkipped = perf.sets.count { it.reps > 0 }
                val reps = perf.sets.filter { it.reps > 0 }.map { it.reps }
                val repsPart = when {
                    reps.isEmpty() -> setsOnlyFmt.format(perf.sets.size)
                    reps.toSet().size == 1 -> "${nonSkipped}×${reps.first()}"
                    else -> "${nonSkipped}×${reps.min()}-${reps.max()}"
                }
                val metric = if (maxWeight > 0) "$repsPart · ${maxWeight.toInt()} kg" else repsPart
                com.shredcoach.app.presentation.share.ShareCardData.ExerciseProgressItem(
                    name = perf.exercise.name,
                    status = if (perf.sets.isEmpty())
                        com.shredcoach.app.presentation.share.ShareCardData.ExerciseStatus.SKIPPED
                    else
                        com.shredcoach.app.presentation.share.ShareCardData.ExerciseStatus.DONE,
                    metric = metric,
                )
            }
            val dateLabel = log.startTime.format(
                java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.getDefault())
            )
            val defaultShareTitle = stringResource(R.string.history_detail_default_share_title)
            com.shredcoach.app.presentation.share.ShareSheet(
                data = com.shredcoach.app.presentation.share.ShareCardData.WorkoutFinished(
                    title = state.workoutName.ifBlank { defaultShareTitle },
                    subtitle = dateLabel,
                    durationSeconds = log.actualDurationSeconds,
                    totalVolumeKg = log.totalVolume,
                    totalSets = log.totalSets,
                    totalReps = log.totalReps,
                    exerciseCount = state.performances.size,
                    completedExercises = items,
                ),
                onDismiss = { showShare = false },
            )
        } else {
            // Pas de log → on ferme silencieusement.
            showShare = false
        }
    }

    // Navigation vers nouvelle séance relancée
    LaunchedEffect(state.relaunchedLogId) {
        val id = state.relaunchedLogId
        if (id != null && id > 0) {
            navController.navigate(Screen.WorkoutSession.createRoute(id)) {
                popUpTo(Screen.WorkoutHistory.route)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.log != null) {
                        IconButton(onClick = { showShare = true }) {
                            Icon(Icons.Default.Share, stringResource(R.string.history_detail_share_cd))
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.DeleteOutline, stringResource(R.string.history_detail_delete_cd),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val log = state.log
            if (log?.workoutId != null) {
                Surface(tonalElevation = 8.dp) {
                    Button(
                        onClick = { viewModel.relaunchSession() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Replay, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_detail_relaunch),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { pad ->
        val log = state.log
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            log == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text(stringResource(R.string.history_detail_not_found), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ═══ Header ═══
                item { DetailHeaderCard(state) }

                // ═══ Stats globales ═══
                item { GlobalStatsGrid(state) }

                // ═══ Notes ═══
                log.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    item { NotesCard(notes) }
                }

                // ═══ Performances par exercice ═══
                item {
                    Text(
                        stringResource(R.string.history_detail_perf_section),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }

                items(state.performances, key = { it.exercise.id }) { perf ->
                    ExercisePerformanceCard(perf)
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.history_detail_delete_dialog_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.history_detail_delete_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteLog(); showDeleteConfirm = false; navController.navigateUp() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

// ═══════════════════════════════════════
// HEADER
// ═══════════════════════════════════════
@Composable
private fun DetailHeaderCard(state: HistoryDetailState) {
    val log = state.log!!
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(
                    colors = if (log.completed)
                        listOf(NeonGreen.copy(alpha = 0.95f), NeonGreen.copy(alpha = 0.65f))
                    else
                        listOf(OrangeVibrant.copy(alpha = 0.95f), OrangeVibrant.copy(alpha = 0.65f))
                )
            ).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (log.completed) Icons.Default.CheckCircle else Icons.Default.Pause,
                            null, tint = Color.White, modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        if (log.completed) stringResource(R.string.history_detail_session_completed) else stringResource(R.string.history_detail_session_abandoned),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Text(
                    state.workoutName, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold, color = Color.White
                )
                // Pill routine — visuellement secondaire au nom mais toujours
                // présente, y compris pour Full Body. Cohérence avec
                // HistoryCard (liste d'historique) et SessionTopBar (séance active).
                val routine = RoutineCatalog.byId(log.routineId)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(routine.icon, fontSize = 13.sp)
                        Text(
                            routine.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Text(
                    formatLongDate(log.date), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// STATS GRID
// ═══════════════════════════════════════
@Composable
private fun GlobalStatsGrid(state: HistoryDetailState) {
    val log = state.log!!
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Default.Timer, stringResource(R.string.history_detail_stat_duration), formatSeconds(log.actualDurationSeconds), Modifier.weight(1f).fillMaxHeight())
            StatTile(Icons.Default.Bolt, stringResource(R.string.history_detail_stat_volume), formatVolume(log.totalVolume), Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Default.FitnessCenter, stringResource(R.string.history_detail_stat_exos), "${log.exercisesCompleted}", Modifier.weight(1f).fillMaxHeight(),
                subtitle = if (log.exercisesSkipped > 0) stringResource(R.string.history_detail_subtitle_skipped, log.exercisesSkipped) else null)
            StatTile(Icons.Default.Repeat, stringResource(R.string.history_detail_stat_sets), "${log.totalSets}", Modifier.weight(1f).fillMaxHeight(),
                subtitle = if (log.totalReps > 0) stringResource(R.string.history_detail_subtitle_reps, log.totalReps) else null)
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Default.LocalFireDepartment, stringResource(R.string.history_detail_stat_rest_total), formatSeconds(log.totalRestSeconds), Modifier.weight(1f).fillMaxHeight())
            val effectiveSec = (log.actualDurationSeconds - log.totalRestSeconds).coerceAtLeast(0)
            StatTile(Icons.AutoMirrored.Filled.DirectionsRun, stringResource(R.string.history_detail_stat_active_time), formatSeconds(effectiveSec), Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = OrangeVibrant, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Notes, null, tint = OrangeVibrant, modifier = Modifier.size(20.dp))
            Column {
                Text(stringResource(R.string.history_detail_notes_label), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = OrangeVibrant)
                Text(notes, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
            }
        }
    }
}

// ═══════════════════════════════════════
// EXERCISE PERFORMANCE CARD
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePerformanceCard(perf: ExercisePerformance) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(OrangeVibrant.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${perf.sets.size}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = OrangeVibrant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(perf.exercise.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(perf.exercise.muscleGroup.displayNameRes), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                Icon(Icons.Default.ExpandMore, null,
                    modifier = Modifier.size(24.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // Mini résumé toujours visible — kind-aware :
            //  - WEIGHTED        : reps · max kg · volume
            //  - BODYWEIGHT_REPS : reps · max reps/série · volume (= 0 ici)
            //  - TIMED           : durée totale · max tenue · — (volume non pertinent)
            val exerciseKind = SetMetricFormatter.kindOf(perf.exercise)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                when (exerciseKind) {
                    ExerciseKind.TIMED -> {
                        // Total = somme des durées, max = meilleure tenue
                        val totalDuration = perf.sets.sumOf { it.reps }
                        val bestDuration = perf.sets.maxOfOrNull { it.reps } ?: 0
                        MiniStat(SetMetricFormatter.formatDuration(totalDuration), stringResource(R.string.history_detail_mini_total))
                        MiniStat(SetMetricFormatter.formatDuration(bestDuration), stringResource(R.string.history_detail_mini_max_hold))
                        MiniStat("${perf.sets.size}", stringResource(R.string.history_detail_mini_sets))
                    }
                    ExerciseKind.BODYWEIGHT_REPS -> {
                        val maxReps = perf.sets.maxOfOrNull { it.reps } ?: 0
                        MiniStat("${perf.totalReps}", stringResource(R.string.history_detail_mini_total_reps))
                        MiniStat("$maxReps", stringResource(R.string.history_detail_mini_max_per_set))
                        MiniStat("${perf.sets.size}", stringResource(R.string.history_detail_mini_sets))
                    }
                    ExerciseKind.WEIGHTED -> {
                        MiniStat("${perf.totalReps}", stringResource(R.string.history_detail_mini_reps))
                        val dash = stringResource(R.string.history_detail_dash)
                        MiniStat(
                            if (perf.maxWeightKg > 0) String.format(Locale.getDefault(), "%.1f kg", perf.maxWeightKg) else dash,
                            stringResource(R.string.history_detail_mini_max)
                        )
                        MiniStat(formatVolume(perf.totalVolume), stringResource(R.string.history_detail_mini_volume))
                    }
                }
            }

            // Détail des sets (expandable)
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    perf.sets.forEach { set ->
                        SetRow(set.setNumber, set.reps, set.targetReps, set.weightKg, set.completed, exerciseKind)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun SetRow(
    setNumber: Int,
    reps: Int,
    targetReps: Int,
    weightKg: Double,
    completed: Boolean,
    exerciseKind: ExerciseKind,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape)
                .background(if (completed) NeonGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$setNumber", style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (completed) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Spacer(Modifier.width(12.dp))
        when (exerciseKind) {
            ExerciseKind.TIMED -> {
                // Pour un exo time-based, `reps` = secondes de tenue
                val targetSuffix = if (targetReps > 0 && targetReps != reps) " / ${SetMetricFormatter.formatDuration(targetReps)}" else ""
                Text(
                    "${SetMetricFormatter.formatDuration(reps)}$targetSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant,
                    modifier = Modifier.weight(1f)
                )
            }
            ExerciseKind.BODYWEIGHT_REPS -> {
                Text(
                    "$reps${if (targetReps > 0 && targetReps != reps) " / $targetReps" else ""} reps",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Lesté éventuel — affiché à droite avec un préfixe "+"
                if (weightKg > 0.0) {
                    Text(
                        "+${SetMetricFormatter.formatWeight(weightKg)} kg",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OrangeVibrant
                    )
                }
            }
            ExerciseKind.WEIGHTED -> {
                Text(
                    "$reps${if (targetReps > 0 && targetReps != reps) " / $targetReps" else ""} reps",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (weightKg > 0) String.format(Locale.getDefault(), "%.1f kg", weightKg) else stringResource(R.string.history_detail_dash),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant
                )
            }
        }
    }
}

