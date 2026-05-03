package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-ranker LLM-texte qui affine le matching heuristique de [GymScanMatcher].
 *
 * Pipeline :
 *  1. Le matcher pré-filtre ~30 candidats pertinents (équipement/muscle/nom)
 *  2. Ce re-ranker passe ces candidats + le contexte vision à un LLM texte
 *  3. Le LLM raisonne sémantiquement et retourne les 6 meilleurs IDs ordonnés
 *
 * Pourquoi un LLM texte plutôt qu'étendre le prompt vision ?
 *  - Le LLM vision coûte plus cher, mieux le garder léger
 *  - Le LLM texte (Groq Llama, Gemini Flash, Mistral) est rapide et peu coûteux
 *  - Le re-ranking peut appeler un modèle **différent** du modèle vision
 *
 * Sécurité anti-hallucination :
 *  - Les IDs retournés par le LLM sont validés contre la liste des candidats
 *  - Tout ID inconnu est filtré
 *  - Si <3 IDs valides reçus → fallback sur le top 6 heuristique
 */
@Singleton
class GymScanReranker @Inject constructor(
    private val textLlmClient: GeminiMealService
) {

    companion object {
        private const val TAG = "GymScan-Rerank"
        private const val FINAL_TOP_N = 6
        private const val MIN_VALID_IDS = 3
    }

    /**
     * Re-classe les [candidates] selon la pertinence pour [visionResult].
     * Garde le matcher heuristique comme fallback si le LLM échoue.
     *
     * @param candidates Top 20-30 issus du matcher heuristique
     * @return les 6 meilleurs exercices, ordonnés par pertinence décroissante
     */
    suspend fun rerank(
        visionResult: GymScanResult,
        candidates: List<ExerciseDbExercise>,
        apiKey: String,
        model: String,
        provider: String
    ): Result<List<ExerciseDbExercise>> {
        if (candidates.isEmpty()) return Result.success(emptyList())
        if (candidates.size <= FINAL_TOP_N) return Result.success(candidates) // pas besoin de rerank
        if (apiKey.isBlank()) {
            Log.w(TAG, "Clé API absente → fallback heuristique top-6")
            return Result.success(candidates.take(FINAL_TOP_N))
        }

        Log.i(TAG, "→ Rerank ${candidates.size} candidats via $provider")
        val t0 = System.currentTimeMillis()

        val prompt = buildPrompt(visionResult, candidates)
        Log.d(TAG, "Prompt taille : ${prompt.length} chars")

        val rawResult = textLlmClient.callTextLLM(
            apiKey = apiKey,
            model = model,
            provider = provider,
            prompt = prompt
        )
        val raw = rawResult.getOrElse { err ->
            Log.w(TAG, "LLM rerank échoué, fallback heuristique : ${err.message}")
            return Result.success(candidates.take(FINAL_TOP_N))
        }
        Log.i(TAG, "← LLM rerank en ${System.currentTimeMillis() - t0}ms (${raw.length} chars)")

        val selectedIds = parseSelectedIds(raw)
        if (selectedIds == null) {
            Log.w(TAG, "Parse rerank échoué, fallback heuristique")
            return Result.success(candidates.take(FINAL_TOP_N))
        }

        // Validation anti-hallucination : les IDs doivent exister dans les candidats
        val candidateIdSet = candidates.map { it.id }.toSet()
        val validIds = selectedIds.filter { it in candidateIdSet }
        Log.d(TAG, "IDs LLM : ${selectedIds.size} reçus, ${validIds.size} valides")

        if (validIds.size < MIN_VALID_IDS) {
            Log.w(TAG, "Seulement ${validIds.size} IDs valides (min $MIN_VALID_IDS), fallback heuristique")
            return Result.success(candidates.take(FINAL_TOP_N))
        }

        // Reconstitue la liste ordonnée par le LLM
        val candidateById = candidates.associateBy { it.id }
        val reranked = validIds.take(FINAL_TOP_N).mapNotNull { candidateById[it] }

        // Complète éventuellement avec des candidats heuristiques si le LLM en a oublié
        val result = if (reranked.size < FINAL_TOP_N) {
            val existing = reranked.map { it.id }.toSet()
            val fillers = candidates.filter { it.id !in existing }.take(FINAL_TOP_N - reranked.size)
            reranked + fillers
        } else reranked

        Log.i(TAG, "✓ Rerank OK : ${result.size} exos finaux (top='${result.first().name}')")
        return Result.success(result)
    }

    // ─────────────────────────────────────────────
    // Construction du prompt
    // ─────────────────────────────────────────────

    private fun buildPrompt(vision: GymScanResult, candidates: List<ExerciseDbExercise>): String {
        // Contexte vision
        val machineBlock = buildString {
            appendLine("MACHINE PHOTOGRAPHIÉE PAR L'UTILISATEUR :")
            appendLine("  • Nom identifié : ${vision.machineName}")
            if (vision.equipmentType.isNotBlank()) appendLine("  • Type technique : ${vision.equipmentType}")
            appendLine("  • Équipement (catégorie DB) : ${vision.equipmentKeyword.ifBlank { "(non spécifié)" }}")
            if (vision.primaryMuscles.isNotEmpty()) appendLine("  • Muscles primaires : ${vision.primaryMuscles.joinToString(", ")}")
            if (vision.secondaryMuscles.isNotEmpty()) appendLine("  • Muscles secondaires : ${vision.secondaryMuscles.joinToString(", ")}")
            if (vision.difficulty.isNotBlank()) appendLine("  • Niveau : ${vision.difficulty}")
            if (vision.description.isNotBlank()) appendLine("  • Description vision : ${vision.description}")
            if (vision.exerciseSearchHints.isNotEmpty())
                appendLine("  • Hints recherche : ${vision.exerciseSearchHints.joinToString(", ")}")
        }

        // Candidats pré-filtrés (format compact pour limiter les tokens)
        val candidatesBlock = buildString {
            appendLine("CANDIDATS PRÉ-FILTRÉS (${candidates.size} exercices parmi les 873 de la base) :")
            candidates.forEachIndexed { i, ex ->
                val eq = ex.equipment ?: "?"
                val pm = ex.primaryMuscles.joinToString(",")
                val sm = if (ex.secondaryMuscles.isNotEmpty()) " | sec=${ex.secondaryMuscles.joinToString(",")}" else ""
                val cat = if (ex.category.isNotBlank()) " | cat=${ex.category}" else ""
                val lvl = if (ex.level.isNotBlank()) " | lvl=${ex.level}" else ""
                appendLine("${i + 1}. id=${ex.id} | ${ex.name} | eq=$eq | pm=$pm$sm$cat$lvl")
            }
        }

        return """
Tu es coach sportif expert, spécialiste de l'équipement de salle de musculation.

TÂCHE : l'utilisateur a pris en photo une machine ; une IA vision a déjà identifié son type.
Ton rôle est de SÉLECTIONNER parmi une liste de candidats pré-filtrés les exercices qui
correspondent le mieux à CETTE machine spécifique. Tu raisonnes sémantiquement, pas par mots-clés.

$machineBlock

$candidatesBlock

═══ CRITÈRES DE SÉLECTION (par ordre de priorité) ═══

1. **COHÉRENCE ÉQUIPEMENT (priorité absolue)**
   - Si l'équipement vision est "machine" → ne choisis QUE des exercices avec eq=machine
   - Exception : si aucun match parfait, équipement "cable" est acceptable pour beaucoup de machines
   - JAMAIS de "bands" ou "body only" si la photo montre clairement une machine/barre/câble

2. **SPÉCIFICITÉ DU MOUVEMENT**
   - Préfère l'exercice le plus précis à la variante générique
   - Exemple : "Machine Adducteur" → "Thigh Adductor" ≫ "Lever Hip Adduction" ≫ autres
   - "Leg Press 45°" → "Leg Press" ≫ "Machine Squat"
   - "Lat Pulldown" → "Wide Grip Lat Pulldown" ~ "Lat Pulldown" ≫ "Cable Row"

3. **DIVERSITÉ DES VARIANTES**
   - Inclus 3-4 exercices CŒUR (même mouvement, variantes mineures)
   - + 2-3 variantes intéressantes (grip différent, position différente, progression)
   - Évite les doublons quasi-identiques

4. **PROGRESSION PÉDAGOGIQUE**
   - Si possible, inclus au moins 1 variante beginner et 1 intermédiaire/expert

═══ FORMAT DE RÉPONSE ═══

Retourne UNIQUEMENT ce JSON (aucun texte autour, aucun markdown) :

{
  "selectedIds": ["id_du_meilleur", "id_2nd", "id_3rd", "id_4th", "id_5th", "id_6th"],
  "reasoning": "1 phrase en français expliquant ta sélection"
}

CONTRAINTES :
- Exactement 6 IDs, ordonnés du plus pertinent au moins pertinent
- Les IDs DOIVENT figurer dans la liste CANDIDATS ci-dessus (pas d'invention)
- Si la liste candidats a moins de 6 entrées, retourne-les toutes dans l'ordre pertinent
- reasoning : bref, en français
""".trimIndent()
    }

    // ─────────────────────────────────────────────
    // Parsing de la réponse
    // ─────────────────────────────────────────────

    private fun parseSelectedIds(raw: String): List<String>? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val root = JsonParser.parseString(cleaned).asJsonObject
            val arr = root.getAsJsonArray("selectedIds") ?: return null
            arr.mapNotNull { runCatching { it.asString }.getOrNull() }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "parseSelectedIds fail: ${e.message}")
            null
        }
    }
}
