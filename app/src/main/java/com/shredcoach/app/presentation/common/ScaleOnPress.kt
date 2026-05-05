package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Modifier qui anime un *scale-down* subtil pendant que l'élément est pressé,
 * puis revient à l'échelle normale avec un spring quand on relâche.
 *
 * Pattern emprunté à iOS — chaque tap renvoie un signal visuel "ton doigt est
 * bien pris en compte", ce qui sépare une app "fonctionnelle" d'une app
 * "qui répond". Compose ne fait rien de tel par défaut (juste le ripple).
 *
 * Utilisation typique avec un `Card` ou `Surface` cliquable :
 *   ```
 *   val interactionSource = remember { MutableInteractionSource() }
 *   Card(
 *       onClick = onClick,
 *       interactionSource = interactionSource,
 *       modifier = Modifier.scaleOnPress(interactionSource)
 *   ) { ... }
 *   ```
 *
 * @param interactionSource L'[InteractionSource] partagée avec le composant
 *                          cliquable. Indispensable pour que le press soit
 *                          détecté.
 * @param pressedScale Échelle pendant le press. Default = 0.96f (subtle).
 *                     0.92 pour plus dramatique sur les boutons CTA.
 */
@Composable
fun Modifier.scaleOnPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scale-on-press"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
