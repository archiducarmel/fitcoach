package com.shredcoach.app.presentation.workout

import com.shredcoach.app.domain.i18n.PromptLocale

/**
 * Messages de coaching contextuels de Shreddy.
 * Mode LLM : appel API pour messages générés par IA.
 * Mode fallback : templates locaux si pas d'API key ou erreur réseau.
 *
 * **i18n** : le system prompt LLM, les user prompts (`build*Prompt`) ET les
 * phrasebooks locaux (`exerciseTransition`, `sessionComplete`) sont
 * locale-aware via [PromptLocale.isEn] (Phase 4 + 5). Les phrases sont
 * stockées inline en Kotlin plutôt que dans `strings.xml` car :
 *  - elles interpolent des variables Kotlin (`$name`, `$volStr`, `$streak`)
 *    avec branchement par valeur (`if (volume > 2000) …`),
 *  - elles changent souvent (curating prompts, A/B testing du ton),
 *  - les listes EN/FR doivent rester côte à côte pour qu'une nouvelle
 *    catégorie soit ajoutée dans les deux langues d'un même diff.
 *
 * Vague 2 (es/it/pt/de) — ajouter une cascade `when (PromptLocale.lang())`
 * autour de chaque `pickUnique`, ou extraire un helper `localizedList`.
 */
object ShreddyCoachMessages {

