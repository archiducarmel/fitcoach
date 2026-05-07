package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Pools de phrases vocales FR (locale par défaut historique).
 *
 * Voir [WorkoutVoicePhrasebook] pour l'architecture multi-locale et les
 * règles d'or (≤ 7 mots, prénom sans virgule en tête, freestyle-safe).
 *
 * **Pourquoi un objet séparé** : isole le contenu FR du code de dispatch,
 * permet d'ajouter d'autres locales (cf. [EnglishVoicePhrases]) sans
 * cluttering la façade. La signature publique reste stable (les tests
 * unitaires ciblent [WorkoutVoicePhrasebook.simplifyExerciseName] qui
 * délègue ici).
 */
internal object FrenchVoicePhrases : VoicePhraseProvider {

    override fun phrasePoolFor(ctx: SetStartContext, exo: String, name: String?): List<String> {
        // Échauffement : ton détendu
        if (ctx.isWarmup) return warmupPool(exo, name)
        if (ctx.isCardio) return cardioPool(exo, name)

        if (ctx.isFreestyle) return freestylePoolForExo(ctx, exo, name)

        if (ctx.isLastSetOfExercise && ctx.isLastExerciseOfSession) {
            return finalOfSessionPool(name, ctx.routineName, ctx.complementaryRoutineName, ctx.isSplitRoutine)
        }
        if (ctx.isFirstSetOfExercise && ctx.isFirstExerciseOfSession) {
            return startOfSessionPool(exo, name, ctx.routineName, ctx.isSplitRoutine)
        }
        if (ctx.isLastSetOfExercise) return lastSetOfExoPool(exo, name)
        if (ctx.isFirstSetOfExercise) return firstSetOfExoPool(exo, name)
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return secondToLastSetPool(ctx, name)
        }
        if (ctx.sessionProgressPercent >= 75) return sprintFinalPool(ctx, name)
        if (ctx.sessionProgressPercent in 40..60) return midSessionPool(ctx, name)
        return defaultMidSetPool(ctx, name)
    }

    private fun warmupPool(exo: String, name: String?): List<String> = buildList {
        add("Échauffement. On démarre tranquille.")
        add("On chauffe le corps. $exo.")
        add("Allez, on s'échauffe.")
        add("Échauffement, garde-le fluide.")
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
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Dernière du $routineName. Lâche rien.")
            if (name != null) add("$name, dernière du $routineName.")
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

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
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
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Avant-dernière. Reste solide.")
                add("Plus que 2 séries.")
                if (name != null) {
                    add("Avant-dernière $name. Reste solide.")
                }
            }
        }
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

    /** Stopwords français qui jouent le rôle de connecteurs. */
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

    override val fallbackExerciseName: String = "l'exercice"

    override fun simplifyExerciseName(name: String): String {
        if (name.isBlank()) return fallbackExerciseName
        val cleaned = name
            .replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isBlank()) return fallbackExerciseName

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return fallbackExerciseName

        val taken = mutableListOf<String>()
        var significantCount = 0
        for (w in words) {
            val isStop = w.lowercase() in stopwords
            if (isStop && significantCount >= 2) break
            taken += w
            if (!isStop) significantCount++
            if (taken.size >= 3) break
        }
        while (taken.size > 1 && taken.last().lowercase() in stopwords) {
            taken.removeAt(taken.size - 1)
        }
        return taken.joinToString(" ")
    }

    override val genericPlaceholderName: String = "Champion"
}
