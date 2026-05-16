package com.shredcoach.app.domain.chat

/**
 * Personae IA disponibles dans l'app. Chacune a son system prompt, son set
 * d'outils, son avatar et sa palette UI. Les conversations sont persistées
 * séparément (filtre `chat_messages.persona`).
 *
 * **Pourquoi un enum et pas du polymorphisme** : le set de personae est borné
 * (2 aujourd'hui, peut-être 3-4 max). Un enum est lisible, sérialisable
 * (DB tag) et permet le when-exhaustive sur les call sites.
 *
 * **tag** : valeur stockée en DB (colonne `persona`). Stable — ne change
 * JAMAIS, même si le displayName évolue.
 */
enum class ChatPersona(
    val tag: String,
    val displayName: String,
) {
    /**
     * Shreddy — coach généraliste sport + nutrition. Persona historique
     * (avant v44, toutes les conversations étaient implicitement Shreddy).
     */
    SHREDDY(tag = "shreddy", displayName = "Shreddy"),

    /**
     * Dr. Glykos — endocrinologue / diabétologue / nutrition sportive
     * spécialisé sur les données CGM. Persona introduite en v44 pour
     * l'analyse glycémique premium.
     */
    DR_GLYKOS(tag = "dr_glykos", displayName = "Dr. Glykos"),
    ;

    companion object {
        fun fromTag(tag: String?): ChatPersona =
            entries.firstOrNull { it.tag == tag } ?: SHREDDY
    }
}
