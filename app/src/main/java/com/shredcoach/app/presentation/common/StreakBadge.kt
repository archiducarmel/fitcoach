package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.shredcoach.app.domain.streak.StreakService

/**
 * Badge compact qui affiche le streak courant.
 *
 * **Comportement visuel** :
 * - Flame icon au flicker permanent (animation discrète, tactile sur Wear OS et home).
 * - Couleur évolue en fonction du palier atteint (gradient de chaleur) — un
 *   streak de 100j ne s'affiche pas comme un streak de 3j.
 * - "X jours" en gras, "vers Y" en discret pour gamifier le prochain palier.
 *
 * **Pas de logique métier** : la valeur de [days] est passée par le ViewModel.
 * Le badge ne lit pas la DB, ne calcule rien — il rend.
 */
@Composable
fun StreakBadge(
    days: Int,
    modifier: Modifier = Modifier,
    showNextMilestone: Boolean = true,
    isAtRisk: Boolean = false,
) {
    if (days <= 0) return  // pas de streak → pas de badge

    val palette = streakPalette(days)
    val infinite = rememberInfiniteTransition(label = "streak-badge")
    val flicker by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "streak-flicker",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(palette.start, palette.end)
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = palette.icon,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { scaleX = flicker; scaleY = flicker },
        )
        Text(
            text = "$days j",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        if (isAtRisk) {
            Spacer(Modifier.width(2.dp))
            Text(
                text = "⚠",
                color = Color.White,
                fontSize = 12.sp,
            )
        }
        if (showNextMilestone) {
            val next = StreakService.MILESTONES.firstOrNull { it > days }
            if (next != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "/$next",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/**
 * Variant héro du badge — gros chiffre, halo, à utiliser comme element premier
 * sur la home ou en début de dashboard.
 */
@Composable
fun StreakHeroBadge(
    days: Int,
    bestDays: Int,
    modifier: Modifier = Modifier,
) {
    if (days <= 0) return
    val palette = streakPalette(days)
    val isPersonalBest = days > 0 && days == bestDays

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.start, palette.end)
                )
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Flame animée (réutilise LottieReward pour la cohérence des assets futurs).
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            LottieReward(type = RewardType.StreakMilestone, size = 48.dp)
        }
        androidx.compose.foundation.layout.Column {
            Text(
                text = "Streak $days j",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
            )
            Text(
                text = if (isPersonalBest) "🏆 record perso" else "best $bestDays j",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private data class StreakPalette(val start: Color, val end: Color, val icon: Color)

/**
 * Gradient de chaleur : plus le streak est long, plus le badge "chauffe".
 * Inspiré des paliers d'engagement Duolingo/Strava.
 */
private fun streakPalette(days: Int): StreakPalette = when {
    days >= 100 -> StreakPalette(Color(0xFFD50000), Color(0xFF6A1B9A), Color(0xFFFFEB3B))   // pourpre incandescent
    days >= 30  -> StreakPalette(Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFD54F))   // braise
    days >= 14  -> StreakPalette(Color(0xFFFF6F00), Color(0xFFFFA000), Color(0xFFFFE082))   // ambre
    days >= 7   -> StreakPalette(Color(0xFFFB8C00), Color(0xFFFFB300), Color(0xFFFFF59D))   // chaud
    days >= 3   -> StreakPalette(Color(0xFFFFA000), Color(0xFFFFC107), Color.White)         // tiède
    else        -> StreakPalette(Color(0xFFFF7043), Color(0xFFFFAB40), Color.White)         // démarrage
}
