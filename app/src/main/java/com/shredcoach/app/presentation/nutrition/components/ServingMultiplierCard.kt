package com.shredcoach.app.presentation.nutrition.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shredcoach.app.R
import com.shredcoach.app.domain.nutrition.MealScanModifierMath
import com.shredcoach.app.presentation.theme.OrangeVibrant
import com.shredcoach.app.presentation.util.hapticClick

/**
 * Card "J'en ai repris" — stepper de portions.
 *
 * Spec produit :
 *  - Chaque scan démarre à ×1 (le repas initial).
 *  - Tap "J'en ai repris" → +1 portion (×2, puis ×3, ×4…). La référence reste
 *    TOUJOURS le scan d'origine ; les portions s'accumulent additivement.
 *  - Tap "+ ½" → +0.5 (demi-portion).
 *  - Tap "−" → −0.5 (undo en cas d'erreur), clampé à 1.0 minimum.
 *
 * UX premium :
 *  - Compteur visuel "×N" en grand, animé (scale léger à chaque update).
 *  - Bouton primary "J'en ai repris" prend toute la largeur quand neutre,
 *    laisse de la place à "−" quand multiplier > 1.
 *  - Live preview "X kcal réels" qui se met à jour à chaque tap.
 *  - Haptic léger à chaque changement.
 *
 * @param current multiplier actuel (servingMultiplier du scan).
 * @param baseCalories calories 1x originales (pour le live preview).
 * @param leftoverCalories calories à déduire (intégrées au preview).
 * @param onMultiplierChange callback déclenché à chaque ajustement (déjà clampé).
 */
@Composable
fun ServingMultiplierCard(
    current: Float,
    baseCalories: Int,
    leftoverCalories: Int,
    onMultiplierChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val effectiveCalories = run {
        val factor = MealScanModifierMath.effectiveFactor(current, baseCalories, leftoverCalories)
        (baseCalories * factor).toInt().coerceAtLeast(0)
    }
    val hasExtra = current > 1f

    // Le multiplicateur "saute" légèrement à chaque changement (feedback visuel).
    val badgeScale by animateFloatAsState(
        targetValue = if (hasExtra) 1.0f else 0.95f,
        animationSpec = tween(durationMillis = 220),
        label = "multiplier_badge_scale",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Header : icône + titre + compteur "×N" ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.RestaurantMenu,
                    null,
                    Modifier.size(20.dp),
                    tint = OrangeVibrant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.meal_modifier_seconds_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.meal_modifier_seconds_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                // Compteur "×N" — affiché toujours pour repère visuel
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (hasExtra) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.scale(badgeScale),
                ) {
                    Text(
                        text = formatMultiplier(current),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = if (hasExtra) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // ── Live preview : "X kcal réels" ──
            AnimatedVisibility(
                visible = hasExtra || leftoverCalories > 0,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    text = stringResource(R.string.meal_modifier_effective_calories, effectiveCalories),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeVibrant,
                )
            }

            // ── Actions : [−] [+ ½] [+ portion (primary)] ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Decrement : visible uniquement si l'user a déjà incrémenté.
                // Decrement de 0.5 — permet de descendre du ×2 vers ×1.5 puis ×1.
                AnimatedVisibility(visible = hasExtra) {
                    OutlinedIconButton(
                        onClick = {
                            hapticClick(context)
                            // Smart decrement : annule la dernière action (−1 si entier, −½ sinon).
                            onMultiplierChange(MealScanModifierMath.smartDecrement(current))
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            stringResource(R.string.meal_modifier_undo_cd),
                            Modifier.size(20.dp),
                        )
                    }
                }
                // + Demi-portion (secondary)
                FilledTonalButton(
                    onClick = {
                        hapticClick(context)
                        onMultiplierChange(
                            MealScanModifierMath.clampMultiplier(current + 0.5f)
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.meal_modifier_add_half),
                        fontWeight = FontWeight.Bold,
                    )
                }
                // + Une portion (primary, prend l'espace restant)
                Button(
                    onClick = {
                        hapticClick(context)
                        onMultiplierChange(
                            MealScanModifierMath.clampMultiplier(current + 1f)
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrangeVibrant,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.meal_modifier_add_full),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Format multiplicateur : `×1`, `×1.5`, `×2`, `×2.5`, `×3` … Affiche `.5` quand
 * fractionnaire, format entier sinon. tnum géré côté Text pour alignement.
 */
internal fun formatMultiplier(value: Float): String {
    val rounded = (Math.round(value * 2f) / 2f) // arrondi au demi le plus proche
    val asInt = rounded.toInt()
    return if (asInt.toFloat() == rounded) "×$asInt"
    else "×%.1f".format(rounded)
}
