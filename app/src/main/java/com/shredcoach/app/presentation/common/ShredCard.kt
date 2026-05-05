package com.shredcoach.app.presentation.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shredcoach.app.presentation.theme.ShredTheme

/**
 * Variantes de card Material 3 :
 *
 *  - **Elevated** : drop shadow + tonal overlay (default — pour la plupart des
 *    contenus listés sur un background neutre)
 *  - **Outlined** : pas d'elevation, juste une fine bordure (pour densités élevées
 *    où le shadow ferait du bruit visuel)
 *  - **Filled**   : fond surfaceVariant, pas d'elevation (pour les contenus
 *    secondaires sur un écran dense)
 */
enum class ShredCardVariant { Elevated, Outlined, Filled }

private val ShredCardShape = RoundedCornerShape(16.dp)
private val ShredCardDefaultPadding = PaddingValues(16.dp)

/**
 * Card unifiée de l'app — corner 16dp, elevation cohérente via tokens, padding
 * intérieur par défaut, optionnellement clickable avec scale-on-press feedback.
 *
 * Pourquoi un wrapper plutôt que des Card M3 directes :
 *  - corner radius unifié (M3 default = 12dp, on préfère 16dp = plus moderne)
 *  - elevation depuis nos tokens (`ShredTheme.elevation.level2` par défaut)
 *  - padding intérieur préfixé (16dp) — on passe direct au content
 *  - une variante = un point d'API unique, pas trois composants distincts
 *  - **scale-on-press automatique** quand `onClick != null` (Apple-style feedback)
 */
@Composable
fun ShredCard(
    modifier: Modifier = Modifier,
    variant: ShredCardVariant = ShredCardVariant.Elevated,
    onClick: (() -> Unit)? = null,
    shape: Shape = ShredCardShape,
    elevation: Dp? = null,
    contentPadding: PaddingValues = ShredCardDefaultPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedElevation = elevation ?: ShredTheme.elevation.level2
    val innerContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }

    // Quand cliquable, on partage un interactionSource entre Card + scaleOnPress
    // pour que le scale se déclenche au press.
    val interactionSource = remember { MutableInteractionSource() }
    val pressableModifier = if (onClick != null) {
        modifier.scaleOnPress(interactionSource)
    } else {
        modifier
    }

    when (variant) {
        ShredCardVariant.Elevated -> {
            if (onClick != null) {
                ElevatedCard(
                    onClick = onClick,
                    modifier = pressableModifier,
                    shape = shape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = resolvedElevation),
                    interactionSource = interactionSource
                ) { innerContent() }
            } else {
                ElevatedCard(
                    modifier = pressableModifier,
                    shape = shape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = resolvedElevation)
                ) { innerContent() }
            }
        }
        ShredCardVariant.Outlined -> {
            if (onClick != null) {
                OutlinedCard(
                    onClick = onClick,
                    modifier = pressableModifier,
                    shape = shape,
                    interactionSource = interactionSource
                ) { innerContent() }
            } else {
                OutlinedCard(
                    modifier = pressableModifier,
                    shape = shape
                ) { innerContent() }
            }
        }
        ShredCardVariant.Filled -> {
            if (onClick != null) {
                Card(
                    onClick = onClick,
                    modifier = pressableModifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    interactionSource = interactionSource
                ) { innerContent() }
            } else {
                Card(
                    modifier = pressableModifier,
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { innerContent() }
            }
        }
    }
}
