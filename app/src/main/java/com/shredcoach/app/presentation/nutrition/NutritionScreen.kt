package com.shredcoach.app.presentation.nutrition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.shredcoach.app.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.domain.nutrition.DailyActivityState
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.format.DateTimeFormatter

private val ProteinColor = Color(0xFF3B82F6)
private val CarbColor = Color(0xFFF59E0B)
private val FatColor = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(navController: NavController, viewModel: NutritionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    // BottomSheet ajout repas
    if (state.showAddMeal) {
        AddMealBottomSheet(state, viewModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nutrition_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Scanner repas
                SmallFloatingActionButton(
                    onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.MealScanner.route) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Default.CameraAlt, stringResource(R.string.nutrition_scan_meal_cd), Modifier.size(20.dp))
                }
                // Ajouter manuellement
                FloatingActionButton(onClick = { viewModel.openAddMeal(MealType.LUNCH) }, containerColor = OrangeVibrant) {
                    Icon(Icons.Default.Add, stringResource(R.string.nutrition_add_meal_cd))
                }
            }
        }
    ) { pad ->
      com.shredcoach.app.presentation.common.PullToRefreshBox(
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.padding(pad)
      ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Sélecteur de date ──
            item { DateSelector(state, viewModel) }

            // ── Banner one-shot : recalibration kcal (V2 MET 3.8) ──
            // Affiché TANT QUE l'user n'a pas dismiss. Placé en haut juste
            // après la date pour être vu (mais sous le sélecteur pour ne pas
            // gêner la navigation). Dismiss persiste en DataStore.
            if (state.showRecalibrationBanner) {
                item {
                    RecalibrationBanner(onDismiss = { viewModel.dismissRecalibrationBanner() })
                }
            }

            // ── Résumé macros ──
            item { MacrosSummaryCard(state) }

            // ── Jeûne nocturne (J-1 dernier repas → J premier repas) ──
            // Placé HAUT car c'est l'info la plus dynamique de la journée :
            // dès le 1er repas logué, la fenêtre se fixe ; un repas plus tôt
            // re-shrink la fenêtre. L'user perçoit immédiatement l'impact
            // sur son jeûne sans avoir à aller dans Stats.
            item {
                com.shredcoach.app.presentation.home.components.NightFastingCard(state.nightFasting)
            }

            // ── Empty state si aucun repas ──
            if (state.meals.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
                        com.shredcoach.app.presentation.common.EmptyState(
                            icon = Icons.Default.Restaurant,
                            title = stringResource(R.string.nutrition_empty_title),
                            description = stringResource(R.string.nutrition_empty_desc),
                            ctaLabel = stringResource(R.string.nutrition_empty_cta),
                            ctaIcon = Icons.Default.Add,
                            onCtaClick = { viewModel.openAddMeal(MealType.LUNCH) }
                        )
                    }
                }
            }

            // ── Repas par type ──
            MealType.values().forEach { type ->
                val mealsOfType = state.meals.filter { it.meal.mealType == type }
                if (mealsOfType.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.nutrition_meal_type_header, type.icon, stringResource(type.displayNameRes)),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    items(mealsOfType, key = { it.meal.id }) { mwf ->
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        val context = androidx.compose.ui.platform.LocalContext.current

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text(stringResource(R.string.nutrition_meal_delete_dialog_title), fontWeight = FontWeight.Bold) },
                                text = { Text(stringResource(R.string.nutrition_meal_delete_dialog_body, mwf.food.name, mwf.meal.quantityGrams)) },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.shredcoach.app.presentation.util.hapticHeavy(context)
                                            viewModel.deleteMeal(mwf.meal.id, mwf.food, mwf.meal.scanId)
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text(stringResource(R.string.common_delete)) }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) } }
                            )
                        }

                        MealCard(mwf, onDelete = {
                            com.shredcoach.app.presentation.util.hapticClick(context)
                            showDeleteConfirm = true
                        })
                    }
                }
            }

            // ── Boutons ajout rapide par type ──
            item {
                Text(stringResource(R.string.nutrition_quickadd_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.values().forEach { type ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.openAddMeal(type) },
                            label = { Text(stringResource(R.string.nutrition_meal_type_header, type.icon, stringResource(type.displayNameRes))) }
                        )
                    }
                }
            }

            // ── Insights nutrition (30 derniers jours) ──
            // Refonte complète de l'ancien "Top aliments" : agrège par
            // ingrédient normalisé (lemmatisé), pas par foodId — le foodId
            // d'un plat scanné n'étant jamais réutilisé. Cf. InsightsSection.
            state.insights?.let { ins ->
                if (!ins.isEmpty) {
                    item { InsightsSection(ins) }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
      }
    }
}

