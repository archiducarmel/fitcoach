package com.shredcoach.app.domain.chat

import com.shredcoach.app.data.local.entity.ChatMessageEntity

/**
 * Génère un récap extractif des vieux messages d'une conversation pour les
 * conversations longues (>10 messages). Permet au LLM d'avoir un signal sur
 * "ce dont on a parlé avant" sans renvoyer tout l'historique (coût + risque
 * de dépassement de fenêtre contexte).
 *
 * **Pourquoi extractif et pas LLM-as-summarizer** : sur chaque turn on n'a
 * pas envie de payer un appel LLM supplémentaire (latence + coût + risque
 * de boucle). Extractif déterministe = ~1ms, 0 €, raisonnablement utile.
 *
 * **Stratégie** : alterner les rôles pour garder le fil U→S→U→S (le LLM
 * comprend mieux qu'une simple liste de questions). On tronque chaque ligne
 * à [MAX_LINE_CHARS] et on cap à [MAX_ITEMS] échanges, avec un compteur
 * d'omis à la fin pour donner l'ordre de grandeur.
 *
 * **Limitations connues** :
 *  - Pas de NLU : on prend la première ligne brute, qui n'est pas forcément
 *    la plus signifiante (mais en pratique c'est presque toujours le sujet
 *    principal pour Shreddy).
 *  - Pas de dédup sémantique : si 3 messages user disent quasi la même chose,
 *    ils figureront 3 fois.
 *
 * **Pour la prod** : option future = LLM-as-summarizer asynchrone (1x par
 * conversation, cache en DB), invalidation tous les 10 nouveaux turns.
 */
object ChatHistorySummarizer {

    /** Caractères max conservés par message dans le récap. */
    private const val MAX_LINE_CHARS = 100

    /** Nombre max de lignes dans le récap (les plus anciens). */
    private const val MAX_ITEMS = 8

    /**
     * @param olderMessages messages tronqués (ordre chronologique ASC) qui
     *   sortent de la fenêtre récente. Liste vide ou rien à dire → null.
     * @return récap formaté prêt à être prepended au userContext, ou null.
     */
    fun summarize(olderMessages: List<ChatMessageEntity>): String? {
        val cleaned = olderMessages.filter { !it.isError && it.content.isNotBlank() }
        if (cleaned.isEmpty()) return null

        val items = cleaned.take(MAX_ITEMS).map { msg ->
            val role = if (msg.role == "user") "U" else "S"
            val firstLine = msg.content.lineSequence().firstOrNull()?.trim().orEmpty()
            val truncated = if (firstLine.length > MAX_LINE_CHARS)
                firstLine.take(MAX_LINE_CHARS - 1) + "…"
            else firstLine
            "$role: $truncated"
        }
        val omitted = (cleaned.size - MAX_ITEMS).coerceAtLeast(0)

        return buildString {
            appendLine("[RÉCAP CONVERSATION ANTÉRIEURE]")
            items.forEach { appendLine(it) }
            if (omitted > 0) appendLine("(+$omitted échanges antérieurs omis)")
        }.trim()
    }
}
