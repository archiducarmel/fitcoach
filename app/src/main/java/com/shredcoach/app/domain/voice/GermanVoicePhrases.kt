package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Voice phrase pool — German (V1 i18n).
 *
 * Mirrors the French/English structure exactly (same cascade priorities, same
 * generic-vs-named variants). Phrases follow the same constraints :
 *  - ≤ ~7 spoken words (German tends long, prefer compact verbs)
 *  - first name placed without leading comma to avoid TTS robotic pause
 *  - "du" not "Sie" (always informal in fitness context)
 *  - freestyle-safe (no session-position references)
 */
internal object GermanVoicePhrases : VoicePhraseProvider {

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
        add("Aufwärmen. Locker und ruhig.")
        add("Aufwärmen. $exo.")
        add("Wir wärmen uns auf.")
        add("Aufwärmen. Bleib geschmeidig.")
        if (name != null) {
            add("$name wärmt locker auf.")
            add("Aufwärmen $name. Bleib geschmeidig.")
            add("Wir wärmen uns auf $name.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. Halte den Rhythmus.")
        add("$exo. Atme und halte.")
        add("Cardio, bleib stabil.")
        if (name != null) {
            add("$name, Cardio. Bleib stabil.")
            add("Cardio $name. Halte den Rhythmus.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Letzter Satz, letzte Übung. Gib alles!")
        add("Endspurt. Mach es zählen.")
        add("Endgegner. Nicht aufgeben.")
        if (name != null) {
            add("Da sind wir $name. Nicht aufgeben.")
            add("$name, Endspurt. Gib alles!")
            add("Endgegner $name.")
            add("$name, stark abschließen.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Letzter $routineName Satz. Nicht aufgeben.")
            if (name != null) add("$name, letzter $routineName Satz.")
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName fertig. $comp nächstes Mal!")
                if (name != null) add("$routineName fertig $name. $comp danach.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Ran an die Arbeit. $exo.")
        add("Auf geht\'s.")
        add("Los geht\'s. $exo.")
        if (name != null) {
            add("$name, los geht\'s. $exo.")
            add("Erste Übung $name. Saubere Form.")
            add("Los geht\'s $name.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("$routineName-Einheit. Los!")
            add("$routineName heute. Los!")
            if (name != null) {
                add("$name, $routineName-Einheit. Los.")
                add("$routineName heute $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Letzter Satz $exo. Gib alles!")
        add("Letzter Satz. Stark abschließen.")
        add("Noch einer, nicht aufgeben.")
        add("Schließe $exo.")
        if (name != null) {
            add("Noch einer $name. Nicht aufgeben.")
            add("Schließe $exo $name.")
            add("$name, letzter Satz. Gib alles!")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. Erster Satz, in Position.")
        add("Wir starten $exo. Saubere Form.")
        add("Los geht\'s, $exo, Satz 1.")
        if (name != null) {
            add("Neue Station $name. $exo.")
            add("$name, wir greifen $exo an.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Vorletzter. Bleib stabil.")
        add("Noch zwei Sätze.")
        add("${ctx.currentSet} von ${ctx.totalSets}. Zieh durch.")
        if (name != null) {
            add("Vorletzter $name. Bleib stabil.")
            add("$name, noch zwei Sätze.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Endspurt. Nicht aufgeben.")
        add("Fast geschafft.")
        add("Satz ${ctx.currentSet}. Zieh durch.")
        if (name != null) {
            add("Fast geschafft $name.")
            add("$name, Endspurt. Nicht aufgeben.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Halbzeit. Bleib fokussiert.")
        add("Satz ${ctx.currentSet}. Voll dabei.")
        if (name != null) {
            add("Halt durch $name.")
            add("$name, Halbzeit. Bleib fokussiert.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Los geht\'s wieder.")
        add("Satz ${ctx.currentSet} von ${ctx.totalSets}.")
        add("Los, ${ctx.currentSet} von ${ctx.totalSets}.")
        add("Los geht\'s.")
        add("Bleib dran.")
        if (name != null) {
            add("Mach weiter $name.")
            add("$name, los geht\'s wieder.")
        }
    }

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. Wir starten saubere Form.")
                add("Neue Station. $exo.")
                add("Los geht\'s, $exo, Satz 1.")
                if (name != null) {
                    add("Neue Station $name. $exo.")
                    add("$name, wir greifen $exo an.")
                }
            }
        }
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Letzter Satz $exo. Gib alles!")
                add("Schließe $exo.")
                add("Noch einer, nicht aufgeben.")
                if (name != null) {
                    add("Schließe $exo $name.")
                    add("$name, letzter Satz. Gib alles!")
                }
            }
        }
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Vorletzter. Bleib stabil.")
                add("Noch zwei Sätze.")
                if (name != null) {
                    add("Vorletzter $name. Bleib stabil.")
                }
            }
        }
        return buildList {
            add("Los geht\'s wieder.")
            if (ctx.totalSets > 0) {
                add("Satz ${ctx.currentSet} von ${ctx.totalSets}.")
                add("Los, ${ctx.currentSet} von ${ctx.totalSets}.")
            } else {
                add("Satz ${ctx.currentSet}. Mach weiter.")
            }
            add("Los geht\'s.")
            add("Bleib dran.")
            if (name != null) {
                add("Mach weiter $name.")
                add("$name, los geht\'s wieder.")
            }
        }
    }

    /** German connectors (prepositions/articles) — drop trailing ones. */
    private val stopwords = setOf(
        "der", "die", "das", "den", "dem", "des",
        "ein", "eine", "einer", "einen", "einem", "eines",
        "zu", "zum", "zur",
        "von", "vom", "in", "im", "an", "am", "auf", "für", "mit",
        "und", "oder",
    )

    override val fallbackExerciseName: String = "die Übung"

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
