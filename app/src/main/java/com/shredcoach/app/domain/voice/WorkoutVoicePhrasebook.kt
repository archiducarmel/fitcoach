package com.shredcoach.app.domain.voice

import java.util.Locale

/**
 * Banque de phrases vocales contextuelles pour le coach pendant la séance —
 * façade locale-aware.
 *
 * **Architecture multi-locale** :
 *  - Cet objet expose l'API publique stable (compat tests, callers existants).
 *  - Il dispatche vers un [VoicePhraseProvider] résolu via [Locale.getDefault]
 *    (overlay AppCompatDelegate). FR par défaut, EN si la locale active est `en*`.
 *  - Pour ajouter une langue (Phase 5 V2 : ES/IT/PT/DE), créer un nouveau
 *    `XxxVoicePhrases : VoicePhraseProvider` et étendre [providerForCurrentLocale].
 *
 * **Règle d'or** : la phrase doit rester **courte** (max ~7 mots audibles), car
 * elle se déclenche au moment précis où l'utilisateur s'apprête à exécuter une
 * série. Trop long = il a déjà commencé la série quand le TTS finit.
 *
 * **Cascade de priorités** dans [setStartPhrase] : on choisit le pool le plus
 * spécifique disponible pour le contexte. Plus le contexte est rare/marquant
 * (dernière série du dernier exo, premier exo de la session…), plus on monte
 * dans la cascade. Le rotation index garantit la variété même quand le même
 * contexte revient.
 *
 * **Règle TTS pour le prénom** : le prénom de l'utilisateur n'est JAMAIS précédé
 * d'une virgule (ex: "On y est, Sitou.") car le TTS marque un temps d'arrêt
 * audible sur la virgule, donnant un rendu robotique. À la place :
 *  - **Vocatif en tête** : "Sitou, on y est." → la virgule APRÈS le prénom est
 *    naturelle et la diction enchaîne sur la suite.
 *  - **Apposition en fin SANS virgule** : "On y est Sitou." → TTS lit en un
 *    seul souffle sans pause forcée.
 *
 * **Freestyle** : en mode séance libre, les exos sont ajoutés à la volée donc
 * `totalExercises` et `sessionProgressPercent` ne sont pas fiables. La cascade
 * est adaptée : on n'utilise QUE le contexte de l'exo (1ère/dernière série,
 * milieu) et jamais de phrase qui dépend de la position dans la séance
 * ("dernier exo", "sprint final", "mi-parcours").
 *
 * **Pourquoi un objet pur (sans Compose, sans coroutines)** : testable
 * unitairement, sans état caché, et le TTS le consomme via `voice.speak()`.
 */
object WorkoutVoicePhrasebook {

    /** Snapshot du contexte au moment où une nouvelle série démarre. */
    data class SetStartContext(
        val firstName: String,
        val exerciseName: String,
        val currentSet: Int,            // 1-based
        val totalSets: Int,
        val currentExerciseIndex: Int,  // 0-based
        val totalExercises: Int,
        val isWarmup: Boolean = false,
        val isCardio: Boolean = false,
        /**
         * En séance libre, les exos sont ajoutés un par un par l'utilisateur :
         * `totalExercises` augmente dynamiquement et n'a pas le sens d'un
         * "total prévu". On évite donc toute phrase qui infère une position
         * relative dans la séance ("dernier exo", "75% de la séance", etc.).
         */
        val isFreestyle: Boolean = false,
        /**
         * Nom de la routine de la séance (ex: "Push", "Pull"). Vide ou ignoré
         * si [isSplitRoutine] = false. Utilisé par le pool d'ouverture pour
         * annoncer "On attaque la séance Push" plutôt que générique.
         */
        val routineName: String = "",
        /**
         * Nom de la routine complémentaire à la routine courante (ex: "Pull"
         * quand on fait Push). Null si la routine n'a pas de complément (FB).
         * Utilisé par le pool de fin pour orienter l'user vers la prochaine
         * séance ("Pull demain !").
         */
        val complementaryRoutineName: String? = null,
        /**
         * **Flag canonique** pour décider si on injecte le contexte routine
         * dans les annonces (vs phrases génériques). Mis à `true` pour
         * Push/Pull/Legs/Upper/Lower/Chest+Tri/Back+Bi, `false` pour Full Body
         * (l'historique de l'app — annoncer "Séance Full Body" est redondant).
         *
         * **Pourquoi un flag plutôt qu'un check `routineName == "Full Body"`** :
         * robuste au renommage du displayName, à l'arrivée de routines custom
         * Phase 4, et à la pluralité linguistique ("Full body" vs "Full Body"
         * vs i18n future).
         */
        val isSplitRoutine: Boolean = false,
    ) {
        val isFirstSetOfExercise: Boolean get() = currentSet == 1
        val isLastSetOfExercise: Boolean get() = currentSet >= totalSets && totalSets > 0
        val isFirstExerciseOfSession: Boolean get() = currentExerciseIndex == 0
        val isLastExerciseOfSession: Boolean
            get() = currentExerciseIndex >= totalExercises - 1 && totalExercises > 0
        /** % de progression de la séance par exo (1-based). */
        val sessionProgressPercent: Int
            get() = if (totalExercises <= 0) 0
            else (((currentExerciseIndex + 1).toFloat() / totalExercises) * 100f).toInt()
    }

