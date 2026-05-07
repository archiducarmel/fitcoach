package com.shredcoach.app.presentation.workout

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.util.Locale

/**
 * Garde-fous i18n pour [ShreddyCoachMessages] (Phase 5).
 *
 * On vérifie que les phrasebooks locaux (`exerciseTransition`, `sessionComplete`)
 * dispatchent bien sur la locale courante :
 *  - En FR, les phrases contiennent des mots français caractéristiques
 *    (« séries », « bouclé », « lâché »).
 *  - En EN, ces mêmes phrases produisent des mots anglais
 *    (« sets », « wrapped », « let go »).
 *
 * Garantit que le fallback offline (sans LLM) reste cohérent avec la locale
 * choisie par l'utilisateur — sinon le coach vocal parlerait FR avec une
 * voix EN ou inversement.
 */
class ShreddyCoachMessagesI18nTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // ──────────────────────────────────────────────────────────
    // exerciseTransition — exercice entièrement passé
    // ──────────────────────────────────────────────────────────

    @Test
    fun `exerciseTransition skipped exercise speaks french in FR locale`() {
        Locale.setDefault(Locale.FRENCH)
        // Génère plusieurs rotations pour couvrir tous les variants
        val phrases = (0..20).map {
            ShreddyCoachMessages.exerciseTransition(
                firstName = "Sitou",
                exerciseName = "Squat",
                sets = 0, reps = 0, volume = 0.0, skipped = 0,
                duration = 0L, exercisesDone = 2, totalExercises = 5,
                isPersonalRecord = false, goalName = "SHRED",
            )
        }
        // Au moins une rotation doit contenir un mot français caractéristique
        val frMarkers = listOf("la suite", "rythme", "batailles", "essentiel")
        assertThat(phrases.any { p -> frMarkers.any { p.contains(it) } }).isTrue()
    }

    @Test
    fun `exerciseTransition skipped exercise speaks english in EN locale`() {
        Locale.setDefault(Locale.ENGLISH)
        val phrases = (0..20).map {
            ShreddyCoachMessages.exerciseTransition(
                firstName = "Sitou",
                exerciseName = "Squat",
                sets = 0, reps = 0, volume = 0.0, skipped = 0,
                duration = 0L, exercisesDone = 2, totalExercises = 5,
                isPersonalRecord = false, goalName = "SHRED",
            )
        }
        val enMarkers = listOf("rhythm", "battles", "showed up", "what matters")
        assertThat(phrases.any { p -> enMarkers.any { p.contains(it) } }).isTrue()
    }

    // ──────────────────────────────────────────────────────────
    // exerciseTransition — record personnel
    // ──────────────────────────────────────────────────────────

    @Test
    fun `exerciseTransition PR mentions record in FR`() {
        Locale.setDefault(Locale.FRENCH)
        val phrases = (0..20).map {
            ShreddyCoachMessages.exerciseTransition(
                firstName = "Sitou", exerciseName = "Squat",
                sets = 4, reps = 8, volume = 800.0, skipped = 0, duration = 1800L,
                exercisesDone = 2, totalExercises = 5,
                isPersonalRecord = true, goalName = "SHRED",
            )
        }
        // "record", "Bravo" ou similaire doit apparaître
        assertThat(phrases.any { it.contains("ecord") || it.contains("Bravo") }).isTrue()
    }

    @Test
    fun `exerciseTransition PR mentions record in EN`() {
        Locale.setDefault(Locale.ENGLISH)
        val phrases = (0..20).map {
            ShreddyCoachMessages.exerciseTransition(
                firstName = "Sitou", exerciseName = "Squat",
                sets = 4, reps = 8, volume = 800.0, skipped = 0, duration = 1800L,
                exercisesDone = 2, totalExercises = 5,
                isPersonalRecord = true, goalName = "SHRED",
            )
        }
        // "record", "PR" ou "Bravo" (commun FR/EN ici) doit apparaître
        val containsRecord = phrases.any {
            it.contains("ecord") || it.contains("PR") || it.contains("Bravo")
        }
        assertThat(containsRecord).isTrue()
    }

    // ──────────────────────────────────────────────────────────
    // sessionComplete — session difficile
    // ──────────────────────────────────────────────────────────

    @Test
    fun `sessionComplete tough session speaks french in FR`() {
        Locale.setDefault(Locale.FRENCH)
        val phrases = (0..15).map {
            ShreddyCoachMessages.sessionComplete(
                firstName = "Sitou", totalSets = 8, totalReps = 60,
                totalVolume = 400.0, durationMinutes = 60L,
                exercisesCompleted = 2, exercisesSkipped = 4,
                streak = 1, goalName = "SHRED",
            )
        }
        val frMarkers = listOf("courage", "venir", "athlètes", "compte")
        assertThat(phrases.any { p -> frMarkers.any { p.contains(it) } }).isTrue()
    }

    @Test
    fun `sessionComplete tough session speaks english in EN`() {
        Locale.setDefault(Locale.ENGLISH)
        val phrases = (0..15).map {
            ShreddyCoachMessages.sessionComplete(
                firstName = "Sitou", totalSets = 8, totalReps = 60,
                totalVolume = 400.0, durationMinutes = 60L,
                exercisesCompleted = 2, exercisesSkipped = 4,
                streak = 1, goalName = "SHRED",
            )
        }
        val enMarkers = listOf("courage", "show up", "athletes", "counts")
        assertThat(phrases.any { p -> enMarkers.any { p.contains(it) } }).isTrue()
    }

    // ──────────────────────────────────────────────────────────
    // sessionComplete — volume formatting locale-aware
    // ──────────────────────────────────────────────────────────

    @Test
    fun `sessionComplete volume suffix tonnes in FR`() {
        Locale.setDefault(Locale.FRENCH)
        val phrases = (0..15).map {
            ShreddyCoachMessages.sessionComplete(
                firstName = "Sitou", totalSets = 30, totalReps = 200,
                totalVolume = 2500.0, durationMinutes = 60L, // ≥ 1000 → tonnes
                exercisesCompleted = 5, exercisesSkipped = 0,
                streak = 2, goalName = "SHRED",
            )
        }
        // Au moins une rotation doit utiliser "tonnes" (FR)
        assertThat(phrases.any { it.contains("tonnes") }).isTrue()
        // Et aucune ne doit utiliser "tons" (EN) en FR locale
        assertThat(phrases.any { it.contains(" tons") }).isFalse()
    }

    @Test
    fun `sessionComplete volume suffix tons in EN`() {
        Locale.setDefault(Locale.ENGLISH)
        val phrases = (0..15).map {
            ShreddyCoachMessages.sessionComplete(
                firstName = "Sitou", totalSets = 30, totalReps = 200,
                totalVolume = 2500.0, durationMinutes = 60L,
                exercisesCompleted = 5, exercisesSkipped = 0,
                streak = 2, goalName = "SHRED",
            )
        }
        assertThat(phrases.any { it.contains(" tons") }).isTrue()
        assertThat(phrases.any { it.contains("tonnes") }).isFalse()
    }

    // ──────────────────────────────────────────────────────────
    // Garde-fou universel : aucune phrase n'est vide
    // ──────────────────────────────────────────────────────────

    @Test
    fun `all locales produce non-empty phrases for typical session`() {
        for (locale in listOf(Locale.FRENCH, Locale.ENGLISH)) {
            Locale.setDefault(locale)
            for (rot in 0..30) {
                val p1 = ShreddyCoachMessages.exerciseTransition(
                    firstName = "Sitou", exerciseName = "Squat",
                    sets = 4, reps = 8, volume = 800.0, skipped = 0, duration = 1500L,
                    exercisesDone = 2, totalExercises = 5,
                    isPersonalRecord = false, goalName = "SHRED",
                )
                val p2 = ShreddyCoachMessages.sessionComplete(
                    firstName = "Sitou", totalSets = 20, totalReps = 150,
                    totalVolume = 1500.0, durationMinutes = 45L,
                    exercisesCompleted = 5, exercisesSkipped = 0,
                    streak = 3, goalName = "SHRED",
                )
                assertThat(p1).isNotEmpty()
                assertThat(p2).isNotEmpty()
            }
        }
    }
}
