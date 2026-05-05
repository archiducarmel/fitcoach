package com.shredcoach.app.presentation.common

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Wrapper unifié pour les animations de récompense.
 *
 * **Stratégie hybride** :
 *  1. Tente de charger `assets/lottie/{asset}.json` au premier rendu.
 *  2. Si l'asset est présent et parsable → animation Lottie native (riche).
 *  3. Sinon → fallback Compose-natif via [CelebrationTrophy] avec icône
 *     adaptée au type de récompense.
 *
 * **Pourquoi ce design** : on veut que la feature soit fonctionnelle dès maintenant
 * (sans bloquer sur la fourniture des .json par le designer/LottieFiles), tout en
 * permettant un upgrade à la qualité Lottie quand les fichiers seront prêts —
 * sans toucher aux call sites. Les .json se déposent dans `app/src/main/assets/lottie/`.
 *
 * **Catalogue d'assets attendus** (téléchargeables sur lottiefiles.com avec ces
 * mots-clefs, gratuits ou payants au choix) :
 *  - `pr_celebration.json` — confettis + médaille (PR battu)
 *  - `streak_milestone.json` — flamme grandissante (streak palier atteint)
 *  - `workout_complete.json` — checkmark + paillettes (séance terminée)
 *  - `goal_reached.json` — fusée + étoiles (objectif poids atteint)
 *  - `body_scan_progress.json` — courbe descendante (progression mesurée)
 *  - `motivation.json` — flammes ou éclair (générique motivation)
 */
@Composable
fun LottieReward(
    type: RewardType,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val context = LocalContext.current
    val assetExists = remember(type) { hasLottieAsset(context, type.assetName) }

    if (assetExists) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.Asset("lottie/${type.assetName}.json")
        )
        Box(modifier, contentAlignment = Alignment.Center) {
            LottieAnimation(
                composition = composition,
                iterations = type.iterations,
                modifier = Modifier.size(size),
            )
        }
    } else {
        FallbackReward(type = type, size = size, modifier = modifier)
    }
}

/**
 * Fallback compose-natif quand le .json Lottie n'est pas (encore) fourni.
 * Réutilise [CelebrationTrophy] avec une icône thématique. Le rendu est moins
 * spectaculaire qu'un Lottie mais reste premium grâce au spring overshoot
 * + halo pulsant.
 */
@Composable
private fun FallbackReward(
    type: RewardType,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val (icon, color) = type.fallbackIconAndColor()
    when (type) {
        RewardType.PrCelebration,
        RewardType.WorkoutComplete,
        RewardType.GoalReached -> {
            CelebrationTrophy(size = size, modifier = modifier, accentColor = color, icon = icon)
        }
        RewardType.StreakMilestone,
        RewardType.Motivation,
        RewardType.BodyScanProgress -> {
            FlameLikeReward(icon = icon, color = color, size = size, modifier = modifier)
        }
    }
}

/**
 * Variation flame-like : icône qui flicker en boucle (alpha + scale légers).
 * Sépare le rendu pour les types "streak/motivation" qui ne sont pas des
 * "trophées" mais des "feux qui durent" — pas de halo gros, juste un battement.
 */
@Composable
private fun FlameLikeReward(
    icon: ImageVector,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "flame")
    val flicker by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "flicker",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = flicker; scaleY = flicker },
            tint = color,
        )
    }
}

/** Vérifie l'existence d'un asset Lottie sans crasher si le dossier n'existe pas. */
private fun hasLottieAsset(context: Context, assetName: String): Boolean = try {
    context.assets.list("lottie")?.contains("$assetName.json") == true
} catch (_: Exception) {
    false
}

/**
 * Catégorie de récompense. Mappe vers un fichier asset Lottie + un fallback
 * Compose. Iterations contrôle la lecture (1 = one-shot, INFINITE = boucle).
 */
enum class RewardType(
    val assetName: String,
    val iterations: Int,
) {
    /** PR battu : 1RM record écrasé. One-shot, célébration ponctuelle. */
    PrCelebration(assetName = "pr_celebration", iterations = 1),

    /** Streak qui atteint un palier (3/7/14/30/60/100j). One-shot. */
    StreakMilestone(assetName = "streak_milestone", iterations = 1),

    /** Séance terminée. One-shot, court (snackbar/dialog). */
    WorkoutComplete(assetName = "workout_complete", iterations = 1),

    /** Objectif poids atteint. One-shot grand format. */
    GoalReached(assetName = "goal_reached", iterations = 1),

    /** Progression body-scan détectée. Boucle car affiché dans une carte. */
    BodyScanProgress(
        assetName = "body_scan_progress",
        iterations = LottieConstants.IterateForever,
    ),

    /** Motivation générique. Boucle (badge en arrière-plan). */
    Motivation(assetName = "motivation", iterations = LottieConstants.IterateForever);

    fun fallbackIconAndColor(): Pair<ImageVector, Color> = when (this) {
        PrCelebration   -> Icons.Filled.MilitaryTech to Color(0xFFFFB300)
        StreakMilestone -> Icons.Filled.LocalFireDepartment to Color(0xFFFF6F00)
        WorkoutComplete -> Icons.Filled.WorkspacePremium to Color(0xFF00C853)
        GoalReached     -> Icons.Filled.RocketLaunch to Color(0xFF536DFE)
        BodyScanProgress -> Icons.Filled.AutoAwesome to Color(0xFF00B0FF)
        Motivation      -> Icons.Filled.EmojiEvents to Color(0xFFFFB300)
    }
}
