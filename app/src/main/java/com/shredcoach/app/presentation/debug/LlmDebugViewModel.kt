package com.shredcoach.app.presentation.debug

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmApiService
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.remote.ChatMessage as ApiChatMessage
import com.shredcoach.app.data.remote.CloudflareAiService
import com.shredcoach.app.data.remote.EmbeddingService
import com.shredcoach.app.data.remote.GitHubModelsCatalogService
import com.shredcoach.app.data.remote.ImageGenerationService
import com.shredcoach.app.data.remote.NvidiaNimCatalogService
import com.shredcoach.app.data.remote.PollinationsService
import com.shredcoach.app.data.remote.SttService
import com.shredcoach.app.data.remote.TtsService
import com.shredcoach.app.domain.llm.LlmCatalog
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.ModelKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * ViewModel du playground debug — orchestre la liste des modeles (statiques +
 * dynamiques GitHub), la selection courante, la conversation chat, et les
 * appels services multi-modalite (Embedding/Image/TTS/STT en Commit F).
 *
 * **Persistence** : aucune (volontairement). C'est un playground temporaire.
 * Les API keys vivent dans SecureKeyStore (chiffre). Les conversations sont
 * en memoire uniquement — perdues au kill app, c'est OK pour du test.
 */
@HiltViewModel
class LlmDebugViewModel @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
    private val llmApiService: LlmApiService,
    private val githubCatalog: GitHubModelsCatalogService,
    private val nvidiaCatalog: NvidiaNimCatalogService,
    private val embeddingService: EmbeddingService,
    private val imageGenService: ImageGenerationService,
    private val ttsService: TtsService,
    private val sttService: SttService,
    private val pollinationsService: PollinationsService,
    private val cloudflareAiService: CloudflareAiService,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmDebugState())
    val state: StateFlow<LlmDebugState> = _state.asStateFlow()

    private var currentStreamJob: Job? = null

    init {
        // Charge les modeles statiques au demarrage (catalogue local sans I/O).
        val staticModels = LlmCatalog.byProvider.flatMap { (provider, models) ->
            models.map { ResolvedModel(provider = provider, info = it) }
        }
        _state.update { it.copy(staticModels = staticModels) }
        // Restore les API keys depuis le keystore (sans afficher leur valeur)
        refreshApiKeyAvailability()
    }

    // ─── Catalog management ─────────────────────────────────────────────────

    /** Refetch le catalogue GitHub Models (force HTTP, bypass cache 24h). */
    fun refreshGitHubCatalog() {
        val token = secureKeyStore.getKey(SecureKeyStore.Provider.GITHUB_MODELS)
        if (token.isBlank()) {
            _state.update { it.copy(catalogError = "Token GitHub manquant. Configure-le d'abord.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isFetchingCatalog = true, catalogError = null) }
            val result = githubCatalog.fetchCatalog(token, forceRefresh = true)
            result.fold(
                onSuccess = { models ->
                    val resolved = models.map { ResolvedModel(LlmProvider.GITHUB_MODELS, it) }
                    _state.update {
                        it.copy(
                            dynamicGitHubModels = resolved,
                            isFetchingCatalog = false,
                            catalogError = null,
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isFetchingCatalog = false,
                            catalogError = err.message ?: "Echec inconnu",
                        )
                    }
                },
            )
        }
    }

    /**
     * Refetch le catalogue NVIDIA NIM : fetch `/v1/models` pour determiner les
     * IDs accessibles a la cle, puis intersection avec [NvidiaNimCatalog]
     * editorialise. Cache 24h.
     */
    fun refreshNvidiaCatalog() {
        val apiKey = secureKeyStore.getKey(SecureKeyStore.Provider.NVIDIA_NIM)
        if (apiKey.isBlank()) {
            _state.update { it.copy(catalogError = "Clé NVIDIA manquante. Configure-la d'abord.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isFetchingCatalog = true, catalogError = null) }
            val result = nvidiaCatalog.fetchCatalog(apiKey, forceRefresh = true)
            result.fold(
                onSuccess = { models ->
                    val resolved = models.map { ResolvedModel(LlmProvider.NVIDIA_NIM, it) }
                    _state.update {
                        it.copy(
                            dynamicNvidiaModels = resolved,
                            isFetchingCatalog = false,
                            catalogError = null,
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isFetchingCatalog = false,
                            catalogError = err.message ?: "Echec inconnu (NVIDIA)",
                        )
                    }
                },
            )
        }
    }

    /** Rafraichit les 2 catalogues (GitHub + NVIDIA) si keys configurees. */
    fun refreshAllCatalogs() {
        if (secureKeyStore.hasKey(SecureKeyStore.Provider.GITHUB_MODELS)) refreshGitHubCatalog()
        if (secureKeyStore.hasKey(SecureKeyStore.Provider.NVIDIA_NIM)) refreshNvidiaCatalog()
    }

    // ─── API keys ───────────────────────────────────────────────────────────

    fun saveApiKey(provider: SecureKeyStore.Provider, value: String) {
        secureKeyStore.setKey(provider, value.trim())
        refreshApiKeyAvailability()
        // Si une cle vient d'arriver, refresh le catalogue correspondant automatiquement
        if (value.isNotBlank()) {
            when (provider) {
                SecureKeyStore.Provider.GITHUB_MODELS -> refreshGitHubCatalog()
                SecureKeyStore.Provider.NVIDIA_NIM -> refreshNvidiaCatalog()
                else -> { /* no-op pour les autres slots */ }
            }
        }
    }

    fun clearApiKey(provider: SecureKeyStore.Provider) {
        secureKeyStore.clear(provider)
        refreshApiKeyAvailability()
    }

    /**
     * Met a jour l'etat avec la disponibilite (non-blank) des cles par provider.
     * On NE PAS expose la valeur reelle dans le state — uniquement un boolean
     * "presente ou pas" pour eviter de leak la cle via state inspection.
     */
    private fun refreshApiKeyAvailability() {
        val avail = SecureKeyStore.Provider.values()
            .associateWith { secureKeyStore.hasKey(it) }
        _state.update { it.copy(apiKeyAvailable = avail) }
    }

    // ─── Model selection ────────────────────────────────────────────────────

    fun selectModel(resolved: ResolvedModel) {
        _state.update {
            it.copy(
                selectedModel = resolved,
                messages = emptyList(), // reset chat sur change de modele
                lastError = null,
                pickerOpen = false,
            )
        }
    }

    fun togglePicker() {
        _state.update { it.copy(pickerOpen = !it.pickerOpen) }
    }

    fun setPickerSearch(query: String) {
        _state.update { it.copy(pickerSearch = query) }
    }

    fun setPickerKindFilter(kind: ModelKind?) {
        _state.update { it.copy(pickerKindFilter = kind) }
    }

    fun setPickerProviderFilter(provider: LlmProvider?) {
        _state.update { it.copy(pickerProviderFilter = provider) }
    }

    fun togglePickerHideGated() {
        _state.update { it.copy(pickerHideGated = !it.pickerHideGated) }
    }

    // ─── Chat (CHAT + VLM) ──────────────────────────────────────────────────

    /**
     * Envoie un message texte (+ optionnel image pour VLM) au modele selectionne.
     * Streaming SSE pour CHAT/VLM via LlmApiService.streamMessage.
     */
    fun sendMessage(text: String, imageBitmap: Bitmap? = null) {
        val resolved = _state.value.selectedModel ?: run {
            _state.update { it.copy(lastError = "Selectionne un modele d'abord.") }
            return
        }
        if (text.isBlank() && imageBitmap == null) return

        // Get API key for the provider
        val keySlot = apiKeySlotFor(resolved.provider)
        val apiKey = secureKeyStore.getKey(keySlot)
        if (apiKey.isBlank()) {
            _state.update { it.copy(lastError = "Cle API manquante pour ${resolved.provider.displayName}.") }
            return
        }

        // Build user message (with image if present)
        val imageBytes = imageBitmap?.let { bmp ->
            ByteArrayOutputStream().apply { bmp.compress(Bitmap.CompressFormat.JPEG, 85, this) }.toByteArray()
        }
        val userMsg = DebugChatMessage(
            role = "user",
            text = text,
            imageBytes = imageBytes,
            provider = resolved.provider,
            model = resolved.info.id,
            timestampMs = System.currentTimeMillis(),
        )

        // Reserve placeholder assistant message (will be streamed into)
        val assistantPlaceholder = DebugChatMessage(
            role = "assistant",
            text = "",
            isStreaming = true,
            provider = resolved.provider,
            model = resolved.info.id,
            timestampMs = System.currentTimeMillis(),
        )

        _state.update {
            it.copy(
                messages = it.messages + userMsg + assistantPlaceholder,
                isSending = true,
                lastError = null,
            )
        }

        currentStreamJob?.cancel()
        currentStreamJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            try {
                // Pour VLM, on construit le content sous forme array avec text + image
                // Mais LlmApiService.streamMessage utilise un simple format text-only.
                // Pour V1, on convertit l'image en payload OpenAI standard via
                // un message synthese : "Image attachee (decodee par le modele):" + content blocks.
                // En realite, LlmApiService.streamOpenAiCompatible accepte des ChatMessage
                // text-only. Pour vraiment supporter VLM, on doit etendre LlmApiService.
                // V1 simplification : on envoie juste le texte et on prefixe une note
                // si image attached. La vraie support VLM image content viendra avec un
                // payload builder dedie (TODO Commit F).
                val effectiveText = if (imageBytes != null) {
                    "[IMAGE ATTACHEE - $imageBytes.size bytes]\n$text"
                } else text

                val messages = listOf(ApiChatMessage(role = "user", content = effectiveText))
                val responseBuf = StringBuilder()

                llmApiService.streamMessage(
                    messages = messages,
                    provider = resolved.provider,
                    apiKey = apiKey,
                    model = resolved.info.id,
                    overrideSystemPrompt = "Tu es un assistant IA en mode test. Reponds naturellement.",
                    slowMode = false,
                ).collect { token ->
                    responseBuf.append(token)
                    // Update in-place le dernier message (assistant placeholder)
                    _state.update { st ->
                        val msgs = st.messages.toMutableList()
                        val idx = msgs.lastIndex
                        if (idx >= 0 && msgs[idx].role == "assistant") {
                            msgs[idx] = msgs[idx].copy(text = responseBuf.toString())
                        }
                        st.copy(messages = msgs)
                    }
                }
                val latency = System.currentTimeMillis() - startMs
                // Finalize message (stop streaming indicator + add metadata)
                _state.update { st ->
                    val msgs = st.messages.toMutableList()
                    val idx = msgs.lastIndex
                    if (idx >= 0 && msgs[idx].role == "assistant") {
                        msgs[idx] = msgs[idx].copy(
                            text = responseBuf.toString(),
                            isStreaming = false,
                            latencyMs = latency,
                            tokensOutput = responseBuf.length / 4, // estimation (LlmApiService streams sans usage block exact)
                            tokensInput = effectiveText.length / 4,
                        )
                    }
                    st.copy(messages = msgs, isSending = false)
                }
            } catch (e: Exception) {
                _state.update { st ->
                    val msgs = st.messages.toMutableList()
                    val idx = msgs.lastIndex
                    if (idx >= 0 && msgs[idx].role == "assistant") {
                        msgs[idx] = msgs[idx].copy(
                            isStreaming = false,
                            error = e.message ?: "Echec inconnu",
                        )
                    }
                    st.copy(messages = msgs, isSending = false, lastError = e.message)
                }
            }
        }
    }

    fun cancelStream() {
        currentStreamJob?.cancel()
        currentStreamJob = null
        _state.update {
            val msgs = it.messages.toMutableList()
            val idx = msgs.lastIndex
            if (idx >= 0 && msgs[idx].role == "assistant" && msgs[idx].isStreaming) {
                msgs[idx] = msgs[idx].copy(isStreaming = false, error = "Annule")
            }
            it.copy(messages = msgs, isSending = false)
        }
    }

    fun resetSession() {
        currentStreamJob?.cancel()
        _state.update { it.copy(messages = emptyList(), lastError = null, isSending = false) }
    }

    fun dismissError() {
        _state.update { it.copy(lastError = null) }
    }

    // ─── EMBEDDING ──────────────────────────────────────────────────────────

    fun generateEmbedding(text: String) {
        val resolved = _state.value.selectedModel ?: return
        val apiKey = secureKeyStore.getKey(apiKeySlotFor(resolved.provider))
        if (apiKey.isBlank()) {
            _state.update { it.copy(lastError = "Cle API manquante.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, lastError = null, embeddingResult = null) }
            val result = embeddingService.embedText(
                input = text, model = resolved.info.id, provider = resolved.provider, apiKey = apiKey,
            )
            result.fold(
                onSuccess = { r ->
                    _state.update { it.copy(isSending = false, embeddingResult = r) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isSending = false, lastError = e.message) }
                },
            )
        }
    }

    // ─── IMAGE GENERATION (txt2img + img2img, multi-providers) ──────────────

    /**
     * Genere une image txt2img. Route vers le service selon le provider :
     *  - POLLINATIONS  → PollinationsService (no auth)
     *  - CLOUDFLARE_AI → CloudflareAiService.txt2img (Account ID + token)
     *  - autres        → ImageGenerationService (OpenAI-compatible)
     */
    fun generateImage(prompt: String, size: String = "1024x1024", sourceImageBytes: ByteArray? = null) {
        val resolved = _state.value.selectedModel ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, lastError = null, imageResult = null) }
            val (w, h) = parseSize(size)
            val result = when (resolved.provider) {
                LlmProvider.POLLINATIONS -> pollinationsService.generate(
                    prompt = prompt, model = resolved.info.id,
                    width = w, height = h,
                )
                LlmProvider.CLOUDFLARE_AI -> {
                    val token = secureKeyStore.getKey(SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN)
                    val accountId = secureKeyStore.getKey(SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID)
                    if (token.isBlank() || accountId.isBlank()) {
                        _state.update { it.copy(isSending = false, lastError = "Cloudflare : Token + Account ID requis.") }
                        return@launch
                    }
                    if (resolved.info.acceptsImageInput && sourceImageBytes != null) {
                        cloudflareAiService.img2img(
                            prompt = prompt, sourceImageBytes = sourceImageBytes,
                            model = resolved.info.id, accountId = accountId, token = token,
                            outputWidth = w, outputHeight = h,
                        )
                    } else {
                        cloudflareAiService.txt2img(
                            prompt = prompt, model = resolved.info.id,
                            accountId = accountId, token = token,
                        )
                    }
                }
                else -> {
                    val apiKey = secureKeyStore.getKey(apiKeySlotFor(resolved.provider))
                    if (apiKey.isBlank()) {
                        _state.update { it.copy(isSending = false, lastError = "Cle API manquante.") }
                        return@launch
                    }
                    imageGenService.generate(
                        prompt = prompt, model = resolved.info.id,
                        provider = resolved.provider, apiKey = apiKey, size = size,
                    )
                }
            }
            result.fold(
                onSuccess = { r -> _state.update { it.copy(isSending = false, imageResult = r) } },
                onFailure = { e -> _state.update { it.copy(isSending = false, lastError = e.message) } },
            )
        }
    }

    private fun parseSize(s: String): Pair<Int, Int> {
        val parts = s.split('x', 'X', '×')
        val w = parts.getOrNull(0)?.toIntOrNull() ?: 1024
        val h = parts.getOrNull(1)?.toIntOrNull() ?: 1024
        return w to h
    }

    // ─── TTS ────────────────────────────────────────────────────────────────

    fun synthesizeTts(text: String, voice: String = "default", format: String = "mp3") {
        val resolved = _state.value.selectedModel ?: return
        val apiKey = secureKeyStore.getKey(apiKeySlotFor(resolved.provider))
        if (apiKey.isBlank()) {
            _state.update { it.copy(lastError = "Cle API manquante.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, lastError = null, ttsResult = null) }
            val result = ttsService.synthesize(
                text = text, model = resolved.info.id, provider = resolved.provider,
                apiKey = apiKey, voice = voice, format = format,
            )
            result.fold(
                onSuccess = { r -> _state.update { it.copy(isSending = false, ttsResult = r) } },
                onFailure = { e -> _state.update { it.copy(isSending = false, lastError = e.message) } },
            )
        }
    }

    // ─── STT ────────────────────────────────────────────────────────────────

    fun transcribeAudio(file: java.io.File, mimeType: String, language: String? = null) {
        val resolved = _state.value.selectedModel ?: return
        val apiKey = secureKeyStore.getKey(apiKeySlotFor(resolved.provider))
        if (apiKey.isBlank()) {
            _state.update { it.copy(lastError = "Cle API manquante.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, lastError = null, sttResult = null) }
            val result = sttService.transcribe(
                audioFile = file, mimeType = mimeType, model = resolved.info.id,
                provider = resolved.provider, apiKey = apiKey, language = language,
                withTimestamps = true,
            )
            result.fold(
                onSuccess = { r -> _state.update { it.copy(isSending = false, sttResult = r) } },
                onFailure = { e -> _state.update { it.copy(isSending = false, lastError = e.message) } },
            )
        }
    }

    fun clearKindResults() {
        _state.update {
            it.copy(
                embeddingResult = null, imageResult = null,
                ttsResult = null, sttResult = null,
            )
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun apiKeySlotFor(provider: LlmProvider): SecureKeyStore.Provider = when (provider) {
        LlmProvider.GITHUB_MODELS -> SecureKeyStore.Provider.GITHUB_MODELS
        LlmProvider.NVIDIA_NIM -> SecureKeyStore.Provider.NVIDIA_NIM
        LlmProvider.GEMINI -> SecureKeyStore.Provider.GEMINI
        LlmProvider.GROQ -> SecureKeyStore.Provider.LLM
        LlmProvider.OPENAI -> SecureKeyStore.Provider.LLM
        LlmProvider.CLAUDE -> SecureKeyStore.Provider.LLM
        LlmProvider.MISTRAL -> SecureKeyStore.Provider.MISTRAL
        LlmProvider.POLLINATIONS -> SecureKeyStore.Provider.LLM // pas utilise (no auth)
        LlmProvider.CLOUDFLARE_AI -> SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN
    }
}

// ────────────────────────────────────────────────────────────────────────────
// STATE
// ────────────────────────────────────────────────────────────────────────────

@Immutable
data class ResolvedModel(
    val provider: LlmProvider,
    val info: LlmModelInfo,
)

@Immutable
data class DebugChatMessage(
    val role: String, // "user" | "assistant"
    val text: String,
    val imageBytes: ByteArray? = null,
    val provider: LlmProvider,
    val model: String,
    val timestampMs: Long,
    val isStreaming: Boolean = false,
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0,
    val latencyMs: Long = 0,
    val error: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is DebugChatMessage && other.timestampMs == timestampMs && other.role == role
    override fun hashCode(): Int = (timestampMs.hashCode() * 31) + role.hashCode()
}

@Immutable
data class LlmDebugState(
    /** Modeles statiques (hardcoded dans LlmCatalog) + dynamiques GitHub + NVIDIA. */
    val staticModels: List<ResolvedModel> = emptyList(),
    val dynamicGitHubModels: List<ResolvedModel> = emptyList(),
    val dynamicNvidiaModels: List<ResolvedModel> = emptyList(),
    val isFetchingCatalog: Boolean = false,
    val catalogError: String? = null,

    val selectedModel: ResolvedModel? = null,

    // Picker state
    val pickerOpen: Boolean = false,
    val pickerSearch: String = "",
    val pickerKindFilter: ModelKind? = null,
    val pickerProviderFilter: LlmProvider? = null,
    val pickerHideGated: Boolean = true, // hide gated par defaut (clarte free tier)

    // Chat state
    val messages: List<DebugChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val lastError: String? = null,

    // Non-chat kind results (un seul affiche a la fois selon le kind du model)
    val embeddingResult: EmbeddingService.EmbeddingResult? = null,
    val imageResult: ImageGenerationService.ImageGenerationResult? = null,
    val ttsResult: TtsService.TtsResult? = null,
    val sttResult: SttService.TranscriptionResult? = null,

    // API key availability (boolean only, jamais la valeur)
    val apiKeyAvailable: Map<SecureKeyStore.Provider, Boolean> = emptyMap(),
) {
    /** Tous les modeles disponibles (statiques + dynamiques) deduplique. */
    val allModels: List<ResolvedModel>
        get() {
            val seen = mutableSetOf<Pair<LlmProvider, String>>()
            val result = mutableListOf<ResolvedModel>()
            for (source in listOf(staticModels, dynamicNvidiaModels, dynamicGitHubModels)) {
                for (m in source) {
                    val key = m.provider to m.info.id
                    if (key !in seen) {
                        seen.add(key)
                        result.add(m)
                    }
                }
            }
            return result
        }
}
