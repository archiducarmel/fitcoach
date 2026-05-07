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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
    val grouped = remember(filteredItems) { groupByBucket(filteredItems) }

    // Scans nutrition
    val mealScans by remember { viewModel.mealScans }.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showShareHistory by remember { mutableStateOf(false) }
    var showExportHistory by remember { mutableStateOf(false) }

    if (showShareHistory) {
        com.shredcoach.app.presentation.share.ShareSheet(
            data = if (selectedTab == 0) buildWorkoutHistoryShareData(filteredItems)
            else buildNutritionHistoryShareData(mealScans),
            onDismiss = { showShareHistory = false },
        )
    }
    if (showExportHistory) {
        com.shredcoach.app.presentation.share.ExportSheet(
            title = if (selectedTab == 0) "Historique séances" else "Historique repas scannés",
            onPick = { format ->
                showExportHistory = false
                scope.launch {
                    val payload = if (selectedTab == 0) buildWorkoutHistoryExportPayload(filteredItems)
                    else buildNutritionHistoryExportPayload(mealScans)
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
                title = { Text("Historique", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showShareHistory = true }) {
                        Icon(Icons.Default.Share, "Partager l'historique")
                    }
                    IconButton(onClick = { showExportHistory = true }) {
                        Icon(Icons.Default.FileDownload, "Exporter")
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
                    Triple(0, "Séances", Icons.Default.FitnessCenter),
                    Triple(1, "Nutrition", Icons.Default.Restaurant)
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
                        title = "Aucune séance dans l'historique",
                        description = "Termine ta première séance et elle apparaîtra ici avec toutes ses métriques.",
                        ctaLabel = "Générer une séance",
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
                                "Aucune séance correspondante", style = MaterialTheme.typography.bodyMedium,
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
                title = "Aucun repas scanné",
                description = "Scanne ton premier repas avec le Meal Scanner pour voir ton historique nutritionnel ici.",
                ctaLabel = "Scanner un repas",
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
                val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
                    .replaceFirstChar { it.uppercase() }
                val dayCalories = dayScans.sumOf { it.totalCalories }

                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dateStr, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                            Text("$dayCalories kcal", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                title = { Text("Supprimer ce repas ?", fontWeight = FontWeight.Bold) },
                                text = { Text("\"${scan.dishName}\" sera supprimé de ton historique et du suivi nutritionnel.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.shredcoach.app.presentation.util.hapticHeavy(navController.context)
                                            viewModel.deleteMealScan(scan)
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Supprimer") }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } }
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
                    Text("${scan.totalCalories} kcal", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = OrangeVibrant)
                    Text("${scan.totalWeight}g", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                // 4 barres macros noms complets
                HistoryMacroBar("Protéines", scan.totalProteins, Color(0xFF3B82F6), totalMacroG)
                HistoryMacroBar("Glucides", scan.totalCarbs, OrangeVibrant, totalMacroG)
                HistoryMacroBar("Lipides", scan.totalFats, Color(0xFFEF4444), totalMacroG)
                HistoryMacroBar("Fibres", scan.totalFibers, NeonGreen, totalMacroG)
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
                    Icon(Icons.Default.DeleteOutline, "Supprimer", Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                }
                // Voir détails
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Voir l'analyse complète", style = MaterialTheme.typography.labelSmall,
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
        Text("${String.format("%.1f", grams)}g", style = MaterialTheme.typography.labelSmall,
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
                        "Mon parcours", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryStat("${state.totalWorkouts}", "Séances", Modifier.weight(1f))
                    VerticalDividerLight()
                    SummaryStat(formatVolume(state.totalVolumeKg), "Volume", Modifier.weight(1f))
                    VerticalDividerLight()
                    SummaryStat(formatDurationShort(state.totalDurationMinutes), "Temps", Modifier.weight(1f))
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
                    filter.displayName,
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
                "Toutes routines",
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
                MetricCell(Icons.Default.Timer, formatSeconds(log.actualDurationSeconds), "Durée", Modifier.weight(1f))
                MetricCell(Icons.Default.FitnessCenter, "${entry.realExercisesCount}", "Exos", Modifier.weight(1f))
                MetricCell(Icons.Default.Repeat, "${entry.realSetsCount}", "Séries", Modifier.weight(1f))
                MetricCell(Icons.Default.Bolt, formatVolume(log.totalVolume), "Volume", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusBadge(completed: Boolean) {
    val (label, color) = if (completed) "Terminée" to NeonGreen else "Abandonnée" to OrangeVibrant
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
    kg >= 10_000 -> String.format(Locale.FRANCE, "%.1ft", kg / 1000)
    kg >= 1_000 -> String.format(Locale.FRANCE, "%.1fk", kg / 1000) + "g"
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
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", Locale.FRANCE)
    return date.format(fmt).replaceFirstChar { it.uppercase() }
}

private fun groupByBucket(items: List<HistoryListItem>): Map<String, List<HistoryListItem>> {
    val today = LocalDate.now()
    val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val startOfMonth = today.withDayOfMonth(1)

    val groups = linkedMapOf<String, MutableList<HistoryListItem>>()
    items.forEach { item ->
        val d = item.log.date.toLocalDate()
        val bucket = when {
            d == today -> "Aujourd'hui"
            d == today.minusDays(1) -> "Hier"
            !d.isBefore(startOfWeek) -> "Cette semaine"
            !d.isBefore(startOfMonth) -> "Ce mois-ci"
            !d.isBefore(today.minusDays(90)) -> "3 derniers mois"
            else -> "Plus ancien"
        }
        groups.getOrPut(bucket) { mutableListOf() }.add(item)
    }
    return groups
}

// ──────────────────────────────────────────────────────────
// Share / Export builders
// ──────────────────────────────────────────────────────────

private val historyDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH)

private fun buildWorkoutHistoryShareData(
    items: List<HistoryListItem>,
): com.shredcoach.app.presentation.share.ShareCardData.HistorySummary {
    val totalSeances = items.size
    val totalVolume = items.sumOf { it.log.totalVolume }
    val totalSets = items.sumOf { it.log.totalSets }
    val totalReps = items.sumOf { it.log.totalReps }
    val totalDurationSec = items.sumOf { it.log.actualDurationSeconds }
    return com.shredcoach.app.presentation.share.ShareCardData.HistorySummary(
        title = "Mon historique séances",
        subtitle = "Total cumulé",
        accentEmoji = "📅",
        totalCount = totalSeances,
        countLabel = "séances",
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Volume", value = totalVolume.toInt().toString(), unit = "kg",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Séries", value = totalSets.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Reps", value = totalReps.toString(),
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Durée", value = (totalDurationSec / 60).toString(), unit = "min",
            ),
        ),
    )
}

private fun buildNutritionHistoryShareData(
    scans: List<com.shredcoach.app.data.local.entity.MealScanEntity>,
): com.shredcoach.app.presentation.share.ShareCardData.HistorySummary {
    val totalCalories = scans.sumOf { it.totalCalories }
    val avgHealth = if (scans.isNotEmpty()) scans.map { it.healthScore }.average().toInt() else 0
    val avgProt = if (scans.isNotEmpty()) scans.map { it.totalProteins }.average().toInt() else 0
    return com.shredcoach.app.presentation.share.ShareCardData.HistorySummary(
        title = "Mon historique repas",
        subtitle = "Tous mes scans",
        accentEmoji = "🍽️",
        totalCount = scans.size,
        countLabel = "repas scannés",
        keyMetrics = listOf(
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Total kcal", value = totalCalories.toString(), unit = "kcal",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Score moyen", value = avgHealth.toString(), unit = "/100",
            ),
            com.shredcoach.app.presentation.share.ShareCardData.StatsAggregate.KeyMetric(
                label = "Protéines/repas", value = avgProt.toString(), unit = "g",
            ),
        ),
    )
}

private fun buildWorkoutHistoryExportPayload(
    items: List<HistoryListItem>,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = "ShredCoach — Historique séances",
        description = "${items.size} séances exportées",
        columns = listOf(
            "Date", "Séance", "Durée (min)", "Volume (kg)", "Séries",
            "Reps", "Repos total (s)", "Exos terminés", "Exos passés", "Statut",
        ),
        rows = items.map { item ->
            val log = item.log
            listOf(
                log.date.format(historyDateFmt),
                item.workoutName.ifBlank { "Séance" },
                (log.actualDurationSeconds / 60).toString(),
                "%.1f".format(log.totalVolume),
                log.totalSets.toString(),
                log.totalReps.toString(),
                log.totalRestSeconds.toString(),
                log.exercisesCompleted.toString(),
                log.exercisesSkipped.toString(),
                if (log.completed) "Terminée" else "Abandonnée",
            )
        },
        summary = listOf(
            "Total séances" to items.size.toString(),
            "Volume cumulé" to "${items.sumOf { it.log.totalVolume }.toInt()} kg",
            "Durée cumulée" to "${items.sumOf { it.log.actualDurationSeconds } / 60} min",
        ),
    )
}

private fun buildNutritionHistoryExportPayload(
    scans: List<com.shredcoach.app.data.local.entity.MealScanEntity>,
): com.shredcoach.app.presentation.share.DataExporter.ExportPayload {
    return com.shredcoach.app.presentation.share.DataExporter.ExportPayload(
        title = "ShredCoach — Historique nutrition (scans)",
        description = "${scans.size} repas scannés",
        columns = listOf(
            "Date", "Type repas", "Plat", "Cuisine",
            "Kcal", "Protéines (g)", "Glucides (g)", "Lipides (g)", "Fibres (g)",
            "Poids (g)", "Score santé", "Nutri-Score", "Verdict", "Ajouté au suivi",
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
                if (s.addedToTracking) "Oui" else "Non",
            )
        },
        summary = listOf(
            "Total scans" to scans.size.toString(),
            "Kcal cumulées" to scans.sumOf { it.totalCalories }.toString(),
            "Score santé moyen" to (if (scans.isNotEmpty()) scans.map { it.healthScore }.average().toInt() else 0).toString(),
        ),
    )
}
