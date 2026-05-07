package com.shredcoach.app.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.presentation.common.AnimatedCounter
import com.shredcoach.app.presentation.common.StaggeredAppear
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

private fun NavController.switchTo(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    val userProfile by viewModel.userProfile.collectAsState()
    val greetingInfo by viewModel.greetingInfo.collectAsState()
    val todayNutrition by viewModel.todayNutrition.collectAsState()
    val resumableSession by viewModel.resumableSession.collectAsState()
    val weeklyInsight by viewModel.weeklyInsight.collectAsState()
    val todayMood by viewModel.todayMood.collectAsState()

    // Demander la permission POST_NOTIFICATIONS (Android 13+) une seule fois
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { /* ignore result — if denied, notifs won't show but the app works */ }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Salutation contextuelle
    val hour = java.time.LocalTime.now().hour
    val timeGreeting = when {
        hour < 12 -> "Bonjour"
        hour < 18 -> "Bon après-midi"
        else -> "Bonsoir"
    }
    val firstName = userProfile?.firstName?.takeIf { it.isNotBlank() } ?: "Champion"
    // Subtitle : on retire toute référence au streak (la card streak a été
    // supprimée de la home) — sinon dissonance entre "X jours de suite"
    // affiché en sous-titre et l'absence de visualisation associée.
    val subtitle = when {
        greetingInfo.hasWorkedOutToday -> "Bien joué aujourd'hui !"
        greetingInfo.isTodayWorkoutDay -> "C'est jour de séance !"
        greetingInfo.lastWorkoutWasYesterday && greetingInfo.lastWorkoutVolume > 0 ->
            "Super séance hier, ${greetingInfo.lastWorkoutVolume.toInt()} kg soulevés"
        else -> "On s'y met ?"
    }

    Scaffold(
        topBar = {
            // TopBar épurée : avatar+prénom (= accès Profile) à gauche, cloche
            // notifications à droite. Calendar accessible via NextSessionWidget,
            // Settings accessible depuis ProfileScreen.
            TopAppBar(
                title = {},
                navigationIcon = {
                    // Avatar + firstName cliquables ensemble (cohérent Strava/Apple).
                    // On wrap dans une Row clickable plutôt qu'un IconButton autour
                    // de la seule icône — le tap couvre toute la zone visuelle.
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { navController.switchTo(Screen.Profile.route) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Profil de $firstName"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(OrangeVibrant.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = OrangeVibrant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            text = firstName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    val notifVm: com.shredcoach.app.presentation.notifications.NotificationsViewModel = hiltViewModel()
                    val notifState by notifVm.state.collectAsState()
                    BadgedBox(
                        badge = {
                            if (notifState.unreadCount > 0) {
                                Badge(containerColor = OrangeVibrant) {
                                    Text("${notifState.unreadCount}", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Icon(Icons.Default.Notifications, "Notifications")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ShreddyFab(onClick = { navController.navigate(Screen.Chat.route) })
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Hero de salutation (full width, pas tronqué par les actions) ───
            StaggeredAppear(index = 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "$timeGreeting, $firstName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 30.sp
                    )
                    // Programme jour : "{Routine} · 2/3 cette semaine".
                    // Affiché seulement si l'user a un planning (workoutDays != vide).
                    // La routine affichée = lastUsedRoutineId du profil (Full Body
                    // par défaut). Si l'user a fait plusieurs routines cette
                    // semaine, on affiche en plus un breakdown chip-row détaillé.
                    if (greetingInfo.totalSessionsPerWeek > 0) {
                        // Compteur de séances neutre — pas de préfixe routine pour
                        // ne pas figer une perception de type de séance unique.
                        // Le breakdown détaillé par routine apparaît juste en-dessous
                        // si l'user a fait plusieurs routines cette semaine.
                        Text(
                            text = "${greetingInfo.sessionsThisWeek}/${greetingInfo.totalSessionsPerWeek} cette semaine",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = OrangeVibrant,
                        )
                        if (greetingInfo.routinesBreakdownThisWeek.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                greetingInfo.routinesBreakdownThisWeek.forEach { (id, count) ->
                                    val routine = RoutineCatalog.byId(id)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = OrangeVibrant.copy(alpha = 0.10f),
                                    ) {
                                        Text(
                                            "${routine.icon} $count× ${routine.displayName}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OrangeVibrant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    // StreakHeroBadge retiré : la card streak n'a plus sa
                    // place sur la home. Le streak reste visible sur le
                    // dashboard "Mes Stats".
                }
            }

            // Dialog célébration palier streak (3/7/14/30/60/100j)
            greetingInfo.pendingMilestone?.let { milestone ->
                com.shredcoach.app.presentation.common.StreakMilestoneDialog(
                    days = milestone,
                    onDismiss = { viewModel.acknowledgeMilestone(milestone) },
                )
            }

            // ═══════════════════════════════════════
            // SECTION OUTILS IA — accès rapide premium
            // ═══════════════════════════════════════
            // Les 4 outils AI-powered (Shreddy, Meal/Body/Gym Scan) sont
            // remontés en hero just après le titre — c'est la valeur n°1
            // différenciante de l'app, ils ne doivent pas être enterrés
            // dans la section "Plus" collapsible.
            StaggeredAppear(index = 1) {
                com.shredcoach.app.presentation.home.components.AiToolsSection(
                    onShreddyClick = { navController.navigate(Screen.Chat.route) },
                    onMealScanClick = { navController.navigate(Screen.MealScanner.route) },
                    onBodyScanClick = { navController.navigate(Screen.BodyScanner.route) },
                    onGymScanClick = { navController.navigate(Screen.GymScan.route) },
                )
            }

            // ═══════════════════════════════════════
            // SECTION 1 : DÉMARRER UN ENTRAÎNEMENT
            // Les 3 entry points, hiérarchisés
            // ═══════════════════════════════════════

            // ─── Widget prochaine séance (Calendar integration) ───
            val calendarVm: com.shredcoach.app.presentation.calendar.CalendarViewModel = hiltViewModel()
            val calendarState by calendarVm.state.collectAsState()
            calendarState.nextUpcoming?.let { next ->
                StaggeredAppear(index = 2) {
                    NextSessionWidget(
                        nextDate = next.date,
                        nextTime = next.time,
                        title = next.title.ifBlank { "Séance planifiée" },
                        onClick = { navController.navigate(Screen.Calendar.route) }
                    )
                }
            }

            StaggeredAppear(index = 3) {
                Text("S'entraîner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }

            // CTA principal — Reprendre (si session <24h) OU Générer.
            // On utilise le même slot car les 2 sont mutuellement exclusifs : si une
            // séance est en cours, l'utilisateur veut la finir avant d'en générer une autre.
            val freestyleLogId by viewModel.freestyleLogId.collectAsState()
            LaunchedEffect(freestyleLogId) {
                val id = freestyleLogId
                if (id != null && id > 0) {
                    viewModel.clearFreestyleLogId()
                    navController.navigate(Screen.WorkoutSession.createRoute(id))
                }
            }

            StaggeredAppear(index = 4) {
                val resumable = resumableSession
                if (resumable != null) {
                    com.shredcoach.app.presentation.home.components.ResumeSessionCard(
                        session = resumable,
                        onClick = {
                            // launchSingleTop : si une instance existe déjà en haut
                            // (ex: l'user vient juste de quitter via back), Compose
                            // Navigation la réutilise → pas de re-init du ViewModel.
                            // Sinon, le ViewModel est créé et loadWorkout restaure
                            // la progression depuis les WorkoutSet persistés.
                            navController.navigate(Screen.WorkoutSession.createRoute(resumable.workoutLogId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                } else {
                    Card(
                        onClick = { navController.switchTo(Screen.WorkoutGenerator.route) },
                        colors = CardDefaults.cardColors(containerColor = OrangeVibrant),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                // Pas de maxLines : "GÉNÉRER UNE SÉANCE" en titleLarge bold
                                // peut juste atteindre la fin sur petits écrans + fontScale
                                // élevé → on préfère un wrap propre à une ellipsis.
                                Text("GÉNÉRER UNE SÉANCE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                // Sub-line agnostique du type de séance — l'utilisateur
                                // choisira sa routine (FB, Push, Pull, …) sur l'écran de
                                // génération. Affichage volontairement neutre pour ne pas
                                // suggérer que l'app est dédiée à un seul type.
                                Text("${userProfile?.preferredWorkoutDuration ?: 90} min • Adapté à ton niveau",
                                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(52.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(28.dp), tint = Color.White) }
                            }
                        }
                    }
                }
            }

            // ─── "Autres options" — collapsible (power-user moves) ───
            // Pourquoi collapsible : 80% des sessions utilisent Générer/Reprendre.
            // Libre/Favoris/Créer sont des chemins de power-user à reléguer en
            // 2e tier visuel — éviter la décision paralysie sur la home.
            var otherOptionsExpanded by rememberSaveable { mutableStateOf(false) }
            StaggeredAppear(index = 5) {
                OtherOptionsHeader(
                    expanded = otherOptionsExpanded,
                    onToggle = { otherOptionsExpanded = !otherOptionsExpanded },
                )
            }

            AnimatedVisibility(
                visible = otherOptionsExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Séance Libre
                    Card(
                        onClick = { viewModel.startFreestyleWorkout() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(12.dp), color = NeonGreen.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FlashOn, null, Modifier.size(24.dp), tint = NeonGreen) }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Séance libre", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Compose ta séance au feeling, exercice par exercice", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 2, lineHeight = 16.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }

                    // Favoris + Créer côte à côte
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            onClick = { navController.switchTo(Screen.FavoriteWorkouts.route) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Favorite, null, Modifier.size(22.dp), tint = Color(0xFFEF4444))
                                    Text("Mes favoris", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("Relancer une séance", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 2, lineHeight = 16.sp)
                            }
                        }
                        Card(
                            onClick = { navController.switchTo(Screen.CustomWorkout.route) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Build, null, Modifier.size(22.dp), tint = Color(0xFF3B82F6))
                                    Text("Créer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("Composer ma séance", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 2, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            // SECTION TODAY NUTRITION (H1)
            // Calories + protéines + macros + prochain repas
            // ═══════════════════════════════════════
            todayNutrition?.let { nutrition ->
                StaggeredAppear(index = 6) {
                    com.shredcoach.app.presentation.home.components.TodayNutritionCard(
                        nutrition = nutrition,
                        onScanMeal = { navController.navigate(Screen.MealScanner.route) },
                        onAddManual = { navController.switchTo(Screen.Nutrition.route) },
                    )
                }
            }

            // ═══════════════════════════════════════
            // INSIGHT DE LA SEMAINE (H3)
            // PR récent / Progression / Plateau — un seul highlight
            // ═══════════════════════════════════════
            weeklyInsight?.let { insight ->
                StaggeredAppear(index = 7) {
                    com.shredcoach.app.presentation.home.components.WeeklyInsightCard(
                        insight = insight,
                        onClick = { navController.switchTo(Screen.Stats.route) },
                    )
                }
            }

            // ═══════════════════════════════════════
            // DAILY CHECK-IN (H4)
            // 5 emojis 1-tap, affiché tant que mood d'aujourd'hui pas tapé
            // ═══════════════════════════════════════
            if (todayMood == null) {
                StaggeredAppear(index = 8) {
                    com.shredcoach.app.presentation.home.components.DailyCheckInCard(
                        onMoodSelected = { viewModel.saveMood(it) },
                    )
                }
            }

            // (Section "Ma progression" supprimée : les vanity metrics
            //  Séances/Volume/Temps all-time ont été remplacées par
            //  l'Insight de la semaine (H3) qui est actionnable. Les stats
            //  détaillées restent accessibles via "Plus → Mes Stats".)

            // ═══════════════════════════════════════
            // SECTION "PLUS" — collapsible (navigation secondaire)
            // ═══════════════════════════════════════
            // Pourquoi collapsible : la home doit rester actionable (CTA + nutrition
            // + insight + check-in). Les routes secondaires (catalogue, photos, etc.)
            // sont accessibles partout via la nav globale, pas besoin qu'elles
            // occupent 3 rangées en permanence sur la home.
            var moreExpanded by rememberSaveable { mutableStateOf(false) }
            StaggeredAppear(index = 9) {
                MoreSectionHeader(
                    expanded = moreExpanded,
                    onToggle = { moreExpanded = !moreExpanded },
                )
            }

            AnimatedVisibility(
                visible = moreExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionCard(Modifier.weight(1f).fillMaxHeight(), "Exercices", Icons.Default.FitnessCenter, OrangeVibrant) {
                            navController.switchTo(Screen.Exercises.route)
                        }
                        ActionCard(Modifier.weight(1f).fillMaxHeight(), "Mes Stats", Icons.Default.Analytics, NeonGreen) {
                            navController.switchTo(Screen.Stats.route)
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallCard(Modifier.weight(1f).fillMaxHeight(), "Nutrition", Icons.Default.Restaurant) { navController.switchTo(Screen.Nutrition.route) }
                        SmallCard(Modifier.weight(1f).fillMaxHeight(), "Photos", Icons.Default.CameraAlt) { navController.switchTo(Screen.ProgressPhotos.route) }
                        SmallCard(Modifier.weight(1f).fillMaxHeight(), "Calendrier", Icons.Default.CalendarMonth) { navController.navigate(Screen.Calendar.route) }
                    }
                }
            }

            // (Card "Meal Scanner" supprimée : actionnable depuis le bouton "Scanner"
            //  du TodayNutritionCard — évite le doublon visuel.)

            Spacer(Modifier.height(60.dp))
        }
    }
}

/**
 * Header cliquable de section collapsible — chevron qui pivote selon expanded.
 * Réutilisé pour les sections "Autres options" (CTAs séance) et "Plus" (nav secondaire).
 */
@Composable
private fun CollapsibleHeader(
    expanded: Boolean,
    labelExpanded: String,
    labelCollapsed: String,
    onToggle: () -> Unit,
) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "chevronRotation",
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        ) {
            Text(
                text = if (expanded) labelExpanded else labelCollapsed,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun OtherOptionsHeader(expanded: Boolean, onToggle: () -> Unit) {
    CollapsibleHeader(
        expanded = expanded,
        labelExpanded = "Moins d'options",
        labelCollapsed = "Autres options",
        onToggle = onToggle,
    )
}

@Composable
private fun MoreSectionHeader(expanded: Boolean, onToggle: () -> Unit) {
    CollapsibleHeader(
        expanded = expanded,
        labelExpanded = "Réduire",
        labelCollapsed = "Plus",
        onToggle = onToggle,
    )
}

// ═══════════════════════════════════════
// COMPOSANTS
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(90.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(26.dp), tint = color)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallCard(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

// ═══════════════════════════════════════
// WIDGET PROCHAINE SÉANCE (Calendar integration)
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextSessionWidget(
    nextDate: java.time.LocalDate,
    nextTime: java.time.LocalTime?,
    title: String,
    onClick: () -> Unit
) {
    val today = java.time.LocalDate.now()
    val dayDelta = java.time.temporal.ChronoUnit.DAYS.between(today, nextDate).toInt()
    val relativeLabel = when {
        dayDelta == 0 -> "Aujourd'hui"
        dayDelta == 1 -> "Demain"
        dayDelta in 2..6 -> nextDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRANCE)
            .replaceFirstChar { it.uppercase() }
        else -> nextDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.FRANCE))
            .replaceFirstChar { it.uppercase() }
    }
    val timeLabel = nextTime?.toString()?.substring(0, 5) ?: "—"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Date mini
            Column(
                Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(OrangeVibrant.copy(alpha = 0.12f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    nextDate.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRANCE)
                        .uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = OrangeVibrant
                )
                Text("${nextDate.dayOfMonth}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeVibrant,
                    maxLines = 1, softWrap = false)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Prochaine séance · $relativeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium)
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (nextTime != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(timeLabel, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1, softWrap = false)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = OrangeVibrant)
        }
    }
}

// ═══════════════════════════════════════
// FAB SHREDDY — Design premium, brandé
// ═══════════════════════════════════════

/**
 * FAB simple sans animation continue. Le halo pulsant infini précédent était
 * lu comme distractif en périphérie de vision (anti-pattern Apple/Google :
 * jamais d'animation de loop sur un FAB en idle). Si l'on veut signaler
 * "Shreddy a quelque chose à dire", il faudra brancher un badge `unreadMessage`
 * sur ChatRepository et n'animer que dans ce cas.
 */
@Composable
private fun ShreddyFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        containerColor = OrangeVibrant,
        contentColor = androidx.compose.ui.graphics.Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        com.shredcoach.app.presentation.common.ShredCoachLogo(
            size = 30.dp,
            tint = androidx.compose.ui.graphics.Color.White
        )
    }
}
