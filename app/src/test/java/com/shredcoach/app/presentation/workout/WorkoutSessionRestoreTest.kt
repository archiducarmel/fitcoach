package com.shredcoach.app.presentation.workout

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import org.junit.Test
import java.time.LocalDateTime

/**
 * Tests pour [rebuildProgressFromSets] — la logique critique de reprise de
 * séance après cold-start ou re-navigation.
 *
 * **Pourquoi ces tests sont importants** : avant ce fix, ouvrir une séance
 * déjà commencée resettait `currentExerciseIndex=0` et perdait toutes les
 * séries déjà loggées côté UI (alors que la DB avait conservé les sets).
 * Bug user reporté : "Je clique sur Séance en cours sur la home → ma séance
 * recommence depuis le début". Ces tests verrouillent que la fonction
 * reconstruit fidèlement l'état UI à partir des [WorkoutSetEntity] persistés.
 */
class WorkoutSessionRestoreTest {

    private val fixedNow = LocalDateTime.of(2026, 5, 5, 10, 30, 0)
    private val now = { fixedNow }

    private fun makeExo(id: Long, series: Int = 3, name: String = "Exo$id"): ExerciseEntity =
        ExerciseEntity(
            id = id,
            name = name,
            muscleGroup = MuscleGroup.CHEST_UPPER,
            variant = ExerciseVariant.WEIGHTS,
            equipment = "Barre",
            executionKey = "test",
            startingWeight = "20 kg",
            series = series,
            repsMin = 8,
            repsMax = 12,
            restSeconds = 90,
            tips = "",
        )

    private fun makeSet(
        logId: Long = 1L,
        exoId: Long,
        setNumber: Int,
        weightKg: Double = 50.0,
        reps: Int = 10,
        completed: Boolean = true,
        exerciseDurationSeconds: Long? = null
    ): WorkoutSetEntity = WorkoutSetEntity(
        workoutLogId = logId,
        exerciseId = exoId,
        setNumber = setNumber,
        reps = reps,
        targetReps = 10,
        weightKg = weightKg,
        targetWeightKg = 50.0,
        targetRestSeconds = 90,
        exerciseDurationSeconds = exerciseDurationSeconds,
        completed = completed,
    )

    @Test
    fun `aucun set persiste reset l'index a 0 sur l'exo 1`() {
        val exos = listOf(makeExo(10), makeExo(20))
        val r = rebuildProgressFromSets(exos, emptyList(), now)
        assertThat(r.currentExerciseIndex).isEqualTo(0)
        assertThat(r.currentSeries).isEqualTo(1)
        assertThat(r.completedSets).isEmpty()
    }

    @Test
    fun `une seule serie faite sur exo 1 (3 series prevues) -- reste sur exo 1, serie 2`() {
        val exos = listOf(makeExo(10, series = 3), makeExo(20, series = 3))
        val sets = listOf(makeSet(exoId = 10, setNumber = 1))
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(0)
        assertThat(r.currentSeries).isEqualTo(2)
        assertThat(r.completedSets).hasSize(1)
        assertThat(r.completedSets[0].seriesNumber).isEqualTo(1)
        assertThat(r.completedSets[0].skipped).isFalse()
    }

