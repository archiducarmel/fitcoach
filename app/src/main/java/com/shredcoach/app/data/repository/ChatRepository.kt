package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.ChatDao
import com.shredcoach.app.data.local.dao.ConversationSummary
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.data.remote.ChatMessage
import com.shredcoach.app.data.remote.LlmApiService
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.remote.LlmStreamEvent
import com.shredcoach.app.domain.chat.ChatHistorySummarizer
import com.shredcoach.app.domain.chat.ChatPersona
import com.shredcoach.app.domain.chat.DrGlykosSystemPrompt
import com.shredcoach.app.domain.chat.ShreddyToolExecutor
import com.shredcoach.app.domain.chat.ShreddyTools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val llmApiService: LlmApiService,
    private val toolExecutor: ShreddyToolExecutor,
) {
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    fun getAllConversations(): Flow<List<ConversationSummary>> =
        chatDao.getAllConversations()

    /** Variante persona-filtrée — affiche uniquement les conversations d'une persona. */
    fun getAllConversationsForPersona(persona: ChatPersona): Flow<List<ConversationSummary>> =
        chatDao.getAllConversationsForPersona(persona.tag)

    suspend fun getRecentMessages(conversationId: String, limit: Int = 20): List<ChatMessageEntity> =
        chatDao.getRecentMessages(conversationId, limit)

    /**
     * Construit un récap extractif des messages plus anciens que les
     * [recentWindow] derniers d'une conversation. Retourne null si la
     * conversation est courte (≤ recentWindow) ou s'il n'y a rien à résumer
     * (uniquement des erreurs/blancs).
     *
     * Coût : 1 read DAO ASC + traitement string. Conversation type < 100
     * messages → ~ms.
     */
    suspend fun buildHistoryRecap(conversationId: String, recentWindow: Int = RECENT_WINDOW): String? {
        val all = chatDao.getMessagesForConversationOnce(conversationId)
        if (all.size <= recentWindow) return null
        val older = all.dropLast(recentWindow)
        return ChatHistorySummarizer.summarize(older)
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long =
        chatDao.insertMessage(message)

    suspend fun deleteConversation(conversationId: String) =
        chatDao.deleteConversation(conversationId)

    suspend fun clearHistory() = chatDao.clearAll()

    /** Met à jour la note utilisateur sur un message (thumbs up/down). */
    suspend fun updateRating(messageId: Long, rating: Int?) =
        chatDao.updateRating(messageId, rating)

    /** Appel rapide pour messages courts — utilise le streaming et collecte tout. */
    suspend fun quickCoachMessage(
        prompt: String,
        systemPrompt: String,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Result<String> = try {
        val messages = listOf(ChatMessage(role = "user", content = prompt))
        val buffer = StringBuilder()
        llmApiService.streamMessage(
            messages = messages, provider = provider, apiKey = apiKey, model = model,
            overrideSystemPrompt = systemPrompt, // Remplace le system prompt principal
            slowMode = false, // Pas de delay entre tokens — on veut la réponse vite
            assistant = assistant,
            fallback = fallback,
        ).collect { token -> buffer.append(token) }
        val result = buffer.toString().trim()
        if (result.isNotBlank()) Result.success(result)
        else Result.failure(Exception("Réponse vide"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun streamFromLlm(
        userMessage: String,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null,
        recentMessages: List<ChatMessageEntity>,
        userContext: String = "",
        persona: ChatPersona = ChatPersona.SHREDDY,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Flow<String> {
        val history = recentMessages.filter { !it.isError }.map {
            ChatMessage(role = it.role, content = it.content)
        }
        val messages = history + ChatMessage(role = "user", content = userMessage)
        return when (persona) {
            ChatPersona.DR_GLYKOS -> {
                // Dr. Glykos a son propre system prompt → on override entièrement.
                // Le userContext est concaténé à la fin du prompt persona.
                val composed = if (userContext.isBlank())
                    DrGlykosSystemPrompt.SYSTEM_PROMPT
                else
                    DrGlykosSystemPrompt.SYSTEM_PROMPT + "\n\n" + userContext
                llmApiService.streamMessage(
                    messages = messages, provider = provider, apiKey = apiKey, model = model,
                    overrideSystemPrompt = composed,
                    assistant = assistant,
                    fallback = fallback,
                )
            }
            ChatPersona.SHREDDY ->
                llmApiService.streamMessage(
                    messages = messages, provider = provider, apiKey = apiKey, model = model,
                    userContext = userContext, assistant = assistant, fallback = fallback,
                )
        }
    }

    /**
     * Variante tool-aware : envoie le message avec tous les tools Shreddy
     * activés. Le LLM peut soit répondre directement, soit demander des
     * appels de tools — auquel cas on les exécute en boucle jusqu'à obtenir
     * une réponse texte finale.
     *
     * **V2 streaming** : sur OpenAI/Groq, les tokens texte sont émis dès leur
     * arrivée SSE. Si `finish_reason = tool_calls`, on exécute les tools puis
     * on relance un stream — le user voit le coach "réfléchir → agir → parler"
     * en quasi temps réel. Sur Claude, fallback non-streaming + faux-stream.
     *
     * **Boucle de tools** : limitée à [MAX_TOOL_ITERATIONS]=4 pour éviter
     * qu'un LLM en boucle infinie demande tools en chaîne. Au-delà, on force
     * une réponse texte sans tools.
     */
    fun streamFromLlmWithTools(
        userMessage: String,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null,
        recentMessages: List<ChatMessageEntity>,
        systemPrompt: String,
        persona: ChatPersona = ChatPersona.SHREDDY,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Flow<String> = flow {
        // Set d'outils par persona. Shreddy peut logger meals/poids; Dr. Glykos
        // ne fait QUE de la lecture glucose (pas de log médical sans encadrement).
        val tools = when (persona) {
            ChatPersona.DR_GLYKOS -> when (provider) {
                LlmProvider.CLAUDE -> ShreddyTools.DR_GLYKOS_CLAUDE
                else -> ShreddyTools.DR_GLYKOS_OPENAI
            }
            ChatPersona.SHREDDY -> when (provider) {
                LlmProvider.CLAUDE -> ShreddyTools.ALL_CLAUDE
                else -> ShreddyTools.ALL_OPENAI
            }
        }

        val history = recentMessages.filter { !it.isError }.map {
            ChatMessage(role = it.role, content = it.content)
        }
        val workingMessages = (history + ChatMessage("user", userMessage)).toMutableList()

        var iterations = 0
        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++
            // V2 : vraiment streaming. Les tokens sont émis directement vers le
            // UI tandis que d'éventuels tool_calls sont accumulés en interne.
            // Sur OpenAI/Groq : SSE temps réel ; sur Claude : fallback simulé.
            var pendingTools: LlmStreamEvent.ToolsReady? = null
            llmApiService.streamWithTools(
                messages = workingMessages,
                provider = provider,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                model = model,
                tools = tools,
                assistant = assistant,
            ).collect { event ->
                when (event) {
                    is LlmStreamEvent.Token -> emit(event.text)
                    is LlmStreamEvent.ToolsReady -> pendingTools = event
                }
            }
            val tr = pendingTools ?: return@flow

            workingMessages += ChatMessage(
                role = "assistant_with_tools",
                content = tr.assistantMessageRaw,
            )
            for (call in tr.toolCalls) {
                val result = toolExecutor.execute(call)
                workingMessages += llmApiService.toolResultMessage(
                    toolCallId = result.toolCallId,
                    content = result.content,
                )
            }
            // Boucle : on relance le streaming avec les résultats des tools.
        }

        // Sortie hard si on dépasse — dernier essai en streaming sans tools.
        llmApiService.streamWithTools(
            messages = workingMessages,
            provider = provider,
            apiKey = apiKey,
            systemPrompt = systemPrompt + "\n\nRépondre maintenant en texte uniquement, sans appeler d'outils.",
            model = model,
            tools = emptyList(),
            assistant = assistant,
        ).collect { event ->
            if (event is LlmStreamEvent.Token) emit(event.text)
        }
    }

    private companion object {
        const val MAX_TOOL_ITERATIONS = 4
        /** Taille de la fenêtre récente conservée dans le prompt LLM tel quel. */
        const val RECENT_WINDOW = 10
    }
}
