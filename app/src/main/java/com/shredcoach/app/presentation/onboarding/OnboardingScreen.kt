package com.shredcoach.app.presentation.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.FitnessGoal
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

private const val TOTAL_PAGES = 7

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Home.route) {
                popUpTo(com.shredcoach.app.presentation.navigation.Screen.Onboarding.route) { inclusive = true }
            }
        }
    }

    // Sync pager avec le ViewModel
    LaunchedEffect(state.currentPage) {
        if (pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(
                state.currentPage,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        // Progress
        if (state.currentPage > 0) {
            LinearProgressIndicator(
                progress = { state.currentPage.toFloat() / (TOTAL_PAGES - 1) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = OrangeVibrant
            )
            Spacer(Modifier.height(16.dp))
        }

        // Page content — HorizontalPager avec animation spring
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false // navigation uniquement par boutons (pour validation)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> FeaturesPage()
                2 -> NamePage(state, viewModel)
                3 -> BodyPage(state, viewModel, navController)
                4 -> GoalPage(state, viewModel)
                5 -> LevelEquipmentPage(state, viewModel)
                6 -> NutritionPage(state, viewModel)
            }
        }

        // Navigation buttons
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (state.currentPage > 0) {
                TextButton(onClick = { viewModel.prevPage() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retour")
                }
            } else Spacer(Modifier.width(1.dp))

            if (state.currentPage < TOTAL_PAGES - 1) {
                Button(
                    onClick = { viewModel.nextPage() },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                    shape = RoundedCornerShape(12.dp),
                    enabled = when (state.currentPage) { 2 -> state.firstName.isNotBlank(); else -> true }
                ) {
                    Text("Suivant", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            } else {
                Button(
                    onClick = { viewModel.completeOnboarding() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("C'est parti !", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Page dots animés
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(TOTAL_PAGES) { i ->
                val dotScale by animateFloatAsState(
                    targetValue = if (i == state.currentPage) 10f else 6f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dot"
                )
                Box(Modifier.padding(horizontal = 3.dp).size(dotScale.dp)
                    .clip(CircleShape).background(if (i == state.currentPage) OrangeVibrant else MaterialTheme.colorScheme.outlineVariant))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ═══════════════════════════════════════
// PAGES
// ═══════════════════════════════════════

@Composable private fun WelcomePage() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        com.shredcoach.app.presentation.common.ShredCoachLogo(size = 96.dp)
        Spacer(Modifier.height(24.dp))
        Text("ShredCoach", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = OrangeVibrant)
        Spacer(Modifier.height(12.dp))
        Text("Ton coach sportif & nutrition", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.height(32.dp))
        Text("Atteins ton physique de rêve avec un programme personnalisé, un suivi intelligent et un coaching en temps réel.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable private fun FeaturesPage() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Ce que ShredCoach fait pour toi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        FeatureItem(Icons.Default.FitnessCenter, "Séances Full Body", "Générateur intelligent avec 68+ exercices, chronomètre, suivi des poids", OrangeVibrant)
        Spacer(Modifier.height(16.dp))
        FeatureItem(Icons.Default.Restaurant, "Suivi Nutrition", "57 aliments, tracking macros, objectifs personnalisés", NeonGreen)
        Spacer(Modifier.height(16.dp))
        FeatureItem(Icons.Default.Analytics, "Dashboard BI", "Graphiques, records, tendances, comparaisons, export CSV", Color(0xFF3B82F6))
        Spacer(Modifier.height(16.dp))
        FeatureItem(Icons.Default.Notifications, "Coach 24/7", "Rappels repas, shakers, motivation, suivi sommeil", Color(0xFF8B5CF6))
    }
}

@Composable private fun FeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = color) }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable private fun NamePage(state: OnboardingState, vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Comment tu t'appelles ?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Pour personnaliser ton expérience", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(state.firstName, { vm.onFirstNameChanged(it) }, label = { Text("Prénom *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(state.lastName, { vm.onLastNameChanged(it) }, label = { Text("Nom (optionnel)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BodyPage(state: OnboardingState, vm: OnboardingViewModel, navController: androidx.navigation.NavController? = null) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Ton physique actuel", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Pour calculer tes besoins et suivre ta progression", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(20.dp))

        // ─── CTA Body Scanner IA (optionnel) ───
        if (navController != null) {
            Card(
                onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.BodyScanner.route) },
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF000814)),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Accessibility, null, Modifier.size(22.dp), tint = androidx.compose.ui.graphics.Color(0xFF00E5FF))
                    Column(Modifier.weight(1f)) {
                        Text("Scanner IA (optionnel)",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White)
                        Text("Photo → mesures auto-remplies",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFF00E5FF).copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = androidx.compose.ui.graphics.Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("M" to "Homme", "F" to "Femme").forEach { (code, label) ->
                val sel = state.sex == code
                Surface(
                    onClick = { vm.onSexChanged(code) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (sel) OrangeVibrant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (sel) BorderStroke(1.5.dp, OrangeVibrant) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.age, { vm.onAgeChanged(it) }, label = { Text("Âge") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(state.height, { vm.onHeightChanged(it) }, label = { Text("Taille (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(state.weight, { vm.onWeightChanged(it) }, label = { Text("Poids actuel (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalPage(state: OnboardingState, vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Ton objectif", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Qu'est-ce que tu veux atteindre ?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))

        GoalCard(Icons.Default.LocalFireDepartment, "Sèche", "Perdre du gras, abdos visibles", FitnessGoal.SHRED, state.goal == FitnessGoal.SHRED) { vm.onGoalChanged(FitnessGoal.SHRED) }
        Spacer(Modifier.height(12.dp))
        GoalCard(Icons.Default.FitnessCenter, "Prise de masse", "Gagner du muscle et de la force", FitnessGoal.BULK, state.goal == FitnessGoal.BULK) { vm.onGoalChanged(FitnessGoal.BULK) }
        Spacer(Modifier.height(12.dp))
        GoalCard(Icons.Default.Balance, "Maintien", "Garder la forme et la condition", FitnessGoal.MAINTAIN, state.goal == FitnessGoal.MAINTAIN) { vm.onGoalChanged(FitnessGoal.MAINTAIN) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, goal: FitnessGoal, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeVibrant.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(2.dp, OrangeVibrant) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) OrangeVibrant.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(26.dp),
                        tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun LevelEquipmentPage(state: OnboardingState, vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Ton niveau & équipement", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Niveau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(FitnessLevel.BEGINNER to "Débutant", FitnessLevel.INTERMEDIATE to "Inter\nmédiaire", FitnessLevel.ADVANCED to "Avancé").forEach { (level, label) ->
                val selected = state.level == level
                Surface(
                    onClick = { vm.onLevelChanged(level) },
                    modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (selected) 0.dp else 1.dp
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Équipement disponible", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        EquipCard(Icons.Default.Warehouse, "Salle complète", "Machines + poids libres", EquipmentType.FULL_GYM, state.equipment == EquipmentType.FULL_GYM) { vm.onEquipmentChanged(EquipmentType.FULL_GYM) }
        Spacer(Modifier.height(8.dp))
        EquipCard(Icons.Default.Home, "Home gym", "Haltères et barres", EquipmentType.HOME_GYM, state.equipment == EquipmentType.HOME_GYM) { vm.onEquipmentChanged(EquipmentType.HOME_GYM) }
        Spacer(Modifier.height(8.dp))
        EquipCard(Icons.Default.AccessibilityNew, "Poids du corps", "Aucun équipement", EquipmentType.BODYWEIGHT, state.equipment == EquipmentType.BODYWEIGHT) { vm.onEquipmentChanged(EquipmentType.BODYWEIGHT) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EquipCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, type: EquipmentType, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = if (selected) OrangeVibrant.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant),
        border = if (selected) BorderStroke(2.dp, OrangeVibrant) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) OrangeVibrant.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp),
                        tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable private fun NutritionPage(state: OnboardingState, vm: OnboardingViewModel) {
    val tdee = vm.calculateTDEE()

    LaunchedEffect(tdee) {
        vm.onCaloriesChanged(tdee.toString())
        vm.onProteinsChanged(((state.weight.toDoubleOrNull() ?: 80.0) * 2.2).toInt().toString())
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Objectifs nutrition", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Calculé automatiquement selon ton profil", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(24.dp))

        // TDEE
        Card(colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TDEE estimé", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("$tdee kcal/jour", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                Text(when (state.goal) {
                    FitnessGoal.SHRED -> "Déficit de 400 kcal pour sèche"
                    FitnessGoal.BULK -> "Surplus de 300 kcal pour prise de masse"
                    FitnessGoal.MAINTAIN -> "Maintenance calorique"
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(state.targetCalories, { vm.onCaloriesChanged(it) }, label = { Text("Calories cibles (kcal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(state.targetProteins, { vm.onProteinsChanged(it) }, label = { Text("Protéines cibles (g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true,
            supportingText = { Text("Recommandé : 2.2g par kg de poids") })

        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(24.dp), tint = NeonGreen)
                Text("Tu pourras modifier tout ça plus tard dans les paramètres.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
