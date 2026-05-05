package com.shredcoach.app.notification

import com.shredcoach.app.domain.coach.CoachSettingsStore

/**
 * System + user prompts pour les notifications de débrief IA.
 *
 * Le débrief repas a évolué V6 : ton aligné sur les settings coach (GENTLE/
 * DIRECT/DRILL), contexte enrichi (autres repas du jour, séance, comparaison
 * veille, Nutri-Score), délai dynamique (vs "45 min" hardcodé). Le débrief
 * séance reste sur l'ancien template — refonte hors scope V6.
 */
object DebriefPrompts {

    /**
     * System prompt **legacy** utilisé encore par le débrief séance. Pour les
     * débriefs repas → utiliser [buildMealSystemPrompt] qui prend le ton.
     */
    val DEBRIEF_SYSTEM_PROMPT = """
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

    /**
     * System prompt pour les débriefs **repas**, paramétré par le ton choisi
     * par l'utilisateur. Mêmes 3 voix que le coach proactif (cohérence UX) +
     * un exemple BON/MAUVAIS de débrief repas matchant le ton sélectionné.
     */
    fun buildMealSystemPrompt(tone: CoachSettingsStore.Tone): String {
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
        val nameClause = firstName.ifBlank { "" }.let { if (it.isNotBlank()) "Prénom : $it\n" else "" }
        val dishLine = if (dishCount > 1) "$dishName + ${dishCount - 1} autre(s) plat(s)"
                       else dishName.ifBlank { "Repas scanné" }
        val nutriLine = if (nutriScoreGrade.isNotBlank())
            "Nutri-Score : $nutriScoreGrade · Score santé : $healthScore/10"
        else "Score santé : $healthScore/10"

        val deficitCals = (dailyCaloriesTarget - dailyCaloriesSoFar).coerceAtLeast(0)
        val deficitProt = (dailyProteinsTarget - dailyProteinsSoFar.toInt()).coerceAtLeast(0)

        val workoutLine = when {
            workoutDoneToday && workoutVolumeKg > 0 ->
                "Séance FAITE aujourd'hui (${workoutVolumeKg}kg de volume) — récup prot importante."
            workoutDoneToday -> "Séance FAITE aujourd'hui — récup prot importante."
            else -> "Pas de séance aujourd'hui."
        }

        val yesterdayLine = if (yesterdayCalsAtSamePoint > 0) {
            val delta = dailyCaloriesSoFar - yesterdayCalsAtSamePoint
            val sign = if (delta > 0) "+" else ""
            "Hier au même point : ${yesterdayCalsAtSamePoint}kcal (delta : $sign${delta}kcal)."
        } else ""

        val timingLine = if (isEndOfDay)
            "Position : FIN de journée, plus de repas prévu."
        else
            "Position : $remainingMealSlots repas restants prévus aujourd'hui."

        val otherMealsLine = if (otherMealsToday.isNotEmpty())
            "Repas déjà pris : ${otherMealsToday.joinToString(", ")}."
        else ""

        val intent = when {
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

        return """
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

Objectif : ${when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }}

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
    ): String = """
Débrief de la séance qui vient de se terminer (il y a 30 min) :

- Séance : $workoutName
- Durée : ${durationMin}min
- Exos : $exercisesCompleted/$exercisesTotal terminés
- Sets : $totalSets | Reps : $totalReps | Volume : ${totalVolumeKg.toInt()}kg
- PR battu : ${if (hasPR) "OUI" else "non"}

Contexte régularité :
- Streak actuel : ${streakDays}j
- Séances cette semaine : $workoutsThisWeek / $targetWorkoutsPerWeek prévues
- Objectif : ${when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }}

Rédige un débrief court pour $firstName : qualité de la séance, dynamique d'entraînement, encouragement vers le prochain objectif.
""".trimIndent()
}
