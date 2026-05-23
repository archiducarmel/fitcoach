package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.LlmTier
import com.shredcoach.app.domain.llm.ModelArchitecture
import com.shredcoach.app.domain.llm.ModelDomain
import com.shredcoach.app.domain.llm.ModelKind
import com.shredcoach.app.domain.llm.ModelOriginRegion
import com.shredcoach.app.domain.llm.NvidiaNimCatalog
import com.shredcoach.app.domain.llm.WeightsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service NVIDIA NIM : fetch `/v1/models` pour determiner les modeles
 * REELLEMENT accessibles a la cle API, et construit dynamiquement les
 * `LlmModelInfo` depuis ces IDs.
 *
 * **Architecture (corrigee 2026-05-24)** :
 *  - Avant : intersection editorial × accessible -> echec car editorial avait
 *    des IDs fictifs (e.g. deepseek-v4-pro inexistant) -> intersection vide
 *    -> fallback sur editorial avec des IDs qui ECHOUENT en chat (404).
 *  - Maintenant : on parse les IDs REELS de NVIDIA (~150 modeles disponibles
 *    sur la cle), et on infere kind/metadata via heuristique sur l'ID.
 *    Les modeles affiches existent vraiment et peuvent etre utilises en chat.
 *
 * **Format reponse NVIDIA** :
 * ```
 * {"object": "list", "data": [
 *   {"id": "meta/llama-3.3-70b-instruct", "object": "model", "owned_by": "meta"}
 * ]}
 * ```
 *
 * Cache 24h TTL.
 */
