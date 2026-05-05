package com.shredcoach.app.presentation.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Extension : applique les "tabular numerals" (alias OpenType `tnum`) à un
 * [TextStyle]. Avec tnum, tous les chiffres rendent à la **même largeur**,
 * indépendamment de leur forme — "0" est aussi large que "1", "11:11" rend
 * à la pixel près identique à "00:00".
 *
 * **Pourquoi c'est important** : tout texte qui contient un compteur ou un
 * timestamp qui change en live (chrono séance, calories du jour, kg soulevés
 * à l'instant) sans tnum produit un "shimmer" horizontal à chaque tick parce
 * que la largeur des chiffres varie en proportionnel. Sur une bannière ou
 * une card, ce reflow rend l'app non-premium.
 *
 * Usage :
 * ```
 * Text(
 *     "12:34",
 *     style = MaterialTheme.typography.titleLarge.tabularNum(),
 *     ...
 * )
 * ```
 */
fun TextStyle.tabularNum(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Composable d'affichage d'un nombre / chrono / compteur **stable en largeur** :
 * combine tnum + maxLines=1 + softWrap=false. À utiliser partout où un texte
 * numérique est susceptible de changer fréquemment (chronos, stats live,
 * calories, poids).
 *
 * Pour un texte qui doit aussi avoir une largeur min réservée (ex: chrono qui
 * passe de "MM:SS" à "H:MM:SS"), passer `Modifier.widthIn(min = X.dp)` via
 * `modifier`.
 */
@Composable
fun StableNumText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        style = style.tabularNum(),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}
