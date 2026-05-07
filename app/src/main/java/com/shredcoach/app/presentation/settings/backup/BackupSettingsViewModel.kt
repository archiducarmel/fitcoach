package com.shredcoach.app.presentation.settings.backup

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.auth.GoogleAuthRepository
import com.shredcoach.app.data.backup.BackupRepository
import com.shredcoach.app.data.backup.BackupSettingsStore
import com.shredcoach.app.data.backup.BackupWorker
import com.shredcoach.app.data.backup.crypto.BackupKeyManager
import com.shredcoach.app.data.backup.provider.ProviderId
import com.shredcoach.app.data.backup.provider.RemoteArchive
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
    private val googleAuth: GoogleAuthRepository,
    private val keyManager: BackupKeyManager,
) : AndroidViewModel(application) {

    private val _running = MutableStateFlow(RunningOp.NONE)
    private val _events = MutableStateFlow<UiEvent?>(null)
    private val _remoteArchives = MutableStateFlow<List<RemoteArchive>>(emptyList())
    private val _loadingArchives = MutableStateFlow(false)
    private val _pendingRecoveryFor = MutableStateFlow<RemoteArchive?>(null)

    /** Snapshot UI consolidé : settings persistés + état runtime + état Google linké. */
    val state: StateFlow<UiState> = combine(
        settings.snapshot,
        googleAuth.state,
        keyManager.isEnabled,
        _running,
    ) { snap, auth, encryptionEnabled, running ->
        UiState(
            providerId = snap.providerId,
            folderUri = snap.folderUri,
            googleAccountEmail = auth.linkedEmail.takeIf { auth.isLinked },
            googleDisplayName = auth.displayName,
            lastBackupAt = snap.lastBackupAt,
            autoBackupEnabled = snap.autoBackupEnabled,
            encryptionEnabled = encryptionEnabled,
            running = running,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )

    val remoteArchives: StateFlow<List<RemoteArchive>> = _remoteArchives.asStateFlow()
    val loadingArchives: StateFlow<Boolean> = _loadingArchives.asStateFlow()

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

    /** Lance un restore depuis l'archive sélectionnée (URI). */
    fun runRestore(archiveUri: Uri) {
        if (_running.value != RunningOp.NONE) return
        viewModelScope.launch {
            _running.update { RunningOp.RESTORE }
            try {
                when (val r = repository.runRestore(archiveUri)) {
                    is BackupRepository.RestoreResult.Success ->
                        _events.value = UiEvent.RestoreOk(r.photosCount, r.skippedPhotos)
                    is BackupRepository.RestoreResult.NeedsRecoveryCode -> {
                        _pendingRestoreUri = archiveUri
                        _pendingRecoveryFor.value = null
                        _events.value = UiEvent.PromptRecoveryCode
                    }
                    is BackupRepository.RestoreResult.Failure ->
                        _events.value = UiEvent.Error(r.message)
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    private var _pendingRestoreUri: Uri? = null

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

    // ── Provider switching ──────────────────────────────────────

    /**
     * Change de provider de stockage. NE déclenche AUCUN backup automatique —
     * l'user doit explicitement configurer le nouveau provider (link Google
     * pour Drive, ou pick a folder pour SAF) puis lancer un backup manuel.
     */
    fun setProvider(provider: ProviderId) {
        viewModelScope.launch {
            settings.setProviderId(provider)
        }
    }

    // ── Google Drive auth ───────────────────────────────────────

    /**
     * Démarre le flow de link Google Drive. Si l'app est déjà autorisée
     * (silent path), on persiste l'email et émet `LinkedToGoogle`. Sinon, on
     * remonte le PendingIntent au composable qui lance la consent UI via
     * un `rememberLauncherForActivityResult` ; le résultat repasse par
     * [completeGoogleLink].
     */
    fun linkGoogleAccount(onConsentRequired: (IntentSender) -> Unit) {
        if (_running.value != RunningOp.NONE) return
        viewModelScope.launch {
            _running.update { RunningOp.LINKING }
            try {
                when (val outcome = googleAuth.requestAuthorization()) {
                    is GoogleAuthRepository.AuthorizationOutcome.Granted -> {
                        finalizeGoogleLink()
                    }
                    is GoogleAuthRepository.AuthorizationOutcome.NeedsConsent -> {
                        // On garde RunningOp.LINKING pendant que la consent UI
                        // est ouverte → completeGoogleLink le clearera au retour.
                        onConsentRequired(outcome.intentSender)
                        return@launch
                    }
                    is GoogleAuthRepository.AuthorizationOutcome.Failed -> {
                        _events.value = UiEvent.Error("Connexion Google : ${outcome.reason}")
                    }
                }
            } finally {
                // On clear sauf si on a délégué à la consent UI (early return ci-dessus)
                _running.update { RunningOp.NONE }
            }
        }
    }

    /** Suite du link Google après que l'user ait accepté la consent UI. */
    fun completeGoogleLink(data: Intent?) {
        viewModelScope.launch {
            _running.update { RunningOp.LINKING }
            try {
                when (val outcome = googleAuth.completeAuthorization(data)) {
                    is GoogleAuthRepository.AuthorizationOutcome.Granted -> {
                        finalizeGoogleLink()
                    }
                    is GoogleAuthRepository.AuthorizationOutcome.Failed -> {
                        _events.value = UiEvent.Error(outcome.reason)
                    }
                    is GoogleAuthRepository.AuthorizationOutcome.NeedsConsent -> {
                        // Improbable : on est censé être post-consent
                        _events.value = UiEvent.Error("Consentement non finalisé")
                    }
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    /**
     * Finalise le link : provider → Drive, auto-backup ON par défaut, worker
     * enrôlé. Pourquoi auto-enable : l'utilisateur a explicitement tapé
     * "Continuer avec Google" pour activer la sauvegarde — opt-out via le
     * toggle après-coup s'il le souhaite. Premium UX = pas demander 2 fois.
     */
    private suspend fun finalizeGoogleLink() {
        settings.setProviderId(ProviderId.GOOGLE_DRIVE)
        if (!settings.snapshot.first().autoBackupEnabled) {
            settings.setAutoBackupEnabled(true)
            BackupWorker.enqueue(getApplication())
        }
        _events.value = UiEvent.LinkedToGoogle
    }

    /**
     * Déconnecte Google. On garde le provider sur GOOGLE_DRIVE (l'user pourra
     * re-link sans repasser par les settings) — l'UI affichera un état
     * "non configuré" et un bouton "Reconnecter".
     */
    fun unlinkGoogleAccount() {
        viewModelScope.launch {
            googleAuth.unlink()
            BackupWorker.cancel(getApplication())
            _events.value = UiEvent.UnlinkedFromGoogle
        }
    }

    // ── Restore depuis remote ────────────────────────────────────

    /** Charge la liste des archives distantes pour le restore picker. */
    fun loadRemoteArchives() {
        if (_loadingArchives.value) return
        viewModelScope.launch {
            _loadingArchives.update { true }
            try {
                _remoteArchives.value = repository.listRemoteArchives()
            } finally {
                _loadingArchives.update { false }
            }
        }
    }

    /** Restore depuis une archive distante (Drive ou SAF). */
    fun runRestoreFromRemote(remote: RemoteArchive) {
        if (_running.value != RunningOp.NONE) return
        viewModelScope.launch {
            _running.update { RunningOp.RESTORE }
            try {
                when (val r = repository.runRestoreFromRemote(remote)) {
                    is BackupRepository.RestoreResult.Success ->
                        _events.value = UiEvent.RestoreOk(r.photosCount, r.skippedPhotos)
                    is BackupRepository.RestoreResult.NeedsRecoveryCode -> {
                        _pendingRecoveryFor.value = remote
                        _pendingRestoreUri = null
                        _events.value = UiEvent.PromptRecoveryCode
                    }
                    is BackupRepository.RestoreResult.Failure ->
                        _events.value = UiEvent.Error(r.message)
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    // ── Encryption ──────────────────────────────────────────────

    /**
     * Active l'encryption AES-GCM. Génère une clé fraîche (idempotent — si
     * une clé existe déjà, on la réutilise) puis émet [UiEvent.ShowRecoveryCode]
     * pour forcer l'user à noter le code. **Critique** : sans le code, en cas de
     * changement de téléphone, les sauvegardes chiffrées sont irrécupérables.
     */
    fun enableEncryption() {
        viewModelScope.launch {
            keyManager.enableAndGenerate()
            val code = keyManager.exportRecoveryCode() ?: return@launch
            _events.value = UiEvent.ShowRecoveryCode(code)
        }
    }

    /**
     * Désactive l'encryption. Les futures sauvegardes seront en clair. Les
     * sauvegardes chiffrées existantes restent lisibles tant que l'user a son
     * code de récupération (qui correspond à la clé qu'on s'apprête à effacer).
     * Le composable doit afficher un dialog de confirmation AVANT.
     */
    fun disableEncryption() {
        viewModelScope.launch {
            keyManager.disable()
            _events.value = UiEvent.EncryptionDisabled
        }
    }

    /** Re-affiche le code de récupération courant (action user dans Settings). */
    fun revealRecoveryCode() {
        viewModelScope.launch {
            val code = keyManager.exportRecoveryCode() ?: return@launch
            _events.value = UiEvent.ShowRecoveryCode(code)
        }
    }

    /**
     * Appelé après que l'user ait collé son code dans le dialog. Parse, importe,
     * puis retry la restauration en attente (URI ou RemoteArchive selon ce qui
     * était pending).
     */
    fun provideRecoveryCodeAndRetry(input: String) {
        val raw = BackupKeyManager.parseRecoveryCode(input)
        if (raw == null) {
            _events.value = UiEvent.Error("Code de récupération invalide. Vérifie que tu l'as recopié exactement.")
            return
        }
        val pendingUri = _pendingRestoreUri
        val pendingRemote = _pendingRecoveryFor.value
        _pendingRestoreUri = null
        _pendingRecoveryFor.value = null

        if (pendingUri == null && pendingRemote == null) {
            _events.value = UiEvent.Error("Aucune restauration en attente.")
            return
        }
        viewModelScope.launch {
            _running.update { RunningOp.RESTORE }
            try {
                val result = if (pendingRemote != null) {
                    repository.runRestoreFromRemote(pendingRemote, raw)
                } else {
                    repository.runRestore(pendingUri!!, raw)
                }
                when (result) {
                    is BackupRepository.RestoreResult.Success ->
                        _events.value = UiEvent.RestoreOk(result.photosCount, result.skippedPhotos)
                    is BackupRepository.RestoreResult.Failure ->
                        _events.value = UiEvent.Error(result.message)
                    is BackupRepository.RestoreResult.NeedsRecoveryCode ->
                        _events.value = UiEvent.Error("Code incorrect — l'archive ne se déchiffre pas avec ce code.")
                }
            } finally {
                _running.update { RunningOp.NONE }
            }
        }
    }

    /** Annule un prompt recovery-code en attente (user a tapé "Annuler"). */
    fun cancelRecoveryPrompt() {
        _pendingRestoreUri = null
        _pendingRecoveryFor.value = null
    }

    data class UiState(
        val providerId: ProviderId = ProviderId.LOCAL_SAF,
        val folderUri: Uri? = null,
        val googleAccountEmail: String? = null,
        val googleDisplayName: String? = null,
        val lastBackupAt: Instant? = null,
        val autoBackupEnabled: Boolean = false,
        val encryptionEnabled: Boolean = false,
        val running: RunningOp = RunningOp.NONE,
    ) {
        /** "Configuré" dépend du provider : SAF → folder pické, Drive → email linké. */
        val isConfigured: Boolean
            get() = when (providerId) {
                ProviderId.LOCAL_SAF -> folderUri != null
                ProviderId.GOOGLE_DRIVE -> googleAccountEmail != null
            }
    }

    enum class RunningOp { NONE, BACKUP, RESTORE, LINKING }

    sealed interface UiEvent {
        data object FolderUpdated : UiEvent
        data class BackupOk(val photosCount: Int) : UiEvent
        data class RestoreOk(val photosCount: Int, val skippedPhotos: Int) : UiEvent
        data class Error(val message: String) : UiEvent
        data object Disconnected : UiEvent
        data object LinkedToGoogle : UiEvent
        data object UnlinkedFromGoogle : UiEvent
        /** Affiche le code de récupération (post-enable ou via "Voir mon code"). */
        data class ShowRecoveryCode(val code: String) : UiEvent
        /** Prompt l'user pour saisir son code (restore d'une archive chiffrée sans clé locale). */
        data object PromptRecoveryCode : UiEvent
        data object EncryptionDisabled : UiEvent
    }
}
