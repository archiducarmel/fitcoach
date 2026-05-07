package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.domain.workout.WorkoutRoutine
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutGeneratorScreen(
    navController: NavController,
    viewModel: WorkoutGeneratorViewModel = hiltViewModel()
) {
    val selectedDuration by viewModel.selectedDuration.collectAsState()
    val selectedLevel by viewModel.selectedLevel.collectAsState()
    val selectedEquipment by viewModel.selectedEquipment.collectAsState()
    val selectedRoutineId by viewModel.selectedRoutineId.collectAsState()
    val generatedWorkout by viewModel.generatedWorkout.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val error by viewModel.error.collectAsState()

    val selectedRoutine = remember(selectedRoutineId) { RoutineCatalog.byId(selectedRoutineId) }

    // Navigate to preview when workout is generated
    LaunchedEffect(generatedWorkout) {
        if (generatedWorkout != null) {
            navController.navigate(Screen.Workout.route)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxHeight().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.Center) {
                        Text("Générer une séance", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        Text("${selectedRoutine.displayName} personnalisée", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = OrangeVibrant.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = OrangeVibrant
                    )
                    Column {
                        Text(
                            "Séance ${selectedRoutine.displayName} Intelligente",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            selectedRoutine.tagline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Routine Selection (Full Body, Push, Pull, Legs, …)
            RoutineSelectionSection(
                routines = RoutineCatalog.builtIn,
                selectedRoutineId = selectedRoutineId,
                onRoutineSelected = { viewModel.selectRoutine(it) }
            )

            // Duration Selection
            DurationSelectionSection(
                selectedDuration = selectedDuration,
                onDurationSelected = { viewModel.selectDuration(it) }
            )

            // Level Selection
            LevelSelectionSection(
                selectedLevel = selectedLevel,
                onLevelSelected = { viewModel.selectLevel(it) }
            )

            // Equipment Selection
            EquipmentSelectionSection(
                selectedEquipment = selectedEquipment,
                onEquipmentSelected = { viewModel.selectEquipment(it) }
            )

            // Error Display
            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Generate Button
            Button(
                onClick = { viewModel.generateWorkout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                enabled = !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeVibrant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Génération en cours...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "GÉNÉRER MA SÉANCE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Sélecteur de routine (Full Body, Push, Pull, Legs, Upper, Lower, Chest+Tri,
 * Back+Bi). Chips horizontaux scrollables — pattern FAANG (Apple Fitness+,
 * Strong, Hevy) pour exposer 8 options sans casser la verticalité de la page.
 *
 * Chaque chip affiche l'icône emoji + le displayName ; la routine sélectionnée
 * apparaît en orange plein, les autres en surfaceVariant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineSelectionSection(
    routines: List<WorkoutRoutine>,
    selectedRoutineId: String,
    onRoutineSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Whatshot,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Type de séance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            routines.forEach { routine ->
                RoutineChip(
                    routine = routine,
                    isSelected = routine.id == selectedRoutineId,
                    onClick = { onRoutineSelected(routine.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineChip(
    routine: WorkoutRoutine,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, OrangeVibrant)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = if (isSelected) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                routine.icon,
                fontSize = 16.sp,
            )
            Text(
                routine.displayName,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DurationSelectionSection(
    selectedDuration: Int,
    onDurationSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Durée de la séance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DurationOption(
                duration = 60,
                exerciseCount = "6 exercices",
                isSelected = selectedDuration == 60,
                onClick = { onDurationSelected(60) },
                modifier = Modifier.weight(1f)
            )
            DurationOption(
                duration = 90,
                exerciseCount = "8 exercices",
                isSelected = selectedDuration == 90,
                onClick = { onDurationSelected(90) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DurationOption(
                duration = 120,
                exerciseCount = "10 exercices",
                isSelected = selectedDuration == 120,
                onClick = { onDurationSelected(120) },
                modifier = Modifier.weight(1f)
            )
            DurationOption(
                duration = 180,
                exerciseCount = "12 exercices",
                isSelected = selectedDuration == 180,
                onClick = { onDurationSelected(180) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DurationOption(
    duration: Int,
    exerciseCount: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                OrangeVibrant
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, OrangeVibrant)
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$duration min",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                exerciseCount,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    Color.White.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
            )
        }
    }
}

@Composable
fun LevelSelectionSection(
    selectedLevel: FitnessLevel,
    onLevelSelected: (FitnessLevel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Votre niveau",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FitnessLevel.values().forEach { level ->
                LevelOption(
                    level = level,
                    isSelected = selectedLevel == level,
                    onClick = { onLevelSelected(level) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelOption(
    level: FitnessLevel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levelName = when (level) {
        FitnessLevel.BEGINNER -> "Débutant"
        FitnessLevel.INTERMEDIATE -> "Inter\nmédiaire"
        FitnessLevel.ADVANCED -> "Avancé"
    }

    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                levelName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EquipmentSelectionSection(
    selectedEquipment: EquipmentType,
    onEquipmentSelected: (EquipmentType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Équipement disponible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EquipmentType.values().forEach { equipment ->
                EquipmentOption(
                    equipment = equipment,
                    isSelected = selectedEquipment == equipment,
                    onClick = { onEquipmentSelected(equipment) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentOption(
    equipment: EquipmentType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (name, description) = when (equipment) {
        EquipmentType.FULL_GYM -> "Salle complète" to "Machines + Haltères + Barres"
        EquipmentType.HOME_GYM -> "Home Gym" to "Haltères et Barres uniquement"
        EquipmentType.BODYWEIGHT -> "Poids du corps" to "Aucun équipement"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) OrangeVibrant.copy(alpha = 0.10f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, OrangeVibrant)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = OrangeVibrant)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) OrangeVibrant else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
