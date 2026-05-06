package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity

/**
 * **Source unique de vérité** pour la cible calorique quotidienne.
 *
 * Pourquoi ce helper centralisé : avant, HomeViewModel lisait
 * `goal.targetCalories` (valeur stockée en DB, potentiellement stale si
 * l'user n'a pas modifié son profil depuis le passage au modèle sédentaire),
 * tandis que NutritionViewModel recalculait live depuis le profil. Résultat :
 * mismatch entre les deux pages.
 *
 * Désormais, toutes les pages (Home, Nutrition, Stats si besoin) appellent
 * [adaptiveTarget] avec le profil + les séances complétées du jour. Elles
 * obtiennent forcément la même valeur. Plus de divergence possible.
 *
 * Modèle : `BMR × 1.20 + ΔObjectif + KCAL_RÉELLES_BRÛLÉES_AUJOURD_HUI`
 *
 * Cf. [TdeeCalculator] pour les formules détaillées et la justification
 * du modèle adaptatif (vs multiplicateur d'activité fixe).
 */
object DailyCalorieTargetCalculator {

    /**
     * Cible quotidienne adaptative pour [profile], en tenant compte des
     * séances complétées passées en paramètre.
     *
     * Si [completedLogsToday] est vide → cible = base sédentaire seule.
     * Si une ou plusieurs séances sont complétées → ajout du bonus calculé
     * via la formule MET sur leur durée réelle et le poids de l'utilisateur.
     */
    fun adaptiveTarget(
        profile: UserProfileEntity,
        completedLogsToday: List<WorkoutLogEntity> = emptyList()
    ): Int {
        val sedentaryBase = TdeeCalculator.targetCaloriesSedentaryBase(
            sex = profile.sex,
            weightKg = profile.currentWeightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            goal = profile.goal
        )
        val workoutBonus = TdeeCalculator.totalWorkoutKcalForDay(
            completedLogs = completedLogsToday,
            userWeightKg = profile.currentWeightKg
        )
        return TdeeCalculator.adaptiveDailyTarget(sedentaryBase, workoutBonus)
    }

    /**
     * Cible "base sédentaire seule" pour [profile] — ce qu'il faudrait manger
     * si aucune activité physique volontaire dans la journée. Utilisée par les
     * pages qui ne connaissent pas l'activité du jour (ex: stats moyennes sur
     * N jours, où agréger un bonus quotidien n'a pas de sens).
     */
    fun sedentaryBaseTarget(profile: UserProfileEntity): Int =
        TdeeCalculator.targetCaloriesSedentaryBase(
            sex = profile.sex,
            weightKg = profile.currentWeightKg,
            heightCm = profile.heightCm,
            age = profile.age,
            goal = profile.goal
        )
}
