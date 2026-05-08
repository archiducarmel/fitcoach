package com.shredcoach.app.presentation.home.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Carte de prévisualisation du calendrier sur la home.
 *
 * **Pourquoi sur la home** : la feature calendrier était reléguée derrière
 * l'accordéon "Plus" — peu visible donc peu utilisée. Cette card promote
 * l'usage en montrant les 2-3 prochaines séances avec leur countdown, et
 * sert de raccourci direct vers l'écran calendrier.
 *
 * **Empty state** : explicite si rien de planifié — c'est l'occasion de
 * montrer la valeur (Shreddy peut générer 5 dates idéales).
 */
@Composable
fun CalendarPreviewCard(
    upcoming: List<ScheduledWorkoutEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            OrangeVibrant.copy(alpha = 0.07f),
                            OrangeVibrant.copy(alpha = 0.02f),
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(OrangeVibrant.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CalendarMonth, null,
                        Modifier.size(20.dp), tint = OrangeVibrant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.home_calendar_preview_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = when {
                        upcoming.isEmpty() -> stringResource(R.string.home_calendar_preview_empty_title)
                        upcoming.size == 1 -> stringResource(R.string.home_calendar_preview_subtitle_one)
                        else -> stringResource(R.string.home_calendar_preview_subtitle_many, upcoming.size)
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }

            if (upcoming.isEmpty()) {
                EmptyPreview()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    upcoming.take(3).forEach { sched ->
                        UpcomingCompactRow(sched)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPreview() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.AutoAwesome, null,
            Modifier.size(20.dp), tint = OrangeVibrant.copy(alpha = 0.7f),
        )
        Text(
            stringResource(R.string.home_calendar_preview_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UpcomingCompactRow(sched: ScheduledWorkoutEntity) {
    val today = LocalDate.now()
    val daysUntil = ChronoUnit.DAYS.between(today, sched.date).toInt()
    val routine = RoutineCatalog.byId(sched.routineId)
    val titleStr = sched.title.ifBlank { stringResource(R.string.calendar_sched_default_title) }
    val timeStr = sched.time?.toString()?.substring(0, 5)
    val countdownLabel = when {
        daysUntil == 0 -> stringResource(R.string.home_calendar_preview_today_chip)
        daysUntil == 1 -> stringResource(R.string.home_calendar_preview_tomorrow_chip)
        else -> sched.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
            .replaceFirstChar { it.uppercase() }
    }
    val isImminent = daysUntil <= 1
    val accent = if (isImminent) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Date tile
        Column(
            Modifier.size(width = 38.dp, height = 42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isImminent) OrangeVibrant.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${sched.date.dayOfMonth}",
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
            Text(
                sched.date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                    .uppercase().take(3),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = accent.copy(alpha = 0.85f),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(routine.icon, fontSize = 12.sp)
                Text(
                    titleStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                buildString {
                    append(routine.displayName)
                    if (timeStr != null) append(" · $timeStr")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accent.copy(alpha = if (isImminent) 0.15f else 0.08f),
        ) {
            Row(
                Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (isImminent) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(11.dp), tint = accent)
                }
                Text(
                    countdownLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                )
            }
        }
    }
}
