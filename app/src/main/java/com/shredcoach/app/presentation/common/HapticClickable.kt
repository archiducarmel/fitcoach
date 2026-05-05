package com.shredcoach.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/**
 * Modifier `clickable` qui déclenche un haptic feedback subtil avant l'action.
 *
 * Pourquoi : `clickable` standard de Compose ne fait pas de haptic par défaut
 * sur Android (contrairement à iOS). Sur une app premium, chaque tap doit
 * répondre tactilement — c'est ce qui sépare une app "fonctionnelle" d'une
 * app "qui se sent bien".
 *
 * @param hapticType Type de feedback. Default = TextHandleMove (subtle).
 *                   Utilisez LongPress pour les actions destructives.
 * @param enableHaptic Pour désactiver ponctuellement (ex: tap répétitif sur
 *                     un slider) tout en gardant le clickable.
 */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    enableHaptic: Boolean = true,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        if (enableHaptic) haptic.performHapticFeedback(hapticType)
        onClick()
    }
}

/**
 * Variante qui évite la double allocation d'`indication` quand on contrôle
 * déjà un `MutableInteractionSource` à l'extérieur (ex: ripple custom).
 */
@Composable
fun Modifier.hapticClickable(
    interactionSource: MutableInteractionSource,
    indication: androidx.compose.foundation.Indication?,
    enabled: Boolean = true,
    enableHaptic: Boolean = true,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role
    ) {
        if (enableHaptic) haptic.performHapticFeedback(hapticType)
        onClick()
    }
}
