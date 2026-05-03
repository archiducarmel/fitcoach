package com.shredcoach.app.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service d'image generation via Gemini (image-capable models).
 *
 * Prend une image RGB (photo corporelle du user) et génère une version mesh/wireframe
 * futuriste (hologramme sci-fi, fil de fer néon cyan sur fond noir).
 *
 * Les modèles Gemini avec image output évoluent rapidement. On utilise une CHAÎNE de fallback :
 * on essaie le modèle préféré, puis les alternatives si le premier n'est pas disponible
 * (erreur 404 "model not found" selon le tier/région API de l'utilisateur).
 */
@Singleton
class BodyMeshService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "BodyMeshService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /**
         * Chaîne de fallback : modèles Gemini Nano Banana actuels (avril 2026).
         *
         * - gemini-3.1-flash-image-preview = Nano Banana 2 (lancé 26 fév 2026, free tier 5000/mois)
         * - gemini-2.5-flash-image         = Nano Banana original (stable, baseline)
         * - gemini-3-pro-image-preview     = Nano Banana Pro (premium uniquement, ~$0.15/image)
         *
         * L'ordre suit : le plus récent et gratuit → stable → payant en dernier recours.
         */
        val FALLBACK_MODELS = listOf(
            "gemini-3.1-flash-image-preview",  // Nano Banana 2 (free tier 5000/mois)
            "gemini-2.5-flash-image",          // Nano Banana 1 (stable baseline)
            "gemini-3-pro-image-preview"       // Nano Banana Pro (fallback payant)
        )

        const val DEFAULT_MODEL = "gemini-3.1-flash-image-preview"

        val MESH_PROMPT = """
Transform this body photo into a futuristic sci-fi wireframe mesh visualization.

REQUIREMENTS:
- Keep the EXACT same body pose, proportions, and silhouette as the input
- Render the body as a polygonal 3D mesh structure (triangular wireframe)
- Use neon cyan/turquoise lines (#00E5FF, #00FFB9) for the wireframe
- Anatomical grid overlay covering the entire body contour
- Background: deep black (#000000) with subtle dark blue gradient
- Add scan line effects and subtle holographic glow around the silhouette
- Visual style: Tron Legacy, Ghost in the Shell, medical body scanner, cyberpunk hologram
- Perfectly centered composition
- No text, no labels, no numbers
- Pure visual representation — just the body mesh on a void background
- Keep the face contour visible but as wireframe only (no skin texture)
- Add subtle particle/data effects in the background for depth

Return ONLY the generated image, no text.
        """.trimIndent()
    }

    /**
     * Génère une image mesh à partir d'une photo corporelle.
     * Tente une chaîne de modèles en fallback si le premier n'est pas disponible.
     *
     * @param preferredModel Modèle à essayer en priorité (default = DEFAULT_MODEL).
     *                        Si null/blank, utilise la chaîne complète.
     * @return Result<ByteArray> contenant les octets PNG de l'image générée, ou une erreur
     *         décrivant le dernier échec de la chaîne.
     */
    suspend fun generateMesh(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        preferredModel: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        // Construire la chaîne : préféré en premier, puis fallbacks (dédupliqués)
        val chain = buildList {
            if (!preferredModel.isNullOrBlank()) add(preferredModel)
            FALLBACK_MODELS.forEach { if (it !in this) add(it) }
        }

        val errors = mutableListOf<String>()
        for (model in chain) {
            Log.d(TAG, "Tentative avec modèle : $model")
            val result = tryGenerateOnce(imageBytes, mimeType, apiKey, model)
            result.fold(
                onSuccess = { return@withContext Result.success(it) },
                onFailure = { e ->
                    val msg = e.message ?: "erreur inconnue"
                    Log.w(TAG, "Échec modèle $model : $msg")
                    errors.add("$model → $msg")
                    if (msg.contains("API key", ignoreCase = true)
                        || msg.contains("authentication", ignoreCase = true)
                        || msg.contains("permission", ignoreCase = true)
                        || msg.contains("quota", ignoreCase = true)
                        || msg.contains("429")
                    ) {
                        return@withContext Result.failure(Exception("Échec : $msg"))
                    }
                }
            )
        }

        // Tous les modèles ont échoué → appeler ListModels pour diagnostiquer
        val available = try { listAvailableImageModels(apiKey) } catch (_: Exception) { emptyList() }
        val diagnostic = buildString {
            append("Aucun modèle d'image gen disponible avec ta clé API.\n\n")
            append("Tentatives :\n")
            errors.forEach { append("• $it\n") }
            if (available.isNotEmpty()) {
                append("\nModèles image-capables disponibles sur ton tier :\n")
                available.forEach { append("• $it\n") }
                append("\n→ Contacte le dev pour ajouter ces modèles à la chaîne de fallback.")
            } else {
                append("\nImpossible de récupérer la liste des modèles disponibles. ")
                append("Vérifie que ta clé API a accès aux modèles Gemini Nano Banana (image gen).")
            }
        }
        Log.e(TAG, diagnostic)
        Result.failure(Exception(diagnostic))
    }

    /**
     * Appelle l'endpoint ListModels de Gemini pour récupérer la liste des modèles supportant
     * `generateContent` ET contenant "image" dans le nom (= image generation capable).
     */
    private fun listAvailableImageModels(apiKey: String): List<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models"
        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return emptyList()
        if (!response.isSuccessful) return emptyList()

        val json = JsonParser.parseString(body).asJsonObject
        val models = json.getAsJsonArray("models") ?: return emptyList()
        val imageModels = mutableListOf<String>()

        for (m in models) {
            try {
                val obj = m.asJsonObject
                val name = obj.get("name")?.asString ?: continue  // "models/gemini-xxx"
                val shortName = name.removePrefix("models/")

                // Filtre : nom contient "image" (nano banana) ET supporte generateContent
                val supportedMethods = obj.getAsJsonArray("supportedGenerationMethods")
                    ?.map { it.asString } ?: emptyList()

                if ("image" in shortName.lowercase() && "generateContent" in supportedMethods) {
                    imageModels.add(shortName)
                }
            } catch (_: Exception) {}
        }
        Log.d(TAG, "ListModels → ${imageModels.size} image-capable models : $imageModels")
        return imageModels
    }

    /** Tente UNE génération avec un modèle donné. */
    private fun tryGenerateOnce(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String
    ): Result<ByteArray> {
        return try {
            val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val url = "$BASE_URL/$model:generateContent"

            // Les modèles Nano Banana (gemini-*-flash-image*) retournent l'image par défaut
            // quand on leur passe un prompt text + une image input. Pas besoin de
            // responseModalities — ça pouvait causer des refus selon le modèle.
            val payload = mapOf(
                "contents" to listOf(mapOf(
                    "parts" to listOf(
                        mapOf("text" to MESH_PROMPT),
                        mapOf("inline_data" to mapOf("mime_type" to mimeType, "data" to b64))
                    )
                ))
            )

            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Réponse vide"))

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JsonParser.parseString(responseBody).asJsonObject
                        .getAsJsonObject("error")?.get("message")?.asString
                } catch (_: Exception) { null } ?: "Erreur ${response.code}"
                return Result.failure(Exception(errorMsg))
            }

            // Extraire les octets de l'image générée
            val json = JsonParser.parseString(responseBody).asJsonObject
            val candidates = json.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                return Result.failure(Exception("Aucun candidate"))
            }

            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?: return Result.failure(Exception("Pas de parts"))

            for (part in parts) {
                val partObj = part.asJsonObject
                val inlineData = partObj.getAsJsonObject("inlineData")
                    ?: partObj.getAsJsonObject("inline_data")

                if (inlineData != null) {
                    val dataStr = inlineData.get("data")?.asString
                    if (!dataStr.isNullOrBlank()) {
                        val bytes = Base64.decode(dataStr, Base64.DEFAULT)
                        return Result.success(bytes)
                    }
                }
            }
            Result.failure(Exception("Aucune inline_data dans les parts"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
