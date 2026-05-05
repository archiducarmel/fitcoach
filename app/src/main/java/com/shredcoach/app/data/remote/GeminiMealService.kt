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
            val finalPrompt = if (hintBlock.isBlank()) MEAL_PROMPT else MEAL_PROMPT + "\n" + hintBlock

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
