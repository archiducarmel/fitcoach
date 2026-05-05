package com.shredcoach.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * ShredPalette — Jeu de couleurs sémantique de l'app.
 *
 * Chaque palette définit :
 *   - primary   : couleur principale (CTA, highlights, accents forts)
 *   - secondary : couleur secondaire (accent complémentaire, gradients)
 *   - success   : vert sémantique (complétion, PR, positivité) — toujours vert
 *   - warning   : jaune sémantique (attention, avertissement) — toujours jaune
 *   - info      : bleu sémantique (info neutre) — toujours bleu
 *   - error     : rouge sémantique (erreur, suppression) — toujours rouge
 *
 * Chaque palette a une variante `light` et `dark` pour adapter la luminosité.
 *
 * Design principle (Apple): seules `primary` et `secondary` changent entre thèmes.
 * Les couleurs sémantiques (success/warning/error) restent constantes pour préserver
 * la lecture cognitive des states.
 */
data class ShredPalette(
    val key: String,
    val displayName: String,
    val icon: String,

    // Couleurs de marque (varient selon la palette)
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,

    // Couleurs sémantiques (stables entre palettes, légère variation light/dark)
    val success: Color,
    val warning: Color,
    val info: Color,
    val error: Color,

    // Couleurs de fond (Slate-like, adaptées à la palette)
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,

    val isDark: Boolean
)

// ═══════════════════════════════════════════════════════════════
// PALETTES — couples (light + dark) par thème
// ═══════════════════════════════════════════════════════════════

object ShredPalettes {

    // ── Backgrounds partagés (charte Slate, cohérent Apple-like) ──
    private val DarkBg = Color(0xFF0F172A)        // Slate 900
    private val DarkSurf = Color(0xFF1E293B)      // Slate 800
    private val DarkSurfV = Color(0xFF334155)     // Slate 700
    private val DarkOn = Color(0xFFF8FAFC)
    private val DarkOnV = Color(0xFFCBD5E1)

    private val LightBg = Color(0xFFF8FAFC)       // Slate 50
    private val LightSurf = Color(0xFFFFFFFF)
    private val LightSurfV = Color(0xFFF1F5F9)    // Slate 100
    private val LightOn = Color(0xFF0F172A)
    private val LightOnV = Color(0xFF64748B)

    // Sémantique stable (très légers ajustements light/dark pour contraste)
    private val Success = Color(0xFF10B981)
    private val Warning = Color(0xFFFBBF24)
    private val Info = Color(0xFF3B82F6)
    private val ErrorL = Color(0xFFDC2626)
    private val ErrorD = Color(0xFFEF4444)

    // Couleurs sémantiques exposées (utilisées en mode "system" / Material You,
    // où les couleurs viennent du wallpaper mais on garde notre sémantique stable).
    internal val SemanticSuccess: Color = Success
    internal val SemanticWarning: Color = Warning
    internal val SemanticInfo: Color = Info
    internal val SemanticErrorLight: Color = ErrorL
    internal val SemanticErrorDark: Color = ErrorD

    // ───────────────────────────────────────────────
    // 1. SUNSET 🔥 — Orange & Red (palette par défaut)
    // ───────────────────────────────────────────────
    val SunsetLight = ShredPalette(
        key = "sunset", displayName = "Sunset", icon = "🔥",
        primary = Color(0xFFFF6B35),       // Orange vibrant
        primaryContainer = Color(0xFFFFE4D6),
        secondary = Color(0xFFE63946),     // Red passion
        secondaryContainer = Color(0xFFFFE0E4),
        success = Success, warning = Warning, info = Info, error = ErrorL,
        background = LightBg, surface = LightSurf, surfaceVariant = LightSurfV,
        onBackground = LightOn, onSurface = LightOn, onSurfaceVariant = LightOnV,
        isDark = false
    )
    val SunsetDark = SunsetLight.copy(
        primary = Color(0xFFFF7E47),
        primaryContainer = Color(0xFF662410),
        secondary = Color(0xFFFF5A6E),
        secondaryContainer = Color(0xFF5C0F16),
        error = ErrorD,
        background = DarkBg, surface = DarkSurf, surfaceVariant = DarkSurfV,
        onBackground = DarkOn, onSurface = DarkOn, onSurfaceVariant = DarkOnV,
        isDark = true
    )

    // ───────────────────────────────────────────────
    // 2. OCEAN 🌊 — Blue & Cyan (calm, pro, focus)
    // ───────────────────────────────────────────────
    val OceanLight = ShredPalette(
        key = "ocean", displayName = "Ocean", icon = "🌊",
        primary = Color(0xFF0284C7),       // Sky 600
        primaryContainer = Color(0xFFDCF4FF),
        secondary = Color(0xFF06B6D4),     // Cyan 500
        secondaryContainer = Color(0xFFCFFAFE),
        success = Success, warning = Warning, info = Info, error = ErrorL,
        background = LightBg, surface = LightSurf, surfaceVariant = LightSurfV,
        onBackground = LightOn, onSurface = LightOn, onSurfaceVariant = LightOnV,
        isDark = false
    )
    val OceanDark = OceanLight.copy(
        primary = Color(0xFF38BDF8),       // Sky 400
        primaryContainer = Color(0xFF0C3F5E),
        secondary = Color(0xFF22D3EE),     // Cyan 400
        secondaryContainer = Color(0xFF0C4A5E),
        error = ErrorD,
        background = DarkBg, surface = DarkSurf, surfaceVariant = DarkSurfV,
        onBackground = DarkOn, onSurface = DarkOn, onSurfaceVariant = DarkOnV,
        isDark = true
    )

