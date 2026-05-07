package com.shredcoach.app.presentation.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.presentation.common.sharedBoundsOptIn
import com.shredcoach.app.presentation.common.sharedElementOptIn
import com.shredcoach.app.presentation.theme.OrangeVibrant
import com.shredcoach.app.presentation.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    navController: NavController,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val exercise by viewModel.exercise.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: stringResource(R.string.exercise_detail_title_default), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { pad ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            exercise == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.exercise_detail_not_found), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.navigateUp() }) { Text(stringResource(R.string.common_back)) }
                }
            }
            else -> ExerciseDetailContent(exercise!!, Modifier.fillMaxSize().padding(pad))
        }
    }
}

@Composable
private fun ExerciseDetailContent(exercise: ExerciseEntity, modifier: Modifier) {
    val context = LocalContext.current
    val variantColor = Color(exercise.variant.color)

    Column(modifier.verticalScroll(rememberScrollState())) {
        // ── GIF animé (plein écran en haut) ──
        // Partage l'image avec la card de la liste via shared element transition.
        Box(
            Modifier
                .sharedElementOptIn(key = "exercise-image-${exercise.id}")
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (exercise.gifUrl != null) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(exercise.gifUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = exercise.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                    },
                    error = { GifPlaceholder() }
                )
            } else {
                GifPlaceholder()
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Header : Nom + badges ──
            Text(
                exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.sharedBoundsOptIn(key = "exercise-name-${exercise.id}")
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(exercise.muscleGroup.displayNameRes), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = variantColor.copy(alpha = 0.2f)) {
                    Text(stringResource(exercise.variant.displayNameRes), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = variantColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val diffLabel = when (exercise.difficulty) {
                    1 -> stringResource(R.string.exercise_difficulty_beginner)
                    2 -> stringResource(R.string.exercise_difficulty_intermediate)
                    else -> stringResource(R.string.exercise_difficulty_advanced)
                }
                val diffColor = when (exercise.difficulty) { 1 -> NeonGreen; 2 -> OrangeVibrant; else -> Color(0xFFEF4444) }
                Surface(shape = RoundedCornerShape(8.dp), color = diffColor.copy(alpha = 0.15f)) {
                    Text(diffLabel, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = diffColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // ── Card paramètres ──
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.exercise_detail_section_programming), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        ParamStat(Icons.Default.RepeatOne, "${exercise.series}", stringResource(R.string.exercises_stat_series))
                        ParamStat(Icons.Default.FitnessCenter, "${exercise.repsMin}-${exercise.repsMax}", stringResource(R.string.exercises_stat_reps))
                        ParamStat(Icons.Default.Timer, "${exercise.restSeconds}s", stringResource(R.string.exercises_stat_rest))
                    }

                    HorizontalDivider()

                    ParamRow(Icons.Default.MonitorWeight, stringResource(R.string.exercise_detail_param_weight), exercise.startingWeight)
                    if (exercise.tempo != "N/A") ParamRow(Icons.Default.Speed, stringResource(R.string.exercise_detail_param_tempo), exercise.tempo)
                    ParamRow(Icons.Default.Build, stringResource(R.string.exercise_detail_param_equipment), exercise.equipment)
                }
            }

            // ── Card exécution ──
            if (exercise.executionKey.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Checklist, null, tint = OrangeVibrant)
                            Text(stringResource(R.string.exercise_detail_section_execution), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                        }
                        // Diviser en étapes numérotées
                        exercise.executionKey.split(". ").filter { it.isNotBlank() }.forEachIndexed { i, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Surface(shape = RoundedCornerShape(4.dp), color = OrangeVibrant.copy(alpha = 0.15f), modifier = Modifier.size(24.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                                    }
                                }
                                Text(step.trim().removeSuffix("."), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            // ── Card conseils ──
            if (exercise.tips.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lightbulb, null, tint = NeonGreen)
                            Text(stringResource(R.string.exercise_detail_section_tips), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NeonGreen)
                        }
                        Text(exercise.tips, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                }
            }

            // ── Card variante ──
            Card(colors = CardDefaults.cardColors(containerColor = variantColor.copy(alpha = 0.08f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, tint = variantColor)
                        Text(stringResource(R.string.exercise_detail_variant_type_label, stringResource(exercise.variant.displayNameRes)),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = variantColor)
                    }
                    Text(stringResource(exercise.variant.descriptionRes), style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GifPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FitnessCenter, null, Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.exercise_detail_gif_placeholder), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ParamStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(28.dp), tint = OrangeVibrant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OrangeVibrant)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun ParamRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}
