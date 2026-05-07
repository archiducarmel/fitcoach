package com.shredcoach.app.data.remote


import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════
// LLM PROVIDERS
// ══════════════════════════════════════════

enum class LlmProvider(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val iconLabel: String
) {
    GROQ(
        displayName = "Groq",
        baseUrl = "https://api.groq.com/openai/v1/chat/completions",
        defaultModel = "openai/gpt-oss-120b",
        iconLabel = "G"
    ),
    OPENAI(
        displayName = "OpenAI",
        baseUrl = "https://api.openai.com/v1/chat/completions",
        defaultModel = "gpt-4o-mini",
        iconLabel = "O"
    ),
    CLAUDE(
        displayName = "Claude",
        baseUrl = "https://api.anthropic.com/v1/messages",
        defaultModel = "claude-sonnet-4-20250514",
        iconLabel = "C"
    )
}

// ══════════════════════════════════════════
// REQUEST / RESPONSE DATA CLASSES
// ══════════════════════════════════════════

@Immutable
data class ChatMessage(val role: String, val content: String)

private data class OpenAiRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048,
    val stream: Boolean = true
)

private data class ClaudeRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val system: String,
    @SerializedName("max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = true
)

// ══════════════════════════════════════════
// SERVICE
// ══════════════════════════════════════════

