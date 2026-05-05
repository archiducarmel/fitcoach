package com.shredcoach.app.presentation.settings.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.backup.BackupRepository
import com.shredcoach.app.data.backup.BackupSettingsStore
import com.shredcoach.app.data.backup.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * ViewModel pour la section "Sauvegarde" des Settings.
 *
 * Architecture :
 * - [BackupSettingsStore] est la source de vérité persistée (DataStore).
 * - [BackupRepository] orchestre les opérations (backup / restore).
 * - Cet AndroidViewModel les expose au UI Compose, gère le loading state
 *   et les résultats one-shot via [eventFlow].
 *
 * Pourquoi [AndroidViewModel] (vs ViewModel) : on a besoin d'un Context
 * pour démarrer/annuler le [BackupWorker] (WorkManager). Plutôt que de
 * passer Context dans chaque action UI, on l'injecte une fois via [Application].
 */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    application: Application,
    private val settings: BackupSettingsStore,
    private val repository: BackupRepository,
) : AndroidViewModel(application) {

    private val _running = MutableStateFlow(RunningOp.NONE)
    private val _events = MutableStateFlow<UiEvent?>(null)

    /** Snapshot UI consolidé : settings persistés + état runtime. */
    val state: StateFlow<UiState> = combine(
        settings.snapshot,
        _running,
    ) { snap, running ->
        UiState(
            folderUri = snap.folderUri,
            lastBackupAt = snap.lastBackupAt,
            autoBackupEnabled = snap.autoBackupEnabled,
            running = running,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    /** Events one-shot (snackbar). Le composable les consomme via [consumeEvent]. */
    val events: StateFlow<UiEvent?> = _events.asStateFlow()
    fun consumeEvent() { _events.value = null }

    // ── Actions ──────────────────────────────────────────────

    /**
     * Persiste le dossier sélectionné via le SAF picker. Le caller a déjà
     * appelé [SafFolderPickerContract.persistPermissions] AVANT d'arriver
     * ici → on présume que l'URI est utilisable au reboot.
     *
     * Side-effect : si l'utilisateur change de dossier, on libère les
     * permissions sur l'ancien (économie sur le quota système des 128 URIs
     * persistantes par contentResolver).
     *
     * **Contrat single-writer** : entre `snapshot.first()` et `setFolderUri(...)`,
     * aucun autre writer ne doit modifier `folderUri` simultanément. Le respect
     * du contrat est trivial aujourd'hui (seul ce VM appelle `setFolderUri`/
     * `reset`). Si un futur consommateur écrit le folderUri, prévoir un mutex
     * (Mutex ou single-thread Dispatcher) pour éviter une race read-then-write.
     */
    fun onFolderPicked(newUri: Uri) {
        viewModelScope.launch {
            val oldUri = settings.snapshot.first().folderUri
            settings.setFolderUri(newUri)
            if (oldUri != null && oldUri != newUri) {
                SafFolderPickerContract.releasePermissions(getApplication(), oldUri)
            }
            _events.value = UiEvent.FolderUpdated
        }
    }

    /** Toggle l'auto-backup quotidien et (dés)enrôle le worker. */
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoBackupEnabled(enabled)
            if (enabled) BackupWorker.enqueue(getApplication())
            else BackupWorker.cancel(getApplication())
        }
    }

    /** Lance un backup manuel. Désactive le bouton via [_running] pour éviter le double-clic. */
    fun runBackupNow() {
        if (_running.value != RunningOp.NONE) return
        viewModelScope.launch {
            _running.update { RunningOp.BACKUP }
            try {
                when (val r = repository.runBackup()) {
                    is BackupRepository.BackupResult.Success ->
                        _events.value = UiEvent.BackupOk(r.photosCount)
                    is BackupRepository.BackupResult.Failure ->
                        _events.value = UiEvent.Error(r.message)
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    /** Lance un restore depuis l'archive sélectionnée. */
    fun runRestore(archiveUri: Uri) {
        if (_running.value != RunningOp.NONE) return
        viewModelScope.launch {
            _running.update { RunningOp.RESTORE }
            try {
                when (val r = repository.runRestore(archiveUri)) {
                    is BackupRepository.RestoreResult.Success ->
                        _events.value = UiEvent.RestoreOk(r.photosCount, r.skippedPhotos)
                    is BackupRepository.RestoreResult.Failure ->
                        _events.value = UiEvent.Error(r.message)
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    /**
     * Déconnecte le backup : libère les permissions SAF, reset les settings,
     * annule le worker. Pas d'action sur les archives existantes (elles
     * appartiennent à l'utilisateur).
     */
    fun disconnect() {
        viewModelScope.launch {
            val snap = settings.snapshot.first()
            snap.folderUri?.let { SafFolderPickerContract.releasePermissions(getApplication(), it) }
            settings.reset()
            BackupWorker.cancel(getApplication())
            _events.value = UiEvent.Disconnected
        }
    }

    data class UiState(
        val folderUri: Uri? = null,
        val lastBackupAt: Instant? = null,
        val autoBackupEnabled: Boolean = false,
        val running: RunningOp = RunningOp.NONE,
    ) {
        val isConfigured: Boolean get() = folderUri != null
    }

    enum class RunningOp { NONE, BACKUP, RESTORE }

    sealed interface UiEvent {
        data object FolderUpdated : UiEvent
        data class BackupOk(val photosCount: Int) : UiEvent
        data class RestoreOk(val photosCount: Int, val skippedPhotos: Int) : UiEvent
        data class Error(val message: String) : UiEvent
        data object Disconnected : UiEvent
    }
}
