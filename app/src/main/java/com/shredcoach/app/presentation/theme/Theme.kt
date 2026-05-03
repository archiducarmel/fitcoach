package com.shredcoach.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Construit un Material3 ColorScheme à partir d'une ShredPalette.
 * Les couleurs Material sont mappées pour que les composants standards (buttons, cards…)
 * utilisent automatiquement la palette sélectionnée.
 */
private fun colorSchemeFromPalette(p: ShredPalette): ColorScheme {
    return if (p.isDark) {
        darkColorScheme(
            primary = p.primary,
            onPrimary = p.onSurface,
            primaryContainer = p.primaryContainer,
            onPrimaryContainer = p.onSurface,

            secondary = p.secondary,
            onSecondary = p.onSurface,
            secondaryContainer = p.secondaryContainer,
            onSecondaryContainer = p.onSurface,

            tertiary = p.success,
            onTertiary = p.background,
            tertiaryContainer = p.warning,
            onTertiaryContainer = p.background,

            background = p.background,
            onBackground = p.onBackground,

            surface = p.surface,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.onSurfaceVariant,

            error = p.error,
            onError = p.onSurface
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = p.surface,
            primaryContainer = p.primaryContainer,
            onPrimaryContainer = p.onBackground,

            secondary = p.secondary,
            onSecondary = p.surface,
            secondaryContainer = p.secondaryContainer,
            onSecondaryContainer = p.onBackground,

            tertiary = p.success,
            onTertiary = p.surface,
            tertiaryContainer = p.warning,
            onTertiaryContainer = p.background,

            background = p.background,
            onBackground = p.onBackground,

            surface = p.surface,
            onSurface = p.onSurface,
            surfaceVariant = p.surfaceVariant,
            onSurfaceVariant = p.onSurfaceVariant,

            error = p.error,
            onError = p.surface
        )
    }
}

/**
 * Thème principal ShredCoach.
 *
 * @param darkTheme true pour forcer le mode sombre, false pour le clair.
 *                  Default = suit le setting système.
 * @param paletteKey Clé de la palette à utiliser ("sunset", "ocean", "forest", "royal", "graphite").
 *                   Default = "sunset" (palette orange historique).
 */
@Composable
fun ShredCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteKey: String = "sunset",
    content: @Composable () -> Unit
) {
    val palette = remember(paletteKey, darkTheme) {
        ShredPalettes.resolve(paletteKey, darkTheme)
    }
    val colorScheme = remember(palette) { colorSchemeFromPalette(palette) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Propager la palette via CompositionLocal pour que OrangeVibrant, RedPassion, etc.
    // (composable getters dans Color.kt) lisent la bonne valeur partout dans l'app.
    CompositionLocalProvider(LocalShredPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