    /**
     * Phrase courte à prononcer quand une nouvelle série démarre.
     * [rotation] est un compteur monotone côté caller pour garantir la variété
     * (ne pas redémarrer toujours du même index quand le même contexte revient).
     */
    fun setStartPhrase(ctx: SetStartContext, rotation: Int): String {
        val provider = providerForCurrentLocale()
        val rawName = ctx.firstName
        val name = rawName.takeIf {
            it.isNotBlank() && !it.equals(provider.genericPlaceholderName, true)
        }
        val exo = provider.simplifyExerciseName(ctx.exerciseName)
        val pool = provider.phrasePoolFor(ctx, exo, name)
        return pool[rotation.mod(pool.size)]
    }

    /**
     * Simplifie un nom d'exo pour la prononciation TTS.
     *
     * **API publique** : utilise toujours le provider FR (cohérence avec les
     * tests unitaires existants qui ciblent les stopwords français). Pour
     * un comportement locale-aware, [setStartPhrase] délègue déjà au bon
     * provider en interne.
     */
    fun simplifyExerciseName(name: String): String =
        FrenchVoicePhrases.simplifyExerciseName(name)

    /**
     * Sélectionne le provider en fonction de la locale courante de l'app.
     * Locale.getDefault() reflète l'overlay [AppCompatDelegate.setApplicationLocales]
     * appliqué au boot par [LocaleManager], donc le bon provider est résolu
     * automatiquement après un changement de langue.
     *
     * **V2 fallback** : ES/IT/PT/DE n'ont pas (encore) de phrasebook dédié et
     * retombent sur EN (langue véhiculaire) plutôt que FR. Cohérent avec la
     * cascade prompts LLM ([PromptLocale.pickCascade]). Mieux vaut un coach
     * qui parle anglais qu'un coach qui parle français à un user hispanophone.
     */
    private fun providerForCurrentLocale(): VoicePhraseProvider {
        return when (Locale.getDefault().language.lowercase()) {
            "fr" -> FrenchVoicePhrases
            "en", "es", "it", "pt", "de" -> EnglishVoicePhrases
            else -> EnglishVoicePhrases
        }
    }
}

/**
 * Contrat d'un fournisseur de phrases pour une locale donnée.
 * Implémenté par [FrenchVoicePhrases] et [EnglishVoicePhrases].
 */
internal interface VoicePhraseProvider {
    fun phrasePoolFor(
        ctx: WorkoutVoicePhrasebook.SetStartContext,
        exo: String,
        name: String?,
    ): List<String>

    fun simplifyExerciseName(name: String): String

    /** Fallback quand le nom d'exo est vide (« l'exercice », « the exercise »). */
    val fallbackExerciseName: String

    /**
     * Placeholder name affiché par défaut dans l'UI quand l'user n'a pas
     * de prénom (ex: "Champion"). Doit matcher le placeholder utilisé en
     * UI pour qu'on puisse le filtrer (pas de "Allez Champion !").
     */
    val genericPlaceholderName: String
}
