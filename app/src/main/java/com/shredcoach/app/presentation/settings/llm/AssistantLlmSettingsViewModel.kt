package com.shredcoach.app.presentation.settings.llm

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.llm.AiAssistant
import com.shredcoach.app.domain.llm.AiCategory
import com.shredcoach.app.domain.llm.AssistantLlmResolver
import com.shredcoach.app.domain.llm.LlmCatalog
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.LlmTier
import com.shredcoach.app.domain.llm.ResolvedLlmConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État d'une ligne assistant dans l'écran Settings.
 *
 * `isOverridden` = true si l'user a explicitement défini un override (la config
 * affichée ne vient pas du fallback legacy mais de la map JSON).
 */
@Immutable
data class AssistantRowState(
    val assistant: AiAssistant,
    val resolved: ResolvedLlmConfig,
    val isOverridden: Boolean,
)

@Immutable
data class AssistantLlmSettingsState(
    /** Rows groupées par catégorie pour le rendu UI. */
    val rowsByCategory: Map<AiCategory, List<AssistantRowState>> = emptyMap(),
    val isLoading: Boolean = true,
    /** Assistant actuellement édité (bottom sheet ouvert). Null = fermé. */
    val editingAssistant: AiAssistant? = null,
    /** Provider sélectionné dans la sheet (peut différer du resolved si changement en cours). */
    val sheetProvider: LlmProvider? = null,
    /** Model id sélectionné dans la sheet. */
    val sheetModelId: String? = null,
)

@HiltViewModel
class AssistantLlmSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val llmResolver: AssistantLlmResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantLlmSettingsState())
    val state: StateFlow<AssistantLlmSettingsState> = _state.asStateFlow()

    init {
        reload()
    }

    /**
     * Recharge le profile + résout l'état pour chaque assistant. Appelé au
     * init et après chaque mutation (save override / reset).
     */
    private fun reload() {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val overridesJson = profile?.llmAssistantOverridesJson.orEmpty()
            val rows = AiAssistant.values().map { assistant ->
                val resolved = llmResolver.resolveWithProfile(assistant, profile)
                AssistantRowState(
                    assistant = assistant,
                    resolved = resolved,
                    isOverridden = isOverridden(overridesJson, assistant.key),
                )
            }
            val grouped = rows.groupBy { it.assistant.category }
            // Ordre fixe des catégories
            val orderedKeys = listOf(AiCategory.CHAT, AiCategory.VISION, AiCategory.ANALYSIS, AiCategory.BACKGROUND)
            val orderedMap = orderedKeys.associateWith { (grouped[it] ?: emptyList()) }
                .filterValues { it.isNotEmpty() }
            _state.update {
                it.copy(
                    rowsByCategory = orderedMap,
                    isLoading = false,
                )
            }
        }
    }

    /** Ouvre la bottom sheet pour configurer un assistant. */
    fun openPickerFor(assistant: AiAssistant) {
        val row = _state.value.rowsByCategory.values.flatten().firstOrNull { it.assistant == assistant }
            ?: return
        _state.update {
            it.copy(
                editingAssistant = assistant,
                sheetProvider = row.resolved.provider,
                sheetModelId = row.resolved.modelId,
            )
        }
    }

    /** Ferme la bottom sheet sans sauver. */
    fun closePicker() {
        _state.update {
            it.copy(
                editingAssistant = null,
                sheetProvider = null,
                sheetModelId = null,
            )
        }
    }

    /** Change le provider dans la sheet — auto-pick le premier modèle dispo. */
    fun setSheetProvider(provider: LlmProvider) {
        val firstModel = LlmCatalog.modelsFor(provider).firstOrNull()
        _state.update {
            it.copy(
                sheetProvider = provider,
                sheetModelId = firstModel?.id,
            )
        }
    }

    /** Change le modèle dans la sheet. */
    fun setSheetModel(modelId: String) {
        _state.update { it.copy(sheetModelId = modelId) }
    }

    /**
     * Sauve l'override courant (sheet state) pour l'assistant en cours d'édition.
     * Ferme la sheet et recharge.
     */
    fun saveCurrentOverride() {
        val s = _state.value
        val assistant = s.editingAssistant ?: return
        val provider = s.sheetProvider ?: return
        val modelId = s.sheetModelId?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            val newJson = llmResolver.writeOverride(
                currentJson = profile.llmAssistantOverridesJson,
                assistant = assistant,
                config = ResolvedLlmConfig(provider, modelId),
            )
            userRepository.updateUserProfile(profile.copy(llmAssistantOverridesJson = newJson))
            closePicker()
            reload()
        }
    }

    /**
     * Reset l'override de l'assistant en cours d'édition (retour au défaut
     * legacy). Ferme la sheet.
     */
    fun resetCurrentOverride() {
        val assistant = _state.value.editingAssistant ?: return
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            val newJson = llmResolver.writeOverride(
                currentJson = profile.llmAssistantOverridesJson,
                assistant = assistant,
                config = null,
            )
            userRepository.updateUserProfile(profile.copy(llmAssistantOverridesJson = newJson))
            closePicker()
            reload()
        }
    }

    /**
     * Reset TOUS les overrides → tous les assistants reviennent au comportement
     * legacy. Utilisé par le bouton "Reset tout" en bas de l'écran.
     */
    fun resetAllOverrides() {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            userRepository.updateUserProfile(profile.copy(llmAssistantOverridesJson = "{}"))
            reload()
        }
    }

    private fun isOverridden(json: String, key: String): Boolean {
        if (json.isBlank() || json == "{}") return false
        return runCatching {
            val root = com.google.gson.JsonParser.parseString(json).asJsonObject
            root.has(key)
        }.getOrDefault(false)
    }

    /** Helper exposé pour la UI pour la liste des providers + modèles dispo. */
    fun availableModelsFor(provider: LlmProvider, needsVision: Boolean): List<LlmModelInfo> {
        val all = LlmCatalog.modelsFor(provider)
        return if (needsVision) all.filter { it.supportsVision } else all
    }

    /** Providers utilisables pour un assistant (filtre par vision si requis). */
    fun providersFor(assistant: AiAssistant): List<LlmProvider> {
        val all = LlmProvider.values().toList()
        return if (assistant.needsVision) all.filter { provider ->
            LlmCatalog.modelsFor(provider).any { it.supportsVision }
        } else all
    }
}
