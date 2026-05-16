package com.shredcoach.app.domain.glucose

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class GlucoseCoherenceEngineTest {

    private val today: LocalDate = LocalDate.of(2026, 5, 16)

    // ─── Postprandial spike detection ─────────────────────

    @Test
    fun `pic notable et repas dans la fenetre 30-120min retourne PostprandialSpike avec repas`() {
        val log = glucoseLog(peak = 195.0, peakTime = LocalTime.of(13, 30))
        val meals = listOf(
            meal(time = LocalTime.of(12, 45), carbs = 80.0), // 45 min avant pic → match
            meal(time = LocalTime.of(8, 0), carbs = 30.0),   // trop ancien
        )
        val pack = GlucoseCoherenceEngine.analyzeDay(log, meals, emptyList())
        val spike = pack.correlations
            .filterIsInstance<GlucoseCorrelation.PostprandialSpike>()
            .firstOrNull()
        assertThat(spike).isNotNull()
        assertThat(spike!!.peakMgdl).isEqualTo(195.0)
        assertThat(spike.candidateMeals.size).isEqualTo(1)
        assertThat(spike.candidateMeals[0].time).isEqualTo(LocalTime.of(12, 45))
    }

    @Test
    fun `pic en dessous de 180 ne genere pas PostprandialSpike`() {
        val log = glucoseLog(peak = 170.0, peakTime = LocalTime.of(13, 30))
        val meals = listOf(meal(time = LocalTime.of(12, 45), carbs = 60.0))
        val pack = GlucoseCoherenceEngine.analyzeDay(log, meals, emptyList())
        assertThat(pack.correlations.none { it is GlucoseCorrelation.PostprandialSpike }).isTrue()
    }

    @Test
    fun `repas trop proche du pic (moins de 30min) n'est pas candidat`() {
        val log = glucoseLog(peak = 195.0, peakTime = LocalTime.of(13, 30))
        val meals = listOf(meal(time = LocalTime.of(13, 15), carbs = 80.0)) // 15 min avant
        val pack = GlucoseCoherenceEngine.analyzeDay(log, meals, emptyList())
        val spike = pack.correlations
            .filterIsInstance<GlucoseCorrelation.PostprandialSpike>().first()
        assertThat(spike.candidateMeals).isEmpty()
    }

    // ─── Hypoglycemia detection ───────────────────────────

    @Test
    fun `hypo nocturne avec workout meme jour flag nocturnal et workoutSameDay`() {
        val log = glucoseLog(min = 62.0, minTime = LocalTime.of(3, 30))
        val workout = workout(today)
        val pack = GlucoseCoherenceEngine.analyzeDay(log, emptyList(), listOf(workout))
        val hypo = pack.correlations
            .filterIsInstance<GlucoseCorrelation.Hypoglycemia>().firstOrNull()
        assertThat(hypo).isNotNull()
        assertThat(hypo!!.nocturnal).isTrue()
        assertThat(hypo.workoutSameDay).isTrue()
    }

    @Test
    fun `hypo diurne ne flag pas nocturnal`() {
        val log = glucoseLog(min = 65.0, minTime = LocalTime.of(14, 30))
        val pack = GlucoseCoherenceEngine.analyzeDay(log, emptyList(), emptyList())
        val hypo = pack.correlations
            .filterIsInstance<GlucoseCorrelation.Hypoglycemia>().first()
        assertThat(hypo.nocturnal).isFalse()
    }

    @Test
    fun `min au seuil exact 70 ne flag pas hypo`() {
        val log = glucoseLog(min = 70.0, minTime = LocalTime.of(4, 30))
        val pack = GlucoseCoherenceEngine.analyzeDay(log, emptyList(), emptyList())
        assertThat(pack.correlations.none { it is GlucoseCorrelation.Hypoglycemia }).isTrue()
    }

    // ─── Workout same-day ─────────────────────────────────

    @Test
    fun `workout meme jour avec peak retourne WorkoutSameDay`() {
        val log = glucoseLog(peak = 145.0, peakTime = LocalTime.of(15, 0), avg = 115.0)
        val workouts = listOf(workout(today, durationSec = 3600, volume = 4500.0))
        val pack = GlucoseCoherenceEngine.analyzeDay(log, emptyList(), workouts)
        val wo = pack.correlations
            .filterIsInstance<GlucoseCorrelation.WorkoutSameDay>().firstOrNull()
        assertThat(wo).isNotNull()
        assertThat(wo!!.workoutsCount).isEqualTo(1)
        assertThat(wo.workoutVolumeKg).isEqualTo(4500.0)
        assertThat(wo.peakMgdl).isEqualTo(145.0)
    }

    @Test
    fun `pas de workout et pas de peak retourne aucune correlation`() {
        val log = glucoseLog(avg = 110.0)
        val pack = GlucoseCoherenceEngine.analyzeDay(log, emptyList(), emptyList())
        assertThat(pack.correlations).isEmpty()
    }

    // ─── Helpers ─────────────────────────────────────────

    private fun glucoseLog(
        avg: Double? = null,
        peak: Double? = null,
        peakTime: LocalTime? = null,
        min: Double? = null,
        minTime: LocalTime? = null,
    ) = GlucoseLogEntity(
        date = today, avgMgdl = avg, peakMgdl = peak, peakTime = peakTime,
        minMgdl = min, minTime = minTime,
    )

    private fun meal(time: LocalTime, carbs: Double) = MealLogEntity(
        foodId = 1L, date = today, mealType = MealType.LUNCH,
        quantityGrams = 200, calories = 500.0, proteins = 30.0,
        carbs = carbs, fats = 15.0, time = time,
    )

    private fun workout(date: LocalDate, durationSec: Long = 3600L, volume: Double = 4500.0) =
        WorkoutLogEntity(
            workoutId = 1L,
            date = date.atTime(10, 0),
            durationMinutes = (durationSec / 60).toInt(),
            actualDurationSeconds = durationSec,
            totalReps = 100,
            totalVolume = volume,
            completed = true,
            routineId = "full_body",
        )
}
