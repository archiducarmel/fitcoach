package com.shredcoach.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens — sémantique stricte à utiliser partout dans l'app
 * **au lieu de** hardcoder des `Color(0xFF…)` au cas par cas.
 *
 * **Convention 4 couleurs (Apple-like)** :
 *   - [action]        : CTA principal, accent forte attention (orange par défaut, varie avec la palette)
 *   - [progression]   : succès / streak / progression / PR (vert sémantique stable)
 *   - [nutrition]     : nutrition / hydratation / info neutre (bleu sémantique stable)
 *   - [warning]       : at-risk / dépassement / erreur (rouge sémantique stable)
 *
 * **Pourquoi ne pas tout faire passer par MaterialTheme.colorScheme** : Material 3
 * a des slots primary/secondary/tertiary qui ne mappent pas 1:1 sur notre sémantique
 * fitness. On préfère des accessors explicites pour éviter l'ambiguïté ("primary"
 * = action ou progression ?). Les couleurs proviennent de [ShredTheme.palette]
 * pour rester palette-aware, mais avec un nommage métier.
 */
object SemanticColors {
    /**
     * Couleur d'action — utilisée pour le CTA principal (Generate, Resume, FAB).
     * Suit la palette active (orange en Sunset, bleu en Ocean, etc.).
     */
    val action: Color
        @Composable
        @ReadOnlyComposable
        get() = ShredTheme.palette.primary

    /**
     * Vert progression — streak, PR, deltas positifs, "complété".
     * Stable entre palettes (toujours vert) pour préserver la lecture cognitive.
     */
    val progression: Color
        @Composable
        @ReadOnlyComposable
        get() = ShredTheme.palette.success

    /**
     * Bleu nutrition / hydratation / info neutre. Stable entre palettes.
     */
    val nutrition: Color
        @Composable
        @ReadOnlyComposable
        get() = ShredTheme.palette.info

    /**
     * Rouge warning — at-risk, dépassement de cible, erreur. Stable entre palettes.
     * **À ne pas utiliser** pour des éléments décoratifs ou des collections (ex:
     * favoris). Réserver strictement aux signaux négatifs.
     */
    val warning: Color
        @Composable
        @ReadOnlyComposable
        get() = ShredTheme.palette.error
}
