package com.shredcoach.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.shredcoach.app.presentation.common.LocalAnimatedVisibilityScope
import com.shredcoach.app.presentation.common.LocalSharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.shredcoach.app.presentation.common.hapticClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shredcoach.app.domain.session.ActiveSessionManager
import com.shredcoach.app.presentation.home.HomeScreen
import com.shredcoach.app.presentation.exercises.ExercisesScreen
import com.shredcoach.app.presentation.exercises.ExerciseDetailScreen
import com.shredcoach.app.presentation.theme.DarkSurface
import com.shredcoach.app.presentation.theme.OrangeVibrant
import com.shredcoach.app.presentation.theme.TextSecondaryDark
import com.shredcoach.app.presentation.onboarding.OnboardingScreen
import com.shredcoach.app.presentation.nutrition.NutritionScreen
import com.shredcoach.app.presentation.profile.ProfileScreen
import com.shredcoach.app.presentation.profile.ProgressPhotosScreen
import com.shredcoach.app.presentation.settings.SettingsScreen
import com.shredcoach.app.presentation.history.WorkoutHistoryScreen
import com.shredcoach.app.presentation.history.WorkoutHistoryDetailScreen
import com.shredcoach.app.presentation.stats.DashboardScreen
import com.shredcoach.app.presentation.workout.CustomWorkoutScreen
import com.shredcoach.app.presentation.workout.FavoriteWorkoutsScreen
import com.shredcoach.app.presentation.workout.WorkoutGeneratorScreen
import com.shredcoach.app.presentation.workout.WorkoutPreviewScreen
import com.shredcoach.app.presentation.chat.ChatScreen
import com.shredcoach.app.presentation.nutrition.MealScanDetailScreen
import com.shredcoach.app.presentation.nutrition.MealScannerScreen
import com.shredcoach.app.presentation.workout.FavoritePreviewScreen
import com.shredcoach.app.presentation.workout.WorkoutSessionScreen
import com.shredcoach.app.presentation.workout.WorkoutSummaryScreen

// ═══════════════════════════════════════
// BOTTOM NAVIGATION — Modele de donnees
// ═══════════════════════════════════════

private data class BottomNavItem(
    val route: String,
    val label: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        label = "Accueil",
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home
    ),
    BottomNavItem(
        route = Screen.Nutrition.route,
        label = "Nutrition",
        outlinedIcon = Icons.Outlined.Restaurant,
        filledIcon = Icons.Filled.Restaurant
    ),
    BottomNavItem(
        route = Screen.WorkoutHistory.route,
        label = "Historique",
        outlinedIcon = Icons.Outlined.History,
        filledIcon = Icons.Filled.History
    ),
    BottomNavItem(
        route = Screen.Stats.route,
        label = "Stats",
        outlinedIcon = Icons.Outlined.BarChart,
        filledIcon = Icons.Filled.BarChart
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        label = "Réglages",
        outlinedIcon = Icons.Outlined.Settings,
        filledIcon = Icons.Filled.Settings
    )
)

/**
 * Associe une route de navigation a l'index de l'onglet parent dans la bottom bar.
 * Retourne -1 si la route n'appartient a aucun onglet (onboarding, etc.).
 */
private fun routeToTabIndex(route: String?): Int = when {
    route == null -> -1
    route == Screen.Home.route || route.startsWith("exercise")
        || route == Screen.WorkoutGenerator.route || route == Screen.Workout.route
        || route == Screen.WorkoutSession.route || route == Screen.WorkoutSummary.route
        || route == Screen.CustomWorkout.route || route == Screen.FavoriteWorkouts.route
        || route.startsWith("favorite_preview") -> 0
    route == Screen.Nutrition.route || route == Screen.MealScanner.route
        || route.startsWith("meal_scan_detail") -> 1
    route == Screen.Notifications.route -> -1 // Ne sélectionne aucun tab (écran transverse)
    route == Screen.BodyScanner.route || route == Screen.BodyMesh.route -> -1 // Transverse
    route == Screen.Calendar.route -> -1 // Transverse
    route == Screen.WorkoutHistory.route || route.startsWith("workout_history_detail") -> 2
    route == Screen.Stats.route -> 3
    route == Screen.Settings.route || route == Screen.Profile.route
        || route == Screen.ProgressPhotos.route -> 4
    else -> -1
}

private const val TAB_COLOR_ANIM_MS = 80
private const val TAB_ANIM_MS = 250
private val TabEasing = FastOutSlowInEasing

/**
 * Préfixes de routes autorisés en deeplink (notif coach proactive et actions).
 * Toute autre route est silencieusement rejetée pour empêcher l'injection
 * d'URI exotique via Intent extra. À synchroniser avec [Screen] au fur et à
 * mesure que de nouvelles cibles deeplink légitimes sont ajoutées.
 */
