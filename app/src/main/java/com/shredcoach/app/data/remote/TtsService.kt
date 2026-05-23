package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.Gson
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
 * Service pour les modeles TTS (Text-To-Speech).
 *
 * **Format OpenAI-compatible** : POST /v1/audio/speech
 *  - Payload : `{"model": "...", "input": "texte", "voice": "...", "response_format": "mp3"}`
 *  - Reponse : audio binaire directement (pas JSON)
 *
 * **Providers supportes** :
 *  - NVIDIA NIM (Magpie multilingual/flow/zeroshot)
 *  - GitHub Models : pas de TTS publique au moment de l'integration
 *
 * **Voices** : depend du modele. Magpie multilingual a des voix par locale.
 * **Formats** : mp3, opus, aac, flac, wav, pcm
 */
@Singleton
class TtsService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {
    private val client = baseClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @androidx.compose.runtime.Immutable
    data class TtsResult(
        val audioBytes: ByteArray,
        val mimeType: String,
        val sizeBytes: Int,
        val latencyMs: Long,
    ) {
        override fun equals(other: Any?): Boolean = other is TtsResult && audioBytes.contentEquals(other.audioBytes)
        override fun hashCode(): Int = audioBytes.contentHashCode()
    }

    /**
     * Genere de l'audio depuis un texte.
     *
     * @param text texte a vocaliser (max ~4096 chars typiquement)
     * @param model id du modele (ex: "nvidia/magpie-tts-multilingual")
     * @param provider NVIDIA_NIM
     * @param apiKey cle API
     * @param voice id de la voix (depend du modele)
     * @param format mp3 / wav / opus / flac / aac / pcm
     */
    suspend fun synthesize(
        text: String,
        model: String,
        provider: LlmProvider,
        apiKey: String,
        voice: String = "default",
        format: String = "mp3",
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<TtsResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val url = speechUrl(provider)
            val payload = mapOf(
                "model" to model,
                "input" to text,
                "voice" to voice,
                "response_format" to format,
            )
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .header("Accept", mimeForFormat(format))
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(200) ?: ""
                throw Exception("HTTP ${response.code} : $errBody")
            }
            val bytes = response.body?.bytes() ?: throw Exception("Reponse vide")
            val result = TtsResult(
                audioBytes = bytes,
                mimeType = mimeForFormat(format),
                sizeBytes = bytes.size,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = text.length / 4, // estimation
                tokensOutput = bytes.size / 100, // proxy audio bytes
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "synthesize failed", e)
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    private fun speechUrl(provider: LlmProvider): String = when (provider) {
        LlmProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/audio/speech"
        else -> throw IllegalArgumentException("Provider $provider ne supporte pas /audio/speech")
    }

    private fun mimeForFormat(format: String): String = when (format.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "opus" -> "audio/opus"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "pcm" -> "audio/pcm"
        else -> "audio/mpeg"
    }

    companion object {
        private const val TAG = "TtsService"
    }
}
