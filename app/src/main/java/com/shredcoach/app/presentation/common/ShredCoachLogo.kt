package com.shredcoach.app.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shredcoach.app.R
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Logo ShredCoach — flamme + haltère, monochrome.
 *
 * @param size taille carrée du logo (par défaut 48dp, 96dp pour l'onboarding, 24dp pour les top bars)
 * @param tint couleur de rendu (par défaut OrangeVibrant)
 */
@Composable
fun ShredCoachLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = OrangeVibrant
) {
    Image(
        painter = painterResource(id = R.drawable.ic_shredcoach_monogram),
        contentDescription = "ShredCoach",
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}
