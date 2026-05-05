package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutScreen(navController: NavController, viewModel: CustomWorkoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Exercise picker dialog
    if (state.showExercisePicker && state.pickerMuscleGroup != null) {
        ExercisePickerDialog(
            muscleGroup = state.pickerMuscleGroup!!,
            exercises = state.availableExercises[state.pickerMuscleGroup] ?: emptyList(),
            onSelect = { viewModel.selectExercise(it) },
            onDismiss = { viewModel.closePicker() }
        )
    }

    // Confirm mode switch (perte de travail)
    state.pendingModeSwitch?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelModeSwitch() },
            icon = { Icon(Icons.Default.SwapHoriz, null, tint = OrangeVibrant) },
            title = { Text("Changer de mode ?", fontWeight = FontWeight.Bold) },
            text = {
                Text(when (pending) {
                    CreationMode.TEMPLATE -> "Ta séance actuelle sera remplacée par le modèle suggéré (4 échauffement + 7 muscu + 1 cardio)."
                    CreationMode.BLANK -> "Ta séance actuelle sera effacée et tu repartiras d'une page blanche."
                })
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmModeSwitch() },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                ) { Text("Continuer") }
            },
            dismissButton = { TextButton(onClick = { viewModel.cancelModeSwitch() }) { Text("Annuler") } }
        )
    }

    // Add slot (muscle group picker for a section)
    if (state.showAddSlotPicker && state.addSlotSection != null) {
        AddSlotDialog(
            section = state.addSlotSection!!,
            onSelect = { mg ->
                viewModel.addSlot(mg)
                viewModel.closeAddSlotPicker()
            },
            onDismiss = { viewModel.closeAddSlotPicker() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Créer ma séance", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favori",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Button(
                    onClick = { viewModel.saveAndStart() },
                    Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(16.dp),
                    enabled = state.slots.any { it.selectedExercise != null }
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("LANCER LA SÉANCE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Hero : durée live + breakdown + chips durée cible ───
                item {
                    DurationHeroCard(
                        targetMinutes = state.durationMinutes,
                        estimatedMinutes = state.estimatedTotalMinutes,
                        warmupSec = state.warmupSeconds,
                        strengthSec = state.strengthSeconds,
                        cardioSec = state.cardioSeconds,
                        transitionSec = state.transitionSeconds,
                        onTargetChange = { viewModel.onDurationChanged(it) },
                        showAutoBalance = state.creationMode == CreationMode.TEMPLATE
                            && kotlin.math.abs(state.estimatedTotalMinutes - state.durationMinutes) > 10,
                        onAutoBalance = { viewModel.autoAdjustSlotsToDuration() }
                    )
                }

                // ─── Tabs de mode + nom ───
                item {
                    ModeTabs(
                        current = state.creationMode,
                        onSelect = { viewModel.switchCreationMode(it) }
                    )
                }
                item {
                    OutlinedTextField(
                        state.name, { viewModel.onNameChanged(it) },
                        label = { Text("Nom de la séance") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                }

                // ─── Section Échauffement ───
                sectionBlock(
                    section = WorkoutSection.WARMUP,
                    state = state, viewModel = viewModel
                )

                // ─── Section Musculation ───
                sectionBlock(
                    section = WorkoutSection.STRENGTH,
                    state = state, viewModel = viewModel
                )

                // ─── Section Cardio ───
                sectionBlock(
                    section = WorkoutSection.CARDIO,
                    state = state, viewModel = viewModel
                )

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Navigation vers la séance quand sauvée
    LaunchedEffect(state.savedWorkoutLogId) {
        val logId = state.savedWorkoutLogId
        if (logId != null && logId > 0) {
            navController.navigate(Screen.WorkoutSession.createRoute(logId))
        }
    }
}

// ═══════════════════════════════════════
// SLOT CARD
// ═══════════════════════════════════════

@Composable
private fun ExerciseSlotCard(
    slot: CustomExerciseSlot, index: Int, totalSlots: Int,
    vibrationEnabled: Boolean,
    onPickExercise: () -> Unit, onUpdateSeries: (Int) -> Unit,
    onUpdateReps: (Int) -> Unit, onUpdateRest: (Int) -> Unit,
    onUpdateDuration: (Int) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit
) {
    val isWarmupOrCardio = slot.muscleGroup == MuscleGroup.WARMUP || slot.muscleGroup == MuscleGroup.CARDIO
    val mgColor = when (slot.muscleGroup) {
        MuscleGroup.WARMUP -> OrangeVibrant
        MuscleGroup.CARDIO -> NeonGreen
        else -> Color(slot.selectedExercise?.variant?.color ?: 0xFF3B82F6)
    }
    val canMoveUp = index > 0
    val canMoveDown = index < totalSlots - 1

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(mgColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = mgColor)
                    }
                    Text(slot.muscleGroup.displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = mgColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, Modifier.size(32.dp), enabled = canMoveUp) {
                        Icon(Icons.Default.KeyboardArrowUp, "Monter", Modifier.size(20.dp),
                            tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    }
                    IconButton(onClick = onMoveDown, Modifier.size(32.dp), enabled = canMoveDown) {
                        Icon(Icons.Default.KeyboardArrowDown, "Descendre", Modifier.size(20.dp),
                            tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    }
                    IconButton(onClick = onRemove, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Supprimer", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            }

            // Exercice sélectionné ou bouton choisir
            val ctx = LocalContext.current
            Card(
                Modifier.fillMaxWidth().clickable { onPickExercise() },
                colors = CardDefaults.cardColors(containerColor = if (slot.selectedExercise != null) mgColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (slot.selectedExercise != null) {
                        // Thumbnail GIF
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center) {
                            if (slot.selectedExercise.gifUrl != null) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(ctx).data(slot.selectedExercise.gifUrl)
                                        .size(Size(112, 112)).crossfade(true).build(),
                                    contentDescription = slot.selectedExercise.name, modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = { Icon(Icons.Default.FitnessCenter, null, Modifier.size(24.dp), tint = mgColor.copy(alpha = 0.5f)) }
                                )
                            } else {
                                Icon(Icons.Default.FitnessCenter, null, Modifier.size(24.dp), tint = mgColor.copy(alpha = 0.5f))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                slot.selectedExercise.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp
                            )
                            Text(slot.selectedExercise.variant.displayName, style = MaterialTheme.typography.labelSmall, color = mgColor, maxLines = 1)
                        }
                    } else {
                        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        Text("Choisir un exercice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                    }
                    Icon(Icons.Default.SwapHoriz, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            // Paramètres ajustables (seulement pour muscu)
            if (!isWarmupOrCardio && slot.selectedExercise != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VerticalStepper("Séries", "${slot.series}", vibrationEnabled) { onUpdateSeries(it) }
                    }
                    Box(
                        Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 4.dp)
                            .background(OrangeVibrant.copy(alpha = 0.3f))
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VerticalStepper("Reps", "${slot.repsMin}-${slot.repsMax}", vibrationEnabled) { onUpdateReps(it) }
                    }
                    Box(
                        Modifier.width(1.5.dp).fillMaxHeight().padding(vertical = 4.dp)
                            .background(OrangeVibrant.copy(alpha = 0.3f))
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        VerticalStepper("Repos", "${slot.restSeconds}s", vibrationEnabled) { onUpdateRest(it * 15) }
                    }
                }
            }

            // Durée ajustable (warmup / cardio)
            if (isWarmupOrCardio && slot.selectedExercise != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VerticalStepper("Durée", "${slot.durationMinutes ?: if (slot.muscleGroup == MuscleGroup.WARMUP) 3 else 15} min",
                        vibrationEnabled) { onUpdateDuration(it) }
                }
            }
        }
    }
}

@Composable
private fun VerticalStepper(label: String, value: String, vibrationEnabled: Boolean = true, onChange: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun tactile() {
        if (vibrationEnabled) com.shredcoach.app.presentation.util.hapticClick(ctx)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        // Bouton +
        Surface(
            onClick = { tactile(); onChange(1) },
            modifier = Modifier.size(width = 56.dp, height = 28.dp),
            shape = RoundedCornerShape(6.dp),
            color = OrangeVibrant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            }
        }
        // Valeur
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OrangeVibrant
        )
        // Bouton -
        Surface(
            onClick = { tactile(); onChange(-1) },
            modifier = Modifier.size(width = 56.dp, height = 28.dp),
            shape = RoundedCornerShape(6.dp),
            color = OrangeVibrant.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OrangeVibrant)
            }
        }
    }
}

