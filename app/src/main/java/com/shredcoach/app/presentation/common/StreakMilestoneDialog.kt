package com.shredcoach.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog de célébration quand l'utilisateur passe un palier de streak.
 *
 * **Trigger** : la home / dashboard détecte qu'un milestone vient d'être
 * atteint (cf. [com.shredcoach.app.domain.streak.StreakService.nextMilestoneToCelebrate])
 * et n'a pas encore été célébré (cf. [com.shredcoach.app.domain.streak.StreakMilestoneStore]).
 * À la fermeture (onDismiss), le caller marque le milestone comme célébré pour
 * éviter de re-popper.
 *
 * **UX** : modal avec animation Lottie centrale + texte motivationnel adapté
 * au palier. Le call-to-action ferme la dialog ("Continue !"). Pas de bouton
 * "skip" — un palier mérite d'être célébré pleinement.
 */
@Composable
fun StreakMilestoneDialog(
    days: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LottieReward(type = RewardType.StreakMilestone, size = 140.dp)
                }

                Text(
                    text = milestoneTitle(days),
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = milestoneSubtitle(days),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = milestoneTagline(days),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = "Continue !",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun milestoneTitle(days: Int): String = when (days) {
    3   -> "3 jours d'affilée"
    7   -> "1 semaine clean"
    14  -> "2 semaines de feu"
    30  -> "30 jours de constance"
    60  -> "60 jours, machine"
    100 -> "100 JOURS · LÉGENDE"
    else -> "$days jours d'affilée"
}

private fun milestoneSubtitle(days: Int): String = when (days) {
    3   -> "Le démarrage est la partie la plus dure. Tu l'as franchi."
    7   -> "Une semaine entière sans rupture — tu sais ce que c'est, tenir un cap."
    14  -> "Deux semaines, ce n'est plus un test, c'est une habitude qui s'installe."
    30  -> "Un mois sans louper. Ton corps a déjà changé, même si tu ne le vois pas encore."
    60  -> "Soixante jours. Tu as quitté la zone des amateurs."
    100 -> "Trois chiffres. Un palier que <1% des utilisateurs Strava atteignent."
    else -> "$days jours sans louper. Continue."
}

private fun milestoneTagline(days: Int): String = when {
    days >= 100 -> "Tu écris ta légende."
    days >= 60  -> "Discipline > motivation."
    days >= 30  -> "Le futur toi te remerciera."
    days >= 14  -> "On verrouille la suite."
    days >= 7   -> "Cap sur le palier 14j."
    else        -> "Cap sur la première semaine."
}
