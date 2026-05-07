package com.shredcoach.app.notification

import com.shredcoach.app.domain.coach.CoachSettingsStore
import com.shredcoach.app.domain.i18n.PromptLocale

/**
 * System + user prompts pour les notifications de débrief IA.
 *
 * Le débrief repas a évolué V6 : ton aligné sur les settings coach (GENTLE/
 * DIRECT/DRILL), contexte enrichi (autres repas du jour, séance, comparaison
 * veille, Nutri-Score), délai dynamique (vs "45 min" hardcodé). Le débrief
 * séance reste sur l'ancien template — refonte hors scope V6.
 */
object DebriefPrompts {

    private val DEBRIEF_SYSTEM_PROMPT_FR = """
Tu es Shreddy, coach sportif et nutrition IA de l'app ShredCoach. Tu rédiges des débriefs courts à destination de l'utilisateur dans une notification push.

RÈGLES ABSOLUES :
- Français uniquement, tutoiement, prénom si dispo.
- Ton HUMORISTIQUE bienveillant, jamais culpabilisant, toujours encourageant.
- MAX 2 phrases, 180 caractères maximum (contrainte notification).
- Pas d'emoji dans le texte (sauf 1 max en fin).
- Pas de salutations ("Hey", "Salut") car c'est une notification qui arrive seule.
- Réponse directe : commence par un constat factuel, termine par un conseil actionnable si pertinent.
- N'invente PAS de données : utilise uniquement les infos fournies dans le prompt.
- Jamais de jargon scientifique.
    """.trimIndent()

    private val DEBRIEF_SYSTEM_PROMPT_EN = """
You are Shreddy, the AI sport and nutrition coach of the ShredCoach app. You write short debriefs to the user as a push notification.

ABSOLUTE RULES:
- English only, direct address (you), use the first name when available.
- HUMOROUS but caring tone, never guilt-tripping, always encouraging.
- MAX 2 sentences, 180 characters max (notification constraint).
- No emoji in the text (max 1 at the very end).
- No greetings ("Hey", "Hi") since this notification arrives standalone.
- Direct answer: start with a factual observation, end with an actionable tip when relevant.
- Do NOT invent data: only use info from the prompt.
- No scientific jargon.
    """.trimIndent()

    /**
     * System prompt **legacy** utilisé encore par le débrief séance. Pour les
     * débriefs repas → utiliser [buildMealSystemPrompt] qui prend le ton.
     */
    val DEBRIEF_SYSTEM_PROMPT: String
        get() = PromptLocale.pick(fr = DEBRIEF_SYSTEM_PROMPT_FR, en = DEBRIEF_SYSTEM_PROMPT_EN)

    /**
     * System prompt pour les débriefs **repas**, paramétré par le ton choisi
     * par l'utilisateur. Mêmes 3 voix que le coach proactif (cohérence UX) +
     * un exemple BON/MAUVAIS de débrief repas matchant le ton sélectionné.
     */
    fun buildMealSystemPrompt(tone: CoachSettingsStore.Tone): String {
        return if (PromptLocale.isEn()) buildMealSystemPromptEn(tone) else buildMealSystemPromptFr(tone)
    }

