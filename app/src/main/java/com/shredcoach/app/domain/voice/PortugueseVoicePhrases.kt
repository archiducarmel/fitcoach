package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Voice phrase pool — Brazilian Portuguese (PT-BR, V1 i18n).
 *
 * Mirrors the French/English structure exactly (same cascade priorities, same
 * generic-vs-named variants). Phrases follow the same constraints :
 *  - ≤ ~7 spoken words
 *  - first name placed without leading comma to avoid TTS robotic pause
 *  - freestyle-safe (no session-position references)
 *  - "você" informal, NEVER European Portuguese
 */
internal object PortugueseVoicePhrases : VoicePhraseProvider {

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
        add("Aquecimento. Calmo e firme.")
        add("Aquecendo. $exo.")
        add("Vamos aquecer.")
        add("Aquecimento. Mantém a suavidade.")
        if (name != null) {
            add("$name aquecendo firme.")
            add("Aquecimento $name. Mantém suave.")
            add("Vamos aquecer $name.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. Mantém o ritmo.")
        add("$exo. Respira e segura.")
        add("Cardio, mantém firme.")
        if (name != null) {
            add("$name cardio. Mantém firme.")
            add("Cardio $name. Mantém o ritmo.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Última série, último exercício. Dá tudo!")
        add("Reta final. Faz valer.")
        add("Boss final. Não desiste.")
        if (name != null) {
            add("Chegamos $name. Não desiste.")
            add("$name reta final. Dá tudo!")
            add("Boss final $name.")
            add("$name termina forte.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Última série $routineName. Não desiste.")
            if (name != null) add("$name última série $routineName.")
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName feito. $comp na próxima!")
                if (name != null) add("$routineName feito $name. $comp depois.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Hora de ralar. $exo.")
        add("Vamos trabalhar.")
        add("Bora! $exo.")
        if (name != null) {
            add("$name bora! $exo.")
            add("Primeiro exercício $name. Forma limpa.")
            add("Bora $name.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("$routineName treino. Bora!")
            add("$routineName hoje. Bora!")
            if (name != null) {
                add("$name $routineName treino. Vai.")
                add("$routineName hoje $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Última série $exo. Dá tudo!")
        add("Série final. Termina forte.")
        add("Mais uma, não desiste.")
        add("Fechando $exo.")
        if (name != null) {
            add("Mais uma $name. Não desiste.")
            add("Fechando $exo $name.")
            add("$name última série. Dá tudo!")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. Primeira série, posiciona-se.")
        add("Começando $exo. Forma limpa.")
        add("Bora, $exo, série 1.")
        if (name != null) {
            add("Nova estação $name. $exo.")
            add("$name atacando $exo.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Penúltima. Mantém firme.")
        add("Faltam duas séries.")
        add("${ctx.currentSet} de ${ctx.totalSets}. A fundo.")
        if (name != null) {
            add("Penúltima $name. Mantém firme.")
            add("$name faltam duas séries.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Sprint final. Não desiste.")
        add("Quase lá.")
        add("Série ${ctx.currentSet}. A fundo.")
        if (name != null) {
            add("Quase lá $name.")
            add("$name sprint final. Não desiste.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Meio caminho. Mantém o foco.")
        add("Série ${ctx.currentSet}. Focado.")
        if (name != null) {
            add("Aguenta firme $name.")
            add("$name meio caminho. Mantém foco.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Bora de novo!")
        add("Série ${ctx.currentSet} de ${ctx.totalSets}.")
        add("Vai, ${ctx.currentSet} de ${ctx.totalSets}.")
        add("Bora!")
        add("Não para.")
        if (name != null) {
            add("Continua assim $name.")
            add("$name bora de novo!")
        }
    }

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. Começando forma limpa.")
                add("Nova estação. $exo.")
                add("Bora, $exo, série 1.")
                if (name != null) {
                    add("Nova estação $name. $exo.")
                    add("$name atacando $exo.")
                }
            }
        }
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Última série $exo. Dá tudo!")
                add("Fechando $exo.")
                add("Mais uma, não desiste.")
                if (name != null) {
                    add("Fechando $exo $name.")
                    add("$name última série. Dá tudo!")
                }
            }
        }
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Penúltima. Mantém firme.")
                add("Faltam duas séries.")
                if (name != null) {
                    add("Penúltima $name. Mantém firme.")
                }
            }
        }
        return buildList {
            add("Bora de novo!")
            if (ctx.totalSets > 0) {
                add("Série ${ctx.currentSet} de ${ctx.totalSets}.")
                add("Vai, ${ctx.currentSet} de ${ctx.totalSets}.")
            } else {
                add("Série ${ctx.currentSet}. Continua.")
            }
            add("Bora!")
            add("Não para.")
            if (name != null) {
                add("Continua assim $name.")
                add("$name bora de novo!")
            }
        }
    }

    /** Portuguese connectors (prepositions/articles) — drop trailing ones. */
    private val stopwords = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas",
        "de", "do", "da", "dos", "das",
        "ao", "à", "aos", "às", "para",
        "com", "sem", "em", "no", "na", "nos", "nas",
        "por", "pelo", "pela",
        "e", "ou",
    )

    override val fallbackExerciseName: String = "o exercício"

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

    override val genericPlaceholderName: String = "Campeão"
}
