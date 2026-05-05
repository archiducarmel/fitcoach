package com.shredcoach.app.presentation.workout

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.media.RingtoneManager
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.presentation.common.tabularNum
import com.shredcoach.app.presentation.theme.BrightYellow
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import com.shredcoach.app.presentation.util.hapticClick
import kotlinx.coroutines.delay

// ═══════════════════════════════════════
// ÉCRAN PRINCIPAL
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    navController: NavController,
    viewModel: WorkoutSessionViewModel = hiltViewModel(),
    shreddyVoice: com.shredcoach.app.domain.voice.ShreddyVoice? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Injecter ShreddyVoice via le contexte Application (Hilt Singleton)
    val voice = shreddyVoice ?: remember {
        try {
            val app = context.applicationContext as com.shredcoach.app.ShredCoachApplication
            app.shreddyVoice
        } catch (_: Exception) { null }
    }

    LaunchedEffect(state.isSessionComplete) {
        if (state.isSessionComplete) {
            navController.navigate(com.shredcoach.app.presentation.navigation.Screen.WorkoutSummary.route) {
                popUpTo(com.shredcoach.app.presentation.navigation.Screen.Workout.route) { inclusive = true }
            }
        }
    }

    // Countdown vocal du repos (5, 3, 2, 1)
    LaunchedEffect(state.restTimeRemaining) {
        if (state.isRestTimerActive && state.voiceEnabled && voice != null) {
            voice.speakCountdown(state.restTimeRemaining)
        }
    }

    // Countdown vocal pour les séries chronométrées (gainage, cardio...)
    LaunchedEffect(state.timedSetSecondsRemaining) {
        val isTimedSetRunning = state.timedSetTotalSeconds > 0 && state.isSetInProgress
        if (isTimedSetRunning && state.voiceEnabled && voice != null) {
            voice.speakCountdown(state.timedSetSecondsRemaining)
        }
    }

    // Vibration + son à la fin du décompte de série chronométrée
    LaunchedEffect(state.timedSetSecondsRemaining, state.timedSetTotalSeconds) {
        if (state.timedSetSecondsRemaining == 0 && state.timedSetTotalSeconds == 0) return@LaunchedEffect
        if (state.timedSetSecondsRemaining == 0 && state.timedSetTotalSeconds > 0) {
            if (state.vibrationEnabled) vibrate(context)
            if (state.soundEnabled) playRestEndSound(context)
        }
    }

    // Vibration + son + voix à la fin du repos
    LaunchedEffect(state.isRestTimerActive, state.restTimeRemaining) {
        if (!state.isRestTimerActive && state.restTimeElapsed > 0 && state.restTimeRemaining == 0) {
            if (state.vibrationEnabled) vibrate(context)
            if (state.soundEnabled) playRestEndSound(context)
            if (state.voiceEnabled) voice?.speakRestEnd()
            viewModel.onRestComplete()
        }
    }

    // Dialog de confirmation Stop
    var showStopConfirm by remember { mutableStateOf(false) }
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Terminer la séance ?", fontWeight = FontWeight.Bold) },
            text = { Text("Tu pourras reprendre plus tard. Les séries complétées sont sauvegardées.") },
            confirmButton = {
                Button(
                    onClick = { showStopConfirm = false; viewModel.stopSessionEarly() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Terminer", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("Annuler") } }
        )
    }

    // Dialog ajout exercice à la volée
    if (state.showAddExerciseDialog) {
        AddExerciseMidSessionDialog(state = state, viewModel = viewModel)
    }

    // Dialog de confirmation ajout exercice
    val pendingExo = state.pendingExerciseToAdd
    if (pendingExo != null) {
        val placementLabel = when (state.pendingExercisePlacement) {
            "start" -> "en échauffement (début)"
            "afterCurrent" -> "après l'exercice en cours"
            else -> "en fin de séance"
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelAddExercise() },
            icon = { Icon(Icons.Default.FitnessCenter, null, tint = OrangeVibrant) },
            title = { Text("Ajouter cet exercice ?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(pendingExo.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text("${pendingExo.series} séries · ${pendingExo.repsMin}-${pendingExo.repsMax} reps · ${pendingExo.restSeconds}s repos",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    Text("Placement : $placementLabel", style = MaterialTheme.typography.labelMedium, color = OrangeVibrant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAddExercise() },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                ) { Text("Ajouter", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelAddExercise() }) { Text("Annuler") } }
        )
    }

    Scaffold(
        topBar = { SessionTopBar(state,
            onBack = {
                // Retour idiomatique : on dépile la session et on revient à
                // l'écran précédent (Home, Preview, History…). Le chrono global
                // continue de tourner via ActiveSessionManager → la bannière
                // s'affichera automatiquement sur l'écran de destination.
                // Si la session est l'unique entry du back stack (cas pathologique
                // — deeplink direct), on retombe sur Home sans dupliquer Home.
                val popped = navController.popBackStack()
                if (!popped) {
                    navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Home.route) {
                        launchSingleTop = true
                        popUpTo(com.shredcoach.app.presentation.navigation.Screen.Home.route) {
                            inclusive = true
                        }
                    }
                }
            },
            onToggleChrono = { if (state.globalChronoRunning) viewModel.stopGlobalChrono() else viewModel.resumeGlobalChrono() },
            onStop = { showStopConfirm = true },
            onShowOverview = { viewModel.toggleExerciseOverview() }
        ) }
    ) { pad ->
        // Cle d'etat pour AnimatedContent
        val screenState = when {
            state.isLoading -> "loading"
            state.error != null -> "error"
            state.showExerciseOverview -> "session" // L'overview prend le dessus sur la transition
            state.showExerciseTransition -> "transition"
            else -> "session"
        }

        AnimatedContent(
            targetState = screenState,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "sessionContent"
        ) { target ->
        when (target) {
            "loading" -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Icone pulsante
                    val inf = rememberInfiniteTransition(label = "loadPulse")
                    val pulseScale by inf.animateFloat(
                        1f, 1.15f,
                        infiniteRepeatable(tween(800), RepeatMode.Reverse),
                        label = "ps"
                    )
                    Surface(
                        shape = CircleShape,
                        color = OrangeVibrant.copy(alpha = 0.15f),
                        modifier = Modifier.size(96.dp).graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(48.dp), tint = OrangeVibrant)
                        }
                    }
                    Text("Préparation de ta séance...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    LinearProgressIndicator(
                        color = OrangeVibrant,
                        modifier = Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            "error" -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Error, null, Modifier.size(64.dp), MaterialTheme.colorScheme.error)
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { navController.popBackStack() }) { Text("Retour") }
                }
            }
            "transition" -> {
                ExerciseTransitionOverlay(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    fromName = state.transitionFromName,
                    toName = state.transitionToName,
                    exercisesDone = state.transitionExercisesDone,
                    totalExercises = state.transitionTotalExercises,
                    exoSets = state.transitionExerciseSets,
                    exoReps = state.transitionExerciseReps,
                    exoVolume = state.transitionExerciseVolume,
                    exoDuration = state.transitionExerciseDuration,
                    exoSkipped = state.transitionExerciseSkipped,
                    shreddyMessage = state.shreddyCoachMessage,
                    isShreddyThinking = state.isShreddyThinking,
                    shreddySource = state.shreddyMessageSource,
                    onDismiss = { viewModel.dismissTransition() }
                )
            }
            else -> {
                val exercise = state.currentExercise
                Box(Modifier.fillMaxSize().padding(pad)) {
                    // IMPORTANT : pendant le fade de l'AnimatedContent (transition → session ou
                    // l'inverse), la composition "session" peut rester vivante avec l'état COURANT
                    // qui a showExerciseTransition=true. Si on ne filtre pas, le user voit la
                    // SeriesTimeline de l'ancien exercice pendant le fade → bug "dernière série".
                    // → Ne rien rendre si showExerciseTransition est active.
                    if (!state.showExerciseTransition) when {
                        // ─── 1. Overview : priorité absolue ───
                        state.showExerciseOverview && state.exercises.isNotEmpty() -> {
                            ExerciseOverviewPanel(state = state, viewModel = viewModel)
                        }
                        // ─── 2. Empty state freestyle : aucun exercice ajouté encore ───
                        state.isFreestyle && state.exercises.isEmpty() -> {
                            Column(
                                Modifier.fillMaxSize().padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FlashOn, null, Modifier.size(64.dp), tint = NeonGreen.copy(alpha = 0.5f))
                                Spacer(Modifier.height(20.dp))
                                Text("Séance libre", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text("Ajoute ton premier exercice pour démarrer !",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(Modifier.height(28.dp))
                                Button(
                                    onClick = { viewModel.openAddExerciseDialog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    Icon(Icons.Default.Add, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ajouter un exercice", fontWeight = FontWeight.Bold)
                                }
                                if (state.globalChronoSeconds > 0) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        com.shredcoach.app.presentation.history.formatSeconds(state.globalChronoSeconds),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangeVibrant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        // ─── 3. Session view : exercice courant ───
                        exercise != null -> {
                            Column(Modifier.fillMaxSize()) {
                                LinearProgressIndicator(progress = { state.progressPercentage }, modifier = Modifier.fillMaxWidth().height(4.dp), color = OrangeVibrant)

                                if (state.isInWarmupBlock) {
                                    WarmupBlockView(state, viewModel)
                                } else {
                                    ExerciseHeader(exercise, state.exerciseChronoSeconds, onSkip = { viewModel.skipToNextExercise() })
                                    Box(Modifier.weight(1f)) { SeriesTimeline(state, exercise, viewModel) }
                                    MainActionButton(state, viewModel)
                                }
                            }
                            SmallFloatingActionButton(
                                onClick = { viewModel.openAddExerciseDialog() },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 80.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = OrangeVibrant
                            ) {
                                Icon(Icons.Default.Add, "Ajouter exercice", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

// ═══════════════════════════════════════
// TOP BAR (croix = retour, pas arrêt)
// ═══════════════════════════════════════

@Composable
private fun SessionTopBar(
    state: WorkoutSessionState,
    onBack: () -> Unit,
    onToggleChrono: () -> Unit,
    onStop: () -> Unit,
    onShowOverview: () -> Unit = {}
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, "Retour", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text(
                "Exercice ${state.currentExerciseIndex + 1}/${state.totalExercises}",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold
            )
            // Bouton vue d'ensemble
            IconButton(onClick = onShowOverview, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FormatListNumbered, "Vue d'ensemble", Modifier.size(18.dp),
                    tint = if (state.showExerciseOverview) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Spacer(Modifier.weight(1f))
            // Chrono global compact
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.globalChronoRunning) {
                    val inf = rememberInfiniteTransition(label = "d")
                    val a by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
                    Box(Modifier.size(6.dp).alpha(a).clip(CircleShape).background(OrangeVibrant))
                }
                Text(
                    fmtChrono(state.globalChronoSeconds),
                    style = MaterialTheme.typography.labelLarge.tabularNum(),
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 56.dp),
                )
            }
            IconButton(onClick = onToggleChrono, modifier = Modifier.size(36.dp)) {
                Icon(if (state.globalChronoRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = OrangeVibrant)
            }
            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Stop, "Arrêter", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ═══════════════════════════════════════
// EN-TÊTE EXERCICE
// ═══════════════════════════════════════

@Composable
private fun ExerciseHeader(exercise: ExerciseEntity, chronoSec: Long, onSkip: () -> Unit) {
    var showGifFullscreen by remember { mutableStateOf(false) }

    // ─── Dialog GIF fullscreen ───
    if (showGifFullscreen && exercise.gifUrl != null) {
        GifFullscreenDialog(exercise = exercise, onDismiss = { showGifFullscreen = false })
    }

    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // GIF miniature — tappable pour agrandir
            if (exercise.gifUrl != null) {
                Box(Modifier.clickable { showGifFullscreen = true }) {
                    ExerciseGif(
                        gifUrl = exercise.gifUrl!!,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    )
                    // Petit badge agrandir
                    Icon(Icons.Default.Fullscreen, null, Modifier.align(Alignment.BottomEnd).size(16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(4.dp)),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            // Nom + badges
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TagBadge(exercise.muscleGroup.displayName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    TagBadge(exercise.variant.displayName, Color(exercise.variant.color).copy(alpha = 0.2f), Color(exercise.variant.color))
                }
            }
            // Chrono + Skip empilés
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                    Text(fmtChrono(chronoSec), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium.tabularNum(), fontWeight = FontWeight.Bold, color = OrangeVibrant,
                        maxLines = 1, softWrap = false)
                }
                Surface(
                    onClick = onSkip,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Text("SKIP", Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Composant GIF avec Coil - supporte assets et URLs */
@Composable
private fun ExerciseGif(gifUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(gifUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Démonstration exercice",
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop,
        error = {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.FitnessCenter, null, Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            }
        },
        loading = {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    )
}

// ═══════════════════════════════════════
// GIF FULLSCREEN DIALOG
// ═══════════════════════════════════════

@Composable
private fun GifFullscreenDialog(exercise: ExerciseEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = {
            Text(exercise.name, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // GIF en grand
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 340.dp)
                        .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (exercise.gifUrl != null) {
                        ExerciseGif(
                            gifUrl = exercise.gifUrl!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagBadge(exercise.muscleGroup.displayName, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    TagBadge(exercise.variant.displayName, Color(exercise.variant.color).copy(alpha = 0.2f), Color(exercise.variant.color))
                    TagBadge(exercise.equipment, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Conseils d'exécution
                if (exercise.executionKey.isNotBlank()) {
                    Text(exercise.executionKey, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                }

                // Tips
                if (exercise.tips.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = OrangeVibrant.copy(alpha = 0.08f)
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = OrangeVibrant)
                            Text(exercise.tips, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

// ═══════════════════════════════════════
// ADD EXERCISE MID-SESSION DIALOG
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseMidSessionDialog(state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = { viewModel.closeAddExerciseDialog() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.addExerciseStep == 1) {
                    IconButton(onClick = { viewModel.backToMuscleGroupStep() }, Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", Modifier.size(18.dp))
                    }
                }
                Column {
                    Text(
                        if (state.addExerciseStep == 0) "Ajouter un exercice"
                        else state.addExerciseMuscleGroup?.displayName ?: "",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (state.addExerciseStep == 0) "Choisis le groupe musculaire"
                        else "Choisis l'exercice à ajouter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ─── Barre de recherche par nom ───
                OutlinedTextField(
                    value = state.addExerciseSearchQuery,
                    onValueChange = { viewModel.onAddExerciseSearchQuery(it) },
                    placeholder = { Text("Rechercher un exercice...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (state.addExerciseSearchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                viewModel.onAddExerciseSearchQuery("")
                                viewModel.backToMuscleGroupStep()
                            }, Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Effacer", Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(12.dp)
                )

                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                if (state.addExerciseStep == 0 && state.addExerciseSearchQuery.isBlank()) {
                    // ─── Étape 1 : groupes musculaires en cards ───
                    val muscleGroups = if (state.isFreestyle) MuscleGroup.values().toList()
                        else MuscleGroup.values().filter { it != MuscleGroup.WARMUP }
                    muscleGroups.forEach { mg ->
                        Card(
                            onClick = { viewModel.selectAddExerciseMuscleGroup(mg) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(mg.displayName, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                                    tint = OrangeVibrant.copy(alpha = 0.6f))
                            }
                        }
                    }
                } else {
                    // ─── Étape 2 : exercices du groupe ───
                    if (state.addExerciseOptions.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Text("Tous les exercices de ce groupe sont déjà dans ta séance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(16.dp))
                        }
                    }
                    // En freestyle, si l'exercice courant est TERMINÉ (all sets done) ou si on vient
                    // de l'overview, on ne doit proposer qu'UN seul bouton "Ajouter" qui navigue vers
                    // le nouvel exo. Sinon, "Après celui-ci" garderait currentExerciseIndex sur l'exo
                    // terminé et l'UI afficherait la dernière série de l'exercice précédent → bug reporté.
                    val currentExo = state.currentExercise
                    val currentExoDone = currentExo != null && run {
                        val totalSets = currentExo.series + (state.extraSeriesMap[state.currentExerciseIndex] ?: 0)
                        val setsDone = state.completedSets.count { it.exerciseId == currentExo.id && !it.skipped }
                        totalSets > 0 && setsDone >= totalSets
                    }
                    val singleButtonFreestyle = state.isFreestyle && (currentExoDone || state.showExerciseOverview)

                    state.addExerciseOptions.forEach { exercise ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Header exo : GIF + nom + variant
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (exercise.gifUrl != null) {
                                        ExerciseGif(
                                            gifUrl = exercise.gifUrl!!,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        }
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(exercise.name, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TagBadge(exercise.variant.displayName,
                                                Color(exercise.variant.color).copy(alpha = 0.2f),
                                                Color(exercise.variant.color))
                                            Text("${exercise.series}×${exercise.repsMin}-${exercise.repsMax} · ${exercise.restSeconds}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                                        }
                                    }
                                }
                                // Boutons de positionnement
                                val isWarmup = exercise.muscleGroup == MuscleGroup.WARMUP
                                val isCardio = exercise.muscleGroup == MuscleGroup.CARDIO

                                if (state.exercises.isEmpty() || singleButtonFreestyle) {
                                    // Premier exercice OU freestyle post-exo : un seul bouton "Ajouter"
                                    // qui insère à la fin ET navigue vers le nouvel exo.
                                    Button(
                                        onClick = { viewModel.requestAddExercise(exercise, "end") },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ajouter", style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                } else if (state.isFreestyle && isWarmup) {
                                    Button(
                                        onClick = { viewModel.requestAddExercise(exercise, "start") },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.FlashOn, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ajouter en échauffement", style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                } else if (state.isFreestyle && isCardio) {
                                    Button(
                                        onClick = { viewModel.requestAddExercise(exercise, "end") },
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ajouter en fin de séance", style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.requestAddExercise(exercise, "afterCurrent") },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Après celui-ci", style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.requestAddExercise(exercise, "end") },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.LastPage, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("En fin", style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                } // fin Column scrollable intérieure
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                if (state.isFreestyle && state.exercises.isEmpty()) {
                    // En freestyle sans exercice, fermer ne fait rien de spécial
                }
                viewModel.closeAddExerciseDialog()
            }) { Text("Fermer") }
        }
    )
}

// ═══════════════════════════════════════
// BLOC ÉCHAUFFEMENT GROUPÉ
// ═══════════════════════════════════════

@Composable
private fun WarmupBlockView(state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    val warmups = state.warmupExercises
    val currentStep = state.warmupStepIndex

    Column(Modifier.fillMaxSize()) {
        // Header warmup
        Surface(tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.LocalFireDepartment, null, Modifier.size(24.dp), tint = OrangeVibrant)
                        Text("ÉCHAUFFEMENT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(fmtChrono(state.exerciseChronoSeconds), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge.tabularNum(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1, softWrap = false)
                    }
                }
                Text("Étape ${currentStep + 1} sur ${warmups.size}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        // Étapes warmup
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(warmups.size) { index ->
                val warmup = warmups[index]
                val isCompleted = index < currentStep
                val isCurrent = index == currentStep
                val globalIdx = state.warmupStartIndex + index

                WarmupStepCard(
                    stepNumber = index + 1,
                    exercise = warmup,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent,
                    duration = if (isCompleted) state.exerciseDurations[globalIdx] else null
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        // Bouton "Étape terminée"
        Surface(tonalElevation = 8.dp) {
            Button(
                onClick = { viewModel.onSetStarted(); viewModel.onSetCompleted() },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (currentStep >= warmups.size - 1) "ÉCHAUFFEMENT TERMINÉ" else "ÉTAPE SUIVANTE",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WarmupStepCard(stepNumber: Int, exercise: ExerciseEntity, isCompleted: Boolean, isCurrent: Boolean, duration: Long?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCompleted -> NeonGreen.copy(alpha = 0.08f)
                isCurrent -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        border = if (isCurrent) BorderStroke(2.dp, OrangeVibrant) else null,
        elevation = if (isCurrent) CardDefaults.cardElevation(defaultElevation = 4.dp) else CardDefaults.cardElevation()
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // GIF miniature ou numéro
            if (exercise.gifUrl != null && isCurrent) {
                ExerciseGif(gifUrl = exercise.gifUrl!!, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(Modifier.size(36.dp).clip(CircleShape).background(
                    when { isCompleted -> NeonGreen.copy(alpha = 0.2f); isCurrent -> OrangeVibrant.copy(alpha = 0.2f); else -> MaterialTheme.colorScheme.surfaceVariant }
                ), contentAlignment = Alignment.Center) {
                    if (isCompleted) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = NeonGreen)
                    else Text("$stepNumber", fontWeight = FontWeight.Bold, color = if (isCurrent) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = if (!isCurrent && !isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface)
                // Durée recommandée
                val recommendedTime = when {
                    exercise.name.contains("Cardio", true) -> "5-10 min"
                    exercise.name.contains("Mobilisation", true) -> "2-3 min"
                    exercise.name.contains("Étirement", true) -> "2-3 min"
                    exercise.name.contains("Activation", true) -> "3-5 min"
                    else -> "${exercise.repsMin}-${exercise.repsMax} reps"
                }
                Text(recommendedTime, style = MaterialTheme.typography.labelSmall, color = OrangeVibrant.copy(alpha = 0.8f))
                if (isCurrent && exercise.executionKey.isNotBlank()) {
                    Text(exercise.executionKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            duration?.let {
                Text(fmtChrono(it), style = MaterialTheme.typography.labelSmall.tabularNum(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

// ═══════════════════════════════════════
// TIMELINE DES SÉRIES
// ═══════════════════════════════════════

@Composable
private fun SeriesTimeline(state: WorkoutSessionState, exercise: ExerciseEntity, viewModel: WorkoutSessionViewModel) {
    val listState = rememberLazyListState()
    val setsForExercise = state.completedSets.filter { it.exerciseId == exercise.id }
    val totalSeries = state.totalSeriesForCurrentExercise

    LaunchedEffect(state.currentSeries, totalSeries) {
        listState.animateScrollToItem((state.currentSeries - 1).coerceAtLeast(0))
    }

    // ─── Cardio: pas de séries, juste un chrono + bouton terminer ───
    if (exercise.muscleGroup == MuscleGroup.CARDIO) {
        CardioSessionCard(exercise = exercise, state = state, viewModel = viewModel)
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        if (exercise.tips.isNotBlank() || exercise.executionKey.isNotBlank()) {
            item { CoachTipCard(exercise) }
        }

        items(totalSeries) { index ->
            val seriesNum = index + 1
            val completedSet = setsForExercise.find { it.seriesNumber == seriesNum }
            val isCurrent = seriesNum == state.currentSeries

            when {
                completedSet != null && completedSet.skipped -> SkippedSeriesCard(seriesNum)
                completedSet != null -> {
                    val isLastDone = setsForExercise.lastOrNull { !it.skipped }?.seriesNumber == seriesNum
                    CompletedSeriesCard(seriesNum, completedSet,
                        showRedo = isLastDone && isCurrent.not(),
                        onRedo = if (isLastDone) {{ viewModel.redoLastSeries() }} else null)
                }
                isCurrent && state.isRestTimerActive -> RestTimerCard(seriesNum, state.restTimeRemaining, state.effectiveRestSeconds,
                    { viewModel.skipRestTimer() }, { viewModel.pauseRestTimer() }, { viewModel.resumeRestTimer() })
                isCurrent -> ActiveSeriesCard(seriesNum, totalSeries, state, viewModel)
                else -> UpcomingSeriesCard(seriesNum)
            }
        }

        // ─── Post-dernière-série : proposer d'ajouter ou de continuer ───
        if (state.showPostLastSetPrompt) {
            item {
                PostLastSetPromptCard(
                    onAddSeries = { viewModel.addExtraSeries() },
                    onContinue = { viewModel.confirmMoveToNextExercise() },
                    continueLabel = if (state.isFreestyle && state.isLastExercise) "Terminer l'exercice"
                        else "Passer à l'exercice suivant"
                )
            }
        }

        // ─── Bouton "+ Ajouter une série" en fin de liste ───
        item {
            OutlinedButton(
                onClick = { viewModel.addExtraSeries() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeVibrant)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ajouter une série", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ═══════════════════════════════════════
// CARDIO SESSION CARD (remplace "Série x/x" pour le cardio)
// ═══════════════════════════════════════

@Composable
private fun CardioSessionCard(exercise: ExerciseEntity, state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // ─── GIF grand format ───
        if (exercise.gifUrl != null) {
            ExerciseGif(
                gifUrl = exercise.gifUrl!!,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 240.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }

        // ─── Chrono cardio en gros ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, Modifier.size(32.dp), tint = NeonGreen)
                Text("Session cardio", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = NeonGreen)

                // Durée cible
                Text(
                    "Objectif : ${exercise.repsMin}–${exercise.repsMax} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Chrono en temps réel (réutilise le tick global pour forcer recomposition)
                @Suppress("UNUSED_VARIABLE") val tick = state.globalChronoSeconds
                val elapsed = state.setStartTime?.let {
                    java.time.Duration.between(it, java.time.LocalDateTime.now()).seconds
                } ?: state.exerciseChronoSeconds

                // displayMedium = ~57sp : un seul caractère qui change est très
                // visible. tnum est critique ici pour que le hero chrono cardio
                // ne "shimmer" pas chaque seconde devant les yeux du user.
                Text(
                    fmtChrono(elapsed),
                    style = MaterialTheme.typography.displayMedium.tabularNum(),
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonGreen,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // ─── Boutons actions ───
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.isSetInProgress) {
                Button(
                    onClick = { viewModel.onSetStarted() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("DÉMARRER", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        // Compléter le cardio comme une seule "série" avec le poids du corps
                        viewModel.onWeightChanged(state.userBodyWeightKg.toString())
                        viewModel.onRepsChanged("1")
                        viewModel.onSetCompleted()
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("TERMINER LE CARDIO", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = { viewModel.skipToNextExercise() },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(20.dp))
            }
        }

        // ─── Tips ───
        if (exercise.tips.isNotBlank()) {
            CoachTipCard(exercise)
        }
    }
}

@Composable
private fun CompletedSeriesCard(seriesNumber: Int, data: WorkoutSetData, showRedo: Boolean = false, onRedo: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = NeonGreen)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Série $seriesNumber", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${data.weight} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    Text("${data.reps} reps", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                data.setDurationSeconds?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.FitnessCenter, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text("${it}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                data.restSecondsActual?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Pause, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text("${it}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                // Bouton refaire (dernière série uniquement)
                if (showRedo && onRedo != null) {
                    TextButton(onClick = onRedo, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Icon(Icons.Default.Replay, null, Modifier.size(14.dp), tint = OrangeVibrant)
                        Spacer(Modifier.width(4.dp))
                        Text("Refaire", style = MaterialTheme.typography.labelSmall, color = OrangeVibrant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkippedSeriesCard(seriesNumber: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SkipNext, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
            }
            Text("Série $seriesNumber — Passée", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RestTimerCard(seriesNum: Int, timeRemaining: Int, totalRest: Int, onSkip: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
    var isPaused by remember { mutableStateOf(false) }
    // 1.0 = plein (début) → 0.0 = vide (fin du repos)
    val progress = if (totalRest > 0) timeRemaining.toFloat() / totalRest else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(900),
        label = "arcProgress"
    )
    val almost = timeRemaining <= 5
    val arcColor = if (almost) NeonGreen else OrangeVibrant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (almost) NeonGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, arcColor.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REPOS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // Arc circulaire progressif
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    drawArc(
                        color = arcColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // Chrono au centre
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        fmtChrono(timeRemaining.toLong()),
                        style = MaterialTheme.typography.headlineMedium.tabularNum(),
                        fontWeight = FontWeight.Bold,
                        color = arcColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        if (almost) "Go !" else "Série $seriesNum",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Boutons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { if (isPaused) { onResume(); isPaused = false } else { onPause(); isPaused = true } }, Modifier.size(36.dp)) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, Modifier.size(20.dp))
                }
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = arcColor),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(if (almost) "C'est parti !" else "Passer", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** Card "hero" qui montre le décompte chronométré d'une série en cours (gainage, cardio...). */
@Composable
private fun TimedSetCountdownHero(
    secondsRemaining: Int,
    totalSeconds: Int,
    seriesNum: Int,
    totalSeries: Int
) {
    val progress = if (totalSeconds > 0) (totalSeconds - secondsRemaining).toFloat() / totalSeconds else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "timedSetProgress"
    )
    val isLastSeconds = secondsRemaining in 1..5
    val color = if (isLastSeconds) OrangeVibrant else NeonGreen

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Label "Série X/Y · Décompte"
            Text(
                "SÉRIE $seriesNum / $totalSeries · DÉCOMPTE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = color.copy(alpha = 0.85f)
            )

            // Cercle avec chiffre géant au centre
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Track de fond
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    drawArc(
                        color = color.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // Progress arc (se remplit de 0 à 360°)
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$secondsRemaining",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                    Text(
                        "secondes",
                        style = MaterialTheme.typography.labelMedium,
                        color = color.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                if (isLastSeconds) "🔥 Tiens bon !" else "Garde la position",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun ActiveSeriesCard(seriesNum: Int, totalSeries: Int, state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    val isTimeBasedExo = state.currentExercise?.isTimeBased == true
    val isTimedSetRunning = isTimeBasedExo && state.isSetInProgress && state.timedSetTotalSeconds > 0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, if (state.isSetInProgress) NeonGreen else OrangeVibrant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ─── Décompte GÉANT pour les séries chronométrées en cours ───
            if (isTimedSetRunning) {
                TimedSetCountdownHero(
                    secondsRemaining = state.timedSetSecondsRemaining,
                    totalSeconds = state.timedSetTotalSeconds,
                    seriesNum = seriesNum,
                    totalSeries = totalSeries
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(
                        if (state.isSetInProgress) NeonGreen.copy(alpha = 0.2f) else OrangeVibrant.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Text("$seriesNum", fontWeight = FontWeight.Bold, color = if (state.isSetInProgress) NeonGreen else OrangeVibrant)
                    }
                    Column {
                        Text("Série $seriesNum / $totalSeries", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (state.isSetInProgress) Text("EN COURS", style = MaterialTheme.typography.labelSmall, color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Chrono live série
                    if (state.isSetInProgress) {
                        // globalChronoSeconds force recomposition chaque seconde
                        val tick = state.globalChronoSeconds
                        val elapsed = state.setStartTime?.let { java.time.Duration.between(it, java.time.LocalDateTime.now()).seconds } ?: tick
                        Surface(shape = RoundedCornerShape(8.dp), color = NeonGreen.copy(alpha = 0.15f)) {
                            Text(fmtChrono(elapsed), Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium.tabularNum(), fontWeight = FontWeight.Bold, color = NeonGreen,
                                maxLines = 1, softWrap = false)
                        }
                    }
                    // Skip série
                    IconButton(onClick = { viewModel.skipCurrentSeries() }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.SkipNext, "Passer", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
            // Suggestions : derniere seance + PR + bouton Suggerer
            // Masqué pour les exos au poids du corps (pas de progression en charge)
            val isBodyweightExo = state.currentExercise?.variant == ExerciseVariant.BODYWEIGHT
            if (!isBodyweightExo && (state.lastSessionWeight != null || state.personalRecordWeight != null)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.lastSessionWeight != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.History, null, Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Text(
                                    "Dernière fois : ${state.lastSessionWeight} kg × ${state.lastSessionReps ?: "?"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (state.personalRecordWeight != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = BrightYellow)
                                Text(
                                    "Record : ${state.personalRecordWeight} kg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrightYellow,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    // Bouton Suggerer
                    if (state.lastSessionWeight != null) {
                        FilledTonalButton(
                            onClick = { viewModel.suggestWeight() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("+5 kg", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Poids, Reps ET Repos côte à côte avec steppers verticaux
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val localCtx = LocalContext.current
                fun tactile(milestone: Boolean) {
                    if (!state.vibrationEnabled) return
                    if (milestone) com.shredcoach.app.presentation.util.hapticHeavy(localCtx)
                    else com.shredcoach.app.presentation.util.hapticClick(localCtx)
                }
                // Poids (kg) — masqué pour les exos au poids du corps (le volume utilise userWeight/2)
                val isBodyweight = state.currentExercise?.variant == ExerciseVariant.BODYWEIGHT
                if (!isBodyweight) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SessionStepper(
                            label = "Poids (kg)",
                            value = state.currentSetWeight,
                            onIncrement = {
                                val w = (state.currentSetWeight.toDoubleOrNull() ?: 0.0) + 1.0
                                if (w <= 300.0) { viewModel.onWeightChanged(fmtWeight(w)); tactile(w % 10.0 == 0.0) }
                            },
                            onDecrement = {
                                val w = (state.currentSetWeight.toDoubleOrNull() ?: 0.0) - 1.0
                                if (w >= 0.0) { viewModel.onWeightChanged(fmtWeight(w)); tactile(w % 10.0 == 0.0) }
                            },
                            onValueChange = { viewModel.onWeightChanged(it) },
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                    StepperDivider()
                }
                // Reps ou Durée (pour les exercices isTimeBased)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val isTimeBased = state.currentExercise?.isTimeBased == true
                    // Verrouiller le stepper pendant le décompte actif (éviter que l'user change la durée en plein milieu)
                    val locked = isTimedSetRunning
                    SessionStepper(
                        label = if (isTimeBased) "Durée (s)" else "Reps",
                        value = state.currentSetReps,
                        onIncrement = {
                            if (locked) return@SessionStepper
                            val r = (state.currentSetReps.toIntOrNull() ?: 10) + (if (isTimeBased) 5 else 1)
                            if (r <= (if (isTimeBased) 300 else 50)) { viewModel.onRepsChanged(r.toString()); tactile(r % 5 == 0) }
                        },
                        onDecrement = {
                            if (locked) return@SessionStepper
                            val r = (state.currentSetReps.toIntOrNull() ?: 10) - (if (isTimeBased) 5 else 1)
                            if (r >= 1) { viewModel.onRepsChanged(r.toString()); tactile(r % 5 == 0) }
                        },
                        onValueChange = { if (!locked) viewModel.onRepsChanged(it) },
                        keyboardType = KeyboardType.Number
                    )
                }
                StepperDivider()
                // Repos (secondes) — ajustable par ±15s
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val rest = state.effectiveRestSeconds
                    SessionStepper(
                        label = "Repos",
                        value = "${rest}s",
                        onIncrement = { viewModel.onRestSecondsChanged(rest + 15); tactile(false) },
                        onDecrement = { viewModel.onRestSecondsChanged(rest - 15); tactile(false) },
                        onValueChange = { it.replace("s", "").toIntOrNull()?.let { v -> viewModel.onRestSecondsChanged(v) } },
                        keyboardType = KeyboardType.Number
                    )
                }
            }

        }
    }
}

@Composable
private fun PostLastSetPromptCard(onAddSeries: () -> Unit, onContinue: () -> Unit, continueLabel: String = "Passer à l'exercice suivant") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f)),
        border = BorderStroke(1.5.dp, OrangeVibrant.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(32.dp), tint = NeonGreen)
            Text("Toutes les séries complétées !",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Tu veux enchaîner une série bonus ou passer à l'exercice suivant ?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddSeries,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, OrangeVibrant)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = OrangeVibrant)
                    Spacer(Modifier.width(6.dp))
                    Text("Encore une série bonus", fontWeight = FontWeight.Bold, color = OrangeVibrant)
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text(continueLabel, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// VUE D'ENSEMBLE SÉANCE (preview mid-session)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseOverviewPanel(state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    val ctx = LocalContext.current
    var confirmDeleteIndex by remember { mutableStateOf(-1) }

    // Dialog de confirmation suppression
    if (confirmDeleteIndex >= 0) {
        val exoName = state.exercises.getOrNull(confirmDeleteIndex)?.name ?: ""
        AlertDialog(
            onDismissRequest = { confirmDeleteIndex = -1 },
            title = { Text("Retirer cet exercice ?", fontWeight = FontWeight.Bold) },
            text = { Text("\"$exoName\" sera retiré de la séance en cours.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.removeExercise(confirmDeleteIndex); confirmDeleteIndex = -1 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteIndex = -1 }) { Text("Annuler") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Surface(tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Vue d'ensemble", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.isFreestyle) "${state.exercises.size} exercice${if (state.exercises.size > 1) "s" else ""} · Ajoute ou termine"
                        else "${state.exercises.size} exercices · Tape pour lancer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // En freestyle, si l'exercice courant est terminé (tous les sets faits), on ne peut pas
                // "retourner" à lui — masquer le bouton. Le user doit ajouter un exo ou terminer.
                val currentExo = state.currentExercise
                val currentExoDone = currentExo != null && run {
                    val totalSets = currentExo.series + (state.extraSeriesMap[state.currentExerciseIndex] ?: 0)
                    val setsDone = state.completedSets.count { it.exerciseId == currentExo.id && !it.skipped }
                    totalSets > 0 && setsDone >= totalSets
                }
                val canGoBack = !(state.isFreestyle && currentExoDone)
                if (canGoBack) {
                    FilledTonalButton(onClick = { viewModel.toggleExerciseOverview() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retour", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(state.exercises) { index, exercise ->
                // Calcul rigoureux : un exercice est "done" quand TOUTES ses séries sont complétées
                // (base + bonus), ou quand il a été entièrement skipped.
                val totalSetsExpected = exercise.series + (state.extraSeriesMap[index] ?: 0)
                val setsDone = state.completedSets.count { it.exerciseId == exercise.id && !it.skipped }
                val skippedAll = state.skippedExercises.contains(index)
                val allSetsDone = setsDone >= totalSetsExpected && totalSetsExpected > 0
                val isDone = skippedAll || allSetsDone || index < state.currentExerciseIndex
                // "isCurrent" = pointeur du VM ET sets pas tous finis (évite "en cours" sur exo terminé en freestyle)
                val isCurrent = index == state.currentExerciseIndex && !isDone
                val isFuture = !isDone && !isCurrent

                OverviewExerciseCard(
                    exercise = exercise,
                    index = index,
                    isCurrent = isCurrent,
                    isDone = isDone,
                    isFuture = isFuture,
                    setsCompleted = setsDone,
                    onJump = if (!isCurrent && !isDone) {{ viewModel.jumpToExercise(index) }} else null,
                    onDelete = if (isFuture) {{ confirmDeleteIndex = index }} else null
                )
            }

            // ─── Freestyle : boutons d'action ───
            if (state.isFreestyle) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.openAddExerciseDialog() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter un exercice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.completeWorkoutFromOverview() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, NeonGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = NeonGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Terminer la séance", fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewExerciseCard(
    exercise: ExerciseEntity,
    index: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    isFuture: Boolean,
    setsCompleted: Int,
    onJump: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val alpha = if (isDone && !isCurrent) 0.45f else 1f

    Card(
        onClick = { onJump?.invoke() },
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        shape = RoundedCornerShape(14.dp),
        enabled = onJump != null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrent -> OrangeVibrant.copy(alpha = 0.1f)
                isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isCurrent) BorderStroke(2.dp, OrangeVibrant) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // GIF thumbnail ou numéro
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) {
                if (exercise.gifUrl != null) {
                    ExerciseGif(
                        gifUrl = exercise.gifUrl!!,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            when {
                                isDone -> NeonGreen.copy(alpha = 0.2f)
                                isCurrent -> OrangeVibrant.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
                // Badge statut en overlay
                if (isDone) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = NeonGreen)
                    }
                } else {
                    // Numéro en overlay (coin)
                    Surface(
                        shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 6.dp),
                        color = if (isCurrent) OrangeVibrant else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text("${index + 1}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = Color.White)
                    }
                }
            }

            // Info exercice — 3 lignes indépendantes, jamais comprimées
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Ligne 1 : nom de l'exercice (pleine largeur, tronque si besoin)
                Text(exercise.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)

                // Ligne 2 : groupe musculaire + badge EN COURS
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exercise.muscleGroup.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    if (isCurrent) {
                        Surface(shape = RoundedCornerShape(4.dp), color = OrangeVibrant) {
                            Text("EN COURS", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = Color.White, fontSize = 9.sp)
                        }
                    }
                }

                // Ligne 3 : séries/reps/repos OU "X séries faites"
                if (isDone && setsCompleted > 0) {
                    Text("$setsCompleted séries faites", style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("${exercise.series} séries · ${exercise.repsMin}-${exercise.repsMax} reps · ${exercise.restSeconds}s repos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            // Bouton supprimer (seulement les futurs)
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Retirer", Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun StepperDivider() {
    Box(
        Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 8.dp)
            .background(OrangeVibrant.copy(alpha = 0.3f))
    )
}

@Composable
private fun UpcomingSeriesCard(seriesNumber: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                Text("$seriesNumber", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
            Text("Série $seriesNumber", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

// ═══════════════════════════════════════
// BOUTON PRINCIPAL
// ═══════════════════════════════════════

@Composable
private fun MainActionButton(state: WorkoutSessionState, viewModel: WorkoutSessionViewModel) {
    if (state.isRestTimerActive || state.showExerciseTransition || state.showPostLastSetPrompt) return
    state.currentExercise ?: return
    val context = LocalContext.current

    Surface(tonalElevation = 8.dp) {
        if (!state.isSetInProgress) {
            Button(onClick = { com.shredcoach.app.presentation.util.hapticClick(context); viewModel.onSetStarted() }, Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                Text("DÉMARRER SÉRIE ${state.currentSeries}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        } else {
            val isTimedSetRunning = state.currentExercise?.isTimeBased == true && state.timedSetTotalSeconds > 0
            Button(
                onClick = { com.shredcoach.app.presentation.util.hapticHeavy(context); viewModel.onSetCompleted() },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTimedSetRunning) OrangeVibrant else NeonGreen
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    if (isTimedSetRunning) Icons.Default.Stop else Icons.Default.Check,
                    null, Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        // Série chronométrée en cours : bouton = arrêter prématurément le décompte
                        isTimedSetRunning -> "ARRÊTER MAINTENANT"
                        state.isLastSeries && state.isLastExercise && state.isFreestyle -> "TERMINER L'EXERCICE"
                        state.isLastSeries && state.isLastExercise -> "TERMINER LA SÉANCE"
                        state.isLastSeries -> "EXERCICE SUIVANT"
                        else -> "SÉRIE TERMINÉE"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// TRANSITION ENTRE EXERCICES
// ═══════════════════════════════════════

@Composable
private fun ExerciseTransitionOverlay(
    modifier: Modifier = Modifier,
    fromName: String, toName: String,
    exercisesDone: Int, totalExercises: Int,
    exoSets: Int, exoReps: Int, exoVolume: Double, exoDuration: Long, exoSkipped: Int,
    shreddyMessage: String = "",
    isShreddyThinking: Boolean = false,
    shreddySource: String = "",
    onDismiss: () -> Unit
) {
    // Animations d'entree
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "checkScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "checkAlpha"
    )

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Contenu scrollable
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        Spacer(Modifier.height(16.dp))

        // Check icon — scale up + fade in
        Surface(
            shape = CircleShape, color = NeonGreen.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = NeonGreen)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(fromName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha })
        Text("TERMINÉ", style = MaterialTheme.typography.labelLarge, color = NeonGreen, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)

        // Message personnalisé de Shreddy
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
                }
            }
            if (isShreddyThinking) {
                // Animation "Shreddy réfléchit..."
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Shreddy réfléchit", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, color = OrangeVibrant.copy(alpha = 0.7f))
                    repeat(3) { i ->
                        val inf = rememberInfiniteTransition(label = "td$i")
                        val a by inf.animateFloat(0.3f, 1f,
                            infiniteRepeatable(tween(400, delayMillis = i * 150), RepeatMode.Reverse), label = "ta$i")
                        Box(Modifier.size(5.dp).offset(y = (-2 * a).dp)
                            .clip(CircleShape).background(OrangeVibrant.copy(alpha = a)))
                    }
                }
            } else if (shreddyMessage.isNotBlank()) {
                Column {
                    Text(
                        shreddyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stats de l'exercice terminé
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Résumé exercice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TransitionStat(icon = Icons.Default.FitnessCenter, value = "$exoSets", label = "Séries")
                    TransitionStat(icon = Icons.Default.RepeatOne, value = "$exoReps", label = "Reps")
                    TransitionStat(icon = Icons.Default.MonitorWeight, value = "%.0f kg".format(exoVolume), label = "Volume")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // exoDuration affiché dans la transition statique → pas de tick,
                    // mais tnum reste cohérent avec les autres stats numériques.
                    TransitionStat(icon = Icons.Default.Timer, value = fmtChrono(exoDuration), label = "Durée")
                    if (exoSkipped > 0) {
                        TransitionStat(icon = Icons.Default.SkipNext, value = "$exoSkipped", label = "Passées")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Progression globale
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$exercisesDone / $totalExercises exercices", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { exercisesDone.toFloat() / totalExercises.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = NeonGreen
            )
        }

        Spacer(Modifier.height(16.dp))
        } // fin Column scrollable

        // BOUTON PROCHAIN — toujours visible en bas (hors scroll)
        val isFreestyleOverview = toName == "Vue d'ensemble"
        Surface(tonalElevation = 4.dp) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isFreestyleOverview) "CONTINUER" else "PROCHAIN EXERCICE",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                    )
                    Text(
                        if (isFreestyleOverview) "Vue d'ensemble" else toName,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        // tnum sur la valeur (souvent numérique : "150 kg", "1:23:45", "12") +
        // maxLines=1 → la stat ne wrappe pas et la Column reste alignée.
        Text(value, style = MaterialTheme.typography.titleSmall.tabularNum(), fontWeight = FontWeight.Bold,
            maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

// ═══════════════════════════════════════
// COMPOSANTS UTILITAIRES
// ═══════════════════════════════════════

@Composable
private fun CoachTipCard(exercise: ExerciseEntity) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lightbulb, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Coach", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
            }
            if (expanded) {
                if (exercise.tips.isNotBlank()) Text(exercise.tips, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                if (exercise.executionKey.isNotBlank()) Text(exercise.executionKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
            }
        }
    }
}

/** Badge à hauteur uniforme */
@Composable
private fun TagBadge(text: String, bgColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bgColor, modifier = Modifier.height(28.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
        }
    }
}

/** Formate le poids sans décimale inutile : 50.0 → "50", 52.5 → "52.5" */
private fun fmtWeight(w: Double): String {
    return if (w == w.toLong().toDouble()) w.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", w)
}

private fun fmtChrono(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun playRestEndSound(context: Context) {
    try {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.play()
    } catch (_: Exception) {}
}

@Composable
private fun SessionStepper(
    label: String,
    value: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        // Bouton +
        Surface(
            onClick = { onIncrement() },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = OrangeVibrant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
            }
        }
        // Valeur editable — BasicTextField sans padding interne
        Surface(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Bouton -
        Surface(
            onClick = { onDecrement() },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(8.dp),
            color = OrangeVibrant.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OrangeVibrant)
            }
        }
    }
}

private fun vibrate(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 500), -1))
        } else {
            @Suppress("DEPRECATION") val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 500), -1))
            else @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 300, 200, 300, 200, 500), -1)
        }
    } catch (_: Exception) {}
}