    private fun buildMealSystemPromptFr(tone: CoachSettingsStore.Tone): String {
        val toneRules = when (tone) {
            CoachSettingsStore.Tone.GENTLE -> """
TON : DOUX & BIENVEILLANT
- Pas d'impératif sec ("fais", "force-toi"). Préférer suggestion ("et si on...", "tu pourrais").
- Reformule les écarts en opportunités ("c'est ok, on rééquilibre au prochain").
- Termine par UNE proposition concrète, jamais une obligation.
            """.trimIndent()
            CoachSettingsStore.Tone.DIRECT -> """
TON : DIRECT & FACTUEL
- Constat → action, deux temps, ton neutre.
- Pas de fioriture ni d'enjolivement. Pas d'emoji dans le texte.
- Termine par UNE action claire, présent de l'indicatif.
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
TON : COACH PRO MAX, ÉNERGIQUE
- Vocabulaire sport élevé : "claque", "verrouille la macro", "sec et propre", "explose la prot".
- Direct mais JAMAIS méprisant. Exigeant comme un pote sportif passionné.
- Termine par UN call-to-action percutant.
            """.trimIndent()
        }

        val example = when (tone) {
            CoachSettingsStore.Tone.DIRECT -> """
✓ BON (DIRECT, déjeuner 720kcal/45g prot, séance faite ce matin) :
"Déj 720kcal, 45g prot après ta séance — apport correct. Reste 60g prot pour finir la journée, vise un dîner riche."

✗ MAUVAIS (DIRECT — flou, sans chiffres) :
"C'était plutôt bien ton repas, continue comme ça !"
            """.trimIndent()
            CoachSettingsStore.Tone.GENTLE -> """
✓ BON (GENTLE, dîner 950kcal sur 600 visés, sèche) :
"Dîner un peu copieux ce soir, c'est ok. Tu pourrais alléger demain midi avec un repas plus protéiné et léger."

✗ MAUVAIS (GENTLE — culpabilisation) :
"950kcal au dîner... tu as dépassé largement, ça va impacter ta sèche. Fais attention demain."
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
✓ BON (DRILL, petit-déj 35g prot, séance prévue ce soir) :
"35g de prot dès le matin, base solide. Charge à midi pour avoir le réservoir plein avant la séance, on verrouille."

✗ MAUVAIS (DRILL — méprisant) :
"35g de prot ?! C'est tout ce que tu as à offrir avant ta séance ? Réveille-toi !"
            """.trimIndent()
        }

        return """
Tu es Shreddy, coach nutrition IA de l'app ShredCoach. Tu rédiges UN débrief
post-repas à destination de l'utilisateur dans une notification push.

RÈGLES ABSOLUES :
- Français uniquement, tutoiement direct, prénom si fourni.
- MAX 2 phrases, 180 caractères maximum (contrainte notification système).
- Pas d'emoji dans le texte (sauf 1 max en fin si pertinent).
- Pas de salutations ("Hey", "Salut") — c'est une notif autonome.
- Constat factuel chiffré → conseil actionnable adapté au moment de la journée.
- N'invente AUCUNE donnée. Utilise UNIQUEMENT les infos fournies.
- Adapte le conseil au timing : si la journée n'est pas finie, parle du prochain
  repas ; si c'est le dîner, parle de récupération ou de demain.
- Si une séance est mentionnée (faite ou prévue), le conseil prot doit s'y
  rapporter explicitement.

$toneRules

EXEMPLE (étudie-le, ton message doit être proche du BON exemple) :

$example
""".trimIndent()
    }

