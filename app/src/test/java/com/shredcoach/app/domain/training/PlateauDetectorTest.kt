package com.shredcoach.app.domain.training

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.dao.SetWithDate
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests pour la détection de progression / plateau / PR par exercice.
 *
 * Stratégie : on alimente le DAO mocké avec des séries de [SetWithDate]
 * pré-construites pour valider chaque branche de l'algo (progression linéaire,
 * plateau, PR récent, données insuffisantes, sessions multiples le même jour).
 */
class PlateauDetectorTest {

    private val dao: WorkoutLogDao = mockk()
    private val detector = PlateauDetector(dao)
    private val today: LocalDate = LocalDate.of(2026, 5, 15)
    private val exerciseId = 42L

    @Test
    fun `retourne null si moins de 3 sessions`() = runTest {
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(7), weight = 80.0, reps = 5),
            set(today.minusDays(3), weight = 82.5, reps = 5),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNull()
    }

    @Test
    fun `progression nette donne status Progressing avec pente positive`() = runTest {
        // 4 séances, +2.5kg/sem en moyenne
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(21), weight = 80.0, reps = 5, logId = 1),
            set(today.minusDays(14), weight = 82.5, reps = 5, logId = 2),
            set(today.minusDays(7), weight = 85.0, reps = 5, logId = 3),
            set(today, weight = 87.5, reps = 5, logId = 4),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.status).isInstanceOf(ProgressStatus.Progressing::class.java)
        val slope = (result.status as ProgressStatus.Progressing).weeklyDeltaKg
        assertThat(slope).isGreaterThan(0.6)
    }

    @Test
    fun `plateau detecte si pente plate sur 4 sessions et 21j sans nouveau best`() = runTest {
        // 4 séances toutes à ~85kg, le dernier best date de 25 jours
        val baseDate = today.minusDays(25)
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(baseDate, weight = 85.0, reps = 5, logId = 1),                    // peak il y a 25j
            set(baseDate.plusDays(7), weight = 84.0, reps = 5, logId = 2),
            set(baseDate.plusDays(14), weight = 84.5, reps = 5, logId = 3),
            set(baseDate.plusDays(21), weight = 84.0, reps = 5, logId = 4),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.status).isInstanceOf(ProgressStatus.Plateau::class.java)
    }

    @Test
    fun `pas de plateau si nouveau best dans les 21 derniers jours`() = runTest {
        // Best il y a 10j → en zone "PR récent", pas plateau
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(28), weight = 80.0, reps = 5, logId = 1),
            set(today.minusDays(21), weight = 82.0, reps = 5, logId = 2),
            set(today.minusDays(14), weight = 83.0, reps = 5, logId = 3),
            set(today.minusDays(10), weight = 90.0, reps = 5, logId = 4),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.status).isNotInstanceOf(ProgressStatus.Plateau::class.java)
        assertThat(result.hasFreshPr).isTrue()
    }

    @Test
    fun `hasFreshPr vrai si all-time-best est dans les 14 derniers jours`() = runTest {
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(30), weight = 80.0, reps = 5, logId = 1),
            set(today.minusDays(20), weight = 85.0, reps = 5, logId = 2),
            set(today.minusDays(5), weight = 95.0, reps = 5, logId = 3),  // PR fraîche
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.hasFreshPr).isTrue()
    }

    @Test
    fun `hasFreshPr faux si all-time-best date de plus de 14 jours`() = runTest {
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(30), weight = 95.0, reps = 5, logId = 1),  // PR ancienne
            set(today.minusDays(20), weight = 85.0, reps = 5, logId = 2),
            set(today.minusDays(5), weight = 88.0, reps = 5, logId = 3),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.hasFreshPr).isFalse()
    }

    @Test
    fun `2 seances le meme jour comptent comme 2 sessions distinctes`() = runTest {
        // User push le matin + pull le soir, sur 4 jours différents → 8 sessions
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(3), weight = 80.0, reps = 5, logId = 1, hour = 8),
            set(today.minusDays(3), weight = 82.5, reps = 3, logId = 2, hour = 19),  // 2e session même jour
            set(today.minusDays(2), weight = 85.0, reps = 5, logId = 3, hour = 8),
            set(today.minusDays(1), weight = 87.5, reps = 5, logId = 4, hour = 8),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        // 4 logs distincts → 4 sessions, pas 3 (avant le fix M3, on aurait fusionné les 2 du même jour)
        assertThat(result!!.sessionsCount).isEqualTo(4)
    }

    @Test
    fun `bestOneRm reflete la valeur all-time apres 1RM Epley`() = runTest {
        // 100kg x 5 reps → Epley = 116.67kg. C'est le best.
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(20), weight = 100.0, reps = 5, logId = 1),
            set(today.minusDays(10), weight = 90.0, reps = 5, logId = 2),
            set(today, weight = 95.0, reps = 5, logId = 3),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        // 100 × (1 + 5/30) = 116.666... arrondi à 116.5 par roundToHalfKg
        assertThat(result!!.bestOneRmKg).isEqualTo(116.5)
    }

    @Test
    fun `sparkline contient au plus 12 points et est ordonnee chronologiquement`() = runTest {
        val sets = (0..15).map { idx ->
            set(today.minusDays((15 - idx).toLong()), weight = 80.0 + idx, reps = 5, logId = idx.toLong() + 1)
        }
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns sets
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.sparkline).hasSize(12)
        // Tri chronologique : valeurs croissantes (les poids progressent dans nos données)
        for (i in 1 until result.sparkline.size) {
            assertThat(result.sparkline[i]).isGreaterThan(result.sparkline[i - 1])
        }
    }

    @Test
    fun `sets sans charge externe sont skippes`() = runTest {
        // Mix : 3 sets valides + 2 sets bodyweight (weight=0) → 3 sessions
        coEvery { dao.getWeightProgressionForExercise(exerciseId) } returns listOf(
            set(today.minusDays(20), weight = 80.0, reps = 5, logId = 1),
            set(today.minusDays(15), weight = 0.0, reps = 10, logId = 2),  // skip
            set(today.minusDays(10), weight = 85.0, reps = 5, logId = 3),
            set(today.minusDays(5), weight = 0.0, reps = 12, logId = 4),   // skip
            set(today, weight = 90.0, reps = 5, logId = 5),
        )
        val result = detector.analyze(exerciseId, today)
        assertThat(result).isNotNull()
        assertThat(result!!.sessionsCount).isEqualTo(3)
    }

    private fun set(
        date: LocalDate,
        weight: Double,
        reps: Int,
        logId: Long = 1L,
        hour: Int = 18,
    ): SetWithDate = SetWithDate(
        exerciseId = exerciseId,
        workoutLogId = logId,
        weightKg = weight,
        reps = reps,
        setNumber = 1,
        date = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
    )
}
