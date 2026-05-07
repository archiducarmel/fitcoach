package com.shredcoach.app.presentation.exercises

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.exercise.rememberLocalizedExercise
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.presentation.common.ShimmerBox
import com.shredcoach.app.presentation.common.ShimmerText
import com.shredcoach.app.presentation.common.sharedBoundsOptIn
import com.shredcoach.app.presentation.common.sharedElementOptIn
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    navController: NavController,
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val exercises by viewModel.filteredExercises.collectAsState()
    val selectedMuscleGroup by viewModel.selectedMuscleGroup.collectAsState()
    val selectedVariant by viewModel.selectedVariant.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxHeight().offset(y = (-6).dp),
                        verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.exercises_title), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        Text(pluralStringResource(R.plurals.exercises_count, exercises.size, exercises.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
      com.shredcoach.app.presentation.common.PullToRefreshBox(
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.padding(paddingValues)
      ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Search bar
            // Recherche + bouton clear filtres sur meme ligne
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQuery(it) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    placeholder = { Text(stringResource(R.string.exercises_search_hint), style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQuery("") }) {
                                Icon(Icons.Default.Close, stringResource(R.string.exercises_search_clear_cd), Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                if (selectedMuscleGroup != null || selectedVariant != null) {
                    IconButton(onClick = { viewModel.clearFilters() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.FilterListOff, stringResource(R.string.exercises_filter_reset_cd), Modifier.size(20.dp), tint = OrangeVibrant)
                    }
                }
            }

            // Filtres combines : muscle groups + variants sur une seule LazyRow scrollable
            MuscleGroupFilters(
                selectedMuscleGroup = selectedMuscleGroup,
                onMuscleGroupSelected = { viewModel.selectMuscleGroup(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
            VariantFilters(
                selectedVariant = selectedVariant,
                onVariantSelected = { viewModel.selectVariant(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).padding(bottom = 6.dp)
            )

            // Exercise List
            if (isLoading) {
                // Shimmer skeleton — anticipe la forme des ExerciseCard pour
                // créer une perception de "déjà là" plutôt qu'un vide tournant.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(6) { ExerciseCardSkeleton() }
                }
            } else if (exercises.isEmpty()) {
                com.shredcoach.app.presentation.common.EmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.exercises_empty_title),
                    description = stringResource(R.string.exercises_empty_desc),
                    ctaLabel = stringResource(R.string.exercises_empty_cta),
                    ctaIcon = Icons.Default.FilterListOff,
                    onCtaClick = { viewModel.clearFilters() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "db_explorer_cta") {
                        ExerciseDbExplorerCta(
                            onClick = {
                                android.util.Log.i("ExoDB-NAV", "Click CTA → navigate vers ${Screen.ExerciseDbExplorer.route}")
                                navController.navigate(Screen.ExerciseDbExplorer.route)
                            }
                        )
                    }
                    item(key = "gym_scan_cta") {
                        GymScanCta(
                            onClick = { navController.navigate(Screen.GymScan.route) }
                        )
                    }
                    items(exercises) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            onClick = {
                                navController.navigate(Screen.ExerciseDetail.createRoute(exercise.id))
                            }
                        )
                    }
                }
            }
        }
      }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupFilters(
    selectedMuscleGroup: MuscleGroup?,
    onMuscleGroupSelected: (MuscleGroup?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(MuscleGroup.values().toList()) { group ->
            FilterChip(
                selected = selectedMuscleGroup == group,
                onClick = { onMuscleGroupSelected(if (selectedMuscleGroup == group) null else group) },
                label = { Text(stringResource(group.displayNameRes), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantFilters(
    selectedVariant: ExerciseVariant?,
    onVariantSelected: (ExerciseVariant?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(ExerciseVariant.values().toList()) { variant ->
            FilterChip(
                selected = selectedVariant == variant,
                onClick = { onVariantSelected(if (selectedVariant == variant) null else variant) },
                label = { Text(stringResource(variant.displayNameRes), style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = androidx.compose.ui.graphics.Color(variant.color)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCard(
    exercise: ExerciseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val variantColor = androidx.compose.ui.graphics.Color(exercise.variant.color)
    val localized = rememberLocalizedExercise(exercise)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail GIF — partagé avec ExerciseDetailScreen via shared element
            Box(
                modifier = Modifier
                    .sharedElementOptIn(key = "exercise-image-${exercise.id}")
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (exercise.gifUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(exercise.gifUrl)
                            .size(Size(128, 128))
                            .crossfade(true)
                            .build(),
                        contentDescription = localized.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        },
                        error = {
                            Icon(Icons.Default.FitnessCenter, null, Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    )
                } else {
                    Icon(Icons.Default.FitnessCenter, null, Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                }
            }

            // Infos
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(localized.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .sharedBoundsOptIn(key = "exercise-name-${exercise.id}")
                            .weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = variantColor.copy(alpha = 0.2f)) {
                        Text(stringResource(exercise.variant.displayNameRes), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = variantColor)
                    }
                }
                Text(stringResource(exercise.muscleGroup.displayNameRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExerciseStat(label = stringResource(R.string.exercises_stat_series), value = "${exercise.series}")
                    ExerciseStat(label = stringResource(R.string.exercises_stat_reps), value = "${exercise.repsMin}-${exercise.repsMax}")
                    ExerciseStat(label = stringResource(R.string.exercises_stat_rest), value = "${exercise.restSeconds}s")
                }
            }
        }
    }
}

/**
 * Skeleton placeholder qui mime la forme d'une [ExerciseCard].
 * À utiliser pendant le loading initial — donne une lecture immédiate de
 * "ce qui va arriver" plutôt qu'un spinner indéterminé.
 */
@Composable
private fun ExerciseCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp)
            )
            // Spacing 4dp + tag variant à droite du nom : on mime exactement
            // la disposition d'ExerciseCard pour zéro saut visuel au moment
            // où la donnée arrive et remplace les skeletons.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerText(width = 140.dp, height = 16.dp)
                    ShimmerBox(
                        modifier = Modifier.size(width = 48.dp, height = 16.dp),
                        shape = RoundedCornerShape(6.dp)
                    )
                }
                ShimmerText(width = 100.dp, height = 12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { ShimmerText(width = 40.dp, height = 12.dp) }
                }
            }
        }
    }
}

@Composable
fun ExerciseStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ═══════════════════════════════════════
// CTA : découvrir la bibliothèque ExerciseDB
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDbExplorerCta(onClick: () -> Unit) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "cta_shimmer")
    val shimmer by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                        OrangeVibrant,
                        androidx.compose.ui.graphics.Color(0xFFE91E63),
                        androidx.compose.ui.graphics.Color(0xFF8B5CF6)
                    ))
                )
        ) {
            // Reflet shimmer animé
            Box(
                Modifier.matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
                                androidx.compose.ui.graphics.Color.Transparent
                            ),
                            start = androidx.compose.ui.geometry.Offset(shimmer * 600f - 200f, 0f),
                            end = androidx.compose.ui.geometry.Offset(shimmer * 600f + 200f, 300f)
                        )
                    )
            )
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FitnessCenter, null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.exercises_db_cta_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = androidx.compose.ui.graphics.Color.White)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)
                        ) {
                            Text(stringResource(R.string.exercises_db_cta_badge),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 9.sp)
                        }
                    }
                    Text(stringResource(R.string.exercises_db_cta_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White)
                    Text(stringResource(R.string.exercises_db_cta_caption),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// CTA : GymScan (IA + base d'exercices)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GymScanCta(onClick: () -> Unit) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "gym_scan_glow")
    val glow by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                        androidx.compose.ui.graphics.Color(0xFF3B82F6),
                        androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                        androidx.compose.ui.graphics.Color(0xFFEC4899).copy(alpha = glow)
                    ))
                )
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.exercises_gymscan_cta_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = androidx.compose.ui.graphics.Color.White)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)
                        ) {
                            Text(stringResource(R.string.exercises_gymscan_cta_badge),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 9.sp)
                        }
                    }
                    Text(stringResource(R.string.exercises_gymscan_cta_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.Color.White)
                    Text(stringResource(R.string.exercises_gymscan_cta_caption),
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