    private fun buildMealSystemPromptEn(tone: CoachSettingsStore.Tone): String {
        val toneRules = when (tone) {
            CoachSettingsStore.Tone.GENTLE -> """
TONE: SOFT & SUPPORTIVE
- No blunt imperative ("do this", "force yourself"). Prefer suggestion ("what if we...", "you could").
- Reframe gaps as opportunities ("it's ok, we balance things at the next meal").
- End with ONE concrete suggestion, never an obligation.
            """.trimIndent()
            CoachSettingsStore.Tone.DIRECT -> """
TONE: DIRECT & FACTUAL
- Observation → action, two beats, neutral tone.
- No fluff or embellishment. No emoji in the text.
- End with ONE clear action, present tense.
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
TONE: PRO COACH MAX, ENERGETIC
- High-level sport vocabulary: "smash", "lock the macro", "clean and tight", "max the protein".
- Direct but NEVER demeaning. Demanding like a passionate sport buddy.
- End with ONE punchy call-to-action.
            """.trimIndent()
        }

        val example = when (tone) {
            CoachSettingsStore.Tone.DIRECT -> """
✓ GOOD (DIRECT, lunch 720 kcal / 45 g protein, workout done this morning):
"Lunch 720 kcal, 45 g protein after your session — solid intake. 60 g protein left for the day, aim for a rich dinner."

✗ BAD (DIRECT — vague, no numbers):
"Pretty good meal, keep it up!"
            """.trimIndent()
            CoachSettingsStore.Tone.GENTLE -> """
✓ GOOD (GENTLE, dinner 950 kcal vs 600 target, shred):
"Dinner a bit heavy tonight, that's ok. You could lighten up tomorrow at lunch with a leaner protein-focused meal."

✗ BAD (GENTLE — guilt-tripping):
"950 kcal at dinner... you went way over, this will impact your shred. Be careful tomorrow."
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
✓ GOOD (DRILL, breakfast 35 g protein, session planned tonight):
"35 g protein at breakfast, solid base. Load up at lunch so the tank is full before the session — let's lock it in."

✗ BAD (DRILL — demeaning):
"35 g of protein?! That's all you've got before your session? Wake up!"
            """.trimIndent()
        }

        return """
You are Shreddy, the AI nutrition coach of the ShredCoach app. You write ONE
post-meal debrief for the user as a push notification.

ABSOLUTE RULES:
- English only, direct address (you), first name when available.
- MAX 2 sentences, 180 characters max (system notification constraint).
- No emoji in the text (max 1 at the very end if relevant).
- No greetings ("Hey", "Hi") — this is a standalone notification.
- Numerical observation → actionable tip adapted to the time of day.
- Do NOT invent data. ONLY use the provided info.
- Adapt the advice to timing: if the day isn't over, speak about the next meal;
  if it's dinner, speak about recovery or tomorrow.
- If a session is mentioned (done or planned), the protein advice must
  explicitly tie back to it.

$toneRules

EXAMPLE (study it, your message must be close to the GOOD example):

$example
""".trimIndent()
    }

