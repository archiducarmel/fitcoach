package com.shredcoach.app.presentation.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shredcoach.app.R
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * État affichable de la carte jeûne nocturne. Reste minimal et "stupide" :
 * la composable se charge du calcul (live ticking) à partir des deux
 * timestamps. Permet à NutritionViewModel et HomeViewModel de la nourrir
 * sans dupliquer la logique de durée.
 */
@Immutable
data class NightFastingDisplay(
    /** Datetime du DERNIER repas de J-1. `null` = pas de repas hier. */
    val lastMealAt: LocalDateTime?,
    /** Datetime du PREMIER repas de J. `null` = pas encore mangé aujourd'hui. */
    val firstMealAt: LocalDateTime?,
    /** True si la date affichée est aujourd'hui — autorise le ticker live. */
    val isToday: Boolean,
)

private val TARGET_HOURS = 16.0  // 16-8 = pratique standard

// Palette progressive selon où l'user en est de son jeûne nocturne.
private val ColorEarly = Color(0xFF6B7280)   // < 12h : neutre
private val ColorBuilding = Color(0xFFF59E0B) // 12-14h : orange — phase active
private val ColorRespected = Color(0xFF10B981) // 14-16h : vert — bénéfice métabolique
private val ColorOptimal = Color(0xFF6366F1)   // ≥ 16h : indigo — 16-8 atteint

private fun colorForHours(hours: Double): Color = when {
    hours >= 16.0 -> ColorOptimal
    hours >= 14.0 -> ColorRespected
    hours >= 12.0 -> ColorBuilding
    else -> ColorEarly
}

/**
 * Carte premium "Jeûne nocturne".
 *
 * **Logique** :
 *  - Aucun repas hier → placeholder pédagogique (ne pas afficher de durée
 *    qui n'a aucun sens, pousser l'user à logguer son dîner).
 *  - Aucun repas aujourd'hui ET on regarde aujourd'hui → ticker LIVE qui
 *    affiche le temps écoulé depuis le dernier repas d'hier (rafraîchi
 *    toutes les 30s, suffisant pour une horloge "temps écoulé").
 *  - Premier repas du jour pris → fenêtre figée (durée fixe entre dernier
 *    repas hier et premier repas aujourd'hui).
 *  - Date passée sans repas → placeholder ("aucun repas ce jour").
 *
 * **Choix design** :
 *  - Hero number `Xh YY` en displayMedium tnum (lecture immédiate).
 *  - Barre de progression vs 16h-target : couleur évolutive (gris → orange
 *    → vert → indigo) — l'user voit où il en est sans avoir besoin de lire.
 *  - Deux chips temporelles encadrant un `→` : ancrage hier ("21h30 hier")
 *    et référence du jour (heure courante en mode live OU 1er repas en mode
 *    figé). Pulse rouge si live → l'user sait que ça tique.
 *  - Aucune dépendance externe (pas de lib chart) — Canvas + Box.
 */
@Composable
fun NightFastingCard(data: NightFastingDisplay, modifier: Modifier = Modifier) {
    val lastMealAt = data.lastMealAt
    if (lastMealAt == null) {
        FastingPlaceholderCard(
            message = stringResource(R.string.nutrition_fasting_no_yesterday),
            modifier = modifier,
        )
        return
    }
    if (data.firstMealAt == null && !data.isToday) {
        // Date passée sans repas du tout — la durée n'aurait pas de référence valide.
        FastingPlaceholderCard(
            message = stringResource(R.string.nutrition_fasting_no_today),
            modifier = modifier,
        )
        return
    }

    val isOngoing = data.firstMealAt == null && data.isToday

    // Tick toutes les 30s en mode live — assez réactif pour le ressenti
    // "temps écoulé", assez espacé pour ne pas brûler de batterie.
    val now by produceState(initialValue = LocalDateTime.now(), key1 = isOngoing) {
        while (isOngoing) {
            value = LocalDateTime.now()
            delay(30_000L)
        }
    }
    val reference: LocalDateTime = data.firstMealAt ?: now

    val durationSec = Duration.between(lastMealAt, reference).seconds.coerceAtLeast(0L)
    val hours = durationSec / 3600.0
    val hoursInt = (durationSec / 3600L).toInt()
    val minsInt = ((durationSec % 3600L) / 60L).toInt()

    val accent = colorForHours(hours)
    val targetProgress = (hours / TARGET_HOURS).coerceIn(0.0, 1.0).toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 600),
        label = "fastingProgress",
    )

    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.10f),
                            accent.copy(alpha = 0.02f),
                        ),
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // ─── Header : icône + titre + indicateur live ───
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.NightsStay, null,
                        Modifier.size(18.dp), tint = accent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.nutrition_fasting_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (isOngoing) LivePulse(accent)
            }

            Spacer(Modifier.height(14.dp))

            // ─── Hero : Xh YY ───
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${hoursInt}h",
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = accent,
                    maxLines = 1,
                )
                Text(
                    minsInt.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Verdict succinct pour positionner l'user vs le 16-8.
                Text(
                    text = stringResource(verdictResForHours(hours)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── Barre vers 16h ───
            FastingProgressBar(progress = animatedProgress, accent = accent)

            Spacer(Modifier.height(6.dp))

            // ─── Repère 16h ───
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "0h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Text(
                    stringResource(R.string.nutrition_fasting_target_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }

            Spacer(Modifier.height(14.dp))

            // ─── Chips temporelles ─── 21:30 hier  →  14:35 maintenant
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeChip(
                    icon = Icons.Default.NightsStay,
                    time = lastMealAt.toLocalTime().format(timeFmt),
                    sub = stringResource(R.string.nutrition_fasting_chip_yesterday),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowRightAlt, null,
                    Modifier.size(20.dp).padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                if (isOngoing) {
                    TimeChip(
                        icon = Icons.Default.AccessTime,
                        time = now.toLocalTime().format(timeFmt),
                        sub = stringResource(R.string.nutrition_fasting_chip_now),
                        accent = accent,
                    )
                } else {
                    TimeChip(
                        icon = Icons.Default.Restaurant,
                        time = reference.toLocalTime().format(timeFmt),
                        sub = stringResource(R.string.nutrition_fasting_chip_first_meal),
                        accent = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun FastingProgressBar(progress: Float, accent: Color) {
    Box(
        Modifier.fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        Box(
            Modifier.fillMaxHeight()
                .fillMaxWidth(progress)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.7f), accent),
                    )
                ),
        )
    }
}

@Composable
private fun TimeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    time: String,
    sub: String,
    accent: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = accent)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                time,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LivePulse(color: Color) {
    val infinite = rememberInfiniteTransition(label = "fastingLive")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(R.string.nutrition_fasting_live),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun FastingPlaceholderCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.NightsStay, null,
                Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.nutrition_fasting_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private fun verdictResForHours(hours: Double): Int = when {
    hours >= 16.0 -> R.string.nutrition_fasting_state_optimal
    hours >= 14.0 -> R.string.nutrition_fasting_state_good
    hours >= 12.0 -> R.string.nutrition_fasting_state_building
    else -> R.string.nutrition_fasting_state_early
}
