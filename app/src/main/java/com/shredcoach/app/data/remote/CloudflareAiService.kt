package com.shredcoach.app.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service Cloudflare Workers AI : image generation (txt2img + img2img).
 *
 * **Auth** : Bearer token Cloudflare + Account ID dans l'URL.
 *   POST https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run/{model}
 * **Free tier** : 10k neurons/jour avec compte.
 *
 * **Formats de reponse** (selon modele) :
 *  - **FLUX models** (flux-1-schnell, flux-2-klein-9b) : JSON
 *    `{"result": {"image": "<base64>"}, "success": true}`
 *  - **SDXL/SD models** (sdxl-lightning, sdxl-base, dreamshaper, sd-v1.5-img2img) :
 *    binaire PNG direct dans le body
 *
 * **IMG2IMG (flux-2-klein-9b)** : multipart avec `input_image_0` + contrainte
 * input ≤ 512×512 (resize cote client via Bitmap.thumbnail equivalent).
 */
@Singleton
class CloudflareAiService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {
    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Text-to-image. Detection automatique du format de reponse (JSON FLUX
     * vs binaire SDXL) via Content-Type.
     *
     * @param prompt description de l'image
     * @param model id "@cf/..." (cf. catalogue)
     * @param accountId Account ID Cloudflare (32 chars hex)
     * @param token API token cfat-xxx
     * @param steps FLUX : ~4 conseille, sinon defaut modele (SDXL ~20-30)
     */
    suspend fun txt2img(
        prompt: String,
        model: String,
        accountId: String,
        token: String,
        steps: Int? = null,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<ImageGenerationService.ImageGenerationResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = "$BASE_URL/$accountId/ai/run/$model"
            val payload = mutableMapOf<String, Any>("prompt" to prompt)
            // FLUX optimise pour low-step
            if (steps != null) {
                payload["steps"] = steps
            } else if (model.contains("flux", ignoreCase = true)) {
                payload["steps"] = 4
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string()?.take(300) ?: ""
                throw Exception("HTTP ${response.code} : $err")
            }
            val (bytes, mime) = decodeResponse(response)

            val result = ImageGenerationService.ImageGenerationResult(
                imageBytes = bytes,
                mimeType = mime,
                sizeBytes = bytes.size,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.CLOUDFLARE_AI, model = model,
                tokensInput = prompt.length / 4,
                tokensOutput = bytes.size / 100,
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "txt2img failed", e)
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.CLOUDFLARE_AI, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    /**
     * Image-to-image en multipart. Le caller doit fournir l'image source en
     * bytes (deja decodee depuis URI/file). On resize cote client a 512×512
     * MAX pour respecter la contrainte FLUX.2 Klein.
     *
     * Multipart fields :
     *  - `input_image_0` : file (image/jpeg)
     *  - `prompt` : texte
     *  - `width` / `height` : taille SORTIE en string ("1024")
     */
    suspend fun img2img(
        prompt: String,
        sourceImageBytes: ByteArray,
        model: String,
        accountId: String,
        token: String,
        outputWidth: Int = 1024,
        outputHeight: Int = 1024,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<ImageGenerationService.ImageGenerationResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            // Resize source ≤ 512×512 (contrainte FLUX.2 Klein notamment)
            val resizedBytes = resizeImageMax512(sourceImageBytes)

            val url = "$BASE_URL/$accountId/ai/run/$model"
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "input_image_0", "source.jpg",
                    resizedBytes.toRequestBody("image/jpeg".toMediaType()),
                )
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("width", outputWidth.toString())
                .addFormDataPart("height", outputHeight.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string()?.take(300) ?: ""
                throw Exception("HTTP ${response.code} : $err")
            }
            val (bytes, mime) = decodeResponse(response)

            val result = ImageGenerationService.ImageGenerationResult(
                imageBytes = bytes,
                mimeType = mime,
                sizeBytes = bytes.size,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.CLOUDFLARE_AI, model = model,
                tokensInput = prompt.length / 4,
                tokensOutput = bytes.size / 100,
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "img2img failed", e)
            usageRecorder.record(
                assistant = assistant, provider = LlmProvider.CLOUDFLARE_AI, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    /**
     * Decode la reponse Cloudflare selon le Content-Type :
     *  - `application/json` -> parse `result.image` base64
     *  - binaire (image/png, image/jpeg, image/webp) -> bytes direct
     */
    private fun decodeResponse(response: okhttp3.Response): Pair<ByteArray, String> {
        val contentType = response.header("Content-Type") ?: ""
        return if (contentType.contains("application/json")) {
            val bodyStr = response.body?.string() ?: throw Exception("JSON body vide")
            val json = JsonParser.parseString(bodyStr).asJsonObject
            val success = json.get("success")?.asBoolean ?: false
            if (!success) {
                val errors = json.getAsJsonArray("errors")?.toString() ?: bodyStr.take(200)
                throw Exception("Cloudflare error : $errors")
            }
            val b64 = json.getAsJsonObject("result")?.get("image")?.asString
                ?: throw Exception("Pas de result.image dans la reponse")
            Base64.decode(b64, Base64.DEFAULT) to "image/png"
        } else {
            val bytes = response.body?.bytes() ?: throw Exception("Body binaire vide")
            bytes to (contentType.takeIf { it.startsWith("image/") } ?: "image/png")
        }
    }

    /**
     * Resize l'image source pour respecter la contrainte ≤ 512×512 du
     * modele FLUX.2 Klein.
     *
     * Fix #8 OOM protection : pour les grosses images (4000×3000 = 50 MB
     * Bitmap), on ne decode PAS le full bitmap. On lit d'abord les bounds
     * via `inJustDecodeBounds=true`, on calcule un `inSampleSize` qui
     * down-sample DECODER-side (jamais alloue la full image en memoire),
     * puis on scale finement. Garanties : peak memory ~MAX_DIM*MAX_DIM*4
     * bytes = ~1 MB max, quelle que soit la source.
     */
    private fun resizeImageMax512(sourceBytes: ByteArray): ByteArray {
        // 1. Lire les dimensions sans decoder (memory-cheap)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) throw Exception("Impossible de lire les dimensions source")

        // 2. Calcul inSampleSize : puissance de 2 telle que srcDim/sampleSize ~= MAX_DIM
        var sampleSize = 1
        while (srcW / (sampleSize * 2) >= MAX_DIM || srcH / (sampleSize * 2) >= MAX_DIM) {
            sampleSize *= 2
        }

        // 3. Decode avec down-sampling au decodeur
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOpts)
            ?: throw Exception("Decode image source impossible")
        val w = bitmap.width
        val h = bitmap.height

        // 4. Scale fin si encore au-dessus de MAX_DIM
        val finalBmp = if (w > MAX_DIM || h > MAX_DIM) {
            val scale = MAX_DIM.toFloat() / maxOf(w, h)
            val newW = (w * scale).toInt().coerceAtLeast(1)
            val newH = (h * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (scaled !== bitmap) bitmap.recycle()
            scaled
        } else bitmap

        val out = ByteArrayOutputStream().apply {
            finalBmp.compress(Bitmap.CompressFormat.JPEG, 90, this)
        }.toByteArray()
        finalBmp.recycle()
        return out
    }

    companion object {
        private const val TAG = "CloudflareAiSvc"
        private const val BASE_URL = "https://api.cloudflare.com/client/v4/accounts"
        private const val MAX_DIM = 512 // contrainte FLUX.2 Klein
    }
}
