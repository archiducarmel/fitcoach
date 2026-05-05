package com.shredcoach.app.presentation.chat


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.ConversationSummary
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserContextBuilder
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Immutable
data class ChatState(
    val messages: List<ChatMessageEntity> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val currentConversationId: String = UUID.randomUUID().toString(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val streamingText: String = "",
    val isConfigured: Boolean = false,
    val providerName: String = "Groq",
    val showConversationList: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val userContextBuilder: UserContextBuilder
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var messagesJob: Job? = null

    init {
        observeMessages()
        loadConversations()
        loadConfig()
    }

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesForConversation(_state.value.currentConversationId)
                .collect { messages -> _state.update { it.copy(messages = messages) } }
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { convos ->
                _state.update { it.copy(conversations = convos) }
            }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            _state.update { it.copy(
                isConfigured = userRepository.hasApiKey(SecureKeyStore.Provider.LLM),
                providerName = try { LlmProvider.valueOf(profile?.llmProvider ?: "GROQ").displayName } catch (_: Exception) { "Groq" }
            ) }
        }
    }

    fun onInputChanged(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    // ══════════════════════════════════════════
    // CONVERSATIONS
    // ══════════════════════════════════════════

    fun startNewConversation() {
        _state.update { it.copy(
            currentConversationId = UUID.randomUUID().toString(),
            messages = emptyList(), showConversationList = false
        ) }
        observeMessages()
    }

    fun openConversation(conversationId: String) {
        _state.update { it.copy(currentConversationId = conversationId, showConversationList = false) }
        observeMessages()
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            // Si c'est la conversation active, en démarrer une nouvelle
            if (_state.value.currentConversationId == conversationId) {
                startNewConversation()
            }
        }
    }

    fun toggleConversationList() {
        _state.update { it.copy(showConversationList = !it.showConversationList) }
    }

    // ══════════════════════════════════════════
    // ENVOI MESSAGE (streaming)
    // ══════════════════════════════════════════

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank() || _state.value.isLoading) return

        val conversationId = _state.value.currentConversationId
        _state.update { it.copy(inputText = "", isLoading = true, streamingText = "") }

        viewModelScope.launch {
            // Charger l'historique AVANT d'insérer le nouveau message (sinon il serait en double)
            val recent = chatRepository.getRecentMessages(conversationId, 10).reversed()

            // Sauver le message utilisateur
            chatRepository.insertMessage(ChatMessageEntity(
                conversationId = conversationId, role = "user", content = text
            ))

            val profile = userRepository.getUserProfileOnce()
            val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
            val providerStr = profile?.llmProvider ?: "GROQ"
            val provider = try { LlmProvider.valueOf(providerStr) } catch (_: Exception) { LlmProvider.GROQ }
            val model = profile?.llmModel?.takeIf { it.isNotBlank() }

            if (apiKey.isBlank()) {
                chatRepository.insertMessage(ChatMessageEntity(
                    conversationId = conversationId, role = "assistant",
                    content = "Configure ta clé API dans Réglages → Assistant Shreddy pour commencer.",
                    isError = true
                ))
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Contexte : full au turn 1, mini-rappel prénom aux turns suivants
            val isFirstMessage = recent.isEmpty()
            val profileForName = userRepository.getUserProfileOnce()
            val firstName = profileForName?.firstName?.takeIf { it.isNotBlank() } ?: "l'utilisateur"
            val userContext = if (isFirstMessage) {
                try { userContextBuilder.buildContext() } catch (_: Exception) { "[PROFIL] Prénom: $firstName" }
            } else {
                "[RAPPEL] Le prénom de l'utilisateur est $firstName. Ne te présente pas, continue la conversation."
            }

            try {
                val buffer = StringBuilder()
                chatRepository.streamFromLlm(text, provider, apiKey, model, recent, userContext)
                    .collect { token ->
                        buffer.append(token)
                        _state.update { it.copy(streamingText = buffer.toString()) }
                    }

                val fullResponse = buffer.toString()
                if (fullResponse.isNotBlank()) {
                    chatRepository.insertMessage(ChatMessageEntity(
                        conversationId = conversationId, role = "assistant", content = fullResponse
                    ))
                }
            } catch (e: Exception) {
                val partial = _state.value.streamingText
                if (partial.isNotBlank()) {
                    chatRepository.insertMessage(ChatMessageEntity(
                        conversationId = conversationId, role = "assistant", content = partial
                    ))
                }
                chatRepository.insertMessage(ChatMessageEntity(
                    conversationId = conversationId, role = "assistant",
                    content = "Erreur : ${e.message ?: "Connexion impossible"}",
                    isError = true
                ))
            }

            _state.update { it.copy(isLoading = false, streamingText = "") }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatRepository.deleteConversation(_state.value.currentConversationId)
            startNewConversation()
        }
    }
}