    /**
     * Construit le user prompt pour un débrief repas.
     *
     * Tous les paramètres sont injectés depuis la DB par [MealDebriefWorker]
     * — ce builder ne fait AUCUN calcul nutritionnel, juste du formatage.
     */
    fun buildMealDebriefPrompt(
        firstName: String,
        dishName: String,
        dishCount: Int,
        mealTypeDisplay: String,
        minutesSinceMeal: Long,
        calories: Int,
        proteins: Double,
        carbs: Double,
        fats: Double,
        fibers: Double,
        sugars: Double,
        saturatedFat: Double,
        saltG: Double,
        nutriScoreGrade: String,
        healthScore: Int,
        // Contexte journée
        mealsLoggedToday: Int,
        otherMealsToday: List<String>,
        dailyCaloriesSoFar: Int,
        dailyCaloriesTarget: Int,
        dailyProteinsSoFar: Double,
        dailyProteinsTarget: Int,
        // Contexte sport
        workoutDoneToday: Boolean,
        workoutVolumeKg: Int,
        // Comparaison veille
        yesterdayCalsAtSamePoint: Int,
        // Position dans la journée
        remainingMealSlots: Int,
        isEndOfDay: Boolean,
        // Profil
        goalName: String,
    ): String {
        val en = PromptLocale.isEn()
        val nameClause = firstName.ifBlank { "" }.let {
            if (it.isBlank()) "" else if (en) "First name: $it\n" else "Prénom : $it\n"
        }
        val dishLine = if (dishCount > 1)
            if (en) "$dishName + ${dishCount - 1} other dish(es)"
            else "$dishName + ${dishCount - 1} autre(s) plat(s)"
        else
            dishName.ifBlank { if (en) "Scanned meal" else "Repas scanné" }

        val nutriLine = if (en) {
            if (nutriScoreGrade.isNotBlank())
                "Nutri-Score: $nutriScoreGrade · Health score: $healthScore/10"
            else "Health score: $healthScore/10"
        } else {
            if (nutriScoreGrade.isNotBlank())
                "Nutri-Score : $nutriScoreGrade · Score santé : $healthScore/10"
            else "Score santé : $healthScore/10"
        }

        val deficitCals = (dailyCaloriesTarget - dailyCaloriesSoFar).coerceAtLeast(0)
        val deficitProt = (dailyProteinsTarget - dailyProteinsSoFar.toInt()).coerceAtLeast(0)

        val workoutLine = if (en) when {
            workoutDoneToday && workoutVolumeKg > 0 ->
                "Workout DONE today (${workoutVolumeKg}kg of volume) — protein recovery matters."
            workoutDoneToday -> "Workout DONE today — protein recovery matters."
            else -> "No workout today."
        } else when {
            workoutDoneToday && workoutVolumeKg > 0 ->
                "Séance FAITE aujourd'hui (${workoutVolumeKg}kg de volume) — récup prot importante."
            workoutDoneToday -> "Séance FAITE aujourd'hui — récup prot importante."
            else -> "Pas de séance aujourd'hui."
        }

        val yesterdayLine = if (yesterdayCalsAtSamePoint > 0) {
            val delta = dailyCaloriesSoFar - yesterdayCalsAtSamePoint
            val sign = if (delta > 0) "+" else ""
            if (en) "Yesterday at same point: ${yesterdayCalsAtSamePoint}kcal (delta: $sign${delta}kcal)."
            else "Hier au même point : ${yesterdayCalsAtSamePoint}kcal (delta : $sign${delta}kcal)."
        } else ""

        val timingLine = if (en) {
            if (isEndOfDay) "Position: END of day, no further meal scheduled."
            else "Position: $remainingMealSlots remaining meals planned today."
        } else {
            if (isEndOfDay) "Position : FIN de journée, plus de repas prévu."
            else "Position : $remainingMealSlots repas restants prévus aujourd'hui."
        }

        val otherMealsLine = if (otherMealsToday.isNotEmpty()) {
            if (en) "Already eaten: ${otherMealsToday.joinToString(", ")}."
            else "Repas déjà pris : ${otherMealsToday.joinToString(", ")}."
        } else ""

        val intent = if (en) {
            when {
                isEndOfDay && goalName == "SHRED" ->
                    "End-of-day summary. Observation on total kcal/protein vs target, advice to optimize tomorrow."
                isEndOfDay ->
                    "End-of-day summary. Factual recap of the day and a word on recovery."
                mealTypeDisplay.contains("breakfast", ignoreCase = true) ->
                    "Day kickoff. Evaluate the foundation laid for the day, advise for the next meal."
                mealTypeDisplay.contains("pre-workout", ignoreCase = true) ->
                    "Pre-workout fuel. Evaluate the quick intake and orient the upcoming session."
                mealTypeDisplay.contains("snack", ignoreCase = true) ->
                    "Snacking break. Watch for sugar, check the gap to target and orient dinner."
                else ->
                    "Mid-day debrief. Observation on intake, advice for the next meal based on what's left."
            }
        } else {
            when {
                isEndOfDay && goalName == "SHRED" ->
                    "Bilan de fin de journée. Constat sur le total kcal/prot vs cible, conseil pour optimiser demain."
                isEndOfDay ->
                    "Bilan de fin de journée. Constat factuel sur la journée et un mot sur la récup."
                mealTypeDisplay.equals("Petit-déjeuner", ignoreCase = true) ->
                    "Démarrage de journée. Évalue la base posée pour la journée, conseille pour le prochain repas."
                mealTypeDisplay.equals("Pré-training", ignoreCase = true) ->
                    "Carburant avant séance. Évalue l'apport rapide et oriente la séance imminente."
                mealTypeDisplay.equals("Goûter", ignoreCase = true) ->
                    "Pause snacking. Vigilance sucre, regarde l'écart avec la cible et oriente le dîner."
                else ->
                    "Débrief milieu de journée. Constat sur l'apport, conseil pour le prochain repas en fonction du restant à atteindre."
            }
        }

        val goalLabel = if (en) when (goalName) { "SHRED" -> "shred"; "BULK" -> "bulk"; else -> "maintain" }
                        else when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }

