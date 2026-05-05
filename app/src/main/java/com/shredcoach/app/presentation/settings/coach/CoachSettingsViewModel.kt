package com.shredcoach.app.presentation.settings.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.consent.ConsentStore
import com.shredcoach.app.domain.coach.CoachSettingsStore
import com.shredcoach.app.domain.coach.ProactiveCoachWorker
import com.shredcoach.app.domain.coach.WeeklyRecapWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour la section "Coach proactif" dans Settings.
 *
 * Expose : enabled, hour, tone, mutedCategories, weeklyCap + flag llmConsentGranted.
 * Orchestre : enrôlement / annulation des workers (proactif quotidien + récap hebdo).
 */
@HiltViewModel
class CoachSettingsViewModel @Inject constructor(
    application: Application,
    private val coachSettings: CoachSettingsStore,
    private val consentStore: ConsentStore,
) : AndroidViewModel(application) {

    val state: StateFlow<UiState> = combine(
        coachSettings.snapshot,
        consentStore.snapshot,
    ) { coach, consent ->
        UiState(
            enabled = coach.enabled,
            preferredHour = coach.preferredHourLocal,
            tone = coach.tone,
            mutedCategories = coach.mutedCategories,
            weeklyCap = coach.weeklyCap,
            llmConsentGranted = consent.isCurrent(ConsentStore.ConsentType.LLM_CHAT),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    /**
     * Active le coach + accorde le consentement LLM en une atomic action.
     * Enrôle les DEUX workers : daily proactif + weekly recap dimanche soir.
     */
    fun acceptAndEnable() {
        viewModelScope.launch {
            consentStore.grant(ConsentStore.ConsentType.LLM_CHAT)
            coachSettings.setEnabled(true)
            ProactiveCoachWorker.enqueue(getApplication(), state.value.preferredHour)
            WeeklyRecapWorker.enqueue(getApplication())
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            coachSettings.setEnabled(enabled)
            if (enabled) {
                ProactiveCoachWorker.enqueue(getApplication(), state.value.preferredHour)
                WeeklyRecapWorker.enqueue(getApplication())
            } else {
                ProactiveCoachWorker.cancel(getApplication())
                WeeklyRecapWorker.cancel(getApplication())
            }
        }
    }

    fun setPreferredHour(hour: Int) {
        viewModelScope.launch {
            coachSettings.setPreferredHour(hour)
            if (state.value.enabled) {
                ProactiveCoachWorker.enqueue(getApplication(), hour)
            }
        }
    }

    fun setTone(tone: CoachSettingsStore.Tone) {
        viewModelScope.launch { coachSettings.setTone(tone) }
    }

    /** Mute / unmute une catégorie de trigger. */
    fun toggleMute(category: String) {
        viewModelScope.launch { coachSettings.toggleMute(category) }
    }

    fun setWeeklyCap(cap: Int) {
        viewModelScope.launch { coachSettings.setWeeklyCap(cap) }
    }

    data class UiState(
        val enabled: Boolean = false,
        val preferredHour: Int = CoachSettingsStore.DEFAULT_HOUR,
        val tone: CoachSettingsStore.Tone = CoachSettingsStore.Tone.DIRECT,
        val mutedCategories: Set<String> = emptySet(),
        val weeklyCap: Int = CoachSettingsStore.DEFAULT_WEEKLY_CAP,
        val llmConsentGranted: Boolean = false,
    )
}
