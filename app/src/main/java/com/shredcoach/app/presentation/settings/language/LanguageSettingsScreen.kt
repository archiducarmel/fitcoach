package com.shredcoach.app.presentation.settings.language

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.R
import com.shredcoach.app.domain.locale.AppLocale
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Écran picker de langue. Réutilisé depuis Settings (avec topBar back) et
 * Onboarding (sans topBar, intégré dans le step layout).
 *
 * Voir [LanguagePickerSection] pour le bloc UI réutilisable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    viewModel: LanguageSettingsViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.language_picker_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.language_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            LanguagePickerSection(viewModel = viewModel)
        }
    }
}

/**
 * Bloc picker pur (sans topBar / sans Scaffold) — réutilisable.
 *
 * Affiche TOUTES les locales (V1 + V2). Les V2 apparaissent grisées avec un
 * badge "Bientôt disponible" pour signaler le futur sans cliquer dessus.
 *
 * **Pourquoi montrer V2 dès maintenant** : le user voit l'ambition i18n de
 * l'app dès le 1er launch. C'est un signal fort pour les non-francophones
 * qu'on les supportera bientôt — diminue le bounce rate côté store EU.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSection(
    viewModel: LanguageSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val current by viewModel.currentLocale.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        viewModel.availableLocales.forEach { locale ->
            LanguageOption(
                locale = locale,
                isSelected = current == locale,
                isEnabled = locale.isV1 && !isApplying,
                onClick = { viewModel.selectLocale(locale) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageOption(
    locale: AppLocale,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        isSelected -> OrangeVibrant.copy(alpha = 0.10f)
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isSelected) BorderStroke(2.dp, OrangeVibrant)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(locale.flag, fontSize = 24.sp)
            Column(Modifier.weight(1f)) {
                Text(
                    locale.displayNameNative,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                if (!locale.isV1) {
                    Text(
                        stringResource(R.string.language_picker_hint_v2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OrangeVibrant,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp).size(16.dp),
                    )
                }
            }
        }
    }
}
