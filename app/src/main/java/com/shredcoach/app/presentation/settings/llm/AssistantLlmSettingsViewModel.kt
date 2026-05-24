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
import com.shredcoach.app.domain.llm.ModelKind
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
    /** Fallback configure (null = pas de fallback, propage l'erreur primary). */
    val fallback: ResolvedLlmConfig? = null,
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
    /** Provider fallback selectionne dans la sheet (null = pas de fallback). */
    val sheetFallbackProvider: LlmProvider? = null,
    /** Model fallback. */
    val sheetFallbackModelId: String? = null,
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
                val fb = llmResolver.resolveFallbackWithProfile(assistant, profile)
                AssistantRowState(
                    assistant = assistant,
                    resolved = resolved,
                    isOverridden = isOverridden(overridesJson, assistant.key),
                    fallback = fb,
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
                sheetFallbackProvider = row.fallback?.provider,
                sheetFallbackModelId = row.fallback?.modelId,
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
                sheetFallbackProvider = null,
                sheetFallbackModelId = null,
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
     * Change le fallback provider — auto-pick premier modele DIFFERENT du
     * primary (si meme provider, sinon le premier dispo). Null = pas de fallback.
     *
     * **Critique** : si on auto-pickait simplement le premier modele du provider,
     * et que ce modele est le meme que le primary (cas typique : primary =
     * Gemini 2.5 Flash, fallback provider = Gemini), on aurait fallback = primary,
     * une bascule no-op silencieuse. On filtre donc explicitement.
     */
    fun setSheetFallbackProvider(provider: LlmProvider?) {
        if (provider == null) {
            _state.update { it.copy(sheetFallbackProvider = null, sheetFallbackModelId = null) }
            return
        }
        val s = _state.value
        val primaryModelId = s.sheetModelId
        val sameAsPrimary = provider == s.sheetProvider
        val candidates = LlmCatalog.modelsFor(provider)
        val pick = if (sameAsPrimary && primaryModelId != null) {
            candidates.firstOrNull { it.id != primaryModelId }
        } else {
            candidates.firstOrNull()
        }
        _state.update {
            it.copy(
                sheetFallbackProvider = provider,
                sheetFallbackModelId = pick?.id,
            )
        }
    }

    /** Change le modele fallback. */
    fun setSheetFallbackModel(modelId: String) {
        _state.update { it.copy(sheetFallbackModelId = modelId) }
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
        val fbProvider = s.sheetFallbackProvider
        val fbModelId = s.sheetFallbackModelId?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            // Sauve primary
            var newJson = llmResolver.writeOverride(
                currentJson = profile.llmAssistantOverridesJson,
                assistant = assistant,
                config = ResolvedLlmConfig(provider, modelId),
            )
            // Sauve fallback (ou clear si null)
            val fbConfig = if (fbProvider != null && fbModelId != null) {
                ResolvedLlmConfig(fbProvider, fbModelId)
            } else null
            newJson = llmResolver.writeFallbackOverride(
                currentJson = newJson,
                assistant = assistant,
                config = fbConfig,
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

    /**
     * Applique un preset (ECONOMIC / BALANCED / PREMIUM) → override les 19
     * assistants en lot. Réversible via "Reinitialiser tout" qui revide la map.
     */
    fun applyPreset(preset: com.shredcoach.app.domain.llm.LlmPreset) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            val newJson = preset.buildOverridesJson()
            userRepository.updateUserProfile(profile.copy(llmAssistantOverridesJson = newJson))
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

    /**
     * Helper expose pour la UI : modeles d'un provider compatibles avec
     * l'assistant. Filtre par :
     *  1. **Kind requis** (LANGUAGE pour chat/analyse/background, VLM pour vision)
     *     -> evite que l'user pick `meta/llama-guard-4-12b` (CLASSIFICATION) ou
     *     `google/deplot` (OCR) pour un assistant chat (renverrait verdict
     *     moderation/JSON, pas reponse coach).
     *  2. **supportsVision** si l'assistant a needsVision (back-compat avec
     *     l'ancien comportement, mais redondant avec kind=VLM en pratique).
     *
     * Pour les **assistants vision**, on accepte LANGUAGE + VLM (un modele LANGUAGE
     * compatible vision est rare mais possible — supportsVision est la verite).
     */
    fun availableModelsFor(provider: LlmProvider, assistant: AiAssistant): List<LlmModelInfo> {
        val all = LlmCatalog.modelsFor(provider)
        return if (assistant.needsVision) {
            all.filter { it.supportsVision }
        } else {
            // Pour chat/analyse/background : LANGUAGE ou VLM (VLM peut aussi
            // gerer du chat texte). On exclut EMBEDDING/OCR/CLASSIFICATION/
            // IMAGE_GENERATION/VIDEO_GENERATION/TTS/STT/REWARD_MODEL/SCIENTIFIC
            // /OPTIMIZATION/OBJECT_DETECTION/MULTIMODAL_EMBEDDING/RERANKER —
            // qui sont des modeles specialises non utilisables comme assistant chat.
            all.filter {
                it.kind == ModelKind.LANGUAGE || it.kind == ModelKind.VLM
            }
        }
    }

    /** @deprecated Utiliser [availableModelsFor] avec l'assistant. */
    @Deprecated("Use availableModelsFor(provider, assistant)")
    fun availableModelsFor(provider: LlmProvider, needsVision: Boolean): List<LlmModelInfo> {
        val all = LlmCatalog.modelsFor(provider)
        return if (needsVision) all.filter { it.supportsVision } else all
    }

    /**
     * Providers utilisables pour un assistant. **Pipeline reality check** :
     *  - Pour les **assistants VISION** (needsVision=true), le pipeline actuel
     *    (GeminiMealService.analyzeMealPhoto/BodyAnalysisService) ne supporte
     *    QUE Gemini + Mistral comme backends. GitHub Models + NVIDIA NIM ont
     *    des modeles vision (gpt-4o, llama-3.2-vision, qwen3.5...) mais le
     *    pipeline ne les route pas encore -> on les exclut pour eviter un
     *    crash silencieux (401 sur cle Gemini quand provider != GEMINI/MISTRAL).
     *  - Pour les **assistants chat/analyse/background** (needsVision=false),
     *    LlmApiService.streamMessage gere tous les providers OpenAI-compat
     *    (GROQ, OPENAI, GITHUB_MODELS, NVIDIA_NIM) + GEMINI/CLAUDE specifiques.
     *    POLLINATIONS + CLOUDFLARE_AI = image-gen only, exclus pour chat.
     *
     * @return providers qui ont AU MOINS un modele utilisable pour cet assistant
     *  ET dont le pipeline est actuellement supporte.
     */
    fun providersFor(assistant: AiAssistant): List<LlmProvider> {
        val all = LlmProvider.values().toList()
        return all.filter { provider ->
            // Pollinations + Cloudflare = image-gen only, jamais utilisables
            // comme backend chat/vision assistant.
            if (!provider.supportsChat && assistant.category != AiCategory.VISION) return@filter false
            if (provider == LlmProvider.POLLINATIONS || provider == LlmProvider.CLOUDFLARE_AI) {
                return@filter false
            }
            // Pour vision : pipeline GeminiMealService route SEULEMENT vers
            // GEMINI/MISTRAL. Pas de support GitHub/NVIDIA vision pour l'instant.
            if (assistant.needsVision) {
                return@filter provider == LlmProvider.GEMINI || provider == LlmProvider.MISTRAL
            }
            // Pour non-vision : au moins un modele du provider doit etre
            // LANGUAGE ou VLM (i.e. utilisable comme backend chat).
            availableModelsFor(provider, assistant).isNotEmpty()
        }
    }
}
