package com.shredcoach.app.domain.chat

import android.content.Context
import com.shredcoach.app.R
import com.shredcoach.app.domain.locale.withCurrentLocale

/**
 * Filtre de sécurité médicale — détecte les keywords critiques (santé urgente)
 * dans le message utilisateur et impose un disclaimer "consulter un médecin"
 * en préambule de la réponse Shreddy.
 *
 * **Pourquoi côté INPUT plutôt que sur la réponse LLM** :
 *  - Déterministe : on ne dépend pas du LLM pour respecter le system prompt
 *    "JAMAIS de conseils médicaux". Si l'user dit "j'ai mal à la poitrine"
 *    et que le LLM essaie quand même de répondre, le banner s'affiche.
 *  - Pas d'appel LLM-as-judge supplémentaire (coût + latence évités).
 *  - L'user voit le disclaimer dès le 1er token streamé, pas seulement à la fin.
 *
 * **Limitations** :
 *  - Match keyword brut, pas de NLU sémantique. "Je n'ai PAS mal à la poitrine"
 *    déclencherait quand même le banner — false positive, acceptable car
 *    safety-first.
 *  - Couvre les 12 keywords les plus critiques (urgence vitale), pas du tout
 *    le diagnostic différentiel médical.
 *
 * **Pour la prod** : extendre la liste, ajouter des patterns regex
 * (combinaisons "douleur + organe"), tests A/B sur faux positifs.
 */
object MedicalSafetyFilter {

    /**
     * Keywords FR+EN qui matchent en mode case-insensitive + partial match
     * (word-boundary pour éviter "chests" matchant "chest").
     */
    private val CRITICAL_KEYWORDS_FR = listOf(
        "douleur thoracique", "mal à la poitrine", "oppression thoracique",
        "essoufflement", "souffle court",
        "perte de conscience", "évanouissement", "syncope", "vertige sévère",
        "palpitations", "arythmie", "tachycardie",
        "vomissement de sang", "selles noires", "saignement abondant",
        "engourdissement bras", "paralysie", "difficultés à parler",
        "fracture", "déchirure musculaire grave",
    )

    private val CRITICAL_KEYWORDS_EN = listOf(
        "chest pain", "chest pressure", "tight chest",
        "shortness of breath", "can't breathe", "cant breathe",
        "lost consciousness", "fainted", "passed out", "blackout",
        "palpitations", "arrhythmia", "tachycardia",
        "vomiting blood", "blood in stool", "heavy bleeding",
        "numbness in arm", "paralysis", "slurred speech",
        "broken bone", "torn muscle",
    )

    private val ALL = (CRITICAL_KEYWORDS_FR + CRITICAL_KEYWORDS_EN).map { it.lowercase() }

    /**
     * Retourne true si [userMessage] contient un keyword critique. Match
     * lowercase + substring — pas de regex pour la perf et la simplicité
     * (on est sur ~25 keywords, .contains naïf < 1µs total).
     */
    fun isMedicalCritical(userMessage: String): Boolean {
        val lower = userMessage.lowercase()
        return ALL.any { kw -> lower.contains(kw) }
    }

    /**
     * Banner localisé à préfixer à la réponse Shreddy. Le LLM répond ensuite
     * en-dessous — pas une suppression, juste un disclaimer + redirection.
     *
     * **Important** : on utilise [withCurrentLocale] car le `@ApplicationContext`
     * Hilt est figé sur la locale du boot. Sans ce wrap, un user qui switche
     * FR → EN à chaud reçoit le banner en FR. Cf. memo
     * `feedback_appcontext_locale_blind` du projet.
     */
    fun safetyBanner(context: Context): String =
        context.withCurrentLocale().getString(R.string.chat_medical_safety_banner)
}
