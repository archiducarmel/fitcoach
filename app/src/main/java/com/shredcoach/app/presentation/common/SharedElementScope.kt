package com.shredcoach.app.presentation.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * CompositionLocal pour propager le [SharedTransitionScope] depuis
 * `SharedTransitionLayout` vers toutes les destinations du NavHost — sans
 * forcer chaque écran à exposer un paramètre supplémentaire.
 *
 * `null` par défaut : si le layout n'est pas wrappé, les modifiers
 * `sharedElementOptIn` / `sharedBoundsOptIn` deviennent no-op au lieu de
 * planter — utile pour les @Preview et les tests.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * CompositionLocal pour propager l'[AnimatedVisibilityScope] de la
 * destination NavHost courante. Re-fourni à chaque `composable {}` du
 * NavHost via `CompositionLocalProvider(LocalAnimatedVisibilityScope provides this)`.
 */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Modifier qui fait morpher cet élément vers son homologue (même `key`)
 * sur la destination suivante du NavHost.
 *
 * À utiliser quand l'élément a la **même apparence** sur les deux écrans
 * (ex: une image qui change juste de taille). Pour des éléments dont le
 * style change (texte qui passe de titleSmall à headlineSmall), préférer
 * [sharedBoundsOptIn] qui anime les bounds + cross-fade.
 *
 * Safe-fallback : si le scope n'est pas fourni (Preview, test), retourne
 * le modifier inchangé sans crasher.
 *
 * @param key Identifiant unique de l'élément. Doit être identique sur les
 *            deux écrans. Inclure un id stable (ex: "exercise-image-42").
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOptIn(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedElementOptIn.sharedElement(
            state = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope
        )
    }
}

/**
 * Modifier qui anime les bounds (position + taille) entre deux composables
 * de keys identiques sur des écrans successifs, avec un cross-fade entre
 * les deux contenus.
 *
 * À utiliser quand l'élément change visuellement entre les écrans (ex: un
 * titre qui passe d'une typo à une autre, ou un texte qui devient un
 * heading).
 *
 * Safe-fallback : si le scope n'est pas fourni, retourne le modifier
 * inchangé.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoundsOptIn(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animScope = LocalAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedBoundsOptIn.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope
        )
    }
}
