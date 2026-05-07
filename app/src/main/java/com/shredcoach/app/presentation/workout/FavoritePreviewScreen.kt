package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.presentation.common.EmptyState
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritePreviewScreen(
    navController: NavController,
    viewModel: FavoritePreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Navigation vers la séance
    LaunchedEffect(state.launchedLogId) {
        val id = state.launchedLogId
        if (id != null && id > 0) {
            navController.navigate(Screen.WorkoutSession.createRoute(id)) {
                popUpTo(Screen.FavoriteWorkouts.route)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.favorite_preview_title), fontWeight = FontWeight.Bold)
                        state.workout?.let {
                            Text(it.name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp) {
                Button(
                    onClick = { viewModel.launchSession() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(16.dp),
                    enabled = state.exercises.isNotEmpty()
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.favorite_preview_launch), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { pad ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator()
            }
            state.exercises.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad)) {
                EmptyState(
                    icon = Icons.Default.FitnessCenter,
                    title = stringResource(R.string.favorite_preview_empty_title),
                    description = stringResource(R.string.favorite_preview_empty_desc)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            stringResource(R.string.favorite_preview_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    itemsIndexed(state.exercises) { index, exercise ->
                        ExercisePreviewCard(
                            exercise = exercise,
                            orderNumber = index + 1,
                            effectiveSeries = viewModel.resolvedSeries(exercise),
                            effectiveRepsMin = viewModel.resolvedRepsMin(exercise),
                            effectiveRepsMax = viewModel.resolvedRepsMax(exercise),
                            effectiveRest = viewModel.resolvedRest(exercise),
                            onSeriesChange = { v -> viewModel.updateOverride(exercise.id) { it.copy(series = v) } },
                            onRepsMinChange = { v -> viewModel.updateOverride(exercise.id) { it.copy(repsMin = v) } },
                            onRepsMaxChange = { v -> viewModel.updateOverride(exercise.id) { it.copy(repsMax = v) } },
                            onRestChange = { v -> viewModel.updateOverride(exercise.id) { it.copy(restSeconds = v) } }
                        )
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}
