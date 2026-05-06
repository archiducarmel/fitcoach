package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.FitnessGoal
import com.shredcoach.app.data.local.entity.WorkoutLogEntity

/**
 * Calcul du TDEE (Total Daily Energy Expenditure) — modèle adaptatif.
 *
 * Pourquoi un modèle "sédentaire + dépense réelle mesurée" plutôt qu'un
 * multiplicateur d'activité fixe :
 *
 * - Le multiplicateur "modérément actif ×1.55" (ancien modèle) baked-in
 *   3-5 séances/semaine dans le BMR. Si le user fait sa séance, on lui
 *   ajoute encore +200 kcal "training day" → DOUBLE-COUNTING. Si le user
 *   ne fait pas sa séance prévue, on lui sert quand même un target gonflé
 *   par le ×1.55. Résultat : la cible nutrition n'a aucun rapport avec la
 *   réalité énergétique du jour.
 *
 * - Le modèle adaptatif sépare proprement les deux composantes :
 *
 *      TARGET_DU_JOUR = BMR_SÉDENTAIRE + ΔOBJECTIF + KCAL_RÉELLES_DÉPENSÉES
 *      ───────────┬──────────  ──────┬──────  ────────────┬────────────
 *           BASE STATIQUE        SÈCHE/BULK         MESURÉ EN TEMPS RÉEL
 *
 *   La base est calculée comme si l'user passait toute la journée assis
 *   (multiplicateur 1.20 = NEAT minimum + métabolisme basal). Toute
 *   activité physique réelle est ajoutée par-dessus, en kcal mesurées via
 *   formule MET sur les WorkoutLogEntity COMPLETED du jour.
 *
 * Référence : Compendium of Physical Activities 2011 (Ainsworth et al.).
 */
object TdeeCalculator {

    /**
     * Multiplicateur sédentaire constant : ce que le corps dépense au
     * minimum hors séance volontaire (métabolisme + déplacements de base
     * de la vie quotidienne). C'est la baseline non-négociable.
     */
    private const val SEDENTARY_MULTIPLIER = 1.20

    /**
     * MET (Metabolic Equivalent of Task) de référence pour la musculation.
     * 5.5 = milieu de fourchette entre "modérée" (5.0) et "vigoureuse" (6.0)
     * — adapté à un programme full-body intense ShredCoach.
     */
    const val WORKOUT_MET_DEFAULT = 5.5

    /** Ajustements caloriques par objectif (déficit / surplus). */
    private const val SHRED_DEFICIT = 400
    private const val BULK_SURPLUS = 300

    // ─────────────────────────────────────────────────────────────────
    // BMR / BASE
    // ─────────────────────────────────────────────────────────────────

    /** BMR brut (Harris-Benedict révisé). Métabolisme basal au repos absolu. */
    fun bmr(sex: String, weightKg: Double, heightCm: Int, age: Int): Double =
        if (sex.uppercase() == "M") {
            88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
        } else {
            447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.330 * age
        }

    /**
     * Maintenance "vie sédentaire" : BMR × 1.20.
     * C'est la dépense d'une journée sans activité physique volontaire
     * (juste les déplacements normaux : marcher chez soi, monter un escalier,
     * etc.). Toute séance volontaire s'ajoute par-dessus via [estimateWorkoutKcal].
     */
    fun sedentaryMaintenance(sex: String, weightKg: Double, heightCm: Int, age: Int): Int =
        (bmr(sex, weightKg, heightCm, age) * SEDENTARY_MULTIPLIER).toInt()

    /**
     * Ajustement calorique imposé par l'objectif fitness.
     *  - SHRED  : −400 kcal (déficit)
     *  - BULK   : +300 kcal (surplus)
     *  - MAINTAIN : 0
     */
    fun goalAdjustment(goal: FitnessGoal): Int = when (goal) {
        FitnessGoal.SHRED -> -SHRED_DEFICIT
        FitnessGoal.BULK -> BULK_SURPLUS
        FitnessGoal.MAINTAIN -> 0
    }

    /**
     * Cible "base" = sedentaryMaintenance + goalAdjustment.
     * C'est la cible servie quand le user n'a fait AUCUNE activité
     * physique volontaire dans la journée. Jamais < 1200 kcal (plancher
     * de sécurité physiologique adulte sédentaire).
     */
    fun targetCaloriesSedentaryBase(
        sex: String,
        weightKg: Double,
        heightCm: Int,
        age: Int,
        goal: FitnessGoal
    ): Int {
        val maint = sedentaryMaintenance(sex, weightKg, heightCm, age)
        return (maint + goalAdjustment(goal)).coerceAtLeast(1200)
    }

