package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service pour les modeles STT (Speech-To-Text / ASR).
 *
 * **Format OpenAI-compatible** : POST /v1/audio/transcriptions
 *  - **multipart/form-data** avec :
 *    - file : audio (wav/mp3/m4a/webm/ogg/flac)
 *    - model : id du modele
 *    - language (optionnel) : ISO-639-1 code (ex: "fr", "en")
 *    - response_format : "json" | "text" | "verbose_json" | "srt" | "vtt"
 *    - temperature (optionnel)
 *  - Reponse JSON : `{"text": "transcript...", "language": "fr", "duration": 12.3}`
 *
 * **Providers** :
 *  - NVIDIA NIM (Whisper, Parakeet, Canary multilingue)
 *  - GitHub Models : pas de STT publique
 *
 * **Canary** supporte aussi la traduction simultanee (paramètres specifiques NVIDIA).
 */
@Singleton
class SttService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
) {
    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS) // gros fichiers audio
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @androidx.compose.runtime.Immutable
    data class TranscriptionResult(
        val text: String,
        val language: String? = null,
        val durationSec: Double? = null,
        val segments: List<Segment>? = null,
        val latencyMs: Long,
    )

    @androidx.compose.runtime.Immutable
    data class Segment(
        val startSec: Double,
        val endSec: Double,
        val text: String,
    )

    /**
     * Transcrit un fichier audio. Le caller fournit un File pointant vers
     * un audio existant (wav recommande pour qualite, mp3 pour taille).
     *
     * @param audioFile fichier audio (sera lu en bytes via multipart)
     * @param mimeType "audio/wav", "audio/mpeg", "audio/m4a", etc.
     * @param model id du modele (ex: "openai/whisper-large-v3")
     * @param provider NVIDIA_NIM
     * @param apiKey cle API
     * @param language ISO-639-1 (ex: "fr"). Auto-detect si null.
     * @param withTimestamps demande verbose_json avec segments timestamps
     */
    suspend fun transcribe(
        audioFile: File,
        mimeType: String,
        model: String,
        provider: LlmProvider,
        apiKey: String,
        language: String? = null,
        withTimestamps: Boolean = false,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            if (!audioFile.exists()) throw Exception("Fichier audio absent : ${audioFile.path}")
            if (audioFile.length() == 0L) throw Exception("Fichier audio vide")

            val url = transcriptionsUrl(provider)
            val responseFormat = if (withTimestamps) "verbose_json" else "json"
            val multipartBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody(mimeType.toMediaType()),
                )
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", responseFormat)

            if (!language.isNullOrBlank()) {
                multipartBuilder.addFormDataPart("language", language)
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .post(multipartBuilder.build())
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code} : ${body.take(200)}")

            val json = JsonParser.parseString(body).asJsonObject
            val text = json.get("text")?.asString ?: throw Exception("Pas de text dans la reponse")
            val language2 = json.get("language")?.asString
            val duration = json.get("duration")?.asDouble
            val segments = json.getAsJsonArray("segments")?.map { seg ->
                val s = seg.asJsonObject
                Segment(
                    startSec = s.get("start")?.asDouble ?: 0.0,
                    endSec = s.get("end")?.asDouble ?: 0.0,
                    text = s.get("text")?.asString.orEmpty(),
                )
            }

            val result = TranscriptionResult(
                text = text,
                language = language2,
                durationSec = duration,
                segments = segments,
                latencyMs = System.currentTimeMillis() - startMs,
            )
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                // STT facture typiquement par minute audio. On estime via duration.
                tokensInput = (duration?.toInt() ?: 0) * 60, // proxy "seconds-tokens"
                tokensOutput = text.length / 4, // proxy
                tokensThinking = 0,
                latencyMs = result.latencyMs, success = true,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "transcribe failed", e)
            usageRecorder.record(
                assistant = assistant, provider = provider, model = model,
                tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    private fun transcriptionsUrl(provider: LlmProvider): String = when (provider) {
        LlmProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/audio/transcriptions"
        else -> throw IllegalArgumentException("Provider $provider ne supporte pas /audio/transcriptions")
    }

    companion object {
        private const val TAG = "SttService"
    }
}
