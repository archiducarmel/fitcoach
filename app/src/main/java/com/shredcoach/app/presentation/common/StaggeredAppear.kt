package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Anime l'apparition d'un contenu avec un fade+slide-up et un délai indexé,
 * pour créer l'effet de cascade sur une liste fixe (Column, Row).
 *
 * Pourquoi ce pattern : sur une vue avec N éléments, faire apparaître chaque
 * élément avec un léger décalage (50ms entre chaque) donne un sentiment de
 * dynamisme et d'attention au détail — c'est très visible sur les écrans
 * d'accueil ou de stats où l'utilisateur ouvre l'app et voit le contenu se
 * "déplier" sous ses yeux.
 *
 * Limitation : à n'utiliser que dans des Column/Row fixes. Dans une
 * LazyColumn, les items sont recyclés et l'animation rejouerait au scroll
 * — utiliser plutôt [androidx.compose.foundation.lazy.LazyItemScope.animateItemPlacement].
 *
 * @param index Position de l'élément (0-based). Détermine le délai.
 * @param delayPerItemMs Délai en ms entre chaque apparition. Default = 50ms.
 * @param initialOffsetDp Décalage vertical initial (descendant). Default = 16dp.
 * @param durationMillis Durée de l'animation. Default = 400ms.
 * @param visible Set false pour cacher (utile pour réanimation conditionnelle).
 */
@Composable
fun StaggeredAppear(
    index: Int = 0,
    delayPerItemMs: Int = 50,
    initialOffsetDp: Int = 16,
    durationMillis: Int = 400,
    visible: Boolean = true,
    content: @Composable () -> Unit
) {
    var hasAppeared by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            delay((index * delayPerItemMs).toLong())
            hasAppeared = true
        } else {
            hasAppeared = false
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "stagger-alpha"
    )
    val offsetY by animateIntAsState(
        targetValue = if (hasAppeared) 0 else initialOffsetDp,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "stagger-offset"
    )

    Box(
        modifier = Modifier
            .alpha(alpha)
            .offset(y = offsetY.dp)
    ) {
        content()
    }
}
