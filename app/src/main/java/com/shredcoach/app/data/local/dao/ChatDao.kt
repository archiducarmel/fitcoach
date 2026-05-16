package com.shredcoach.app.data.local.dao


import androidx.compose.runtime.Immutable
import androidx.room.*
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Immutable
data class ConversationSummary(
    val conversationId: String,
    val firstUserMessage: String?, // Peut être null si seul message est du bot
    val lastTimestamp: String,
    val messageCount: Int
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    /** Snapshot global de toutes les conversations, utilisé par le backup. */
    @Query("SELECT * FROM chat_messages ORDER BY conversationId ASC, timestamp ASC")
    suspend fun getAllMessagesOnce(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<ChatMessageEntity>

    /** Snapshot une-shot ASC d'une conversation (utilisé par le récap historique). */
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationOnce(conversationId: String): List<ChatMessageEntity>

    // ─── Variantes filtrées par PERSONA (Shreddy vs Dr. Glykos) ──────

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId AND persona = :persona")
    suspend fun deleteConversationForPersona(conversationId: String, persona: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

    /**
     * Update la note utilisateur sur un message assistant. [rating] : +1 = up,
     * -1 = down, null = unrate. Sert au feedback continu (P2a télémétrie).
     */
    @Query("UPDATE chat_messages SET userRating = :rating WHERE id = :messageId")
    suspend fun updateRating(messageId: Long, rating: Int?)

    /** Liste des conversations triées par date (la plus récente en premier). */
    @Query("""
        SELECT
            cm.conversationId,
            (SELECT content FROM chat_messages sub
             WHERE sub.conversationId = cm.conversationId AND sub.role = 'user'
             ORDER BY sub.timestamp ASC LIMIT 1) as firstUserMessage,
            MAX(cm.timestamp) as lastTimestamp,
            COUNT(*) as messageCount
        FROM chat_messages cm
        GROUP BY cm.conversationId
        ORDER BY MAX(cm.timestamp) DESC
    """)
    fun getAllConversations(): Flow<List<ConversationSummary>>

    /** Variante filtrée par persona (Shreddy vs Dr. Glykos). */
    @Query("""
        SELECT
            cm.conversationId,
            (SELECT content FROM chat_messages sub
             WHERE sub.conversationId = cm.conversationId AND sub.role = 'user' AND sub.persona = :persona
             ORDER BY sub.timestamp ASC LIMIT 1) as firstUserMessage,
            MAX(cm.timestamp) as lastTimestamp,
            COUNT(*) as messageCount
        FROM chat_messages cm
        WHERE cm.persona = :persona
        GROUP BY cm.conversationId
        ORDER BY MAX(cm.timestamp) DESC
    """)
    fun getAllConversationsForPersona(persona: String): Flow<List<ConversationSummary>>
}
