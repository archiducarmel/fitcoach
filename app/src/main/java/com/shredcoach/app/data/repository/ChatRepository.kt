package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.ChatDao
import com.shredcoach.app.data.local.dao.ConversationSummary
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.data.remote.ChatMessage
import com.shredcoach.app.data.remote.LlmApiService
import com.shredcoach.app.data.remote.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val llmApiService: LlmApiService
) {
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    fun getAllConversations(): Flow<List<ConversationSummary>> =
        chatDao.getAllConversations()

    suspend fun getRecentMessages(conversationId: String, limit: Int = 20): List<ChatMessageEntity> =
        chatDao.getRecentMessages(conversationId, limit)

    suspend fun insertMessage(message: ChatMessageEntity): Long =
        chatDao.insertMessage(message)

    suspend fun deleteConversation(conversationId: String) =
        chatDao.deleteConversation(conversationId)

    suspend fun clearHistory() = chatDao.clearAll()

    /** Appel rapide pour messages courts — utilise le streaming et collecte tout. */
    suspend fun quickCoachMessage(
        prompt: String,
        systemPrompt: String,
        provider: LlmProvider,
        apiKey: String,
        model: String? = null
    ): Result<String> = try {
        val messages = listOf(ChatMessage(role = "user", content = prompt))
        val buffer = StringBuilder()
        llmApiService.streamMessage(
            messages = messages, provider = provider, apiKey = apiKey, model = model,
            overrideSystemPrompt = systemPrompt, // Remplace le system prompt principal
            slowMode = false // Pas de delay entre tokens — on veut la réponse vite
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
        userContext: String = ""
    ): Flow<String> {
        val history = recentMessages.filter { !it.isError }.map {
            ChatMessage(role = it.role, content = it.content)
        }
        val messages = history + ChatMessage(role = "user", content = userMessage)
        return llmApiService.streamMessage(messages, provider, apiKey, model, userContext)
    }
}
