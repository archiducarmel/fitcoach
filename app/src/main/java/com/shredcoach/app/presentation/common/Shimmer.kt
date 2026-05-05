package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer — placeholder animé pour les états de chargement.
 *
 * Pourquoi remplacer `CircularProgressIndicator` par du shimmer :
 *  - Le shimmer **anticipe la forme** du contenu qui va arriver, donnant
 *    l'illusion d'une UX déjà aboutie pendant le chargement
 *  - Pas de "saut" visuel quand les données arrivent
 *  - Pattern standard sur les apps premium (LinkedIn, Facebook, Instagram…)
 *
 * Utilisation :
 * ```
 * if (isLoading) {
 *     Column {
 *         repeat(3) { ShimmerBox(Modifier.fillMaxWidth().height(80.dp)) }
 *     }
 * } else {
 *     // contenu réel
 * }
 * ```
 *
 * Ou directement appliqué sur un Modifier existant : `Modifier.shimmer()`.
 */

/**
 * Modifier shimmer : un linear gradient animé qui se déplace de gauche à droite.
 * À appliquer sur tout composable de taille fixe (Box, Card, etc.).
 *
 * @param highlightColor Couleur du "flash" qui se déplace. Default = surface.
 * @param baseColor Couleur de base du shimmer. Default = surfaceVariant.
 * @param durationMillis Durée d'un cycle complet. Default = 1200ms.
 */
@Composable
fun Modifier.shimmer(
    highlightColor: Color = MaterialTheme.colorScheme.surface,
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    durationMillis: Int = 1200
): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )

    val brush = remember(translateAnim, highlightColor, baseColor) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(translateAnim * 200f, translateAnim * 200f),
            end = Offset(translateAnim * 200f + 400f, translateAnim * 200f + 400f)
        )
    }

    return this.background(brush)
}

/**
 * Boîte shimmer prête à l'emploi — un rectangle aux coins arrondis qui shimmer.
 * Pour les placeholders de cards, images, blocs.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .shimmer()
    )
}

/**
 * Ligne shimmer prête à l'emploi — pour simuler un bloc de texte chargé.
 * Hauteur par défaut = 14dp (≈ ligne de bodyMedium).
 *
 * @param width Largeur du bloc. Default = 120dp.
 * @param height Hauteur du bloc. Default = 14dp.
 */
@Composable
fun ShimmerText(
    width: Dp = 120.dp,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier.width(width).height(height),
        shape = RoundedCornerShape(4.dp)
    )
}

/**
 * Cercle shimmer — pour les placeholders d'avatars/icônes circulaires.
 */
@Composable
fun ShimmerCircle(
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier.size(size),
        shape = androidx.compose.foundation.shape.CircleShape
    )
}
