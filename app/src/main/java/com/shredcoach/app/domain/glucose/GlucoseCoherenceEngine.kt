package com.shredcoach.app.domain.glucose

import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs

/**
 * Croise les pics/baisses glycémiques d'un jour avec les repas et les
 * séances loggés à proximité. Génère un fact-pack interprétable destiné à :
 *  - L'UI (timeline avec annotations colorées)
 *  - Dr. Glykos chat (tool `get_glucose_correlations` retourne ce pack)
 *  - Le notif builder J+1 (peut surcharger le body avec un fait précis)
 *
 * **Pure JVM** — pas de dépendance Android. Testable à 100% sur seuils.
 *
 * **Conception** : on cherche des CORRÉLATIONS temporelles, pas causales.
 * Un pic à 13h32 + un repas à 12h45 ne PROUVE pas la causalité ("la corrélation
 * n'est pas la causalité"), mais c'est un signal physiologiquement plausible
 * qu'on remonte. Le LLM Dr. Glykos peut ensuite raisonner dessus.
 *
 * **Fenêtres temporelles** :
 *  - meal → pic : repas de 30 à 120 min AVANT le pic (réponse postprandiale
 *    typique : pic à T+45-90min après absorption glucidique)
 *  - workout → impact : séance qui CHEVAUCHE ou termine dans les 60 min
 *    précédant un pic ou une chute notable
 *  - hypo nocturne : entre 23h et 06h, post-workout intense
 */
object GlucoseCoherenceEngine {

    /** Pic notable : ≥180 mg/dL (postprandial spike standard ADA). */
    const val NOTABLE_PEAK_THRESHOLD = 180.0
    /** Hypo : <70 mg/dL. */
    const val HYPO_THRESHOLD = 70.0
    /** Baisse notable post-workout : delta ≥30 mg/dL. */
    const val NOTABLE_DROP_DELTA = 30.0

    /** Fenêtre meal → pic (un repas dans les 30-120 min précédant un pic). */
    private val MEAL_TO_PEAK_WINDOW = Duration.ofMinutes(30L) to Duration.ofMinutes(120L)

    /**
     * Analyse une journée et retourne un pack de corrélations détectées.
     *
     * @param log entrée CGM du jour (avec peak_time et min_time si présents)
     * @param meals repas loggés ce jour-là (avec `time` non-null si possible)
     * @param workouts séances complétées ce jour-là (date = jour, possiblement
     *   `actualDurationSeconds` connu).
     */
    fun analyzeDay(
        log: GlucoseLogEntity,
        meals: List<MealLogEntity>,
        workouts: List<WorkoutLogEntity>,
    ): GlucoseCoherencePack {
        val mealsTimed = meals.filter { it.time != null }
        val correlations = mutableListOf<GlucoseCorrelation>()

        // ─── PIC POSTPRANDIAL ────────────────────────────────
        val peak = log.peakMgdl
        val peakTime = log.peakTime
        if (peak != null && peakTime != null && peak >= NOTABLE_PEAK_THRESHOLD) {
            val candidateMeals = mealsTimed.filter { meal ->
                val mt = meal.time ?: return@filter false
                val diff = Duration.between(mt, peakTime)
                !diff.isNegative
                    && diff >= MEAL_TO_PEAK_WINDOW.first
                    && diff <= MEAL_TO_PEAK_WINDOW.second
            }.sortedBy { Duration.between(it.time, peakTime).abs() }

            correlations += GlucoseCorrelation.PostprandialSpike(
                peakMgdl = peak,
                peakTime = peakTime,
                candidateMeals = candidateMeals.take(2).map { it.toRef() },
            )
        }

        // ─── HYPO ────────────────────────────────────────────
        val min = log.minMgdl
        val minTime = log.minTime
        if (min != null && minTime != null && min < HYPO_THRESHOLD) {
            // Si l'hypo est nocturne (23h-06h) ET il y a une séance dans la journée,
            // flag du risque "hypo post-effort différée".
            val isNocturnal = minTime.isAfter(LocalTime.of(23, 0)) || minTime.isBefore(LocalTime.of(6, 0))
            correlations += GlucoseCorrelation.Hypoglycemia(
                minMgdl = min,
                minTime = minTime,
                nocturnal = isNocturnal,
                workoutSameDay = workouts.isNotEmpty(),
            )
        }

        // ─── WORKOUT IMPACT (baisse post-séance) ──────────────
        // Si on a une courbe 24h, on pourrait calculer le delta réel ; sans elle,
        // on signale juste qu'une séance + un pic ou min existent le même jour.
        if (workouts.isNotEmpty() && peak != null) {
            correlations += GlucoseCorrelation.WorkoutSameDay(
                workoutsCount = workouts.size,
                workoutVolumeKg = workouts.sumOf { it.totalVolume },
                peakMgdl = peak,
                avgMgdl = log.avgMgdl,
            )
        }

        return GlucoseCoherencePack(
            date = log.date,
            correlations = correlations,
        )
    }

    private fun MealLogEntity.toRef() = MealRef(
        foodId = foodId, time = time!!, calories = calories,
        carbsG = carbs, mealTypeName = mealType.name,
    )
}

// ─── Modèles ────────────────────────────────────────────────

data class GlucoseCoherencePack(
    val date: java.time.LocalDate,
    val correlations: List<GlucoseCorrelation>,
)

sealed interface GlucoseCorrelation {
    data class PostprandialSpike(
        val peakMgdl: Double,
        val peakTime: LocalTime,
        val candidateMeals: List<MealRef>,
    ) : GlucoseCorrelation

    data class Hypoglycemia(
        val minMgdl: Double,
        val minTime: LocalTime,
        val nocturnal: Boolean,
        val workoutSameDay: Boolean,
    ) : GlucoseCorrelation

    data class WorkoutSameDay(
        val workoutsCount: Int,
        val workoutVolumeKg: Double,
        val peakMgdl: Double,
        val avgMgdl: Double?,
    ) : GlucoseCorrelation
}

data class MealRef(
    val foodId: Long,
    val time: LocalTime,
    val calories: Double,
    val carbsG: Double,
    val mealTypeName: String,
)

private fun Duration.abs(): Duration = if (isNegative) negated() else this
