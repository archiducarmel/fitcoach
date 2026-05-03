package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.domain.session.ActiveSessionManager
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    navController: NavController,
    sessionManager: ActiveSessionManager
) {
    // Lire les stats depuis le SessionManager (persiste après destruction du ViewModel)
    val duration = sessionManager.lastSessionDuration
    val totalVolume = sessionManager.lastSessionVolume
    val totalSets = sessionManager.lastSessionSets
    val totalReps = sessionManager.lastSessionReps
    val totalRest = sessionManager.lastSessionRestSeconds
    val skipped = sessionManager.lastSessionSkipped

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Séance terminée !", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Congratulations card
            item {
                val shreddyMsg = sessionManager.lastShreddyMessage
                Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.15f))) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, Modifier.size(72.dp), tint = NeonGreen)
                        Text("Séance terminée !", style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold, color = NeonGreen)

                        // Message personnalisé de Shreddy
                        if (shreddyMsg.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = OrangeVibrant.copy(alpha = 0.12f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
                                    }
                                }
                                Text(shreddyMsg, style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Start,
                                    lineHeight = 22.sp)
                            }
                        } else {
                            Text("Bien joué !", style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Stats
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Statistiques", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatColumn(Icons.Default.Timer, fmtDuration(duration), "Durée réelle")
                            StatColumn(Icons.Default.FitnessCenter, "$totalSets", "Séries")
                            StatColumn(Icons.Default.MonitorWeight, fmtVolume(totalVolume), "Volume total")
                        }

                        Divider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatColumn(Icons.Default.RepeatOne, "$totalReps", "Reps totales")
                            StatColumn(Icons.Default.Pause, fmtDuration(totalRest), "Repos total")
                            if (skipped > 0) {
                                StatColumn(Icons.Default.SkipNext, "$skipped", "Skippés")
                            }
                        }
                    }
                }
            }

            // CTAs secondaires
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Stats.route) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Analytics, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voir stats")
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.WorkoutGenerator.route) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nouvelle séance")
                    }
                }
            }

            // ─── Réserver la prochaine séance (Calendar integration) ───
            item {
                Card(
                    onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Calendar.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, OrangeVibrant.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                .background(OrangeVibrant.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.EventAvailable, null, Modifier.size(22.dp), tint = OrangeVibrant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Réserver la prochaine séance",
                                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Planifie-la maintenant pour rester régulier",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = OrangeVibrant)
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        // Bouton terminer
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = {
                    navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Home.route) {
                        popUpTo(com.shredcoach.app.presentation.navigation.Screen.Home.route) { inclusive = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Home, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("TERMINER", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(32.dp), tint = OrangeVibrant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

private fun fmtDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}min"
        m > 0 -> "${m}min"
        else -> "${seconds}s"
    }
}

private fun fmtVolume(v: Double): String = when {
    v >= 1000 -> String.format(java.util.Locale.US, "%.1fk kg",v / 1000)
    else -> "%.0f kg".format(v)
}
