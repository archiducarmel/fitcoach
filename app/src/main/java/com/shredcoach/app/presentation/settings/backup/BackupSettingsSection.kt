package com.shredcoach.app.presentation.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.R
import com.shredcoach.app.data.backup.provider.ProviderId
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.Duration
import java.time.Instant

/**
 * Section "Sauvegarde locale" pour SettingsScreen.
 *
 * Encapsule :
 * - Folder picker (SAF) → persist URI dans BackupSettingsStore
 * - File picker (zip) pour restore
 * - Toggle auto-backup quotidien
 * - Boutons "Sauvegarder maintenant" / "Restaurer" / "Déconnecter"
 * - Dialogues de confirmation pour les actions destructives (restore wipe DB,
 *   disconnect révoque l'accès au dossier)
 *
 * À insérer dans SettingsScreen via :
 * ```kotlin
 * BackupSettingsSection(snackbar = snackbarHostState)
 * ```
 *
 * Le composable embarque son propre [BackupSettingsViewModel] via
 * `hiltViewModel()` — l'écran parent n'a rien à câbler.
 */
@Composable
fun BackupSettingsSection(
    snackbar: androidx.compose.material3.SnackbarHostState,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val event by viewModel.events.collectAsState()
    val context = LocalContext.current

    // Folder picker SAF — appel takePersistableUriPermission DANS le callback
    // (cf. SafFolderPickerContract.persistPermissions). Sans ça, l'URI expire
    // au reboot du device.
    val folderPicker = rememberLauncherForActivityResult(SafFolderPickerContract()) { uri ->
        if (uri != null) {
            SafFolderPickerContract.persistPermissions(context, uri)
            viewModel.onFolderPicked(uri)
        }
    }

    // Dialogues de confirmation — state local, pas dans le VM (UI-only).
    // Le restore est DESTRUCTIF (wipe DB) → on ne lance jamais directement
    // après pick, on passe systématiquement par showRestoreConfirm.
    var showRestoreConfirm by remember { mutableStateOf<Uri?>(null) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) showRestoreConfirm = uri
    }

    // Launcher pour la consent UI Google. Le ViewModel nous remonte un
    // IntentSender via [linkGoogleAccount(onConsentRequired = ...)], on le
    // wrappe dans un IntentSenderRequest et on lance ; le résultat (data Intent)
    // repasse dans completeGoogleLink.
    val googleConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.completeGoogleLink(result.data)
    }
    var showGoogleUnlinkConfirm by remember { mutableStateOf(false) }
    var showRemoteRestorePicker by remember { mutableStateOf(false) }
    var showRestoreConfirmRemote by remember { mutableStateOf<com.shredcoach.app.data.backup.provider.RemoteArchive?>(null) }
    var showEncryptionEnableDialog by remember { mutableStateOf(false) }
    var showEncryptionDisableConfirm by remember { mutableStateOf(false) }
    var revealedRecoveryCode by remember { mutableStateOf<String?>(null) }
    var promptRecoveryCode by remember { mutableStateOf(false) }

    // Snackbar pour les events one-shot.
    LaunchedEffect(event) {
        when (val e = event) {
            null -> Unit
            is BackupSettingsViewModel.UiEvent.FolderUpdated ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_folder_set))
            is BackupSettingsViewModel.UiEvent.BackupOk ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_ok, e.photosCount))
            is BackupSettingsViewModel.UiEvent.RestoreOk -> {
                val skipMsg = if (e.skippedPhotos > 0)
                    " " + context.getString(R.string.backup_snack_restore_skipped, e.skippedPhotos)
                else ""
                snackbar.showSnackbar(
                    context.getString(R.string.backup_snack_restore_ok, e.photosCount) + skipMsg
                )
            }
            is BackupSettingsViewModel.UiEvent.Error ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_error, e.message))
            BackupSettingsViewModel.UiEvent.Disconnected ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_disconnected))
            BackupSettingsViewModel.UiEvent.LinkedToGoogle ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_drive_linked))
            BackupSettingsViewModel.UiEvent.UnlinkedFromGoogle ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_drive_unlinked))
            is BackupSettingsViewModel.UiEvent.ShowRecoveryCode ->
                revealedRecoveryCode = e.code
            BackupSettingsViewModel.UiEvent.PromptRecoveryCode ->
                promptRecoveryCode = true
            BackupSettingsViewModel.UiEvent.EncryptionDisabled ->
                snackbar.showSnackbar(context.getString(R.string.backup_snack_encryption_disabled))
        }
        viewModel.consumeEvent()
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusCard(state = state)

        // ── Sélecteur de provider de stockage ──
        ProviderSelector(
            selected = state.providerId,
            onSelect = { viewModel.setProvider(it) },
        )

        // ── Configuration spécifique au provider sélectionné ──
        when (state.providerId) {
            ProviderId.LOCAL_SAF -> {
                OutlinedButton(
                    onClick = { folderPicker.launch(state.folderUri) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Folder, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(
                            if (state.folderUri != null) R.string.backup_change_folder
                            else R.string.backup_pick_folder
                        ),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            ProviderId.GOOGLE_DRIVE -> {
                GoogleAccountCard(
                    accountEmail = state.googleAccountEmail,
                    displayName = state.googleDisplayName,
                    enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                    isLinking = state.running == BackupSettingsViewModel.RunningOp.LINKING,
                    onConnect = {
                        viewModel.linkGoogleAccount { sender ->
                            googleConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                    },
                    onDisconnect = { showGoogleUnlinkConfirm = true },
                )
            }
        }

        if (state.isConfigured) {
            // Auto-backup toggle dans sa propre Surface pour signaler "switch zone"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            stringResource(R.string.backup_auto_title),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.backup_auto_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(
                        checked = state.autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                    )
                }
            }

            // ── Encryption AES-GCM ──
            EncryptionSection(
                enabled = state.encryptionEnabled,
                onToggle = { wantOn ->
                    if (wantOn) showEncryptionEnableDialog = true
                    else showEncryptionDisableConfirm = true
                },
                onRevealCode = { viewModel.revealRecoveryCode() },
            )

            // Bouton primaire — orange plein, le CTA principal de l'écran
            Button(
                onClick = { viewModel.runBackupNow() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrangeVibrant,
                    disabledContainerColor = OrangeVibrant.copy(alpha = 0.4f),
                ),
            ) {
                if (state.running == BackupSettingsViewModel.RunningOp.BACKUP) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.backup_running), fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.backup_now), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Bouton secondaire — outlined, action "moins fréquente"
        OutlinedButton(
            onClick = {
                when (state.providerId) {
                    ProviderId.LOCAL_SAF -> {
                        // OpenDocument MIME filter — on accepte */* car certains providers
                        // (Drive, OneDrive) ne déclarent pas correctement le MIME zip ;
                        // mais on filtre côté unpack via la signature ZIP magic-bytes.
                        zipPicker.launch(arrayOf("application/zip", "*/*"))
                    }
                    ProviderId.GOOGLE_DRIVE -> {
                        // Pour Drive, on liste les archives du dossier appdata et
                        // on présente un picker dans un AlertDialog (pas de file
                        // picker système possible sur appdata, le scope est privé).
                        viewModel.loadRemoteArchives()
                        showRemoteRestorePicker = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
            shape = RoundedCornerShape(14.dp),
        ) {
            if (state.running == BackupSettingsViewModel.RunningOp.RESTORE) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.backup_restoring), fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Restore, null, Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                // Texte court — le contexte "Drive vs local" est déjà signifié
                // par le ProviderSelector (chips) au-dessus.
                Text(
                    stringResource(R.string.backup_restore),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }

        // Bouton déconnexion : SAF uniquement (Drive a son propre disconnect
        // dans la GoogleAccountCard ci-dessus).
        if (state.providerId == ProviderId.LOCAL_SAF && state.isConfigured) {
            TextButton(
                onClick = { showDisconnectConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.backup_disconnect_local))
            }
        }
    }

    // ── Dialogues ───────────────────────────────────────────
    val pendingRestore = showRestoreConfirm
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            icon = { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
            text = { Text(stringResource(R.string.backup_restore_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.runRestore(pendingRestore)
                        showRestoreConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            icon = { Icon(Icons.Default.CloudOff, null) },
            title = { Text(stringResource(R.string.backup_disconnect_dialog_title)) },
            text = { Text(stringResource(R.string.backup_disconnect_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    if (showGoogleUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showGoogleUnlinkConfirm = false },
            icon = { Icon(Icons.Default.CloudOff, null) },
            title = { Text(stringResource(R.string.backup_disconnect_drive_dialog_title)) },
            text = { Text(stringResource(R.string.backup_disconnect_drive_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.unlinkGoogleAccount()
                        showGoogleUnlinkConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_disconnect_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleUnlinkConfirm = false }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    if (showRemoteRestorePicker) {
        RemoteArchivePickerDialog(
            archives = viewModel.remoteArchives.collectAsState().value,
            isLoading = viewModel.loadingArchives.collectAsState().value,
            onDismiss = { showRemoteRestorePicker = false },
            onPick = { archive ->
                showRemoteRestorePicker = false
                // Réutilise le dialogue de confirmation destructif existant en
                // pointant vers une URI placeholder ; le ViewModel gère le
                // download → restore via runRestoreFromRemote.
                showRestoreConfirmRemote = archive
            },
        )
    }

    val pendingRemoteRestore = showRestoreConfirmRemote
    if (pendingRemoteRestore != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmRemote = null },
            icon = { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_dialog_text_with_date,
                        formatRelative(context, pendingRemoteRestore.createdAt),
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.runRestoreFromRemote(pendingRemoteRestore)
                        showRestoreConfirmRemote = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmRemote = null }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    // ── Dialog : confirmer l'activation de l'encryption ──
    if (showEncryptionEnableDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionEnableDialog = false },
            icon = { Icon(Icons.Default.EnhancedEncryption, null, tint = OrangeVibrant) },
            title = { Text(stringResource(R.string.backup_encryption_enable_title)) },
            text = { Text(stringResource(R.string.backup_encryption_enable_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.enableEncryption()
                        showEncryptionEnableDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = OrangeVibrant),
                ) { Text(stringResource(R.string.backup_encryption_enable_confirm), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptionEnableDialog = false }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    // ── Dialog : confirmer la désactivation ──
    if (showEncryptionDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showEncryptionDisableConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_encryption_disable_title)) },
            text = { Text(stringResource(R.string.backup_encryption_disable_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disableEncryption()
                        showEncryptionDisableConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_encryption_disable_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEncryptionDisableConfirm = false }) {
                    Text(stringResource(R.string.common_cancel_short))
                }
            },
        )
    }

    // ── Dialog : afficher le code de récupération ──
    val codeToReveal = revealedRecoveryCode
    if (codeToReveal != null) {
        RecoveryCodeDialog(
            code = codeToReveal,
            onDismiss = { revealedRecoveryCode = null },
        )
    }

    // ── Dialog : prompter l'user pour son code (restore archive chiffrée) ──
    if (promptRecoveryCode) {
        RecoveryCodeInputDialog(
            isLoading = state.running == BackupSettingsViewModel.RunningOp.RESTORE,
            onConfirm = { input ->
                viewModel.provideRecoveryCodeAndRetry(input)
                promptRecoveryCode = false
            },
            onDismiss = {
                viewModel.cancelRecoveryPrompt()
                promptRecoveryCode = false
            },
        )
    }
}

@Composable
private fun StatusCard(state: BackupSettingsViewModel.UiState) {
    val context = LocalContext.current
    val (icon, tint, title, subtitle) = when {
        !state.isConfigured -> StatusInfo(
            icon = Icons.Default.CloudOff,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            title = stringResource(R.string.backup_status_not_configured),
            subtitle = when (state.providerId) {
                ProviderId.GOOGLE_DRIVE -> stringResource(R.string.backup_status_subtitle_drive)
                ProviderId.LOCAL_SAF -> stringResource(R.string.backup_status_subtitle_local)
            },
        )
        state.lastBackupAt == null -> StatusInfo(
            icon = Icons.Default.Schedule,
            tint = OrangeVibrant,
            title = stringResource(R.string.backup_status_configured_never),
            subtitle = stringResource(R.string.backup_status_configured_never_subtitle),
        )
        else -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            tint = NeonGreen,
            title = stringResource(R.string.backup_status_active),
            subtitle = stringResource(
                R.string.backup_status_active_subtitle,
                formatRelative(context, state.lastBackupAt),
            ),
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(tint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private data class StatusInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: androidx.compose.ui.graphics.Color,
    val title: String,
    val subtitle: String,
)

/**
 * Formatage relatif "il y a X" / "X ago", tronqué à la granularité utile.
 * On évite une dep `RelativeDateTimeFormatter` (API 24+) pour rester ISO
 * sur le wording — Context permet le dispatch FR/EN via stringResource.
 */
private fun formatRelative(context: android.content.Context, instant: Instant): String {
    val seconds = Duration.between(instant, Instant.now()).seconds
    return when {
        seconds < 60 -> context.getString(R.string.backup_time_now)
        seconds < 3600 -> context.getString(R.string.backup_time_minutes_ago, seconds / 60)
        seconds < 86_400 -> context.getString(R.string.backup_time_hours_ago, seconds / 3600)
        seconds < 604_800 -> context.getString(R.string.backup_time_days_ago, seconds / 86_400)
        else -> context.getString(R.string.backup_time_weeks_ago, seconds / 604_800)
    }
}

/**
 * Sélecteur de provider — paire de FilterChip "Local | Google Drive".
 * Pas un SegmentedButton M3 car celui-ci dépend d'une version material3 plus
 * récente que celle bundlée. FilterChip donne le même rendu visuel propre
 * avec un comportement "select-one".
 */
@Composable
private fun ProviderSelector(
    selected: ProviderId,
    onSelect: (ProviderId) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == ProviderId.LOCAL_SAF,
            onClick = { onSelect(ProviderId.LOCAL_SAF) },
            label = { Text(stringResource(R.string.backup_provider_local)) },
            leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(16.dp)) },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = selected == ProviderId.GOOGLE_DRIVE,
            onClick = { onSelect(ProviderId.GOOGLE_DRIVE) },
            label = { Text(stringResource(R.string.backup_provider_drive)) },
            leadingIcon = { Icon(Icons.Default.CloudUpload, null, Modifier.size(16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Card de statut Google. Deux états visuels distincts :
 *
 * - **Non linké** : surface neutre + CTA orange "Continuer avec Google".
 * - **Linké** : surface neutre + avatar (cercle 56dp avec badge ✓ vert overlay
 *   bottom-right pour signifier "compte vérifié + sauvegarde active") +
 *   identité (nom + email, maxLines+ellipsis pour ne jamais squeeze) + footer
 *   séparé par un divider avec status "Drive privé sécurisé" et action
 *   "Déconnecter" sur leur propre ligne. Cette séparation footer/identité
 *   empêche le bouton Déconnecter de manger l'espace horizontal du nom/email
 *   (régression précédente : tout collé sur 1 row).
 */
@Composable
private fun GoogleAccountCard(
    accountEmail: String?,
    displayName: String?,
    enabled: Boolean,
    isLinking: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (accountEmail == null) {
            UnlinkedGoogleContent(isLinking = isLinking, enabled = enabled, onConnect = onConnect)
        } else {
            LinkedGoogleContent(
                accountEmail = accountEmail,
                displayName = displayName,
                enabled = enabled,
                onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
private fun UnlinkedGoogleContent(
    isLinking: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AccountCircle, null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (isLinking) R.string.backup_google_linking else R.string.backup_google_no_account),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (isLinking) R.string.backup_google_linking_subtitle else R.string.backup_google_no_account_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Button(
            onClick = onConnect,
            enabled = enabled && !isLinking,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
        ) {
            if (isLinking) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.backup_google_connecting), fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Login, null, Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(stringResource(R.string.backup_google_connect), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LinkedGoogleContent(
    accountEmail: String,
    displayName: String?,
    enabled: Boolean,
    onDisconnect: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        // ── Identité — avatar + nom/email — l'email peut wrap sur 2 lignes pour
        // les longs adresses, on n'ellipse jamais (premium = jamais de "...") ──
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.AccountCircle, null,
                    Modifier.size(48.dp),
                    tint = OrangeVibrant.copy(alpha = 0.85f),
                )
                // Badge ✓ overlay bottom-end : signal visuel "compte vérifié"
                Box(
                    Modifier
                        .size(18.dp)
                        .align(Alignment.BottomEnd)
                        .background(NeonGreen, CircleShape)
                        .border(2.dp, surfaceColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check, null,
                        Modifier.size(11.dp),
                        tint = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.backup_google_account_fallback),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    accountEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    // Pas de maxLines : on laisse wrap sur 2 lignes pour les
                    // adresses longues plutôt que tronquer avec "..."
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))
        // ── Footer — status à gauche, déconnecter à droite, jamais squeezed ──
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Lock, null,
                    Modifier.size(14.dp),
                    tint = NeonGreen,
                )
                Text(
                    stringResource(R.string.backup_google_secure),
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreen,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(
                onClick = onDisconnect,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                ),
            ) {
                Text(
                    stringResource(R.string.backup_disconnect_confirm),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Picker pour les archives distantes (Drive). Liste triée par date desc, avec
 * loading state + état vide ("aucune sauvegarde trouvée").
 */
@Composable
private fun RemoteArchivePickerDialog(
    archives: List<com.shredcoach.app.data.backup.provider.RemoteArchive>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onPick: (com.shredcoach.app.data.backup.provider.RemoteArchive) -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CloudUpload, null, tint = OrangeVibrant) },
        title = { Text(stringResource(R.string.backup_picker_title)) },
        text = {
            when {
                isLoading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.backup_picker_loading))
                    }
                }
                archives.isEmpty() -> Text(stringResource(R.string.backup_picker_empty))
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        archives.take(20).forEach { archive ->
                            Surface(
                                onClick = { onPick(archive) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        formatRelative(context, archive.createdAt).replaceFirstChar { it.uppercase() },
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        stringResource(R.string.backup_picker_size_kb, archive.sizeBytes / 1024),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_picker_close)) }
        },
    )
}

/**
 * Section "Chiffrement" — toggle + lien "Voir mon code de récupération" quand
 * activé. Visuellement aligné avec la Surface auto-backup pour cohérence.
 */
@Composable
private fun EncryptionSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRevealCode: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.EnhancedEncryption, null,
                        Modifier.size(20.dp),
                        tint = if (enabled) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Column {
                        Text(
                            stringResource(R.string.backup_encryption_title),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(
                                if (enabled) R.string.backup_encryption_subtitle_on
                                else R.string.backup_encryption_subtitle_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onRevealCode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = OrangeVibrant),
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.backup_encryption_view_code),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Affiche le code de récupération dans un format lisible (groupes de 4 chars
 * monospace), avec un bouton copy-to-clipboard. Sert au premier enable ET au
 * "voir mon code" depuis Settings.
 *
 * **Sécurité visuelle** : on n'affiche pas le code à l'écran tant qu'il n'est
 * pas demandé explicitement. Une fois affiché, l'user peut copier ou screenshot
 * — c'est sa responsabilité de stocker le code de manière sécurisée.
 */
@Composable
private fun RecoveryCodeDialog(
    code: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.EnhancedEncryption, null, tint = OrangeVibrant) },
        title = { Text(stringResource(R.string.backup_recovery_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.backup_recovery_code_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            code,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                            Icon(
                                Icons.Default.ContentCopy,
                                stringResource(R.string.backup_recovery_code_copy_cd),
                                tint = OrangeVibrant
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Warning, null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.backup_recovery_code_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_recovery_code_saved), fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/**
 * Prompt l'user pour saisir son code de récupération après détection d'une
 * archive chiffrée sans clé locale (= cas restore sur un nouveau téléphone).
 * Le format saisi est tolérant — on accepte avec/sans hyphens et espaces.
 */
@Composable
private fun RecoveryCodeInputDialog(
    isLoading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = OrangeVibrant) },
        title = { Text(stringResource(R.string.backup_recovery_code_input_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.backup_recovery_code_input_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.backup_recovery_code_input_placeholder),
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    enabled = !isLoading,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank() && !isLoading,
                colors = ButtonDefaults.textButtonColors(contentColor = OrangeVibrant),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OrangeVibrant)
                    Spacer(Modifier.size(6.dp))
                }
                Text(stringResource(R.string.backup_restore_confirm), fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.common_cancel_short))
            }
        },
    )
}
