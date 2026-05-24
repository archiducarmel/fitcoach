package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.LlmTier
import com.shredcoach.app.domain.llm.ModelArchitecture
import com.shredcoach.app.domain.llm.ModelDescriptions
import com.shredcoach.app.domain.llm.ModelDomain
import com.shredcoach.app.domain.llm.ModelKind
import com.shredcoach.app.domain.llm.ModelOriginRegion
import com.shredcoach.app.domain.llm.WeightsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service qui fetche le catalogue dynamique GitHub Models et le classifie
 * selon notre taxonomie locale (`ModelKind` + metadata).
 *
 * **Endpoint** : `GET https://models.github.ai/catalog/models`
 * **Auth** : Bearer ghp_xxx + Accept application/vnd.github+json
 * **Reponse** : JSON array de modeles avec leurs modalites, publisher, tier, etc.
 *
 * **Cache 24h** : evite de spammer GitHub. Le catalogue change rarement
 * (releases ponctuelles), 24h est un bon compromis fraicheur/courtoisie.
 * Cache en memoire (pas DataStore/Room) — perdu au kill app, c'est OK.
 *
 * **Auto-classification heuristique** : le DTO GitHub fournit les modalites
 * (input/output) mais pas le `ModelKind` directement. On infere via :
 *   1. (inputs, outputs) → kind primaire
 *   2. Pattern matching sur l'`id` → kind plus precis si possible
 *   3. Inference architecture / weights source depuis le publisher + id
 */
