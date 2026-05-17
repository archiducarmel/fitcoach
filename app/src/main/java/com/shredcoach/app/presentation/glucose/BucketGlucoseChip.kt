package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

/**
 * Chip glycémique compacte affichée à côté d'un header de date (ex: "Hier")
 * dans WorkoutHistoryScreen. Couleur dérivée du statut clinique de la moyenne :
 *  - in-range → emerald soft + emerald 700
 *  - warning  → amber soft + amber 700
 *  - critical → red soft + red 700
 *
 * Un petit dot coloré matérialise le statut au-delà de la couleur du texte.
 * Affiche rien si aucun log → pas de pollution visuelle.
 */
@Composable
fun BucketGlucoseChip(
    date: LocalDate,
    fetcher: BucketGlucoseFetcher = hiltViewModel(),
) {
    val log by produceState<GlucoseLogEntity?>(initialValue = null, key1 = date) {
        value = fetcher.fetch(date)
    }
    val current = log ?: return
    val avg = current.avgMgdl ?: return
    val tir = current.timeInRangePct
    val status = GlucoseStatus.forAvg(avg)

    val text = if (tir != null) {
        stringResource(R.string.history_glucose_chip, avg.toInt(), tir)
    } else {
        stringResource(R.string.history_glucose_chip_avg_only, avg.toInt())
    }

    Surface(
        color = status.color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(status.color)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = status.color,
                maxLines = 1,
            )
        }
    }
}

@HiltViewModel
class BucketGlucoseFetcher @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {
    suspend fun fetch(date: LocalDate): GlucoseLogEntity? =
        glucoseRepository.getForDate(date)
}
