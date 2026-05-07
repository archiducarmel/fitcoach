package com.shredcoach.app.domain.voice

import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext

/**
 * Voice phrase pool — English (V1 i18n).
 *
 * Mirrors the French structure exactly (same cascade priorities, same
 * generic-vs-named variants). Phrases follow the same constraints :
 *  - ≤ ~7 spoken words
 *  - first name placed without leading comma to avoid TTS robotic pause
 *  - freestyle-safe (no session-position references)
 */
internal object EnglishVoicePhrases : VoicePhraseProvider {

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
        add("Warmup. Easy and steady.")
        add("Heating up. $exo.")
        add("Let's warm up.")
        add("Warmup. Keep it smooth.")
        if (name != null) {
            add("$name, warming up steady.")
            add("Warmup $name. Stay smooth.")
            add("Let's warm up $name.")
        }
    }

    private fun cardioPool(exo: String, name: String?): List<String> = buildList {
        add("Cardio. Keep the rhythm.")
        add("$exo. Breathe and hold.")
        add("Cardio, stay steady.")
        if (name != null) {
            add("$name, cardio. Stay steady.")
            add("Cardio $name. Keep the rhythm.")
        }
    }

    private fun finalOfSessionPool(
        name: String?,
        routineName: String,
        complementaryName: String?,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Last set, last exercise. Give it all.")
        add("Final push. Make it count.")
        add("Final boss. Don't quit.")
        if (name != null) {
            add("Here we are $name. Don't quit.")
            add("$name, final push. Give it all.")
            add("Final boss $name.")
            add("$name, finish strong.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("Last $routineName set. Don't quit.")
            if (name != null) add("$name, last $routineName set.")
            complementaryName?.takeIf { it.isNotBlank() }?.let { comp ->
                add("$routineName done. $comp next time!")
                if (name != null) add("$routineName done $name. $comp next.")
            }
        }
    }

    private fun startOfSessionPool(
        exo: String,
        name: String?,
        routineName: String,
        isSplitRoutine: Boolean,
    ): List<String> = buildList {
        add("Time to grind. $exo.")
        add("Let's get to work.")
        add("Here we go. $exo.")
        if (name != null) {
            add("$name, here we go. $exo.")
            add("First exercise $name. Clean form.")
            add("Let's go $name.")
        }
        if (isSplitRoutine && routineName.isNotBlank()) {
            add("$routineName session. Let's go.")
            add("$routineName today. Here we go.")
            if (name != null) {
                add("$name, $routineName session. Go.")
                add("$routineName today $name.")
            }
        }
    }

    private fun lastSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("Last set $exo. Give it all.")
        add("Final set. Finish strong.")
        add("One more, don't quit.")
        add("Closing $exo.")
        if (name != null) {
            add("One more $name. Don't quit.")
            add("Closing $exo $name.")
            add("$name, last set. Give it all.")
        }
    }

    private fun firstSetOfExoPool(exo: String, name: String?): List<String> = buildList {
        add("$exo. First set, get set up.")
        add("Starting $exo. Clean form.")
        add("Let's go, $exo, set 1.")
        if (name != null) {
            add("New station $name. $exo.")
            add("$name, attacking $exo.")
        }
    }

    private fun secondToLastSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Second to last. Stay solid.")
        add("Two sets left.")
        add("${ctx.currentSet} of ${ctx.totalSets}. Dig in.")
        if (name != null) {
            add("Second to last $name. Stay solid.")
            add("$name, two sets left.")
        }
    }

    private fun sprintFinalPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Final sprint. Don't quit.")
        add("Almost done.")
        add("Set ${ctx.currentSet}. Dig in.")
        if (name != null) {
            add("Almost done $name.")
            add("$name, final sprint. Don't quit.")
        }
    }

    private fun midSessionPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Halfway. Stay focused.")
        add("Set ${ctx.currentSet}. Locked in.")
        if (name != null) {
            add("Holding the line $name.")
            add("$name, halfway. Stay focused.")
        }
    }

    private fun defaultMidSetPool(ctx: SetStartContext, name: String?): List<String> = buildList {
        add("Let's go again.")
        add("Set ${ctx.currentSet} of ${ctx.totalSets}.")
        add("Go, ${ctx.currentSet} of ${ctx.totalSets}.")
        add("Here we go.")
        add("Stay on it.")
        if (name != null) {
            add("Keep it up $name.")
            add("$name, let's go again.")
        }
    }

    private fun freestylePoolForExo(ctx: SetStartContext, exo: String, name: String?): List<String> {
        if (ctx.isFirstSetOfExercise) {
            return buildList {
                add("$exo. Starting clean form.")
                add("New station. $exo.")
                add("Let's go, $exo, set 1.")
                if (name != null) {
                    add("New station $name. $exo.")
                    add("$name, attacking $exo.")
                }
            }
        }
        if (ctx.isLastSetOfExercise) {
            return buildList {
                add("Last set $exo. Give it all.")
                add("Closing $exo.")
                add("One more, don't quit.")
                if (name != null) {
                    add("Closing $exo $name.")
                    add("$name, last set. Give it all.")
                }
            }
        }
        if (ctx.totalSets - ctx.currentSet == 1 && ctx.totalSets >= 3) {
            return buildList {
                add("Second to last. Stay solid.")
                add("Two sets left.")
                if (name != null) {
                    add("Second to last $name. Stay solid.")
                }
            }
        }
        return buildList {
            add("Let's go again.")
            if (ctx.totalSets > 0) {
                add("Set ${ctx.currentSet} of ${ctx.totalSets}.")
                add("Go, ${ctx.currentSet} of ${ctx.totalSets}.")
            } else {
                add("Set ${ctx.currentSet}. Keep going.")
            }
            add("Here we go.")
            add("Stay on it.")
            if (name != null) {
                add("Keep it up $name.")
                add("$name, let's go again.")
            }
        }
    }

    /** English connectors (prepositions/articles) — drop trailing ones. */
    private val stopwords = setOf(
        "a", "an", "the",
        "of", "to", "for",
        "with", "without",
        "on", "in", "at", "by",
        "and", "or",
    )

    override val fallbackExerciseName: String = "the exercise"

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
