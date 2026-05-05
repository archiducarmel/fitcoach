package com.shredcoach.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens d'elevation — Material 3 utilise 5 niveaux conventionnels.
 *
 * En Material 3, l'elevation se traduit visuellement par :
 *  - **Light theme** : drop shadow + tint subtle
 *  - **Dark theme** : tonal overlay (surfaceContainer plus clair)
 *
 * Niveaux :
 *  - none   = 0dp  — flat sur le background (pas de séparation)
 *  - level1 = 1dp  — subtle (cards inactives, separators)
 *  - level2 = 3dp  — raised (cards interactives, app bars)
 *  - level3 = 6dp  — hover / pressed states
 *  - level4 = 8dp  — modals, bottom sheets (Material 3 default for sheets)
 *  - level5 = 12dp — navigation drawer, top of stack (FAB pressed)
 *
 * Usage :
 *   ```
 *   Card(elevation = CardDefaults.cardElevation(defaultElevation = ShredTheme.elevation.level2))
 *   ```
 */
@Immutable
data class ShredElevation(
    val none: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 12.dp
)

val LocalShredElevation = staticCompositionLocalOf { ShredElevation() }
