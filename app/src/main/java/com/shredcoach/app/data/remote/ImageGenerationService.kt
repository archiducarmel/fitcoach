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
 * Service pour les modeles IMAGE_GENERATION.
 *
 * **Format OpenAI-compatible** : POST /v1/images/generations
 *  - Payload : `{"prompt": "...", "model": "...", "n": 1, "size": "1024x1024"}`
 *  - Reponse : `{"data": [{"b64_json": "..."}], "created": ...}` ou
 *              `{"data": [{"url": "https://..."}], ...}` selon le provider
 *
 * **Providers supportes** :
 *  - NVIDIA NIM (FLUX, Stable Diffusion 3, SDXL)
 *  - GitHub Models n'expose pas de modeles IMAGE_GENERATION publiquement V1
 *
 * **Sortie** : ByteArray du PNG decode (b64 → bytes). Le caller peut
 * directement charger via Coil ou sauvegarder en fichier.
 */
@Singleton
class ImageGenerationService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {
    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS) // image gen prend 5-30s typiquement
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @androidx.compose.runtime.Immutable
    data class ImageGenerationResult(
        val imageBytes: ByteArray,
        val mimeType: String,
        val sizeBytes: Int,
        val latencyMs: Long,
        val seed: Long? = null,
    ) {
        override fun equals(other: Any?): Boolean = other is ImageGenerationResult && imageBytes.contentEquals(other.imageBytes)
        override fun hashCode(): Int = imageBytes.contentHashCode()
    }

    /**
     * Genere une image depuis un prompt texte.
     *
     * @param prompt description de l'image
     * @param model id du modele (ex: "black-forest-labs/flux.1-schnell")
     * @param provider NVIDIA_NIM principalement
     * @param apiKey cle API
     * @param size "1024x1024" / "1024x768" / "512x512" selon le modele
     * @param negativePrompt optionnel (Stable Diffusion)
     */
    suspend fun generate(
        prompt: String,
        model: String,
        provider: LlmProvider,
        apiKey: String,
        size: String = "1024x1024",
        negativePrompt: String? = null,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<ImageGenerationResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = imagesUrl(provider)
            val payload = mutableMapOf<String, Any>(
                "model" to model,
                "prompt" to prompt,
                "n" to 1,
                "size" to size,
                "response_format" to "b64_json",
            )
            if (!negativePrompt.isNullOrBlank()) payload["negative_prompt"] = negativePrompt

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} : ${body.take(200)}")

            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: throw Exception("Pas de data")
            if (data.size() == 0) throw Exception("Pas d'image generee")
            val first = data[0].asJsonObject
            val b64 = first.get("b64_json")?.asString
                ?: throw Exception("Pas de b64_json (response_format different ?)")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val seed = first.get("seed")?.asLong

            val result = ImageGenerationResult(
                imageBytes = bytes,
                mimeType = "image/png",
                sizeBytes = bytes.size,
                latencyMs = System.currentTimeMillis() - startMs,
                seed = seed,
            )
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                // L'image gen ne report pas de tokens classiques. On estime
                // le "cost equivalent" via le nombre de pixels (heuristique).
                tokensInput = prompt.length / 4,
                tokensOutput = bytes.size / 100, // proxy
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "generate failed", e)
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    private fun imagesUrl(provider: LlmProvider): String = when (provider) {
        LlmProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/images/generations"
        LlmProvider.GITHUB_MODELS -> "https://models.github.ai/inference/images/generations"
        else -> throw IllegalArgumentException("Provider $provider ne supporte pas /images/generations")
    }

    companion object {
        private const val TAG = "ImageGenService"
    }
}
