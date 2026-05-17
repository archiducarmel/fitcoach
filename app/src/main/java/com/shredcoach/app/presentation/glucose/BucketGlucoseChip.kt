package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Palette médicale Dr. Glykos. Aligné sur TodayGlucoseCard et AiToolsSection. */
private val GlucoseEmerald = Color(0xFF059669)
private val GlucoseEmeraldSoft = Color(0xFFD1FAE5)

/**
 * Chip compact à afficher à côté d'un header de date (ex: "Hier") dans
 * WorkoutHistoryScreen. Récupère le log glucose de [date] (one-shot suspend)
 * et affiche avg / TIR si dispo. Affiche rien si aucun log → pas de pollution
 * visuelle.
 *
 * **Hilt dependency** : utilise un VM scoped à la card pour avoir accès au
 * GlucoseRepository sans le passer en param.
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

    val text = if (tir != null) {
        stringResource(R.string.history_glucose_chip, avg.toInt(), tir)
    } else {
        stringResource(R.string.history_glucose_chip_avg_only, avg.toInt())
    }

    Surface(
        color = GlucoseEmeraldSoft,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = GlucoseEmerald,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@HiltViewModel
class BucketGlucoseFetcher @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
) : ViewModel() {
    suspend fun fetch(date: LocalDate): GlucoseLogEntity? =
        glucoseRepository.getForDate(date)
}
