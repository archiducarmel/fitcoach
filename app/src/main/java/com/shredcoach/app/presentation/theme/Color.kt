package com.shredcoach.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// COULEURS PALETTE-AWARE (composable getters backed by LocalShredPalette)
//
// Ces propriétés changent automatiquement selon la palette active.
// Elles doivent être lues dans un contexte @Composable.
//
// Règle : seules OrangeVibrant/RedPassion/DeepBlue/VioletDark sont palette-aware
// (correspondent à primary/secondary de la palette). Les couleurs sémantiques
// (NeonGreen, BrightYellow, ErrorRed, etc.) restent constantes pour préserver
// la lecture cognitive des states.
// ═══════════════════════════════════════════════════════════════

/** Couleur primaire de la palette active (ex: orange pour Sunset, bleu pour Ocean). */
val OrangeVibrant: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShredPalette.current.primary

/** Couleur secondaire de la palette active (utilisée pour les gradients et accents complémentaires). */
val RedPassion: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShredPalette.current.secondary

/** Couleur secondaire (alias) — utilisée comme secondaire "profondeur" sur certains écrans. */
val DeepBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShredPalette.current.secondary

val VioletDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalShredPalette.current.secondaryContainer

// ═══════════════════════════════════════════════════════════════
// COULEURS SÉMANTIQUES (constantes — ne changent pas avec la palette)
//
// Vert = succès / complétion / PR
// Jaune = attention / warning
// Bleu = info neutre
// Rouge = erreur / suppression
// ═══════════════════════════════════════════════════════════════

val NeonGreen = Color(0xFF10B981)      // Success, progression — toujours vert
val BrightYellow = Color(0xFFFBBF24)   // Attention, warnings — toujours jaune

// Exercise Variant Colors (statiques — liés à la sémantique de l'exercice)
val MachineBlue = Color(0xFF3B82F6)
val WeightsRed = Color(0xFFEF4444)
val BodyweightGreen = Color(0xFF10B981)
val IsolationAmber = Color(0xFFF59E0B)

// ═══════════════════════════════════════════════════════════════
// COULEURS DE FOND (statiques — adaptées light/dark par ShredCoachTheme)
// Ces constantes sont utilisées UNIQUEMENT par le thème Material3 et
// des composants neutres. Les screens ne devraient pas y recourir directement.
// ═══════════════════════════════════════════════════════════════

val DarkBackground = Color(0xFF0F172A)      // Slate 900
val DarkSurface = Color(0xFF1E293B)         // Slate 800
val DarkSurfaceVariant = Color(0xFF334155)  // Slate 700

val LightBackground = Color(0xFFF8FAFC)     // Slate 50
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9) // Slate 100

val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFFCBD5E1)   // Slate 300
val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF64748B)  // Slate 500

// Fonctionnelles
val SuccessGreen = Color(0xFF22C55E)
val ErrorRed = Color(0xFFEF4444)
val WarningYellow = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)

// Chart Colors (statiques — lisibilité stable)
val ChartColor1 = Color(0xFFFF6B35)
val ChartColor2 = Color(0xFF3B82F6)
val ChartColor3 = Color(0xFF10B981)
val ChartColor4 = Color(0xFFF59E0B)
val ChartColor5 = Color(0xFF8B5CF6)
