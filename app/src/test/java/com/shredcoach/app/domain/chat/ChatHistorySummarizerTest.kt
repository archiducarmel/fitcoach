package com.shredcoach.app.domain.chat

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import org.junit.Test

/**
 * Tests sur le résumé extractif d'historique long.
 */
class ChatHistorySummarizerTest {

    @Test
    fun `liste vide retourne null`() {
        assertThat(ChatHistorySummarizer.summarize(emptyList())).isNull()
    }

    @Test
    fun `que des erreurs ou blancs retourne null`() {
        val list = listOf(
            msg("user", "", isError = false),
            msg("assistant", "   ", isError = false),
            msg("assistant", "boom", isError = true),
        )
        assertThat(ChatHistorySummarizer.summarize(list)).isNull()
    }

    @Test
    fun `prefixe header et alterne role markers`() {
        val list = listOf(
            msg("user", "Combien de protéines en sèche ?"),
            msg("assistant", "Vise 1.8-2g par kg de poids corporel"),
            msg("user", "Et pour les glucides ?"),
        )
        val recap = ChatHistorySummarizer.summarize(list)!!
        assertThat(recap).startsWith("[RÉCAP CONVERSATION ANTÉRIEURE]")
        assertThat(recap).contains("U: Combien de protéines en sèche ?")
        assertThat(recap).contains("S: Vise 1.8-2g")
        assertThat(recap).contains("U: Et pour les glucides ?")
    }

    @Test
    fun `tronque les lignes longues a 100 chars avec ellipse`() {
        val longContent = "A".repeat(150)
        val list = listOf(msg("user", longContent))
        val recap = ChatHistorySummarizer.summarize(list)!!
        val line = recap.lineSequence().first { it.startsWith("U:") }
        // "U: " + 99 chars + "…" = 103 chars total
        assertThat(line.length).isAtMost(103)
        assertThat(line).endsWith("…")
    }

    @Test
    fun `cap a 8 items et signale les omis`() {
        val list = (1..20).map { msg("user", "Question $it") }
        val recap = ChatHistorySummarizer.summarize(list)!!
        // 8 items inclus
        assertThat(recap).contains("U: Question 1")
        assertThat(recap).contains("U: Question 8")
        // 9+ omis
        assertThat(recap).doesNotContain("U: Question 9")
        assertThat(recap).contains("+12 échanges antérieurs omis")
    }

    @Test
    fun `prend uniquement la premiere ligne du content multiligne`() {
        val list = listOf(msg("assistant", "Ligne 1 punch\n\nLigne 2 détail\nLigne 3"))
        val recap = ChatHistorySummarizer.summarize(list)!!
        assertThat(recap).contains("S: Ligne 1 punch")
        assertThat(recap).doesNotContain("Ligne 2")
        assertThat(recap).doesNotContain("Ligne 3")
    }

    @Test
    fun `ignore les messages errors meme valides en contenu`() {
        val list = listOf(
            msg("assistant", "Réponse OK", isError = false),
            msg("assistant", "Erreur réseau", isError = true),
        )
        val recap = ChatHistorySummarizer.summarize(list)!!
        assertThat(recap).contains("Réponse OK")
        assertThat(recap).doesNotContain("Erreur réseau")
    }

    private fun msg(role: String, content: String, isError: Boolean = false) =
        ChatMessageEntity(conversationId = "c1", role = role, content = content, isError = isError)
}