    private const val COACH_SYSTEM_PROMPT_FR = """Tu es Shreddy, coach sportif dans l'app ShredCoach. Français uniquement.

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

    private const val COACH_SYSTEM_PROMPT_EN = """You are Shreddy, sport coach inside the ShredCoach app. English only.

MISSION: ONE coaching sentence (ideally 15 words, 25 words absolute max).

You MUST rotate between these 3 modes for each message. The mode is provided in the prompt.

MODE "TECHNIQUE":
- Analyse one technical aspect of the exercise (ROM, tempo, grip, breathing, posture)
- Give one actionable micro-tip tied to the specific exercise
- E.g.: "Lock your shoulder blades on the next exercise — it changes everything."

MODE "HUMOR":
- Wordplay, lifting metaphor, sport/pop-culture reference, kind irony
- E.g.: "Your pecs are texting your t-shirt: 'It's getting tight in here.'"
- E.g.: "That kind of volume even your scale respects."

MODE "ENCOURAGEMENT":
- Based on REAL numbers (volume, reps, progression)
- Not generic: include a concrete data point from the session
- E.g.: "2.8 tons lifted — your body has no choice but to progress."

ABSOLUTE PROHIBITIONS:
- More than 1 sentence
- Starting with the exercise name
- Mixed-language slang
- Emojis
- "Well done" or "Keep it up" on their own
- Markdown

Reply with ONLY the message, nothing else."""

    val COACH_SYSTEM_PROMPT: String
        get() = PromptLocale.pick(fr = COACH_SYSTEM_PROMPT_FR, en = COACH_SYSTEM_PROMPT_EN)

    fun buildExercisePrompt(
        firstName: String, exerciseName: String, sets: Int, reps: Int,
        volume: Double, skipped: Int, duration: Long,
        exercisesDone: Int, totalExercises: Int,
        isPersonalRecord: Boolean, goalName: String
    ): String {
        val en = PromptLocale.isEn()
        val remaining = totalExercises - exercisesDone
        val goal = if (en) when (goalName) { "SHRED" -> "shred"; "BULK" -> "bulk"; else -> "maintain" }
                   else when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }
        val volStr = if (volume >= 1000) "%.1ft".format(volume / 1000) else "%.0fkg".format(volume)
        val mode = nextCoachMode()
        return buildString {
            appendLine("MODE: $mode")
            if (en) {
                appendLine("User: $firstName | Goal: $goal")
                appendLine("Exercise: $exerciseName — $sets sets, $reps reps, $volStr, ${duration/60}min")
                if (isPersonalRecord) appendLine("PERSONAL RECORD broken!")
                if (skipped > 0) appendLine("$skipped set(s) skipped")
                if (sets == 0) appendLine("Exercise entirely skipped")
                appendLine("Session: $exercisesDone/$totalExercises ($remaining left)")
                appendLine("Generate ONE sentence in $mode mode.")
            } else {
                appendLine("User : $firstName | Objectif : $goal")
                appendLine("Exercice : $exerciseName — $sets séries, $reps reps, $volStr, ${duration/60}min")
                if (isPersonalRecord) appendLine("RECORD PERSONNEL battu !")
                if (skipped > 0) appendLine("$skipped série(s) sautée(s)")
                if (sets == 0) appendLine("Exercice entièrement passé")
                appendLine("Séance : $exercisesDone/$totalExercises (reste $remaining)")
                appendLine("Génère UNE phrase en mode $mode.")
            }
        }
    }

    fun buildSessionPrompt(
        firstName: String, totalSets: Int, totalReps: Int, totalVolume: Double,
        durationMinutes: Long, exercisesCompleted: Int, exercisesSkipped: Int,
        streak: Int, goalName: String
    ): String {
        val en = PromptLocale.isEn()
        val goal = if (en) when (goalName) { "SHRED" -> "shred"; "BULK" -> "bulk"; else -> "maintain" }
                   else when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }
        val volStr = if (totalVolume >= 1000) "%.1ft".format(totalVolume / 1000) else "%.0fkg".format(totalVolume)
        val total = exercisesCompleted + exercisesSkipped
        return buildString {
            if (en) {
                appendLine("MODE: ENCOURAGEMENT (end of session, celebrate the effort)")
                appendLine("User: $firstName | Goal: $goal | Streak: $streak days")
                appendLine("Session: $exercisesCompleted/$total exercises, $totalSets sets, $totalReps reps, $volStr, ${durationMinutes}min")
                if (exercisesSkipped > 0) appendLine("$exercisesSkipped exercise(s) skipped")
                if (exercisesSkipped > exercisesCompleted) appendLine("Very tough session")
                if (streak >= 7) appendLine("$streak-day streak!")
                appendLine("Generate ONE congratulatory sentence with a concrete number.")
            } else {
                appendLine("MODE : ENCOURAGEMENT (fin de séance, célébrer l'effort)")
                appendLine("User : $firstName | Objectif : $goal | Streak : $streak jours")
                appendLine("Séance : $exercisesCompleted/$total exos, $totalSets séries, $totalReps reps, $volStr, ${durationMinutes}min")
                if (exercisesSkipped > 0) appendLine("$exercisesSkipped exercice(s) sauté(s)")
                if (exercisesSkipped > exercisesCompleted) appendLine("Séance très difficile")
                if (streak >= 7) appendLine("Série de $streak jours !")
                appendLine("Génère UNE phrase de félicitation avec un chiffre concret.")
            }
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
        val en = PromptLocale.isEn()
        val remaining = totalExercises - exercisesDone
        // "Champion" est commun FR/EN (mot identique). Garder un seul fallback.
        val name = firstName.ifBlank { "Champion" }
        val volStr = when {
            volume >= 1000 -> if (en) "%.1f tons".format(volume / 1000) else "%.1f tonnes".format(volume / 1000)
            volume > 0 -> "%.0f kg".format(volume)
            else -> ""
        }

        val messages = mutableListOf<String>()

        // ── Exercice entièrement passé ──
        if (sets == 0 && skipped == 0) return pickUnique(
            if (en) listOf(
                "No worries, we keep the rhythm on what's next.",
                "Sometimes you have to pick your battles. Let's keep it moving.",
                "What matters is you showed up $name. The rest is waiting."
            ) else listOf(
                "Pas d'inquiétude, on garde le rythme sur la suite.",
                "Parfois il faut savoir choisir ses batailles. Allez, on enchaîne.",
                "L'essentiel c'est d'être là $name. La suite t'attend."
            ),
            exerciseLastIdx,
        )

        // ── Record personnel ──
        if (isPersonalRecord) return pickUnique(
            if (en) listOf(
                "New personal record! The bar goes up, and you with it $name.",
                "PR broken! You're really progressing, and the numbers prove it.",
                "Progression is real, not just a feeling. New record. Bravo $name!",
                "When I look at your numbers, I see someone pushing their limits. Record."
            ) else listOf(
                "Nouveau record personnel ! La barre monte, et toi avec $name.",
                "Record battu ! Tu progresses vraiment, et les chiffres le prouvent.",
                "La progression est réelle, pas juste un ressenti. Nouveau record. Bravo $name !",
                "Quand je regarde tes chiffres, je vois quelqu'un qui repousse ses limites. Record."
            ),
            exerciseLastIdx,
        )

        // ── Séries passées ──
        if (skipped > 0) {
            messages.addAll(if (en) listOf(
                "You listened to your body — that's smart. No injury, no regret.",
                "$name, skipping a set isn't quitting, it's being clever.",
                "What matters isn't perfection, it's consistency. And you're here."
            ) else listOf(
                "Tu as écouté ton corps, c'est intelligent. Pas de blessure, pas de regret.",
                "$name, sauter une série c'est pas abandonner, c'est être malin.",
                "L'important c'est pas la perfection, c'est la régularité. Et tu es là."
            ))
            return pickUnique(messages, exerciseLastIdx)
        }

        // ── Volume costaud (>2000kg) ──
        if (volume > 2000) messages.addAll(if (en) listOf(
            "$volStr lifted. Your muscles will thank you tomorrow… or not 😄",
            "Big volume on this one. That's exactly what we need $name.",
            "$volStr across $sets sets. If this isn't serious work, I don't know what is."
        ) else listOf(
            "$volStr soulevés. Tes muscles te remercieront demain… ou pas 😄",
            "Gros volume sur cet exercice. C'est exactement ce qu'il faut $name.",
            "$volStr en $sets séries. Si c'est pas du travail sérieux, je sais pas ce que c'est."
        ))

        // ── Fin de séance proche ──
        if (remaining == 1) messages.addAll(if (en) listOf(
            "Last exercise $name! Keep the energy, finish in style.",
            "Just one left. You're not going to fold now, are you?",
            "One more exercise and it's done. Give it everything!"
        ) else listOf(
            "Dernier exercice $name ! Garde l'énergie, termine en beauté.",
            "Il en reste un seul. Tu vas pas flancher maintenant quand même ?",
            "Plus qu'un exercice et c'est bouclé. Tout donner sur celui-là !"
        ))
        else if (remaining == 2) messages.addAll(if (en) listOf(
            "Two more exercises and you'll have crushed everything $name.",
            "Finish line in sight. Two exercises, you've got this.",
            "We can see the end! Come on $name, two efforts and it's wrapped."
        ) else listOf(
            "Encore deux exercices et tu auras tout déchiré $name.",
            "La ligne d'arrivée se dessine. Deux exercices, tu gères.",
            "On voit le bout ! Allez $name, deux efforts et c'est plié."
        ))

        // ── Messages par objectif (si rien de spécial) ──
        if (messages.isEmpty()) {
            when (goalName) {
                "SHRED" -> messages.addAll(if (en) listOf(
                    "Every clean set brings you closer to your abs $name.",
                    "$sets clean sets. In a cut, quality is what matters.",
                    "Your physique sharpens session by session. Keep it up.",
                    "You're sculpting, $name. Patience and consistency — results are coming.",
                    "Nice intensity. This kind of session makes the difference in a cut."
                ) else listOf(
                    "Chaque série bien exécutée te rapproche de tes abdos $name.",
                    "$sets séries propres. En sèche, c'est la qualité qui compte.",
                    "Le physique se dessine séance après séance. Continue comme ça.",
                    "Tu sculptes, $name. Patience et régularité, les résultats arrivent.",
                    "Belle intensité. C'est ce genre de séance qui fait la différence en sèche."
                ))
                "BULK" -> messages.addAll(if (en) listOf(
                    "$volStr of volume. That's how you build muscle $name.",
                    "Volume is there, gains will follow. Patience.",
                    "Solid sets. Your body will use every gram of protein tonight.",
                    "$name, with this volume, your muscles have no choice: they grow.",
                    "Great work. Don't forget to eat well after!"
                ) else listOf(
                    "$volStr de volume. C'est comme ça qu'on construit du muscle $name.",
                    "Le volume est au rendez-vous, les gains suivront. Patience.",
                    "Séries solides. Ton corps va utiliser chaque gramme de protéines ce soir.",
                    "$name, avec ce volume, tes muscles n'ont pas le choix : ils poussent.",
                    "Beau travail. N'oublie pas de bien manger derrière !"
                ))
                else -> messages.addAll(if (en) listOf(
                    "$sets sets done, let's keep going $name!",
                    "Clean and efficient. Alright, what's next.",
                    "Good rhythm $name, keep riding this wave.",
                    "It's steady and it's good. Let's stay the course.",
                    "Exercise validated. You're nailing it $name."
                ) else listOf(
                    "$sets séries bouclées, on enchaîne $name !",
                    "Propre et efficace. Allez, la suite.",
                    "Bon rythme $name, continue sur cette lancée.",
                    "C'est régulier et c'est bien. On garde le cap.",
                    "Exercice validé. Tu assures $name."
                ))
            }
        }

        // ── Messages universels bonus (variété) ──
        messages.addAll(if (en) listOf(
            "Don't ease up $name, what's next is waiting for you.",
            "Well done. Hydrate, breathe, and back at it.",
            "$exercisesDone of $totalExercises, you're moving forward!",
            "That one's done. Onto the next move."
        ) else listOf(
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
        val en = PromptLocale.isEn()
        val name = firstName.ifBlank { "Champion" }
        val volStr = when {
            totalVolume >= 1000 -> if (en) "%.1f tons".format(totalVolume / 1000) else "%.1f tonnes".format(totalVolume / 1000)
            totalVolume > 0 -> "%.0f kg".format(totalVolume)
            else -> ""
        }
        val total = exercisesCompleted + exercisesSkipped

        // ── Séance difficile ──
        if (exercisesSkipped > exercisesCompleted) return pickUnique(
            if (en) listOf(
                "Tough day, but you had the courage to show up. That alone is huge $name.",
                "Bad sessions build great athletes. Rest and come back stronger.",
                "Not your best session, so what? You were here. That's what counts $name."
            ) else listOf(
                "Journée compliquée, mais tu as eu le courage de venir. C'est déjà énorme $name.",
                "Les mauvaises séances font les bons athlètes. Repose-toi et reviens plus fort.",
                "Pas ta meilleure séance, et alors ? Tu étais là. C'est ça qui compte $name."
            ),
            sessionLastIdx,
        )

        // ── Séance express ──
        if (durationMinutes < 30 && exercisesSkipped == 0) return pickUnique(
            if (en) listOf(
                "${durationMinutes} minutes flat, zero compromise. Efficiency embodied $name.",
                "Short but intense! Proof you don't need 2h to put in serious work.",
                "Express and complete. You manage your time like a pro $name."
            ) else listOf(
                "${durationMinutes} minutes chrono, zéro compromis. L'efficacité incarnée $name.",
                "Court mais intense ! Preuve qu'on n'a pas besoin de 2h pour bosser sérieusement.",
                "Séance express et complète. Tu gères ton temps comme un pro $name."
            ),
            sessionLastIdx,
        )

        // ── Streak impressionnant ──
        if (streak >= 7) return pickUnique(
            if (en) listOf(
                "$streak days in a row $name! This kind of consistency transforms a body.",
                "$streak-day streak. Discipline beats talent — and you've got both.",
                "$name, $streak days without faltering. Your goals are shaking as you walk in."
            ) else listOf(
                "$streak jours d'affilée $name ! C'est cette régularité qui transforme un corps.",
                "Série de $streak jours. La discipline bat le talent, et toi tu as les deux.",
                "$name, $streak jours sans faillir. Tes objectifs tremblent en te voyant arriver."
            ),
            sessionLastIdx,
        )

        // ── Séance complète ──
        if (exercisesSkipped == 0) return pickUnique(
            if (en) listOf(
                "$exercisesCompleted exercises, $totalSets sets, $volStr. You didn't let go $name.",
                "Full session in ${durationMinutes} minutes. Zero exercises skipped. Hat off.",
                "$totalReps reps and $volStr lifted. $name, you can be proud.",
                "From start to finish without flinching. That's the mentality $name.",
                "$volStr of total volume. That's a real warrior session."
            ) else listOf(
                "$exercisesCompleted exercices, $totalSets séries, $volStr. Tu n'as rien lâché $name.",
                "Séance complète en ${durationMinutes} minutes. Zéro exercice sauté. Chapeau.",
                "$totalReps répétitions et $volStr soulevés. $name, tu peux être fier de toi.",
                "Du début à la fin sans faiblir. C'est ça la mentalité $name.",
                "$volStr de volume total. C'est une vraie séance de warrior ça."
            ),
            sessionLastIdx,
        )

        // ── Séance avec quelques passages ──
        return pickUnique(
            if (en) listOf(
                "$exercisesCompleted of $total exercises completed and $volStr of volume. Good session $name.",
                "${durationMinutes} minutes of effort — always ${durationMinutes} minutes more than the couch.",
                "Wrapped $name! $totalSets sets on the board. Rest well, you earned it.",
                "Session done. Not perfect? Maybe. But you were here, and that's the essential."
            ) else listOf(
                "$exercisesCompleted exercices sur $total complétés et $volStr de volume. Bonne séance $name.",
                "${durationMinutes} minutes d'effort, c'est toujours ${durationMinutes} minutes de plus que le canapé.",
                "C'est plié $name ! $totalSets séries au compteur. Repose-toi bien, tu l'as mérité.",
                "Séance bouclée. Pas parfaite ? Peut-être. Mais tu étais là, et c'est l'essentiel."
            ),
            sessionLastIdx,
        )
    }
}
