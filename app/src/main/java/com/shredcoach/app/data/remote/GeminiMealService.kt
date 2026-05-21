package com.shredcoach.app.data.remote


import androidx.compose.runtime.Immutable
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════
// DATA CLASSES — résultat structuré
// ═══════════════════════════════════════

@Immutable
data class MealAnalysisResult(
    val dishes: List<AnalyzedDish> = emptyList(),
    val totalCalories: Int = 0,
    val totalProteins: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val totalFibers: Double = 0.0,
    val totalWeight: Int = 0,
    val healthScore: Int = 0,
    val verdict: String = "",
    val allergens: List<String> = emptyList(),
    val micronutrients: List<Micronutrient> = emptyList()
)

@Immutable
data class AnalyzedDish(
    val name: String = "",
    @SerializedName("meal_type") val mealType: String = "dejeuner",
    val cuisine: String = "",
    @SerializedName("weight_g") val weightG: Int = 0,
    val calories: Int = 0,
    val proteins: Double = 0.0,
    val carbs: Double = 0.0,
    @SerializedName("carbs_sugar") val carbsSugar: Double = 0.0,
    val fats: Double = 0.0,
    @SerializedName("fats_saturated") val fatsSaturated: Double = 0.0,
    val fibers: Double = 0.0,
    val salt: Double = 0.0,
    val ingredients: List<Ingredient> = emptyList()
)

data class Ingredient(
    val name: String = "",
    @SerializedName("weight_g") val weightG: Int = 0,
    val category: String = "",
    val calories: Int = 0,
    val proteins: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fibers: Double = 0.0
)

data class Micronutrient(
    val name: String = "",
    val quantity: String = "",
    @SerializedName("ajr_percent") val ajrPercent: Int = 0
)

// ═══════════════════════════════════════
// SERVICE MULTI-PROVIDER
// ═══════════════════════════════════════

