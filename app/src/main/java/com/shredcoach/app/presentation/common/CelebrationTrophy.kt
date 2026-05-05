package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Trophée célébration — entrée scale-in spring overshoot + halo pulsant.
 *
 * Composable qui transforme un moment de validation banal (icône statique)
 * en moment d'émotion. À utiliser pour les hero positifs : fin de séance,
 * achievement débloqué, record battu.
 *
 * Animation :
 *  1. Scale 0 → 1.15 → 1.0 avec un spring overshoot (effet "boing")
 *  2. Halo pulsant en boucle infinie autour (alpha + scale)
 *
 * @param size Taille du trophée central. Default = 80dp.
 * @param accentColor Couleur du halo + tint du trophée. Default = doré.
 * @param icon Icône à utiliser. Default = EmojiEvents (trophée).
 */
@Composable
fun CelebrationTrophy(
    size: Dp = 80.dp,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFFFB300), // doré
    icon: ImageVector = Icons.Default.EmojiEvents
) {
    // 1. Scale d'entrée avec spring overshoot
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // 2. Halo pulsant (infini)
    val infinite = rememberInfiniteTransition(label = "trophy-halo")
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo-alpha"
    )
    val haloScale by infinite.animateFloat(
        initialValue = 1.15f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo-scale"
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        // Halo arrière (pulse infini)
        Box(
            Modifier
                .size(size * 1.6f)
                .graphicsLayer {
                    scaleX = haloScale * scale.value
                    scaleY = haloScale * scale.value
                    alpha = haloAlpha * scale.value
                }
                .clip(CircleShape)
                .background(accentColor)
        )
        // Cercle moyen
        Box(
            Modifier
                .size(size * 1.25f)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = scale.value
                }
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f))
        )
        // Trophée
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            tint = accentColor
        )
    }
}
