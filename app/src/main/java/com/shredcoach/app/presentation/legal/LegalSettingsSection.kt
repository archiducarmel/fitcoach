package com.shredcoach.app.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.presentation.navigation.Screen

/**
 * Section "Confidentialité & données" pour SettingsScreen.
 *
 * Deux entrées :
 * 1. Lien vers [PrivacyPolicyScreen]
 * 2. Bouton destructif "Supprimer toutes mes données" → [LegalSettingsViewModel.purgeAll]
 *    avec confirmation + reconfirmation par saisie ("SUPPRIMER") pour éviter
 *    les clics accidentels.
 *
 * UX : la double confirmation est volontairement plus lourde que pour le
 * disconnect backup — ici l'action est **vraiment** irréversible (DB +
 * photos + clés API). Pattern aligné avec GitHub "delete repository", Google
 * "delete account".
 */
@Composable
fun LegalSettingsSection(
    navController: NavController,
    snackbar: androidx.compose.material3.SnackbarHostState,
    viewModel: LegalSettingsViewModel = hiltViewModel(),
) {
    val running by viewModel.running.collectAsState()
    val event by viewModel.events.collectAsState()
    var showPurgeDialog by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    LaunchedEffect(event) {
        when (event) {
            null -> Unit
            LegalSettingsViewModel.UiEvent.PurgeOk -> {
                snackbar.showSnackbar(ctx.getString(R.string.legal_purge_ok_snackbar))
                viewModel.consumeEvent()
            }
            is LegalSettingsViewModel.UiEvent.PurgeFailed -> {
                snackbar.showSnackbar(ctx.getString(R.string.legal_purge_failed_snackbar, (event as LegalSettingsViewModel.UiEvent.PurgeFailed).message))
                viewModel.consumeEvent()
            }
        }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = { navController.navigate(Screen.PrivacyPolicy.route) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.legal_btn_privacy_policy))
        }

        OutlinedButton(
            onClick = { showPurgeDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !running,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            if (running) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.legal_btn_purge_running))
            } else {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.legal_btn_purge))
            }
        }
    }

    if (showPurgeDialog) {
        PurgeConfirmDialog(
            onConfirm = {
                viewModel.purgeAll()
                showPurgeDialog = false
            },
            onDismiss = { showPurgeDialog = false },
        )
    }
}

@Composable
private fun PurgeConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val magicWord = stringResource(R.string.legal_purge_dialog_magic_word)
    val canConfirm = typed.equals(magicWord, ignoreCase = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.legal_purge_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.legal_purge_dialog_body))
                Text(
                    stringResource(R.string.legal_purge_dialog_confirm_prompt, magicWord),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.legal_purge_dialog_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
