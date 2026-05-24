package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId"), Index("persona")],
)
@Immutable
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String, // UUID de la conversation
    val role: String, // "user" ou "assistant"
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isError: Boolean = false,
    /**
     * Tag persona ([com.shredcoach.app.domain.chat.ChatPersona.tag]). Permet
     * de séparer les conversations Shreddy vs Dr. Glykos dans le même DAO.
     * Migration v43→v44, default `"shreddy"` (back-compat).
     */
    val persona: String = "shreddy",
    /**
     * Note user pour la réponse assistant : null = pas noté, +1 = thumb up,
     * -1 = thumb down. Toujours null pour les messages role="user" (on ne
     * note pas sa propre saisie). Migration v41→v42.
     */
    val userRating: Int? = null,
    /**
     * Latence en ms du tour LLM (envoi requête → dernier token reçu). Toujours
     * null pour les messages role="user". Permet du tracking empirique
     * par-provider sur la perf. Migration v41→v42.
     */
    val latencyMs: Long? = null,
    /**
     * Chemin local vers une image attachee au message user (filesDir/chat_images/
     * UUID.jpg). null = pas d'image. Permet a Shreddy/Dr. Glykos d'accepter
     * une photo en input et au modele VLM resolu de l'analyser. Migration v49→v50.
     */
    val imagePath: String? = null,
)
