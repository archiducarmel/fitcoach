package com.shredcoach.app.presentation.settings.llm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.shredcoach.app.domain.llm.LlmPreset
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

            // Presets one-tap pour configurer 19 assistants en bloc
            item { PresetsRow(onPresetSelected = viewModel::applyPreset) }

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
            currentFallbackProvider = state.sheetFallbackProvider,
            currentFallbackModelId = state.sheetFallbackModelId,
            providers = viewModel.providersFor(state.editingAssistant!!),
            modelsForProvider = { p -> viewModel.availableModelsFor(p, state.editingAssistant!!) },
            onProviderSelected = viewModel::setSheetProvider,
            onModelSelected = viewModel::setSheetModel,
            onFallbackProviderSelected = viewModel::setSheetFallbackProvider,
            onFallbackModelSelected = viewModel::setSheetFallbackModel,
            onSave = viewModel::saveCurrentOverride,
            onReset = viewModel::resetCurrentOverride,
            onDismiss = viewModel::closePicker,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Row de 3 presets one-tap pour configurer 19 assistants en lot :
 *  - ECONOMIC (vert)    : tout sur les modèles low-cost
 *  - BALANCED (bleu)    : équilibre coût/qualité (defaults)
 *  - PREMIUM (orange)   : qualité max sur les assistants reasoning-heavy
 *
 * Effet visuel premium : cards avec icones distincts + couleurs tier + tap
 * applique immediatement. Notification visuelle de l'application.
 */
@Composable
private fun PresetsRow(onPresetSelected: (LlmPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.llm_settings_presets_header).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresetCard(
                icon = Icons.Default.Bolt,
                label = stringResource(R.string.llm_preset_economic),
                description = stringResource(R.string.llm_preset_economic_desc),
                tint = Color(0xFF10B981),
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(LlmPreset.ECONOMIC) },
            )
            PresetCard(
                icon = Icons.Default.AutoAwesome,
                label = stringResource(R.string.llm_preset_balanced),
                description = stringResource(R.string.llm_preset_balanced_desc),
                tint = Color(0xFF6366F1),
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(LlmPreset.BALANCED) },
            )
            PresetCard(
                icon = Icons.Default.AutoAwesome,
                label = stringResource(R.string.llm_preset_premium),
                description = stringResource(R.string.llm_preset_premium_desc),
                tint = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(LlmPreset.PREMIUM) },
            )
        }
    }
}

