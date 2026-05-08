package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Voice phrase pool — Italian (V1 i18n).
 *
 * Mirrors the French/English structure exactly (same cascade priorities, same
 * generic-vs-named variants). Phrases follow the same constraints :
 *  - ≤ ~7 spoken words
 *  - first name placed without leading comma to avoid TTS robotic pause
 *  - freestyle-safe (no session-position references)
 *  - "tu" informal, gym slang
 */
internal object ItalianVoicePhrases : VoicePhraseProvider {

    override fun phrasePoolFor(ctx: SetStartContext, exo: String, name: String?): List<String> {
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
        add("Riscaldamento. Tranquillo e costante.")
        add("Si scalda. $exo.")
        add("Riscaldiamoci.")
        add("Riscaldamento. Resta fluido.")
        if (name != null) {
            add("$name riscaldamento costante.")
            add("Riscaldamento $name. Resta fluido.")
            add("Riscaldiamoci $name.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. Tieni il ritmo.")
        add("$exo. Respira e tieni.")
        add("Cardio, resta costante.")
        if (name != null) {
            add("$name cardio. Resta costante.")
            add("Cardio $name. Tieni il ritmo.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Ultima serie, ultimo esercizio. Dai tutto!")
        add("Sprint finale. Falla valere.")
        add("Boss finale. Non mollare.")
        if (name != null) {
            add("Ci siamo $name. Non mollare.")
            add("$name sprint finale. Dai tutto!")
            add("Boss finale $name.")
            add("$name finisci forte.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Ultima serie $routineName. Non mollare.")
            if (name != null) add("$name ultima serie $routineName.")
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName fatto. $comp la prossima volta!")
                if (name != null) add("$routineName fatto $name. $comp poi.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Si lavora. $exo.")
        add("Mettiamoci al lavoro.")
        add("Andiamo. $exo.")
        if (name != null) {
            add("$name andiamo. $exo.")
            add("Primo esercizio $name. Forma pulita.")
            add("Andiamo $name.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("$routineName sessione. Andiamo!")
            add("$routineName oggi. Andiamo!")
            if (name != null) {
                add("$name sessione $routineName. Vai.")
                add("$routineName oggi $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Ultima serie $exo. Dai tutto!")
        add("Serie finale. Finisci forte.")
        add("Ancora una, non mollare.")
        add("Chiudendo $exo.")
        if (name != null) {
            add("Ancora una $name. Non mollare.")
            add("Chiudendo $exo $name.")
            add("$name ultima serie. Dai tutto!")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. Prima serie, sistemati.")
        add("Partenza $exo. Forma pulita.")
        add("Andiamo, $exo, serie 1.")
        if (name != null) {
            add("Nuova stazione $name. $exo.")
            add("$name attacchiamo $exo.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Penultima. Resta solido.")
        add("Restano due serie.")
        add("${ctx.currentSet} di ${ctx.totalSets}. Vai a fondo.")
        if (name != null) {
            add("Penultima $name. Resta solido.")
            add("$name restano due serie.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Sprint finale. Non mollare.")
        add("Quasi fatto.")
        add("Serie ${ctx.currentSet}. Vai a fondo.")
        if (name != null) {
            add("Quasi fatto $name.")
            add("$name sprint finale. Non mollare.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("A metà. Resta concentrato.")
        add("Serie ${ctx.currentSet}. Concentrato.")
        if (name != null) {
            add("Tieni duro $name.")
            add("$name a metà. Resta concentrato.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Si riparte.")
        add("Serie ${ctx.currentSet} di ${ctx.totalSets}.")
        add("Vai, ${ctx.currentSet} di ${ctx.totalSets}.")
        add("Andiamo.")
        add("Non mollare.")
        if (name != null) {
            add("Continua così $name.")
            add("$name si riparte.")
        }
    }

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. Partenza forma pulita.")
                add("Nuova stazione. $exo.")
                add("Andiamo, $exo, serie 1.")
                if (name != null) {
                    add("Nuova stazione $name. $exo.")
                    add("$name attacchiamo $exo.")
                }
            }
        }
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Ultima serie $exo. Dai tutto!")
                add("Chiudendo $exo.")
                add("Ancora una, non mollare.")
                if (name != null) {
                    add("Chiudendo $exo $name.")
                    add("$name ultima serie. Dai tutto!")
                }
            }
        }
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Penultima. Resta solido.")
                add("Restano due serie.")
                if (name != null) {
                    add("Penultima $name. Resta solido.")
                }
            }
        }
        return buildList {
            add("Si riparte.")
            if (ctx.totalSets > 0) {
                add("Serie ${ctx.currentSet} di ${ctx.totalSets}.")
                add("Vai, ${ctx.currentSet} di ${ctx.totalSets}.")
            } else {
                add("Serie ${ctx.currentSet}. Continua.")
            }
            add("Andiamo.")
            add("Non mollare.")
            if (name != null) {
                add("Continua così $name.")
                add("$name si riparte.")
            }
        }
    }

    /** Italian connectors (prepositions/articles) — drop trailing ones. */
    private val stopwords = setOf(
        "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
        "di", "del", "della", "dei", "degli", "delle",
        "a", "al", "alla", "ai", "agli", "alle",
        "da", "in", "su", "per", "con",
        "e", "o",
    )

    override val fallbackExerciseName: String = "l\'esercizio"

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

    override val genericPlaceholderName: String = "Campione"
}