    // ───────────────────────────────────────────────
    // 3. FOREST 🌲 — Emerald & Lime (health, growth)
    // ───────────────────────────────────────────────
    val ForestLight = ShredPalette(
        key = "forest", displayName = "Forest", icon = "🌲",
        primary = Color(0xFF059669),       // Emerald 600
        primaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF65A30D),     // Lime 600
        secondaryContainer = Color(0xFFECFCCB),
        success = Success, warning = Warning, info = Info, error = ErrorL,
        background = LightBg, surface = LightSurf, surfaceVariant = LightSurfV,
        onBackground = LightOn, onSurface = LightOn, onSurfaceVariant = LightOnV,
        isDark = false
    )
    val ForestDark = ForestLight.copy(
        primary = Color(0xFF34D399),       // Emerald 400
        primaryContainer = Color(0xFF064E3B),
        secondary = Color(0xFFA3E635),     // Lime 400
        secondaryContainer = Color(0xFF3F6212),
        error = ErrorD,
        background = DarkBg, surface = DarkSurf, surfaceVariant = DarkSurfV,
        onBackground = DarkOn, onSurface = DarkOn, onSurfaceVariant = DarkOnV,
        isDark = true
    )

    // ───────────────────────────────────────────────
    // 4. ROYAL 👑 — Purple & Pink (creative, bold)
    // ───────────────────────────────────────────────
    val RoyalLight = ShredPalette(
        key = "royal", displayName = "Royal", icon = "👑",
        primary = Color(0xFF7C3AED),       // Violet 600
        primaryContainer = Color(0xFFEDE9FE),
        secondary = Color(0xFFDB2777),     // Pink 600
        secondaryContainer = Color(0xFFFCE7F3),
        success = Success, warning = Warning, info = Info, error = ErrorL,
        background = LightBg, surface = LightSurf, surfaceVariant = LightSurfV,
        onBackground = LightOn, onSurface = LightOn, onSurfaceVariant = LightOnV,
        isDark = false
    )
    val RoyalDark = RoyalLight.copy(
        primary = Color(0xFFA78BFA),       // Violet 400
        primaryContainer = Color(0xFF4C1D95),
        secondary = Color(0xFFF472B6),     // Pink 400
        secondaryContainer = Color(0xFF831843),
        error = ErrorD,
        background = DarkBg, surface = DarkSurf, surfaceVariant = DarkSurfV,
        onBackground = DarkOn, onSurface = DarkOn, onSurfaceVariant = DarkOnV,
        isDark = true
    )

    // ───────────────────────────────────────────────
    // 5. GRAPHITE ⚫ — Minimalist charcoal (premium, focus)
    // ───────────────────────────────────────────────
    val GraphiteLight = ShredPalette(
        key = "graphite", displayName = "Graphite", icon = "⚫",
        primary = Color(0xFF27272A),       // Zinc 800
        primaryContainer = Color(0xFFE4E4E7),
        secondary = Color(0xFF71717A),     // Zinc 500
        secondaryContainer = Color(0xFFF4F4F5),
        success = Success, warning = Warning, info = Info, error = ErrorL,
        background = LightBg, surface = LightSurf, surfaceVariant = LightSurfV,
        onBackground = LightOn, onSurface = LightOn, onSurfaceVariant = LightOnV,
        isDark = false
    )
    val GraphiteDark = GraphiteLight.copy(
        primary = Color(0xFFFAFAFA),       // Zinc 50 — inverse contrasté
        primaryContainer = Color(0xFF3F3F46),
        secondary = Color(0xFFA1A1AA),     // Zinc 400
        secondaryContainer = Color(0xFF27272A),
        error = ErrorD,
        background = DarkBg, surface = DarkSurf, surfaceVariant = DarkSurfV,
        onBackground = DarkOn, onSurface = DarkOn, onSurfaceVariant = DarkOnV,
        isDark = true
    )

    // ── API ──

    /** Liste ordonnée des palettes disponibles (pour l'UI de sélection). */
    val all: List<Pair<ShredPalette, ShredPalette>> = listOf(
        SunsetLight to SunsetDark,
        OceanLight to OceanDark,
        ForestLight to ForestDark,
        RoyalLight to RoyalDark,
        GraphiteLight to GraphiteDark
    )

    /** Résout une palette depuis sa clé + l'état dark. Fallback = Sunset. */
    fun resolve(key: String?, isDark: Boolean): ShredPalette {
        val (light, dark) = all.firstOrNull { it.first.key == key } ?: (SunsetLight to SunsetDark)
        return if (isDark) dark else light
    }

    val Default: ShredPalette = SunsetLight
}

// ═══════════════════════════════════════════════════════════════
// CompositionLocal — propage la palette active à toute la hiérarchie
// ═══════════════════════════════════════════════════════════════

val LocalShredPalette = staticCompositionLocalOf<ShredPalette> { ShredPalettes.Default }

/**
 * Accesseur pratique pour le design system :
 *   ShredTheme.palette.primary
 *   ShredTheme.spacing.md
 *   ShredTheme.elevation.level2
 */
object ShredTheme {
    val palette: ShredPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalShredPalette.current

    val spacing: ShredSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalShredSpacing.current

    val elevation: ShredElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalShredElevation.current
}
