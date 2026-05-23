package com.shredcoach.app.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service pour les modeles EMBEDDING / MULTIMODAL_EMBEDDING.
 *
 * **Format OpenAI-compatible** pour les 2 providers cibles :
 *  - GitHub Models : POST /inference/embeddings
 *  - NVIDIA NIM    : POST /v1/embeddings
 *
 * **Payload** : `{"input": "text" | ["text1","text2",...], "model": "model-id"}`
 * **Reponse** : `{"data": [{"embedding": [0.1, 0.2, ...], "index": 0}], "usage": {...}}`
 *
 * Pour les MULTIMODAL_EMBEDDING (NVCLIP, nemoretriever-vlm), l'`input` accepte
 * aussi des objets `{type: "image_url", image_url: ...}` ou base64.
 */
@Singleton
class EmbeddingService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {
    private val client = baseClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @androidx.compose.runtime.Immutable
    data class EmbeddingResult(
        val embedding: List<Double>,
        val dimension: Int,
        val tokensInput: Int,
        val latencyMs: Long,
    )

    /**
     * Genere un embedding text-only.
     *
     * @param input texte a embedder
     * @param model id du modele (ex: "nvidia/nv-embed-v1", "baai/bge-m3")
     * @param provider GITHUB_MODELS ou NVIDIA_NIM
     * @param apiKey cle API du provider
     * @param assistant assistant pour la telemetrie (optionnel)
     */
    suspend fun embedText(
        input: String,
        model: String,
        provider: LlmProvider,
        apiKey: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<EmbeddingResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = embeddingsUrl(provider)
            val payload = mapOf("input" to input, "model" to model)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .apply {
                    if (provider == LlmProvider.GITHUB_MODELS) {
                        header("Accept", "application/vnd.github+json")
                    }
                }
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} : ${body.take(200)}")

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data")
                ?: throw Exception("Pas de data dans la reponse")
            if (data.size() == 0) throw Exception("Embedding vide")
            val embeddingArr = data[0].asJsonObject.getAsJsonArray("embedding")
                ?: throw Exception("Pas d'embedding dans data[0]")
            val embedding = embeddingArr.map { it.asDouble }
            val tokensInput = json.getAsJsonObject("usage")?.get("prompt_tokens")?.asInt ?: 0

            val result = EmbeddingResult(
                embedding = embedding,
                dimension = embedding.size,
                tokensInput = tokensInput,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = tokensInput, tokensOutput = 0, tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "embedText failed", e)
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    /**
     * Multimodal embedding : input = texte + image base64 (pour CLIP-like
     * models comme NVCLIP, nemoretriever-vlm-embed). Format NVIDIA NIM
     * specifique pour les modeles multimodaux.
     */
    suspend fun embedMultimodal(
        text: String?,
        imageBytes: ByteArray?,
        mimeType: String = "image/jpeg",
        model: String,
        provider: LlmProvider,
        apiKey: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<EmbeddingResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            // Construit un input multimodal compatible NVIDIA NIM
            val inputArr = mutableListOf<Map<String, Any>>()
            if (!text.isNullOrBlank()) {
                inputArr.add(mapOf("type" to "text", "text" to text))
            }
            if (imageBytes != null) {
                val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                inputArr.add(mapOf(
                    "type" to "image_url",
                    "image_url" to mapOf("url" to "data:$mimeType;base64,$b64"),
                ))
            }
            if (inputArr.isEmpty()) throw Exception("Au moins un input (texte ou image) requis")

            val payload = mapOf("input" to inputArr, "model" to model, "encoding_format" to "float")
            val request = Request.Builder()
                .url(embeddingsUrl(provider))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} : ${body.take(200)}")

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: throw Exception("Pas de data")
            val embedding = data[0].asJsonObject.getAsJsonArray("embedding").map { it.asDouble }
            val tokensInput = json.getAsJsonObject("usage")?.get("prompt_tokens")?.asInt ?: 0

            val result = EmbeddingResult(
                embedding = embedding,
                dimension = embedding.size,
                tokensInput = tokensInput,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = tokensInput, tokensOutput = 0, tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "embedMultimodal failed", e)
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    private fun embeddingsUrl(provider: LlmProvider): String = when (provider) {
        LlmProvider.GITHUB_MODELS -> "https://models.github.ai/inference/embeddings"
        LlmProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/embeddings"
        else -> throw IllegalArgumentException("Provider $provider ne supporte pas /embeddings")
    }

    companion object {
        private const val TAG = "EmbeddingService"
    }
}