@Singleton
class GitHubModelsCatalogService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
) {

    private val client = baseClient.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Cache en memoire — TTL 24h. Pas thread-safe mais accede uniquement
    // depuis Dispatchers.IO via les coroutines.
    @Volatile private var cachedCatalog: List<LlmModelInfo>? = null
    @Volatile private var cachedAtMs: Long = 0L
    private val cacheTtlMs = 24L * 60 * 60 * 1000 // 24h

    /**
     * Recupere le catalogue. Cache 24h, force-refresh via [forceRefresh].
     *
     * @return List<LlmModelInfo> avec kind/metadata classifies, triee
     *   intelligemment (accessibles d'abord, gated en bas).
     */
    suspend fun fetchCatalog(
        token: String,
        forceRefresh: Boolean = false,
    ): Result<List<LlmModelInfo>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCatalog != null && (now - cachedAtMs) < cacheTtlMs) {
            return@withContext Result.success(cachedCatalog!!)
        }
        if (token.isBlank()) return@withContext Result.failure(Exception("Token GitHub manquant"))

        try {
            val request = Request.Builder()
                .url("$BASE_URL/catalog/models")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} : ${body.take(200)}")
            }
            val classified = parseAndClassify(body)
            cachedCatalog = classified
            cachedAtMs = now
            Log.i(TAG, "GitHub Models catalog : ${classified.size} modeles classifies")
            Result.success(classified)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCatalog failed", e)
            Result.failure(e)
        }
    }

    /** Vide le cache (utilise par le bouton "Refresh" UI debug). */
    fun invalidateCache() {
        cachedCatalog = null
        cachedAtMs = 0L
    }

    // ────────────────────────────────────────────────────────────────────────
    // Parsing + classification
    // ────────────────────────────────────────────────────────────────────────

    private fun parseAndClassify(rawJson: String): List<LlmModelInfo> {
        val arr = JsonParser.parseString(rawJson).asJsonArray
        val raw = arr.mapNotNull { el ->
            try {
                gson.fromJson(el, GitHubCatalogModel::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "Skip model parse failed : ${e.message}")
                null
            }
        }
        // Classification + tri : accessibles d'abord, puis tier low → high → custom
        return raw
            .map { classify(it) }
            .sortedWith(
                compareBy<LlmModelInfo>(
                    { if (it.isGated) 1 else 0 },
                    { rateLimitOrder(it.rateLimitTier) },
                    { it.publisher ?: "" },
                    { it.id },
                )
            )
    }

    private fun rateLimitOrder(tier: String?): Int = when (tier?.lowercase()) {
        "low" -> 0
        "high" -> 1
        "custom" -> 2
        else -> 99
    }

    /**
     * Classifie un modele GitHub Models en `LlmModelInfo` complet.
     *
     * Pipeline :
     *  1. Detection kind depuis modalites (input/output)
     *  2. Affinement kind depuis pattern matching sur l'id (vision/code/etc.)
     *  3. Inference architecture (MoE detection)
     *  4. Inference weights source (publisher-based heuristique)
     *  5. Inference domain (code, medical, finance, etc.)
     *  6. Inference origine geographique
     *  7. Detection gated (custom tier + openai/gpt-5/o-series)
     */
    private fun classify(m: GitHubCatalogModel): LlmModelInfo {
        val id = m.id
        val inputs = m.supportedInputModalities.orEmpty().map { it.lowercase() }.toSet()
        val outputs = m.supportedOutputModalities.orEmpty().map { it.lowercase() }.toSet()

        val kind = inferKind(id, inputs, outputs)
        val isGated = isGatedForFree(id, m.rateLimitTier)
        val (publisher, modelName) = splitPublisher(id)

        return LlmModelInfo(
            id = id,
            displayName = m.friendlyName ?: prettifyId(id),
            kind = kind,
            tier = LlmTier.STANDARD, // GitHub Models n'a pas de tier qualite explicite
            // Description priorite : notre catalogue editorial ModelDescriptions
            // (≤30 mots, finalite + contexte) > summary GitHub > vide
            notes = ModelDescriptions.describe(id, publisher) ?: m.summary?.take(180).orEmpty(),

            acceptsTextInput = "text" in inputs,
            acceptsImageInput = "image" in inputs,
            acceptsAudioInput = "audio" in inputs,
            acceptsVideoInput = "video" in inputs,
            supportsVision = "image" in inputs,

            supportsThinking = inferThinking(id),
            supportsToolCalling = inferToolCalling(id, publisher),
            supportsStreaming = kind in ModelKind.CHAT_COMPLETION_KINDS,
            supportsJsonMode = kind in ModelKind.CHAT_COMPLETION_KINDS,
            supportsAgentic = inferAgentic(id),
            supportsCodeGen = inferCodeGen(id),
            supportsTranslation = inferTranslation(id),

            architecture = inferArchitecture(id, publisher),
            weightsSource = inferWeightsSource(publisher),
            domain = inferDomain(id),
            originRegion = inferRegion(publisher),
            publisher = publisher,
            rateLimitTier = m.rateLimitTier,
            isGated = isGated,
            maxContextTokens = m.limits?.maxInputTokens ?: 0,
            releaseYear = inferReleaseYear(id),
        )
    }

    // ─── Kind inference ─────────────────────────────────────────────────────

    /**
     * Classifie un modele en ModelKind avec une **logique de priorite robuste**.
     *
     * REGLE D'OR : un modele STT pur a **audio en entree uniquement**. Si le
     * modele accepte text+image+audio (= multimodal type gpt-4o, gemini-2.5),
     * il est VLM, pas STT. De meme TTS pur = text-only entree + audio sortie.
     *
     * Pipeline de priorite :
     *  1. **Patterns d'id explicites** (whisper, magpie, flux, embed, vlm…) :
     *     l'id est le signal le plus fiable, surtout pour les modeles
     *     specialises ou les API qui exposent mal les modalites.
     *  2. **Output-driven** : embedding/image/video → kind directement.
     *  3. **Audio output** : TTS uniquement si entree text-only (pur synth),
     *     sinon multimodal (= VLM en V1).
     *  4. **Text output** :
     *     - audio-only en entree → STT (Whisper-like dedicated)
     *     - image en entree (avec ou sans audio) → VLM
     *     - audio en entree (sans image) → VLM (multimodal speaker)
     *     - text-only → LANGUAGE
     */
    private fun inferKind(id: String, inputs: Set<String>, outputs: Set<String>): ModelKind {
        val lower = id.lowercase()

        // ─── 1. Patterns d'id prioritaires (specialises) ────────────────────
        // Embedding / reranking
        if (lower.contains("rerank")) return ModelKind.RERANKER
        if (lower.contains("nvclip")) return ModelKind.MULTIMODAL_EMBEDDING
        if (lower.contains("embed")) {
            return if ("image" in inputs || lower.contains("clip")) ModelKind.MULTIMODAL_EMBEDDING
                   else ModelKind.EMBEDDING
        }
        // Safety / moderation / PII
        if (lower.contains("guard") || lower.contains("safety") || lower.contains("nemoguard") ||
            lower.contains("jailbreak") || lower.contains("gliner-pii") ||
            lower.contains("deepfake") || lower.contains("ai-synthetic")) {
            return ModelKind.CLASSIFICATION
        }
        // Reward
        if (lower.contains("reward")) return ModelKind.REWARD_MODEL
        // OCR dedicated
        if (lower.contains("ocr") || lower.contains("ocdrnet") || lower.contains("deplot") ||
            (lower.contains("parse") && !lower.contains("sparse"))) {
            return ModelKind.OCR
        }
        // Sciences (bio, drug, weather, autonomy)
        if (lower.contains("alphafold") || lower.contains("esmfold") || lower.contains("esm2") ||
            lower.contains("diffdock") || lower.contains("boltz") || lower.contains("molmim") ||
            lower.contains("genmol") || lower.contains("proteinmpnn") || lower.contains("rfdiffusion") ||
            lower.contains("evo2") || lower.contains("maisi") || lower.contains("vista3d") ||
            lower.contains("corrdiff") || lower.contains("fourcastnet")) {
            return ModelKind.SCIENTIFIC
        }
        if (lower.contains("cuopt")) return ModelKind.OPTIMIZATION
        // Object detection / vision specialise
        if (lower.contains("dinov2") || lower.contains("grounding-dino") ||
            lower.contains("retail-object-detection") || lower.contains("sparsedrive") ||
            lower.contains("streampetr") || lower.contains("bevformer") ||
            lower.contains("visual-changenet")) {
            return ModelKind.OBJECT_DETECTION
        }
        // STT dedicated (Whisper/Parakeet/Canary/ASR)
        if (lower.contains("whisper") || lower.contains("parakeet") || lower.contains("canary") ||
            lower.contains("-asr") || lower.contains("/asr-") ||
            lower.contains("speech-to-text") || lower.contains("speech-recognition")) {
            return ModelKind.STT
        }
        // TTS dedicated (Magpie / TTS-* / Coqui / Bark / VALL-E / OpenVoice)
        if (lower.contains("magpie") || lower.contains("/tts-") || lower.contains("-tts-") ||
            lower.contains("text-to-speech") || lower.contains("/coqui-") ||
            lower.contains("/bark") || lower.contains("vall-e") || lower.contains("openvoice")) {
            return ModelKind.TTS
        }
        // Image generation dedicated
        if (lower.contains("flux") || lower.contains("stable-diffusion") || lower.contains("sdxl") ||
            lower.contains("dall-e") || lower.contains("dreamshaper") ||
            lower.contains("/sd-v") || lower.contains("imagen") || lower.contains("midjourney") ||
            lower.contains("kandinsky") || lower.contains("playground-v")) {
            return ModelKind.IMAGE_GENERATION
        }
        // Video generation
        if (lower.contains("stable-video") || lower.contains("trellis") ||
            lower.contains("cosmos-predict") || lower.contains("/sora") ||
            lower.contains("animatediff")) {
            return ModelKind.VIDEO_GENERATION
        }
        // VLM explicit dans le nom
        if (lower.contains("-vl-") || lower.contains("-vlm") || lower.contains("/vlm-") ||
            lower.contains("vision-instruct") || lower.contains("vila") || lower.contains("neva") ||
            lower.contains("kosmos") || lower.contains("fuyu") || lower.contains("llava") ||
            lower.contains("idefics") || lower.contains("minicpm-v")) {
            return ModelKind.VLM
        }

        // ─── 2. Output-driven (apres les patterns specialises) ──────────────
        if ("embedding" in outputs) {
            return if ("image" in inputs) ModelKind.MULTIMODAL_EMBEDDING else ModelKind.EMBEDDING
        }
        if ("image" in outputs) return ModelKind.IMAGE_GENERATION
        if ("video" in outputs) return ModelKind.VIDEO_GENERATION

        // ─── 3. Audio output : TTS pur uniquement si entree text-only ───────
        // (sinon = multimodal qui parle, type gpt-4o-audio → traite comme VLM)
        if ("audio" in outputs) {
            // TTS pur : entree text-only ou audio (regen)
            if (inputs.isEmpty() || inputs == setOf("text") ||
                (inputs == setOf("audio")) || (inputs == setOf("text", "audio"))) {
                return ModelKind.TTS
            }
            // Sinon : multimodal (text+image+audio in → text+audio out), traite comme VLM
            // car le chat playground n'exploite pas l'audio out en V1.
            return ModelKind.VLM
        }

        // ─── 4. Text output (le cas le plus frequent) ───────────────────────
        if ("text" in outputs) {
            // STT pur : audio-only en entree (Whisper-like dedicated, sans text/image)
            if ("audio" in inputs && "text" !in inputs && "image" !in inputs) {
                return ModelKind.STT
            }
            // VLM : image en entree (multimodal vision, peut aussi avoir audio)
            if ("image" in inputs) return ModelKind.VLM
            // Multimodal speaker (audio in + text in, sans image) : VLM en V1
            // (= gpt-4o-mini-audio, gpt-4o-realtime, etc.)
            if ("audio" in inputs) return ModelKind.VLM
            // Default : pur text-in/text-out
            return ModelKind.LANGUAGE
        }

        // Fallback : si pas de text in outputs (rare), default LANGUAGE
        return ModelKind.LANGUAGE
    }

    // ─── Capability inference ───────────────────────────────────────────────

    private fun inferThinking(id: String): Boolean {
        val lower = id.lowercase()
        return lower.contains("reasoning") || lower.contains("-o1") || lower.contains("-o3") ||
                lower.contains("/o1-") || lower.contains("/o3-") ||
                lower.contains("deepseek-r1") || lower.contains("deepseek-v4-pro") ||
                lower.contains("nemotron-ultra") || lower.contains("nemotron-super")
    }

    private fun inferToolCalling(id: String, publisher: String?): Boolean {
        // Publishers known to support tool calling broadly
        val toolCapable = setOf("openai", "anthropic", "meta", "mistral-ai", "mistralai",
            "google", "cohere", "nvidia", "microsoft", "ibm")
        return publisher?.lowercase() in toolCapable && !id.lowercase().contains("embed") &&
                !id.lowercase().contains("guard")
    }

    private fun inferAgentic(id: String): Boolean {
        val lower = id.lowercase()
        return lower.contains("llama-4-maverick") || lower.contains("qwen3-coder") ||
                lower.contains("nemotron-ultra") || lower.contains("opus") ||
                lower.contains("gpt-4o") || lower.contains("claude") && !lower.contains("haiku")
    }

    private fun inferCodeGen(id: String): Boolean {
        val lower = id.lowercase()
        return lower.contains("code") || lower.contains("starcoder") || lower.contains("codellama") ||
                lower.contains("codestral") || lower.contains("codegemma") ||
                lower.contains("granite-code") || lower.contains("/usdcode")
    }

    private fun inferTranslation(id: String): Boolean {
        val lower = id.lowercase()
        return lower.contains("translate") || lower.contains("riva-translate") ||
                lower.contains("seamless")
    }

    // ─── Metadata inference ─────────────────────────────────────────────────

    private fun inferArchitecture(id: String, publisher: String?): ModelArchitecture {
        val lower = id.lowercase()
        return when {
            lower.contains("jamba") -> ModelArchitecture.HYBRID_MAMBA_TRANSFORMER
            lower.contains("mamba") -> ModelArchitecture.MAMBA
            lower.contains("recurrentgemma") -> ModelArchitecture.RNN
            lower.contains("mixtral") || lower.contains("-moe") ||
                    lower.contains("phi-3.5-moe") || lower.contains("scout-17b-16e") ||
                    lower.contains("maverick-17b-128e") || lower.contains("dbrx") -> ModelArchitecture.TRANSFORMER_MOE
            lower.contains("flux") || lower.contains("stable-diffusion") -> ModelArchitecture.DIFFUSION
            lower.contains("magpie-flow") -> ModelArchitecture.FLOW_MATCHING
            lower.contains("dinov2") || lower.contains("clip") -> ModelArchitecture.CONVOLUTIONAL
            lower.contains("alphafold") || lower.contains("rfdiffusion") -> ModelArchitecture.GRAPH_NEURAL
            else -> ModelArchitecture.TRANSFORMER_DENSE
        }
    }

    private fun inferWeightsSource(publisher: String?): WeightsSource {
        val closedPublishers = setOf("openai", "anthropic", "cohere", "ai21labs")
        val openWeightsPublishers = setOf("meta", "mistralai", "mistral-ai", "google", "microsoft",
            "ibm", "nvidia", "nv-mistralai", "deepseek-ai", "qwen", "alibaba", "01-ai",
            "snowflake", "baai", "bigcode", "databricks", "abacusai", "adept", "aisingapore",
            "minimaxai", "moonshotai", "sarvamai", "stockmark", "stepfun-ai", "upstage", "writer",
            "z-ai", "zyphra", "bytedance", "huggingface")
        return when (publisher?.lowercase()) {
            in closedPublishers -> WeightsSource.CLOSED_SOURCE
            in openWeightsPublishers -> WeightsSource.OPEN_WEIGHTS
            null -> WeightsSource.UNKNOWN
            else -> WeightsSource.OPEN_WEIGHTS // default heuristique
        }
    }

    private fun inferDomain(id: String): ModelDomain {
        val lower = id.lowercase()
        return when {
            lower.contains("code") || lower.contains("starcoder") || lower.contains("codestral") ||
                    lower.contains("codellama") || lower.contains("granite-code") -> ModelDomain.CODE
            lower.contains("palmyra-med") || lower.contains("maisi") || lower.contains("vista3d") ||
                    lower.contains("medical") -> ModelDomain.MEDICAL
            lower.contains("palmyra-fin") || lower.contains("finance") -> ModelDomain.FINANCE
            lower.contains("palmyra-creative") -> ModelDomain.CREATIVE
            lower.contains("alphafold") || lower.contains("esmfold") || lower.contains("esm2") ||
                    lower.contains("proteinmpnn") -> ModelDomain.BIOLOGY
            lower.contains("diffdock") || lower.contains("molmim") || lower.contains("genmol") ||
                    lower.contains("boltz") -> ModelDomain.DRUG_DISCOVERY
            lower.contains("sparsedrive") || lower.contains("streampetr") ||
                    lower.contains("bevformer") -> ModelDomain.AUTONOMOUS_DRIVING
            lower.contains("corrdiff") || lower.contains("fourcastnet") -> ModelDomain.WEATHER_CLIMATE
            lower.contains("guard") || lower.contains("safety") || lower.contains("nemoguard") ||
                    lower.contains("jailbreak") -> ModelDomain.SAFETY_MODERATION
            lower.contains("gliner-pii") -> ModelDomain.PII_DETECTION
            lower.contains("ocr") || lower.contains("parse") || lower.contains("ocdrnet") -> ModelDomain.OCR_DOCUMENT
            lower.contains("translate") || lower.contains("riva-translate") -> ModelDomain.TRANSLATION
            else -> ModelDomain.GENERAL
        }
    }

    private fun inferRegion(publisher: String?): ModelOriginRegion = when (publisher?.lowercase()) {
        "mistralai", "mistral-ai" -> ModelOriginRegion.FRANCE
        "01-ai", "qwen", "alibaba", "deepseek-ai", "moonshotai", "minimaxai",
        "z-ai", "stepfun-ai", "bytedance" -> ModelOriginRegion.CHINA
        "aisingapore" -> ModelOriginRegion.SINGAPORE
        "sarvamai" -> ModelOriginRegion.INDIA
        "stockmark" -> ModelOriginRegion.JAPAN
        "upstage" -> ModelOriginRegion.SOUTH_KOREA
        "openai", "anthropic", "meta", "google", "microsoft", "nvidia", "ibm",
        "cohere", "ai21labs", "databricks", "abacusai", "adept", "snowflake",
        "writer", "zyphra", "bigcode" -> ModelOriginRegion.US
        else -> ModelOriginRegion.UNKNOWN
    }

    /**
     * GitHub Models gating : modeles OpenAI premium en tier custom sont
     * reserves aux comptes Copilot Pro+. Heuristique du Python POC.
     */
    private fun isGatedForFree(id: String, tier: String?): Boolean {
        if (tier != "custom") return false
        val lower = id.lowercase()
        return lower.startsWith("openai/gpt-5") ||
                lower.startsWith("openai/o1") ||
                lower.startsWith("openai/o3") ||
                lower.startsWith("openai/o4")
    }

    private fun inferReleaseYear(id: String): Int {
        // Pattern courant : id contient "YYYY" en suffixe (e.g., claude-sonnet-4-20250514)
        val match = Regex("""\b(20\d{2})""").find(id)
        return match?.value?.toIntOrNull() ?: 0
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun splitPublisher(id: String): Pair<String?, String> {
        val idx = id.indexOf('/')
        if (idx < 0) return null to id
        return id.substring(0, idx) to id.substring(idx + 1)
    }

    /** Convertit "meta-llama/llama-4-scout-17b-16e-instruct" en libelle lisible. */
    private fun prettifyId(id: String): String {
        val name = id.substringAfter('/', id)
        return name.split('-', '_').joinToString(" ") { part ->
            if (part.length <= 3 || part.all { it.isDigit() || it == 'b' }) part
            else part.replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        private const val TAG = "GHModelsCatalog"
        private const val BASE_URL = "https://models.github.ai"
    }
}

// ────────────────────────────────────────────────────────────────────────────
// DTO GitHub Models catalog
// ────────────────────────────────────────────────────────────────────────────

internal data class GitHubCatalogModel(
    val id: String,
    @SerializedName("friendly_name") val friendlyName: String? = null,
    val publisher: String? = null,
    val summary: String? = null,
    @SerializedName("supported_input_modalities") val supportedInputModalities: List<String>? = null,
    @SerializedName("supported_output_modalities") val supportedOutputModalities: List<String>? = null,
    @SerializedName("rate_limit_tier") val rateLimitTier: String? = null,
    val limits: GitHubCatalogLimits? = null,
)

internal data class GitHubCatalogLimits(
    @SerializedName("max_input_tokens") val maxInputTokens: Int? = null,
    @SerializedName("max_output_tokens") val maxOutputTokens: Int? = null,
)
