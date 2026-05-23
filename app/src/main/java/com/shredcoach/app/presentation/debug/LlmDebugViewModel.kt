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
        // Restore les API keys depuis le keystore (sans afficher leur valeur).
        // Defensif : EncryptedSharedPreferences peut throw si le master key
        // a ete invalide (mismatch apres install/clear data).
        runCatching { refreshApiKeyAvailability() }
            .onFailure { android.util.Log.e(TAG, "init refreshApiKeyAvailability failed", it) }
        // Fix #4 : auto-fetch les catalogues dynamiques si les cles sont deja
        // configurees, pour que le picker montre l'integralite des modeles
        // accessibles des l'ouverture du Playground (vs attendre un refresh manuel).
        runCatching {
            if (secureKeyStore.hasKey(SecureKeyStore.Provider.GITHUB_MODELS)) refreshGitHubCatalog()
            if (secureKeyStore.hasKey(SecureKeyStore.Provider.NVIDIA_NIM)) refreshNvidiaCatalog()
        }.onFailure { android.util.Log.e(TAG, "init auto-fetch catalogs failed", it) }
    }

    companion object {
        private const val TAG = "LlmDebugVM"
    }

    // ─── Catalog management ─────────────────────────────────────────────────

    /** Refetch le catalogue GitHub Models (force HTTP, bypass cache 24h). */
    fun refreshGitHubCatalog() {
        val token = runCatching { secureKeyStore.getKey(SecureKeyStore.Provider.GITHUB_MODELS) }
            .getOrDefault("")
        if (token.isBlank()) {
            _state.update { it.copy(catalogError = "Token GitHub manquant. Configure-le d'abord.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isFetchingCatalog = true, catalogError = null) }
            val result = runCatching { githubCatalog.fetchCatalog(token, forceRefresh = true) }
                .getOrElse {
                    android.util.Log.e(TAG, "refreshGitHubCatalog crash", it)
                    Result.failure(it)
                }
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
        val apiKey = runCatching { secureKeyStore.getKey(SecureKeyStore.Provider.NVIDIA_NIM) }
            .getOrDefault("")
        if (apiKey.isBlank()) {
            _state.update { it.copy(catalogError = "Clé NVIDIA manquante. Configure-la d'abord.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isFetchingCatalog = true, catalogError = null) }
            val result = runCatching { nvidiaCatalog.fetchCatalog(apiKey, forceRefresh = true) }
                .getOrElse {
                    android.util.Log.e(TAG, "refreshNvidiaCatalog crash", it)
                    Result.failure(it)
                }
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

    /**
     * Persiste une cle API et auto-refresh le catalogue du provider si applicable.
     *
     * Bulletproof : tout le chemin est wrappe dans un runCatching car
     * EncryptedSharedPreferences peut throw (master key invalide / fichier
     * corrompu / decrypt fail) — sans ce wrapper, la Composable click handler
     * propage l'exception et l'app crash.
     */
    fun saveApiKey(provider: SecureKeyStore.Provider, value: String) {
        runCatching {
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
        }.onFailure { e ->
            android.util.Log.e(TAG, "saveApiKey failed for $provider", e)
            _state.update { it.copy(lastError = "Echec sauvegarde cle : ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    fun clearApiKey(provider: SecureKeyStore.Provider) {
        runCatching {
            secureKeyStore.clear(provider)
            refreshApiKeyAvailability()
        }.onFailure { e ->
            android.util.Log.e(TAG, "clearApiKey failed for $provider", e)
            _state.update { it.copy(lastError = "Echec suppression cle : ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    /**
     * Met a jour l'etat avec la disponibilite (non-blank) des cles par provider.
     * On NE PAS expose la valeur reelle dans le state — uniquement un boolean
     * "presente ou pas" pour eviter de leak la cle via state inspection.
     *
     * Defensif par cle : si une cle precise echoue a se decrypter, on la traite
     * comme absente (false) au lieu de crash global.
     */
    private fun refreshApiKeyAvailability() {
        val avail = SecureKeyStore.Provider.values()
            .associateWith { p ->
                runCatching { secureKeyStore.hasKey(p) }.getOrDefault(false)
            }
        _state.update { it.copy(apiKeyAvailable = avail) }
    }

    // ─── Model selection ────────────────────────────────────────────────────

    fun selectModel(resolved: ResolvedModel) {
        // Fix #3 : annule le stream en cours si l'user switche de modele
        // (sinon les tokens continuent d'arriver vers un state reset).
        currentStreamJob?.cancel()
        currentStreamJob = null
        _state.update {
            it.copy(
                selectedModel = resolved,
                messages = emptyList(), // reset chat sur change de modele
                embeddingResult = null,
                imageResult = null,
                ttsResult = null,
                sttResult = null,
                isSending = false,
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
        android.util.Log.d("LlmDiag", "═══ sendMessage CALLED text='${text.take(40)}' image=${imageBitmap != null} ═══")
        val resolved = _state.value.selectedModel ?: run {
            android.util.Log.e("LlmDiag", "× selectedModel == null — abort")
            _state.update { it.copy(lastError = "Selectionne un modele d'abord.") }
            return
        }
        android.util.Log.d("LlmDiag", "▶ resolved provider=${resolved.provider} model=${resolved.info.id} kind=${resolved.info.kind}")
        if (text.isBlank() && imageBitmap == null) {
            android.util.Log.w("LlmDiag", "× text blank + no image — abort silently")
            return
        }

        // Bug A fix : Gemini/Mistral ont `supportsChat=false` car endpoints non
        // OpenAI-compatible (Gemini = generateContent, Mistral via GeminiMealService).
        // Bloquer ici plutot que laisser un 404 silencieux remonter au user.
        if (!resolved.provider.supportsChat) {
            val msg = "${resolved.provider.displayName} n'est pas compatible chat dans le Playground. Endpoint Gemini/Mistral utilise un format different."
            android.util.Log.e("LlmDiag", "× provider=${resolved.provider} supportsChat=false — abort")
            _state.update { it.copy(lastError = msg) }
            return
        }

        // Get API key for the provider
        val keySlot = apiKeySlotFor(resolved.provider)
        val apiKey = secureKeyStore.getKey(keySlot)
        android.util.Log.d("LlmDiag", "▶ keySlot=$keySlot apiKey present=${apiKey.isNotBlank()} length=${apiKey.length}")
        if (apiKey.isBlank()) {
            android.util.Log.e("LlmDiag", "× apiKey blank for slot=$keySlot — abort")
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
        android.util.Log.d("LlmDiag", "▶ state.messages.size after add = ${_state.value.messages.size}")

        currentStreamJob?.cancel()
        currentStreamJob = viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            try {
                val responseBuf = StringBuilder()
                val systemPrompt = "Tu es un assistant IA en mode test. Reponds naturellement."

                // Fix #1 VLM proper : si image attachee + VLM, on construit un payload
                // multimodal OpenAI-compatible (content blocks text + image_url base64).
                // Pour CHAT pur, on garde le path streamMessage classique text-only.
                val flow = if (imageBytes != null && resolved.info.acceptsImageInput) {
                    llmApiService.streamMessageWithImage(
                        text = text,
                        imageBytes = imageBytes,
                        imageMimeType = "image/jpeg",
                        provider = resolved.provider,
                        apiKey = apiKey,
                        model = resolved.info.id,
                        overrideSystemPrompt = systemPrompt,
                        assistant = null,
                    )
                } else {
                    val messages = listOf(ApiChatMessage(role = "user", content = text))
                    llmApiService.streamMessage(
                        messages = messages,
                        provider = resolved.provider,
                        apiKey = apiKey,
                        model = resolved.info.id,
                        overrideSystemPrompt = systemPrompt,
                        slowMode = false,
                    )
                }

                var tokenCount = 0
                var lastUiUpdateMs = 0L
                val UPDATE_INTERVAL_MS = 50L
                flow.collect { token ->
                    tokenCount++
                    if (tokenCount <= 3 || tokenCount % 20 == 0) {
                        android.util.Log.d("LlmDiag", "◀ VM token #$tokenCount : '${token.take(40)}'")
                    }
                    responseBuf.append(token)
                    // ROOT CAUSE FIX : throttle UI updates to 20 FPS (50ms).
                    // Sans ce throttle, les ~500 updates/sec saturaient le Main
                    // dispatcher (chaque update = StateFlow emit + Snapshot notif
                    // + auto-scroll relance) ce qui empechait Choreographer de
                    // fire des frames -> 0 recomposition pendant le stream ->
                    // bulle invisible jusqu'a la fin du stream.
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs >= UPDATE_INTERVAL_MS) {
                        lastUiUpdateMs = now
                        _state.update { st ->
                            val msgs = st.messages.toMutableList()
                            val idx = msgs.lastIndex
                            if (idx >= 0 && msgs[idx].role == "assistant") {
                                msgs[idx] = msgs[idx].copy(text = responseBuf.toString())
                            }
                            st.copy(messages = msgs)
                        }
                    }
                }
                android.util.Log.d("LlmDiag", "✓ VM stream FINISHED : tokenCount=$tokenCount responseLength=${responseBuf.length}")
                // Update final OBLIGATOIRE : garantit que le dernier texte est
                // affiche meme si le throttle a skip le dernier token.
                _state.update { st ->
                    val msgs = st.messages.toMutableList()
                    val idx = msgs.lastIndex
                    if (idx >= 0 && msgs[idx].role == "assistant") {
                        msgs[idx] = msgs[idx].copy(text = responseBuf.toString())
                    }
                    st.copy(messages = msgs)
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
                            tokensInput = text.length / 4 + (imageBytes?.size?.div(1024)?.times(3) ?: 0),
                        )
                    }
                    st.copy(messages = msgs, isSending = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("LlmDiag", "× sendMessage caught exception", e)
                val errMsg = e.message ?: "${e.javaClass.simpleName} (pas de message)"
                _state.update { st ->
                    val msgs = st.messages.toMutableList()
                    val idx = msgs.lastIndex
                    if (idx >= 0 && msgs[idx].role == "assistant") {
                        msgs[idx] = msgs[idx].copy(
                            isStreaming = false,
                            error = errMsg,
                        )
                    }
                    st.copy(messages = msgs, isSending = false, lastError = errMsg)
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

    /** Dismiss explicite : clear lastError ET catalogError (single banner partage). */
    fun dismissError() {
        _state.update { it.copy(lastError = null, catalogError = null) }
    }

    // ─── EMBEDDING (text-only et multimodal) ────────────────────────────────

    /**
     * Genere un embedding. Route automatiquement vers `embedText` ou
     * `embedMultimodal` selon le kind du modele :
     *  - EMBEDDING            -> embedText (texte seul)
     *  - MULTIMODAL_EMBEDDING -> embedMultimodal (texte + image base64)
     *
     * Fix #2 : avant ce fix, MULTIMODAL_EMBEDDING tombait sur embedText et
     * recevait du texte-seul (les modeles CLIP-like comme NVCLIP refusent ou
     * retournent un vecteur degrade).
     */
    fun generateEmbedding(text: String, imageBytes: ByteArray? = null) {
        val resolved = _state.value.selectedModel ?: return
        val apiKey = secureKeyStore.getKey(apiKeySlotFor(resolved.provider))
        if (apiKey.isBlank()) {
            _state.update { it.copy(lastError = "Cle API manquante.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, lastError = null, embeddingResult = null) }
            val result = if (resolved.info.kind == ModelKind.MULTIMODAL_EMBEDDING) {
                embeddingService.embedMultimodal(
                    text = text.takeIf { it.isNotBlank() },
                    imageBytes = imageBytes,
                    mimeType = "image/jpeg",
                    model = resolved.info.id,
                    provider = resolved.provider,
                    apiKey = apiKey,
                )
            } else {
                embeddingService.embedText(
                    input = text, model = resolved.info.id,
                    provider = resolved.provider, apiKey = apiKey,
                )
            }
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

/**
 * Message du chat playground.
 *
 * **CRITIQUE** : pas de `@Immutable` ni de `equals` custom — DebugChatMessage
 * doit avoir l'equality data-class par defaut (compare TOUTES les props).
 * Une ancienne version overridait `equals` pour ne comparer que timestampMs+role
 * ce qui faisait que Compose smart-skipping voyait les updates de text comme
 * "rien change" et NE RECOMPOSAIT PAS la ChatBubble pendant le streaming.
 * Resultat : "•••" stagne meme apres le stream fini.
 */
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
)

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
