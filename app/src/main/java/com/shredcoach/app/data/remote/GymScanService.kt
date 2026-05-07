package com.shredcoach.app.data.remote


import androidx.compose.runtime.Immutable
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════
// DTO — résultat structuré de l'analyse machine
// ═══════════════════════════════════════

@Immutable
data class GymScanResult(
    @SerializedName("isGymEquipment") val isGymEquipment: Boolean = true,
    @SerializedName("machineName") val machineName: String = "",
    @SerializedName("equipmentType") val equipmentType: String = "",
    @SerializedName("confidence") val confidence: Int = 0,
    @SerializedName("description") val description: String = "",
    @SerializedName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
    @SerializedName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
    @SerializedName("difficulty") val difficulty: String = "",
    @SerializedName("safetyTips") val safetyTips: List<String> = emptyList(),
    @SerializedName("setupSteps") val setupSteps: List<String> = emptyList(),
    @SerializedName("equipmentKeyword") val equipmentKeyword: String = "",
    /** IDs sélectionnés par le LLM directement depuis le dataset injecté (approche RAG inline). */
    @SerializedName("selectedExerciseIds") val selectedExerciseIds: List<String> = emptyList(),
    /**
     * Termes de recherche textuels (compat legacy : utilisé par le fallback [GymScanMatcher]).
     * N'est PAS peuplé par le nouveau prompt inline-catalog, reste à la valeur par défaut.
     */
    @SerializedName("exerciseSearchHints") val exerciseSearchHints: List<String> = emptyList()
)

// ═══════════════════════════════════════
// SERVICE — pipeline 1-call : vision + matching sémantique
// ═══════════════════════════════════════

/**
 * Service GymScan à UN SEUL APPEL LLM :
 * Le catalogue complet des 873 exercices free-exercise-db est injecté dans le prompt vision.
 * Le LLM raisonne globalement et retourne identification + IDs sélectionnés en même temps.
 *
 * Avantages vs pipeline 3-étages (vision + matcher heuristique + reranker texte) :
 *  - 1 seul appel → pas de double latence
 *  - Le LLM voit la photo ET le catalogue : matching 100% sémantique sans heuristique fragile
 *  - Plus de confusion Machine/Bands : le LLM comprend visuellement le type d'équipement
 *  - Pas de sur-filtrage préalable qui exclurait le vrai match
 *
 * Coût : prompt plus lourd (~40KB de dataset) mais modèles modernes (Gemini Flash 1M,
 * Groq Llama 10M, Mistral 128K) n'ont aucun souci avec ça.
 */