        return if (en) """
${nameClause}Meal: $dishLine ($mealTypeDisplay, scanned ${minutesSinceMeal}min ago)
Macros: ${calories}kcal · ${proteins.toInt()}g protein · ${carbs.toInt()}g carbs · ${fats.toInt()}g fat · ${fibers.toInt()}g fiber
Detail: sugar ${sugars.toInt()}g · sat. fat ${"%.1f".format(saturatedFat)}g · salt ${"%.1f".format(saltG)}g
$nutriLine

Day:
- $mealsLoggedToday meals logged ${if (otherMealsLine.isNotBlank()) "($otherMealsLine)" else ""}
- Total: $dailyCaloriesSoFar / $dailyCaloriesTarget kcal · ${dailyProteinsSoFar.toInt()} / $dailyProteinsTarget g protein
- Remaining to hit: $deficitCals kcal · $deficitProt g protein
- $workoutLine
${if (yesterdayLine.isNotBlank()) "- $yesterdayLine" else ""}
- $timingLine

Goal: $goalLabel

Task:
$intent
""".trimIndent() else """
${nameClause}Repas : $dishLine ($mealTypeDisplay, scanné il y a ${minutesSinceMeal}min)
Macros : ${calories}kcal · ${proteins.toInt()}g prot · ${carbs.toInt()}g gluc · ${fats.toInt()}g lip · ${fibers.toInt()}g fibres
Détail : sucres ${sugars.toInt()}g · graisses sat. ${"%.1f".format(saturatedFat)}g · sel ${"%.1f".format(saltG)}g
$nutriLine

Journée :
- $mealsLoggedToday repas pris ${if (otherMealsLine.isNotBlank()) "($otherMealsLine)" else ""}
- Cumul : $dailyCaloriesSoFar / $dailyCaloriesTarget kcal · ${dailyProteinsSoFar.toInt()} / $dailyProteinsTarget g prot
- Restant à atteindre : $deficitCals kcal · $deficitProt g prot
- $workoutLine
${if (yesterdayLine.isNotBlank()) "- $yesterdayLine" else ""}
- $timingLine

Objectif : $goalLabel

Tâche :
$intent
""".trimIndent()
    }

    /** Prompt utilisateur pour un débrief de séance, injecté avec contexte. */
    fun buildWorkoutDebriefPrompt(
        firstName: String,
        workoutName: String,
        durationMin: Long,
        exercisesCompleted: Int,
        exercisesTotal: Int,
        totalSets: Int,
        totalReps: Int,
        totalVolumeKg: Double,
        hasPR: Boolean,
        streakDays: Int,
        workoutsThisWeek: Int,
        targetWorkoutsPerWeek: Int,
        goalName: String
    ): String {
        val en = PromptLocale.isEn()
        val goal = if (en) when (goalName) { "SHRED" -> "shred"; "BULK" -> "bulk"; else -> "maintain" }
                   else when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }
        return if (en) """
Debrief of the session that just ended (30 min ago):

- Session: $workoutName
- Duration: ${durationMin}min
- Exercises: $exercisesCompleted/$exercisesTotal completed
- Sets: $totalSets | Reps: $totalReps | Volume: ${totalVolumeKg.toInt()}kg
- PR broken: ${if (hasPR) "YES" else "no"}

Consistency context:
- Current streak: ${streakDays}d
- Sessions this week: $workoutsThisWeek / $targetWorkoutsPerWeek planned
- Goal: $goal

Write a short debrief for $firstName: session quality, training momentum, encouragement toward the next goal.
""".trimIndent() else """
Débrief de la séance qui vient de se terminer (il y a 30 min) :

- Séance : $workoutName
- Durée : ${durationMin}min
- Exos : $exercisesCompleted/$exercisesTotal terminés
- Sets : $totalSets | Reps : $totalReps | Volume : ${totalVolumeKg.toInt()}kg
- PR battu : ${if (hasPR) "OUI" else "non"}

Contexte régularité :
- Streak actuel : ${streakDays}j
- Séances cette semaine : $workoutsThisWeek / $targetWorkoutsPerWeek prévues
- Objectif : $goal

Rédige un débrief court pour $firstName : qualité de la séance, dynamique d'entraînement, encouragement vers le prochain objectif.
""".trimIndent()
    }
}
