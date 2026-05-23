package com.shredcoach.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import com.shredcoach.app.presentation.common.LocalAnimatedVisibilityScope
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.shredcoach.app.R
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
import com.shredcoach.app.presentation.common.IncomingShareIntent
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
    @androidx.annotation.StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        labelRes = R.string.nav_home,
        outlinedIcon = Icons.Outlined.Home,
        filledIcon = Icons.Filled.Home
    ),
    BottomNavItem(
        route = Screen.Nutrition.route,
        labelRes = R.string.nav_nutrition,
        outlinedIcon = Icons.Outlined.Restaurant,
        filledIcon = Icons.Filled.Restaurant
    ),
    BottomNavItem(
        route = Screen.WorkoutHistory.route,
        labelRes = R.string.nav_history,
        outlinedIcon = Icons.Outlined.History,
        filledIcon = Icons.Filled.History
    ),
    BottomNavItem(
        route = Screen.Stats.route,
        labelRes = R.string.nav_stats,
        outlinedIcon = Icons.Outlined.BarChart,
        filledIcon = Icons.Filled.BarChart
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        labelRes = R.string.nav_settings,
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
    "settings",
    "glucose_analysis",  // deeplink notif recap quotidien 12h17 → analyse J-1
    "glucose_entry",     // permettre aussi un deeplink vers upload
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

    // Share intent entrant : l'user a partagé une image vers ShredCoach via
    // la system share sheet. MainActivity a déposé (target, uri) dans le
    // IncomingShareIntent bus → on navigue vers la destination correspondante.
    // Le ViewModel cible observera ensuite ce même bus pour consommer l'Uri.
    val pendingShare by IncomingShareIntent.pending.collectAsState()
    LaunchedEffect(pendingShare, hasProfile) {
        val p = pendingShare ?: return@LaunchedEffect
        if (!hasProfile) return@LaunchedEffect
        val route = when (p.target) {
            IncomingShareIntent.Target.GLUCOSE -> Screen.GlucoseEntry.createRoute()
            IncomingShareIntent.Target.MEAL -> Screen.MealScanner.route
        }
        navController.navigate(route) { launchSingleTop = true }
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
      // **Pourquoi pas de SharedTransitionLayout global** : la version 1.7+ de
      // Compose a un bug "Placement happened before lookahead" quand un
      // SharedTransitionLayout englobe un NavHost qui contient des écrans avec
      // LazyColumn (cf. WorkoutSessionScreen) et qu'on déclenche une transition
      // complexe (popUpTo / popBackStack avec sauts multiples — par ex. tap sur
      // la bannière "séance en cours" depuis un sous-écran d'un autre tab).
      // Crash systématique avec stacktrace pointant vers
      // SharedTransitionScopeKt$SharedTransitionScope$1$1$1$1.invoke.
      // Les modifiers `sharedElementOptIn` / `sharedBoundsOptIn` ont un fallback
      // no-op quand `LocalSharedTransitionScope.current == null` → la suppression
      // du wrapper est gracieuse, on perd juste le morph image entre Exercises
      // et ExerciseDetail (cosmétique). À ré-introduire si l'upstream Compose
      // corrige le bug en ciblant uniquement la sous-arborescence concernée.
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

            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("persona") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = "shreddy"
                    nullable = false
                })
            ) {
                ChatScreen(navController = navController)
            }

            composable(
                route = Screen.GlucoseEntry.route,
                arguments = listOf(
                    androidx.navigation.navArgument("date") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                com.shredcoach.app.presentation.glucose.GlucoseEntryScreen(navController = navController)
            }
            composable(Screen.GlucoseHistory.route) {
                com.shredcoach.app.presentation.glucose.GlucoseHistoryScreen(navController = navController)
            }
            composable(
                route = Screen.GlucoseAnalysis.route,
                arguments = listOf(
                    androidx.navigation.navArgument("date") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                com.shredcoach.app.presentation.glucose.GlucoseAnalysisScreen(navController = navController)
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
                composable(Screen.BodyComposition.route) { backStackEntry ->
                    val parentEntry = remember(backStackEntry) {
                        navController.getBackStackEntry("body_scanner_graph")
                    }
                    com.shredcoach.app.presentation.bodyscanner.BodyCompositionScreen(
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

            composable(Screen.LanguageSettings.route) {
                com.shredcoach.app.presentation.settings.language.LanguageSettingsScreen(
                    onBack = { navController.navigateUp() }
                )
            }

            composable(Screen.AssistantLlmSettings.route) {
                com.shredcoach.app.presentation.settings.llm.AssistantLlmSettingsScreen(
                    navController = navController,
                )
            }

            composable(Screen.LlmUsageDashboard.route) {
                com.shredcoach.app.presentation.settings.llm.LlmUsageDashboardScreen(
                    navController = navController,
                )
            }

            composable(Screen.LlmDebugPlayground.route) {
                com.shredcoach.app.presentation.debug.LlmDebugScreen(
                    navController = navController,
                )
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
                            if (alreadyOnTabRoot) return@hapticClickable

                            // **Pourquoi pas le pattern saveState/restoreState canonique** :
                            // il a un piège quand la destination est la start destination
                            // (Home). Si un état avait été sauvegardé sous la clé `home`
                            // par une navigation antérieure (Stats→Home avec saveState),
                            // `restoreState=true` réinjecte cet état au-dessus du Home
                            // courant — résultat : l'utilisateur tape "Accueil" depuis
                            // Notifications et l'écran ne change pas visuellement (l'ancien
                            // sous-écran de Home revient).
                            //
                            // Solution simple et robuste : si le tab cible est déjà dans
                            // le back-stack, on `popBackStack` jusqu'à lui (retour propre,
                            // l'état natif du back-stack se gère). Sinon, navigate frais
                            // avec popUpTo(start) pour ne pas accumuler les tabs.
                            val poppedToExisting = navController.popBackStack(
                                route = item.route,
                                inclusive = false,
                            )
                            if (!poppedToExisting) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val itemLabel = stringResource(item.labelRes)
                    Icon(
                        imageVector = if (isSelected) item.filledIcon else item.outlinedIcon,
                        contentDescription = itemLabel,
                        modifier = Modifier.size(22.dp),
                        tint = iconColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = itemLabel,
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
            // Hauteur min légèrement supérieure à la somme des 2 textes empilés
            // (labelSmall + bodyMedium ≈ 36dp + padding interne 20dp = ~56dp).
            // 68dp donne ~12dp de respiration verticale → l'œil perçoit la
            // bannière comme "généreuse" sans qu'elle prenne trop de place,
            // et accommode bien le scaling accessibilité jusqu'à fontScale 1.2.
            .heightIn(min = 68.dp)
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                // Pas de maxLines : le label est statique ("SEANCE EN COURS"
                // = 15 chars en labelSmall bold). Sur les écrans étroits avec
                // fontScale élevé, on préfère qu'il wrappe sur 2 lignes plutôt
                // que de l'ellipsiser → la lisibilité prime ici, le heightIn
                // de la Card absorbe le débordement minimal.
                Text(
                    "SEANCE EN COURS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    letterSpacing = 1.dp.value.sp,
                )
                val subtitle = when {
                    session.totalExercises > 0 && session.currentExerciseName.isNotBlank() ->
                        "${session.currentExerciseName} (${session.currentExerciseIndex + 1}/${session.totalExercises})"
                    session.currentExerciseName.isNotBlank() -> session.currentExerciseName
                    else -> "Séance libre"
                }
                // Subtitle : maxLines=1 + ellipsis ICI parce que les noms d'exos
                // sont VARIABLES et peuvent être très longs ("Développé incliné
                // haltères unilatéral"). Sans cette contrainte, la bannière
                // sauterait de 1 à 2 lignes selon l'exo courant.
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Chrono : tnum stabilise les chiffres au sein du même format
            // (MM:SS et H:MM:SS). Pas de widthIn : sur écrans étroits, ça
            // mangerait la place du sous-titre. Le shift mineur de 5→7 chars
            // (à la transition 1h, événement unique par séance) est acceptable.
            Text(
                formatBannerChrono(seconds),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                softWrap = false,
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
