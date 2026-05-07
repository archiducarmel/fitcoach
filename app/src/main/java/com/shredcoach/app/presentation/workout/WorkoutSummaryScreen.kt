package com.shredcoach.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.domain.session.ActiveSessionManager
import com.shredcoach.app.presentation.common.AnimatedCounter
import com.shredcoach.app.presentation.common.CelebrationTrophy
import com.shredcoach.app.presentation.common.ShredButton
import com.shredcoach.app.presentation.common.ShredButtonVariant
import com.shredcoach.app.presentation.common.StaggeredAppear
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlinx.coroutines.delay

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

    // Haptic celebration au mount : double pulse "victory" (150ms entre chaque)
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(150)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val ctx = LocalContext.current
    var showSharePreview by remember { mutableStateOf(false) }
    if (showSharePreview) {
        // Reconstruit la liste d'exos avec status DONE/SKIPPED depuis les
        // snapshots persistés sur ActiveSessionManager (le state du
        // ViewModel a déjà été détruit par stopSession()).
        val skippedSet = sessionManager.lastSessionSkippedIndices
        val metrics = sessionManager.lastSessionExerciseMetrics
        val items = sessionManager.lastSessionExerciseNames.mapIndexed { idx, name ->
            val status = if (idx in skippedSet) {
                com.shredcoach.app.presentation.share.ShareCardData.ExerciseStatus.SKIPPED
            } else {
                com.shredcoach.app.presentation.share.ShareCardData.ExerciseStatus.DONE
            }
            com.shredcoach.app.presentation.share.ShareCardData.ExerciseProgressItem(
                name = name,
                status = status,
                metric = metrics[idx],
            )
        }
        com.shredcoach.app.presentation.share.ShareSheet(
            data = com.shredcoach.app.presentation.share.ShareCardData.WorkoutFinished(
                title = stringResource(R.string.summary_share_card_title),
                subtitle = stringResource(R.string.summary_share_card_subtitle),
                durationSeconds = duration,
                totalVolumeKg = totalVolume,
                totalSets = totalSets,
                totalReps = totalReps,
                exerciseCount = sessionManager.lastSessionExerciseCount,
                coachMessage = sessionManager.lastShreddyMessage.ifBlank { null },
                completedExercises = items,
            ),
            onDismiss = { showSharePreview = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.summary_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSharePreview = true }) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.Share,
                            contentDescription = stringResource(R.string.summary_share_cd),
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        // Column scrollable plutôt que LazyColumn : le contenu est fixe et court
        // (4 sections), pas de listage dynamique. Avec une Column, StaggeredAppear
        // joue son anim une seule fois au mount et ne risque jamais de se rejouer
        // sur un recyclage d'item — contrat respecté.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Hero card : trophée animé + message Shreddy ───
            StaggeredAppear(index = 0, delayPerItemMs = 0) {
                Card(colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.15f))) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CelebrationTrophy(size = 80.dp, accentColor = NeonGreen)
                        Text(
                            stringResource(R.string.summary_title),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            textAlign = TextAlign.Center
                        )

                        // Message personnalisé de Shreddy
                        val shreddyMsg = sessionManager.lastShreddyMessage
                        if (shreddyMsg.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = OrangeVibrant.copy(alpha = 0.12f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
                                    }
                                }
                                Text(
                                    shreddyMsg,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start,
                                    lineHeight = 22.sp
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.summary_default_message),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // ─── Stats animées (compteurs qui se déroulent) ───
            StaggeredAppear(index = 1, delayPerItemMs = 200) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.summary_stats_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            AnimatedStatColumn(
                                icon = Icons.Default.Timer,
                                targetValue = duration,
                                label = stringResource(R.string.summary_stat_duration),
                                formatter = { fmtDuration(it.toLong(), ctx) }
                            )
                            AnimatedStatColumn(
                                icon = Icons.Default.FitnessCenter,
                                targetValue = totalSets,
                                label = stringResource(R.string.summary_stat_sets)
                            )
                            AnimatedStatColumn(
                                icon = Icons.Default.MonitorWeight,
                                targetValue = totalVolume,
                                label = stringResource(R.string.summary_stat_volume),
                                formatter = { fmtVolume(it.toDouble(), ctx) }
                            )
                        }

                        HorizontalDivider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            AnimatedStatColumn(
                                icon = Icons.Default.RepeatOne,
                                targetValue = totalReps,
                                label = stringResource(R.string.summary_stat_reps)
                            )
                            AnimatedStatColumn(
                                icon = Icons.Default.Pause,
                                targetValue = totalRest,
                                label = stringResource(R.string.summary_stat_rest),
                                formatter = { fmtDuration(it.toLong(), ctx) }
                            )
                            if (skipped > 0) {
                                AnimatedStatColumn(
                                    icon = Icons.Default.SkipNext,
                                    targetValue = skipped,
                                    label = stringResource(R.string.summary_stat_skipped)
                                )
                            }
                        }
                    }
                }
            }

            // ─── CTAs secondaires ───
            StaggeredAppear(index = 2, delayPerItemMs = 200) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShredButton(
                        onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Stats.route) },
                        variant = ShredButtonVariant.Tertiary,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Icon(Icons.Default.Analytics, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.summary_cta_view_stats), fontWeight = FontWeight.SemiBold)
                    }
                    ShredButton(
                        onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.WorkoutGenerator.route) },
                        variant = ShredButtonVariant.Tertiary,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.summary_cta_new_session), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ─── Réserver la prochaine séance (Calendar integration) ───
            StaggeredAppear(index = 3, delayPerItemMs = 200) {
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
                            Text(
                                stringResource(R.string.summary_book_next_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stringResource(R.string.summary_book_next_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = OrangeVibrant)
                    }
                }
            }

            // Spacer pour laisser de la place au bouton "TERMINER" en overlay bottom
            Spacer(Modifier.height(80.dp))
        }

        // Bouton terminer (en overlay bottom)
        Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.BottomCenter) {
            ShredButton(
                onClick = {
                    navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Home.route) {
                        popUpTo(com.shredcoach.app.presentation.navigation.Screen.Home.route) { inclusive = false }
                    }
                },
                variant = ShredButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(60.dp)
            ) {
                Icon(Icons.Default.Home, null, Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.summary_finish_button), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * StatColumn avec compteur animé : la valeur se déroule de 0 vers la cible
 * sur 1.5s. À utiliser pour les hero stats (post-séance, achievements).
 */
@Composable
private fun AnimatedStatColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    targetValue: Number,
    label: String,
    formatter: (Float) -> String = { it.toInt().toString() }
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(32.dp), tint = OrangeVibrant)
        AnimatedCounter(
            targetValue = targetValue,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            formatter = formatter
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

private fun fmtDuration(seconds: Long, ctx: android.content.Context): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return when {
        h > 0 -> ctx.getString(R.string.fmt_duration_h_m, h.toInt(), m.toInt())
        m > 0 -> ctx.getString(R.string.fmt_duration_m, m.toInt())
        else -> ctx.getString(R.string.fmt_duration_s, seconds.toInt())
    }
}

private fun fmtVolume(v: Double, ctx: android.content.Context): String = when {
    v >= 1000 -> ctx.getString(R.string.fmt_volume_k, (v / 1000).toFloat())
    else -> ctx.getString(R.string.fmt_volume_kg, v.toFloat())
}
