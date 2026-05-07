package com.shredcoach.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.domain.workout.RoutineCatalog
import kotlinx.coroutines.launch
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    navController: NavController,
    viewModel: WorkoutHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Sport, 1 = Nutrition

    // Filtrage sport
    val filteredItems = remember(state.items, state.filter, state.routineFilter) {
        val byStatus = when (state.filter) {
            HistoryFilter.ALL -> state.items
            HistoryFilter.COMPLETED -> state.items.filter { it.log.completed }
            HistoryFilter.ABANDONED -> state.items.filter { !it.log.completed }
        }
        val routine = state.routineFilter
        if (routine == null) byStatus else byStatus.filter { it.log.routineId == routine }
    }
    // Routines présentes dans l'historique — sert à n'afficher dans le filter
    // bar que les routines qu'on a effectivement croisées (évite le bruit
    // de chips vides si l'user n'a jamais fait de Pull).
    val availableRoutineIds = remember(state.items) {
        state.items.map { it.log.routineId }.distinct()
    }
    val grouped = groupByBucketComposable(filteredItems)

    // Scans nutrition
    val mealScans by remember { viewModel.mealScans }.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showShareHistory by remember { mutableStateOf(false) }
    var showExportHistory by remember { mutableStateOf(false) }

    if (showShareHistory) {
        com.shredcoach.app.presentation.share.ShareSheet(
            data = if (selectedTab == 0) buildWorkoutHistoryShareData(filteredItems, context)
            else buildNutritionHistoryShareData(mealScans, context),
            onDismiss = { showShareHistory = false },
        )
    }
    if (showExportHistory) {
        val exportTitle = if (selectedTab == 0) stringResource(R.string.history_export_workouts_title)
            else stringResource(R.string.history_export_nutrition_title)
        com.shredcoach.app.presentation.share.ExportSheet(
            title = exportTitle,
            onPick = { format ->
                showExportHistory = false
                scope.launch {
                    val payload = if (selectedTab == 0) buildWorkoutHistoryExportPayload(filteredItems, context)
                    else buildNutritionHistoryExportPayload(mealScans, context)
                    val content = com.shredcoach.app.presentation.share.DataExporter.render(payload, format)
                    val uri = com.shredcoach.app.presentation.share.DataExporter.saveToCache(
                        context, content, format,
                        baseFilename = if (selectedTab == 0) "shredcoach_historique_seances" else "shredcoach_historique_nutrition",
                    )
                    com.shredcoach.app.presentation.share.DataExporter.launchShareIntent(
                        context, uri, format, subject = payload.title,
                    )
                }
            },
            onDismiss = { showExportHistory = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showShareHistory = true }) {
                        Icon(Icons.Default.Share, stringResource(R.string.history_share_cd))
                    }
                    IconButton(onClick = { showExportHistory = true }) {
                        Icon(Icons.Default.FileDownload, stringResource(R.string.history_export_cd))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->

        Column(Modifier.fillMaxSize().padding(pad)) {
            // ─── Tab selector premium ───
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(0, stringResource(R.string.history_tab_workouts), Icons.Default.FitnessCenter),
                    Triple(1, stringResource(R.string.history_tab_nutrition), Icons.Default.Restaurant)
                ).forEach { (idx, label, icon) ->
                    val selected = selectedTab == idx
                    Surface(
                        onClick = { selectedTab = idx },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        tonalElevation = if (selected) 2.dp else 0.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, Modifier.size(18.dp),
                                tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ─── Contenu selon tab ───
            if (selectedTab == 1) {
                // NUTRITION HISTORY
                NutritionHistoryContent(mealScans, navController, viewModel)
            } else {
            // SPORT HISTORY (contenu existant)
        com.shredcoach.app.presentation.common.PullToRefreshBox(
            onRefresh = { viewModel.refresh() }
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    com.shredcoach.app.presentation.common.EmptyState(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.history_workouts_empty_title),
                        description = stringResource(R.string.history_workouts_empty_desc),
                        ctaLabel = stringResource(R.string.history_workouts_empty_cta),
                        ctaIcon = Icons.Default.AutoAwesome,
                        onCtaClick = { navController.navigate(Screen.WorkoutGenerator.route) }
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ═══ Summary hero ═══
                    item { HistorySummaryHero(state) }

                    // ═══ Filter chips ═══
                    item { FilterChipsRow(state.filter, onSelect = viewModel::setFilter) }
                    // ═══ Routine filter (apparait seulement si > 1 routine en historique) ═══
                    if (availableRoutineIds.size > 1) {
                        item {
                            RoutineFilterChipsRow(
                                availableRoutineIds = availableRoutineIds,
                                current = state.routineFilter,
                                onSelect = viewModel::setRoutineFilter,
                            )
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.history_no_match), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 32.dp).fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // ═══ Liste groupée ═══
                    grouped.forEach { (bucket, entries) ->
                        item {
                            Text(
                                bucket, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(entries, key = { it.log.id }) { entry ->
                            HistoryCard(
                                entry = entry,
                                onClick = {
                                    navController.navigate(Screen.WorkoutHistoryDetail.createRoute(entry.log.id))
                                }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
            } // fin else (sport tab)
        } // fin Column
    }
}

// ═══════════════════════════════════════
// NUTRITION HISTORY
// ═══════════════════════════════════════

@Composable
private fun NutritionHistoryContent(
    scans: List<com.shredcoach.app.data.local.entity.MealScanEntity>,
    navController: NavController,
    viewModel: WorkoutHistoryViewModel
) {
    if (scans.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            com.shredcoach.app.presentation.common.EmptyState(
                icon = Icons.Default.Restaurant,
                title = stringResource(R.string.history_nutrition_empty_title),
                description = stringResource(R.string.history_nutrition_empty_desc),
                ctaLabel = stringResource(R.string.history_nutrition_empty_cta),
                ctaIcon = Icons.Default.CameraAlt,
                onCtaClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.MealScanner.route) }
            )
        }
    } else {
        // Grouper par date
        val grouped = scans.groupBy { it.timestamp.toLocalDate() }
            .entries.sortedByDescending { it.key }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            grouped.forEach { (date, dayScans) ->
                val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
                    .replaceFirstChar { it.uppercase() }
                val dayCalories = dayScans.sumOf { it.totalCalories }

                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dateStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                            Text(stringResource(R.string.history_meal_calories, dayCalories), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                        }
                    }
                }

                dayScans.forEach { scan ->
                    item(key = "meal_${scan.id}") {
                        var showDeleteConfirm by remember { mutableStateOf(false) }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text(stringResource(R.string.history_meal_delete_dialog_title), fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.history_meal_delete_dialog_body, scan.dishName)) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.shredcoach.app.presentation.util.hapticHeavy(navController.context)
                                            viewModel.deleteMealScan(scan)
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text(stringResource(R.string.common_delete)) }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
                            )
                        }

                        MealHistoryCard(
                            scan = scan,
                            onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.MealScanDetail.createRoute(scan.id)) },
                            onDelete = {
                                com.shredcoach.app.presentation.util.hapticClick(navController.context)
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealHistoryCard(scan: com.shredcoach.app.data.local.entity.MealScanEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val timeStr = scan.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val scoreColor = when { scan.healthScore >= 8 -> NeonGreen; scan.healthScore >= 5 -> OrangeVibrant; else -> MaterialTheme.colorScheme.error }
    val mealLabel = com.shredcoach.app.domain.nutrition.MealTypeClassifier
        .fromId(scan.mealType).displayName

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ─── Header : score + nom (multi-lignes) + tags ───
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Photo thumbnail ou score fallback
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                    if (scan.photoPath != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(
                                androidx.compose.ui.platform.LocalContext.current
                            ).data(java.io.File(scan.photoPath)).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                        // Score en overlay
                        Surface(
                            shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 12.dp),
                            color = scoreColor, modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text("${scan.healthScore}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 10.sp)
                        }
                    } else {
                        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp), color = scoreColor.copy(alpha = 0.12f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${scan.healthScore}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = scoreColor)
                            }
                        }
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Nom du plat — PAS de troncature (maxLines = 3)
                    Text(scan.dishName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3)

                    // Tags sur une ligne : type repas + heure + cuisine
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                            Text(mealLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                        }
                        Text(timeStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    if (scan.cuisine.isNotBlank()) {
                        Text(scan.cuisine, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    // Pictogramme Nutri-Score
                    val nutriGrade = scan.nutriScoreGrade.firstOrNull()
                        ?: com.shredcoach.app.domain.nutrition.NutriScoreCalculator.fromTotals(
                            calories = scan.totalCalories,
                            sugars = scan.totalCarbs * 0.3,
                            saturatedFat = scan.totalFats * 0.35,
                            saltG = 1.5,
                            fibers = scan.totalFibers,
                            proteins = scan.totalProteins,
                            weightG = scan.totalWeight
                        ).grade
                    com.shredcoach.app.domain.nutrition.NutriScorePictogram(nutriGrade, height = 22.dp)
                }
            }

            // ─── Macros — noms complets + barres proportionnelles ───
            val totalMacroG = (scan.totalProteins + scan.totalCarbs + scan.totalFats + scan.totalFibers).coerceAtLeast(1.0)
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_meal_calories_card, scan.totalCalories), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = OrangeVibrant)
                    Text(stringResource(R.string.history_meal_weight, scan.totalWeight), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // 4 barres macros noms complets
                HistoryMacroBar(stringResource(R.string.history_meal_macro_proteins), scan.totalProteins, Color(0xFF3B82F6), totalMacroG)
                HistoryMacroBar(stringResource(R.string.history_meal_macro_carbs), scan.totalCarbs, OrangeVibrant, totalMacroG)
                HistoryMacroBar(stringResource(R.string.history_meal_macro_fats), scan.totalFats, Color(0xFFEF4444), totalMacroG)
                HistoryMacroBar(stringResource(R.string.history_meal_macro_fibers), scan.totalFibers, NeonGreen, totalMacroG)
            }

            // ─── Verdict ───
            if (scan.verdict.isNotBlank()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp), tint = OrangeVibrant.copy(alpha = 0.6f))
                    Text(scan.verdict, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }

            // ─── Footer : voir détails + supprimer ───
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Supprimer
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.history_meal_delete_cd), Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
                // Voir détails
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_meal_full_analysis), style = MaterialTheme.typography.labelSmall,
                        color = OrangeVibrant, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp), tint = OrangeVibrant)
                }
            }
        }
    }
}

