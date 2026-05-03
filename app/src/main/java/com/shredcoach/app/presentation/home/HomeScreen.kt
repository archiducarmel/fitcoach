package com.shredcoach.app.presentation.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val exerciseCount by viewModel.exerciseCount.collectAsState()
    val totalWorkouts by viewModel.totalWorkouts.collectAsState()
    val totalVolume by viewModel.totalVolume.collectAsState()
    val totalTimeMinutes by viewModel.totalTimeMinutes.collectAsState()
    val greetingInfo by viewModel.greetingInfo.collectAsState()

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
    val subtitle = when {
        greetingInfo.hasWorkedOutToday && greetingInfo.streakDays > 1 ->
            "Bravo ! ${greetingInfo.streakDays} jours de suite"
        greetingInfo.hasWorkedOutToday -> "Bien joué aujourd'hui !"
        greetingInfo.isTodayWorkoutDay -> "C'est jour de séance !"
        greetingInfo.lastWorkoutWasYesterday && greetingInfo.lastWorkoutVolume > 0 ->
            "Super séance hier, ${greetingInfo.lastWorkoutVolume.toInt()} kg soulevés"
        greetingInfo.streakDays > 1 -> "${greetingInfo.streakDays} jours de suite, continue !"
        else -> "On s'y met ?"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    Row(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.shredcoach.app.presentation.common.ShredCoachLogo(size = 28.dp)
                        Text(
                            "ShredCoach",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Cloche notifications avec badge
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
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                            Icon(Icons.Default.Notifications, "Notifications")
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Calendar.route) }) {
                        Icon(Icons.Default.CalendarMonth, "Calendrier")
                    }
                    IconButton(onClick = { navController.switchTo(Screen.Profile.route) }) { Icon(Icons.Default.Person, "Profil") }
                    IconButton(onClick = { navController.switchTo(Screen.Settings.route) }) { Icon(Icons.Default.Settings, "Paramètres") }
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "$timeGreeting, $firstName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 30.sp
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 18.sp
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
                NextSessionWidget(
                    nextDate = next.date,
                    nextTime = next.time,
                    title = next.title.ifBlank { "Séance planifiée" },
                    onClick = { navController.navigate(Screen.Calendar.route) }
                )
            }

            Text("S'entraîner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            // CTA #1 : Générer (action principale, la plus fréquente)
            Card(
                onClick = { navController.switchTo(Screen.WorkoutGenerator.route) },
                colors = CardDefaults.cardColors(containerColor = OrangeVibrant),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GÉNÉRER UNE SÉANCE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Full Body • ${userProfile?.preferredWorkoutDuration ?: 90} min • Adapté à ton niveau",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, Modifier.size(28.dp), tint = Color.White) }
                    }
                }
            }

            // CTA #2 : Séance Libre (freestyle)
            val freestyleLogId by viewModel.freestyleLogId.collectAsState()
            LaunchedEffect(freestyleLogId) {
                val id = freestyleLogId
                if (id != null && id > 0) {
                    viewModel.clearFreestyleLogId()
                    navController.navigate(Screen.WorkoutSession.createRoute(id))
                }
            }

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

            // CTA #3 et #4 côte à côte : Favoris + Créer
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Favoris
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

                // Créer
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

            // ═══════════════════════════════════════
            // SECTION 2 : PROGRESSION
            // ═══════════════════════════════════════

            Text("Ma progression", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

            if (totalWorkouts == 0) {
                // Nouvel utilisateur : card motivationnelle
                Card(
                    onClick = { navController.switchTo(Screen.WorkoutGenerator.route) },
                    colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(NeonGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EmojiEvents, null, Modifier.size(28.dp), tint = NeonGreen)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Ta première séance t'attend !", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Débloque tes stats de progression.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ArrowForward, null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                // Utilisateur actif : stats avec animation
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedStatCard(Modifier.weight(1f).fillMaxHeight(), "$totalWorkouts", "Séances", OrangeVibrant) {
                        navController.switchTo(Screen.Stats.route)
                    }
                    AnimatedStatCard(Modifier.weight(1f).fillMaxHeight(), fmtVol(totalVolume), "Volume", NeonGreen) {
                        navController.switchTo(Screen.Stats.route)
                    }
                    AnimatedStatCard(Modifier.weight(1f).fillMaxHeight(), fmtTime(totalTimeMinutes), "Temps", Color(0xFF3B82F6)) {
                        navController.switchTo(Screen.Stats.route)
                    }
                }
            }

            // ═══════════════════════════════════════
            // SECTION 3 : EXPLORER
            // ═══════════════════════════════════════

            Text("Explorer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

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
                SmallCard(Modifier.weight(1f).fillMaxHeight(), "Profil", Icons.Default.Person) { navController.switchTo(Screen.Profile.route) }
                SmallCard(Modifier.weight(1f).fillMaxHeight(), "Photos", Icons.Default.CameraAlt) { navController.switchTo(Screen.ProgressPhotos.route) }
            }

            // ─── Meal Scanner — accès rapide ───
            Card(
                onClick = { navController.navigate(Screen.MealScanner.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.9f),
                            Color(0xFF6D28D9).copy(alpha = 0.85f)
                        ))
                    ).padding(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CameraAlt, null, Modifier.size(24.dp), tint = Color.White)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Meal Scanner", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Scanne ton repas, Shreddy analyse les macros",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Default.ArrowForward, null, Modifier.size(20.dp),
                            tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ═══════════════════════════════════════
// COMPOSANTS
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedStatCard(modifier: Modifier, value: String, label: String, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.animation.Crossfade(
                targetState = value,
                animationSpec = androidx.compose.animation.core.tween(400),
                label = "statValue"
            ) { v ->
                Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeVibrant)
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
                        Text(timeLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = OrangeVibrant)
        }
    }
}

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", v / 1_000)
    else -> "%.0f kg".format(v)
}

private fun fmtTime(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}"
    else -> "${minutes}min"
}

// ═══════════════════════════════════════
// FAB SHREDDY — Design premium, brandé
// ═══════════════════════════════════════

@Composable
private fun ShreddyFab(onClick: () -> Unit) {
    // Halo pulsant subtil autour du FAB
    val inf = rememberInfiniteTransition(label = "shreddyPulse")
    val haloAlpha by inf.animateFloat(
        initialValue = 0.25f, targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "haloAlpha"
    )
    val haloScale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "haloScale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Halo
        Box(
            Modifier.size(64.dp)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale; alpha = haloAlpha }
                .background(OrangeVibrant, CircleShape)
        )
        // FAB
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
}
