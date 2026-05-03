package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.FitnessGoal

/**
 * Calcul du TDEE (Total Daily Energy Expenditure) — Harris-Benedict révisé.
 *
 * Multiplicateurs d'activité (Frankenfield et al., 2005) :
 *  1 = Sédentaire (bureau, peu ou pas d'exercice) → 1.2
 *  2 = Légèrement actif (1-2 séances/sem)        → 1.375
 *  3 = Modérément actif (3-5 séances/sem)         → 1.55
 *  4 = Actif (6-7 séances/sem)                    → 1.725
 *  5 = Très actif (2x/jour, travail physique)     → 1.9
 */
object TdeeCalculator {

    private val ACTIVITY_MULTIPLIERS = mapOf(
        1 to 1.20,
        2 to 1.375,
        3 to 1.55,
        4 to 1.725,
        5 to 1.9
    )

    /** BMR brut (Harris-Benedict révisé). */
    fun bmr(sex: String, weightKg: Double, heightCm: Int, age: Int): Double {
        return if (sex.uppercase() == "M") {
            88.362 + 13.397 * weightKg + 4.799 * heightCm - 5.677 * age
        } else {
            447.593 + 9.247 * weightKg + 3.098 * heightCm - 4.330 * age
        }
    }

    /** TDEE = BMR × multiplicateur d'activité. */
    fun tdee(sex: String, weightKg: Double, heightCm: Int, age: Int, activityLevel: Int): Int {
        val mult = ACTIVITY_MULTIPLIERS[activityLevel.coerceIn(1, 5)] ?: 1.55
        return (bmr(sex, weightKg, heightCm, age) * mult).toInt()
    }

    /** TDEE ajusté selon l'objectif (déficit sèche / surplus prise de masse). */
    fun targetCalories(
        sex: String,
        weightKg: Double,
        heightCm: Int,
        age: Int,
        activityLevel: Int,
        goal: FitnessGoal
    ): Int {
        val base = tdee(sex, weightKg, heightCm, age, activityLevel)
        return when (goal) {
            FitnessGoal.SHRED -> base - 400
            FitnessGoal.BULK -> base + 300
            FitnessGoal.MAINTAIN -> base
        }
    }

    /**
     * Ajustement journalier selon que c'est un jour d'entraînement ou de repos.
     *
     * Principe : le TDEE hebdomadaire global est préservé, mais redistribué :
     * - Jour d'entraînement : +200 kcal (carbs pour la performance)
     * - Jour de repos       : -(200 × nbTrainingDays / nbRestDays) kcal
     *
     * Exemple : 4 jours de sport / 3 jours repos, target SHRED = 2100 kcal
     *  → Training day : 2300 kcal
     *  → Rest day     : 2100 - (200×4/3) ≈ 1833 kcal
     *  → Total semaine : 4×2300 + 3×1833 = 14 700 ≈ 7×2100 ✓
     */
    fun dailyAdjustedCalories(
        weeklyBaseTarget: Int,
        isTrainingDay: Boolean,
        trainingDaysPerWeek: Int
    ): Int {
        val trainDays = trainingDaysPerWeek.coerceIn(1, 7)
        val restDays = 7 - trainDays
        val trainingBonus = 200

        return if (isTrainingDay) {
            weeklyBaseTarget + trainingBonus
        } else if (restDays > 0) {
            // Compenser pour garder le même total hebdomadaire
            val restReduction = (trainingBonus * trainDays) / restDays
            weeklyBaseTarget - restReduction
        } else {
            // 7/7 training → pas de compensation
            weeklyBaseTarget + trainingBonus
        }
    }
}
