package com.shredcoach.app.presentation.explorer

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shredcoach.app.data.remote.ExerciseDbExercise
import com.shredcoach.app.data.remote.ExerciseDbMeta
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDbExplorerScreen(
    navController: NavController,
    viewModel: ExerciseDbExplorerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        Log.i("ExoDB-UI", "★ Composition démarrée")
    }
    LaunchedEffect(state.isLoading, state.exercises.size, state.error, state.totalInDataset) {
        Log.i("ExoDB-UI", "STATE → isLoading=${state.isLoading} | exos=${state.exercises.size} | total=${state.totalInDataset} | error=${state.error?.take(80)}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Découvrir", fontWeight = FontWeight.Bold)
                        val subtitle = when {
                            state.isLoading -> "Chargement…"
                            state.totalInDataset > 0 -> "${state.exercises.size} / ${state.totalInDataset} exercices"
                            else -> "Free Exercise DB"
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    if (state.searchQuery.isNotBlank() || state.selectedMuscle != null
                        || state.selectedEquipment != null || state.selectedCategory != null
                        || state.selectedLevel != null) {
                        TextButton(onClick = { viewModel.clearAllFilters() }) {
                            Text("Reset", color = OrangeVibrant, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Recharger")
                    }
                }
            )
        }
    ) { padding ->
        // ── LazyVerticalGrid unique : hero + recherche + filtres en headers span-full,
        //    puis cartes exos dans la grille adaptive. Tout scrolle ensemble. ──
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // ── Bandeau diagnostic (always on top, visible pour debug) ──
            item(span = { GridItemSpan(maxLineSpan) }, key = "diag") {
                DiagnosticBanner(state)
            }
            // ── Hero ──
            item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
                HeroBanner(state.totalInDataset)
            }
            // ── Recherche ──
            item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchChanged,
                    placeholder = { Text("Rechercher (squat, bench, curl…)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.onSearchChanged("") }) {
                                Icon(Icons.Default.Close, "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
            // ── Filtres ──
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                FilterSection(
                    meta = state.meta,
                    selectedMuscle = state.selectedMuscle,
                    selectedEquipment = state.selectedEquipment,
                    selectedCategory = state.selectedCategory,
                    selectedLevel = state.selectedLevel,
                    onMuscle = viewModel::selectMuscle,
                    onEquipment = viewModel::selectEquipment,
                    onCategory = viewModel::selectCategory,
                    onLevel = viewModel::selectLevel
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "divider") {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }

            // ── État principal : skeleton / error / empty / grid ──
            when {
                state.isLoading && state.exercises.isEmpty() -> {
                    items(8, key = { "skel_$it" }, span = { GridItemSpan(1) }) {
                        SkeletonCard()
                    }
                }
                state.error != null && state.exercises.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "err") {
                        ErrorView(state.error!!) { viewModel.refresh() }
                    }
                }
                state.exercises.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyView()
                    }
                }
                else -> {
                    itemsIndexed(
                        state.exercises,
                        key = { _, ex -> ex.id }
                    ) { _, ex ->
                        ExerciseGridCard(ex) {
                            navController.navigate(Screen.ExerciseDbDetail.createRoute(ex.id))
                        }
                    }
                    if (state.exercises.size >= 12) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "end_marker") {
                            Text(
                                "✦ ${state.exercises.size} résultats affichés",
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }

            // Padding latéral géré par les cards via padding horizontal
            item(span = { GridItemSpan(maxLineSpan) }, key = "bottom_spacer") {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ═══════════════════════════════════════
// BANDEAU DIAGNOSTIC
// ═══════════════════════════════════════

@Composable
private fun DiagnosticBanner(state: ExerciseDbExplorerState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            state.error != null -> MaterialTheme.colorScheme.errorContainer
            state.isLoading -> Color(0xFFFEF3C7)
            state.exercises.isNotEmpty() -> Color(0xFFD1FAE5)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = when {
                    state.error != null -> "⚠ ERREUR"
                    state.isLoading -> "⏳ Chargement…"
                    state.exercises.isNotEmpty() -> "✓ OK · ${state.exercises.size}/${state.totalInDataset} exos"
                    else -> "○ Vide"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold
            )
            state.error?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ═══════════════════════════════════════
// HERO BANNER
// ═══════════════════════════════════════

@Composable
private fun HeroBanner(totalCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(
                        OrangeVibrant.copy(alpha = 0.95f),
                        Color(0xFFE91E63).copy(alpha = 0.85f)
                    ))
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Bibliothèque Free Exercise DB", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(
                        if (totalCount > 0) "$totalCount exercices · photos HD"
                        else "Chargement de la bibliothèque…",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.18f)) {
                    Text("FREE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// FILTRES
// ═══════════════════════════════════════

@Composable
private fun FilterSection(
    meta: ExerciseDbMeta,
    selectedMuscle: String?,
    selectedEquipment: String?,
    selectedCategory: String?,
    selectedLevel: String?,
    onMuscle: (String?) -> Unit,
    onEquipment: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onLevel: (String?) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FilterChipsRow(
            label = "Muscle ciblé",
            icon = Icons.Default.SelfImprovement,
            options = meta.muscles,
            selected = selectedMuscle,
            onSelect = onMuscle,
            chipColor = NeonGreen,
            displayFn = ExerciseDbTranslations::displayMuscle
        )
        FilterChipsRow(
            label = "Équipement",
            icon = Icons.Default.SportsGymnastics,
            options = meta.equipments,
            selected = selectedEquipment,
            onSelect = onEquipment,
            chipColor = OrangeVibrant,
            displayFn = ExerciseDbTranslations::displayEquipment
        )
        FilterChipsRow(
            label = "Catégorie",
            icon = Icons.Default.Category,
            options = meta.categories,
            selected = selectedCategory,
            onSelect = onCategory,
            chipColor = Color(0xFF3B82F6),
            displayFn = ExerciseDbTranslations::displayCategory
        )
        FilterChipsRow(
            label = "Niveau",
            icon = Icons.Default.Star,
            options = meta.levels,
            selected = selectedLevel,
            onSelect = onLevel,
            chipColor = Color(0xFF8B5CF6),
            displayFn = ExerciseDbTranslations::displayLevel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    chipColor: Color,
    /** Fonction de traduction de chaque option pour l'AFFICHAGE (la valeur stockée reste l'originale anglaise). */
    displayFn: (String) -> String = { it.replaceFirstChar { c -> c.uppercase() } }
) {
    if (options.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = chipColor)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            if (selected != null) {
                Surface(shape = RoundedCornerShape(6.dp), color = chipColor.copy(alpha = 0.15f)) {
                    Text(displayFn(selected), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = chipColor, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onSelect(null) }) {
                    Icon(Icons.Default.Close, "Retirer le filtre $label", modifier = Modifier.size(12.dp), tint = chipColor)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(options) { opt ->
                val isSel = selected.equals(opt, ignoreCase = true)
                FilterChip(
                    selected = isSel,
                    onClick = { onSelect(if (isSel) null else opt) },
                    label = { Text(displayFn(opt), fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.18f),
                        selectedLabelColor = chipColor,
                        selectedLeadingIconColor = chipColor
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// CARTE D'EXERCICE (grille)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseGridCard(ex: ExerciseDbExercise, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Thumbnail STATIQUE (pas d'animation) — l'animation est réservée à la page détail
                if (ex.firstImageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ex.firstImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = ex.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp), color = OrangeVibrant)
                            }
                        },
                        error = {
                            Icon(Icons.Default.BrokenImage, null, modifier = Modifier.size(40.dp).align(Alignment.Center),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                } else {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(40.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                if (ex.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(ExerciseDbTranslations.displayCategory(ex.category),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    }
                }
                if (ex.level.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(bottomStart = 10.dp),
                        color = levelColor(ex.level).copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(ExerciseDbTranslations.displayLevel(ex.level),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                )
                if (ex.primaryMuscle.isNotBlank()) {
                    Text(
                        "🎯 ${ExerciseDbTranslations.displayMuscle(ex.primaryMuscle)}",
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp
                    )
                }
            }
            // Bloc texte à hauteur FIXE pour uniformité des cards sur une même ligne de grille.
            // Structure reservée : titre 2 lignes (minLines=2) + row équipement (toujours présente).
            Column(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    ExerciseDbTranslations.translateExerciseName(ex.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                // Row équipement toujours rendue (avec contenu ou vide) pour garantir hauteur constante
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 13.dp)
                ) {
                    if (!ex.equipment.isNullOrBlank()) {
                        Icon(Icons.Default.SportsGymnastics, null, modifier = Modifier.size(11.dp),
                            tint = OrangeVibrant)
                        Text(
                            ExerciseDbTranslations.displayEquipment(ex.equipment),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Composable qui simule un GIF en alternant entre 2 photos avec crossfade.
 * Si une seule photo est dispo, l'affiche statiquement.
 * Animation pause automatique quand le composable n'est plus visible.
 */
@Composable
internal fun AnimatedExerciseImage(
    firstUrl: String,
    secondUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    frameDurationMs: Long = 700L
) {
    val ctx = LocalContext.current
    val hasTwoFrames = firstUrl.isNotBlank() && secondUrl.isNotBlank()

    // Index courant (0 ou 1) — alterne via LaunchedEffect
    var frameIndex by remember(firstUrl, secondUrl) { mutableStateOf(0) }

    if (hasTwoFrames) {
        LaunchedEffect(firstUrl, secondUrl) {
            while (true) {
                kotlinx.coroutines.delay(frameDurationMs)
                frameIndex = 1 - frameIndex
            }
        }
    }

    val currentUrl = when {
        !hasTwoFrames -> firstUrl.ifBlank { secondUrl }
        frameIndex == 0 -> firstUrl
        else -> secondUrl
    }

    Box(modifier) {
        if (currentUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(currentUrl)
                    .crossfade(300) // crossfade smooth entre les frames → effet GIF naturel
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp), color = OrangeVibrant)
                    }
                },
                error = {
                    Icon(Icons.Default.BrokenImage, null, modifier = Modifier.size(40.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            )
            // Petit indicateur "play" en haut-droite si animé
            if (hasTwoFrames) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null,
                            modifier = Modifier.size(10.dp), tint = Color.White)
                        Text("ANIM", color = Color.White, fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        } else {
            Icon(Icons.Default.Image, null, modifier = Modifier.size(40.dp).align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

internal fun levelColor(level: String): Color = when (level.lowercase()) {
    "beginner" -> Color(0xFF10B981)
    "intermediate" -> Color(0xFFF59E0B)
    "expert" -> Color(0xFFEF4444)
    else -> Color(0xFF64748B)
}

// ═══════════════════════════════════════
// ÉTATS : SKELETON / EMPTY / ERROR
// ═══════════════════════════════════════

@Composable
private fun SkeletonCard() {
    val infinite = rememberInfiniteTransition(label = "skel")
    val alpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
    )
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            Box(Modifier.fillMaxWidth().height(40.dp).padding(10.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            }
        }
    }
}

@Composable
private fun EmptyView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        Text("Aucun exercice trouvé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Ajuste tes filtres ou ta recherche",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text("Téléchargement impossible",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Réessayer")
        }
        Spacer(Modifier.height(12.dp))
        Text("Vérifie ta connexion internet — la bibliothèque est servie depuis GitHub",
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
    }
}
