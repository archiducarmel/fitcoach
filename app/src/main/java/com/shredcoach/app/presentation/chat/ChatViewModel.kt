package com.shredcoach.app.presentation.chat


import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.ConversationSummary
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserContextBuilder
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.chat.ChatPersona
import com.shredcoach.app.domain.llm.AiAssistant
import com.shredcoach.app.domain.llm.AssistantLlmResolver
import com.shredcoach.app.domain.locale.withCurrentLocale
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
    val showConversationList: Boolean = false,
    /** Persona du chat (Shreddy par défaut, Dr. Glykos pour /chat/dr_glykos). */
    val persona: ChatPersona = ChatPersona.SHREDDY,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val userContextBuilder: UserContextBuilder,
    private val llmResolver: AssistantLlmResolver,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val applicationContext: android.content.Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Persona lue depuis l'argument de nav (clé `"persona"`). Permet à la
     * MÊME ChatScreen / ViewModel d'être utilisée pour Shreddy ET Dr. Glykos
     * sans duplication d'infrastructure.
     */
    private val persona: ChatPersona =
        ChatPersona.fromTag(savedStateHandle.get<String>("persona"))

    private val _state = MutableStateFlow(ChatState(persona = persona))
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
            // Filtre par persona pour ne pas mélanger Shreddy et Dr. Glykos
            chatRepository.getAllConversationsForPersona(persona).collect { convos ->
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

            // Sauver le message utilisateur (avec persona pour isoler Shreddy vs Dr. Glykos)
            chatRepository.insertMessage(ChatMessageEntity(
                conversationId = conversationId, role = "user", content = text,
                persona = persona.tag,
            ))

            val profile = userRepository.getUserProfileOnce()
            val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
            // Resolver per-assistant : ChatShreddy vs ChatDrGlykos peuvent
            // utiliser un LLM different (configurable dans Settings → Assistants IA).
            val assistant = if (persona == ChatPersona.DR_GLYKOS) AiAssistant.CHAT_DR_GLYKOS else AiAssistant.CHAT_SHREDDY
            val llmConfig = llmResolver.resolveWithProfile(assistant, profile)
            val provider = llmConfig.provider
            val model: String? = llmConfig.modelId  // jamais null (resolver garantit non-blank)

            if (apiKey.isBlank()) {
                // Persona-aware : message renvoie vers l'écran de réglages du
                // bon assistant (Shreddy ou Dr. Glykos) — sinon l'utilisateur
                // est dirigé vers les réglages de l'autre persona.
                val errMsgRes = if (persona == ChatPersona.DR_GLYKOS) {
                    com.shredcoach.app.R.string.chat_error_no_api_key_dr_glykos
                } else {
                    com.shredcoach.app.R.string.chat_error_no_api_key_shreddy
                }
                chatRepository.insertMessage(ChatMessageEntity(
                    conversationId = conversationId, role = "assistant",
                    content = applicationContext.withCurrentLocale().getString(errMsgRes),
                    isError = true,
                    persona = persona.tag,
                ))
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            // Contexte : full au turn 1, mini-rappel prénom aux turns suivants
            val isFirstMessage = recent.isEmpty()
            val profileForName = userRepository.getUserProfileOnce()
            val firstName = profileForName?.firstName?.takeIf { it.isNotBlank() } ?: "l'utilisateur"
            val baseContext = if (isFirstMessage) {
                try { userContextBuilder.buildContext() } catch (_: Exception) { "[PROFIL] Prénom: $firstName" }
            } else {
                "[RAPPEL] Le prénom de l'utilisateur est $firstName. Ne te présente pas, continue la conversation."
            }
            // Sliding window : on n'envoie que les 10 derniers messages (cf.
            // `getRecentMessages(..., 10)`). Pour les conversations longues
            // (>10), on prepend un récap extractif des plus anciens pour que
            // le LLM ne perde pas le contexte du début. Coût ~1ms, déterministe.
            val historyRecap = chatRepository.buildHistoryRecap(conversationId)
            val userContext = if (historyRecap != null) "$historyRecap\n\n$baseContext" else baseContext

            // Safety filter : si le user mentionne un symptôme critique, on
            // pré-affiche un disclaimer "consulter un médecin" AVANT toute
            // réponse LLM. Couvre les cas où le LLM ignorerait sa consigne
            // "JAMAIS de conseils médicaux".
            val needsMedicalSafety = com.shredcoach.app.domain.chat.MedicalSafetyFilter
                .isMedicalCritical(text)
            if (needsMedicalSafety) {
                val banner = com.shredcoach.app.domain.chat.MedicalSafetyFilter
                    .safetyBanner(applicationContext)
                _state.update { it.copy(streamingText = banner) }
            }

            // Démarrer le chrono LLM (télémétrie : envoi → dernier token).
            val turnStartMs = System.currentTimeMillis()

            try {
                val buffer = StringBuilder()
                if (needsMedicalSafety) {
                    buffer.append(
                        com.shredcoach.app.domain.chat.MedicalSafetyFilter
                            .safetyBanner(applicationContext)
                    )
                }
                // Routage par intent (P0a fix) :
                //  - Action verbs (log, je pèse, j'ai mangé, où j'en suis…)
                //    → tool-aware path (non-streaming, ~2-5s avant le 1er char
                //    mais permet log_meal / set_weight / get_today_stats).
                //  - Sinon : streaming SSE rapide (1er token ~300-500ms),
                //    sans tools. Le snapshot context du turn 1 + system prompt
                //    suffisent largement pour répondre à 80% des questions
                //    coaching/conseil sans appel d'outil.
                val useTools = com.shredcoach.app.domain.chat.ChatIntentClassifier
                    .shouldUseTools(text)

                if (useTools) {
                    // Le system prompt utilisé par les tools est persona-aware.
                    val basePrompt = when (persona) {
                        ChatPersona.DR_GLYKOS ->
                            com.shredcoach.app.domain.chat.DrGlykosSystemPrompt.SYSTEM_PROMPT
                        ChatPersona.SHREDDY ->
                            com.shredcoach.app.data.remote.LlmApiService.SYSTEM_PROMPT
                    }
                    val fullSystemPrompt = basePrompt +
                        if (userContext.isNotBlank()) "\n\n$userContext" else ""
                    chatRepository.streamFromLlmWithTools(
                        userMessage = text,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        recentMessages = recent,
                        systemPrompt = fullSystemPrompt,
                        persona = persona,
                        assistant = when (persona) {
                            ChatPersona.DR_GLYKOS -> com.shredcoach.app.domain.llm.AiAssistant.CHAT_DR_GLYKOS
                            ChatPersona.SHREDDY -> com.shredcoach.app.domain.llm.AiAssistant.CHAT_SHREDDY
                        },
                    ).collect { token ->
                        buffer.append(token)
                        _state.update { it.copy(streamingText = buffer.toString()) }
                    }
                } else {
                    // Streaming classique — userContext est injecté APRÈS le
                    // system prompt par LlmApiService.streamMessage.
                    chatRepository.streamFromLlm(
                        userMessage = text,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        recentMessages = recent,
                        userContext = userContext,
                        persona = persona,
                        assistant = when (persona) {
                            ChatPersona.DR_GLYKOS -> com.shredcoach.app.domain.llm.AiAssistant.CHAT_DR_GLYKOS
                            ChatPersona.SHREDDY -> com.shredcoach.app.domain.llm.AiAssistant.CHAT_SHREDDY
                        },
                    ).collect { token ->
                        buffer.append(token)
                        _state.update { it.copy(streamingText = buffer.toString()) }
                    }
                }

                val fullResponse = buffer.toString()
                if (fullResponse.isNotBlank()) {
                    chatRepository.insertMessage(ChatMessageEntity(
                        conversationId = conversationId, role = "assistant", content = fullResponse,
                        latencyMs = System.currentTimeMillis() - turnStartMs,
                        persona = persona.tag,
                    ))
                }
            } catch (e: Exception) {
                val partial = _state.value.streamingText
                if (partial.isNotBlank()) {
                    chatRepository.insertMessage(ChatMessageEntity(
                        conversationId = conversationId, role = "assistant", content = partial,
                        persona = persona.tag,
                    ))
                }
                chatRepository.insertMessage(ChatMessageEntity(
                    conversationId = conversationId, role = "assistant",
                    content = "Erreur : ${e.message ?: "Connexion impossible"}",
                    isError = true,
                    persona = persona.tag,
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

    /**
     * Note utilisateur sur un message assistant. [rating] : +1 = thumb up,
     * -1 = thumb down, null = un-rate. Le UI Compose appelle cette méthode
     * sur tap d'un bouton thumb. Source de vérité pour l'amélioration empirique
     * des prompts via analyse offline des bad ratings.
     */
    fun rateMessage(messageId: Long, rating: Int?) {
        viewModelScope.launch { chatRepository.updateRating(messageId, rating) }
    }
}
