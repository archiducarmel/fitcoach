package com.shredcoach.app.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Score de pertinence + classement des exercices du dataset free-exercise-db
 * selon les hints du LLM (GymScanResult).
 *
 * Barème équilibré (l'équipement a la priorité pour éviter qu'un "Band Hip Adduction"
 * gagne contre un "Thigh Adductor" quand l'utilisateur photographie une MACHINE) :
 *
 *   - Match de phrase complète d'un hint dans le nom : **+15** par hint trouvé
 *   - Match mot-à-mot (chaque mot du hint dans le nom) : **+2** par mot
 *   - Match exact de l'équipement : **+15**
 *   - Équipement mismatch : **−5** (pénalité)
 *   - Chaque muscle primaire en commun : **+3**
 *   - Niveau de difficulté match : **+2**
 *
 * Les exos à score ≤ 0 sont filtrés. Top N (par défaut 6) retournés triés descendant.
 */
@Singleton
class GymScanMatcher @Inject constructor(
    private val exerciseDbService: ExerciseDbService
) {

    companion object {
        private const val TAG = "GymScan-Match"
        /** Top N finaux quand le matcher est utilisé SEUL (sans reranker). */
        private const val DEFAULT_TOP_N = 6
        /** Pool pré-filtré pour le reranker LLM (plus large pour donner du choix au LLM). */
        const val RERANKER_POOL_SIZE = 30

        private const val WEIGHT_PHRASE_IN_NAME = 15
        private const val WEIGHT_WORD_IN_NAME = 2
        private const val WEIGHT_EQUIPMENT_MATCH = 15
        private const val WEIGHT_EQUIPMENT_MISMATCH = -5
        private const val WEIGHT_MUSCLE_MATCH = 3
        private const val WEIGHT_LEVEL_MATCH = 2

        /** Mots à ignorer pour le scoring mot-à-mot (articles, prépositions EN). */
        private val STOP_WORDS = setOf(
            "a", "an", "the", "in", "on", "of", "to", "with", "and", "or",
            "for", "from", "by", "at", "up", "down"
        )
    }

    /**
     * Lance le matching. Charge le dataset si pas encore en cache.
     * @return liste triée des exos les plus pertinents (max [topN]).
     */
    suspend fun findMatches(hints: GymScanResult, topN: Int = DEFAULT_TOP_N): Result<List<ExerciseDbExercise>> {
        val datasetResult = exerciseDbService.filterExercises()
        val dataset = datasetResult.getOrElse {
            Log.e(TAG, "Dataset load failed: ${it.message}")
            return Result.failure(it)
        }
        Log.d(TAG, "Dataset dispo : ${dataset.size} exos, hints=${hints.exerciseSearchHints}, eq=${hints.equipmentKeyword}")

        val searchPhrases = hints.exerciseSearchHints.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val searchWords = searchPhrases.flatMap { it.split(Regex("\\s+")) }
            .filter { it.length > 2 && it !in STOP_WORDS }
            .toSet()
        val targetMuscles = hints.primaryMuscles.map { it.lowercase().trim() }.toSet()
        val targetEquipment = hints.equipmentKeyword.lowercase().trim().takeIf { it.isNotBlank() }
        val targetLevel = hints.difficulty.lowercase().trim().takeIf { it.isNotBlank() }

        val scored = dataset.asSequence()
            .map { ex ->
                val score = scoreExercise(ex, searchPhrases, searchWords, targetMuscles, targetEquipment, targetLevel)
                ex to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { (_, score) -> score }
            .take(topN)
            .map { (ex, score) ->
                Log.v(TAG, "  → ${ex.name} (eq=${ex.equipment}) score=$score")
                ex
            }
            .toList()

        Log.i(TAG, "✓ Matching : ${scored.size} exos pertinents sélectionnés (top score cible: équipement=$targetEquipment)")
        return Result.success(scored)
    }

    private fun scoreExercise(
        ex: ExerciseDbExercise,
        searchPhrases: List<String>,
        searchWords: Set<String>,
        targetMuscles: Set<String>,
        targetEquipment: String?,
        targetLevel: String?
    ): Int {
        var score = 0
        val nameLower = ex.name.lowercase()
        val exEquipment = ex.equipment?.lowercase()

        // Match phrase complète : forte pertinence (nom de l'exo contient le hint entier)
        searchPhrases.forEach { phrase ->
            if (phrase in nameLower) score += WEIGHT_PHRASE_IN_NAME
        }

        // Match mot-à-mot : pertinence modérée (flexible sur l'ordre)
        val nameWords = nameLower.split(Regex("\\s+|[-/]")).toSet()
        searchWords.forEach { word ->
            if (word in nameWords) score += WEIGHT_WORD_IN_NAME
        }

        // Équipement : match fortement récompensé, mismatch pénalisé (empêche Band/Machine confusion)
        if (targetEquipment != null) {
            score += when {
                exEquipment == targetEquipment -> WEIGHT_EQUIPMENT_MATCH
                exEquipment == null -> 0 // inconnu, neutre
                else -> WEIGHT_EQUIPMENT_MISMATCH
            }
        }

        // Muscle principal commun
        ex.primaryMuscles.forEach { muscle ->
            if (muscle.lowercase() in targetMuscles) score += WEIGHT_MUSCLE_MATCH
        }

        // Niveau de difficulté
        if (targetLevel != null && ex.level.lowercase() == targetLevel) {
            score += WEIGHT_LEVEL_MATCH
        }

        return score
    }
}