@Composable
private fun HistoryMacroBar(label: String, grams: Double, color: Color, totalG: Double) {
    val fraction = (grams / totalG).toFloat().coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(62.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(stringResource(R.string.history_meal_grams, grams), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// ═══════════════════════════════════════
// HERO SUMMARY
// ═══════════════════════════════════════
@Composable
private fun HistorySummaryHero(state: WorkoutHistoryState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            OrangeVibrant.copy(alpha = 0.95f),
                            OrangeVibrant.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.history_hero_title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat("${state.totalWorkouts}", stringResource(R.string.history_hero_label_seances), Modifier.weight(1f))
                    VerticalDividerLight()
                    SummaryStat(formatVolume(state.totalVolumeKg), stringResource(R.string.history_hero_label_volume), Modifier.weight(1f))
                    VerticalDividerLight()
                    SummaryStat(formatDurationShort(state.totalDurationMinutes), stringResource(R.string.history_hero_label_time), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VerticalDividerLight() {
    Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.25f)))
}

// ═══════════════════════════════════════
// FILTER CHIPS
// ═══════════════════════════════════════
@Composable
private fun FilterChipsRow(current: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.values().forEach { filter ->
            val selected = filter == current
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(filter) },
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(filter.displayNameRes),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Filtre par routine — chips horizontaux. Affiche "Toutes" + une chip par
 * routine présente dans l'historique. Pas affiché s'il n'y a qu'une seule
 * routine (sinon c'est du bruit visuel pour 0 valeur).
 */
@Composable
private fun RoutineFilterChipsRow(
    availableRoutineIds: List<String>,
    current: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "Toutes" reset
        val allSelected = current == null
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(null) },
            color = if (allSelected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                stringResource(R.string.history_routine_filter_all),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (allSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        availableRoutineIds.forEach { id ->
            val routine = RoutineCatalog.byId(id)
            val selected = current == id
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onSelect(id) },
                color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(routine.icon, fontSize = 12.sp)
                    Text(
                        routine.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// HISTORY CARD
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCard(entry: HistoryListItem, onClick: () -> Unit) {
    val log = entry.log

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header : date + status badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icône cercle avec initiale du jour
                val dayInitial = log.date.dayOfMonth.toString()
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(
                            if (log.completed) NeonGreen.copy(alpha = 0.12f)
                            else OrangeVibrant.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        dayInitial, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (log.completed) NeonGreen else OrangeVibrant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        entry.workoutName, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // Routine pill seule sur sa ligne — la date pleine ("Vendredi 7
                    // mai à 18:30") est trop longue pour cohabiter sur la même
                    // ligne avec le pill et le StatusBadge à droite. Sur sa propre
                    // ligne (full width via softWrap), elle ne peut JAMAIS être
                    // tronquée même sur petit écran.
                    val routine = RoutineCatalog.byId(log.routineId)
                    Text(
                        "${routine.icon} ${routine.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = OrangeVibrant.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                    Text(
                        formatLongDate(log.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        // Pas de maxLines : on autorise un wrap éventuel sur 2
                        // lignes plutôt qu'une troncature ; en pratique la date
                        // tient sur une ligne car elle a tout le width dispo.
                        softWrap = true,
                    )
                }
                StatusBadge(log.completed)
            }

            // Divider subtil
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)))

            // Métriques principales
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell(Icons.Default.Timer, formatSeconds(log.actualDurationSeconds), stringResource(R.string.history_metric_duration), Modifier.weight(1f))
                MetricCell(Icons.Default.FitnessCenter, "${entry.realExercisesCount}", stringResource(R.string.history_metric_exos), Modifier.weight(1f))
                MetricCell(Icons.Default.Repeat, "${entry.realSetsCount}", stringResource(R.string.history_metric_sets), Modifier.weight(1f))
                MetricCell(Icons.Default.Bolt, formatVolume(log.totalVolume), stringResource(R.string.history_metric_volume), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusBadge(completed: Boolean) {
    val label = if (completed) stringResource(R.string.history_status_completed) else stringResource(R.string.history_status_abandoned)
    val color = if (completed) NeonGreen else OrangeVibrant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun MetricCell(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

// ═══════════════════════════════════════
// Helpers — formatting
// ═══════════════════════════════════════
internal fun formatVolume(kg: Double): String = when {
    kg >= 10_000 -> String.format(Locale.getDefault(), "%.1ft", kg / 1000)
    kg >= 1_000 -> String.format(Locale.getDefault(), "%.1fk", kg / 1000) + "g"
    else -> "${kg.toInt()}kg"
}

internal fun formatDurationShort(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h${if (m > 0) "${m}" else ""}" else "${m}min"
}

internal fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h${String.format("%02d", m)}" else "${m}min"
}

internal fun formatLongDate(date: LocalDateTime): String {
    // Pattern locale-aware. 'à' / 'at' n'est pas i18n parfait mais gardé
    // simple — le séparateur passe en remarque dans translatable_pattern le cas échéant.
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM HH:mm", Locale.getDefault())
    return date.format(fmt).replaceFirstChar { it.uppercase() }
}

@Composable
private fun bucketLabelFor(d: LocalDate, today: LocalDate, startOfWeek: LocalDate, startOfMonth: LocalDate): String = when {
    d == today -> stringResource(R.string.history_bucket_today)
    d == today.minusDays(1) -> stringResource(R.string.history_bucket_yesterday)
    !d.isBefore(startOfWeek) -> stringResource(R.string.history_bucket_this_week)
    !d.isBefore(startOfMonth) -> stringResource(R.string.history_bucket_this_month)
    !d.isBefore(today.minusDays(90)) -> stringResource(R.string.history_bucket_3_months)
    else -> stringResource(R.string.history_bucket_older)
}

@Composable
private fun groupByBucketComposable(items: List<HistoryListItem>): Map<String, List<HistoryListItem>> {
    val today = LocalDate.now()
    val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val startOfMonth = today.withDayOfMonth(1)

    val labelToday = stringResource(R.string.history_bucket_today)
    val labelYesterday = stringResource(R.string.history_bucket_yesterday)
    val labelThisWeek = stringResource(R.string.history_bucket_this_week)
    val labelThisMonth = stringResource(R.string.history_bucket_this_month)
    val label3Months = stringResource(R.string.history_bucket_3_months)
    val labelOlder = stringResource(R.string.history_bucket_older)

    val groups = linkedMapOf<String, MutableList<HistoryListItem>>()
    items.forEach { item ->
        val d = item.log.date.toLocalDate()
        val bucket = when {
            d == today -> labelToday
            d == today.minusDays(1) -> labelYesterday
            !d.isBefore(startOfWeek) -> labelThisWeek
            !d.isBefore(startOfMonth) -> labelThisMonth
            !d.isBefore(today.minusDays(90)) -> label3Months
            else -> labelOlder
        }
        groups.getOrPut(bucket) { mutableListOf() }.add(item)
    }
    return groups
}

// ──────────────────────────────────────────────────────────
// Share / Export builders
// ──────────────────────────────────────────────────────────

private val historyDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

private fun buildWorkoutHistoryShareData(
    items: List<HistoryListItem>,
    ctx: android.content.Context,
): com.shredcoach.app.presentation.share.ShareCardData.HistorySummary {
    val totalSeances = items.size
    val totalVolume = items.sumOf { it.log.totalVolume }
    val totalSets = items.sumOf { it.log.totalSets }
    val totalReps = items.sumOf { it.log.totalReps }
    val totalDurationSec = items.sumOf { it.log.actualDurationSeconds }
    return com.shredcoach.app.presentation.share.ShareCardData.HistorySummary(
        title = ctx.getString(R.string.history_share_workouts_title),
        subtitle = ctx.getString(R.string.history_share_workouts_subtitle),
        accentEmoji = "📅",
        totalCount = totalSeances,
        countLabel = ctx.getString(R.string.history_share_workouts_count_label),
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_volume), value = totalVolume.toInt().toString(), unit = "kg",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_sets), value = totalSets.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_reps), value = totalReps.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_duration), value = (totalDurationSec / 60).toString(), unit = "min",
            ),
        ),
    )
}

private fun buildNutritionHistoryShareData(
    scans: List<com.shredcoach.app.data.local.entity.MealScanEntity>,
    ctx: android.content.Context,
): com.shredcoach.app.presentation.share.ShareCardData.HistorySummary {
    val totalCalories = scans.sumOf { it.totalCalories }
    val avgHealth = if (scans.isNotEmpty()) scans.map { it.healthScore }.average().toInt() else 0
    val avgProt = if (scans.isNotEmpty()) scans.map { it.totalProteins }.average().toInt() else 0
    return com.shredcoach.app.presentation.share.ShareCardData.HistorySummary(
        title = ctx.getString(R.string.history_share_nutrition_title),
        subtitle = ctx.getString(R.string.history_share_nutrition_subtitle),
        accentEmoji = "🍽️",
        totalCount = scans.size,
        countLabel = ctx.getString(R.string.history_share_nutrition_count_label),
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_total_kcal), value = totalCalories.toString(), unit = "kcal",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_avg_score), value = avgHealth.toString(), unit = "/100",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = ctx.getString(R.string.history_share_metric_avg_protein), value = avgProt.toString(), unit = "g",
            ),
        ),
    )
}

