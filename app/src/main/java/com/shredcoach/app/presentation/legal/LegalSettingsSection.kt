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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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

    LaunchedEffect(event) {
        when (event) {
            null -> Unit
            LegalSettingsViewModel.UiEvent.PurgeOk -> {
                snackbar.showSnackbar("Toutes tes données ont été supprimées. Relance l'app.")
                viewModel.consumeEvent()
            }
            is LegalSettingsViewModel.UiEvent.PurgeFailed -> {
                snackbar.showSnackbar("Suppression incomplète : ${(event as LegalSettingsViewModel.UiEvent.PurgeFailed).message}")
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
            Text("Politique de confidentialité")
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
                Text("Suppression en cours…")
            } else {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Supprimer toutes mes données")
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
    val canConfirm = typed.equals(MAGIC_WORD, ignoreCase = false)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Tout supprimer ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cette action est IRRÉVERSIBLE. Tout disparaît : séances, repas, photos, " +
                        "conversations Shreddy, clés API, paramètres. Les sauvegardes existantes " +
                        "dans ton cloud ne sont PAS touchées (tu pourras restaurer plus tard).",
                )
                Text(
                    "Pour confirmer, écris exactement « $MAGIC_WORD » ci-dessous :",
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
            ) { Text("Tout supprimer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private const val MAGIC_WORD = "SUPPRIMER"
