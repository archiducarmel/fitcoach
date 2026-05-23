package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.JsonParser
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.NvidiaNimCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service NVIDIA NIM : fetch `/v1/models` pour determiner les modeles
 * accessibles a la cle API, puis intersection avec le catalogue editorialise
 * [NvidiaNimCatalog].
 *
 * **Difference cle avec GitHub Models** :
 *  - GitHub `/catalog/models` retourne TOUTES les metadata
 *  - NVIDIA `/v1/models` retourne SEULEMENT les IDs `{"data": [{"id": "..."}]}`
 *  - Donc on maintient les metadata cote app dans [NvidiaNimCatalog]
 *  - On affiche au user uniquement les modeles editorialises ET accessibles
 *
 * Cache 24h TTL (similaire a GitHubModelsCatalogService).
 */
@Singleton
class NvidiaNimCatalogService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
) {

    private val client = baseClient.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedAccessibleIds: Set<String>? = null
    @Volatile private var cachedAtMs: Long = 0L
    private val cacheTtlMs = 24L * 60 * 60 * 1000

    /**
     * Fetch les IDs accessibles et retourne le catalogue editorialise filtre.
     *
     * @param apiKey cle nvapi-xxx
     * @param forceRefresh bypass cache
     * @return List<LlmModelInfo> editorialises ET accessibles. Tri preserve
     *   l'ordre du catalogue (Reasoning d'abord, puis Generalists, etc.).
     */
    suspend fun fetchCatalog(
        apiKey: String,
        forceRefresh: Boolean = false,
    ): Result<List<LlmModelInfo>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Cle NVIDIA manquante"))
        val now = System.currentTimeMillis()
        val cached = cachedAccessibleIds
        if (!forceRefresh && cached != null && (now - cachedAtMs) < cacheTtlMs) {
            return@withContext Result.success(filterByAccessible(cached))
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Réponse vide")
            if (!response.isSuccessful) {
                // Friendly error mapping
                val friendly = when (response.code) {
                    401 -> "Clé NVIDIA invalide (HTTP 401). Vérifie le format nvapi-xxx."
                    403 -> "Clé NVIDIA sans accès au catalogue (HTTP 403). Vérifie le scope."
                    404 -> "Endpoint /v1/models introuvable (HTTP 404)."
                    429 -> "Quota NVIDIA dépassé (HTTP 429). Réessaie plus tard."
                    in 500..599 -> "Serveur NVIDIA indisponible (HTTP ${response.code})."
                    else -> "HTTP ${response.code}"
                }
                throw Exception(friendly)
            }
            // Parsing defensif : si la structure differe (NVIDIA peut renvoyer un
            // wrapper different selon les tiers de cle), on tente plusieurs paths.
            val json = JsonParser.parseString(body).asJsonObject
            val dataArr = json.getAsJsonArray("data")
                ?: json.getAsJsonArray("models")
                ?: throw Exception("Format inattendu (pas de data[] ni models[])")
            val ids = dataArr.mapNotNull { el ->
                runCatching {
                    if (el == null || !el.isJsonObject) null
                    else el.asJsonObject.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
            }.toSet()

            cachedAccessibleIds = ids
            cachedAtMs = now
            Log.i(TAG, "NVIDIA NIM : ${ids.size} IDs accessibles, ${NvidiaNimCatalog.ALL_MODELS.size} editorialises")
            val filtered = filterByAccessible(ids)
            Log.i(TAG, "Intersection : ${filtered.size} modeles affichables")

            // Resilience : si l'intersection est vide (ex: les IDs NVIDIA ont
            // un prefix different de notre catalogue), on FALLBACK sur le catalogue
            // editorialise complet plutot que de laisser le user sans aucun
            // modele. La cle aura ete validee par le 200 OK du /v1/models.
            if (filtered.isEmpty() && ids.isNotEmpty()) {
                Log.w(TAG, "Intersection vide — fallback sur le catalogue editorialise complet")
                Result.success(NvidiaNimCatalog.ALL_MODELS)
            } else {
                Result.success(filtered)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCatalog failed", e)
            // Pour les erreurs reseau pures (timeout, no internet), on fallback
            // egalement sur le catalogue editorialise (l'user pourra tester chaque
            // modele individuellement — l'erreur reseau se reverra a l'envoi).
            if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException) {
                Log.w(TAG, "Erreur reseau — fallback catalogue editorialise complet")
                return@withContext Result.success(NvidiaNimCatalog.ALL_MODELS)
            }
            Result.failure(e)
        }
    }

    /** Invalide le cache (bouton refresh UI). */
    fun invalidateCache() {
        cachedAccessibleIds = null
        cachedAtMs = 0L
    }

    /**
     * Filtre les editorialises pour ne garder que ceux dont l'ID est dans
     * la liste accessible a la cle de l'utilisateur. Preserve l'ordre du
     * catalogue (categories Python : Reasoning > Generalist > Coding > Small
     * > Specialized > Multilingual).
     */
    private fun filterByAccessible(accessibleIds: Set<String>): List<LlmModelInfo> =
        NvidiaNimCatalog.ALL_MODELS.filter { it.id in accessibleIds }

    companion object {
        private const val TAG = "NvidiaNimCatalog"
        private const val BASE_URL = "https://integrate.api.nvidia.com/v1"
    }
}
