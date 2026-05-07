package com.shredcoach.app.domain.voice

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.domain.voice.WorkoutVoicePhrasebook.SetStartContext
import org.junit.Test

/**
 * Tests pour [WorkoutVoicePhrasebook].
 *
 * On vérifie 3 garanties critiques :
 *  1. Les phrases ne contiennent JAMAIS de virgule directement avant le prénom
 *     (ex: "On y est, Sitou.") — TTS marquerait une pause robotique.
 *  2. `simplifyExerciseName` ne finit JAMAIS sur un stopword français
 *     (« en », « à », « de » seul, …) qui produirait un nom tronqué.
 *  3. En mode freestyle, aucune phrase n'évoque la position dans la séance
 *     (« dernier exo », « sprint final », « mi-parcours ») car les exos
 *     s'ajoutent dynamiquement.
 */
class WorkoutVoicePhrasebookTest {

    // ──────────────────────────────────────────────────────────
    // simplifyExerciseName
    // ──────────────────────────────────────────────────────────

    @Test
    fun `simplify trims trailing stopword - tirage vertical en supination`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Tirage vertical en supination")
        // Doit produire "Tirage vertical" — surtout PAS "Tirage vertical en"
        assertThat(out).isEqualTo("Tirage vertical")
    }

    @Test
    fun `simplify trims trailing stopword - curl biceps a la barre EZ`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Curl biceps à la barre EZ")
        assertThat(out).isEqualTo("Curl biceps")
    }

    @Test
    fun `simplify keeps structural de in soulevé de terre`() {
        // « de » est structurel ici (< 2 mots significatifs avant lui)
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Soulevé de terre")
        assertThat(out).isEqualTo("Soulevé de terre")
    }

    @Test
    fun `simplify caps at 3 words - soulevé de terre roumain`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Soulevé de terre roumain")
        assertThat(out).isEqualTo("Soulevé de terre")
    }

    @Test
    fun `simplify keeps short names intact`() {
        assertThat(WorkoutVoicePhrasebook.simplifyExerciseName("Squat")).isEqualTo("Squat")
        assertThat(WorkoutVoicePhrasebook.simplifyExerciseName("Pompes")).isEqualTo("Pompes")
    }

    @Test
    fun `simplify strips parentheses - squat with haltères`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Squat (haltères)")
        assertThat(out).isEqualTo("Squat")
    }

    @Test
    fun `simplify never ends on stopword - dev couche a la barre`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Développé couché à la barre")
        // Ne doit ni se terminer par "à" ni par "la"
        val lastWord = out.split(" ").last().lowercase()
        assertThat(lastWord).isNotEqualTo("à")
        assertThat(lastWord).isNotEqualTo("la")
        assertThat(lastWord).isNotEqualTo("de")
        assertThat(lastWord).isNotEqualTo("en")
    }

    @Test
    fun `simplify blank returns generic`() {
        assertThat(WorkoutVoicePhrasebook.simplifyExerciseName("")).isEqualTo("l'exercice")
        assertThat(WorkoutVoicePhrasebook.simplifyExerciseName("   ")).isEqualTo("l'exercice")
    }

    @Test
    fun `simplify normalizes multi spaces`() {
        val out = WorkoutVoicePhrasebook.simplifyExerciseName("Tirage   vertical")
        assertThat(out).isEqualTo("Tirage vertical")
    }

    // ──────────────────────────────────────────────────────────
    // Pas de virgule + prénom (TTS pause)
    // ──────────────────────────────────────────────────────────

    @Test
    fun `aucune phrase ne contient virgule espace prenom suivi de point`() {
        // On scanne tous les contextes possibles avec rotation pour couvrir
        // toutes les phrases du phrasebook.
        val name = "Sitou"
        val contexts = listOf(
            ctx(name, isWarmup = true),
            ctx(name, isCardio = true),
            ctx(name, currentSet = 1, totalSets = 4, currentExerciseIndex = 0, totalExercises = 5),
            ctx(name, currentSet = 4, totalSets = 4, currentExerciseIndex = 0, totalExercises = 5),
            ctx(name, currentSet = 1, totalSets = 4, currentExerciseIndex = 4, totalExercises = 5),
            ctx(name, currentSet = 4, totalSets = 4, currentExerciseIndex = 4, totalExercises = 5),
            ctx(name, currentSet = 3, totalSets = 4, currentExerciseIndex = 2, totalExercises = 5),
            ctx(name, currentSet = 2, totalSets = 4, currentExerciseIndex = 4, totalExercises = 5),
            ctx(name, currentSet = 2, totalSets = 4, currentExerciseIndex = 2, totalExercises = 5),
            ctx(name, isFreestyle = true, currentSet = 1, totalSets = 3, totalExercises = 1),
            ctx(name, isFreestyle = true, currentSet = 3, totalSets = 3, totalExercises = 1),
        )
        // Bad pattern : virgule + espace + prénom suivi (immédiatement) d'un point ou fin
        // Ex: ", Sitou." ou ", Sitou " en fin de clause.
        val badPattern = Regex(", \\Q$name\\E([.!?]|\\s*$)")
        for (c in contexts) {
            for (rot in 0..30) {
                val phrase = WorkoutVoicePhrasebook.setStartPhrase(c, rot)
                assertThat(phrase).doesNotContainMatch(badPattern.pattern)
            }
        }
    }

    // ──────────────────────────────────────────────────────────
    // Freestyle : pas de référence à la position dans la séance
    // ──────────────────────────────────────────────────────────

    @Test
    fun `freestyle phrases ne mentionnent jamais sprint final ni dernier exo`() {
        val name = "Sitou"
        // Plein de contextes freestyle variés
        val contexts = listOf(
            ctx(name, isFreestyle = true, currentSet = 1, totalSets = 3, currentExerciseIndex = 0, totalExercises = 1),
            ctx(name, isFreestyle = true, currentSet = 3, totalSets = 3, currentExerciseIndex = 0, totalExercises = 1),
            ctx(name, isFreestyle = true, currentSet = 2, totalSets = 4, currentExerciseIndex = 1, totalExercises = 2),
            ctx(name, isFreestyle = true, currentSet = 4, totalSets = 4, currentExerciseIndex = 1, totalExercises = 2),
            // Cas piège : currentExerciseIndex == totalExercises - 1 (qui en preprogrammé
            // déclencherait "dernier exo") mais on est en freestyle → ne doit PAS firer.
            ctx(name, isFreestyle = true, currentSet = 4, totalSets = 4, currentExerciseIndex = 0, totalExercises = 1),
        )
        val forbiddenPhrases = listOf(
            "dernier exo", "sprint final", "mi-parcours", "boss final",
            "on voit la fin",
        )
        for (c in contexts) {
            for (rot in 0..30) {
                val phrase = WorkoutVoicePhrasebook.setStartPhrase(c, rot).lowercase()
                for (forbidden in forbiddenPhrases) {
                    assertThat(phrase).doesNotContain(forbidden)
                }
            }
        }
    }

    @Test
    fun `freestyle dernière série de l'exo dit dernière série pas dernier exo`() {
        val c = ctx(
            firstName = "Sitou",
            isFreestyle = true,
            currentSet = 3,
            totalSets = 3,
            currentExerciseIndex = 0,
            totalExercises = 1, // serait "dernier exo" en preprogrammé
        )
        // Au moins une rotation produit "Dernière série" — sans ajouter "dernier exo"
        val phrases = (0..20).map { WorkoutVoicePhrasebook.setStartPhrase(c, it) }
        val mentionsDerniereSerie = phrases.any { it.lowercase().contains("dernière série") }
        val mentionsDernierExo = phrases.any { it.lowercase().contains("dernier exo") }
        assertThat(mentionsDerniereSerie).isTrue()
        assertThat(mentionsDernierExo).isFalse()
    }

    // ──────────────────────────────────────────────────────────
    // Cohérence générale
    // ──────────────────────────────────────────────────────────

    @Test
    fun `phrase est non vide pour tous contextes`() {
        val cases = listOf(
            ctx("", currentSet = 1, totalSets = 3),
            ctx("Sitou", currentSet = 1, totalSets = 3),
            ctx("Champion", currentSet = 1, totalSets = 3),  // valeur "Champion" ignorée
            ctx("Sitou", isWarmup = true),
            ctx("Sitou", isCardio = true),
            ctx("Sitou", isFreestyle = true, currentSet = 1, totalSets = 0), // open-ended
        )
        for (c in cases) {
            for (rot in 0..10) {
                val phrase = WorkoutVoicePhrasebook.setStartPhrase(c, rot)
                assertThat(phrase).isNotEmpty()
            }
        }
    }

    @Test
    fun `placeholder Champion est ignoré comme prénom`() {
        val c = ctx("Champion", currentSet = 1, totalSets = 3, currentExerciseIndex = 0, totalExercises = 5)
        // Aucune phrase ne doit contenir "Champion"
        for (rot in 0..30) {
            val phrase = WorkoutVoicePhrasebook.setStartPhrase(c, rot)
            assertThat(phrase).doesNotContain("Champion")
        }
    }

    private fun ctx(
        firstName: String = "Sitou",
        exerciseName: String = "Squat",
        currentSet: Int = 2,
        totalSets: Int = 3,
        currentExerciseIndex: Int = 1,
        totalExercises: Int = 4,
        isWarmup: Boolean = false,
        isCardio: Boolean = false,
        isFreestyle: Boolean = false,
    ) = SetStartContext(
        firstName = firstName,
        exerciseName = exerciseName,
        currentSet = currentSet,
        totalSets = totalSets,
        currentExerciseIndex = currentExerciseIndex,
        totalExercises = totalExercises,
        isWarmup = isWarmup,
        isCardio = isCardio,
        isFreestyle = isFreestyle,
    )
}