    @Test
    fun `exo 1 termine (3 series faites) -- on passe a exo 2 serie 1`() {
        val exos = listOf(makeExo(10, series = 3), makeExo(20, series = 3))
        val sets = listOf(
            makeSet(exoId = 10, setNumber = 1, exerciseDurationSeconds = 180L),
            makeSet(exoId = 10, setNumber = 2),
            makeSet(exoId = 10, setNumber = 3),
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(1)
        assertThat(r.currentSeries).isEqualTo(1)
        assertThat(r.completedSets).hasSize(3)
    }

    @Test
    fun `tous les exos termines -- on reste sur le dernier (UI affichera fin de seance)`() {
        val exos = listOf(makeExo(10, series = 2), makeExo(20, series = 2))
        val sets = listOf(
            makeSet(exoId = 10, setNumber = 1),
            makeSet(exoId = 10, setNumber = 2, exerciseDurationSeconds = 200L),
            makeSet(exoId = 20, setNumber = 1),
            makeSet(exoId = 20, setNumber = 2, exerciseDurationSeconds = 150L),
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(1) // dernier exo
        // currentSeries cape a series (2) — l'UI saura qu'on est sur la dernière
        assertThat(r.currentSeries).isEqualTo(2)
    }

    @Test
    fun `un set skipped compte comme une serie occupee (la suivante avance)`() {
        // Bug évité : si on ne comptait QUE les sets non-skipped, l'index pourrait
        // boucler sur l'exo courant sans jamais avancer après un skip user.
        val exos = listOf(makeExo(10, series = 3))
        val sets = listOf(
            makeSet(exoId = 10, setNumber = 1),
            makeSet(exoId = 10, setNumber = 2, completed = false), // skipped
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(0)
        assertThat(r.currentSeries).isEqualTo(3)
        assertThat(r.completedSets).hasSize(2)
        assertThat(r.completedSets[1].skipped).isTrue()
    }

    @Test
    fun `volume et reps reconstitues fidelement depuis la DB`() {
        val exos = listOf(makeExo(10, series = 3))
        val sets = listOf(
            makeSet(exoId = 10, setNumber = 1, weightKg = 60.0, reps = 10),
            makeSet(exoId = 10, setNumber = 2, weightKg = 65.0, reps = 8),
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        // Volume des sets non-skipped = 60×10 + 65×8 = 1120
        val volume = r.completedSets.filter { !it.skipped }.sumOf { it.weight * it.reps }
        val totalReps = r.completedSets.filter { !it.skipped }.sumOf { it.reps }
        assertThat(volume).isEqualTo(1120.0)
        assertThat(totalReps).isEqualTo(18)
    }

    @Test
    fun `exerciseDurations recuperes pour les exos termines`() {
        val exos = listOf(makeExo(10, series = 2), makeExo(20, series = 2), makeExo(30, series = 2))
        val sets = listOf(
            // Exo 1 (idx 0) terminé : duration 180s sur le dernier set
            makeSet(exoId = 10, setNumber = 1),
            makeSet(exoId = 10, setNumber = 2, exerciseDurationSeconds = 180L),
            // Exo 2 (idx 1) terminé : duration 150s
            makeSet(exoId = 20, setNumber = 1),
            makeSet(exoId = 20, setNumber = 2, exerciseDurationSeconds = 150L),
            // Exo 3 (idx 2) non commencé
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(2)
        assertThat(r.exerciseDurations).containsExactly(0, 180L, 1, 150L)
    }

    @Test
    fun `exerciseStartTimes ne contient que l'exo courant`() {
        val exos = listOf(makeExo(10, series = 2), makeExo(20, series = 2))
        val sets = listOf(
            makeSet(exoId = 10, setNumber = 1),
            makeSet(exoId = 10, setNumber = 2, exerciseDurationSeconds = 100L),
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        // L'exo courant est idx 1 — il reçoit le timestamp now()
        assertThat(r.exerciseStartTimes).containsExactly(1, fixedNow)
    }

    @Test
    fun `appel sur liste d'exos vide leve une exception`() {
        // Garde-fou : freestyle (exos = []) doit être géré côté caller, pas ici.
        try {
            rebuildProgressFromSets(emptyList(), emptyList(), now)
            assertThat("Should have thrown").isEmpty()
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("vide")
        }
    }

    @Test
    fun `un set skipped sans set valide -- currentSeries = 2`() {
        // Cas où l'utilisateur skippe la 1re série d'un exo.
        val exos = listOf(makeExo(10, series = 3))
        val sets = listOf(makeSet(exoId = 10, setNumber = 1, completed = false))
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.currentExerciseIndex).isEqualTo(0)
        assertThat(r.currentSeries).isEqualTo(2)
        assertThat(r.completedSets).hasSize(1)
        assertThat(r.completedSets[0].skipped).isTrue()
    }

    @Test
    fun `tempoUsed et durations sont preserves dans le mapping`() {
        val exos = listOf(makeExo(10, series = 2))
        val sets = listOf(
            WorkoutSetEntity(
                workoutLogId = 1L, exerciseId = 10, setNumber = 1,
                reps = 10, targetReps = 10, weightKg = 50.0, targetWeightKg = 50.0,
                restSeconds = 75, targetRestSeconds = 90,
                tempoUsed = "4-0-2-0", setDurationSeconds = 32,
                exerciseDurationSeconds = null, completed = true
            )
        )
        val r = rebuildProgressFromSets(exos, sets, now)
        assertThat(r.completedSets).hasSize(1)
        val set = r.completedSets[0]
        assertThat(set.tempoUsed).isEqualTo("4-0-2-0")
        assertThat(set.setDurationSeconds).isEqualTo(32)
        assertThat(set.restSecondsActual).isEqualTo(75)
    }
}
