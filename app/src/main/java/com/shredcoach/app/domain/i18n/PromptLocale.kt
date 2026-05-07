package com.shredcoach.app.domain.i18n

import java.util.Locale

/**
 * Helper de dispatch FR/EN pour les prompts LLM.
 *
 * Convention : la locale courante est lue via [Locale.getDefault] (overlay
 * AppCompatDelegate) à chaque construction de prompt, **pas mémoïsée** —
 * l'utilisateur peut changer la langue depuis Settings et le prochain
 * prompt LLM construit doit refléter le nouveau choix immédiatement.
 *
 * Convention V1 : seuls FR + EN sont supportés ; toute autre locale
 * retombe sur FR (langue par défaut historique de l'app).
 *
 * **Pourquoi pas de string resources** : les prompts LLM peuvent être très
 * longs (1000+ chars), changer souvent au gré de l'optimisation prompt
 * engineering, et ne sont jamais affichés à l'utilisateur. Les garder
 * inline en Kotlin permet :
 *  - Diffs Git lisibles (vs encoding XML qui mange les retours à la ligne)
 *  - Refactor IDE (rename, extract, etc.) sur les sections de prompt
 *  - Templates Kotlin avec interpolation `${...}` directement dans le texte
 *
 * @sample
 *   val prompt = if (PromptLocale.isEn()) ENGLISH_PROMPT else FRENCH_PROMPT
 *   // ou plus concis :
 *   val prompt = PromptLocale.pick(fr = FRENCH_PROMPT, en = ENGLISH_PROMPT)
 */
object PromptLocale {

    /** Tag BCP-47 court ("fr" / "en") de la locale courante. */
    fun lang(): String = Locale.getDefault().language.lowercase()

    fun isEn(): Boolean = lang() == "en"

    /** Sélectionne la variante FR ou EN. Toute locale ≠ "en" retombe sur FR. */
    fun <T> pick(fr: T, en: T): T = if (isEn()) en else fr

    /** Nom de langue à injecter dans un prompt ("français" / "English"). */
    fun languageName(): String = pick(fr = "français", en = "English")

    /**
     * Directive d'override de langue de sortie, à préfixer à un prompt
     * écrit en français. Quand la locale courante est EN, on force le LLM
     * à produire ses **champs texte de sortie** (noms de plats, verdict,
     * conseils, etc.) en anglais — sans avoir à dupliquer 200+ lignes de
     * spec FR. Quand la locale est FR, retourne "" (no-op).
     *
     * **Pourquoi ça marche** : les LLMs modernes (Gemini, Groq, Mistral,
     * Claude) suivent fidèlement une instruction de sortie placée en tête
     * de prompt même si le reste du prompt est dans une autre langue.
     * Validé sur Gemini 2.5 Flash, Llama 3 70B, Mistral Large.
     */
    fun outputLanguageDirective(): String = if (isEn()) """
═══ OUTPUT LANGUAGE OVERRIDE ═══
The instructions below are written in French, but you MUST produce ALL
user-facing text fields (dish/exercise/machine names, descriptions, tips,
verdicts, allergens, micronutrient names, etc.) in **English**. JSON
schema keys remain unchanged. Numerical values stay numerical.

""".trimIndent() + "\n\n" else ""
}
