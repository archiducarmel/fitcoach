package com.shredcoach.app.presentation.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.consent.DataPurger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel pour la section légale des Settings (privacy policy + suppression
 * totale). Existe surtout pour exposer [DataPurger] au UI sans le contaminer
 * avec les détails d'implémentation (DB, IO, paths).
 */
@HiltViewModel
class LegalSettingsViewModel @Inject constructor(
    private val purger: DataPurger,
) : ViewModel() {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _events = MutableStateFlow<UiEvent?>(null)
    val events: StateFlow<UiEvent?> = _events.asStateFlow()

    fun purgeAll() {
        if (_running.value) return
        viewModelScope.launch {
            _running.value = true
            try {
                purger.purgeAll()
                _events.value = UiEvent.PurgeOk
            } catch (e: Exception) {
                _events.value = UiEvent.PurgeFailed(e.message ?: "Erreur inconnue")
            } finally {
                _running.value = false
            }
        }
    }

    fun consumeEvent() { _events.value = null }

    sealed interface UiEvent {
        data object PurgeOk : UiEvent
        data class PurgeFailed(val message: String) : UiEvent
    }
}
