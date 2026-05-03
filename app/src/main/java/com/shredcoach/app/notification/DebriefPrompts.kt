package com.shredcoach.app.notification

/**
 * System + user prompts pour les notifications de débrief IA.
 * Tous en français, ton humoristique bienveillant, pas culpabilisant.
 * Format court pour tenir dans une notification push (max ~200 chars visibles).
 */
object DebriefPrompts {

    /**
     * System prompt commun aux débriefs Shreddy.
     * Impose : ton humour bienveillant, zéro culpabilisation, 1-2 phrases max, conseil actionnable.
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

    /** Prompt utilisateur pour un débrief de repas, injecté avec contexte. */
    fun buildMealDebriefPrompt(
        firstName: String,
        dishName: String,
        calories: Int,
        proteins: Double,
        carbs: Double,
        fats: Double,
        healthScore: Int,
        mealType: String,
        dailyCaloriesSoFar: Int,
        dailyCaloriesTarget: Int,
        dailyProteinsSoFar: Double,
        dailyProteinsTarget: Int,
        goalName: String // SHRED, BULK, MAINTAIN
    ): String = """
Débrief du repas qui vient d'être consommé (il y a 45 min) :

- Repas : $dishName ($mealType)
- Macros : ${calories}kcal, ${proteins.toInt()}g prot, ${carbs.toInt()}g gluc, ${fats.toInt()}g lip
- Score santé : $healthScore/10

Contexte journée (progression aujourd'hui) :
- Calories : $dailyCaloriesSoFar / $dailyCaloriesTarget kcal
- Protéines : ${dailyProteinsSoFar.toInt()} / $dailyProteinsTarget g
- Objectif : ${when (goalName) { "SHRED" -> "sèche"; "BULK" -> "prise de masse"; else -> "maintien" }}

Rédige un débrief court pour $firstName : qualité/quantité du repas, contribution à l'objectif, conseil pour le prochain repas.
""".trimIndent()

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
