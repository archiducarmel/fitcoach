package com.shredcoach.app.domain.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage du routage tool-aware vs streaming.
 *
 * Le classifier doit être conservateur : en cas de doute, retourner false
 * (privilégier la latence faible). Les vrais positifs DOIVENT inclure les
 * actions de logging (log_meal, set_weight) et les queries temps réel
 * (get_today_stats).
 */
class ChatIntentClassifierTest {

    // ─── Vrais positifs : tool path doit s'activer ────────────────

    @Test fun `j'ai mangé déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("J'ai mangé du poulet et du riz"))
    }

    @Test fun `je pèse déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("Je pèse 79.4 ce matin"))
    }

    @Test fun `log brut déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("Log ce shake : 30g whey + banane"))
    }

    @Test fun `ajoute déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("Ajoute mon repas du midi"))
    }

    @Test fun `où j'en suis déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("Où j'en suis sur mes macros ?"))
    }

    @Test fun `EN i ate déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("I ate 200g of chicken for lunch"))
    }

    @Test fun `EN i weigh déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("I weigh 78 kg today"))
    }

    @Test fun `EN track déclenche tools`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("track this meal please"))
    }

    @Test fun `casse mixte indifférente`() {
        assertTrue(ChatIntentClassifier.shouldUseTools("LOG mon poids"))
        assertTrue(ChatIntentClassifier.shouldUseTools("J'ai MANGÉ trop ce midi"))
    }

    // ─── Vrais négatifs : streaming rapide ────────────────────────

    @Test fun `question conseil pure pas de tools`() {
        assertFalse(ChatIntentClassifier.shouldUseTools("Donne-moi un conseil pour mes ischios"))
    }

    @Test fun `question forme pure pas de tools`() {
        assertFalse(ChatIntentClassifier.shouldUseTools("Comment bien faire un développé couché ?"))
    }

    @Test fun `salutation pas de tools`() {
        assertFalse(ChatIntentClassifier.shouldUseTools("Salut Shreddy ça va ?"))
    }

    @Test fun `vide pas de tools`() {
        assertFalse(ChatIntentClassifier.shouldUseTools(""))
        assertFalse(ChatIntentClassifier.shouldUseTools("   "))
    }

    @Test fun `EN motivation pure pas de tools`() {
        assertFalse(ChatIntentClassifier.shouldUseTools("I need some motivation to keep pushing"))
    }

    // ─── Faux positif à NE PAS produire (word boundary) ───────────

    @Test fun `logique ne déclenche pas tools`() {
        // Le mot "log" est inclus dans "logique" — le split par \W+ doit éviter
        // ce faux positif. Critique car "logique" est ultra courant en FR.
        assertFalse(ChatIntentClassifier.shouldUseTools("Quelle est la logique derrière la sèche progressive ?"))
    }

    @Test fun `addition ne déclenche pas tools`() {
        // "add" est dans "addition" mais doit être un mot isolé.
        assertFalse(ChatIntentClassifier.shouldUseTools("Une addition de protéines suffit-elle ?"))
    }

    @Test fun `note de musique pas déclenche par hasard`() {
        // "note" est ambigu — actuellement il déclenche les tools (intention
        // logging probable : "note mon poids"). On documente le comportement.
        assertTrue(ChatIntentClassifier.shouldUseTools("Note que je m'entraîne le lundi"))
    }
}