    // ─────────────────────────────────────────────────────────────────
    // DÉPENSE RÉELLE (séances effectuées)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Estime les kcal brûlées sur une séance via formule MET.
     *
     *      kcal = MET × poidsKg × dureeHeures
     *
     * La durée prise en compte est la durée RÉELLE mesurée par le chrono
     * (`actualDurationSeconds`) si > 0, sinon la durée prévue (`durationMinutes`).
     * Cela reflète la dépense effective : si la séance a été plus courte que
     * prévu (l'user a quitté tôt), on n'attribue pas de kcal fantômes.
     *
     * MET par défaut [WORKOUT_MET_DEFAULT] = 5.5 (full-body intense). Pour
     * affiner, on pourrait moduler selon `totalVolume` ou intensité ressentie,
     * mais 5.5 reste une baseline solide non sur-estimée.
     */
    fun estimateWorkoutKcal(
        log: WorkoutLogEntity,
        userWeightKg: Double,
        met: Double = WORKOUT_MET_DEFAULT
    ): Int {
        val durationMinutes = if (log.actualDurationSeconds > 60L) {
            log.actualDurationSeconds / 60.0
        } else {
            log.durationMinutes.toDouble()
        }
        if (durationMinutes <= 0) return 0
        val hours = durationMinutes / 60.0
        return (met * userWeightKg * hours).toInt().coerceAtLeast(0)
    }

    /**
     * Somme des kcal brûlées sur une journée à partir des séances complétées.
     * Tolère liste vide (= 0 kcal additionnel).
     */
    fun totalWorkoutKcalForDay(
        completedLogs: List<WorkoutLogEntity>,
        userWeightKg: Double,
        met: Double = WORKOUT_MET_DEFAULT
    ): Int = completedLogs.sumOf { estimateWorkoutKcal(it, userWeightKg, met) }

    // ─────────────────────────────────────────────────────────────────
    // TARGET QUOTIDIEN ADAPTATIF
    // ─────────────────────────────────────────────────────────────────

    /**
     * Cible quotidienne adaptative : base sédentaire + dépense réelle.
     * Renvoie un nombre arrondi à 10 kcal pour éviter les "1837" qui font
     * cheap, et des fluctuations imperceptibles à chaque recompose.
     */
    fun adaptiveDailyTarget(
        sedentaryBase: Int,
        kcalBurnedToday: Int
    ): Int {
        val raw = sedentaryBase + kcalBurnedToday.coerceAtLeast(0)
        return (raw / 10) * 10
    }

    // ─────────────────────────────────────────────────────────────────
    // LEGACY (ancien modèle multiplicateur fixe)
    // ─────────────────────────────────────────────────────────────────
    //
    // Conservé pour compatibilité avec d'éventuels callers externes qui
    // l'importent. Marqué deprecated : ne plus l'utiliser pour de nouveaux
    // calculs nutrition — préférer [targetCaloriesSedentaryBase] +
    // [adaptiveDailyTarget].

    private val ACTIVITY_MULTIPLIERS = mapOf(
        1 to 1.20, 2 to 1.375, 3 to 1.55, 4 to 1.725, 5 to 1.9
    )

    @Deprecated(
        "Modèle multiplicateur fixe inadapté : il ne tient pas compte de l'activité réelle. " +
            "Préférer targetCaloriesSedentaryBase + adaptiveDailyTarget.",
        ReplaceWith("targetCaloriesSedentaryBase(sex, weightKg, heightCm, age, goal)")
    )
    fun tdee(sex: String, weightKg: Double, heightCm: Int, age: Int, activityLevel: Int): Int {
        val mult = ACTIVITY_MULTIPLIERS[activityLevel.coerceIn(1, 5)] ?: 1.55
        return (bmr(sex, weightKg, heightCm, age) * mult).toInt()
    }

    @Deprecated(
        "Préférer targetCaloriesSedentaryBase qui ne dépend plus d'activityLevel.",
        ReplaceWith("targetCaloriesSedentaryBase(sex, weightKg, heightCm, age, goal)")
    )
    fun targetCalories(
        sex: String,
        weightKg: Double,
        heightCm: Int,
        age: Int,
        @Suppress("UNUSED_PARAMETER") activityLevel: Int,
        goal: FitnessGoal
    ): Int = targetCaloriesSedentaryBase(sex, weightKg, heightCm, age, goal)

    @Deprecated(
        "Le bonus +200/-200 selon le calendrier ne reflète pas la réalité. " +
            "Préférer adaptiveDailyTarget(sedentaryBase, kcalBurnedToday) avec mesure MET.",
        ReplaceWith("adaptiveDailyTarget(weeklyBaseTarget, if (isTrainingDay) 250 else 0)")
    )
    fun dailyAdjustedCalories(
        weeklyBaseTarget: Int,
        isTrainingDay: Boolean,
        trainingDaysPerWeek: Int
    ): Int {
        val trainDays = trainingDaysPerWeek.coerceIn(1, 7)
        val restDays = 7 - trainDays
        val trainingBonus = 200
        return when {
            isTrainingDay -> weeklyBaseTarget + trainingBonus
            restDays > 0 -> weeklyBaseTarget - (trainingBonus * trainDays) / restDays
            else -> weeklyBaseTarget + trainingBonus
        }
    }
}

/**
 * État réel d'activité du jour, calculé depuis les WorkoutLogEntity
 * complétés et l'horloge wall-clock — JAMAIS depuis le calendrier prévu.
 */
enum class DailyActivityState {
    /** ≥1 séance complétée aujourd'hui (l'utilisateur a effectivement bougé). */
    TRAINED,

    /**
     * Aucune séance + journée terminée (heure cutoff dépassée OU date < aujourd'hui).
     * État final, plus de bonus possible.
     */
    RESTED,

    /**
     * Aucune séance + journée pas encore terminée (et c'est aujourd'hui).
     * Le bonus peut encore monter si l'user décide de s'entraîner.
     */
    PENDING,
}
