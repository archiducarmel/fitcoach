package com.shredcoach.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
 * Construit une ShredPalette synthétique à partir d'un ColorScheme Material You
 * (dynamicLight/DarkColorScheme). Permet à `ShredTheme.palette.xxx` de continuer
 * à fonctionner partout dans l'app, même en mode "system".
 *
 * Les couleurs sémantiques (success/warning/info) restent constantes — c'est
 * voulu : un check vert doit rester vert même si le wallpaper est rose.
 */
private fun synthesizePaletteFromDynamic(scheme: ColorScheme, isDark: Boolean): ShredPalette {
    val errorColor: Color =
        if (isDark) ShredPalettes.SemanticErrorDark else ShredPalettes.SemanticErrorLight
    return ShredPalette(
        key = SYSTEM_PALETTE_KEY,
        displayName = "Système",
        icon = "✨",
        primary = scheme.primary,
        primaryContainer = scheme.primaryContainer,
        secondary = scheme.secondary,
        secondaryContainer = scheme.secondaryContainer,
        success = ShredPalettes.SemanticSuccess,
        warning = ShredPalettes.SemanticWarning,
        info = ShredPalettes.SemanticInfo,
        error = errorColor,
        background = scheme.background,
        surface = scheme.surface,
        surfaceVariant = scheme.surfaceVariant,
        onBackground = scheme.onBackground,
        onSurface = scheme.onSurface,
        onSurfaceVariant = scheme.onSurfaceVariant,
        isDark = isDark
    )
}

/** Clé spéciale pour activer le mode Material You (dynamic color, Android 12+). */
const val SYSTEM_PALETTE_KEY: String = "system"

/**
 * Thème principal ShredCoach.
 *
 * @param darkTheme true pour forcer le mode sombre, false pour le clair.
 *                  Default = suit le setting système.
 * @param paletteKey Clé de la palette à utiliser :
 *                   - "sunset" (défaut), "ocean", "forest", "royal", "graphite"
 *                   - "system" → Material You (Android 12+), fallback sunset si <12
 */
@Composable
fun ShredCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteKey: String = "sunset",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamicColor =
        paletteKey == SYSTEM_PALETTE_KEY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        useDynamicColor -> dynamicLightColorScheme(context)
        else -> {
            val palette = ShredPalettes.resolve(paletteKey, darkTheme)
            colorSchemeFromPalette(palette)
        }
    }

    val effectivePalette = remember(paletteKey, darkTheme, useDynamicColor) {
        if (useDynamicColor) {
            synthesizePaletteFromDynamic(colorScheme, darkTheme)
        } else {
            // Fallback : si "system" demandé mais API < 31, on retombe sur Sunset.
            val resolveKey = if (paletteKey == SYSTEM_PALETTE_KEY) "sunset" else paletteKey
            ShredPalettes.resolve(resolveKey, darkTheme)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Propager palette + tokens (spacing/elevation) via CompositionLocal pour que
    // tout l'arbre Compose ait accès à `ShredTheme.palette/spacing/elevation`.
    CompositionLocalProvider(
        LocalShredPalette provides effectivePalette,
        LocalShredSpacing provides ShredSpacing(),
        LocalShredElevation provides ShredElevation()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
