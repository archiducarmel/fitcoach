package com.shredcoach.app.presentation.home

import com.shredcoach.app.data.local.entity.NutritionType
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * État nutrition consolidé pour le Today Card de la home.
 *
 * Toutes les valeurs sont calculées côté ViewModel à partir de :
 *  - [com.shredcoach.app.data.local.entity.MealLogEntity] (consommé du jour)
 *  - [com.shredcoach.app.data.local.entity.NutritionGoalEntity] (cibles)
 *  - [com.shredcoach.app.data.local.entity.NutritionScheduleEntity] (prochain repas)
 */
data class TodayNutrition(
    val caloriesConsumed: Int,
    val caloriesTarget: Int,
    val proteinsConsumedGrams: Int,
    val proteinsTargetGrams: Int,
    val carbsConsumedGrams: Int,
    val fatsConsumedGrams: Int,
    /** Prochain item du planning nutrition (repas/shaker/eau) après l'heure courante. */
    val next: NextScheduleItem?,
) {
    val caloriesRemaining: Int get() = (caloriesTarget - caloriesConsumed).coerceAtLeast(0)
    val proteinsRemainingGrams: Int get() = (proteinsTargetGrams - proteinsConsumedGrams).coerceAtLeast(0)

    /** Ratio [0,1] capé pour les rings. Au-delà de la cible on stoppe à 1.0 et on bascule en "over" via [isOver]. */
    val caloriesProgress: Float
        get() = if (caloriesTarget <= 0) 0f
        else (caloriesConsumed.toFloat() / caloriesTarget).coerceIn(0f, 1f)

    val proteinsProgress: Float
        get() = if (proteinsTargetGrams <= 0) 0f
        else (proteinsConsumedGrams.toFloat() / proteinsTargetGrams).coerceIn(0f, 1f)

    val isCaloriesOver: Boolean get() = caloriesConsumed > caloriesTarget && caloriesTarget > 0
}

data class NextScheduleItem(
    val name: String,
    val time: LocalTime,
    val type: NutritionType,
)

/**
 * Séance à reprendre — log non-complété datant de moins de 24h.
 * Au-delà, on n'expose pas la session (auto-hide ; le log reste en base
 * pour ne pas perdre les sets déjà loggés, mais sort de la home).
 */
data class ResumableSession(
    val workoutLogId: Long,
    val workoutName: String,
    val startedAt: LocalDateTime,
    val elapsedMinutes: Int,
    val completedExercises: Int,
    val totalExercises: Int,
) {
    /** Ratio [0,1] pour la progress bar du card. */
    val progress: Float
        get() = if (totalExercises <= 0) 0f
        else (completedExercises.toFloat() / totalExercises).coerceIn(0f, 1f)
}
