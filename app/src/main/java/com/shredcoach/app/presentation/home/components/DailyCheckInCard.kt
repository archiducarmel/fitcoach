package com.shredcoach.app.presentation.home.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.wellness.MoodOption
import com.shredcoach.app.domain.wellness.WellnessStore

/**
 * Card "Daily Check-In" — 5 emojis tappables (😴 😐 🙂 💪 🔥) en 1 tap.
 *
 * **UX** :
 *  - Affichée uniquement si pas encore tapé aujourd'hui (le caller filtre via
 *    `todayMood == null`).
 *  - Tap emoji → callback + haptic, la card disparaît au prochain emit.
 *  - Touch target 56dp (a11y AAA, Apple HIG/Material).
 *  - Spring overshoot sur le scale du tap (feedback premium).
 *
 * **Pourquoi 5 emojis et pas 3 sliders** : décision produit (cf wireframe v2).
 * 1 tap < 5 sec, alors que 3 sliders demanderait 15-30 sec. Pour un check-in
 * quotidien que l'user verra 365 fois/an, la friction doit être quasi nulle —
 * sinon il l'évite.
 */
@Composable
fun DailyCheckInCard(
    onMoodSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Comment tu te sens aujourd'hui ?" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Comment tu te sens ce matin ?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WellnessStore.MOOD_OPTIONS.forEach { option ->
                    MoodButton(option = option, onClick = { onMoodSelected(option.index) })
                }
            }
        }
    }
}

@Composable
private fun MoodButton(option: MoodOption, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "moodScale",
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    pressed = true
                    onClick()
                },
            )
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                contentDescription = "Mood ${option.label}"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.emoji,
            fontSize = 28.sp,
        )
    }
}
