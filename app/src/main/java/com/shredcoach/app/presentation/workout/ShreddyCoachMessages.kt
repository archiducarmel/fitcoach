package com.shredcoach.app.presentation.workout

/**
 * Messages de coaching contextuels de Shreddy.
 * Mode LLM : appel API pour messages générés par IA.
 * Mode fallback : templates locaux si pas d'API key ou erreur réseau.
 */
object ShreddyCoachMessages {

    const val COACH_SYSTEM_PROMPT = """Tu es Shreddy, coach sportif dans l'app ShredCoach. Français uniquement.

MISSION : UNE SEULE phrase de coaching (15 mots max idéalement, 25 mots max absolu).

Tu DOIS alterner entre ces 3 modes à chaque message. Le mode est indiqué dans le prompt.

MODE "TECHNIQUE" :
- Analyse un aspect technique de l'exercice (ROM, tempo, grip, respiration, posture)
- Donne un micro-conseil actionnable lié à l'exercice spécifique
- Ex: "Pense à verrouiller tes omoplates sur le prochain exo, ça change tout."

MODE "HUMOUR" :
- Jeu de mots, métaphore muscu, référence sport/pop culture, ironie bienveillante
- Ex: "Tes pecs envoient un SMS à ton t-shirt : 'On est à l'étroit ici.'"
- Ex: "Ce volume-là, même ta balance le respecte."

MODE "ENCOURAGEMENT" :
- Basé sur les CHIFFRES réels (volume, reps, progression)
- Pas générique : intégrer une donnée concrète de la séance
- Ex: "2.8 tonnes soulevées, ton corps n'a pas le choix de progresser."

INTERDICTIONS ABSOLUES :
- Plus de 1 phrase
- Commencer par le nom de l'exercice
- Franglais (skip, PR, push, let's go)
- Emoji
- "Bien joué" ou "Continue comme ça" seuls
- Markdown

Réponds UNIQUEMENT le message, rien d'autre."""

