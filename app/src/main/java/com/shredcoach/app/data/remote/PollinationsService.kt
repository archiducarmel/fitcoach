package com.shredcoach.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service Pollinations : text-to-image gratuit, sans cle, sans inscription.
 *
 * **Endpoint** : GET https://image.pollinations.ai/prompt/{encoded_prompt}
 * **Auth** : aucune (anonyme). Optionnel : token via header pour rate limit
 *   eleve (pas encore expose dans l'app, V2 si besoin).
 * **Reponse** : binaire direct (image/png ou image/jpeg). Pas de JSON wrapper.
 *
 * **Modeles** : "flux", "turbo", "kontext", "gptimage" (cf. catalogue).
 * **Parametres** : width, height, model, seed (reproductibilite), nologo.
 *
 * Reuse [ImageGenerationService.ImageGenerationResult] pour symmetrie avec
 * les autres providers d'image gen.
 */
@Singleton
class PollinationsService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {

    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS) // generation peut prendre 5-60s
        .build()

    /**
     * Genere une image avec Pollinations.
     *
     * @param prompt description de l'image
     * @param model "flux" / "turbo" / "kontext" / "gptimage"
     * @param width / height taille pixels (defaut 1024x1024)
     * @param seed reproductibilite (meme seed + meme prompt = meme image)
     * @param nologo retire le watermark Pollinations
     */
    suspend fun generate(
        prompt: String,
        model: String,
        width: Int = 1024,
        height: Int = 1024,
        seed: Long? = null,
        nologo: Boolean = true,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<ImageGenerationService.ImageGenerationResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            // URL-encode le prompt pour gerer espaces/accents/punctuation
            val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
            val urlBuilder = "$BASE_URL/$encodedPrompt".toHttpUrl().newBuilder()
                .addQueryParameter("width", width.toString())
                .addQueryParameter("height", height.toString())
                .addQueryParameter("model", model)
                .addQueryParameter("nologo", nologo.toString())
            if (seed != null) urlBuilder.addQueryParameter("seed", seed.toString())

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "image/*")
                .get()
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string()?.take(200) ?: ""
                throw Exception("HTTP ${response.code} : $err")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Reponse vide")
            val mime = response.header("Content-Type") ?: "image/png"

            val result = ImageGenerationService.ImageGenerationResult(
                imageBytes = bytes,
                mimeType = mime,
                sizeBytes = bytes.size,
                latencyMs = System.currentTimeMillis() - startMs,
                seed = seed,
            )
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.POLLINATIONS, model = model,
                tokensInput = prompt.length / 4,
                tokensOutput = bytes.size / 100,
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "generate failed", e)
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.POLLINATIONS, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "PollinationsSvc"
        private const val BASE_URL = "https://image.pollinations.ai/prompt"
    }
}
