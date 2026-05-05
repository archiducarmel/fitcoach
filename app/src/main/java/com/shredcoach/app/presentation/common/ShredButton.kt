package com.shredcoach.app.presentation.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Variantes de bouton ShredCoach — couvre les 5 niveaux d'emphase Material 3.
 *
 *  - **Primary**     : CTA principal d'un écran (« Démarrer la séance »)
 *  - **Secondary**   : action complémentaire de moyenne importance (« Voir détails »)
 *  - **Tertiary**    : action peu importante mais visible (« Annuler »)
 *  - **Destructive** : action destructive (« Supprimer », « Reset »)
 *  - **Text**        : action très discrète, dans une liste ou un footer
 */
enum class ShredButtonVariant { Primary, Secondary, Tertiary, Destructive, Text }

private val ShredButtonShape = RoundedCornerShape(14.dp)
private val ShredButtonMinHeight = 48.dp

/**
 * Bouton unifié de l'app — toutes les variantes M3 derrière une seule API.
 *
 * Particularités :
 *  - Hauteur min 48dp (touch target accessibilité)
 *  - Corner radius 14dp (cohérence visuelle, plus généreux que M3 par défaut)
 *  - **Haptic feedback** intégré (subtle sur action standard, fort sur Destructive)
 *  - **Loading state** intégré : remplace le contenu par un spinner sans changer
 *    la taille du bouton (évite le jump visual)
 *
 * Slot-based : passez ce que vous voulez en `content`.
 * Pour le cas standard texte (+ icône), utilisez l'overload [ShredButton] qui
 * prend `text` et `leadingIcon`.
 */
@Composable
fun ShredButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShredButtonVariant = ShredButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    enableHaptic: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val effectivelyEnabled = enabled && !isLoading

    val onClickInternal: () -> Unit = {
        if (enableHaptic) {
            val type = if (variant == ShredButtonVariant.Destructive) {
                HapticFeedbackType.LongPress
            } else {
                HapticFeedbackType.TextHandleMove
            }
            haptic.performHapticFeedback(type)
        }
        onClick()
    }

    val resolvedContent: @Composable RowScope.() -> Unit = {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current
            )
        } else {
            content()
        }
    }

    val sizedModifier = modifier.heightIn(min = ShredButtonMinHeight)

    when (variant) {
        ShredButtonVariant.Primary -> Button(
            onClick = onClickInternal,
            modifier = sizedModifier,
            enabled = effectivelyEnabled,
            shape = ShredButtonShape,
            contentPadding = contentPadding,
            content = resolvedContent
        )
        ShredButtonVariant.Secondary -> FilledTonalButton(
            onClick = onClickInternal,
            modifier = sizedModifier,
            enabled = effectivelyEnabled,
            shape = ShredButtonShape,
            contentPadding = contentPadding,
            content = resolvedContent
        )
        ShredButtonVariant.Tertiary -> OutlinedButton(
            onClick = onClickInternal,
            modifier = sizedModifier,
            enabled = effectivelyEnabled,
            shape = ShredButtonShape,
            contentPadding = contentPadding,
            content = resolvedContent
        )
        ShredButtonVariant.Destructive -> Button(
            onClick = onClickInternal,
            modifier = sizedModifier,
            enabled = effectivelyEnabled,
            shape = ShredButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            contentPadding = contentPadding,
            content = resolvedContent
        )
        ShredButtonVariant.Text -> TextButton(
            onClick = onClickInternal,
            modifier = sizedModifier,
            enabled = effectivelyEnabled,
            shape = ShredButtonShape,
            contentPadding = contentPadding,
            content = resolvedContent
        )
    }
}

/**
 * Overload pratique pour le cas standard : un texte (+ optionnellement une icône
 * de tête). 90% des appels utilisent celle-ci ; les 10% qui veulent un layout
 * custom passent par l'overload slot-based.
 */
@Composable
fun ShredButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShredButtonVariant = ShredButtonVariant.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    enableHaptic: Boolean = true
) {
    ShredButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        isLoading = isLoading,
        enableHaptic = enableHaptic
    ) {
        if (leadingIcon != null && !isLoading) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
