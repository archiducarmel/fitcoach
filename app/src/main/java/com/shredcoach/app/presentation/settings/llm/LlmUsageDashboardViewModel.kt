package com.shredcoach.app.presentation.settings.llm

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.LlmUsageDao
import com.shredcoach.app.data.local.dao.UsageByAssistant
import com.shredcoach.app.data.local.dao.UsageByModel
import com.shredcoach.app.data.local.dao.UsageByProvider
import com.shredcoach.app.data.local.dao.UsageDayBucket
import com.shredcoach.app.data.local.dao.UsageHourBucket
import com.shredcoach.app.data.local.dao.UsageTotals
import com.shredcoach.app.domain.llm.LlmUsageRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Échelles temporelles supportées par le dashboard. Chaque échelle définit
 * son cutoff (depuis combien de temps on agrège) + son granularité (heure /
 * jour) pour le rendu UI.
 */
enum class UsageTimeScale(
    val labelKey: String,
    val cutoffHours: Long,
    val granularity: Granularity,
) {
    DAY_24H("usage_scale_24h", cutoffHours = 24, Granularity.HOURLY),
    DAYS_7("usage_scale_7d", cutoffHours = 24 * 7, Granularity.DAILY),
    DAYS_30("usage_scale_30d", cutoffHours = 24 * 30, Granularity.DAILY),
    ALL_TIME("usage_scale_all", cutoffHours = 24 * 365 * 10, Granularity.DAILY);

    enum class Granularity { HOURLY, DAILY }

    /** Cutoff comme LocalDateTime relatif à maintenant. */
    fun cutoff(): LocalDateTime = LocalDateTime.now().minusHours(cutoffHours)
}

@Immutable
data class LlmUsageDashboardState(
    val scale: UsageTimeScale = UsageTimeScale.DAYS_7,
    val totals: UsageTotals = UsageTotals(0, 0, 0.0, 0.0, 0.0),
    val byAssistant: List<UsageByAssistant> = emptyList(),
    val byModel: List<UsageByModel> = emptyList(),
    val byProvider: List<UsageByProvider> = emptyList(),
    val hourlyHeatmap: List<UsageHourBucket> = emptyList(),
    val dailySeries: List<UsageDayBucket> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val totalEventsAllTime: Int = 0,
    val earliestTimestamp: LocalDateTime? = null,
)

/**
 * ViewModel du dashboard de consommation LLM.
 *
 * **Comportement** :
 *  - Charge les agrégations pour l'échelle temporelle courante (default 7j)
 *  - L'utilisateur switch d'échelle (24h / 7j / 30j / All) → reload
 *  - Observable count d'events pour invalider en live si une nouvelle ligne
 *    arrive (Flow émet automatiquement)
 *  - "Vider l'historique" (bouton bas de page) wipe la table
 *
 * **Performance** : 6 queries d'aggrégation par chargement. SQLite avec
 * indexes sur (timestamp, assistantKey, model) → ~10-30ms même sur 10k
 * rows. Acceptable car le user attend rarement le dashboard.
 */
@HiltViewModel
class LlmUsageDashboardViewModel @Inject constructor(
    private val dao: LlmUsageDao,
    private val recorder: LlmUsageRecorder,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmUsageDashboardState())
    val state: StateFlow<LlmUsageDashboardState> = _state.asStateFlow()

    // Observe le count d'events — si ça change pendant que l'écran est ouvert,
    // on recharge automatiquement (telemetrie live).
    private val eventCountFlow: StateFlow<Int> = dao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Reload quand le count change (nouveaux events arrivés).
        viewModelScope.launch {
            eventCountFlow.collect {
                reload()
            }
        }
    }

    fun setScale(scale: UsageTimeScale) {
        if (_state.value.scale == scale) return
        _state.update { it.copy(scale = scale) }
        viewModelScope.launch { reload() }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            recorder.clearAll()
            // Le observeCount triggera reload automatiquement.
        }
    }

    private suspend fun reload() {
        _state.update { it.copy(isLoading = true) }
        val scale = _state.value.scale
        val cutoff = scale.cutoff()

        // Queries en parallèle pour minimiser la latence totale.
        // Note : Room ne supporte pas async sur la même connexion, on
        // séquence dans le scope IO via DAO suspend (chaque query bloque
        // brièvement son thread). Latence cumulée acceptable (~50ms total
        // sur 10k rows).
        val totals = dao.getTotalsSince(cutoff)
        val byAssistant = dao.getByAssistantSince(cutoff)
        val byModel = dao.getByModelSince(cutoff)
        val byProvider = dao.getByProviderSince(cutoff)
        val hourly = dao.getHourlyHeatmap(cutoff)
        val daily = dao.getDailySeries(cutoff)
        val totalAllTime = dao.count()
        val earliest = dao.earliestTimestamp()

        _state.update {
            it.copy(
                totals = totals,
                byAssistant = byAssistant,
                byModel = byModel,
                byProvider = byProvider,
                hourlyHeatmap = hourly,
                dailySeries = daily,
                isLoading = false,
                isEmpty = totals.totalCalls == 0,
                totalEventsAllTime = totalAllTime,
                earliestTimestamp = earliest,
            )
        }
    }
}
