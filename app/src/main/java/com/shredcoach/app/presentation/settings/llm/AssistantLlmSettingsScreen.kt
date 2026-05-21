package com.shredcoach.app.presentation.settings.llm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.domain.llm.AiAssistant
import com.shredcoach.app.domain.llm.AiCategory
import com.shredcoach.app.domain.llm.LlmCatalog
import com.shredcoach.app.domain.llm.LlmTier

/**
 * Écran Settings dédié à la configuration LLM per-assistant.
 *
 * 4 catégories d'assistants (Chat, Vision, Analyse, Background), chaque ligne
 * montre le provider + modèle actuellement résolus. Tap → bottom sheet avec
 * deux dropdowns (provider, modèle) + bouton "Réinitialiser au défaut".
 *
 * **Pattern UX premium** :
 *  - Pill "Personnalisé" visible si l'assistant a un override défini
 *  - Tier badge (Premium / Économique) sur le modèle pour orienter le choix
 *  - Notes courtes sous chaque modèle dans le picker (e.g., "5× plus cher")
 *  - Bouton "Réinitialiser tous les défauts" en bas pour un nettoyage rapide
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantLlmSettingsScreen(
    navController: NavController,
    viewModel: AssistantLlmSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.llm_settings_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { IntroCard() }

            state.rowsByCategory.forEach { (category, rows) ->
                item { CategoryHeader(category) }
                items(rows, key = { it.assistant.key }) { row ->
                    AssistantRow(
                        row = row,
                        onClick = { viewModel.openPickerFor(row.assistant) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetAllOverrides() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.llm_settings_reset_all),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (state.editingAssistant != null) {
        AssistantLlmPickerSheet(
            assistant = state.editingAssistant!!,
            currentProvider = state.sheetProvider,
            currentModelId = state.sheetModelId,
            providers = viewModel.providersFor(state.editingAssistant!!),
            modelsForProvider = { p -> viewModel.availableModelsFor(p, state.editingAssistant!!.needsVision) },
            onProviderSelected = viewModel::setSheetProvider,
            onModelSelected = viewModel::setSheetModel,
            onSave = viewModel::saveCurrentOverride,
            onReset = viewModel::resetCurrentOverride,
            onDismiss = viewModel::closePicker,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun IntroCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.llm_settings_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: AiCategory) {
    val (icon, labelRes) = when (category) {
        AiCategory.CHAT -> Icons.Default.Chat to R.string.llm_settings_category_chat
        AiCategory.VISION -> Icons.Default.PhotoCamera to R.string.llm_settings_category_vision
        AiCategory.ANALYSIS -> Icons.Default.MonitorHeart to R.string.llm_settings_category_analysis
        AiCategory.BACKGROUND -> Icons.Default.WorkOutline to R.string.llm_settings_category_background
    }
    Row(
        Modifier.padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(labelRes).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun AssistantRow(
    row: AssistantRowState,
    onClick: () -> Unit,
) {
    val modelInfo = LlmCatalog.modelInfo(row.resolved.modelId)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (row.isOverridden) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(assistantLabelRes(row.assistant)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (row.isOverridden) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.llm_settings_custom_pill),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(row.resolved.provider.displayName)
                        append(" · ")
                        append(modelInfo?.displayName ?: row.resolved.modelId)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                )
            }
            // Tier badge
            modelInfo?.let { TierBadge(it.tier) }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun TierBadge(tier: LlmTier) {
    val (label, bg, fg) = when (tier) {
        LlmTier.ECONOMIC -> Triple("$", Color(0xFFD1FAE5), Color(0xFF065F46))
        LlmTier.STANDARD -> Triple("$$", Color(0xFFE0E7FF), Color(0xFF3730A3))
        LlmTier.PREMIUM -> Triple("$$$", Color(0xFFFEF3C7), Color(0xFF92400E))
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

private fun assistantLabelRes(assistant: AiAssistant): Int = when (assistant) {
    AiAssistant.MEAL_SCAN_PHOTO -> R.string.assistant_meal_scan_photo
    AiAssistant.MEAL_SCAN_TEXT -> R.string.assistant_meal_scan_text
    AiAssistant.MEAL_SCAN_LEFTOVER -> R.string.assistant_meal_scan_leftover
    AiAssistant.BODY_SCAN -> R.string.assistant_body_scan
    AiAssistant.GYM_SCAN -> R.string.assistant_gym_scan
    AiAssistant.GLUCOSE_OCR -> R.string.assistant_glucose_ocr
    AiAssistant.GLUCOSE_ANALYSIS -> R.string.assistant_glucose_analysis
    AiAssistant.BODY_INSIGHT -> R.string.assistant_body_insight
    AiAssistant.WEEKLY_RECAP -> R.string.assistant_weekly_recap
    AiAssistant.CALENDAR_RECAP -> R.string.assistant_calendar_recap
    AiAssistant.CHAT_SHREDDY -> R.string.assistant_chat_shreddy
    AiAssistant.CHAT_DR_GLYKOS -> R.string.assistant_chat_dr_glykos
    AiAssistant.PROACTIVE_COACH -> R.string.assistant_proactive_coach
    AiAssistant.WORKOUT_DEBRIEF -> R.string.assistant_workout_debrief
    AiAssistant.MEAL_DEBRIEF -> R.string.assistant_meal_debrief
    AiAssistant.SCHEDULED_REMINDER -> R.string.assistant_scheduled_reminder
    AiAssistant.GYM_SCAN_RERANK -> R.string.assistant_gym_scan_rerank
    AiAssistant.INSTRUCTIONS_TRANSLATE -> R.string.assistant_instructions_translate
}

// ═══════════════════════════════════════════════════════════════════════════
// BOTTOM SHEET PICKER
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantLlmPickerSheet(
    assistant: AiAssistant,
    currentProvider: LlmProvider?,
    currentModelId: String?,
    providers: List<LlmProvider>,
    modelsForProvider: (LlmProvider) -> List<com.shredcoach.app.domain.llm.LlmModelInfo>,
    onProviderSelected: (LlmProvider) -> Unit,
    onModelSelected: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Column {
                Text(
                    stringResource(assistantLabelRes(assistant)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (assistant.needsVision) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.llm_settings_vision_required),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Divider()

            // Provider section
            Text(
                stringResource(R.string.llm_settings_section_provider).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.forEach { provider ->
                    val isSelected = provider == currentProvider
                    Surface(
                        onClick = { onProviderSelected(provider) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp),
                            ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                provider.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            // Model section
            Text(
                stringResource(R.string.llm_settings_section_model).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            val models = remember(currentProvider) { currentProvider?.let { modelsForProvider(it) } ?: emptyList() }
            if (models.isEmpty()) {
                Text(
                    stringResource(R.string.llm_settings_no_model),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.forEach { model ->
                        val isSelected = model.id == currentModelId
                        Surface(
                            onClick = { onModelSelected(model.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            model.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                        )
                                        TierBadge(model.tier)
                                    }
                                    if (model.notes.isNotBlank()) {
                                        Text(
                                            model.notes,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle, null,
                                        Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // Actions
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.llm_settings_reset_one),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = currentProvider != null && !currentModelId.isNullOrBlank(),
                ) {
                    Text(
                        stringResource(R.string.common_save),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