@Singleton
class NvidiaNimCatalogService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
) {

    private val client = baseClient.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedCatalog: List<LlmModelInfo>? = null
    @Volatile private var cachedAtMs: Long = 0L
    private val cacheTtlMs = 24L * 60 * 60 * 1000

    suspend fun fetchCatalog(
        apiKey: String,
        forceRefresh: Boolean = false,
    ): Result<List<LlmModelInfo>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Cle NVIDIA manquante"))
        val now = System.currentTimeMillis()
        val cached = cachedCatalog
        if (!forceRefresh && cached != null && (now - cachedAtMs) < cacheTtlMs) {
            return@withContext Result.success(cached)
        }

        try {
            Log.d("LlmDiag", "▶ NVIDIA fetchCatalog url=$BASE_URL/models apiKey length=${apiKey.length} prefix=${apiKey.take(8)}…")
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Réponse vide")
            Log.d("LlmDiag", "◀ NVIDIA fetchCatalog HTTP ${response.code} body.size=${body.length}")
            if (!response.isSuccessful) {
                val friendly = when (response.code) {
                    401 -> "Clé NVIDIA invalide (HTTP 401). Vérifie le format nvapi-xxx."
                    403 -> "Clé NVIDIA sans accès au catalogue (HTTP 403)."
                    404 -> "Endpoint /v1/models introuvable (HTTP 404)."
                    429 -> "Quota NVIDIA dépassé (HTTP 429)."
                    in 500..599 -> "Serveur NVIDIA indisponible (HTTP ${response.code})."
                    else -> "HTTP ${response.code}"
                }
                throw Exception(friendly)
            }
            val json = JsonParser.parseString(body).asJsonObject
            val dataArr = json.getAsJsonArray("data")
                ?: json.getAsJsonArray("models")
                ?: throw Exception("Format inattendu (pas de data[] ni models[])")

            Log.d("LlmDiag", "▶ NVIDIA dataArr.size=${dataArr.size()}")

            // Build LlmModelInfo from each id with inferred metadata
            val models = dataArr.mapNotNull { el ->
                runCatching {
                    if (el == null || !el.isJsonObject) return@runCatching null
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: return@runCatching null
                    val ownedBy = obj.get("owned_by")?.takeIf { it.isJsonPrimitive }?.asString
                    classifyNvidiaModel(id, ownedBy)
                }.onFailure { Log.e("LlmDiag", "× NVIDIA classify failed", it) }.getOrNull()
            }
            Log.d("LlmDiag", "▶ NVIDIA classified ${models.size} models (sample first 3 : ${models.take(3).joinToString { "${it.id}/${it.kind}" }})")

            // Trier : LANGUAGE/VLM (chat) d'abord, puis embeddings, puis le reste
            val sorted = models.sortedWith(
                compareBy<LlmModelInfo>(
                    { kindOrder(it.kind) },
                    { it.publisher ?: "" },
                    { it.id },
                )
            )
            cachedCatalog = sorted
            cachedAtMs = now
            Log.d("LlmDiag", "✓ NVIDIA fetchCatalog returns ${sorted.size} models")
            Result.success(sorted)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCatalog failed", e)
            Result.failure(e)
        }
    }

    /** Invalide le cache (bouton refresh UI). */
    fun invalidateCache() {
        cachedCatalog = null
        cachedAtMs = 0L
    }

    // ────────────────────────────────────────────────────────────────────────
    // Classification heuristique d'un modele NVIDIA depuis son ID
    // ────────────────────────────────────────────────────────────────────────

    /**
     * NVIDIA NIM expose juste id+owned_by, pas de modalites. On infere tout
     * via pattern matching sur l'id (memes heuristiques que GitHub Models).
     */
    private fun classifyNvidiaModel(id: String, ownedBy: String?): LlmModelInfo {
        val lower = id.lowercase()
        val (publisher, modelName) = splitPublisher(id)
        val effectivePublisher = publisher ?: ownedBy
        val kind = inferKindFromId(lower)
        val displayName = prettifyName(modelName)

        return LlmModelInfo(
            id = id,
            displayName = displayName,
            kind = kind,
            tier = LlmTier.STANDARD,
            notes = "",
            acceptsTextInput = kind != ModelKind.STT,
            acceptsImageInput = kind in setOf(
                ModelKind.VLM, ModelKind.MULTIMODAL_EMBEDDING,
                ModelKind.OCR, ModelKind.OBJECT_DETECTION,
            ),
            acceptsAudioInput = kind == ModelKind.STT,
            supportsVision = kind == ModelKind.VLM,
            supportsThinking = NvidiaNimCatalog.isSlow(id),
            supportsToolCalling = inferToolCalling(lower, effectivePublisher),
            supportsStreaming = kind in ModelKind.CHAT_COMPLETION_KINDS,
            supportsJsonMode = kind in ModelKind.CHAT_COMPLETION_KINDS,
            supportsAgentic = inferAgentic(lower),
            supportsCodeGen = inferCodeGen(lower),
            architecture = inferArchitecture(lower),
            weightsSource = inferWeightsSource(effectivePublisher),
            domain = inferDomain(lower),
            originRegion = inferRegion(effectivePublisher),
            publisher = effectivePublisher,
        )
    }

    private fun inferKindFromId(lower: String): ModelKind = when {
        // Embedding / reranking
        lower.contains("rerank") -> ModelKind.RERANKER
        lower.contains("nvclip") -> ModelKind.MULTIMODAL_EMBEDDING
        lower.contains("embed") -> ModelKind.EMBEDDING
        // Safety / moderation / PII
        lower.contains("guard") || lower.contains("nemoguard") || lower.contains("jailbreak") ||
            lower.contains("gliner-pii") || lower.contains("deepfake") -> ModelKind.CLASSIFICATION
        lower.contains("reward") -> ModelKind.REWARD_MODEL
        // OCR / document parsing
        lower.contains("ocr") || lower.contains("ocdrnet") || lower.contains("deplot") ||
            lower.contains("paddleocr") -> ModelKind.OCR
        // Sciences (bio/drug/weather/auto)
        lower.contains("alphafold") || lower.contains("esmfold") || lower.contains("esm2") ||
            lower.contains("diffdock") || lower.contains("boltz") || lower.contains("molmim") ||
            lower.contains("genmol") || lower.contains("proteinmpnn") || lower.contains("rfdiffusion") ||
            lower.contains("evo2") || lower.contains("maisi") || lower.contains("vista3d") ||
            lower.contains("corrdiff") || lower.contains("fourcastnet") -> ModelKind.SCIENTIFIC
        lower.contains("cuopt") -> ModelKind.OPTIMIZATION
        // Object detection
        lower.contains("dinov2") || lower.contains("grounding-dino") ||
            lower.contains("retail-object-detection") || lower.contains("sparsedrive") ||
            lower.contains("streampetr") || lower.contains("bevformer") ||
            lower.contains("visual-changenet") -> ModelKind.OBJECT_DETECTION
        // STT / TTS dedicated
        lower.contains("whisper") || lower.contains("parakeet") || lower.contains("canary") ||
            lower.contains("-asr") || lower.contains("/asr-") -> ModelKind.STT
        lower.contains("magpie") || lower.contains("/tts-") || lower.contains("-tts-") -> ModelKind.TTS
        // Image / video gen
        lower.contains("flux") || lower.contains("stable-diffusion") || lower.contains("sdxl") ||
            lower.contains("dall-e") || lower.contains("/sd-v") -> ModelKind.IMAGE_GENERATION
        lower.contains("stable-video") || lower.contains("trellis") ||
            lower.contains("cosmos-predict") -> ModelKind.VIDEO_GENERATION
        // VLM explicit
        lower.contains("-vl-") || lower.contains("-vlm") || lower.contains("vision-instruct") ||
            lower.contains("vila") || lower.contains("neva") || lower.contains("kosmos") ||
            lower.contains("fuyu") || lower.contains("llava") || lower.contains("idefics") ||
            lower.contains("minicpm-v") || lower.contains("phi-3.5-vision") ||
            lower.contains("phi-4-multimodal") -> ModelKind.VLM
        // Default : chat language model
        else -> ModelKind.LANGUAGE
    }

    private fun inferToolCalling(lower: String, publisher: String?): Boolean {
        val toolCapable = setOf("openai", "anthropic", "meta", "mistral-ai", "mistralai",
            "google", "cohere", "nvidia", "microsoft", "ibm")
        return publisher?.lowercase() in toolCapable && !lower.contains("embed") &&
            !lower.contains("guard")
    }

    private fun inferAgentic(lower: String): Boolean =
        lower.contains("llama-4-maverick") || lower.contains("qwen3-coder") ||
            lower.contains("nemotron-ultra") || lower.contains("opus")

    private fun inferCodeGen(lower: String): Boolean =
        lower.contains("code") || lower.contains("starcoder") || lower.contains("codestral") ||
            lower.contains("granite-code")

    private fun inferArchitecture(lower: String): ModelArchitecture = when {
        lower.contains("jamba") -> ModelArchitecture.HYBRID_MAMBA_TRANSFORMER
        lower.contains("mamba") -> ModelArchitecture.MAMBA
        lower.contains("recurrentgemma") -> ModelArchitecture.RNN
        lower.contains("mixtral") || lower.contains("-moe") || lower.contains("dbrx") ||
            lower.contains("scout-17b-16e") || lower.contains("maverick-17b-128e") ->
                ModelArchitecture.TRANSFORMER_MOE
        lower.contains("flux") || lower.contains("stable-diffusion") -> ModelArchitecture.DIFFUSION
        lower.contains("magpie-flow") -> ModelArchitecture.FLOW_MATCHING
        lower.contains("dinov2") || lower.contains("clip") -> ModelArchitecture.CONVOLUTIONAL
        lower.contains("alphafold") || lower.contains("rfdiffusion") -> ModelArchitecture.GRAPH_NEURAL
        else -> ModelArchitecture.TRANSFORMER_DENSE
    }

    private fun inferWeightsSource(publisher: String?): WeightsSource {
        val closed = setOf("openai", "anthropic", "cohere", "ai21labs")
        val openW = setOf("meta", "mistralai", "mistral-ai", "google", "microsoft", "ibm",
            "nvidia", "nv-mistralai", "deepseek-ai", "qwen", "alibaba", "01-ai", "snowflake",
            "baai", "bigcode", "databricks", "abacusai", "adept", "aisingapore", "minimaxai",
            "moonshotai", "sarvamai", "stockmark", "stepfun-ai", "upstage", "writer",
            "z-ai", "zyphra", "bytedance", "huggingface", "tiiuae", "yandex", "thudm")
        return when (publisher?.lowercase()) {
            in closed -> WeightsSource.CLOSED_SOURCE
            in openW -> WeightsSource.OPEN_WEIGHTS
            null -> WeightsSource.UNKNOWN
            else -> WeightsSource.OPEN_WEIGHTS
        }
    }

    private fun inferDomain(lower: String): ModelDomain = when {
        lower.contains("code") || lower.contains("starcoder") -> ModelDomain.CODE
        lower.contains("palmyra-med") || lower.contains("maisi") -> ModelDomain.MEDICAL
        lower.contains("palmyra-fin") -> ModelDomain.FINANCE
        lower.contains("alphafold") || lower.contains("esmfold") -> ModelDomain.BIOLOGY
        lower.contains("diffdock") || lower.contains("molmim") -> ModelDomain.DRUG_DISCOVERY
        lower.contains("guard") || lower.contains("jailbreak") -> ModelDomain.SAFETY_MODERATION
        lower.contains("gliner-pii") -> ModelDomain.PII_DETECTION
        lower.contains("ocr") -> ModelDomain.OCR_DOCUMENT
        lower.contains("translate") || lower.contains("seamless") -> ModelDomain.TRANSLATION
        else -> ModelDomain.GENERAL
    }

    private fun inferRegion(publisher: String?): ModelOriginRegion = when (publisher?.lowercase()) {
        "mistralai", "mistral-ai" -> ModelOriginRegion.FRANCE
        "01-ai", "qwen", "alibaba", "deepseek-ai", "moonshotai", "minimaxai",
        "z-ai", "stepfun-ai", "bytedance", "thudm" -> ModelOriginRegion.CHINA
        "aisingapore" -> ModelOriginRegion.SINGAPORE
        "sarvamai" -> ModelOriginRegion.INDIA
        "stockmark" -> ModelOriginRegion.JAPAN
        "upstage" -> ModelOriginRegion.SOUTH_KOREA
        "tiiuae" -> ModelOriginRegion.UAE
        "openai", "anthropic", "meta", "google", "microsoft", "nvidia", "ibm",
        "cohere", "ai21labs", "databricks", "writer", "zyphra" -> ModelOriginRegion.US
        else -> ModelOriginRegion.UNKNOWN
    }

    private fun splitPublisher(id: String): Pair<String?, String> {
        val idx = id.indexOf('/')
        if (idx < 0) return null to id
        return id.substring(0, idx) to id.substring(idx + 1)
    }

    private fun prettifyName(name: String): String =
        name.split('-', '_').joinToString(" ") { part ->
            if (part.length <= 3 || part.all { it.isDigit() || it == 'b' }) part
            else part.replaceFirstChar { it.uppercase() }
        }

    /** Ordre de tri : chat d'abord, puis embed, puis specialises. */
    private fun kindOrder(kind: ModelKind): Int = when (kind) {
        ModelKind.LANGUAGE -> 0
        ModelKind.VLM -> 1
        ModelKind.EMBEDDING -> 2
        ModelKind.MULTIMODAL_EMBEDDING -> 3
        ModelKind.RERANKER -> 4
        ModelKind.STT -> 5
        ModelKind.TTS -> 6
        ModelKind.IMAGE_GENERATION -> 7
        ModelKind.VIDEO_GENERATION -> 8
        ModelKind.OCR -> 9
        ModelKind.OBJECT_DETECTION -> 10
        ModelKind.CLASSIFICATION -> 11
        ModelKind.REWARD_MODEL -> 12
        ModelKind.SCIENTIFIC -> 13
        ModelKind.OPTIMIZATION -> 14
    }

    companion object {
        private const val TAG = "NvidiaNimCatalog"
        private const val BASE_URL = "https://integrate.api.nvidia.com/v1"
    }
}