// ═══════════════════════════════════════
// PICKER DIALOG
// ═══════════════════════════════════════

@Composable
private fun ExercisePickerDialog(
    muscleGroup: MuscleGroup, exercises: List<ExerciseEntity>,
    onSelect: (ExerciseEntity) -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir : ${muscleGroup.displayName}", fontWeight = FontWeight.Bold) },
        text = {
            val ctx = LocalContext.current
            LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(exercises) { exercise ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onSelect(exercise) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Thumbnail GIF
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center) {
                                if (exercise.gifUrl != null) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(ctx).data(exercise.gifUrl)
                                            .size(Size(96, 96)).crossfade(true).build(),
                                        contentDescription = exercise.name, modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = { Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)) }
                                    )
                                } else {
                                    Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    exercise.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(exercise.variant.color).copy(alpha = 0.2f)) {
                                        Text(exercise.variant.displayName, Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall, color = Color(exercise.variant.color), maxLines = 1)
                                    }
                                    Text("${exercise.series}x${exercise.repsMin}-${exercise.repsMax} · ${exercise.restSeconds}s",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

// ═══════════════════════════════════════
// Duration HERO — live estimation + breakdown
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationHeroCard(
    targetMinutes: Int,
    estimatedMinutes: Int,
    warmupSec: Int,
    strengthSec: Int,
    cardioSec: Int,
    transitionSec: Int,
    onTargetChange: (Int) -> Unit,
    showAutoBalance: Boolean = false,
    onAutoBalance: () -> Unit = {}
) {
    val presets = listOf(60, 90, 120, 150, 180)
    val delta = estimatedMinutes - targetMinutes
    val deltaColor = when {
        kotlin.math.abs(delta) <= 5 -> NeonGreen
        kotlin.math.abs(delta) <= 15 -> OrangeVibrant
        else -> MaterialTheme.colorScheme.error
    }
    val total = (warmupSec + strengthSec + cardioSec + transitionSec).coerceAtLeast(1).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        OrangeVibrant.copy(alpha = 0.96f),
                        OrangeVibrant.copy(alpha = 0.78f)
                    )
                )
            ).padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ─── Row principale : estimée + cible + delta ───
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Durée estimée", style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(formatMinutes(estimatedMinutes),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 40.sp)
                            Text("/ cible ${targetMinutes} min",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    // Badge delta
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = deltaColor.copy(alpha = 0.95f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (delta == 0) "OK" else "${if (delta > 0) "+" else ""}${delta}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold, color = Color.White
                            )
                            if (delta != 0) {
                                Text("min", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }

                // ─── Barre stacked (breakdown) ───
                Row(
                    Modifier.fillMaxWidth().height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    if (warmupSec > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(warmupSec / total).background(Color.White.copy(alpha = 0.95f)))
                    if (strengthSec > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(strengthSec / (total - warmupSec).coerceAtLeast(1f)).background(Color.White.copy(alpha = 0.7f)))
                    if (cardioSec > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(cardioSec / (total - warmupSec - strengthSec).coerceAtLeast(1f)).background(Color.White.copy(alpha = 0.45f)))
                }

                // ─── Légende breakdown ───
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BreakdownItem("Échauf.", warmupSec / 60, 0.95f)
                    BreakdownItem("Muscu", strengthSec / 60, 0.7f)
                    BreakdownItem("Cardio", cardioSec / 60, 0.45f)
                    BreakdownItem("Transitions", transitionSec / 60, 0.25f)
                }

                // ─── Chips de durée cible ───
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Cible :", style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp))
                    presets.forEach { mins ->
                        val selected = mins == targetMinutes
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .clickable { onTargetChange(mins) },
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "${mins} min",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected) OrangeVibrant else Color.White
                            )
                        }
                    }
                }

                // ─── CTA Auto-balance (affichée si delta > 10 min en mode TEMPLATE) ───
                if (showAutoBalance) {
                    Surface(
                        onClick = onAutoBalance,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.95f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = OrangeVibrant)
                            Text("Rééquilibrer les exos muscu", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold, color = OrangeVibrant,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp),
                                tint = OrangeVibrant.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinutes(m: Int): String {
    val h = m / 60
    val mm = m % 60
    return if (h > 0) "${h}h${if (mm > 0) String.format("%02d", mm) else ""}" else "${m} min"
}

@Composable
private fun BreakdownItem(label: String, minutes: Int, alpha: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = alpha)))
        Text("$label · ${minutes}'",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium)
    }
}

