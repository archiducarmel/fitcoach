package com.shredcoach.app.data.local.dao

import androidx.room.*
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()

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
}
