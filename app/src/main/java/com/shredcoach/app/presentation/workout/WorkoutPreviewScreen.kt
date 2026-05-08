package com.shredcoach.app.presentation.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.R
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.presentation.common.EmptyState
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPreviewScreen(
    navController: NavController,
    viewModel: WorkoutGeneratorViewModel = hiltViewModel()
) {
    val generatedWorkout by viewModel.generatedWorkout.collectAsState()
    val overrides by viewModel.exerciseOverrides.collectAsState()
    val error by viewModel.error.collectAsState()

    // Swap dialog state
    var exerciseToSwap by remember { mutableStateOf<ExerciseEntity?>(null) }
    var alternatives by remember { mutableStateOf<List<ExerciseEntity>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var showSharePreview by remember { mutableStateOf(false) }

    // Show swap dialog
    if (exerciseToSwap != null) {
        SwapExerciseDialog(
            exercise = exerciseToSwap!!,
            alternatives = alternatives,
            onSelect = { newExercise ->
                viewModel.replaceExercise(exerciseToSwap!!, newExercise)
                exerciseToSwap = null
                alternatives = emptyList()
            },
            onDismiss = {
                exerciseToSwap = null
                alternatives = emptyList()
            }
        )
    }

    // Share preview bottom sheet
    val workoutForShare = generatedWorkout
    if (showSharePreview && workoutForShare != null) {
        val routineForShare = RoutineCatalog.byId(workoutForShare.routineId)
        com.shredcoach.app.presentation.share.ShareSheet(
            data = com.shredcoach.app.presentation.share.ShareCardData.WorkoutPlanned(
                title = stringResource(R.string.preview_share_card_title),
                subtitle = stringResource(R.string.preview_share_subtitle, workoutForShare.totalDuration, routineForShare.displayName),
                durationMinutes = workoutForShare.totalDuration,
                exerciseCount = workoutForShare.exerciseCount,
                warmupCount = workoutForShare.warmupExercises.size,
                cardioCount = workoutForShare.cardioExercises.size,
                muscleGroups = workoutForShare.exercises
                    .map { it.muscleGroup.name.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() } }
                    .distinct(),
                // Pas de pré-troncation : la share card a son propre cap
                // (16 visibles + footer overflow) et adapte la densité (mode
                // ultra-compact pour 13+ items).
                firstFewExercises = workoutForShare.exercises.map { it.name },
            ),
            onDismiss = { showSharePreview = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.preview_topbar_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Clear workout and navigate back
                        viewModel.clearWorkout()
                        navController.navigateUp()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    val isFavorite by viewModel.markAsFavorite.collectAsState()
                    val snackbarHostState = com.shredcoach.app.presentation.navigation.LocalSnackbarHostState.current
                    val snackScope = rememberCoroutineScope()
                    val favAddedMsg = stringResource(R.string.preview_fav_added)
                    val favRemovedMsg = stringResource(R.string.preview_fav_removed)
                    // Bouton partager — n'apparaît que si une séance est générée
                    if (generatedWorkout != null) {
                        IconButton(onClick = { showSharePreview = true }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Default.Share,
                                contentDescription = stringResource(R.string.preview_share_cd),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                    IconButton(onClick = {
                        viewModel.toggleFavorite()
                        snackScope.launch { snackbarHostState.showSnackbar(
                            if (!isFavorite) favAddedMsg else favRemovedMsg,
                            duration = SnackbarDuration.Short) }
                    }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            stringResource(R.string.preview_favorite_cd),
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (generatedWorkout == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = stringResource(R.string.preview_empty_title),
                    description = stringResource(R.string.preview_empty_desc)
                )
            }
        } else {
            val workout = generatedWorkout!!

            // Scroll-driven collapsing header
            val listState = rememberLazyListState()
            val isCollapsed by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 80 }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header collapsing avec animation
                WorkoutSummaryCard(
                    totalDuration = workout.totalDuration,
                    warmupMinutes = workout.warmupMinutes,
                    exerciseCount = workout.exerciseCount,
                    cardioMinutes = workout.cardioMinutes,
                    routineDisplayName = RoutineCatalog.byId(workout.routineId).displayName,
                    collapsed = isCollapsed,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Scrollable exercise list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Échauffement Section
                    if (workout.warmupExercises.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(20.dp), tint = OrangeVibrant)
                                Text(
                                    stringResource(R.string.preview_section_warmup, workout.warmupMinutes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OrangeVibrant
                                )
                            }
                        }

                        itemsIndexed(workout.warmupExercises) { index, exercise ->
                            ExercisePreviewCard(
                                exercise = exercise,
                                orderNumber = index + 1,
                                showOrderBadge = false,
                                onClick = {
                                    navController.navigate(
                                        com.shredcoach.app.presentation.navigation.Screen.ExerciseDetail.createRoute(exercise.id)
                                    )
                                },
                                onSwap = {
                                    scope.launch {
                                        alternatives = viewModel.getAlternativeExercises(exercise)
                                        exerciseToSwap = exercise
                                    }
                                }
                            )
                        }
                    }

                    // Exercices Musculation Section
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Text(
                                stringResource(R.string.preview_section_strength, workout.exerciseCount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    itemsIndexed(workout.exercises) { index, exercise ->
                        ExercisePreviewCard(
                            exercise = exercise,
                            orderNumber = index + 1,
                            effectiveSeries = viewModel.resolvedSeries(exercise),
                            effectiveRepsMin = viewModel.resolvedRepsMin(exercise),
                            effectiveRepsMax = viewModel.resolvedRepsMax(exercise),
                            effectiveRest = viewModel.resolvedRestSeconds(exercise),
                            onClick = {
                                navController.navigate(
                                    com.shredcoach.app.presentation.navigation.Screen.ExerciseDetail.createRoute(exercise.id)
                                )
                            },
                            onSeriesChange = { v -> viewModel.updateExerciseOverride(exercise.id) { it.copy(series = v) } },
                            onRepsMinChange = { v -> viewModel.updateExerciseOverride(exercise.id) { it.copy(repsMin = v) } },
                            onRepsMaxChange = { v -> viewModel.updateExerciseOverride(exercise.id) { it.copy(repsMax = v) } },
                            onRestChange = { v -> viewModel.updateExerciseOverride(exercise.id) { it.copy(restSeconds = v) } },
                            onSwap = {
                                scope.launch {
                                    alternatives = viewModel.getAlternativeExercises(exercise)
                                    exerciseToSwap = exercise
                                }
                            }
                        )
                    }

                    // Cardio Section
                    if (workout.cardioExercises.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(20.dp), tint = NeonGreen)
                                Text(
                                    stringResource(R.string.preview_section_cardio, workout.cardioMinutes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen
                                )
                            }
                        }

                        itemsIndexed(workout.cardioExercises) { index, exercise ->
                            CardioExerciseCard(
                                exercise = exercise,
                                durationMinutes = workout.cardioMinutes,
                                onClick = {
                                    navController.navigate(
                                        com.shredcoach.app.presentation.navigation.Screen.ExerciseDetail.createRoute(exercise.id)
                                    )
                                },
                                onSwap = {
                                    scope.launch {
                                        alternatives = viewModel.getAlternativeExercises(exercise)
                                        exerciseToSwap = exercise
                                    }
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Space for button
                    }
                }

                // Start workout button
                var isStarting by remember { mutableStateOf(false) }
                var startError by remember { mutableStateOf<String?>(null) }
                val ctxForErrors = LocalContext.current
                val unknownErrorMsg = stringResource(R.string.preview_error_unknown)
                val startFailedMsg = stringResource(R.string.preview_error_start_failed)

                // Show error message if start failed
                if (startError != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                startError ?: unknownErrorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        isStarting = true
                        startError = null
                        scope.launch {
                            try {
                                val workoutLogId = viewModel.startWorkoutSession()
                                if (workoutLogId != null) {
                                    navController.navigate(
                                        com.shredcoach.app.presentation.navigation.Screen.WorkoutSession.createRoute(workoutLogId)
                                    )
                                } else {
                                    startError = error ?: startFailedMsg
                                }
                            } catch (e: Exception) {
                                startError = ctxForErrors.getString(R.string.preview_error_prefix, e.message ?: "")
                            } finally {
                                isStarting = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isStarting
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.preview_start_button),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkoutSummaryCard(
    totalDuration: Int,
    warmupMinutes: Int,
    exerciseCount: Int,
    cardioMinutes: Int,
    routineDisplayName: String = stringResource(R.string.preview_routine_default),
    modifier: Modifier = Modifier,
    collapsed: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(
            animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing)
        ),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant)
    ) {
        if (collapsed) {
            // Mode compact : une seule ligne
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = Color.White.copy(alpha = 0.7f))
                Text(stringResource(R.string.preview_summary_compact, routineDisplayName, totalDuration),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(stringResource(R.string.preview_summary_exos_count, exerciseCount), style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f))
            }
        } else {
            // Mode complet
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.preview_summary_title, routineDisplayName), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(stringResource(R.string.preview_summary_total, totalDuration), style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f))
                    }
                    Icon(Icons.Default.FitnessCenter, null, Modifier.size(32.dp), tint = Color.White.copy(alpha = 0.6f))
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    BreakdownMini(icon = Icons.AutoMirrored.Filled.DirectionsRun,
                        value = stringResource(R.string.preview_breakdown_minutes, warmupMinutes),
                        label = stringResource(R.string.preview_breakdown_warmup_label), modifier = Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.3f)))
                    BreakdownMini(icon = Icons.Default.FitnessCenter, value = "$exerciseCount",
                        label = stringResource(R.string.preview_breakdown_strength_label), modifier = Modifier.weight(1f))
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.3f)))
                    BreakdownMini(icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        value = stringResource(R.string.preview_breakdown_minutes, cardioMinutes),
                        label = stringResource(R.string.preview_breakdown_cardio_label), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BreakdownMini(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun TimeBreakdownItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    minutes: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "${minutes}min",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePreviewCard(
    exercise: ExerciseEntity,
    orderNumber: Int,
    modifier: Modifier = Modifier,
    showOrderBadge: Boolean = true,
    // Valeurs effectives (override ou défaut)
    effectiveSeries: Int = exercise.series,
    effectiveRepsMin: Int = exercise.repsMin,
    effectiveRepsMax: Int = exercise.repsMax,
    effectiveRest: Int = exercise.restSeconds,
    onClick: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
    // Callbacks pour modifier les paramètres
    onSeriesChange: ((Int) -> Unit)? = null,
    onRepsMinChange: ((Int) -> Unit)? = null,
    onRepsMaxChange: ((Int) -> Unit)? = null,
    onRestChange: ((Int) -> Unit)? = null
) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val isStrength = exercise.muscleGroup != MuscleGroup.WARMUP && exercise.muscleGroup != MuscleGroup.CARDIO
    val canExpand = isStrength && onSeriesChange != null
    val localized = com.shredcoach.app.domain.exercise.rememberLocalizedExercise(exercise)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { if (canExpand) expanded = !expanded else onClick?.invoke() }
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail GIF avec numero en overlay
                Box(Modifier.size(64.dp)) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center) {
                        if (exercise.gifUrl != null) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(ctx).data(exercise.gifUrl)
                                    .size(Size(128, 128)).crossfade(true).build(),
                                contentDescription = localized.name, modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = { Icon(Icons.Default.FitnessCenter, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)) }
                            )
                        } else {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                    if (showOrderBadge) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                            color = OrangeVibrant, modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text("$orderNumber", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Exercise Info
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(localized.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = MaterialTheme.shapes.small, color = Color(exercise.variant.color).copy(alpha = 0.2f)) {
                            Text(stringResource(exercise.variant.displayNameRes), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color(exercise.variant.color), maxLines = 1)
                        }
                        Text(stringResource(R.string.preview_card_summary_format, effectiveSeries, effectiveRepsMin, effectiveRepsMax, effectiveRest),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSwap != null) {
                        IconButton(onClick = onSwap, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SwapHoriz, stringResource(R.string.preview_swap_cd), Modifier.size(20.dp), tint = OrangeVibrant)
                        }
                    }
                    if (canExpand) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            stringResource(R.string.preview_settings_cd), Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // ─── Panneau de réglages inline (expanded) ───
            androidx.compose.animation.AnimatedVisibility(visible = expanded && canExpand) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PreviewMiniStepper(stringResource(R.string.preview_stepper_series), "$effectiveSeries",
                            onMinus = { onSeriesChange?.invoke((effectiveSeries - 1).coerceAtLeast(1)) },
                            onPlus = { onSeriesChange?.invoke((effectiveSeries + 1).coerceAtMost(10)) })
                        PreviewDivider()
                        PreviewMiniStepper(stringResource(R.string.preview_stepper_reps), stringResource(R.string.preview_card_reps_range, effectiveRepsMin, effectiveRepsMax),
                            onMinus = { onRepsMinChange?.invoke((effectiveRepsMin - 1).coerceAtLeast(1)); onRepsMaxChange?.invoke((effectiveRepsMax - 1).coerceAtLeast(1)) },
                            onPlus = { onRepsMinChange?.invoke(effectiveRepsMin + 1); onRepsMaxChange?.invoke(effectiveRepsMax + 1) })
                        PreviewDivider()
                        PreviewMiniStepper(stringResource(R.string.preview_stepper_rest), stringResource(R.string.preview_card_rest_seconds, effectiveRest),
                            onMinus = { onRestChange?.invoke((effectiveRest - 15).coerceAtLeast(15)) },
                            onPlus = { onRestChange?.invoke((effectiveRest + 15).coerceAtMost(300)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewMiniStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
        // + en haut
        Surface(onClick = onPlus, shape = RoundedCornerShape(6.dp),
            color = OrangeVibrant, modifier = Modifier.size(width = 52.dp, height = 26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        // Valeur au milieu
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
        // − en bas
        Surface(onClick = onMinus, shape = RoundedCornerShape(6.dp),
            color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(width = 52.dp, height = 26.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("−", color = OrangeVibrant, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun PreviewDivider() {
    Box(Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioExerciseCard(
    exercise: ExerciseEntity,
    durationMinutes: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val localized = com.shredcoach.app.domain.exercise.rememberLocalizedExercise(exercise)
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail GIF cardio
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(NeonGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                if (exercise.gifUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(ctx).data(exercise.gifUrl)
                            .size(Size(128, 128)).crossfade(true).build(),
                        contentDescription = localized.name, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = { Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(28.dp), tint = NeonGreen) }
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(28.dp), tint = NeonGreen)
                }
            }

            // Exercise Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    localized.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Durée en gros
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.preview_cardio_minutes, durationMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (localized.tips.isNotBlank()) {
                    Text(
                        localized.tips.take(80) + if (localized.tips.length > 80) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Swap button
            if (onSwap != null) {
                IconButton(onClick = onSwap) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.preview_swap_cd),
                        tint = NeonGreen
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapExerciseDialog(
    exercise: ExerciseEntity,
    alternatives: List<ExerciseEntity>,
    onSelect: (ExerciseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val localizedCurrent = com.shredcoach.app.domain.exercise.rememberLocalizedExercise(exercise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.swap_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.swap_dialog_current, localizedCurrent.name, stringResource(exercise.variant.displayNameRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (alternatives.isEmpty()) {
                    Text(
                        stringResource(R.string.swap_dialog_no_alternatives),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        stringResource(R.string.swap_dialog_choose),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    val ctx = LocalContext.current
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alternatives, key = { it.id }) { alt ->
                            val localizedAlt = com.shredcoach.app.domain.exercise.rememberLocalizedExercise(alt)
                            Card(
                                onClick = { onSelect(alt) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail GIF
                                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center) {
                                        if (alt.gifUrl != null) {
                                            SubcomposeAsyncImage(
                                                model = ImageRequest.Builder(ctx).data(alt.gifUrl)
                                                    .size(Size(112, 112)).crossfade(true).build(),
                                                contentDescription = localizedAlt.name, modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = { Icon(Icons.Default.FitnessCenter, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)) }
                                            )
                                        } else {
                                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(localizedAlt.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${localizedAlt.equipment.take(40)}${if (localizedAlt.equipment.length > 40) "..." else ""}",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Surface(shape = MaterialTheme.shapes.small, color = Color(alt.variant.color).copy(alpha = 0.2f)) {
                                        Text(stringResource(alt.variant.displayNameRes), style = MaterialTheme.typography.labelSmall, color = Color(alt.variant.color),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.swap_dialog_close))
            }
        }
    )
}

@Composable
fun ExerciseQuickStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
