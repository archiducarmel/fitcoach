package com.shredcoach.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shredcoach.app.R
import com.shredcoach.app.data.remote.GeminiRetryBus
import com.shredcoach.app.data.remote.GeminiRetryEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Banner global qui s'affiche en bas de l'écran quand [GeminiRetryBus] émet
 * un événement. Branché UNE FOIS dans MainActivity au-dessus du NavHost, il
 * couvre tous les écrans qui déclenchent un appel Gemini (MealScanner,
 * BodyScanner, GymScan, GlucoseEntry, Coach Shreddy, etc.).
 *
 * **Comportement** :
 *  - `Retrying` : snackbar avec spinner + humour, persiste tant que d'autres
 *    `Retrying` arrivent (l'user voit "ça mouline" en continu).
 *  - `Recovered` : snackbar court (1.5s) "Ça repart ✓" puis disparaît.
 *  - `Failed` : snackbar 4s "Gemini surchargé, réessaie".
 *
 * **Choix UX** :
 *  - On pioche un humour aléatoire parmi 3 variantes localisées → l'user qui
 *    rencontre 5 overloads dans sa session ne voit pas 5× la même blague.
 *  - Position bottom + insetting navigation bars : compatible scaffolds des
 *    écrans sous-jacents sans recouvrir leur propre snackbar host.
 *  - Pas de bouton dismissable : l'user n'a rien à faire, ça s'auto-clôt.
 *
 * **Robustesse** :
 *  - L'observation se fait dans un `LaunchedEffect(Unit)` ancré au cycle de
 *    vie de MainActivity → pas de leak, ré-abonnement transparent après
 *    recreate (changement de thème, rotation).
 *  - `SharedFlow.replay = 0` côté bus → un événement raté pendant que le
 *    user était dans une autre app est… raté, par design. Mieux que de
 *    réafficher un snackbar obsolète après un retour à l'app.
 */
@Composable
fun GeminiRetryBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // État de la banner. `null` = caché.
    var banner by remember { mutableStateOf<BannerState?>(null) }

    LaunchedEffect(Unit) {
        GeminiRetryBus.events.collect { event ->
            banner = when (event) {
                is GeminiRetryEvent.Retrying -> BannerState.Retrying(
                    text = pickHumor(ctx),
                    attempt = event.attempt,
                    maxAttempts = event.maxAttempts,
                )
                GeminiRetryEvent.Recovered -> BannerState.Recovered(
                    text = ctx.getString(R.string.gemini_retry_recovered)
                )
                GeminiRetryEvent.Failed -> BannerState.Failed(
                    text = ctx.getString(R.string.gemini_retry_failed)
                )
            }
            // Auto-dismiss pour Recovered/Failed. Pour Retrying, on laisse
            // poser jusqu'au prochain événement (Recovered, Failed, ou
            // nouveau Retrying qui rafraîchit la durée).
            val current = banner
            when (current) {
                is BannerState.Recovered -> {
                    scope.launch {
                        delay(1500)
                        if (banner === current) banner = null
                    }
                }
                is BannerState.Failed -> {
                    scope.launch {
                        delay(4000)
                        if (banner === current) banner = null
                    }
                }
                is BannerState.Retrying -> {
                    // Si aucun nouvel événement n'arrive sous 12s (cumul max retry
                    // ~10s + marge), on assume que le caller a abandonné et on
                    // cache la banner pour ne pas rester collé indéfiniment.
                    scope.launch {
                        delay(12_000)
                        if (banner === current) banner = null
                    }
                }
                null -> Unit
            }
        }
    }

    val state = banner ?: return

    Box(modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp)),
            color = when (state) {
                is BannerState.Retrying -> MaterialTheme.colorScheme.surfaceVariant
                is BannerState.Recovered -> Color(0xFF065F46)  // emerald 800 — succès médical
                is BannerState.Failed -> MaterialTheme.colorScheme.errorContainer
            },
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp).background(Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is BannerState.Retrying -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    is BannerState.Recovered -> {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    is BannerState.Failed -> {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private sealed class BannerState {
    abstract val text: String
    data class Retrying(override val text: String, val attempt: Int, val maxAttempts: Int) : BannerState()
    data class Recovered(override val text: String) : BannerState()
    data class Failed(override val text: String) : BannerState()
}

/**
 * Pioche un humour parmi les 3 variantes localisées. Évite que l'user voie
 * toujours le même message s'il a plusieurs overloads dans une session.
 */
private fun pickHumor(ctx: android.content.Context): String {
    val pool = listOf(
        R.string.gemini_retry_humor_1,
        R.string.gemini_retry_humor_2,
        R.string.gemini_retry_humor_3,
    )
    return ctx.getString(pool.random())
}
