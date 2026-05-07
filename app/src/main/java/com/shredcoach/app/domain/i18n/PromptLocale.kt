package com.shredcoach.app.domain.i18n

import java.util.Locale

/**
 * Helper de dispatch multi-locale pour les prompts LLM et templates de coaching.
 *
 * **Convention V2** : 6 locales supportées — FR (canonique), EN, ES, IT, PT (BR), DE.
 * Toute autre locale retombe sur EN (langue véhiculaire) puis FR (canonique source).
 *
 * **Pourquoi pas de string resources pour les prompts** : les prompts LLM peuvent
 * être très longs (1000+ chars), changent souvent au gré du prompt engineering, et
 * ne sont jamais affichés à l'utilisateur. Les garder inline en Kotlin permet :
 *  - Diffs Git lisibles (vs encoding XML qui mange les retours à la ligne)
 *  - Refactor IDE (rename, extract, etc.) sur les sections de prompt
 *  - Templates Kotlin avec interpolation `${...}` directement dans le texte
 *
 * **Cascade fallback pour les variantes optionnelles** : `pick(fr=…, en=…)` reste
 * la signature de base. Si on veut ajouter ES/IT/PT/DE optionnellement,
 * `pickCascade` accepte des nullable et retombe sur EN ou FR si la locale courante
 * n'a pas de variante dédiée. Permet de migrer progressivement les prompts vers
 * des spécialisations par langue sans tout traduire d'un coup.
 *
 * @sample
 *   val prompt = if (PromptLocale.isEn()) ENGLISH_PROMPT else FRENCH_PROMPT
 *   // ou plus concis :
 *   val prompt = PromptLocale.pick(fr = FRENCH_PROMPT, en = ENGLISH_PROMPT)
 *   // V2 — cascade :
 *   val prompt = PromptLocale.pickCascade(
 *       fr = FRENCH_PROMPT, en = ENGLISH_PROMPT,
 *       es = SPANISH_PROMPT  // it/pt/de retomberont sur en puis fr
 *   )
 */
object PromptLocale {

    /** Tag BCP-47 court ("fr" / "en" / "es" / "it" / "pt" / "de") de la locale courante. */
    fun lang(): String = Locale.getDefault().language.lowercase()

    fun isFr(): Boolean = lang() == "fr"
    fun isEn(): Boolean = lang() == "en"
    fun isEs(): Boolean = lang() == "es"
    fun isIt(): Boolean = lang() == "it"
    fun isPt(): Boolean = lang() == "pt"
    fun isDe(): Boolean = lang() == "de"

    /** Sélectionne la variante FR ou EN. Toute autre locale retombe sur EN puis FR. */
    fun <T> pick(fr: T, en: T): T = if (isEn() || isNonFrV2Locale()) en else fr

    /**
     * Cascade explicite par locale, avec fallback EN puis FR. Permet de migrer
     * progressivement les prompts vers des variantes ES/IT/PT/DE dédiées sans
     * tout traduire d'un coup. Locale absente = retour sur EN puis FR.
     *
     * Exemple : `pickCascade(fr=…, en=…, de=DE_VARIANT)` — DE utilise sa variante,
     * ES/IT/PT retombent sur EN.
     */
    fun <T> pickCascade(fr: T, en: T, es: T? = null, it: T? = null, pt: T? = null, de: T? = null): T {
        return when {
            isFr() -> fr
            isEs() -> es ?: en
            isIt() -> it ?: en
            isPt() -> pt ?: en
            isDe() -> de ?: en
            isEn() -> en
            else -> en // langue non supportée → EN véhiculaire
        }
    }

    /**
     * True si la locale courante est ES/IT/PT/DE (= V2 langues qui retombent par
     * défaut sur EN pour les prompts LLM, faute de phrasebook FR direct).
     */
    private fun isNonFrV2Locale(): Boolean = isEs() || isIt() || isPt() || isDe()

    /** Nom de langue à injecter dans un prompt ("français" / "English" / "español" / …). */
    fun languageName(): String = when {
        isFr() -> "français"
        isEn() -> "English"
        isEs() -> "español"
        isIt() -> "italiano"
        isPt() -> "português brasileiro"
        isDe() -> "Deutsch"
        else -> "English"
    }

    /**
     * Directive d'override de langue de sortie, à préfixer à un prompt
     * écrit en français. Quand la locale courante est ≠ FR, on force le LLM
     * à produire ses **champs texte de sortie** (noms de plats, verdict,
     * conseils, etc.) dans la langue cible — sans avoir à dupliquer 200+ lignes
     * de spec FR. Quand la locale est FR, retourne "" (no-op).
     *
     * **Pourquoi ça marche** : les LLMs modernes (Gemini, Groq, Mistral,
     * Claude) suivent fidèlement une instruction de sortie placée en tête
     * de prompt même si le reste du prompt est dans une autre langue.
     * Validé sur Gemini 2.5 Flash, Llama 3 70B, Mistral Large.
     */
    fun outputLanguageDirective(): String {
        if (isFr()) return ""
        val target = languageName()
        return """
═══ OUTPUT LANGUAGE OVERRIDE ═══
The instructions below are written in French, but you MUST produce ALL
user-facing text fields (dish/exercise/machine names, descriptions, tips,
verdicts, allergens, micronutrient names, etc.) in **$target**. JSON
schema keys remain unchanged. Numerical values stay numerical.

""".trimIndent() + "\n\n"
    }
}
