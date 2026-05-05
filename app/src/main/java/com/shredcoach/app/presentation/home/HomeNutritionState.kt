package com.shredcoach.app.presentation.home

import com.shredcoach.app.data.local.entity.NutritionType
import com.shredcoach.app.domain.training.ExerciseProgression
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
 * "Insight de la semaine" — un highlight unique surfacé en hero, basé sur
 * [com.shredcoach.app.domain.training.PlateauDetector] sur les exercices les
 * plus pratiqués. Choix du highlight (par priorité) :
 *  1. PR récent (hasFreshPr)
 *  2. Progression la plus marquée (pente kg/sem la plus élevée > seuil)
 *  3. Plateau le plus long (nudge actionnable)
 *  4. Stable → sinon null (on ne montre pas de carte)
 *
 * Pourquoi UN seul insight et pas un carrousel : la home est dense, et on veut
 * une seule prise d'attention forte. Le carrousel complet existe déjà sur
 * [com.shredcoach.app.presentation.stats.DashboardScreen].
 */
data class WeeklyInsight(
    val exerciseName: String,
    val progression: ExerciseProgression,
    val tone: InsightTone,
)

enum class InsightTone {
    /** PR battu récemment → célébration. */
    PR,
    /** Pente de progression positive significative. */
    PROGRESS,
    /** Plateau détecté ≥ 3 semaines → nudge "essaie de varier". */
    PLATEAU,
}

/**
 * Séance à reprendre — log non-complété datant de moins de 24h.
 * Au-delà, on n'expose pas la session (auto-hide ; le log reste en base
 * pour ne pas perdre les sets déjà loggés, mais sort de la home).
 *
 * @param totalExercises 0 = séance libre (pas de plan préétabli) — l'UI affichera
 *                       "{completedExercises} exercices" au lieu de "X/Y".
 */
data class ResumableSession(
    val workoutLogId: Long,
    val workoutName: String,
    val startedAt: LocalDateTime,
    val elapsedMinutes: Int,
    val completedExercises: Int,
    val totalExercises: Int,
) {
    /** True si séance libre (pas de plan préétabli). UI adapte le rendu progression. */
    val isFreestyle: Boolean get() = totalExercises == 0

    /**
     * Ratio [0,1] pour la progress bar du card. En freestyle (totalExercises=0)
     * on génère une asymptote vers 1 (`1 - 1/(1+done)`) — la barre se remplit à
     * mesure que l'user fait des exos sans jamais atteindre 100% (cohérent avec
     * l'absence de "fin" prédéfinie en freestyle).
     */
    val progress: Float
        get() = when {
            isFreestyle -> 1f - 1f / (1f + completedExercises.toFloat())
            else -> (completedExercises.toFloat() / totalExercises).coerceIn(0f, 1f)
        }
}