// (pas de wrapper custom)

// ═══════════════════════════════════════
// SÉLECTEUR DATE
// ═══════════════════════════════════════
@Composable
private fun DateSelector(state: NutritionState, viewModel: NutritionViewModel) {
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.getDefault())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.previousDay() }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.nutrition_date_prev_cd)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.selectedDate.format(fmt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.selectedDate == java.time.LocalDate.now()) {
                Text(stringResource(R.string.nutrition_date_today), style = MaterialTheme.typography.labelSmall, color = OrangeVibrant)
            }
        }
        IconButton(onClick = { viewModel.nextDay() }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.nutrition_date_next_cd)) }
    }
}

// ═══════════════════════════════════════
// BANNER RECALIBRATION KCAL (one-shot, dismissible)
// ═══════════════════════════════════════
/**
 * Affiché tant que l'utilisateur n'a pas tapé "Compris". Explique pourquoi la
 * cible kcal des jours d'entraînement a baissé suite au passage MET 5.5 → 3.8.
 *
 * **Style** : Card outlined avec tinte info (bleu) — pas alert rouge, c'est
 * une info pas une erreur. Bouton text-only à droite pour rester sobre.
 */
@Composable
private fun RecalibrationBanner(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.recalib_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.recalib_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                )
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        stringResource(R.string.recalib_banner_dismiss),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// CARD MACROS
// ═══════════════════════════════════════
@Composable
private fun MacrosSummaryCard(state: NutritionState) {
    val g = state.goal
    val target = state.adjustedTargetCalories
    val calPct = (state.totalCalories / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1.5f)
    val calColor = when { calPct > 1.1f -> MaterialTheme.colorScheme.error; calPct > 0.9f -> NeonGreen; else -> OrangeVibrant }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth()) {
            // ─── Hero calories ───
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(calColor.copy(alpha = 0.95f), calColor.copy(alpha = 0.7f))))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Badge état d'activité réelle (calculé depuis WorkoutLogEntity, pas calendrier)
                    ActivityStatePill(state = state.activityState, breakdown = state.energyBreakdown)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text(stringResource(R.string.nutrition_hero_calories_label), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${state.totalCalories.toInt()}", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(stringResource(R.string.nutrition_hero_calories_target, target), style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        val remaining = target - state.totalCalories.toInt()
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (remaining >= 0) "$remaining" else "+${-remaining}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(if (remaining >= 0) stringResource(R.string.nutrition_hero_remaining) else stringResource(R.string.nutrition_hero_excess),
                                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }

                    // Décomposition transparente : pourquoi cette cible
                    EnergyBreakdownStrip(state.energyBreakdown)
                }
            }

            // ─── Macros rings ───
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NutritionMacroRing(stringResource(R.string.nutrition_macro_proteins), state.totalProteins, g.targetProteins, ProteinColor)
                NutritionMacroRing(stringResource(R.string.nutrition_macro_carbs), state.totalCarbs, g.targetCarbs, CarbColor)
                NutritionMacroRing(stringResource(R.string.nutrition_macro_fats), state.totalFats, g.targetFats, FatColor)
            }
        }
    }
}

// ═══════════════════════════════════════
// PILL ÉTAT D'ACTIVITÉ + DÉCOMPOSITION ÉNERGIE
// ═══════════════════════════════════════
//
// Affichage transparent de la logique adaptative : la cible calorique du
// jour est calculée à partir de l'activité RÉELLE (séances complétées),
// pas du calendrier prévu. L'user voit clairement :
//  - L'état actuel (entraîné / en attente / au repos).
//  - La décomposition (base sédentaire + bonus séance).
//  - Combien de séance(s) ont contribué au bonus.

