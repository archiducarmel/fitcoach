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
            val body = response.body?.string() ?: throw Exception("Reponse vide")
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} : ${body.take(200)}")
            }
            val json = JsonParser.parseString(body).asJsonObject
            val data = json.getAsJsonArray("data") ?: throw Exception("Pas de data[]")
            val ids = data.mapNotNull { it.asJsonObject.get("id")?.asString }.toSet()

            cachedAccessibleIds = ids
            cachedAtMs = now
            Log.i(TAG, "NVIDIA NIM catalog : ${ids.size} IDs accessibles, ${NvidiaNimCatalog.ALL_MODELS.size} editorialises")
            val filtered = filterByAccessible(ids)
            Log.i(TAG, "Intersection : ${filtered.size} modeles affichables")
            Result.success(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "fetchCatalog failed", e)
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