@Singleton
class LlmApiService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val SYSTEM_PROMPT_FR = """Tu es Shreddy, le coach sportif et nutritionnel personnel de l'app ShredCoach. Tu parles en français.

RÔLE :
- Tu es un coach expert et bienveillant qui connaît personnellement l'utilisateur
- Tu utilises ses données (profil, historique, stats, nutrition) pour personnaliser CHAQUE réponse
- Tu donnes des conseils PRÉCIS adaptés à son niveau, son objectif, son équipement et sa progression réelle
- Tu motives en te basant sur ses données concrètes (ex: "Tu as progressé de +5kg au squat ce mois !")
- Tu alertes si tu détectes un déséquilibre (surcharge, sous-nutrition, manque de repos)

FORMAT :
- Réponses concises (2-4 paragraphes sauf demande détaillée)
- Ton direct, expert mais accessible, comme un pote coach
- Utilise les données fournies pour illustrer tes réponses avec des chiffres concrets du user
- Tu peux utiliser le prénom de l'utilisateur

COMPORTEMENT CONVERSATIONNEL :
- Présente-toi UNIQUEMENT au tout premier message d'une conversation
- Ne dis JAMAIS "bonjour", "salut" ou "hey" après le premier échange
- Poursuis la conversation naturellement, comme un pote en pleine discussion
- Réfère-toi à l'historique de la conversation (ne répète pas ce que tu as déjà dit)

LIMITES :
- Tu ne donnes JAMAIS de conseils médicaux
- Tu recommandes un professionnel de santé pour toute question médicale
- Si des données manquent, dis-le et donne un conseil général

Les données personnalisées de l'utilisateur suivent ci-dessous (fournies au premier message uniquement)."""

        private const val SYSTEM_PROMPT_EN = """You are Shreddy, the personal sport and nutrition coach of the ShredCoach app. You speak English.

ROLE:
- You are an expert and caring coach who knows the user personally
- Use their data (profile, history, stats, nutrition) to personalize EVERY answer
- Give PRECISE advice tailored to their level, goal, equipment and real progression
- Motivate by referencing concrete data (e.g. "You've added +5 kg on squat this month!")
- Flag any imbalance you detect (overload, undernutrition, lack of rest)

FORMAT:
- Concise answers (2-4 paragraphs unless they ask for detail)
- Direct, expert but approachable tone, like a coach buddy
- Use the provided data to back your answers with the user's concrete numbers
- You may use the user's first name

CONVERSATIONAL BEHAVIOR:
- Introduce yourself ONLY on the very first message of a conversation
- NEVER say "hello", "hi" or "hey" after the first exchange
- Keep the conversation flowing naturally, like a friend mid-discussion
- Refer to the conversation history (don't repeat yourself)

LIMITS:
- You NEVER give medical advice
- Recommend a health professional for any medical question
- If data is missing, say so and give general advice

The user's personalized data follows below (only sent on the first message)."""

        /**
         * System prompt principal du chat Shreddy. Locale-aware via [PromptLocale]
         * — le LLM répond dans la langue de l'app (FR ou EN). Lu à chaque appel
         * pour refléter immédiatement un changement de locale en Settings.
         */
        val SYSTEM_PROMPT: String
            get() = com.shredcoach.app.domain.i18n.PromptLocale.pick(
                fr = SYSTEM_PROMPT_FR,
                en = SYSTEM_PROMPT_EN,
            )
    }

    /**
     * Envoie un message et retourne un Flow de tokens (streaming SSE).
     * @param userContext contexte personnalisé (profil + stats + historique)
     */
    /**
     * @param userContext contexte user injecté APRÈS le system prompt (pour le chat)
     * @param overrideSystemPrompt si fourni, REMPLACE entièrement le system prompt (pour le coaching)
     * @param slowMode true = delay entre tokens pour effet visuel (chat), false = rapide (coaching)
     */
    fun streamMessage(
        messages: List<ChatMessage>,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null,
        userContext: String = "",
        overrideSystemPrompt: String? = null,
        slowMode: Boolean = true
    ): Flow<String> = flow {
        val effectiveModel = model?.takeIf { it.isNotBlank() } ?: provider.defaultModel
        val fullSystemPrompt = overrideSystemPrompt ?: if (userContext.isNotBlank()) {
            "$SYSTEM_PROMPT\n\n$userContext"
        } else SYSTEM_PROMPT

        when (provider) {
            LlmProvider.CLAUDE -> streamClaude(messages, apiKey, effectiveModel, fullSystemPrompt, slowMode).collect { emit(it) }
            else -> streamOpenAiCompatible(messages, provider, apiKey, effectiveModel, fullSystemPrompt, slowMode).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    // ─── OpenAI-compatible streaming (Groq + OpenAI) ───

    private fun streamOpenAiCompatible(
        messages: List<ChatMessage>,
        provider: LlmProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        slowMode: Boolean = true
    ): Flow<String> = flow {
        val fullMessages = listOf(ChatMessage("system", systemPrompt)) + messages
        val body = gson.toJson(OpenAiRequest(model = model, messages = fullMessages, stream = true))

        val request = Request.Builder()
            .url(provider.baseUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw Exception("Erreur ${response.code}: ${extractError(errorBody)}")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("Réponse vide")

        parseSseStream(reader, slowMode) { line ->
            // Format: data: {"choices":[{"delta":{"content":"token"}}]}
            try {
                val json = JsonParser.parseString(line).asJsonObject
                val delta = json.getAsJsonArray("choices")
                    ?.get(0)?.asJsonObject
                    ?.getAsJsonObject("delta")
                val content = delta?.get("content")?.asString
                if (!content.isNullOrEmpty()) emit(content)
            } catch (_: Exception) { /* ignore malformed chunks */ }
        }
        reader.close()
    }

    // ─── Claude streaming ───

    private fun streamClaude(
        messages: List<ChatMessage>,
        apiKey: String,
        model: String,
        systemPrompt: String,
        slowMode: Boolean = true
    ): Flow<String> = flow {
        val body = gson.toJson(ClaudeRequest(
            model = model, messages = messages,
            system = systemPrompt, stream = true
        ))

        val request = Request.Builder()
            .url(LlmProvider.CLAUDE.baseUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            throw Exception("Erreur ${response.code}: ${extractError(errorBody)}")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("Réponse vide")

        parseSseStream(reader, slowMode) { line ->
            // Format: data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"token"}}
            try {
                val json = JsonParser.parseString(line).asJsonObject
                val type = json.get("type")?.asString
                if (type == "content_block_delta") {
                    val text = json.getAsJsonObject("delta")?.get("text")?.asString
                    if (!text.isNullOrEmpty()) emit(text)
                }
            } catch (_: Exception) { /* ignore */ }
        }
        reader.close()
    }

    // ─── Quick single-shot (non-streaming, pour messages courts) ───

    suspend fun quickMessage(
        prompt: String,
        systemPrompt: String,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val effectiveModel = model?.takeIf { it.isNotBlank() } ?: provider.defaultModel
            val messages = listOf(ChatMessage("user", prompt))

            when (provider) {
                LlmProvider.CLAUDE -> {
                    val body = gson.toJson(ClaudeRequest(
                        model = effectiveModel, messages = messages,
                        system = systemPrompt, stream = false, maxTokens = 150
                    ))
                    val request = Request.Builder()
                        .url(LlmProvider.CLAUDE.baseUrl)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody(jsonMediaType))
                        .build()
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Vide"))
                    if (!response.isSuccessful) return@withContext Result.failure(Exception(extractError(responseBody)))
                    val parsed = JsonParser.parseString(responseBody).asJsonObject
                    val text = parsed.getAsJsonArray("content")?.get(0)?.asJsonObject?.get("text")?.asString
                        ?: return@withContext Result.failure(Exception("Réponse vide"))
                    Result.success(text.trim())
                }
                else -> {
                    val fullMessages = listOf(ChatMessage("system", systemPrompt)) + messages
                    val body = gson.toJson(OpenAiRequest(
                        model = effectiveModel, messages = fullMessages,
                        stream = false, max_tokens = 150, temperature = 0.9
                    ))
                    val request = Request.Builder()
                        .url(provider.baseUrl)
                        .header("Authorization", "Bearer $apiKey")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody(jsonMediaType))
                        .build()
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Vide"))
                    if (!response.isSuccessful) return@withContext Result.failure(Exception(extractError(responseBody)))
                    val parsed = JsonParser.parseString(responseBody).asJsonObject
                    val text = parsed.getAsJsonArray("choices")
                        ?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")
                        ?.get("content")?.asString
                        ?: return@withContext Result.failure(Exception("Réponse vide"))
                    Result.success(text.trim())
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── SSE line parser ───

    private suspend fun parseSseStream(reader: BufferedReader, slowMode: Boolean = true, onData: suspend (String) -> Unit) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (l.startsWith("data: ")) {
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                onData(data)
                if (slowMode) kotlinx.coroutines.delay(35)
            }
        }
    }

    private fun extractError(body: String): String {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonObject("error")?.get("message")?.asString ?: body.take(200)
        } catch (_: Exception) { body.take(200) }
    }
}