@Composable
private fun ActivityStatePill(state: DailyActivityState, breakdown: EnergyBreakdown) {
    val (label, icon) = when (state) {
        DailyActivityState.TRAINED -> {
            val sessions = breakdown.completedWorkouts
            val mins = breakdown.totalWorkoutMinutes
            val text = if (sessions == 1) stringResource(R.string.nutrition_activity_trained_one, mins)
            else stringResource(R.string.nutrition_activity_trained_many, sessions, mins)
            text to Icons.Default.FitnessCenter
        }
        DailyActivityState.PENDING -> stringResource(R.string.nutrition_activity_pending) to Icons.Default.AccessTime
        DailyActivityState.RESTED -> stringResource(R.string.nutrition_activity_rested) to Icons.Default.SelfImprovement
    }
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.22f)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun EnergyBreakdownStrip(breakdown: EnergyBreakdown) {
    if (breakdown.total == 0) return
    val deltaSign = if (breakdown.goalDelta >= 0) "+" else ""
    val sep = stringResource(R.string.nutrition_energy_separator)
    val basePart = stringResource(R.string.nutrition_energy_base, breakdown.sedentaryMaintenance)
    val goalPart = if (breakdown.goalDelta != 0)
        stringResource(R.string.nutrition_energy_goal_delta, deltaSign, breakdown.goalDelta) else null
    val sessionPart = if (breakdown.workoutBonus > 0) {
        if (breakdown.completedWorkouts > 1)
            stringResource(R.string.nutrition_energy_session_bonus_many, breakdown.workoutBonus)
        else stringResource(R.string.nutrition_energy_session_bonus_one, breakdown.workoutBonus)
    } else null
    val text = buildString {
        append(basePart)
        if (goalPart != null) { append(sep); append(goalPart) }
        if (sessionPart != null) { append(sep); append(sessionPart) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = Color.White.copy(alpha = 0.78f),
        maxLines = 2,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun NutritionMacroRing(label: String, current: Double, target: Int, color: Color) {
    val fraction = (current / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawArc(color.copy(alpha = 0.12f), -90f, 360f, false, style = s)
                drawArc(color, -90f, fraction * 360f, false, style = s)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.nutrition_macro_grams, current.toInt()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(stringResource(R.string.nutrition_macro_target_g, target), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}

// ═══════════════════════════════════════
// CARD REPAS
// ═══════════════════════════════════════
@Composable
private fun MealCard(mwf: MealWithFood, onDelete: () -> Unit) {
    val hasPhoto = mwf.photoPath != null
    // Nutri-Score : stocké si scan, sinon calculé à la volée depuis les macros du food
    val nutriGrade = mwf.meal.nutriScoreGrade.firstOrNull()
        ?: com.shredcoach.app.domain.nutrition.NutriScoreCalculator.calculate(
            energyKcalPer100g = mwf.food.caloriesPer100g,
            sugarsPer100g = mwf.food.carbsPer100g * 0.3,
            saturatedFatPer100g = mwf.food.fatsPer100g * 0.35,
            sodiumMgPer100g = 200.0,
            fibersPer100g = mwf.food.fiberPer100g,
            proteinsPer100g = mwf.food.proteinsPer100g
        ).grade

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth()) {
            // ─── Photo du repas (si scan) ───
            if (hasPhoto) {
                Box(Modifier.fillMaxWidth().height(140.dp)) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        ).data(java.io.File(mwf.photoPath!!)).crossfade(true).build(),
                        contentDescription = mwf.food.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Overlay kcal en haut à droite
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Text(stringResource(R.string.nutrition_meal_kcal, mwf.meal.calories.toInt()), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Ligne 1 : nom + kcal + delete
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mwf.food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                        val timeStr = mwf.meal.time?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                        val mealTypeLabel = stringResource(mwf.meal.mealType.displayNameRes)
                        val details = if (timeStr != null)
                            stringResource(R.string.nutrition_meal_details_with_time, timeStr, mwf.meal.quantityGrams, mealTypeLabel)
                        else stringResource(R.string.nutrition_meal_details, mwf.meal.quantityGrams, mealTypeLabel)
                        Text(details,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                        Text(stringResource(R.string.nutrition_meal_kcal, mwf.meal.calories.toInt()), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Close, stringResource(R.string.nutrition_meal_delete_cd), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
                // Ligne 2 : PICTOGRAMME NUTRI-SCORE (toujours visible)
                com.shredcoach.app.domain.nutrition.NutriScorePictogram(nutriGrade, height = 24.dp)
                // Ligne 3 : barres macros
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionMealMacro(stringResource(R.string.nutrition_macro_proteins), mwf.meal.proteins, ProteinColor, Modifier.weight(1f))
                    NutritionMealMacro(stringResource(R.string.nutrition_macro_carbs), mwf.meal.carbs, CarbColor, Modifier.weight(1f))
                    NutritionMealMacro(stringResource(R.string.nutrition_macro_fats), mwf.meal.fats, FatColor, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NutritionMealMacro(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.nutrition_macro_grams, grams.toInt()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = color.copy(alpha = 0.6f))
    }
}

// ═══════════════════════════════════════
// DIALOG AJOUT REPAS
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMealBottomSheet(state: NutritionState, viewModel: NutritionViewModel) {
    val snackbarHostState = com.shredcoach.app.presentation.navigation.LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { viewModel.closeAddMeal() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.nutrition_meal_type_header, state.selectedMealType.icon, stringResource(state.selectedMealType.displayNameRes)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Recherche
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQuery(it) },
                    label = { Text(stringResource(R.string.nutrition_addmeal_search_label)) },
                    placeholder = { Text(stringResource(R.string.nutrition_addmeal_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                // Résultats
                if (state.searchResults.isNotEmpty() && state.selectedFood == null) {
                    LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        items(state.searchResults) { food ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.selectFood(food) }.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(food.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.nutrition_addmeal_food_kcal_per100, food.caloriesPer100g.toInt(), food.proteinsPer100g.toInt(), food.carbsPer100g.toInt(), food.fatsPer100g.toInt()),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                                if (food.isFavorite) Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = CarbColor)
                            }
                            HorizontalDivider()
                        }
                    }
                }

                // Aliment sélectionné
                if (state.selectedFood != null) {
                    val food = state.selectedFood!!
                    val qty = state.quantity.toIntOrNull() ?: 0
                    val factor = qty / 100.0

                    Card(colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(food.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { viewModel.onSearchQuery(""); viewModel.selectFood(food).let { /* reset */ } }) {
                                    // TODO: better reset
                                }
                            }

                            OutlinedTextField(
                                value = state.quantity,
                                onValueChange = { viewModel.onQuantityChanged(it) },
                                label = { Text(stringResource(R.string.nutrition_addmeal_quantity_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                supportingText = { Text(stringResource(R.string.nutrition_addmeal_portion_hint, food.portionLabel, food.defaultPortionGrams)) }
                            )

                            // Preview macros
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                MacroPreview("${(food.caloriesPer100g * factor).toInt()}", stringResource(R.string.nutrition_addmeal_kcal_unit), OrangeVibrant)
                                MacroPreview(stringResource(R.string.nutrition_macro_grams, (food.proteinsPer100g * factor).toInt()), stringResource(R.string.nutrition_macro_label_prot), ProteinColor)
                                MacroPreview(stringResource(R.string.nutrition_macro_grams, (food.carbsPer100g * factor).toInt()), stringResource(R.string.nutrition_macro_label_gluc), CarbColor)
                                MacroPreview(stringResource(R.string.nutrition_macro_grams, (food.fatsPer100g * factor).toInt()), stringResource(R.string.nutrition_macro_label_lip), FatColor)
                            }
                        }
                    }

                    val addedSnackbar = stringResource(R.string.nutrition_addmeal_added_snackbar)
                    Button(
                        onClick = {
                            viewModel.confirmAddMeal()
                            scope.launch { snackbarHostState.showSnackbar(addedSnackbar, duration = SnackbarDuration.Short) }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        enabled = qty > 0
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nutrition_addmeal_add_cta), fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = { viewModel.closeAddMeal() }, Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.nutrition_addmeal_cancel))
                }
            }
    }
}

@Composable
private fun MacroPreview(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