private fun buildWorkoutHistoryExportPayload(
    items: List<HistoryListItem>,
    ctx: android.content.Context,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    val defaultSessionName = ctx.getString(R.string.history_detail_default_share_title)
    val statusDone = ctx.getString(R.string.history_status_completed)
    val statusAbandoned = ctx.getString(R.string.history_status_abandoned)
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = ctx.getString(R.string.history_export_workouts_payload_title),
        description = ctx.getString(R.string.history_export_workouts_payload_desc, items.size),
        columns = listOf(
            ctx.getString(R.string.history_export_col_date),
            ctx.getString(R.string.history_export_col_session),
            ctx.getString(R.string.history_export_col_duration_min),
            ctx.getString(R.string.history_export_col_volume_kg),
            ctx.getString(R.string.history_export_col_sets),
            ctx.getString(R.string.history_export_col_reps),
            ctx.getString(R.string.history_export_col_rest_total_s),
            ctx.getString(R.string.history_export_col_exos_done),
            ctx.getString(R.string.history_export_col_exos_skipped),
            ctx.getString(R.string.history_export_col_status),
        ),
        rows = items.map { item ->
            val log = item.log
            listOf(
                log.date.format(historyDateFmt),
                item.workoutName.ifBlank { defaultSessionName },
                (log.actualDurationSeconds / 60).toString(),
                "%.1f".format(log.totalVolume),
                log.totalSets.toString(),
                log.totalReps.toString(),
                log.totalRestSeconds.toString(),
                log.exercisesCompleted.toString(),
                log.exercisesSkipped.toString(),
                if (log.completed) statusDone else statusAbandoned,
            )
        },
        summary = listOf(
            ctx.getString(R.string.history_export_summary_total_sessions) to items.size.toString(),
            ctx.getString(R.string.history_export_summary_total_volume) to "${items.sumOf { it.log.totalVolume }.toInt()} kg",
            ctx.getString(R.string.history_export_summary_total_duration) to "${items.sumOf { it.log.actualDurationSeconds } / 60} min",
        ),
    )
}