@Singleton
class GymScanService @Inject constructor(
    private val visionClient: GeminiMealService
) {

    private val gson = Gson()

    companion object {
        private const val TAG = "GymScan"
        private const val TARGET_MATCH_COUNT = 6
    }

    /**
     * Analyse une photo de machine + matche avec le dataset EN UN SEUL APPEL LLM vision.
     *
     * @param dataset Liste complète des exos (source de vérité — le LLM choisira parmi eux)
     */
    suspend fun analyzeMachine(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        dataset: List<ExerciseDbExercise>
    ): Result<GymScanResult> {
        Log.i(TAG, "analyzeMachine → ${dataset.size} exos injectés, provider=$provider, image=${imageBytes.size}B")
        val t0 = System.currentTimeMillis()

        val prompt = buildPromptWithDataset(dataset)
        Log.d(TAG, "Prompt taille : ${prompt.length} chars (~${prompt.length / 4} tokens)")

        val rawResult = visionClient.callVisionLLM(
            imageBytes = imageBytes,
            mimeType = mimeType,
            apiKey = apiKey,
            model = model,
            provider = provider,
            prompt = prompt
        )
        val raw = rawResult.getOrElse {
            Log.e(TAG, "LLM call failed: ${it.message}")
            return Result.failure(it)
        }
        Log.i(TAG, "← LLM répondu en ${System.currentTimeMillis() - t0}ms (${raw.length} chars)")

        val result = parseResult(raw).getOrElse {
            return Result.failure(it)
        }

        // Validation anti-hallucination : les IDs doivent exister dans le dataset
        val datasetIdSet = dataset.map { it.id }.toSet()
        val validatedIds = result.selectedExerciseIds.filter { it in datasetIdSet }
        val invalidCount = result.selectedExerciseIds.size - validatedIds.size
        if (invalidCount > 0) {
            Log.w(TAG, "⚠ $invalidCount IDs hallucinés filtrés (LLM a inventé des IDs)")
        }
        Log.i(TAG, "✓ ${result.machineName} (${result.confidence}%) · ${validatedIds.size} exos valides")

        return Result.success(result.copy(selectedExerciseIds = validatedIds))
    }

    // ─────────────────────────────────────────────
    // PROMPT — identification + sélection en une passe
    // ─────────────────────────────────────────────

    private fun buildPromptWithDataset(dataset: List<ExerciseDbExercise>): String {
        // Groupement PAR MUSCLE PRINCIPAL : navigation mentale naturelle pour le LLM
        // Chaque exo apparaît dans les sections de TOUS ses muscles primaires (rare >1)
        val byMuscle = linkedMapOf<String, MutableList<ExerciseDbExercise>>()
        dataset.forEach { ex ->
            val muscles = ex.primaryMuscles.ifEmpty { listOf("(non classé)") }
            muscles.forEach { m -> byMuscle.getOrPut(m) { mutableListOf() }.add(ex) }
        }
        // Trie les sections par ordre alphabétique pour cohérence
        val sortedMuscles = byMuscle.keys.sorted()

        val catalog = buildString {
            sortedMuscles.forEach { muscle ->
                val exos = byMuscle[muscle] ?: return@forEach
                appendLine()
                appendLine("═══ MUSCLE: $muscle (${exos.size} exercices) ═══")
                exos.forEach { ex ->
                    val eq = ex.equipment ?: "-"
                    val sm = ex.secondaryMuscles.joinToString(",").ifBlank { "-" }
                    appendLine("@${ex.id}|${ex.name}|eq=$eq|sm=$sm")
                }
            }
        }

        return com.shredcoach.app.domain.i18n.PromptLocale.outputLanguageDirective() + """
Tu es coach sportif expert, spécialiste de l'équipement de salle de musculation.
L'utilisateur a pris en photo une machine. Tu as DEUX tâches :

TÂCHE A — Identifier la machine et les muscles travaillés
TÂCHE B — Sélectionner 6 exercices du CATALOGUE qui enseignent le MÊME MOUVEMENT

═══════════════════════════════════════════════════════════════════════════════
CATALOGUE (${dataset.size} exercices, groupés par MUSCLE PRINCIPAL) :
═══════════════════════════════════════════════════════════════════════════════
$catalog
═══════════════════════════════════════════════════════════════════════════════

⚠️ Si l'image n'est PAS un équipement de sport : retourne {"isGymEquipment": false}

Sinon, retourne UNIQUEMENT ce JSON (aucun texte autour, aucun markdown) :

{
  "isGymEquipment": true,
  "machineName": "Nom précis (ex: 'Hip Thrust Machine', 'Machine Adducteur', 'Leg Press 45°')",
  "equipmentType": "Catégorie technique",
  "confidence": 0-100,
  "description": "2-3 phrases FR sur le mouvement et son intérêt",
  "primaryMuscles": ["glutes"],
  "secondaryMuscles": ["hamstrings"],
  "difficulty": "beginner | intermediate | expert",
  "safetyTips": ["..."],
  "setupSteps": ["..."],
  "equipmentKeyword": "machine",
  "selectedExerciseIds": ["id_du_meilleur", "...", "id_6"]
}

═══════════════════════════════════════════════════════════════════════════════
🎯 RÈGLE D'OR POUR selectedExerciseIds : MUSCLE > MOUVEMENT > ÉQUIPEMENT
═══════════════════════════════════════════════════════════════════════════════

**Étape 1** — Identifie le ou les MUSCLES PRINCIPAUX ciblés par la machine vue sur la photo.

**Étape 2** — Va LIRE UNIQUEMENT la/les section(s) MUSCLE correspondante(s) du catalogue.
   Ex : machine pour glutes → lire SEULEMENT la section "MUSCLE: glutes"

**Étape 3** — Dans cette section, choisis les exos qui enseignent le MÊME MOUVEMENT.
   Peu importe que `eq=barbell`, `eq=bands`, `eq=body only` ou `eq=machine` :
   si le mouvement est identique (hip thrust, adduction, extension…) c'est bon.

⚠️ NE CHOISIS JAMAIS un exo en DEHORS de la section du muscle identifié.
   L'équipement est secondaire, le MUSCLE est non-négociable.

═══════════════════════════════════════════════════════════════════════════════
📚 EXEMPLES CONCRETS (avec IDs RÉELS du catalogue)
═══════════════════════════════════════════════════════════════════════════════

📸 Photo "Hip Thrust Machine" (machine pour fessiers)
   → muscle = glutes → section MUSCLE: glutes
   → 6 IDs à choisir dans CETTE section :
     ["Barbell_Hip_Thrust", "Barbell_Glute_Bridge", "Hip_Lift_with_Band",
      "Single_Leg_Glute_Bridge", "Pull_Through", "Butt_Lift_Bridge"]
   ❌ NE PAS choisir "Reverse_Hyperextension" (pm=hamstrings, hors section glutes)

📸 Photo "Machine Adducteur"
   → muscle = adductors → section MUSCLE: adductors
   → IDs à choisir : ["Thigh_Adductor", "Band_Hip_Adductions", "Adductor",
                      "Adductor_Groin", "Lying_Bent_Leg_Groin", ...]
   Priorité à `Thigh_Adductor` (eq=machine = parfait match équipement)

📸 Photo "Leg Press 45°"
   → muscle principal = quadriceps → section MUSCLE: quadriceps
   → chercher "leg press" puis autres exos quads

📸 Photo "Lat Pulldown Machine"
   → muscle = lats → section MUSCLE: lats
   → chercher "pulldown", "pull-up", etc.

═══════════════════════════════════════════════════════════════════════════════
🚫 ANTI-PATTERNS (erreurs graves à éviter)
═══════════════════════════════════════════════════════════════════════════════

❌ Choisir un exo parce que `eq=machine` alors que son pm= est DIFFÉRENT du muscle identifié
   → "Reverse_Hyperextension" a eq=machine MAIS pm=hamstrings, donc JAMAIS pour une machine glute

❌ Choisir un exo juste parce qu'il est dans le même registre (bas du corps, haut du corps)
   → La machine Pec Deck ne matche PAS avec les exos de dos, même si "haut du corps"

❌ Inventer un ID qui n'est pas dans le catalogue
   → Toujours vérifier l'ID dans les sections muscles ci-dessus avant de l'inclure

═══════════════════════════════════════════════════════════════════════════════
AUTRES CONTRAINTES
═══════════════════════════════════════════════════════════════════════════════

- primaryMuscles / secondaryMuscles : EXCLUSIVEMENT parmi {abdominals, abductors, adductors,
  biceps, calves, chest, forearms, glutes, hamstrings, lats, lower back, middle back, neck,
  quadriceps, shoulders, traps, triceps}
- equipmentKeyword : UNE valeur parmi {bands, barbell, body only, cable, dumbbell,
  e-z curl bar, exercise ball, foam roll, kettlebells, machine, medicine ball, other}
- description, safetyTips (3 entrées), setupSteps (3 entrées) : FRANÇAIS
- Exactement 6 IDs dans selectedExerciseIds (ou moins si la section muscle contient <6 exos)
""".trimIndent()
    }

    // ─────────────────────────────────────────────
    // PARSING
    // ─────────────────────────────────────────────

    private fun parseResult(raw: String): Result<GymScanResult> {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        // Tentative 1 : parse direct
        try {
            val r = gson.fromJson(cleaned, GymScanResult::class.java)
            if (!r.isGymEquipment) {
                return Result.failure(Exception("La photo ne semble pas être un équipement de sport"))
            }
            return Result.success(r)
        } catch (e: Exception) {
            Log.w(TAG, "Parse strict échoué : ${e.message}, tentative lenient…")
        }

        // Tentative 2 : parse lenient
        try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(cleaned))
            reader.isLenient = true
            val r = gson.getAdapter(GymScanResult::class.java).read(reader)
            if (!r.isGymEquipment) {
                return Result.failure(Exception("La photo ne semble pas être un équipement de sport"))
            }
            return Result.success(r)
        } catch (e: Exception) {
            Log.w(TAG, "Parse lenient échoué : ${e.message}, extraction manuelle…")
        }

        // Tentative 3 : extraction manuelle
        try {
            val root = JsonParser.parseString(cleaned).asJsonObject
            val isGym = root.get("isGymEquipment")?.asBoolean ?: true
            if (!isGym) return Result.failure(Exception("La photo ne semble pas être un équipement de sport"))

            fun str(k: String) = try { root.get(k)?.asString.orEmpty() } catch (_: Exception) { "" }
            fun int(k: String) = try { root.get(k)?.asInt ?: 0 } catch (_: Exception) { 0 }
            fun list(k: String): List<String> = try {
                root.getAsJsonArray(k)?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val r = GymScanResult(
                isGymEquipment = true,
                machineName = str("machineName"),
                equipmentType = str("equipmentType"),
                confidence = int("confidence"),
                description = str("description"),
                primaryMuscles = list("primaryMuscles"),
                secondaryMuscles = list("secondaryMuscles"),
                difficulty = str("difficulty"),
                safetyTips = list("safetyTips"),
                setupSteps = list("setupSteps"),
                equipmentKeyword = str("equipmentKeyword"),
                selectedExerciseIds = list("selectedExerciseIds")
            )
            return Result.success(r)
        } catch (e: Exception) {
            Log.e(TAG, "Toutes tentatives parse échouées", e)
            val preview = cleaned.take(200).replace("\n", "↵")
            return Result.failure(Exception("Parsing impossible. Début réponse: $preview"))
        }
    }
}
