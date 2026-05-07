package com.shredcoach.app.domain.voice

/**
 * Banque de phrases vocales contextuelles pour le coach pendant la séance.
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
        val name = ctx.firstName.takeIf { it.isNotBlank() && !it.equals("Champion", true) }
        val exo = simplifyExerciseName(ctx.exerciseName)
        val pool = phrasePoolFor(ctx, exo, name)
        return pool[rotation.mod(pool.size)]
    }

    /** Choix du pool selon la cascade de priorités. */
    private fun phrasePoolFor(ctx: SetStartContext, exo: String, name: String?): List<String> {
        // Échauffement : ton détendu, pas d'urgence
        if (ctx.isWarmup) return warmupPool(exo, name)
        // Cardio : ton motivant régulier
        if (ctx.isCardio) return cardioPool(exo, name)

        // **Freestyle** : on saute toutes les branches qui dépendent de la
        // position dans la séance (last exo, sprint final, mi-parcours). On
        // garde seulement les contextes liés à l'exo lui-même.
        if (ctx.isFreestyle) return freestylePoolForExo(ctx, exo, name)

        // PRIORITÉ MAX : dernière série du DERNIER exercice — péroraison
        if (ctx.isLastSetOfExercise && ctx.isLastExerciseOfSession) {
            return finalOfSessionPool(
                name = name,
                routineName = ctx.routineName,
                complementaryName = ctx.complementaryRoutineName,
                isSplitRoutine = ctx.isSplitRoutine,
            )
        }
        // Coup d'envoi : 1ère série du 1er exercice
        if (ctx.isFirstSetOfExercise && ctx.isFirstExerciseOfSession) {
            return startOfSessionPool(
                exo = exo,
                name = name,
                routineName = ctx.routineName,
                isSplitRoutine = ctx.isSplitRoutine,
            )
        }
        // Dernière série de l'exo courant (pas dernier exo)
        if (ctx.isLastSetOfExercise) return lastSetOfExoPool(exo, name)
        // Première série d'un exo (pas le 1er de la séance)
        if (ctx.isFirstSetOfExercise) return firstSetOfExoPool(exo, name)
        // Avant-dernière série (et exo qui a au moins 3 séries)
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return secondToLastSetPool(ctx, name)
        }
        // Sprint final (≥75% séance)
        if (ctx.sessionProgressPercent >= 75) return sprintFinalPool(ctx, name)
        // Mi-parcours (40-60%)
        if (ctx.sessionProgressPercent in 40..60) return midSessionPool(ctx, name)
        // Fallback : milieu de série standard
        return defaultMidSetPool(ctx, name)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pools spécifiques (chaque pool offre des variantes avec/sans prénom)
    // ──────────────────────────────────────────────────────────────────────

    private fun warmupPool(exo: String, name: String?): List<String> = buildList {
        // Sans prénom (toujours dispo)
        add("Échauffement. On démarre tranquille.")
        add("On chauffe le corps. $exo.")
        add("Allez, on s'échauffe.")
        add("Échauffement, garde-le fluide.")
        // Avec prénom : vocatif en tête OU apposition en fin sans virgule
        if (name != null) {
            add("$name, on chauffe tranquille.")
            add("Échauffement $name. On garde fluide.")
            add("Allez $name, on s'échauffe.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. On garde le rythme.")
        add("$exo. On souffle, on tient.")
        add("Cardio, reste régulier.")
        if (name != null) {
            add("$name, cardio. Reste régulier.")
            add("Cardio $name. On garde le rythme.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Dernière série, dernier exo. Tout donner.")
        add("Ultime effort. Fais-toi plaisir.")
        add("Le boss final. Lâche rien.")
        if (name != null) {
            add("On y est $name. Lâche rien.")
            add("$name, ultime effort. Tout donner.")
            add("Le boss final $name.")
            add("$name, on finit en beauté.")
        }
        // Phrases routine-aware : on annonce la routine actuelle en passant.
        // Décidé par flag explicite (cf. doc SetStartContext.isSplitRoutine).
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Dernière du $routineName. Lâche rien.")
            if (name != null) add("$name, dernière du $routineName.")
            // Suggestion routine complémentaire — donne du sens à la péroraison.
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName bouclé. $comp à la prochaine !")
                if (name != null) add("$routineName fini $name. $comp à la prochaine.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("On rentre dans le dur. $exo.")
        add("Allez, on s'y met.")
        add("C'est parti. On attaque $exo.")
        if (name != null) {
            add("$name, c'est parti. On attaque $exo.")
            add("Premier exo $name. On chauffe propre.")
            add("Allez $name, on s'y met.")
        }
        // Routine-aware : "On attaque la séance Push" — contexte audible direct.
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Séance $routineName. On attaque.")
            add("$routineName aujourd'hui. C'est parti.")
            if (name != null) {
                add("$name, séance $routineName. On y va.")
                add("$routineName aujourd'hui $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Dernière série $exo. Donne tout.")
        add("Ultime série. On finit fort.")
        add("Plus qu'une, lâche rien.")
        add("On boucle $exo.")
        if (name != null) {
            add("Plus qu'une $name. Lâche rien.")
            add("On boucle $exo $name.")
            add("$name, dernière série. Donne tout.")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. Première série, on s'installe.")
        add("On démarre $exo. Forme propre.")
        add("Allez, $exo, série 1.")
        if (name != null) {
            add("Nouvelle station $name. $exo.")
            add("$name, on attaque $exo.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Avant-dernière. Reste solide.")
        add("Plus que 2 séries.")
        add("${ctx.currentSet} sur ${ctx.totalSets}. On creuse.")
        if (name != null) {
            add("Avant-dernière $name. Reste solide.")
            add("$name, plus que 2 séries.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Sprint final. Tu lâches rien.")
        add("On voit la fin.")
        add("Série ${ctx.currentSet}. On creuse.")
        if (name != null) {
            add("On voit la fin $name.")
            add("$name, sprint final. Lâche rien.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Mi-parcours. Garde le cap.")
        add("Série ${ctx.currentSet}. Concentré.")
        if (name != null) {
            add("On tient le cap $name.")
            add("$name, mi-parcours. Garde le cap.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Allez, on y retourne.")
        add("Série ${ctx.currentSet} sur ${ctx.totalSets}.")
        add("Go, ${ctx.currentSet} sur ${ctx.totalSets}.")
        add("C'est reparti.")
        add("On garde le cap.")
        if (name != null) {
            add("On enchaîne $name.")
            add("$name, on y retourne.")
        }
    }

    /**
     * Pool dédié au mode freestyle. On reste centré sur l'exo lui-même
     * (1ère/dernière série, milieu) sans jamais évoquer la position dans la
     * séance — l'utilisateur peut ajouter un autre exo derrière à tout moment,
     * donc une phrase comme "dernier exo" serait fausse 30 secondes plus tard.
     */
    private fun freestylePoolForExo(
        ctx: SetStartContext,
        exo: String,
        name: String?,
    ): List<String> {
        // 1ère série de l'exo : démarrage de cette station
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. On démarre, forme propre.")
                add("Nouvelle station. $exo.")
                add("Allez, $exo, série 1.")
                if (name != null) {
                    add("Nouvelle station $name. $exo.")
                    add("$name, on attaque $exo.")
                }
            }
        }
        // Dernière série de l'exo : on boucle sans dire "dernier exo session"
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Dernière série $exo. Donne tout.")
                add("On boucle $exo.")
                add("Plus qu'une, lâche rien.")
                if (name != null) {
                    add("On boucle $exo $name.")
                    add("$name, dernière série. Tout donner.")
                }
            }
        }
        // Avant-dernière série (si totalSets ≥ 3)
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Avant-dernière. Reste solide.")
                add("Plus que 2 séries.")
                if (name != null) {
                    add("Avant-dernière $name. Reste solide.")
                }
            }
        }
        // Milieu de série standard
        return buildList {
            add("Allez, on y retourne.")
            if (ctx.totalSets > 0) {
                add("Série ${ctx.currentSet} sur ${ctx.totalSets}.")
                add("Go, ${ctx.currentSet} sur ${ctx.totalSets}.")
            } else {
                add("Série ${ctx.currentSet}. On enchaîne.")
            }
            add("C'est reparti.")
            add("On garde le cap.")
            if (name != null) {
                add("On enchaîne $name.")
                add("$name, on y retourne.")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper : raccourcissement intelligent du nom d'exercice pour TTS
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Stopwords français qui jouent le rôle de connecteurs introduisant un
     * complément (« en supination », « à la barre », « avec haltères »…).
     * On coupe AVANT eux quand on a déjà ≥ 2 mots significatifs, et on les
     * garde sinon (ex: "Soulevé de terre" — le « de » est structurel).
     */
    private val stopwords = setOf(
        "à", "au", "aux",
        "de", "des", "du",
        "en", "et",
        "la", "le", "les",
        "un", "une",
        "avec", "sans",
        "sur", "sous",
        "pour", "par",
        "dans",
    )

    /**
     * Simplifie le nom d'exo pour la prononciation TTS — garde 2-3 mots
     * significatifs en évitant de finir sur un connecteur.
     *
     * Exemples :
     *  - "Tirage vertical en supination" → "Tirage vertical" (coupe avant
     *    « en » car on a déjà 2 mots significatifs)
     *  - "Curl biceps à la barre EZ" → "Curl biceps"
     *  - "Soulevé de terre" → "Soulevé de terre" (« de » conservé car < 2 mots
     *    significatifs avant lui)
     *  - "Soulevé de terre roumain" → "Soulevé de terre" (max 3 mots)
     *  - "Développé couché barre" → "Développé couché barre"
     *  - "Squat (haltères)" → "Squat" (parenthèses retirées avant split)
     */
    internal fun simplifyExerciseName(name: String): String {
        if (name.isBlank()) return "l'exercice"
        // Pré-nettoyage : retire le contenu entre parenthèses (« Squat (haltères) »
        // → « Squat »), normalise les espaces multiples.
        val cleaned = name
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return "l'exercice"

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return "l'exercice"

        // Compte des mots significatifs accumulés (non-stopword) et bornage à 3 mots total.
        val taken = mutableListOf<String>()
        var significantCount = 0
        for (w in words) {
            val isStop = w.lowercase() in stopwords
            if (isStop && significantCount >= 2) break // coupe avant le complément
            taken += w
            if (!isStop) significantCount++
            if (taken.size >= 3) break
        }
        // Trim trailing stopwords (sécurité, ex: "X de" résiduel après bornage)
        while (taken.size > 1 && taken.last().lowercase() in stopwords) {
            taken.removeAt(taken.size - 1)
        }
        return taken.joinToString(" ")
    }
}