private fun buildNutritionHistoryExportPayload(
    scans: List<com.shredcoach.app.data.local.entity.MealScanEntity>,
    ctx: android.content.Context,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    val yes = ctx.getString(R.string.history_export_yes)
    val no = ctx.getString(R.string.history_export_no)
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = ctx.getString(R.string.history_export_nutrition_payload_title),
        description = ctx.getString(R.string.history_export_nutrition_payload_desc, scans.size),
        columns = listOf(
            ctx.getString(R.string.history_export_col_date),
            ctx.getString(R.string.history_export_col_meal_type),
            ctx.getString(R.string.history_export_col_dish),
            ctx.getString(R.string.history_export_col_cuisine),
            ctx.getString(R.string.history_export_col_kcal),
            ctx.getString(R.string.history_export_col_proteins_g),
            ctx.getString(R.string.history_export_col_carbs_g),
            ctx.getString(R.string.history_export_col_fats_g),
            ctx.getString(R.string.history_export_col_fibers_g),
            ctx.getString(R.string.history_export_col_weight_g),
            ctx.getString(R.string.history_export_col_health_score),
            ctx.getString(R.string.history_export_col_nutri_score),
            ctx.getString(R.string.history_export_col_verdict),
            ctx.getString(R.string.history_export_col_added_to_tracking),
        ),
        rows = scans.map { s ->
            listOf(
                s.timestamp.format(historyDateFmt),
                s.mealType,
                s.dishName,
                s.cuisine,
                s.totalCalories.toString(),
                "%.1f".format(s.totalProteins),
                "%.1f".format(s.totalCarbs),
                "%.1f".format(s.totalFats),
                "%.1f".format(s.totalFibers),
                s.totalWeight.toString(),
                s.healthScore.toString(),
                s.nutriScoreGrade,
                s.verdict,
                if (s.addedToTracking) yes else no,
            )
        },
        summary = listOf(
            ctx.getString(R.string.history_export_summary_total_scans) to scans.size.toString(),
            ctx.getString(R.string.history_export_summary_total_kcal) to scans.sumOf { it.totalCalories }.toString(),
            ctx.getString(R.string.history_export_summary_avg_health_score) to (if (scans.isNotEmpty()) scans.map { it.healthScore }.average().toInt() else 0).toString(),
        ),
    )
}
