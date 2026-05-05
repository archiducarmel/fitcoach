package com.shredcoach.app.presentation.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shredcoach.app.presentation.home.ResumableSession
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * CTA "Reprendre la séance" — remplace "Générer une séance" en première position
 * quand un log non-complété de moins de 24h existe.
 *
 * **Hiérarchie visuelle** : design plus saillant que le CTA Générer (dégradé
 * vif + halo) car la friction d'abandon est forte et reprendre est plus
 * urgent que générer (l'utilisateur a déjà investi du temps).
 *
 * **Pourquoi pas une simple Snackbar** : la session interrompue est l'élément
 * le plus actionable de la home — elle mérite un slot premier-plan, pas un
 * artefact transitoire.
 */
@Composable
fun ResumeSessionCard(
    session: ResumableSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11y = remember(session) {
        val exoLabel = if (session.isFreestyle) {
            "${session.completedExercises} exercices terminés"
        } else {
            "${session.completedExercises} exercices sur ${session.totalExercises} terminés"
        }
        "Reprendre ta séance ${session.workoutName}, " +
            "${session.elapsedMinutes} minutes écoulées, $exoLabel"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = session.progress,
        animationSpec = tween(durationMillis = 900),
        label = "resumeProgress",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            OrangeVibrant,
                            OrangeVibrant.copy(alpha = 0.85f),
                            Color(0xFFEF4444).copy(alpha = 0.9f),
                        )
                    )
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ─── Header : titre + icône play ───
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "REPRENDRE TA SÉANCE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = session.workoutName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }

                // ─── Progression : barre + chiffres ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InfoChip(
                        label = "Écoulé",
                        value = formatElapsed(session.elapsedMinutes),
                    )
                    InfoChip(
                        label = "Exercices",
                        value = if (session.isFreestyle) "${session.completedExercises}"
                        else "${session.completedExercises}/${session.totalExercises}",
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatElapsed(minutes: Int): String = when {
    minutes < 1 -> "<1min"
    minutes < 60 -> "${minutes}min"
    else -> "${minutes / 60}h${"%02d".format(minutes % 60)}"
}
