package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "chat_messages", indices = [Index("conversationId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String, // UUID de la conversation
    val role: String, // "user" ou "assistant"
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isError: Boolean = false
)
