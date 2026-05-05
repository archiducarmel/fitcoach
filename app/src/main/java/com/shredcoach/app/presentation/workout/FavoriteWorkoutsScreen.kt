package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.format.DateTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class FavoriteWorkoutWithExercises(
    val workout: WorkoutEntity,
    val exercises: List<ExerciseEntity>
)

@HiltViewModel
class FavoriteWorkoutsViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {
    private val _favorites = MutableStateFlow<List<FavoriteWorkoutWithExercises>>(emptyList())
    val favorites: StateFlow<List<FavoriteWorkoutWithExercises>> = _favorites.asStateFlow()

    private val _launchedWorkoutLogId = MutableStateFlow<Long?>(null)
    val launchedWorkoutLogId: StateFlow<Long?> = _launchedWorkoutLogId.asStateFlow()

    init { loadFavorites() }

    fun refresh() { loadFavorites() }

    private fun loadFavorites() {
        viewModelScope.launch {
            workoutRepository.getFavoriteWorkouts().collect { workouts ->
                val withExercises = workouts.map { workout ->
                    val exerciseEntities = workoutRepository.getWorkoutExercises(workout.id)
                    val exercises = exerciseEntities.mapNotNull { we ->
                        workoutRepository.getExercisesForWorkoutId(we.exerciseId)
                    }
                    FavoriteWorkoutWithExercises(workout, exercises)
                }
                _favorites.value = withExercises
            }
        }
    }

    fun removeFavorite(workoutId: Long) {
        viewModelScope.launch { workoutRepository.setFavorite(workoutId, false) }
    }

    fun launchWorkout(workout: WorkoutEntity) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            val log = WorkoutLogEntity(
                workoutId = workout.id, date = now, startTime = now,
                durationMinutes = workout.durationMinutes, completed = false
            )
            val logId = workoutRepository.insertWorkoutLog(log)
            _launchedWorkoutLogId.value = logId
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteWorkoutsScreen(navController: NavController, viewModel: FavoriteWorkoutsViewModel = hiltViewModel()) {
    val favorites by viewModel.favorites.collectAsState()
    // Plus de lancement direct — on passe par la preview

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes séances favorites", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } }
            )
        }
    ) { pad ->
      com.shredcoach.app.presentation.common.PullToRefreshBox(
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.padding(pad)
      ) {
        if (favorites.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                com.shredcoach.app.presentation.common.EmptyState(
                    icon = Icons.Default.Favorite,
                    title = "Sauvegarde tes meilleures séances",
                    description = "Génère ou crée une séance, puis ajoute-la en favori avec le ❤️. Tu pourras la relancer en 1 clic à chaque fois.",
                    ctaLabel = "Générer une séance",
                    ctaIcon = Icons.Default.AutoAwesome,
                    onCtaClick = {
                        navController.navigate(com.shredcoach.app.presentation.navigation.Screen.WorkoutGenerator.route) {
                            popUpTo(com.shredcoach.app.presentation.navigation.Screen.FavoriteWorkouts.route) { inclusive = true }
                        }
                    }
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(favorites, key = { it.workout.id }) { fav ->
                    FavoriteWorkoutCard(
                        fav = fav,
                        onLaunch = {
                            navController.navigate(Screen.FavoritePreview.createRoute(fav.workout.id))
                        },
                        onRemoveFavorite = { viewModel.removeFavorite(fav.workout.id) }
                    )
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteWorkoutCard(fav: FavoriteWorkoutWithExercises, onLaunch: () -> Unit, onRemoveFavorite: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fav.workout.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${fav.workout.exerciseCount} exos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text("${fav.workout.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        if (fav.workout.isCustom) {
                            Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            Text("Custom", style = MaterialTheme.typography.labelSmall, color = OrangeVibrant, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Date d'ajout
                    Text("Ajouté le ${fav.workout.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                // Supprimer avec confirmation
                var showDeleteConfirm by remember { mutableStateOf(false) }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Retirer des favoris ?", fontWeight = FontWeight.Bold) },
                        text = { Text("\"${fav.workout.name}\" sera retirée de tes favoris.") },
                        confirmButton = {
                            Button(onClick = { onRemoveFavorite(); showDeleteConfirm = false },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Supprimer")
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } }
                    )
                }
            }

            // Preview exercices (collapsible)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                onClick = { expanded = !expanded }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${fav.exercises.size} exercices", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Toggle", Modifier.size(20.dp))
                    }
                    if (!expanded) {
                        // Aperçu compact
                        Text(
                            fav.exercises.take(4).joinToString(" • ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        // Liste complète
                        Spacer(Modifier.height(8.dp))
                        fav.exercises.forEachIndexed { i, exo ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    color = OrangeVibrant, modifier = Modifier.width(20.dp))
                                Text(exo.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(exo.variant.color).copy(alpha = 0.15f)) {
                                    Text(exo.variant.displayName, Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Color(exo.variant.color))
                                }
                            }
                        }
                    }
                }
            }

            // Bouton LANCER
            Button(
                onClick = onLaunch,
                Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("LANCER CETTE SÉANCE", fontWeight = FontWeight.Bold)
            }
        }
    }
}
