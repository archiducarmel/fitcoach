package com.shredcoach.app.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shredcoach.app.R

/**
 * Politique de confidentialité — texte template à adapter avec un avocat
 * avant publication. Le wording ci-dessous donne les bonnes briques RGPD
 * (responsable de traitement, finalités, transferts tiers, droits utilisateur,
 * durées de conservation) mais NE remplace PAS un audit juridique.
 *
 * Les sections marquées `[À COMPLÉTER]` doivent être remplies avant de
 * publier l'app sur le Play Store. Google exige une URL de privacy policy
 * publique en plus de cette version in-app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.privacy_section_summary_title), stringResource(R.string.privacy_section_summary_body))
            Section(stringResource(R.string.privacy_section_controller_title), stringResource(R.string.privacy_section_controller_body))
            Section(stringResource(R.string.privacy_section_data_title), stringResource(R.string.privacy_section_data_body))
            Section(stringResource(R.string.privacy_section_transfers_title), stringResource(R.string.privacy_section_transfers_body))
            Section(stringResource(R.string.privacy_section_backup_title), stringResource(R.string.privacy_section_backup_body))
            Section(stringResource(R.string.privacy_section_rights_title), stringResource(R.string.privacy_section_rights_body))
            Section(stringResource(R.string.privacy_section_retention_title), stringResource(R.string.privacy_section_retention_body))
            Section(stringResource(R.string.privacy_section_security_title), stringResource(R.string.privacy_section_security_body))
            Section(stringResource(R.string.privacy_section_cookies_title), stringResource(R.string.privacy_section_cookies_body))
            Section(stringResource(R.string.privacy_section_complaint_title), stringResource(R.string.privacy_section_complaint_body))
            Section(stringResource(R.string.privacy_section_changes_title), stringResource(R.string.privacy_section_changes_body))

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.privacy_version_footer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
