package com.shredcoach.app.presentation.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.shredcoach.app.data.remote.AnalyzedDish
import com.shredcoach.app.data.remote.BowlType
import com.shredcoach.app.data.remote.MealAnalysisResult
import com.shredcoach.app.data.remote.Micronutrient
import com.shredcoach.app.data.remote.PlateType
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScannerScreen(
    navController: NavController,
    viewModel: MealScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { viewModel.setImage(it) } }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted; if (granted) cameraLauncher.launch(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it)).copy(Bitmap.Config.ARGB_8888, false)
                else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                viewModel.setImage(bmp)
            } catch (_: Exception) {}
        }
    }
    fun launchCamera() {
        // Toujours re-vérifier la permission au moment du tap (peut être révoquée depuis les paramètres)
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        cameraPermissionGranted = hasPermission
        if (hasPermission) cameraLauncher.launch(null) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(24.dp), tint = OrangeVibrant)
                        Column {
                            Text("Meal Scanner", fontWeight = FontWeight.Bold)
                            Text("Analyse nutritionnelle par IA", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { viewModel.toggleHistory() }) {
                        Icon(Icons.Default.History, "Historique",
                            tint = if (state.showHistory) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Historique des scans ───
            if (state.showHistory) {
                if (state.scanHistory.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Spacer(Modifier.height(8.dp))
                                Text("Aucun scan enregistré", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                } else {
                    state.scanHistory.forEach { scan ->
                        item(key = "scan_${scan.id}") { ScanHistoryCard(scan, onDelete = { viewModel.deleteScan(scan) }) }
                    }
                }
            }

            if (!state.showHistory) {

            // ─── Photo ───
            item {
                if (state.imageBitmap != null) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                        Image(bitmap = state.imageBitmap!!.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 280.dp), contentScale = ContentScale.Crop)
                        if (state.isAnalyzing) ScanOverlay(Modifier.matchParentSize())
                        if (!state.isAnalyzing) {
                            Surface(onClick = { viewModel.clear() }, shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color.White) }
                            }
                        }
                    }
                } else {
                    PhotoCaptureZone(onCamera = { launchCamera() }, onGallery = { galleryLauncher.launch("image/*") })
                }
            }

            // ─── Panneau d'aide à l'analyse (optionnel) ───
            if (state.imageBitmap != null && state.result == null && !state.isAnalyzing) {
                item { HintsPanel(state, viewModel) }
            }

            // ─── Bouton analyser ───
            if (state.imageBitmap != null && state.result == null && !state.isAnalyzing) {
                item {
                    Button(onClick = { viewModel.analyze() }, modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp))
                        Text("Analyser ce repas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ─── Loading ───
            if (state.isAnalyzing) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Shreddy analyse ton repas...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                        Text("Identification, calcul des macros et micronutriments", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                    }
                }
            }

            // ─── Erreur ───
            state.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // RÉSULTATS PREMIUM
            // ═══════════════════════════════════════
            val result = state.result
            if (result != null) {
                // ─── Hero card : score + totaux ───
                item { ScoreHeroCard(result) }

                // ─── Macros visuelles ───
                item { MacrosCard(result) }

                // ─── Chaque plat détaillé ───
                result.dishes.forEachIndexed { index, dish ->
                    item(key = "dish_$index") {
                        DishCard(
                            dish = dish,
                            dishIndex = index,
                            onIngredientWeightChanged = { ingIdx, newWeight ->
                                viewModel.updateIngredientWeight(index, ingIdx, newWeight)
                            }
                        )
                    }
                }

                // ─── Micronutriments ───
                if (result.micronutrients.isNotEmpty()) {
                    item { MicronutrientsCard(result.micronutrients) }
                }

                // ─── Allergènes ───
                if (result.allergens.isNotEmpty()) {
                    item { AllergensCard(result.allergens) }
                }

                // ─── Confirmation auto-ajout ───
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = NeonGreen)
                            Text("Automatiquement ajouté au suivi nutrition", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = NeonGreen)
                        }
                    }
                }

                // ─── Date/heure du repas (modifiable pour scans tardifs) ───
                item { MealDateTimeCard(state, viewModel) }

                // ─── Nouveau scan ───
                item {
                    OutlinedButton(onClick = { viewModel.clear() }, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Scanner un autre repas", fontWeight = FontWeight.Bold)
                    }
                }
            }

            } // fin if (!state.showHistory)

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ═══════════════════════════════════════
// HINTS PANEL — aide optionnelle pour l'analyse
// ═══════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HintsPanel(state: MealScannerState, viewModel: MealScannerViewModel) {
    val hasAnyHint = state.hintPlate != PlateType.NONE || state.hintBowl != BowlType.NONE || state.hintDescription.isNotBlank()

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ─── Header cliquable (toggle) ───
            Row(
                Modifier.fillMaxWidth().clickable { viewModel.toggleHintsPanel() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lightbulb, null, Modifier.size(22.dp), tint = OrangeVibrant)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Aide à l'analyse", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (hasAnyHint) "Indices renseignés · Shreddy les prendra en compte"
                        else "Optionnel · Améliore la précision de l'analyse",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasAnyHint) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (hasAnyHint) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = NeonGreen)
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    if (state.showHintsPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // ─── Contenu déplié ───
            if (state.showHintsPanel) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // ─── Assiettes ───
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = OrangeVibrant)
                            Text("Type d'assiette", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Aide à estimer le poids de la nourriture via le diamètre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlateType.values().filter { it != PlateType.NONE }.forEach { plate ->
                                val selected = state.hintPlate == plate
                                HintChip(
                                    label = "${plate.label} (${plate.diameterCm} cm)",
                                    selected = selected,
                                    onClick = { viewModel.setHintPlate(if (selected) PlateType.NONE else plate) }
                                )
                            }
                        }
                    }

                    // ─── Bols ───
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Circle, null, Modifier.size(16.dp), tint = OrangeVibrant)
                            Text("Type de bol", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Aide à estimer le poids via le volume",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BowlType.values().filter { it != BowlType.NONE }.forEach { bowl ->
                                val selected = state.hintBowl == bowl
                                val volLabel = if (bowl == BowlType.SALADIER) "≥ 1.5 L" else "${bowl.volumeMl} ml"
                                HintChip(
                                    label = "${bowl.label} ($volLabel)",
                                    selected = selected,
                                    onClick = { viewModel.setHintBowl(if (selected) BowlType.NONE else bowl) }
                                )
                            }
                        }
                    }

                    // ─── Description libre ───
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = OrangeVibrant)
                            Text("Précisions (facultatif)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Lève les ambiguïtés visuelles (ex: frite igname vs pomme de terre, haricot niébé, fromage blanc)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        OutlinedTextField(
                            value = state.hintDescription,
                            onValueChange = { viewModel.setHintDescription(it.take(300)) },
                            placeholder = { Text("Ex: frites d'igname, haricots niébé, fromage blanc 0%...",
                                style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions.Default
                        )
                        Text("${state.hintDescription.length}/300",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End)
                    }

                    // ─── Bouton reset ───
                    if (hasAnyHint) {
                        TextButton(
                            onClick = { viewModel.clearHints() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tout effacer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════
// RÉSULTATS RÉUTILISABLES (partagé avec MealScanDetailScreen)
// ═══════════════════════════════════════

@Composable
fun MealAnalysisResultView(result: MealAnalysisResult, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScoreHeroCard(result) }
        item { MacrosCard(result) }
        result.dishes.forEachIndexed { index, dish ->
            item(key = "detail_dish_$index") { DishCard(dish) }
        }
        if (result.micronutrients.isNotEmpty()) {
            item { MicronutrientsCard(result.micronutrients) }
        }
        if (result.allergens.isNotEmpty()) {
            item { AllergensCard(result.allergens) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ═══════════════════════════════════════
// SCORE HERO CARD
// ═══════════════════════════════════════

@Composable
internal fun ScoreHeroCard(result: MealAnalysisResult) {
    val scoreColor = when {
        result.healthScore >= 8 -> NeonGreen
        result.healthScore >= 5 -> OrangeVibrant
        else -> MaterialTheme.colorScheme.error
    }
    // Calcul Nutri-Score réel depuis les macros
    val nutriScore = computeNutriScore(result)

    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(scoreColor.copy(alpha = 0.95f), scoreColor.copy(alpha = 0.7f)))
        ).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.dishes.firstOrNull()?.name ?: "Repas analysé",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        if (result.dishes.size > 1)
                            Text("${result.dishes.size} plats détectés", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                        result.dishes.firstOrNull()?.cuisine?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                        }
                        Spacer(Modifier.height(8.dp))
                        com.shredcoach.app.domain.nutrition.NutriScorePictogram(nutriScore.grade, height = 26.dp)
                    }
                    // Score cercle
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(64.dp)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("${result.healthScore}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("/10", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                if (result.verdict.isNotBlank()) {
                    Text(result.verdict, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.95f))
                }
                // Totaux rapides
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickStat("${result.totalCalories}", "kcal")
                    QuickStatDivider()
                    QuickStat("${result.totalWeight}g", "poids")
                    QuickStatDivider()
                    QuickStat("${result.dishes.size}", "plat(s)")
                }
            }
        }
    }
}

