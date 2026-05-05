package com.shredcoach.app.presentation.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

    // Snackbar pour les events one-shot.
    LaunchedEffect(event) {
        when (val e = event) {
            null -> Unit
            is BackupSettingsViewModel.UiEvent.FolderUpdated ->
                snackbar.showSnackbar("Dossier de sauvegarde configuré")
            is BackupSettingsViewModel.UiEvent.BackupOk ->
                snackbar.showSnackbar("Sauvegarde réussie · ${e.photosCount} photos")
            is BackupSettingsViewModel.UiEvent.RestoreOk -> {
                val skipMsg = if (e.skippedPhotos > 0) " (${e.skippedPhotos} ignorées)" else ""
                snackbar.showSnackbar("Restauration OK · ${e.photosCount} photos$skipMsg")
            }
            is BackupSettingsViewModel.UiEvent.Error ->
                snackbar.showSnackbar("Échec : ${e.message}")
            BackupSettingsViewModel.UiEvent.Disconnected ->
                snackbar.showSnackbar("Sauvegarde déconnectée")
        }
        viewModel.consumeEvent()
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(state = state)

        OutlinedButton(
            onClick = { folderPicker.launch(state.folderUri) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(if (state.isConfigured) "Changer le dossier de sauvegarde" else "Choisir un dossier de sauvegarde")
        }

        if (state.isConfigured) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.padding(end = 16.dp)) {
                    Text(
                        "Sauvegarde automatique",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Une fois par jour à 3h, batterie OK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(
                    checked = state.autoBackupEnabled,
                    onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                )
            }

            OutlinedButton(
                onClick = { viewModel.runBackupNow() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeVibrant),
            ) {
                if (state.running == BackupSettingsViewModel.RunningOp.BACKUP) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OrangeVibrant)
                    Spacer(Modifier.size(8.dp))
                    Text("Sauvegarde en cours…")
                } else {
                    Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Sauvegarder maintenant")
                }
            }
        }

        OutlinedButton(
            onClick = {
                // OpenDocument MIME filter — on accepte */* car certains providers
                // (Drive, OneDrive) ne déclarent pas correctement le MIME zip ;
                // mais on filtre côté unpack via la signature ZIP magic-bytes.
                zipPicker.launch(arrayOf("application/zip", "*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.running == BackupSettingsViewModel.RunningOp.RESTORE) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Restauration en cours…")
            } else {
                Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Restaurer depuis une archive")
            }
        }

        if (state.isConfigured) {
            TextButton(
                onClick = { showDisconnectConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.running == BackupSettingsViewModel.RunningOp.NONE,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.CloudOff, null, Modifier.size(16.dp))
                Spacer(Modifier.size(8.dp))
                Text("Déconnecter la sauvegarde")
            }
        }
    }

    // ── Dialogues ───────────────────────────────────────────
    val pendingRestore = showRestoreConfirm
    if (pendingRestore != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            icon = { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Restaurer cette archive ?") },
            text = {
                Text(
                    "Toutes tes données actuelles (séances, repas, conversations Shreddy, photos…) " +
                        "seront REMPLACÉES par celles de l'archive. Cette opération est irréversible.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.runRestore(pendingRestore)
                        showRestoreConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) { Text("Annuler") }
            },
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            icon = { Icon(Icons.Default.CloudOff, null) },
            title = { Text("Déconnecter la sauvegarde ?") },
            text = {
                Text(
                    "On va oublier ton dossier de sauvegarde et arrêter la sauvegarde automatique. " +
                        "Tes archives existantes ne sont pas supprimées — tu peux toujours les " +
                        "restaurer plus tard depuis ton cloud.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        showDisconnectConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Déconnecter") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun StatusCard(state: BackupSettingsViewModel.UiState) {
    val (icon, tint, title, subtitle) = when {
        !state.isConfigured -> StatusInfo(
            icon = Icons.Default.CloudOff,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            title = "Aucune sauvegarde configurée",
            subtitle = "Choisis un dossier (Drive, OneDrive, local) pour protéger tes données.",
        )
        state.lastBackupAt == null -> StatusInfo(
            icon = Icons.Default.Schedule,
            tint = OrangeVibrant,
            title = "Configuré, pas encore sauvegardé",
            subtitle = "Lance une sauvegarde manuelle, ou active la sauvegarde quotidienne.",
        )
        else -> StatusInfo(
            icon = Icons.Default.CheckCircle,
            tint = OrangeVibrant,
            title = "Sauvegarde active",
            subtitle = "Dernière : ${formatRelative(state.lastBackupAt)}",
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.padding(end = 4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
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
 * Formatage relatif "il y a X" en français, tronqué à la granularité utile.
 * On évite une dep `RelativeDateTimeFormatter` (API 24+) pour rester ISO
 * sur le wording (formulation conviviale "il y a quelques instants" plutôt
 * que "0 minute").
 */
private fun formatRelative(instant: Instant): String {
    val seconds = Duration.between(instant, Instant.now()).seconds
    return when {
        seconds < 60 -> "à l'instant"
        seconds < 3600 -> "il y a ${seconds / 60} min"
        seconds < 86_400 -> "il y a ${seconds / 3600} h"
        seconds < 604_800 -> "il y a ${seconds / 86_400} j"
        else -> "il y a ${seconds / 604_800} sem"
    }
}