    fun buildExercisePrompt(
        firstName: String, exerciseName: String, sets: Int, reps: Int,
        volume: Double, skipped: Int, duration: Long,
        exercisesDone: Int, totalExercises: Int,
        isPersonalRecord: Boolean, goalName: String
    ): String {
        val remaining = totalExercises - exercisesDone
        val goal = when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }
        val volStr = if (volume >= 1000) "%.1ft".format(volume / 1000) else "%.0fkg".format(volume)
        val mode = nextCoachMode()
        return buildString {
            appendLine("MODE : $mode")
            appendLine("User : $firstName | Objectif : $goal")
            appendLine("Exercice : $exerciseName — $sets séries, $reps reps, $volStr, ${duration/60}min")
            if (isPersonalRecord) appendLine("RECORD PERSONNEL battu !")
            if (skipped > 0) appendLine("$skipped série(s) sautée(s)")
            if (sets == 0) appendLine("Exercice entièrement passé")
            appendLine("Séance : $exercisesDone/$totalExercises (reste $remaining)")
            appendLine("Génère UNE phrase en mode $mode.")
        }
    }

    fun buildSessionPrompt(
        firstName: String, totalSets: Int, totalReps: Int, totalVolume: Double,
        durationMinutes: Long, exercisesCompleted: Int, exercisesSkipped: Int,
        streak: Int, goalName: String
    ): String {
        val goal = when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }
        val volStr = if (totalVolume >= 1000) "%.1ft".format(totalVolume / 1000) else "%.0fkg".format(totalVolume)
        val total = exercisesCompleted + exercisesSkipped
        return buildString {
            appendLine("MODE : ENCOURAGEMENT (fin de séance, célébrer l'effort)")
            appendLine("User : $firstName | Objectif : $goal | Streak : $streak jours")
            appendLine("Séance : $exercisesCompleted/$total exos, $totalSets séries, $totalReps reps, $volStr, ${durationMinutes}min")
            if (exercisesSkipped > 0) appendLine("$exercisesSkipped exercice(s) sauté(s)")
            if (exercisesSkipped > exercisesCompleted) appendLine("Séance très difficile")
            if (streak >= 7) appendLine("Série de $streak jours !")
            appendLine("Génère UNE phrase de félicitation avec un chiffre concret.")
        }
    }

    private var lastExerciseMessageIndex = -1
    private var lastSessionMessageIndex = -1

    private fun pickUnique(options: List<String>, lastIndex: IntArray): String {
        var idx: Int
        do { idx = (0 until options.size).random() } while (idx == lastIndex[0] && options.size > 1)
        lastIndex[0] = idx
        return options[idx]
    }

    private val exerciseLastIdx = intArrayOf(-1)
    private val sessionLastIdx = intArrayOf(-1)
    private var coachModeCounter = 0
    private val MODES = listOf("TECHNIQUE", "HUMOUR", "ENCOURAGEMENT")
    /** Retourne le mode suivant dans la rotation. */
    fun nextCoachMode(): String {
        val mode = MODES[coachModeCounter % MODES.size]
        coachModeCounter++
        return mode
    }

    // ═══════════════════════════════════════
    // TRANSITION EXERCICE
    // ═══════════════════════════════════════

    fun exerciseTransition(
        firstName: String,
        exerciseName: String,
        sets: Int,
        reps: Int,
        volume: Double,
        skipped: Int,
        duration: Long,
        exercisesDone: Int,
        totalExercises: Int,
        isPersonalRecord: Boolean = false,
        goalName: String = "SHRED"
    ): String {
        val remaining = totalExercises - exercisesDone
        val name = firstName.ifBlank { "Champion" }
        val volStr = when {
            volume >= 1000 -> "%.1f tonnes".format(volume / 1000)
            volume > 0 -> "%.0f kg".format(volume)
            else -> ""
        }
        val durMin = duration / 60

        val messages = mutableListOf<String>()

        // ── Exercice entièrement passé ──
        if (sets == 0 && skipped == 0) return pickUnique(listOf(
            "Pas d'inquiétude, on garde le rythme sur la suite.",
            "Parfois il faut savoir choisir ses batailles. Allez, on enchaîne.",
            "L'essentiel c'est d'être là $name. La suite t'attend."
        ), exerciseLastIdx)

        // ── Record personnel ──
        if (isPersonalRecord) return pickUnique(listOf(
            "Nouveau record personnel ! La barre monte, et toi avec $name.",
            "Record battu ! Tu progresses vraiment, et les chiffres le prouvent.",
            "La progression est réelle, pas juste un ressenti. Nouveau record. Bravo $name !",
            "Quand je regarde tes chiffres, je vois quelqu'un qui repousse ses limites. Record."
        ), exerciseLastIdx)

        // ── Séries passées ──
        if (skipped > 0) {
            messages.addAll(listOf(
                "Tu as écouté ton corps, c'est intelligent. Pas de blessure, pas de regret.",
                "$name, sauter une série c'est pas abandonner, c'est être malin.",
                "L'important c'est pas la perfection, c'est la régularité. Et tu es là."
            ))
            return pickUnique(messages, exerciseLastIdx)
        }

        // ── Volume costaud (>2000kg) ──
        if (volume > 2000) messages.addAll(listOf(
            "$volStr soulevés. Tes muscles te remercieront demain… ou pas 😄",
            "Gros volume sur cet exercice. C'est exactement ce qu'il faut $name.",
            "$volStr en $sets séries. Si c'est pas du travail sérieux, je sais pas ce que c'est."
        ))

        // ── Fin de séance proche ──
        if (remaining == 1) messages.addAll(listOf(
            "Dernier exercice $name ! Garde l'énergie, termine en beauté.",
            "Il en reste un seul. Tu vas pas flancher maintenant quand même ?",
            "Plus qu'un exercice et c'est bouclé. Tout donner sur celui-là !"
        ))
        else if (remaining == 2) messages.addAll(listOf(
            "Encore deux exercices et tu auras tout déchiré $name.",
            "La ligne d'arrivée se dessine. Deux exercices, tu gères.",
            "On voit le bout ! Allez $name, deux efforts et c'est plié."
        ))

        // ── Messages par objectif (si rien de spécial) ──
        if (messages.isEmpty()) {
            when (goalName) {
                "SHRED" -> messages.addAll(listOf(
                    "Chaque série bien exécutée te rapproche de tes abdos $name.",
                    "$sets séries propres. En sèche, c'est la qualité qui compte.",
                    "Le physique se dessine séance après séance. Continue comme ça.",
                    "Tu sculptes, $name. Patience et régularité, les résultats arrivent.",
                    "Belle intensité. C'est ce genre de séance qui fait la différence en sèche."
                ))
                "BULK" -> messages.addAll(listOf(
                    "$volStr de volume. C'est comme ça qu'on construit du muscle $name.",
                    "Le volume est au rendez-vous, les gains suivront. Patience.",
                    "Séries solides. Ton corps va utiliser chaque gramme de protéines ce soir.",
                    "$name, avec ce volume, tes muscles n'ont pas le choix : ils poussent.",
                    "Beau travail. N'oublie pas de bien manger derrière !"
                ))
                else -> messages.addAll(listOf(
                    "$sets séries bouclées, on enchaîne $name !",
                    "Propre et efficace. Allez, la suite.",
                    "Bon rythme $name, continue sur cette lancée.",
                    "C'est régulier et c'est bien. On garde le cap.",
                    "Exercice validé. Tu assures $name."
                ))
            }
        }

        // ── Messages universels bonus (variété) ──
        messages.addAll(listOf(
            "On lâche rien $name, la suite t'attend.",
            "Bien joué. Hydrate-toi, respire, et on repart.",
            "$exercisesDone sur $totalExercises, tu avances bien !",
            "Ça c'est fait. Allez, prochain mouvement."
        ))

        return pickUnique(messages, exerciseLastIdx)
    }

    // ═══════════════════════════════════════
    // FIN DE SÉANCE
    // ═══════════════════════════════════════

    fun sessionComplete(
        firstName: String,
        totalSets: Int,
        totalReps: Int,
        totalVolume: Double,
        durationMinutes: Long,
        exercisesCompleted: Int,
        exercisesSkipped: Int,
        streak: Int,
        goalName: String = "SHRED"
    ): String {
        val name = firstName.ifBlank { "Champion" }
        val volStr = when {
            totalVolume >= 1000 -> "%.1f tonnes".format(totalVolume / 1000)
            totalVolume > 0 -> "%.0f kg".format(totalVolume)
            else -> ""
        }
        val total = exercisesCompleted + exercisesSkipped

        // ── Séance difficile ──
        if (exercisesSkipped > exercisesCompleted) return pickUnique(listOf(
            "Journée compliquée, mais tu as eu le courage de venir. C'est déjà énorme $name.",
            "Les mauvaises séances font les bons athlètes. Repose-toi et reviens plus fort.",
            "Pas ta meilleure séance, et alors ? Tu étais là. C'est ça qui compte $name."
        ), sessionLastIdx)

        // ── Séance express ──
        if (durationMinutes < 30 && exercisesSkipped == 0) return pickUnique(listOf(
            "${durationMinutes} minutes chrono, zéro compromis. L'efficacité incarnée $name.",
            "Court mais intense ! Preuve qu'on n'a pas besoin de 2h pour bosser sérieusement.",
            "Séance express et complète. Tu gères ton temps comme un pro $name."
        ), sessionLastIdx)

        // ── Streak impressionnant ──
        if (streak >= 7) return pickUnique(listOf(
            "$streak jours d'affilée $name ! C'est cette régularité qui transforme un corps.",
            "Série de $streak jours. La discipline bat le talent, et toi tu as les deux.",
            "$name, $streak jours sans faillir. Tes objectifs tremblent en te voyant arriver."
        ), sessionLastIdx)

        // ── Séance complète ──
        if (exercisesSkipped == 0) return pickUnique(listOf(
            "$exercisesCompleted exercices, $totalSets séries, $volStr. Tu n'as rien lâché $name.",
            "Séance complète en ${durationMinutes} minutes. Zéro exercice sauté. Chapeau.",
            "$totalReps répétitions et $volStr soulevés. $name, tu peux être fier de toi.",
            "Du début à la fin sans faiblir. C'est ça la mentalité $name.",
            "$volStr de volume total. C'est une vraie séance de warrior ça."
        ), sessionLastIdx)

        // ── Séance avec quelques passages ──
        return pickUnique(listOf(
            "$exercisesCompleted exercices sur $total complétés et $volStr de volume. Bonne séance $name.",
            "${durationMinutes} minutes d'effort, c'est toujours ${durationMinutes} minutes de plus que le canapé.",
            "C'est plié $name ! $totalSets séries au compteur. Repose-toi bien, tu l'as mérité.",
            "Séance bouclée. Pas parfaite ? Peut-être. Mais tu étais là, et c'est l'essentiel."
        ), sessionLastIdx)
    }
}
