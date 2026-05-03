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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealType
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
                title = { Text("Nutrition", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "Retour") } }
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
                    Icon(Icons.Default.CameraAlt, "Scanner repas", Modifier.size(20.dp))
                }
                // Ajouter manuellement
                FloatingActionButton(onClick = { viewModel.openAddMeal(MealType.LUNCH) }, containerColor = OrangeVibrant) {
                    Icon(Icons.Default.Add, "Ajouter repas")
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

            // ── Résumé macros ──
            item { MacrosSummaryCard(state) }

            // ── Empty state si aucun repas ──
            if (state.meals.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
                        com.shredcoach.app.presentation.common.EmptyState(
                            icon = Icons.Default.Restaurant,
                            title = "Qu'est-ce qu'on mange aujourd'hui ?",
                            description = "Ajoute tes repas pour suivre calories, protéines, glucides et lipides en temps réel.",
                            ctaLabel = "Ajouter un repas",
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
                        Text("${type.icon} ${type.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    items(mealsOfType, key = { it.meal.id }) { mwf ->
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        val context = androidx.compose.ui.platform.LocalContext.current

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                title = { Text("Supprimer ce repas ?", fontWeight = FontWeight.Bold) },
                                text = { Text("\"${mwf.food.name}\" (${mwf.meal.quantityGrams}g) sera retiré de ton suivi nutritionnel.") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            com.shredcoach.app.presentation.util.hapticHeavy(context)
                                            viewModel.deleteMeal(mwf.meal.id, mwf.food, mwf.meal.scanId)
                                            showDeleteConfirm = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Supprimer") }
                                },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } }
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
                Text("Ajouter un repas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.values().forEach { type ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.openAddMeal(type) },
                            label = { Text("${type.icon} ${type.displayName}") }
                        )
                    }
                }
            }

            // ── Top aliments (30 derniers jours) ──
            if (state.topFoods.isNotEmpty()) {
                item { TopFoodsCard(state.topFoods) }
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
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.previousDay() }) { Icon(Icons.Default.ChevronLeft, "Jour précédent") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.selectedDate.format(fmt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.selectedDate == java.time.LocalDate.now()) {
                Text("Aujourd'hui", style = MaterialTheme.typography.labelSmall, color = OrangeVibrant)
            }
        }
        IconButton(onClick = { viewModel.nextDay() }) { Icon(Icons.Default.ChevronRight, "Jour suivant") }
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
                    // Badge jour training / repos
                    val dayLabel = if (state.isTrainingDay) "Jour d'entraînement" else "Jour de repos"
                    val dayIcon = if (state.isTrainingDay) Icons.Default.FitnessCenter else Icons.Default.SelfImprovement
                    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(dayIcon, null, Modifier.size(14.dp), tint = Color.White)
                            Text(dayLabel, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text("Calories", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${state.totalCalories.toInt()}", style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("/ $target kcal", style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 4.dp))
                            }
                        }
                        val remaining = target - state.totalCalories.toInt()
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (remaining >= 0) "$remaining" else "+${-remaining}",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(if (remaining >= 0) "restantes" else "en excès",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            // ─── Macros rings ───
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                NutritionMacroRing("Protéines", state.totalProteins, g.targetProteins, ProteinColor)
                NutritionMacroRing("Glucides", state.totalCarbs, g.targetCarbs, CarbColor)
                NutritionMacroRing("Lipides", state.totalFats, g.targetFats, FatColor)
            }
        }
    }
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
                Text("${current.toInt()}g", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text("/ ${target}g", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
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
                        Text("${mwf.meal.calories.toInt()} kcal", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                        val details = buildString {
                            if (timeStr != null) { append(timeStr); append(" · ") }
                            append("${mwf.meal.quantityGrams}g · ${mwf.meal.mealType.displayName}")
                        }
                        Text(details,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                        Text("${mwf.meal.calories.toInt()} kcal", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    }
                    IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Supprimer", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
                // Ligne 2 : PICTOGRAMME NUTRI-SCORE (toujours visible)
                com.shredcoach.app.domain.nutrition.NutriScorePictogram(nutriGrade, height = 24.dp)
                // Ligne 3 : barres macros
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionMealMacro("Protéines", mwf.meal.proteins, ProteinColor, Modifier.weight(1f))
                    NutritionMealMacro("Glucides", mwf.meal.carbs, CarbColor, Modifier.weight(1f))
                    NutritionMealMacro("Lipides", mwf.meal.fats, FatColor, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TopFoodsCard(foods: List<TopFoodDisplay>) {
    val maxCount = foods.maxOfOrNull { it.count } ?: 1

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TrendingUp, null, Modifier.size(20.dp), tint = OrangeVibrant)
                    Text("Top aliments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("30 jours", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Liste avec podium visuel
            foods.forEachIndexed { index, food ->
                val fraction = (food.count.toFloat() / maxCount).coerceIn(0f, 1f)
                val medal = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> null }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Rang
                    if (medal != null) {
                        Text(medal, fontSize = 18.sp)
                    } else {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        }
                    }

                    // Nom + barre + stats
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(food.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(8.dp))
                            Text("${food.count}× · ${food.totalGrams}g", style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold, color = OrangeVibrant)
                        }
                        // Barre de fréquence
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                            .background(OrangeVibrant.copy(alpha = 0.08f))) {
                            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                                .background(
                                    when (index) {
                                        0 -> OrangeVibrant
                                        1 -> OrangeVibrant.copy(alpha = 0.7f)
                                        2 -> OrangeVibrant.copy(alpha = 0.5f)
                                        else -> OrangeVibrant.copy(alpha = 0.3f)
                                    }
                                ))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NutritionMealMacro(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${grams.toInt()}g", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
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
                Text("${state.selectedMealType.icon} ${state.selectedMealType.displayName}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Recherche
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQuery(it) },
                    label = { Text("Rechercher un aliment") },
                    placeholder = { Text("Ex: poulet, riz, banane...") },
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
                                    Text("${food.caloriesPer100g.toInt()} kcal/100g • P${food.proteinsPer100g.toInt()} G${food.carbsPer100g.toInt()} L${food.fatsPer100g.toInt()}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                                if (food.isFavorite) Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = CarbColor)
                            }
                            Divider()
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
                                label = { Text("Quantité (g)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                supportingText = { Text("Portion: ${food.portionLabel} (${food.defaultPortionGrams}g)") }
                            )

                            // Preview macros
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                MacroPreview("${(food.caloriesPer100g * factor).toInt()}", "kcal", OrangeVibrant)
                                MacroPreview("${(food.proteinsPer100g * factor).toInt()}g", "Prot", ProteinColor)
                                MacroPreview("${(food.carbsPer100g * factor).toInt()}g", "Gluc", CarbColor)
                                MacroPreview("${(food.fatsPer100g * factor).toInt()}g", "Lip", FatColor)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.confirmAddMeal()
                            scope.launch { snackbarHostState.showSnackbar("Repas ajouté", duration = SnackbarDuration.Short) }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        enabled = qty > 0
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter", fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = { viewModel.closeAddMeal() }, Modifier.align(Alignment.End)) {
                    Text("Annuler")
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