@Composable
private fun PresetCard(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.08f),
        modifier = modifier.border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp,
            )
        }
    }
}

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
                // Description du rôle de l'assistant (1-2 lignes max)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(assistantDescriptionRes(row.assistant)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(row.resolved.provider.displayName)
                        append(" · ")
                        append(modelInfo?.displayName ?: row.resolved.modelId)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    maxLines = 1,
                    fontWeight = FontWeight.Medium,
                )
                // Fallback chip si configure (v50)
                row.fallback?.let { fb ->
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Default.SwapHoriz, null,
                            Modifier.size(11.dp),
                            tint = Color(0xFFFF8A65),
                        )
                        Text(
                            stringResource(
                                R.string.llm_settings_fallback_chip,
                                fb.provider.displayName,
                                com.shredcoach.app.domain.llm.LlmCatalog.modelInfo(fb.modelId)?.displayName ?: fb.modelId,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color(0xFFFF8A65),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
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

/**
 * Card modele premium pour le picker Settings :
 *  - Nom + tier badge ($/$$/$$$) + cadenas si gated
 *  - Description vulgarisee via ModelDescriptions (fallback notes statiques)
 *  - Capability pills (vision/thinking/tools/code) en row condensee
 *  - Meta-row : kind emoji + publisher (creator) + parameter count + contexte
 */
@Composable
private fun ModelCard(
    model: com.shredcoach.app.domain.llm.LlmModelInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Title row : kind emoji + name + tier + gated lock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${model.kind.emoji} ${model.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                    )
                    TierBadge(model.tier)
                    if (model.isGated) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(Icons.Default.Lock, null, Modifier.size(9.dp),
                                    tint = MaterialTheme.colorScheme.error)
                                Text("Pro+", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                // Meta row : publisher · params · context
                val metaParts = buildList {
                    model.publisher?.takeIf { it.isNotBlank() }?.let {
                        add("🧪 ${prettifyMakerLabel(it)}")
                    }
                    if (model.parameterCountBillions > 0) {
                        add("${model.parameterCountBillions.toInt()}B params")
                    }
                    if (model.maxContextTokens > 0) {
                        add("${model.maxContextTokens / 1000}K ctx")
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        metaParts.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }

                // Description vulgarisee : ModelDescriptions > notes catalog
                val description = com.shredcoach.app.domain.llm.ModelDescriptions
                    .describe(model.id, model.publisher) ?: model.notes
                if (description.isNotBlank()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3,
                    )
                }

                // Capability pills (uniquement si au moins une est true)
                if (model.supportsVision || model.supportsThinking ||
                    model.supportsToolCalling || model.supportsCodeGen ||
                    model.supportsAgentic) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (model.supportsVision) CapabilityPill("👁", "Vision")
                        if (model.supportsThinking) CapabilityPill("🧠", "Reasoning")
                        if (model.supportsToolCalling) CapabilityPill("🛠", "Tools")
                        if (model.supportsCodeGen) CapabilityPill("💻", "Code")
                        if (model.supportsAgentic) CapabilityPill("⚡", "Agents")
                    }
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CapabilityPill(emoji: String, label: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Text("$emoji $label",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.5.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** "mistralai" -> "Mistral AI", "openai" -> "OpenAI", etc.
 *  Symétrique avec prettifyMaker du picker debug. */
private fun prettifyMakerLabel(maker: String): String = when (maker.lowercase()) {
    "openai" -> "OpenAI"
    "mistralai", "mistral-ai" -> "Mistral AI"
    "meta", "meta-llama" -> "Meta"
    "google" -> "Google"
    "microsoft" -> "Microsoft"
    "anthropic" -> "Anthropic"
    "deepseek", "deepseek-ai" -> "DeepSeek"
    "qwen", "alibaba" -> "Alibaba"
    "nvidia", "nv-mistralai" -> "NVIDIA"
    "cohere" -> "Cohere"
    "ibm" -> "IBM"
    "xai" -> "xAI"
    "moonshotai" -> "Moonshot"
    "minimaxai" -> "MiniMax"
    "z-ai" -> "Z.ai"
    "stepfun-ai" -> "StepFun"
    "ai21labs", "ai21-labs" -> "AI21 Labs"
    "01-ai" -> "01.AI"
    "black-forest-labs" -> "Black Forest Labs"
    "stabilityai" -> "Stability AI"
    else -> maker.replaceFirstChar { it.uppercase() }
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

/**
 * Mapping assistant → description courte (1-2 lignes) qui explique a l'user
 * ce que fait cet assistant concretement dans l'app. Affiche sous le titre
 * dans la card du Settings.
 *
 * **Tone** : action-oriented, du point de vue user, sans jargon technique.
 * Ex : "Analyse tes repas a partir d'une photo" (pas "Pipeline LLM vision
 * Gemini avec parsing JSON robuste").
 */
@androidx.annotation.StringRes
private fun assistantDescriptionRes(assistant: AiAssistant): Int = when (assistant) {
    AiAssistant.MEAL_SCAN_PHOTO -> R.string.assistant_desc_meal_scan_photo
    AiAssistant.MEAL_SCAN_TEXT -> R.string.assistant_desc_meal_scan_text
    AiAssistant.MEAL_SCAN_LEFTOVER -> R.string.assistant_desc_meal_scan_leftover
    AiAssistant.BODY_SCAN -> R.string.assistant_desc_body_scan
    AiAssistant.GYM_SCAN -> R.string.assistant_desc_gym_scan
    AiAssistant.GLUCOSE_OCR -> R.string.assistant_desc_glucose_ocr
    AiAssistant.GLUCOSE_ANALYSIS -> R.string.assistant_desc_glucose_analysis
    AiAssistant.BODY_INSIGHT -> R.string.assistant_desc_body_insight
    AiAssistant.WEEKLY_RECAP -> R.string.assistant_desc_weekly_recap
    AiAssistant.CALENDAR_RECAP -> R.string.assistant_desc_calendar_recap
    AiAssistant.CHAT_SHREDDY -> R.string.assistant_desc_chat_shreddy
    AiAssistant.CHAT_DR_GLYKOS -> R.string.assistant_desc_chat_dr_glykos
    AiAssistant.PROACTIVE_COACH -> R.string.assistant_desc_proactive_coach
    AiAssistant.WORKOUT_DEBRIEF -> R.string.assistant_desc_workout_debrief
    AiAssistant.MEAL_DEBRIEF -> R.string.assistant_desc_meal_debrief
    AiAssistant.SCHEDULED_REMINDER -> R.string.assistant_desc_scheduled_reminder
    AiAssistant.GYM_SCAN_RERANK -> R.string.assistant_desc_gym_scan_rerank
    AiAssistant.INSTRUCTIONS_TRANSLATE -> R.string.assistant_desc_instructions_translate
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
    currentFallbackProvider: LlmProvider?,
    currentFallbackModelId: String?,
    providers: List<LlmProvider>,
    modelsForProvider: (LlmProvider) -> List<com.shredcoach.app.domain.llm.LlmModelInfo>,
    onProviderSelected: (LlmProvider) -> Unit,
    onModelSelected: (String) -> Unit,
    onFallbackProviderSelected: (LlmProvider?) -> Unit,
    onFallbackModelSelected: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // Hauteur cible : 88% de l'ecran → laisse le pull-handle visible
        // ET garantit que le footer sticky a sa place reservee.
        modifier = Modifier.fillMaxHeight(0.88f),
    ) {
        // ─── Structure : contenu scrollable + footer sticky toujours visible ──
        // Sans cette structure, la section Fallback + Actions etaient tronquees
        // ou inaccessibles (cf. feedback user "boutons inaccessibles").
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f) // Prend tout l'espace restant SAUF le footer
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Column {
                Text(
                    stringResource(assistantLabelRes(assistant)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(assistantDescriptionRes(assistant)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp,
                )
                if (assistant.needsVision) {
                    Spacer(Modifier.height(6.dp))
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
                        ModelCard(
                            model = model,
                            isSelected = model.id == currentModelId,
                            onSelect = { onModelSelected(model.id) },
                        )
                    }
                }
            }

            Divider()

            // ── Section Fallback (v50) — optionnel ────────────────────────
            FallbackSection(
                assistant = assistant,
                primaryProvider = currentProvider,
                primaryModelId = currentModelId,
                fallbackProvider = currentFallbackProvider,
                fallbackModelId = currentFallbackModelId,
                providers = providers,
                modelsForProvider = modelsForProvider,
                onProviderSelected = onFallbackProviderSelected,
                onModelSelected = onFallbackModelSelected,
            )
        } // fin du Column scrollable

        // ─── Footer STICKY : Save + Reset toujours visibles ───────────────
        // Surface avec ombre haute pour separer visuellement du contenu scrollable.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
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
        } // fin du Column parent
    }
}

/**
 * Section "Fallback" en bas du sheet picker. Toggle on/off + provider + modele.
 *
 * **Logique** :
 *  - Si fallbackProvider null → toggle OFF + bouton "Activer un fallback"
 *  - Si actif → header explicatif + picker provider compact (chips) + picker modele
 *  - On exclut le provider primaire du picker fallback (sinon "fallback = primary" = no-op)
 *
 * **Pourquoi compact** : c'est un setting AVANCE, pas la fonction principale. UI
 * doit etre claire mais moins proeminente que le picker primary.
 */
@Composable
private fun FallbackSection(
    assistant: AiAssistant,
    primaryProvider: LlmProvider?,
    primaryModelId: String?,
    fallbackProvider: LlmProvider?,
    fallbackModelId: String?,
    providers: List<LlmProvider>,
    modelsForProvider: (LlmProvider) -> List<com.shredcoach.app.domain.llm.LlmModelInfo>,
    onProviderSelected: (LlmProvider?) -> Unit,
    onModelSelected: (String) -> Unit,
) {
    val fallbackEnabled = fallbackProvider != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header avec toggle
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.llm_settings_fallback_section).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Text(
                    stringResource(R.string.llm_settings_fallback_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = fallbackEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        // Auto-pick le premier provider != primary
                        val firstOther = providers.firstOrNull { it != primaryProvider }
                            ?: providers.firstOrNull()
                        onProviderSelected(firstOther)
                    } else {
                        onProviderSelected(null)
                    }
                },
            )
        }

        if (fallbackEnabled) {
            // Tous les providers sont eligibles, MEME le primary : on autorise
            // "primary Gemini 3 Preview → fallback Gemini 2.5 Flash" car les
            // quotas free tier sont per-MODELE chez Gemini/Groq/OpenAI, pas
            // per-provider. La contrainte d'unicite est sur le (provider, model)
            // complet, gere au niveau du picker modele ci-dessous.
            val fbCandidates = providers
            if (fbCandidates.isEmpty()) {
                Text(
                    stringResource(R.string.llm_settings_fallback_no_alternative),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                return
            }

            // Picker provider compact (chips horizontaux)
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(fbCandidates) { provider ->
                    val selected = provider == fallbackProvider
                    val isSameAsPrimary = provider == primaryProvider
                    Surface(
                        onClick = { onProviderSelected(provider) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (selected) androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.primary
                        ) else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                provider.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                            if (isSameAsPrimary) {
                                // Hint visuel : meme provider que le primary → l'user
                                // devra choisir un modele different ci-dessous
                                Text(
                                    "=",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }

            // Picker modele du fallback provider
            // **Critique** : si fallback provider == primary provider, on doit
            // exclure le modele primary du picker (sinon fallback = primary,
            // bascule no-op = bug subtil).
            val isSameProviderAsPrimary = fallbackProvider == primaryProvider
            val fbModels = remember(fallbackProvider, primaryProvider, primaryModelId) {
                val all = fallbackProvider?.let {
                    modelsForProvider(it).filter { m -> !assistant.needsVision || m.supportsVision }
                } ?: emptyList()
                if (isSameProviderAsPrimary && primaryModelId != null) {
                    all.filter { it.id != primaryModelId }
                } else all
            }
            if (fbModels.isEmpty()) {
                // Empty state : meme provider que primary mais aucun autre modele dispo
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.llm_settings_fallback_same_model_hint),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                fbModels.forEach { model ->
                    val selected = model.id == fallbackModelId
                    Surface(
                        onClick = { onModelSelected(model.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    model.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (model.notes.isNotBlank()) {
                                    Text(
                                        model.notes,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