@Singleton
class GeminiMealService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "MealScanner"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"
        private const val GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        private const val MISTRAL_MODEL = "mistral-small-latest"

        /**
         * Prompt pour analyse d'un repas DÉCRIT EN TEXTE par l'utilisateur
         * (cas où il a oublié de prendre la photo).
         *
         * **Différences vs MEAL_PROMPT (vision)** :
         *  - Pas d'instructions visuelles (surfaces, épaisseurs, contenants).
         *  - La description user devient AUTORITÉ ABSOLUE (aliments + quantités
         *    explicites). Pas de "deviner ce qui est sur la photo".
         *  - Heuristiques de conversion text→grams (œuf, tranche, cuillère…)
         *    pour absorber les descriptions naturelles ("2 œufs", "1 bol de riz").
         *  - Mêmes champs JSON en sortie → réutilise parseRobust + même UI.
         *
         * **Sécurité de parse** : `responseMimeType=application/json` côté Gemini,
         * `response_format=json_object` côté Groq/Mistral → JSON garanti par l'API.
         */
        val MEAL_TEXT_PROMPT = """
Tu es Shreddy, l'IA nutrition. L'utilisateur DÉCRIT son repas en texte (il
n'a pas pu prendre de photo). Ta mission : extraire les ingrédients,
estimer les quantités, calculer macros + micronutriments.

JSON valide UNIQUEMENT, sans texte ni backticks.

══ ÉTAPE 1 — LECTURE DE LA DESCRIPTION (AUTORITÉ ABSOLUE) ══
Le texte de l'utilisateur PRIME sur tes hypothèses. Identifie chaque
aliment mentionné. Si l'user dit "igname", c'est igname (pas pomme de
terre). S'il dit "thon en boîte", c'est thon en boîte (pas thon frais).

Si la description n'est PAS alimentaire (ex: "le ciel est bleu") :
→ retourne {"error": "non_food"}

Si la description est trop vague pour estimer (ex: "j'ai mangé") :
→ retourne {"error": "too_vague"}

══ ÉTAPE 2 — ESTIMATION DES QUANTITÉS ══

Quand l'user donne des quantités EXPLICITES (poids, unités, contenants),
applique-les LITTÉRALEMENT. Conversions standard :
- 1 œuf moyen ≈ 60g | 1 gros œuf ≈ 70g
- 1 tranche pain de mie ≈ 30g | 1 tranche pain complet ≈ 40g
- 1 baguette ≈ 250g | 1/2 baguette ≈ 125g
- 1 cuillère à soupe (cas) ≈ 15g (solide) ou 15ml (liquide)
- 1 cuillère à café (cac) ≈ 5g
- 1 verre standard ≈ 200ml | 1 mug ≈ 250ml | 1 canette ≈ 330ml
- 1 bol ≈ 250-350ml (riz/céréales cuits ≈ 200-280g)
- 1 yaourt ≈ 125g | 1 fromage blanc petit ≈ 100g
- 1 banane ≈ 120g | 1 pomme ≈ 150g | 1 orange ≈ 130g
- 1 portion riz/pâtes cuits ≈ 200-250g (≈ 80g cru)

Quand la description est VAGUE, infère selon le sens commun adulte :
- "une portion normale" → portion standard adulte
- "un peu de" → 30-50g (protéine), 50-80g (féculent), 30g (matière grasse)
- "beaucoup de" → +50% portion standard
- "grosse"/"généreuse" → +30% | "petite"/"légère" → −30%
- "1 assiette" sans détail → 400g total (féculent + protéine + légume)

Si l'user mentionne un plat composé sans détailler (ex: "un couscous",
"un burger") : décompose en ingrédients raisonnables d'une recette
classique, en respectant les proportions standard du plat.

══ ÉTAPE 3 — CALCUL DES MACROS ══
Pour chaque ingrédient : macros = (weight_g / 100) × valeur CIQUAL/USDA
pour 100g. Les macros du plat = somme de ses ingrédients (COHÉRENCE
OBLIGATOIRE). Les totaux = somme des plats.

Pour chaque plat, identifie le type : "petit_dejeuner", "dejeuner",
"gouter", "diner", "collation", "shaker" ou "grignotage".

JSON (mêmes champs que l'analyse photo) :
{
  "dishes": [
    {
      "name": "Nom du plat",
      "meal_type": "dejeuner",
      "cuisine": "Origine",
      "weight_g": 0,
      "calories": 0,
      "proteins": 0.0,
      "carbs": 0.0,
      "carbs_sugar": 0.0,
      "fats": 0.0,
      "fats_saturated": 0.0,
      "fibers": 0.0,
      "salt": 0.0,
      "ingredients": [
        {"name": "Ingrédient", "weight_g": 0, "category": "Cat", "calories": 0, "proteins": 0.0, "carbs": 0.0, "fats": 0.0, "fibers": 0.0}
      ]
    }
  ],
  "totalCalories": 0,
  "totalProteins": 0.0,
  "totalCarbs": 0.0,
  "totalFats": 0.0,
  "totalFibers": 0.0,
  "totalWeight": 0,
  "healthScore": 7,
  "verdict": "Analyse courte basée sur la description",
  "allergens": [],
  "micronutrients": [
    {"name": "Nutriment", "quantity": "X mg", "ajr_percent": 0}
  ]
}

Remplace tous les 0 par tes estimations RÉELLES basées sur la description.
healthScore = note 0–10 (entier). AJR EFSA 2000kcal, 8–12 micronutriments
> 5% AJR. Le verdict mentionne "analyse texte" pour signaler à l'user que
les valeurs sont basées sur sa description (sans photo).

══ DESCRIPTION DE L'UTILISATEUR ══
"{{USER_DESCRIPTION}}"
""".trimIndent()

        val MEAL_PROMPT = """
Analyse la photo de repas. JSON valide UNIQUEMENT, sans texte ni backticks.

══ ÉTAPE 1 — ESTIMATION DES QUANTITÉS RÉELLES (CRITIQUE) ══
NE DONNE JAMAIS des poids "standards" de recette (ex: toujours 200g de riz, 150g de poulet).
Tu dois estimer le poids RÉEL visible sur la photo en utilisant ces indices visuels :

RÉFÉRENCES DE TAILLE (utilise ce qui est visible sur la photo) :
- Assiette plate standard : 25-27 cm → la nourriture qui couvre la moitié = ~50% de la surface
- Cuillère à soupe : ~15 ml / ~15-20g de solide
- Fourchette : ~20 cm de long (échelle de référence)
- Verre standard : 25 cl, canette : 33 cl
- Main adulte : ~18 cm (si visible)
- Épaisseur : une couche fine (~1 cm) vs un monticule (~4-5 cm) change le poids x3-4

MÉTHODE D'ESTIMATION (applique systématiquement) :
1. Identifie le contenant et estime ses dimensions (diamètre, profondeur)
2. Évalue la SURFACE COUVERTE par chaque aliment (ex: le riz couvre 40% de l'assiette)
3. Évalue l'ÉPAISSEUR / HAUTEUR de chaque portion (fine couche vs monticule vs débordante)
4. Calcule : Volume estimé × Densité alimentaire = Poids
   - Riz cuit : ~0.7 g/cm³ | Pâtes cuites : ~0.8 g/cm³ | Viande : ~1.0 g/cm³
   - Légumes crus : ~0.3-0.5 g/cm³ | Légumes cuits : ~0.6-0.8 g/cm³
   - Sauce/liquide : ~1.0 g/cm³ | Pain : ~0.3 g/cm³ | Fromage : ~1.1 g/cm³
5. VÉRIFIE la cohérence : le poids total doit correspondre à ce que tu vois
   - Petite portion (assiette peu remplie) : 150-250g
   - Portion normale : 300-500g
   - Grande portion (assiette bien remplie, monticule) : 500-800g
   - Très grande portion (déborde, saladier plein) : 800-1200g+

⚠️ ERREUR FRÉQUENTE À ÉVITER : ne pas mettre le même poids pour une petite assiette à moitié vide et une assiette débordante. Adapte TOUJOURS les poids à ce que tu VOIS réellement.

══ ÉTAPE 2 — IDENTIFICATION ══
DÉFINITION D'UN PLAT :
- Une assiette contenant couscous + poulet + légumes = 1 SEUL plat avec 3 ingrédients
- Un plateau avec 3 assiettes séparées = 3 plats distincts
- Un plat = une unité culinaire servie ensemble dans le même contenant

Pour chaque plat, identifie le type de repas : "petit_dejeuner", "dejeuner", "gouter", "diner", "collation", "shaker" ou "grignotage".

Si image NON alimentaire : {"error": "non_food"}

══ ÉTAPE 3 — CALCUL DES MACROS ══
Pour chaque ingrédient : macros = (weight_g estimé / 100) × valeur CIQUAL/USDA pour 100g.
Les macros du plat = somme des macros de ses ingrédients (COHÉRENCE OBLIGATOIRE).

JSON :
{
  "dishes": [
    {
      "name": "Nom du plat",
      "meal_type": "dejeuner",
      "cuisine": "Origine",
      "weight_g": 0,
      "calories": 0,
      "proteins": 0.0,
      "carbs": 0.0,
      "carbs_sugar": 0.0,
      "fats": 0.0,
      "fats_saturated": 0.0,
      "fibers": 0.0,
      "salt": 0.0,
      "ingredients": [
        {"name": "Ingrédient", "weight_g": 0, "category": "Cat", "calories": 0, "proteins": 0.0, "carbs": 0.0, "fats": 0.0, "fibers": 0.0}
      ]
    }
  ],
  "totalCalories": 0,
  "totalProteins": 0.0,
  "totalCarbs": 0.0,
  "totalFats": 0.0,
  "totalFibers": 0.0,
  "totalWeight": 0,
  "healthScore": 7,
  "verdict": "Analyse courte",
  "allergens": [],
  "micronutrients": [
    {"name": "Nutriment", "quantity": "X mg", "ajr_percent": 0}
  ]
}

Remplace tous les 0 par tes estimations RÉELLES basées sur la photo. healthScore est une note de 0 à 10 (entier). AJR EFSA 2000kcal, 8-12 micronutriments > 5% AJR.
""".trimIndent()
    }

    // ═══════════════════════════════════════
    // API PUBLIQUE — dispatch par provider
    // ═══════════════════════════════════════

    /**
     * Appel multi-provider TEXTE SEUL (pas d'image). Utilisé pour la traduction d'instructions, etc.
     * Retourne le contenu texte brut.
     */
    suspend fun callTextLLM(
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val raw = when (provider.uppercase()) {
                "GROQ" -> callGroqText(apiKey, prompt)
                "MISTRAL" -> callMistralText(apiKey, prompt)
                else -> callGeminiText(apiKey, model, prompt)
            }
            if (raw.isBlank()) Result.failure(Exception("Réponse LLM vide")) else Result.success(raw)
        } catch (e: Exception) {
            Log.e(TAG, "callTextLLM failed", e)
            Result.failure(e)
        }
    }

    /**
     * Appel Gemini DÉDIÉ aux outputs JSON volumineux et structurés (analyse
     * glycémique experte, etc.).
     *
     * **Pourquoi pas réutiliser [callTextLLM]** :
     *  - `callTextLLM` cible des outputs courts (~1k tokens) et limite donc à
     *    `maxOutputTokens=2048`. C'est suffisant pour de la traduction
     *    d'instructions ou un résumé bref.
     *  - L'analyse glycémique requiert ~6 insights × ~150 tokens + verdict +
     *    summary + globalAdvice + overhead JSON → 1500-2500 tokens d'output net.
     *  - **Gemini 2.5 Flash a le mode "thinking" activé par défaut** : la
     *    réflexion CoT consomme une part importante du budget (`thoughts` parts
     *    dans la réponse). Avec `maxOutputTokens=2048`, le thinking peut
     *    facilement épuiser le budget avant que le JSON final ne soit
     *    intégralement émis → output tronqué → JSON malformé → PARSE_FAILURE
     *    systématique côté caller. **C'est exactement le bug remonté sur la
     *    fonction d'analyse Dr. Glykos.**
     *
     * **Fix structural** :
     *  - `maxOutputTokens=8192` : confortable pour un JSON multi-insights
     *  - `thinkingConfig.thinkingBudget=0` : désactive le thinking (notre
     *    prompt fournit déjà la méthode + few-shot examples, le LLM n'a pas
     *    besoin de raisonner step-by-step côté serveur — il génère direct)
     *  - `responseMimeType=application/json` : enforce JSON output côté API
     *
     * Compatible avec [GeminiOverloadInterceptor] (retry transparent sur 503).
     */
    suspend fun callJsonAnalysisLLM(
        apiKey: String,
        prompt: String,
        model: String = "gemini-2.5-flash",
        maxOutputTokens: Int = 8192,
        disableThinking: Boolean = true,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val raw = callGeminiJsonAnalysis(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                disableThinking = disableThinking,
            )
            if (raw.isBlank()) Result.failure(Exception("Réponse LLM vide"))
            else Result.success(raw)
        } catch (e: Exception) {
            Log.e(TAG, "callJsonAnalysisLLM failed", e)
            Result.failure(e)
        }
    }

    private fun callGeminiJsonAnalysis(
        apiKey: String,
        model: String,
        prompt: String,
        maxOutputTokens: Int,
        disableThinking: Boolean,
    ): String {
        val url = "$GEMINI_BASE_URL/$model:generateContent"
        // Gemini 3.x deprecate temperature/top_p/top_k → on les retire (per
        // Google recommendation officielle). 2.x les supporte toujours.
        val isGemini3Plus = model.startsWith("gemini-3.") || model.startsWith("gemini-3-")
        val generationConfig = mutableMapOf<String, Any>(
            "maxOutputTokens" to maxOutputTokens,
            "responseMimeType" to "application/json",
        )
        if (!isGemini3Plus) {
            generationConfig["temperature"] = 0.4
        }
        if (disableThinking) {
            // BREAKING CHANGE Gemini 3.x : thinkingBudget (numeric) deprecated
            // → thinkingLevel enum {LOW, MEDIUM, HIGH}. Pas de "OFF" possible
            // (tested via POC, retourne 400 INVALID_ARGUMENT). On utilise LOW
            // sur 3.x (~620 thinking tokens, ~3× moins que default) et
            // thinkingBudget=0 sur 2.x (vraiment 0 thinking).
            generationConfig["thinkingConfig"] = if (isGemini3Plus) {
                mapOf("thinkingLevel" to "LOW")
            } else {
                mapOf("thinkingBudget" to 0)
            }
        }
        val payload = mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(mapOf("text" to prompt))
            )),
            "generationConfig" to generationConfig,
        )
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) {
            val errorMsg = try {
                JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Gemini ${response.code}"
            throw Exception(errorMsg)
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val candidates = json.getAsJsonArray("candidates") ?: throw Exception("Aucun résultat")
        if (candidates.size() == 0) throw Exception("Aucun résultat Gemini")
        val finishReason = candidates[0].asJsonObject.get("finishReason")?.asString
        if (finishReason != null && finishReason != "STOP") {
            Log.w(TAG, "Gemini analysis non-STOP finishReason=$finishReason")
        }
        val parts = candidates[0].asJsonObject.getAsJsonObject("content")?.getAsJsonArray("parts")
        val textParts = parts?.filter { it.asJsonObject.has("text") && !it.asJsonObject.has("thought") }
            ?.mapNotNull { it.asJsonObject.get("text")?.asString } ?: emptyList()
        val raw = textParts.joinToString("").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        Log.d(TAG, "Analysis LLM raw output: ${raw.length} chars, finishReason=$finishReason")
        return raw
    }

    private fun callGeminiText(apiKey: String, model: String, prompt: String): String {
        val url = "$GEMINI_BASE_URL/$model:generateContent"
        val payload = mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(mapOf("text" to prompt))
            )),
            "generationConfig" to mapOf(
                "temperature" to 0.2,
                "maxOutputTokens" to 2048,
                "responseMimeType" to "application/json"
            )
        )
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) {
            val errorMsg = try {
                JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Gemini ${response.code}"
            throw Exception(errorMsg)
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val candidates = json.getAsJsonArray("candidates") ?: throw Exception("Aucun résultat")
        if (candidates.size() == 0) throw Exception("Aucun résultat Gemini")
        val parts = candidates[0].asJsonObject.getAsJsonObject("content")?.getAsJsonArray("parts")
        val textParts = parts?.filter { it.asJsonObject.has("text") && !it.asJsonObject.has("thought") }
            ?.mapNotNull { it.asJsonObject.get("text")?.asString } ?: emptyList()
        return textParts.joinToString("").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callGroqText(apiKey: String, prompt: String): String {
        val payload = mapOf(
            "model" to GROQ_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to prompt
            )),
            "max_completion_tokens" to 2048,
            "temperature" to 0.2,
            "response_format" to mapOf("type" to "json_object")
        )
        val request = Request.Builder()
            .url(GROQ_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) throw Exception("Erreur Groq ${response.code}")
        val json = JsonParser.parseString(responseBody).asJsonObject
        val raw = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString ?: throw Exception("Réponse Groq vide")
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callMistralText(apiKey: String, prompt: String): String {
        val payload = mapOf(
            "model" to MISTRAL_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to prompt
            )),
            "max_tokens" to 2048,
            "temperature" to 0.2,
            "response_format" to mapOf("type" to "json_object")
        )
        val request = Request.Builder()
            .url(MISTRAL_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) throw Exception("Erreur Mistral ${response.code}")
        val json = JsonParser.parseString(responseBody).asJsonObject
        val raw = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString ?: throw Exception("Réponse Mistral vide")
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    /**
     * Appel multi-provider générique avec un prompt arbitraire. Retourne le JSON brut (non parsé).
     * Utilisé par les features qui ont leur propre schéma de réponse (GymScan, etc.).
     */
    suspend fun callVisionLLM(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroq(imageBytes, mimeType, apiKey, prompt)
                "MISTRAL" -> callMistral(imageBytes, mimeType, apiKey, prompt)
                else -> callGemini(imageBytes, mimeType, apiKey, model, prompt)
            }
            if (rawJson.isBlank()) Result.failure(Exception("Réponse LLM vide"))
            else Result.success(rawJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Analyse un repas DÉCRIT EN TEXTE par l'utilisateur (cas où il a oublié
     * de prendre la photo). Mêmes garanties que [analyzeMeal] (vision) :
     * dispatch multi-provider, parsing robuste à 4 niveaux, schéma JSON
     * identique. Le caller obtient un [MealAnalysisResult] interchangeable
     * avec celui d'une analyse photo → la pipeline DB + UI reste la même.
     *
     * **Sécurité d'entrée** : la description est validée côté ViewModel
     * (non-vide, longueur raisonnable). Ici on inject simplement dans le prompt.
     */
    suspend fun analyzeMealFromText(
        description: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        hintBlock: String = ""
    ): Result<MealAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // Substitution simple via `replace` (PAS String.format) : le prompt
            // contient des `%` littéraux ("+50% portion", "5% AJR"…) qui
            // déclencheraient un IllegalFormatConversionException si on
            // utilisait String.format. `replace(CharSequence, CharSequence)`
            // n'interprète aucun caractère spécial → safe pour toute
            // description (y compris avec `$`, `\`, `%`, accents, emojis).
            val basePrompt = MEAL_TEXT_PROMPT.replace("{{USER_DESCRIPTION}}", description.trim())
            // Append du bloc d'indices contenant (assiette/bol) si l'user en a renseigné.
            val withHints = if (hintBlock.isBlank()) basePrompt else basePrompt + "\n" + hintBlock
            // i18n : préfixe une directive d'override de langue de sortie quand l'app
            // est en EN. Le LLM continue de lire le spec FR mais produit les champs
            // texte (noms de plats, verdict, etc.) dans la langue utilisateur.
            val finalPrompt = com.shredcoach.app.domain.i18n.PromptLocale.outputLanguageDirective() + withHints

            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroqTextMeal(apiKey, finalPrompt)
                "MISTRAL" -> callMistralTextMeal(apiKey, finalPrompt)
                else -> callGeminiTextMeal(apiKey, model, finalPrompt)
            }

            if (rawJson.isBlank()) return@withContext Result.failure(Exception("Analyse vide"))

            // Cas erreurs structurées du LLM (description non alimentaire ou trop vague)
            if (rawJson.contains("\"error\"") && rawJson.length < 200) {
                val errMsg = when {
                    rawJson.contains("non_food") -> "La description ne correspond pas à un aliment"
                    rawJson.contains("too_vague") -> "Description trop vague — précise les aliments et quantités"
                    else -> "Description non analysable"
                }
                return@withContext Result.failure(Exception(errMsg))
            }

            // Réutilise le pipeline de parsing robuste de l'analyse photo
            val result = parseRobust(rawJson)
            if (result == null) {
                val preview = rawJson.take(300).replace("\n", "↵")
                return@withContext Result.failure(Exception("Parsing échoué. Début réponse: $preview"))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeMeal(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        hintBlock: String = ""
    ): Result<MealAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // Construit le prompt final : prompt de base + bloc d'indices utilisateur (vide si aucun indice)
            val withHints = if (hintBlock.isBlank()) MEAL_PROMPT else MEAL_PROMPT + "\n" + hintBlock
            // i18n : directive d'override de langue (cf. analyzeMealFromText).
            val finalPrompt = com.shredcoach.app.domain.i18n.PromptLocale.outputLanguageDirective() + withHints

            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroq(imageBytes, mimeType, apiKey, finalPrompt)
                "MISTRAL" -> callMistral(imageBytes, mimeType, apiKey, finalPrompt)
                else -> callGemini(imageBytes, mimeType, apiKey, model, finalPrompt)
            }

            if (rawJson.isBlank()) return@withContext Result.failure(Exception("Analyse vide"))

            // Vérifier erreur image non alimentaire
            if (rawJson.contains("\"error\"") && rawJson.length < 200) {
                return@withContext Result.failure(Exception("Image non alimentaire"))
            }

            // Parser avec le même pipeline robuste pour les 3 providers
            val result = parseRobust(rawJson)
            if (result == null) {
                val preview = rawJson.take(300).replace("\n", "↵")
                return@withContext Result.failure(Exception("Parsing échoué. Début réponse: $preview"))
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════
    // TEXT MEAL — appels API sans image
    // ═══════════════════════════════════════
    //
    // Pourquoi des méthodes dédiées (vs réutiliser callTextLLM) : l'analyse
    // d'un repas en texte produit le MÊME schéma JSON que l'analyse photo
    // (dishes + ingredients + micronutriments) → maxTokens doit être élevé
    // (~16k pour Gemini, 4k pour Groq/Mistral). callTextLLM utilise 2048,
    // ce qui tronquerait souvent la réponse pour les repas riches.

    private fun callGeminiTextMeal(apiKey: String, model: String, prompt: String): String {
        val url = "$GEMINI_BASE_URL/$model:generateContent"
        val payload = mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(mapOf("text" to prompt))
            )),
            "generationConfig" to mapOf(
                "temperature" to 0.3,
                "maxOutputTokens" to 16384,
                "responseMimeType" to "application/json"
            )
        )
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) {
            val errorMsg = try {
                JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Gemini ${response.code}"
            throw Exception(errorMsg)
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val candidates = json.getAsJsonArray("candidates") ?: throw Exception("Aucun résultat")
        if (candidates.size() == 0) throw Exception("Aucun résultat Gemini")
        val parts = candidates[0].asJsonObject.getAsJsonObject("content")?.getAsJsonArray("parts")
        val textParts = parts?.filter { it.asJsonObject.has("text") && !it.asJsonObject.has("thought") }
            ?.mapNotNull { it.asJsonObject.get("text")?.asString } ?: emptyList()
        return textParts.joinToString("").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callGroqTextMeal(apiKey: String, prompt: String): String {
        val payload = mapOf(
            "model" to GROQ_MODEL,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_completion_tokens" to 4096,
            "temperature" to 0.3,
            "response_format" to mapOf("type" to "json_object")
        )
        val request = Request.Builder()
            .url(GROQ_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JsonParser.parseString(responseBody).asJsonObject
                errJson.getAsJsonObject("error")?.get("message")?.asString
                    ?: errJson.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Groq ${response.code}"
            throw Exception(errorMsg)
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val raw = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString
            ?: throw Exception("Réponse Groq vide")
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callMistralTextMeal(apiKey: String, prompt: String): String {
        val payload = mapOf(
            "model" to MISTRAL_MODEL,
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "max_tokens" to 4096,
            "temperature" to 0.3,
            "response_format" to mapOf("type" to "json_object")
        )
        val request = Request.Builder()
            .url(MISTRAL_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")
        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JsonParser.parseString(responseBody).asJsonObject
                errJson.get("message")?.asString
                    ?: errJson.getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Mistral ${response.code}"
            throw Exception(errorMsg)
        }
        val json = JsonParser.parseString(responseBody).asJsonObject
        val raw = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString
            ?: throw Exception("Réponse Mistral vide")
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // GEMINI
    // ═══════════════════════════════════════

    private fun callGemini(imageBytes: ByteArray, mimeType: String, apiKey: String, model: String, prompt: String = MEAL_PROMPT): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val url = "$GEMINI_BASE_URL/$model:generateContent"

        val payload = mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(
                    mapOf("text" to prompt),
                    mapOf("inline_data" to mapOf("mime_type" to mimeType, "data" to b64))
                )
            )),
            "generationConfig" to mapOf(
                "temperature" to 0.3,
                "maxOutputTokens" to 16384,
                "responseMimeType" to "application/json"
            )
        )

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Gemini ${response.code}"
            throw Exception(errorMsg)
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val candidates = json.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) throw Exception("Aucun résultat Gemini")

        val parts = candidates[0].asJsonObject
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")

        val textParts = parts?.filter {
            it.asJsonObject.has("text") && !it.asJsonObject.has("thought")
        }?.mapNotNull { it.asJsonObject.get("text")?.asString } ?: emptyList()

        var rawJson = textParts.joinToString("").trim()
        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // GROQ (Llama 4 Scout — OpenAI-compatible)
    // ═══════════════════════════════════════

    private fun callGroq(imageBytes: ByteArray, mimeType: String, apiKey: String, prompt: String = MEAL_PROMPT): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$b64"

        val payload = mapOf(
            "model" to GROQ_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to prompt),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl))
                )
            )),
            "max_completion_tokens" to 4096,
            "temperature" to 0.3,
            "response_format" to mapOf("type" to "json_object")
        )

        val request = Request.Builder()
            .url(GROQ_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JsonParser.parseString(responseBody).asJsonObject
                errJson.getAsJsonObject("error")?.get("message")?.asString
                    ?: errJson.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Groq ${response.code}"
            throw Exception(errorMsg)
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val rawJson = json.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw Exception("Réponse Groq vide")

        Log.d(TAG, "Groq usage: ${json.getAsJsonObject("usage")}")
        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // MISTRAL (Mistral Small — OpenAI-compatible)
    // ═══════════════════════════════════════

    private fun callMistral(imageBytes: ByteArray, mimeType: String, apiKey: String, prompt: String = MEAL_PROMPT): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$b64"

        val payload = mapOf(
            "model" to MISTRAL_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to prompt),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl))
                )
            )),
            "max_tokens" to 4096,
            "temperature" to 0.3,
            "response_format" to mapOf("type" to "json_object")
        )

        val request = Request.Builder()
            .url(MISTRAL_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Réponse vide")

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errJson = JsonParser.parseString(responseBody).asJsonObject
                errJson.get("message")?.asString
                    ?: errJson.getAsJsonObject("error")?.get("message")?.asString
            } catch (_: Exception) { null } ?: "Erreur Mistral ${response.code}"
            throw Exception(errorMsg)
        }

        val json = JsonParser.parseString(responseBody).asJsonObject
        val rawJson = json.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw Exception("Réponse Mistral vide")

        Log.d(TAG, "Mistral usage: ${json.getAsJsonObject("usage")}")
        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // PARSING ROBUSTE (commun aux 3 providers)
    // ═══════════════════════════════════════

    private fun parseRobust(raw: String): MealAnalysisResult? {
        Log.d(TAG, "=== RAW JSON (${raw.length} chars) ===")
        Log.d(TAG, raw.take(2000))

        val repaired = repairTruncatedJson(raw)
        if (repaired != raw) Log.d(TAG, "JSON réparé (${repaired.length} chars)")

        // Tentative 1 : parse direct
        try {
            val r = gson.fromJson(repaired, MealAnalysisResult::class.java)
            Log.d(TAG, "✓ Tentative 1 (strict) OK")
            return r
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 1: ${e.message}")
        }

        // Tentative 2 : nettoyage des strings puis parse
        val cleaned = sanitizeJsonStrings(repaired)
        try {
            val r = gson.fromJson(cleaned, MealAnalysisResult::class.java)
            Log.d(TAG, "✓ Tentative 2 (sanitized) OK")
            return r
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 2: ${e.message}")
        }

        // Tentative 3 : Gson avec JsonReader lenient
        try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(cleaned))
            reader.isLenient = true
            val r = gson.getAdapter(MealAnalysisResult::class.java).read(reader)
            Log.d(TAG, "✓ Tentative 3 (lenient) OK")
            return r
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 3: ${e.message}")
        }

        // Tentative 4 : extraction manuelle
        try {
            val r = extractManual(cleaned)
            if (r != null) Log.d(TAG, "✓ Tentative 4 (manual) OK — ${r.dishes.size} plats")
            return r
        } catch (e: Exception) {
            Log.e(TAG, "✗ Tentative 4: ${e.message}", e)
        }

        return null
    }

    private fun repairTruncatedJson(json: String): String {
        var trimmed = json.trimEnd()
        while (trimmed.endsWith(",") || trimmed.endsWith(":")) {
            trimmed = trimmed.dropLast(1).trimEnd()
        }
        val stack = mutableListOf<Char>()
        var inString = false; var escaped = false
        for (c in trimmed) {
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && (c == '{' || c == '[') -> stack.add(c)
                !inString && c == '}' -> { if (stack.lastOrNull() == '{') stack.removeLastOrNull() }
                !inString && c == ']' -> { if (stack.lastOrNull() == '[') stack.removeLastOrNull() }
            }
        }
        if (inString) trimmed += "\""
        val sb = StringBuilder(trimmed)
        for (open in stack.reversed()) {
            val last = sb.toString().trimEnd()
            if (last.endsWith(",")) {
                sb.setLength(0)
                sb.append(last.dropLast(1))
            }
            sb.append(if (open == '{') "}" else "]")
        }
        return sb.toString()
    }

    private fun sanitizeJsonStrings(json: String): String {
        val sb = StringBuilder(json.length)
        var inString = false
        var escaped = false
        for (i in json.indices) {
            val c = json[i]
            when {
                escaped -> { sb.append(c); escaped = false }
                c == '\\' && inString -> { sb.append(c); escaped = true }
                c == '"' -> { inString = !inString; sb.append(c) }
                inString && (c == '\n' || c == '\r' || c == '\t') -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
            .replace(Regex(",\\s*\\]"), "]")
            .replace(Regex(",\\s*\\}"), "}")
    }

    private fun extractManual(json: String): MealAnalysisResult? {
        val root = try { JsonParser.parseString(json).asJsonObject } catch (_: Exception) { return null }

        fun safeInt(key: String) = try { root.get(key)?.asInt ?: 0 } catch (_: Exception) { 0 }
        fun safeDouble(key: String) = try { root.get(key)?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 }
        fun safeString(key: String) = try { root.get(key)?.asString ?: "" } catch (_: Exception) { "" }

        val dishes = try {
            root.getAsJsonArray("dishes")?.map { dishEl ->
                val d = dishEl.asJsonObject
                AnalyzedDish(
                    name = try { d.get("name")?.asString ?: "" } catch (_: Exception) { "" },
                    mealType = try { d.get("meal_type")?.asString ?: "dejeuner" } catch (_: Exception) { "dejeuner" },
                    cuisine = try { d.get("cuisine")?.asString ?: "" } catch (_: Exception) { "" },
                    weightG = try { d.get("weight_g")?.asInt ?: 0 } catch (_: Exception) { 0 },
                    calories = try { d.get("calories")?.asInt ?: 0 } catch (_: Exception) { 0 },
                    proteins = try { d.get("proteins")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    carbs = try { d.get("carbs")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    carbsSugar = try { d.get("carbs_sugar")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    fats = try { d.get("fats")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    fatsSaturated = try { d.get("fats_saturated")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    fibers = try { d.get("fibers")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    salt = try { d.get("salt")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                    ingredients = try {
                        d.getAsJsonArray("ingredients")?.map { ingEl ->
                            val ig = ingEl.asJsonObject
                            Ingredient(
                                name = try { ig.get("name")?.asString ?: "" } catch (_: Exception) { "" },
                                weightG = try { ig.get("weight_g")?.asInt ?: 0 } catch (_: Exception) { 0 },
                                category = try { ig.get("category")?.asString ?: "" } catch (_: Exception) { "" },
                                calories = try { ig.get("calories")?.asInt ?: 0 } catch (_: Exception) { 0 },
                                proteins = try { ig.get("proteins")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                                carbs = try { ig.get("carbs")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                                fats = try { ig.get("fats")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 },
                                fibers = try { ig.get("fibers")?.asDouble ?: 0.0 } catch (_: Exception) { 0.0 }
                            )
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val micros = try {
            root.getAsJsonArray("micronutrients")?.map { mEl ->
                val m = mEl.asJsonObject
                Micronutrient(
                    name = try { m.get("name")?.asString ?: "" } catch (_: Exception) { "" },
                    quantity = try { m.get("quantity")?.asString ?: "" } catch (_: Exception) { "" },
                    ajrPercent = try { m.get("ajr_percent")?.asInt ?: 0 } catch (_: Exception) { 0 }
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        val allergens = try {
            root.getAsJsonArray("allergens")?.map { it.asString } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        return MealAnalysisResult(
            dishes = dishes,
            totalCalories = safeInt("totalCalories"),
            totalProteins = safeDouble("totalProteins"),
            totalCarbs = safeDouble("totalCarbs"),
            totalFats = safeDouble("totalFats"),
            totalFibers = safeDouble("totalFibers"),
            totalWeight = safeInt("totalWeight"),
            healthScore = safeInt("healthScore").let { if (it > 10) (it / 10).coerceIn(0, 10) else it.coerceIn(0, 10) },
            verdict = safeString("verdict"),
            allergens = allergens,
            micronutrients = micros
        )
    }
}