/** Calcule le Nutri-Score depuis les données d'un MealAnalysisResult (somme des dishes). */
private fun computeNutriScore(result: MealAnalysisResult): com.shredcoach.app.domain.nutrition.NutriScoreCalculator.NutriScoreResult {
    val totalSugars = result.dishes.sumOf { it.carbsSugar }
    val totalSatFat = result.dishes.sumOf { it.fatsSaturated }
    val totalSalt = result.dishes.sumOf { it.salt }
    return com.shredcoach.app.domain.nutrition.NutriScoreCalculator.fromTotals(
        calories = result.totalCalories,
        sugars = totalSugars,
        saturatedFat = totalSatFat,
        saltG = totalSalt,
        fibers = result.totalFibers,
        proteins = result.totalProteins,
        weightG = result.totalWeight
    )
}

@Composable private fun QuickStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}
@Composable private fun QuickStatDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.25f)))
}

// ═══════════════════════════════════════
// MACROS CARD — Donut chart de répartition calorique
// ═══════════════════════════════════════

@Composable
internal fun MacrosCard(result: MealAnalysisResult) {
    // AJR EFSA pour les rings (% de l'apport journalier)
    val pAjr = (result.totalProteins / 50.0).toFloat().coerceIn(0f, 1f)
    val gAjr = (result.totalCarbs / 260.0).toFloat().coerceIn(0f, 1f)
    val lAjr = (result.totalFats / 70.0).toFloat().coerceIn(0f, 1f)
    val fAjr = (result.totalFibers / 25.0).toFloat().coerceIn(0f, 1f)

    // Répartition calorique pour le donut (fibres = 2 kcal/g)
    val pCal = result.totalProteins * 4
    val gCal = result.totalCarbs * 4
    val lCal = result.totalFats * 9
    val fCal = result.totalFibers * 2
    val totalCal = (pCal + gCal + lCal + fCal).coerceAtLeast(1.0)
    val pPct = (pCal / totalCal).toFloat()
    val gPct = (gCal / totalCal).toFloat()
    val lPct = (lCal / totalCal).toFloat()
    val fPct = (fCal / totalCal).toFloat()

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            // ═══ SECTION 1 : 4 mini rings (poids + % AJR) ═══
            Text("Macronutriments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MacroRing("Protéines", result.totalProteins, pAjr, Color(0xFF3B82F6), Modifier.weight(1f))
                MacroRing("Glucides", result.totalCarbs, gAjr, OrangeVibrant, Modifier.weight(1f))
                MacroRing("Lipides", result.totalFats, lAjr, Color(0xFFEF4444), Modifier.weight(1f))
                MacroRing("Fibres", result.totalFibers, fAjr, NeonGreen, Modifier.weight(1f))
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ═══ SECTION 2 : Donut répartition calorique (centré) ═══
            Text("Répartition calorique", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Donut centré
            // Capturer les couleurs composables AVANT le Canvas (DrawScope n'est pas @Composable)
            val proteinColor = Color(0xFF3B82F6)
            val carbColor = OrangeVibrant
            val fatColor = Color(0xFFEF4444)
            val fiberColor = NeonGreen
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt)
                        var startAngle = -90f
                        listOf(pPct to proteinColor, gPct to carbColor, lPct to fatColor, fPct to fiberColor).forEach { (pct, color) ->
                            val sweep = pct * 360f
                            if (sweep > 0.5f) {
                                drawArc(color, startAngle, sweep - 1.5f, false, style = stroke)
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${result.totalCalories}", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold)
                        Text("kcal", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            // Légende sous le donut — 2×2 grid bien espacée
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MacroLegendItem("Protéines", result.totalProteins, (pPct * 100).toInt(), Color(0xFF3B82F6))
                MacroLegendItem("Glucides", result.totalCarbs, (gPct * 100).toInt(), OrangeVibrant)
                MacroLegendItem("Lipides", result.totalFats, (lPct * 100).toInt(), Color(0xFFEF4444))
                MacroLegendItem("Fibres", result.totalFibers, (fPct * 100).toInt(), NeonGreen)
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, grams: Double, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animFraction by animateFloatAsState(fraction, tween(800), label = "ring_$label")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(58.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color.copy(alpha = 0.12f), -90f, 360f, false, style = s)
                drawArc(color, -90f, animFraction * 360f, false, style = s)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${String.format("%.0f", grams)}g", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text("${(fraction * 100).toInt()}% AJR", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            color = color.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MacroLegendItem(label: String, grams: Double, pct: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text("$pct%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text("${String.format("%.1f", grams)}g", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ═══════════════════════════════════════
// DISH CARD (par plat)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DishCard(
    dish: AnalyzedDish,
    dishIndex: Int = 0,
    onIngredientWeightChanged: ((ingredientIndex: Int, newWeightG: Int) -> Unit)? = null
) {
    val mealLabel = mealTypeLabel(dish.mealType)
    val mealIcon = mealTypeIcon(dish.mealType)

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            // ─── Header gradient ───
            Box(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ))
                ).padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Tag repas + cuisine
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = OrangeVibrant.copy(alpha = 0.12f)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(mealIcon, null, Modifier.size(12.dp), tint = OrangeVibrant)
                                Text(mealLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                            }
                        }
                        if (dish.cuisine.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(dish.cuisine, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                    // Nom + calories
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(dish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${dish.weightG} g", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Surface(shape = RoundedCornerShape(12.dp), color = OrangeVibrant) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${dish.calories}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("kcal", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                    }
                }
            }

            // ─── Macros — barres horizontales premium ───
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DishMacroRow("Protéines", dish.proteins, Color(0xFF3B82F6), dish.calories.toFloat())
                DishMacroRow("Glucides", dish.carbs, OrangeVibrant, dish.calories.toFloat())
                DishMacroRow("Lipides", dish.fats, Color(0xFFEF4444), dish.calories.toFloat())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DishMacroMini("Fibres", dish.fibers, NeonGreen, Modifier.weight(1f))
                    DishMacroMini("Sucres", dish.carbsSugar, Color(0xFFF59E0B), Modifier.weight(1f))
                    DishMacroMini("Saturés", dish.fatsSaturated, Color(0xFFDC2626), Modifier.weight(1f))
                    DishMacroMini("Sel", dish.salt, Color(0xFF64748B), Modifier.weight(1f))
                }
            }

        }
    }

    // ─── Cards ingrédients détaillées (sous le dish card) ───
    if (dish.ingredients.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        dish.ingredients.forEachIndexed { ingIdx, ing ->
            IngredientDetailCard(
                ing = ing,
                onWeightChange = onIngredientWeightChanged?.let { cb -> { newWeight -> cb(ingIdx, newWeight) } }
            )
            Spacer(Modifier.height(6.dp))
        }
    }
    } // fin Column wrapper
}

