package com.shredcoach.app.domain.chat

/**
 * Classifie l'intent d'un message utilisateur pour décider du pipeline LLM
 * à utiliser : streaming rapide (SSE) ou tool-aware non-streaming.
 *
 * **Pourquoi cette dichotomie** : le tool calling oblige un round-trip
 * complet (LLM → tools → LLM → texte) avant de pouvoir afficher quoi que
 * ce soit. Sur les questions de coaching pur ("donne-moi un conseil pour
 * mes ischios"), l'utilisateur n'a pas besoin de tools — utiliser le tool
 * path ferait perdre ~2-5s de latence perçue pour rien.
 *
 * **Stratégie** : on n'active le tool path QUE si le message contient
 * clairement un verbe d'action (logging, mesure, query sur stats du jour).
 * Le reste reste sur du SSE streaming classique (UX premium).
 *
 * **Conséquence** : le bot ne pourra pas appeler les tools sur une question
 * coaching générique. Mais P1b ajoute déjà le snapshot frais dans le
 * system prompt → le bot a ces données dès turn 1 même sans `get_today_stats`.
 * Acceptable.
 *
 * **Limitation** : détection par keywords, pas par NLU. Faux négatifs
 * possibles ("j'aimerais bien noter mon poids" — pas de "log" / "ajoute").
 * Pour V1 acceptable, à raffiner avec un mini-classifier local plus tard.
 */
object ChatIntentClassifier {

    /**
     * Patterns FR + EN qui signalent une action (write OU query temps réel
     * sur "aujourd'hui").
     *
     * **Conception** : on regarde des MOTS-clés isolés (boundary word) pour
     * éviter les faux matches ("logique" ne matchera pas "log"). Mais les
     * expressions multi-mots (`j'ai mangé`, `i ate`) sont testées en substring
     * car elles sont déjà spécifiques.
     */
    private val ACTION_WORDS = setOf(
        // FR — actions logging
        "log", "loggue", "logger", "logué", "loggué",
        "ajoute", "ajouter", "enregistre", "enregistrer", "note", "noter",
        // EN — actions logging
        "track", "add", "record",
    )

    private val ACTION_PHRASES = listOf(
        // FR
        "j'ai mangé", "j'ai pris", "j'ai bu", "je pèse", "mon poids",
        "où j'en suis", "où en suis-je", "combien il me reste",
        "qu'est-ce que j'ai mangé", "ce que j'ai mangé",
        "ma séance d'aujourd'hui", "ma séance du jour",
        // EN
        "i ate", "i had", "i drank", "i weigh", "my weight",
        "where am i at", "how much left", "how many left",
        "what did i eat", "today's workout", "todays workout",
    )

    /**
     * Retourne true si [message] contient un signal d'action → utiliser le
     * tool-aware path. Sinon, streaming rapide.
     *
     * Heuristique conservatrice : en cas de doute, on N'active PAS les tools
     * (privilégier la latence faible). Le bot peut toujours suggérer
     * "veux-tu que je logue ce repas ?" et l'user re-prompt.
     */
    fun shouldUseTools(message: String): Boolean {
        val lower = message.lowercase().trim()
        if (lower.isEmpty()) return false

        // Phrases multi-mots : substring match
        for (phrase in ACTION_PHRASES) {
            if (lower.contains(phrase)) return true
        }

        // Mots isolés : split + lookup pour éviter "logique" → "log"
        val words = lower.split(Regex("\\W+")).filter { it.isNotEmpty() }
        return words.any { it in ACTION_WORDS }
    }
}