// ═══════════════════════════════════════
// Mode tabs (TEMPLATE / BLANK)
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTabs(current: CreationMode, onSelect: (CreationMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab(
            modifier = Modifier.weight(1f),
            label = "Modèle suggéré",
            subtitle = "Équilibré, personnalisable",
            icon = Icons.Default.AutoAwesome,
            selected = current == CreationMode.TEMPLATE,
            onClick = { onSelect(CreationMode.TEMPLATE) }
        )
        ModeTab(
            modifier = Modifier.weight(1f),
            label = "Partir de zéro",
            subtitle = "100% personnalisé",
            icon = Icons.Default.Tune,
            selected = current == CreationMode.BLANK,
            onClick = { onSelect(CreationMode.BLANK) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeTab(
    modifier: Modifier = Modifier,
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, null, Modifier.size(18.dp),
                tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
        }
    }
}

// ═══════════════════════════════════════
// Section block (warmup / strength / cardio)
// ═══════════════════════════════════════
private fun androidx.compose.foundation.lazy.LazyListScope.sectionBlock(
    section: WorkoutSection,
    state: CustomWorkoutState,
    viewModel: CustomWorkoutViewModel
) {
    val slots = state.slotsInSection(section)
    val totalSec = when (section) {
        WorkoutSection.WARMUP -> state.warmupSeconds
        WorkoutSection.STRENGTH -> state.strengthSeconds
        WorkoutSection.CARDIO -> state.cardioSeconds
    }

    item(key = "header-${section.name}") {
        SectionHeader(
            section = section,
            slotCount = slots.size,
            totalSeconds = totalSec,
            onAddClick = { viewModel.openAddSlotPicker(section) }
        )
    }

    if (slots.isEmpty()) {
        item(key = "empty-${section.name}") {
            EmptySectionHint(section = section,
                onAddClick = { viewModel.openAddSlotPicker(section) })
        }
    } else {
        items(slots, key = { "slot-${it.index}-${it.slot.muscleGroup}" }) { (index, slot) ->
            ExerciseSlotCard(
                slot = slot, index = index, totalSlots = state.slots.size,
                vibrationEnabled = state.vibrationEnabled,
                onPickExercise = { viewModel.openExercisePicker(index) },
                onUpdateSeries = { viewModel.updateSlotSeries(index, it) },
                onUpdateReps = { viewModel.updateSlotReps(index, it) },
                onUpdateRest = { viewModel.updateSlotRest(index, it) },
                onUpdateDuration = { viewModel.updateSlotDuration(index, it) },
                onRemove = { viewModel.removeSlot(index) },
                onMoveUp = { viewModel.moveSlot(index, index - 1) },
                onMoveDown = { viewModel.moveSlot(index, index + 1) }
            )
        }
    }
}

@Composable
private fun SectionHeader(
    section: WorkoutSection,
    slotCount: Int,
    totalSeconds: Int,
    onAddClick: () -> Unit
) {
    val (icon, color) = when (section) {
        WorkoutSection.WARMUP -> Icons.Default.LocalFireDepartment to OrangeVibrant
        WorkoutSection.STRENGTH -> Icons.Default.FitnessCenter to MaterialTheme.colorScheme.onSurface
        WorkoutSection.CARDIO -> Icons.AutoMirrored.Filled.DirectionsRun to NeonGreen
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Column(Modifier.weight(1f)) {
            Text(section.displayName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = color)
            Text("$slotCount exercice${if (slotCount > 1) "s" else ""} · ${totalSeconds / 60} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onAddClick,
            modifier = Modifier.size(34.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = color.copy(alpha = 0.15f), contentColor = color
            )
        ) {
            Icon(Icons.Default.Add, "Ajouter", Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptySectionHint(section: WorkoutSection, onAddClick: () -> Unit) {
    val hint = when (section) {
        WorkoutSection.WARMUP -> "Ajoute des exos d'échauffement pour préparer ton corps"
        WorkoutSection.STRENGTH -> "Ajoute tes exos muscu par groupe musculaire"
        WorkoutSection.CARDIO -> "Ajoute du cardio en fin de séance (optionnel)"
    }
    Surface(
        onClick = onAddClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = androidx.compose.foundation.BorderStroke(1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f))
        }
    }
}

// ═══════════════════════════════════════
// AddSlotDialog : picker groupe musculaire pour une section
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSlotDialog(
    section: WorkoutSection,
    onSelect: (MuscleGroup) -> Unit,
    onDismiss: () -> Unit
) {
    // Le ViewModel ne nous appelle que pour STRENGTH (warmup/cardio ajoutés directement).
    val groups = MuscleGroup.values().filter {
        it != MuscleGroup.WARMUP && it != MuscleGroup.CARDIO
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter à ${section.displayName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groups.forEach { mg ->
                    Surface(
                        onClick = { onSelect(mg) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mg.displayName, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ═══════════════════════════════════════
// Legacy duration selector — deprecated (ignored, kept for reference)
// ═══════════════════════════════════════
@Suppress("unused")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationSelectorCard(
    currentDuration: Int,
    recommendedStrength: Int,
    currentStrength: Int,
    onDurationChange: (Int) -> Unit,
    onAutoAdjust: () -> Unit
) {
    val presets = listOf(60, 90, 120, 150, 180)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header avec icône + heures:minutes
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(OrangeVibrant.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Timer, null, tint = OrangeVibrant, modifier = Modifier.size(22.dp)) }

                Column(Modifier.weight(1f)) {
                    Text("Durée de la séance", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
                    Text(
                        if (currentDuration >= 60) "${currentDuration / 60}h${if (currentDuration % 60 != 0) " ${currentDuration % 60}min" else ""}" else "${currentDuration} min",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Chips de durée
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { mins ->
                    val selected = mins == currentDuration
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                            .clickable { onDurationChange(mins) },
                        color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "${mins} min",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Barre de recommandation : tient / dépasse
            val ratio = (currentStrength.toFloat() / recommendedStrength.coerceAtLeast(1)).coerceIn(0f, 1.5f)
            val isOver = currentStrength > recommendedStrength
            val barColor = when {
                isOver -> MaterialTheme.colorScheme.error
                currentStrength == recommendedStrength -> NeonGreen
                else -> OrangeVibrant
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$currentStrength / $recommendedStrength exos muscu recommandés",
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        color = barColor
                    )
                    Spacer(Modifier.weight(1f))
                    if (currentStrength != recommendedStrength) {
                        Surface(
                            onClick = onAutoAdjust,
                            shape = RoundedCornerShape(8.dp),
                            color = OrangeVibrant.copy(alpha = 0.15f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, tint = OrangeVibrant, modifier = Modifier.size(14.dp))
                                Text("Ajuster", style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold, color = OrangeVibrant)
                            }
                        }
                    } else {
                        Icon(Icons.Default.CheckCircle, null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }
                }
                // Barre progressive avec animation
                val animatedRatio by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = ratio / 1.5f, label = "duration-bar"
                )
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxWidth(animatedRatio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}