private val ALLOWED_DEEPLINK_PREFIXES = setOf(
    "home",
    "workout_generator",
    "workout_history_detail",
    "calendar",
    "meal_scanner",
    "body_scanner",
    "stats",
    "profile",
)

// Direction du slide entre tabs : -1 = vers la gauche, +1 = vers la droite
private fun tabDirection(from: String?, to: String?): Int {
    val fromIdx = routeToTabIndex(from)
    val toIdx = routeToTabIndex(to)
    return if (toIdx >= fromIdx) 1 else -1
}

private fun tabEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition {
    val dir = tabDirection(scope.initialState.destination.route, scope.targetState.destination.route)
    return slideInHorizontally(
        initialOffsetX = { width -> dir * (width / 8) },
        animationSpec = tween(TAB_ANIM_MS, easing = TabEasing)
    ) + fadeIn(tween(TAB_ANIM_MS / 2))
}

private fun tabExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition {
    val dir = tabDirection(scope.initialState.destination.route, scope.targetState.destination.route)
    return slideOutHorizontally(
        targetOffsetX = { width -> -dir * (width / 8) },
        animationSpec = tween(TAB_ANIM_MS, easing = TabEasing)
    ) + fadeOut(tween(TAB_ANIM_MS / 2))
}

// ═══════════════════════════════════════
// SNACKBAR CENTRALISE (via CompositionLocal)
// ═══════════════════════════════════════

val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState non fourni")
}

// ═══════════════════════════════════════
// NAVIGATION PRINCIPALE
// ═══════════════════════════════════════

