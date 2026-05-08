package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Voice phrase pool — Spanish (V1 i18n, generic Latin Spanish, "tú" informal).
 *
 * Mirrors the French structure exactly (same cascade priorities, same
 * generic-vs-named variants). Phrases follow the same constraints :
 *  - ≤ ~7 spoken words
 *  - first name placed without leading comma to avoid TTS robotic pause
 *  - freestyle-safe (no session-position references)
 */
internal object SpanishVoicePhrases : VoicePhraseProvider {

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
        add("Calentamiento. Suave y constante.")
        add("Calentando. $exo.")
        add("A calentar.")
        add("Calentamiento. Mantén la suavidad.")
        if (name != null) {
            add("$name, calentando suave.")
            add("Calentamiento $name. Mantén la suavidad.")
            add("A calentar $name.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. Mantén el ritmo.")
        add("$exo. Respira y aguanta.")
        add("Cardio, mantente constante.")
        if (name != null) {
            add("$name, cardio. Mantente constante.")
            add("Cardio $name. Mantén el ritmo.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Última serie, último ejercicio. ¡Dale todo!")
        add("Sprint final. Que valga la pena.")
        add("Boss final. No aflojes.")
        if (name != null) {
            add("Aquí estamos $name. No aflojes.")
            add("$name, sprint final. ¡Dale todo!")
            add("Boss final $name.")
            add("$name, termina fuerte.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Última serie de $routineName. No aflojes.")
            if (name != null) add("$name, última serie de $routineName.")
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName listo. ¡$comp la próxima vez!")
                if (name != null) add("$routineName listo $name. $comp después.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("A trabajar. $exo.")
        add("A trabajar.")
        add("¡Vamos! $exo.")
        if (name != null) {
            add("$name, vamos. $exo.")
            add("Primer ejercicio $name. Técnica limpia.")
            add("Vamos $name.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("$routineName sesión. ¡Vamos!")
            add("$routineName hoy. ¡Vamos!")
            if (name != null) {
                add("$name, sesión $routineName. Vamos.")
                add("$routineName hoy $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Última serie $exo. ¡Dale todo!")
        add("Serie final. Termina fuerte.")
        add("Una más, no aflojes.")
        add("Cerrando $exo.")
        if (name != null) {
            add("Una más $name. No aflojes.")
            add("Cerrando $exo $name.")
            add("$name, última serie. ¡Dale todo!")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. Primera serie, colócate.")
        add("Arrancando $exo. Técnica limpia.")
        add("Vamos, $exo, serie 1.")
        if (name != null) {
            add("Nueva estación $name. $exo.")
            add("$name, atacando $exo.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Penúltima. Mantente firme.")
        add("Quedan dos series.")
        add("${ctx.currentSet} de ${ctx.totalSets}. A fondo.")
        if (name != null) {
            add("Penúltima $name. Mantente firme.")
            add("$name, quedan dos series.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Sprint final. No aflojes.")
        add("Casi terminado.")
        add("Serie ${ctx.currentSet}. A fondo.")
        if (name != null) {
            add("Casi terminado $name.")
            add("$name, sprint final. No aflojes.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("A medio camino. Mantén el foco.")
        add("Serie ${ctx.currentSet}. Concentrado.")
        if (name != null) {
            add("Aguanta $name.")
            add("$name, a medio camino. Mantén el foco.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("¡Vamos de nuevo!")
        add("Serie ${ctx.currentSet} de ${ctx.totalSets}.")
        add("Vamos, ${ctx.currentSet} de ${ctx.totalSets}.")
        add("¡Vamos!")
        add("No pares.")
        if (name != null) {
            add("Sigue así $name.")
            add("$name, vamos de nuevo.")
        }
    }

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. Arrancando con técnica limpia.")
                add("Nueva estación. $exo.")
                add("Vamos, $exo, serie 1.")
                if (name != null) {
                    add("Nueva estación $name. $exo.")
                    add("$name, atacando $exo.")
                }
            }
        }
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Última serie $exo. ¡Dale todo!")
                add("Cerrando $exo.")
                add("Una más, no aflojes.")
                if (name != null) {
                    add("Cerrando $exo $name.")
                    add("$name, última serie. ¡Dale todo!")
                }
            }
        }
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Penúltima. Mantente firme.")
                add("Quedan dos series.")
                if (name != null) {
                    add("Penúltima $name. Mantente firme.")
                }
            }
        }
        return buildList {
            add("¡Vamos de nuevo!")
            if (ctx.totalSets > 0) {
                add("Serie ${ctx.currentSet} de ${ctx.totalSets}.")
                add("Vamos, ${ctx.currentSet} de ${ctx.totalSets}.")
            } else {
                add("Serie ${ctx.currentSet}. Sigue.")
            }
            add("¡Vamos!")
            add("No pares.")
            if (name != null) {
                add("Sigue así $name.")
                add("$name, vamos de nuevo.")
            }
        }
    }

    /** Spanish connectors (prepositions/articles) — drop trailing ones. */
    private val stopwords = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "a", "al", "para",
        "con", "sin",
        "en", "por",
        "y", "o", "u",
    )

    override val fallbackExerciseName: String = "el ejercicio"

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

    override val genericPlaceholderName: String = "Campeón"
}
