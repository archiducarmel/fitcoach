package com.shredcoach.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de spacing centralisés — UNE source de vérité pour les marges/paddings de l'app.
 *
 * Pourquoi : avant Phase A, chaque écran utilisait des `8.dp`, `12.dp`, `16.dp`, `20.dp`
 * en dur, ce qui créait des micro-incohérences invisibles à l'œil mais cumulatives
 * (deux cards côte à côte avec des marges légèrement différentes = friction visuelle).
 *
 * Échelle 4 dp-based (Material 3 standard, multiples de 4) :
 *   - xs   = 4dp   — micro spacing (icône ↔ texte adjacent)
 *   - sm   = 8dp   — spacing serré (entre items proches)
 *   - md   = 16dp  — spacing par défaut (padding cards, marges écran)
 *   - lg   = 24dp  — spacing aéré (entre sections d'un écran)
 *   - xl   = 32dp  — spacing hero (marge haute d'un titre principal)
 *   - xxl  = 48dp  — spacing dramatique (entre blocs distincts)
 *   - xxxl = 64dp  — spacing très lâche (header d'écran d'accueil)
 *
 * Usage :
 *   ```
 *   Modifier.padding(ShredTheme.spacing.md)
 *   Spacer(Modifier.height(ShredTheme.spacing.lg))
 *   ```
 */
@Immutable
data class ShredSpacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp
)

val LocalShredSpacing = staticCompositionLocalOf { ShredSpacing() }