@Composable
fun ShredCoachNavigation(
    sessionManager: ActiveSessionManager,
    hasProfile: Boolean = false,
    /** Incrémenté à chaque tap sur une notification push (inbox fallback). 0 = pas de deeplink. */
    openNotificationsTrigger: Int = 0,
    /**
     * (counter, route) — counter incrémenté à chaque deeplink demandé par
     * MainActivity. Quand `counter > 0 && route != null`, on navigue.
     * Le counter force la recomposition même quand la route est identique
     * à la précédente (re-tap sur la même catégorie de notif).
     */
    deeplinkRoute: Pair<Int, String?> = 0 to null,
) {
    val navController = rememberNavController()

    // Deeplink : chaque incrément du trigger → naviguer vers l'inbox (onCreate + onNewIntent)
    LaunchedEffect(openNotificationsTrigger, hasProfile) {
        if (openNotificationsTrigger > 0 && hasProfile) {
            navController.navigate(Screen.Notifications.route) {
                launchSingleTop = true
            }
        }
    }

    // Deeplink route (depuis notif coach proactive ou bouton d'action).
    LaunchedEffect(deeplinkRoute, hasProfile) {
        val (counter, route) = deeplinkRoute
        if (counter > 0 && route != null && hasProfile) {
            // Sécurité : allow-list de préfixes connus (vs regex char-class).
            // Le regex précédent rejetait `%`, `?`, `=`, ce qui faisait échouer
            // silencieusement les routes avec args encodés (ex: `meal_detail/123`
            // futur, ou paramètres query). Ici on valide le préfixe statique de la
            // route (avant le premier `/`) contre une liste connue, et on laisse
            // Compose Navigation parser la suite. Une route non listée → silent skip.
            val prefix = route.substringBefore('/')
            if (prefix in ALLOWED_DEEPLINK_PREFIXES) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    // Determiner la visibilite de la bottom bar
    val isOnSessionScreen = currentRoute?.startsWith("workout_session") == true
    val isOnOnboarding = currentRoute == Screen.Onboarding.route
    val isOnChat = currentRoute == Screen.Chat.route
    val showBottomBar = !isOnSessionScreen && !isOnOnboarding && !isOnChat

    // Session active (pour le bandeau)
    val session by sessionManager.session.collectAsState()
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    val hasActiveSession = session != null
    // Reset dismiss uniquement quand une session demarre (null→non-null), pas a chaque tick chrono
    LaunchedEffect(hasActiveSession) { if (hasActiveSession) bannerDismissed = false }

    val showSessionBanner = session != null && !isOnSessionScreen && !bannerDismissed

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // Bandeau seance active AU-DESSUS de la nav bar
                    if (showSessionBanner) {
                        ActiveSessionBanner(
                            session = session!!,
                            navController = navController,
                            onDismiss = { bannerDismissed = true }
                        )
                    }
                    // Bottom Navigation Bar
                    ShredCoachBottomBar(
                        navController = navController,
                        currentRoute = currentRoute
                    )
                }
            }
        }
    ) { paddingValues ->
      // SharedTransitionLayout englobe le NavHost pour permettre aux shared
      // element transitions (Modifier.sharedElementOptIn) de morpher entre
      // destinations consécutives. Aucun coût quand aucun shared element n'est
      // déclaré — c'est un simple wrapper layout.
      @OptIn(ExperimentalSharedTransitionApi::class)
      SharedTransitionLayout {
        val sharedTransitionScope = this
        CompositionLocalProvider(LocalSharedTransitionScope provides sharedTransitionScope) {
        NavHost(
            navController = navController,
            startDestination = if (hasProfile) Screen.Home.route else Screen.Onboarding.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(200)) +
                    fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) +
                    fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) +
                    fadeOut(animationSpec = tween(200))
            }
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(navController = navController)
            }

            // ── Tabs racines : crossfade rapide (pas de slide) ──
            // ── Tabs racines : slide directionnel + fade ──
            composable(
                Screen.Home.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                HomeScreen(navController = navController)
            }

            composable(
                Screen.Exercises.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                // Provide AnimatedVisibilityScope pour permettre aux ExerciseCard
                // de partager leur image+nom avec ExerciseDetailScreen.
                val animScope = this
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides animScope) {
                    ExercisesScreen(navController = navController)
                }
            }

            composable(
                route = Screen.ExerciseDetail.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) {
                val animScope = this
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides animScope) {
                    ExerciseDetailScreen(navController = navController)
                }
            }

            composable(Screen.ExerciseDbExplorer.route) {
                com.shredcoach.app.presentation.explorer.ExerciseDbExplorerScreen(navController = navController)
            }

            composable(Screen.GymScan.route) {
                com.shredcoach.app.presentation.gymscan.GymScanScreen(navController = navController)
            }

            composable(
                route = Screen.ExerciseDbDetail.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType })
            ) {
                com.shredcoach.app.presentation.explorer.ExerciseDbDetailScreen(navController = navController)
            }

            composable(
                Screen.WorkoutGenerator.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                WorkoutGeneratorScreen(navController = navController)
            }

            composable(Screen.CustomWorkout.route) {
                CustomWorkoutScreen(navController = navController)
            }

            composable(Screen.FavoriteWorkouts.route) {
                FavoriteWorkoutsScreen(navController = navController)
            }

            composable(
                route = Screen.FavoritePreview.route,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) {
                FavoritePreviewScreen(navController = navController)
            }

            composable(Screen.Workout.route) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.WorkoutGenerator.route)
                }
                WorkoutPreviewScreen(
                    navController = navController,
                    viewModel = hiltViewModel(parentEntry)
                )
            }

            composable(
                route = Screen.WorkoutSession.route,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) {
                WorkoutSessionScreen(navController = navController)
            }

            composable(Screen.WorkoutSummary.route) {
                WorkoutSummaryScreen(navController = navController, sessionManager = sessionManager)
            }

            composable(Screen.Nutrition.route) {
                NutritionScreen(navController = navController)
            }

            composable(Screen.Chat.route) {
                ChatScreen(navController = navController)
            }

            composable(Screen.Notifications.route) {
                com.shredcoach.app.presentation.notifications.NotificationsScreen(navController = navController)
            }

            composable(Screen.Calendar.route) {
                com.shredcoach.app.presentation.calendar.CalendarScreen(navController = navController)
            }

            // ─── Body Scanner (graph imbriqué pour partager le ViewModel entre Scanner + Mesh) ───
            navigation(
                route = "body_scanner_graph",
                startDestination = Screen.BodyScanner.route
            ) {
                composable(Screen.BodyScanner.route) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("body_scanner_graph")
                    }
                    com.shredcoach.app.presentation.bodyscanner.BodyScannerScreen(
                        navController = navController,
                        viewModel = hiltViewModel(parentEntry)
                    )
                }
                composable(Screen.BodyMesh.route) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("body_scanner_graph")
                    }
                    com.shredcoach.app.presentation.bodyscanner.BodyMeshScreen(
                        navController = navController,
                        viewModel = hiltViewModel(parentEntry)
                    )
                }
            }

            composable(Screen.MealScanner.route) {
                MealScannerScreen(navController = navController)
            }

            composable(
                route = Screen.MealScanDetail.route,
                arguments = listOf(navArgument("scanId") { type = NavType.StringType })
            ) {
                MealScanDetailScreen(navController = navController)
            }

            composable(
                Screen.Profile.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                ProfileScreen(navController = navController)
            }

            composable(Screen.ProgressPhotos.route) {
                ProgressPhotosScreen(navController = navController)
            }

            composable(
                Screen.Settings.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                SettingsScreen(navController = navController)
            }

            composable(Screen.PrivacyPolicy.route) {
                com.shredcoach.app.presentation.legal.PrivacyPolicyScreen(navController = navController)
            }

            composable(
                Screen.WorkoutHistory.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                WorkoutHistoryScreen(navController = navController)
            }

            composable(
                route = Screen.WorkoutHistoryDetail.route,
                arguments = listOf(navArgument("logId") { type = NavType.StringType })
            ) {
                WorkoutHistoryDetailScreen(navController = navController)
            }

            composable(
                Screen.Stats.route,
                enterTransition = { tabEnter(this) },
                exitTransition = { tabExit(this) },
                popEnterTransition = { tabEnter(this) },
                popExitTransition = { tabExit(this) }
            ) {
                DashboardScreen(navController = navController)
            }
        }
        } // CompositionLocalProvider LocalSharedTransitionScope
      } // SharedTransitionLayout
    }

    // Bandeau session visible meme quand la bottom bar est masquee (sauf ecran de seance)
    if (!showBottomBar && showSessionBanner) {
        Box(Modifier.fillMaxSize()) {
            ActiveSessionBanner(
                session = session!!,
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter),
                onDismiss = { bannerDismissed = true }
            )
        }
    }
    } // CompositionLocalProvider
}