@Composable
private fun DishMacroRow(label: String, grams: Double, color: Color, totalCal: Float) {
    val calContrib = when (label) {
        "Protéines" -> grams * 4; "Glucides" -> grams * 4; "Lipides" -> grams * 9; else -> 0.0
    }
    val pct = if (totalCal > 0) (calContrib / totalCal * 100).toInt() else 0
    val fraction = (pct / 100f).coerceIn(0f, 1f)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(65.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
        Text("${String.format("%.1f", grams)}g", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
        Text("$pct%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            color = color, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun DishMacroMini(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${String.format("%.1f", grams)}g", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), fontSize = 10.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun IngredientDetailCard(
    ing: com.shredcoach.app.data.remote.Ingredient,
    onWeightChange: ((Int) -> Unit)? = null
) {
    val color = categoryColor(ing.category)
    val totalMacroG = (ing.proteins + ing.carbs + ing.fats).coerceAtLeast(0.1)
    val editable = onWeightChange != null

    // État local du champ (synchronisé avec le poids courant mais permet la saisie libre)
    var weightText by remember(ing.weightG) { mutableStateOf(ing.weightG.toString()) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun commit() {
        val parsed = weightText.toIntOrNull()
        if (parsed != null && parsed in 1..9999 && parsed != ing.weightG) {
            onWeightChange?.invoke(parsed)
        } else {
            // Valeur invalide ou inchangée → resync affichage sur la valeur courante
            weightText = ing.weightG.toString()
        }
        keyboardController?.hide()
    }

    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ─── Header avec accent couleur catégorie ───
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.1f), Color.Transparent)))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Dot catégorie
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Text(ing.category.take(1).uppercase(), style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold, color = color)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ing.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        if (editable) {
                            // ── Stepper premium : [−] [valeur éditable] g [+] ──
                            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                            val pulseTransition = rememberInfiniteTransition(label = "edit_pulse")
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 0.95f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1400, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse_alpha"
                            )

                            fun applyDelta(delta: Int) {
                                val baseVal = weightText.toIntOrNull() ?: ing.weightG
                                val next = (baseVal + delta).coerceIn(1, 9999)
                                if (next != ing.weightG) {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                    )
                                    onWeightChange?.invoke(next)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                // (catégorie déjà affichée dans la pastille de gauche → pas de doublon textuel)

                                // Widget stepper avec gradient + pulse + ombre
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    shadowElevation = 4.dp,
                                    color = Color.Transparent,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(
                                                Brush.linearGradient(listOf(
                                                    color.copy(alpha = 0.22f),
                                                    color.copy(alpha = 0.10f)
                                                ))
                                            )
                                            .border(
                                                width = 1.5.dp,
                                                brush = Brush.linearGradient(listOf(
                                                    color.copy(alpha = pulseAlpha),
                                                    color.copy(alpha = pulseAlpha * 0.5f)
                                                )),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .padding(horizontal = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        // Bouton −5g
                                        Box(
                                            Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(color.copy(alpha = 0.18f))
                                                .clickable { applyDelta(-5) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Remove, "Diminuer 5g",
                                                tint = color, modifier = Modifier.size(14.dp))
                                        }

                                        // Champ texte central + suffixe g
                                        Row(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            BasicTextField(
                                                value = weightText,
                                                onValueChange = { new ->
                                                    weightText = new.filter { it.isDigit() }.take(4)
                                                },
                                                modifier = Modifier
                                                    .width(34.dp)
                                                    .onFocusChanged { focus -> if (!focus.isFocused) commit() },
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = color,
                                                    textAlign = TextAlign.Center
                                                ),
                                                cursorBrush = SolidColor(color),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(onDone = { commit() })
                                            )
                                            Text("g",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = color)
                                        }

                                        // Bouton +5g
                                        Box(
                                            Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(color.copy(alpha = 0.18f))
                                                .clickable { applyDelta(5) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, "Augmenter 5g",
                                                tint = color, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }

                                // Pictogramme stylo signalant l'éditabilité
                                Icon(
                                    Icons.Default.Edit, "Modifiable",
                                    modifier = Modifier.size(12.dp),
                                    tint = color.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Text("${ing.category} · ${ing.weightG}g", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                    // Badge kcal
                    Surface(shape = RoundedCornerShape(8.dp), color = OrangeVibrant.copy(alpha = 0.1f)) {
                        Text("${ing.calories} kcal", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    }
                }
            }

            // ─── Barres macros proportionnelles ───
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IngredientMacroBar("Protéines", ing.proteins, Color(0xFF3B82F6), totalMacroG, Modifier.weight(1f))
                IngredientMacroBar("Glucides", ing.carbs, OrangeVibrant, totalMacroG, Modifier.weight(1f))
                IngredientMacroBar("Lipides", ing.fats, Color(0xFFEF4444), totalMacroG, Modifier.weight(1f))
                if (ing.fibers > 0) {
                    IngredientMacroBar("Fibres", ing.fibers, NeonGreen, totalMacroG, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun IngredientMacroBar(label: String, grams: Double, color: Color, totalG: Double, modifier: Modifier) {
    val fraction = (grams / totalG).toFloat().coerceIn(0f, 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${String.format("%.1f", grams)}g", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = color)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

// ═══════════════════════════════════════
// HISTORIQUE SCAN CARD
// ═══════════════════════════════════════

@Composable
private fun ScanHistoryCard(scan: com.shredcoach.app.data.local.entity.MealScanEntity, onDelete: () -> Unit) {
    val dateStr = scan.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
    val scoreColor = when { scan.healthScore >= 8 -> NeonGreen; scan.healthScore >= 5 -> OrangeVibrant; else -> MaterialTheme.colorScheme.error }

    // Nutri-Score : utiliser le grade stocké, sinon fallback depuis healthScore
    val nutriGrade = scan.nutriScoreGrade.firstOrNull() ?: nutriScoreGrade(scan.healthScore)

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Photo miniature ou score cercle
            if (scan.photoPath != null) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp))) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(
                            androidx.compose.ui.platform.LocalContext.current
                        ).data(java.io.File(scan.photoPath)).crossfade(true).build(),
                        contentDescription = scan.dishName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Score en overlay sur la photo
                    Surface(
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        color = scoreColor.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text("${scan.healthScore}", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            } else {
                // Fallback : score cercle si pas de photo
                Surface(shape = CircleShape, color = scoreColor.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("${scan.healthScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = scoreColor)
                        Text("/10", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = scoreColor.copy(alpha = 0.7f))
                    }
                }
            }
            // Infos
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(scan.dishName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                com.shredcoach.app.domain.nutrition.NutriScorePictogram(nutriGrade, height = 20.dp)
                Text("$dateStr · ${scan.totalCalories} kcal",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            // Supprimer
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Supprimer", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun categoryColor(category: String): Color = when (category.lowercase()) {
    "protéine" -> Color(0xFF3B82F6)
    "féculent" -> OrangeVibrant
    "légume" -> NeonGreen
    "mg", "matière grasse" -> Color(0xFFEF4444)
    "sauce", "condiment" -> Color(0xFF8B5CF6)
    else -> Color(0xFF64748B)
}

private fun mealTypeLabel(type: String): String =
    com.shredcoach.app.domain.nutrition.MealTypeClassifier.fromId(type).displayName

@Composable
private fun mealTypeIcon(type: String): androidx.compose.ui.graphics.vector.ImageVector {
    val cat = com.shredcoach.app.domain.nutrition.MealTypeClassifier.fromId(type)
    return when (cat.id) {
        "petit_dejeuner" -> Icons.Default.FreeBreakfast
        "dejeuner" -> Icons.Default.Restaurant
        "gouter" -> Icons.Default.Cake
        "diner" -> Icons.Default.RestaurantMenu
        "pretraining" -> Icons.Default.Bolt
        "grignotage" -> Icons.Default.Fastfood
        else -> Icons.Default.Restaurant
    }
}

// ═══════════════════════════════════════
// MICRONUTRIMENTS CARD
// ═══════════════════════════════════════

@Composable
internal fun MicronutrientsCard(micros: List<Micronutrient>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Micronutriments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            micros.forEach { micro ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(micro.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(micro.quantity, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                    Box(Modifier.width(60.dp).padding(start = 8.dp)) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        Box(Modifier.fillMaxWidth((micro.ajrPercent / 100f).coerceIn(0f, 1f)).height(6.dp)
                            .clip(RoundedCornerShape(3.dp)).background(
                                when { micro.ajrPercent >= 50 -> NeonGreen; micro.ajrPercent >= 20 -> OrangeVibrant; else -> Color(0xFF94A3B8) }
                            ))
                    }
                    Text("${micro.ajrPercent}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// ALLERGÈNES CARD
// ═══════════════════════════════════════

@Composable
internal fun AllergensCard(allergens: List<String>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = Color(0xFFF59E0B))
            Column {
                Text("Allergènes potentiels", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                Text(allergens.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════
// SCAN OVERLAY — Animation futuriste
// ═══════════════════════════════════════

@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "scan")
    val scanY by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "sl")
    val haloAlpha by inf.animateFloat(0.1f, 0.3f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ha")
    // Capturer les couleurs AVANT les Canvas (DrawScope n'est pas @Composable)
    val scanColor = OrangeVibrant
    val cornerColor = scanColor.copy(alpha = 0.8f)

    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Canvas(Modifier.fillMaxSize()) {
            val lineY = size.height * scanY
            drawRect(brush = Brush.verticalGradient(
                listOf(Color.Transparent, scanColor.copy(alpha = haloAlpha), scanColor.copy(alpha = 0.6f),
                    scanColor.copy(alpha = haloAlpha), Color.Transparent),
                startY = (lineY - 60f).coerceAtLeast(0f), endY = lineY + 60f
            ))
            drawLine(scanColor, Offset(0f, lineY), Offset(size.width, lineY), 3f, cap = StrokeCap.Round)
        }
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val cs = 24.dp.toPx(); val sw = 2.5f
            drawLine(cornerColor, Offset(0f,0f), Offset(cs,0f), sw); drawLine(cornerColor, Offset(0f,0f), Offset(0f,cs), sw)
            drawLine(cornerColor, Offset(size.width,0f), Offset(size.width-cs,0f), sw); drawLine(cornerColor, Offset(size.width,0f), Offset(size.width,cs), sw)
            drawLine(cornerColor, Offset(0f,size.height), Offset(cs,size.height), sw); drawLine(cornerColor, Offset(0f,size.height), Offset(0f,size.height-cs), sw)
            drawLine(cornerColor, Offset(size.width,size.height), Offset(size.width-cs,size.height), sw); drawLine(cornerColor, Offset(size.width,size.height), Offset(size.width,size.height-cs), sw)
        }
    }
}

// ═══════════════════════════════════════
// ZONE CAPTURE PHOTO
// ═══════════════════════════════════════

@Composable
private fun PhotoCaptureZone(onCamera: () -> Unit, onGallery: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.06f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeVibrant.copy(alpha = 0.2f))) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CameraAlt, null, Modifier.size(36.dp), tint = OrangeVibrant) }
            }
            Text("Scanner un repas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Shreddy identifie les ingrédients et calcule les macros et micronutriments",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text("Prendre une photo", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, OrangeVibrant)) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(20.dp), tint = OrangeVibrant); Spacer(Modifier.width(8.dp))
                    Text("Choisir depuis la galerie", fontWeight = FontWeight.Bold, color = OrangeVibrant)
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// MEAL DATE/TIME — Override pour scans tardifs
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDateTimeCard(state: MealScannerState, viewModel: MealScannerViewModel) {
    val dt = state.mealDateTime
    val now = java.time.LocalDateTime.now()
    val isLate = java.time.Duration.between(dt, now).toMinutes() > 15

    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.FRENCH)
    val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLate) OrangeVibrant.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isLate) OrangeVibrant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp),
                    tint = if (isLate) OrangeVibrant else MaterialTheme.colorScheme.primary)
                Text("Date et heure du repas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (isLate) {
                Text("Tu as scanné en différé. Indique l'heure réelle du repas pour une bonne classification (petit-dej, déj, dîner...).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            // Boutons date + heure côte à côte
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openDatePicker() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dt.format(dateFmt), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { viewModel.openTimePicker() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AccessTime, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dt.format(timeFmt), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            // Type de repas calculé
            state.mealCategory?.let { cat ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OrangeVibrant.copy(alpha = 0.12f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Restaurant, null, Modifier.size(14.dp), tint = OrangeVibrant)
                        Text("Classé : ${cat.displayName}", style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold, color = OrangeVibrant)
                    }
                }
            }
        }
    }

    // ─── Date Picker Dialog ───
    if (state.showDatePicker) {
        val initialMillis = dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { viewModel.closeDatePicker() },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val newDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                        viewModel.applyMealDateTime(java.time.LocalDateTime.of(newDate, dt.toLocalTime()))
                    } else {
                        viewModel.closeDatePicker()
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeDatePicker() }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ─── Time Picker Dialog ───
    if (state.showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = dt.hour, initialMinute = dt.minute, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { viewModel.closeTimePicker() },
            confirmButton = {
                TextButton(onClick = {
                    val newTime = java.time.LocalTime.of(timePickerState.hour, timePickerState.minute)
                    viewModel.applyMealDateTime(java.time.LocalDateTime.of(dt.toLocalDate(), newTime))
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeTimePicker() }) { Text("Annuler") }
            },
            title = { Text("Heure du repas", fontWeight = FontWeight.Bold) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}

// ═══════════════════════════════════════
// NUTRI-SCORE — fallback pour anciens scans sans grade stocké
// ═══════════════════════════════════════

/**
 * Fallback Nutri-Score basé sur le healthScore /10 du LLM.
 * Utilisé uniquement pour les anciens scans qui n'ont pas de nutriScoreGrade en DB.
 */
private fun nutriScoreGrade(healthScore: Int): Char = when {
    healthScore >= 9 -> 'A'
    healthScore >= 7 -> 'B'
    healthScore >= 5 -> 'C'
    healthScore >= 3 -> 'D'
    else -> 'E'
}
