package com.shredcoach.app.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Compteur animé : démarre à 0 et se déroule vers `targetValue` sur la
 * durée donnée. À utiliser pour les hero numbers (stats post-séance,
 * achievements) — ça crée un effet "le chiffre se construit sous tes yeux"
 * qui valorise l'effort.
 *
 * Implémentation : on utilise un [Animatable] dont la valeur initiale est 0,
 * et on déclenche `animateTo(target)` dans un [LaunchedEffect] keyed sur la
 * cible. C'est le seul pattern qui garantit que l'anim joue dès la première
 * composition (contrairement à `animateFloatAsState` qui s'initialise à la
 * cible).
 *
 * @param targetValue Valeur finale à atteindre.
 * @param durationMillis Durée totale de l'animation. Default = 1500ms.
 * @param formatter Fonction de formatting de la valeur courante (ex:
 *                  `{ it.toInt().toString() }`, `{ "%.1f kg".format(it) }`).
 */
@Composable
fun AnimatedCounter(
    targetValue: Number,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1500,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    formatter: (Float) -> String = { it.toInt().toString() }
) {
    val target = targetValue.toFloat()
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
    }

    // **Stabilité largeur durant l'animation** :
    //  - tnum : tous les chiffres ont la même largeur → pas de "shimmer"
    //    horizontal pendant que la valeur interpole de 0 vers la cible.
    //  - maxLines=1 + softWrap=false : empêche tout reflow vertical si la
    //    valeur intermédiaire produit un texte transitoirement plus long.
    // Note : si le formatter change drastiquement de largeur (ex: "5kg"→"1.2M"),
    // le caller doit en plus poser un `widthIn(min=...)` sur le modifier.
    Text(
        text = formatter(animatable.value),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}