// ═══════════════════════════════════════
// BOTTOM NAVIGATION BAR (custom, sans NavigationBarItem M3)
// ═══════════════════════════════════════

@Composable
private fun ShredCoachBottomBar(
    navController: NavController,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val currentTabIndex = routeToTabIndex(currentRoute)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DarkSurface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .height(64.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEachIndexed { index, item ->
                val isSelected = currentTabIndex == index
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) OrangeVibrant else TextSecondaryDark,
                    animationSpec = tween(TAB_COLOR_ANIM_MS),
                    label = "tabColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .hapticClickable {
                            // Ne rien faire UNIQUEMENT si on est déjà sur la route racine du tab
                            // (sinon on bloquerait la navigation depuis un sous-écran — ex : MealScanDetail → Historique)
                            val alreadyOnTabRoot = currentRoute == item.route
                            if (!alreadyOnTabRoot) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.filledIcon else item.outlinedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp),
                        tint = iconColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = iconColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// BANDEAU SEANCE ACTIVE
// ═══════════════════════════════════════

@Composable
private fun ActiveSessionBanner(
    session: ActiveSessionManager.ActiveSession,
    navController: NavController,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val seconds = session.globalChronoSeconds

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .hapticClickable {
                // Navigation directe avec popUpTo Home (sans inclusive) : nettoie
                // les sous-écrans (Exercises, MealScanner, etc.) au-dessus de Home,
                // garantit que la session est unique en sommet de stack, et évite
                // le crash du popBackStack-by-pattern sur certaines configurations
                // de back-stack mixtes (sous-écran d'un autre tab + WorkoutSession
                // déjà présente plus bas). L'état de la séance est restauré depuis
                // la DB par WorkoutSessionViewModel.loadWorkout — pas de perte.
                val sessionRoute = Screen.WorkoutSession.createRoute(session.workoutLogId)
                navController.navigate(sessionRoute) {
                    popUpTo(Screen.Home.route) { inclusive = false; saveState = false }
                    launchSingleTop = true
                }
            },
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dot pulsant
            val inf = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by inf.animateFloat(
                1f, 0.3f,
                infiniteRepeatable(tween(600), RepeatMode.Reverse),
                label = "a"
            )
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary)
            )

            // Infos. Le sous-titre s'adapte au type de séance :
            //  - Mode normal avec exos chargés : "Squat barre (3/8)"
            //  - Freestyle ou totalExercises=0 (pré-ajout) : "Séance libre" ou nom courant seul
            // Évite l'affichage moche "(1/0)" quand l'user a ouvert un freestyle
            // sans encore avoir ajouté d'exercice.
            Column(Modifier.weight(1f)) {
                Text(
                    "SEANCE EN COURS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    letterSpacing = 1.dp.value.sp
                )
                val subtitle = when {
                    session.totalExercises > 0 && session.currentExerciseName.isNotBlank() ->
                        "${session.currentExerciseName} (${session.currentExerciseIndex + 1}/${session.totalExercises})"
                    session.currentExerciseName.isNotBlank() -> session.currentExerciseName
                    else -> "Séance libre"
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // Chrono
            Text(
                formatBannerChrono(seconds),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )

            // Reprendre
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Reprendre",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )

            // Dismiss
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Masquer",
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatBannerChrono(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
