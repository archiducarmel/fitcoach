package com.shredcoach.app.data.remote

import android.util.Base64
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

// ═══════════════════════════════════════════════════════════════
// RÉSULTAT DE L'ANALYSE CORPORELLE
// ═══════════════════════════════════════════════════════════════

data class BodyAnalysisResult(
    val sex: String = "M",                           // "M" ou "F"
    @SerializedName("height_cm") val heightCm: Int = 0,
    @SerializedName("waist_cm") val waistCm: Int = 0,
    @SerializedName("chest_cm") val chestCm: Int = 0,
    @SerializedName("hip_cm") val hipCm: Int = 0,
    @SerializedName("arm_cm") val armCm: Int = 0,
    @SerializedName("thigh_cm") val thighCm: Int = 0,
    @SerializedName("calf_cm") val calfCm: Int = 0,
    @SerializedName("body_fat_percent") val bodyFatPercent: Int = 0,
    @SerializedName("weight_estimate_kg") val weightEstimateKg: Int = 0,
    val confidence: String = "medium",               // "low", "medium", "high"
    val notes: String = ""
)

// ═══════════════════════════════════════════════════════════════
// SERVICE MULTI-PROVIDER
// ═══════════════════════════════════════════════════════════════

@Singleton
class BodyAnalysisService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions"
        private const val GROQ_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct"
        private const val MISTRAL_MODEL = "mistral-small-latest"

        val BODY_PROMPT = """
Tu es un analyste morphologique expert, formé aux méthodes d'estimation visuelle Navy/YMCA pour le taux de gras corporel et aux tables anthropométriques standard (WHO, Tanita, InBody).

MISSION : Observe cette photo d'une personne et ESTIME AU MIEUX ses mesures corporelles avec une approche proportionnelle rigoureuse.

Retourne UNIQUEMENT un JSON valide, sans texte avant/après, sans backticks.

{
  "sex": "M",
  "height_cm": 178,
  "waist_cm": 82,
  "chest_cm": 102,
  "hip_cm": 98,
  "arm_cm": 35,
  "thigh_cm": 58,
  "calf_cm": 39,
  "body_fat_percent": 18,
  "weight_estimate_kg": 78,
  "confidence": "medium",
  "notes": "Corps entier visible, estimation proportionnelle fiable"
}

RÈGLES STRICTES :
- Toutes les valeurs en CENTIMÈTRES ENTIERS (sauf bodyFat en pourcentage entier).
- sex : "M" ou "F" uniquement.
- height_cm : basé sur les proportions tête/corps (tête ≈ 1/7.5 de la hauteur totale).
- waist_cm : tour de taille mesuré au niveau du nombril.
- chest_cm : tour de poitrine au niveau des pectoraux (point le plus large).
- hip_cm : tour de hanches au point le plus large.
- arm_cm : tour du biceps contracté estimé (point le plus large).
- thigh_cm : tour de cuisse au point le plus large.
- calf_cm : tour du mollet au point le plus large.
- body_fat_percent : méthode visuelle Navy (homme 8-30%, femme 16-38%).
- weight_estimate_kg : estimation du poids corporel total en kilogrammes entiers.
- confidence :
    "high"   = corps entier visible, posture claire, vêtements ajustés
    "medium" = buste seul OU vêtements amples OU pose imparfaite
    "low"    = cadrage difficile, beaucoup d'inconnues
- notes : 1 courte phrase en français expliquant la fiabilité.

N'invente pas : en cas d'incertitude, utilise des valeurs proportionnelles moyennes pour le sexe détecté.
Si l'image N'EST PAS un corps humain → retourne {"error": "not_body"}
""".trimIndent()
    }

    // ═══════════════════════════════════════
    // API PUBLIQUE
    // ═══════════════════════════════════════

    suspend fun analyzeBody(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI"
    ): Result<BodyAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroq(imageBytes, mimeType, apiKey)
                "MISTRAL" -> callMistral(imageBytes, mimeType, apiKey)
                else -> callGemini(imageBytes, mimeType, apiKey, model)
            }

            if (rawJson.isBlank()) return@withContext Result.failure(Exception("Analyse vide"))

            if (rawJson.contains("\"error\"") && rawJson.contains("not_body") && rawJson.length < 200) {
                return@withContext Result.failure(Exception("L'image ne contient pas de corps humain"))
            }

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

    private fun callGemini(imageBytes: ByteArray, mimeType: String, apiKey: String, model: String): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val url = "$GEMINI_BASE_URL/$model:generateContent"

        val payload = mapOf(
            "contents" to listOf(mapOf(
                "parts" to listOf(
                    mapOf("text" to BODY_PROMPT),
                    mapOf("inline_data" to mapOf("mime_type" to mimeType, "data" to b64))
                )
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
    // GROQ
    // ═══════════════════════════════════════

    private fun callGroq(imageBytes: ByteArray, mimeType: String, apiKey: String): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$b64"

        val payload = mapOf(
            "model" to GROQ_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to BODY_PROMPT),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl))
                )
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

        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // MISTRAL
    // ═══════════════════════════════════════

    private fun callMistral(imageBytes: ByteArray, mimeType: String, apiKey: String): String {
        val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val dataUrl = "data:$mimeType;base64,$b64"

        val payload = mapOf(
            "model" to MISTRAL_MODEL,
            "messages" to listOf(mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to BODY_PROMPT),
                    mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl))
                )
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

        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // PARSING ROBUSTE (fallback 3 niveaux)
    // ═══════════════════════════════════════

    private fun parseRobust(raw: String): BodyAnalysisResult? {
        val cleaned = raw.replace(Regex(",\\s*\\]"), "]").replace(Regex(",\\s*\\}"), "}")

        // Tentative 1 : strict
        try {
            val r = gson.fromJson(cleaned, BodyAnalysisResult::class.java)
            if (r != null && r.heightCm > 0) return r
        } catch (_: Exception) {}

        // Tentative 2 : lenient
        try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(cleaned))
            reader.isLenient = true
            val r = gson.getAdapter(BodyAnalysisResult::class.java).read(reader)
            if (r != null) return r
        } catch (_: Exception) {}

        // Tentative 3 : extraction manuelle
        return try {
            val root = JsonParser.parseString(cleaned).asJsonObject
            fun safeInt(k: String) = try { root.get(k)?.asInt ?: 0 } catch (_: Exception) { 0 }
            fun safeStr(k: String) = try { root.get(k)?.asString ?: "" } catch (_: Exception) { "" }
            BodyAnalysisResult(
                sex = safeStr("sex").ifBlank { "M" },
                heightCm = safeInt("height_cm"),
                waistCm = safeInt("waist_cm"),
                chestCm = safeInt("chest_cm"),
                hipCm = safeInt("hip_cm"),
                armCm = safeInt("arm_cm"),
                thighCm = safeInt("thigh_cm"),
                calfCm = safeInt("calf_cm"),
                bodyFatPercent = safeInt("body_fat_percent"),
                weightEstimateKg = safeInt("weight_estimate_kg"),
                confidence = safeStr("confidence").ifBlank { "medium" },
                notes = safeStr("notes")
            )
        } catch (_: Exception) { null }
    }
}
