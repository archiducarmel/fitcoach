package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════
// DTO — schéma yuhonas/free-exercise-db
// (873 exercices, GitHub raw, 100% gratuit, illimité)
// ═══════════════════════════════════════

/**
 * Exercice du dataset free-exercise-db.
 * Source : https://github.com/yuhonas/free-exercise-db (CC0 public domain)
 *
 * @property id        Identifiant slug (ex: "Barbell_Bench_Press")
 * @property name      Nom lisible (ex: "Barbell Bench Press")
 * @property force     "pull" | "push" | "static" | null
 * @property level     "beginner" | "intermediate" | "expert"
 * @property mechanic  "compound" | "isolation" | null
 * @property equipment "bands" | "barbell" | "body only" | "cable" | "dumbbell" | "e-z curl bar" |
 *                     "exercise ball" | "foam roll" | "kettlebells" | "machine" | "medicine ball" | "other"
 * @property primaryMuscles   17 valeurs : abdominals, abductors, adductors, biceps, calves, chest,
 *                            forearms, glutes, hamstrings, lats, lower back, middle back, neck,
 *                            quadriceps, shoulders, traps, triceps
 * @property category  "strength" | "cardio" | "olympic weightlifting" | "plyometrics" |
 *                     "powerlifting" | "stretching" | "strongman"
 * @property images    Chemins relatifs (ex: ["3_4_Sit-Up/0.jpg", "3_4_Sit-Up/1.jpg"])
 *                     URL absolue construite via [imageUrl].
 */
data class ExerciseDbExercise(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("force") val force: String? = null,
    @SerializedName("level") val level: String = "",
    @SerializedName("mechanic") val mechanic: String? = null,
    @SerializedName("equipment") val equipment: String? = null,
    @SerializedName("primaryMuscles") val primaryMuscles: List<String> = emptyList(),
    @SerializedName("secondaryMuscles") val secondaryMuscles: List<String> = emptyList(),
    @SerializedName("instructions") val instructions: List<String> = emptyList(),
    @SerializedName("category") val category: String = "",
    @SerializedName("images") val images: List<String> = emptyList()
) {
    /** URL absolue de la première image (position de départ). */
    val firstImageUrl: String
        get() = images.firstOrNull()?.let { ExerciseDbService.imageUrl(it) } ?: ""

    /** URL absolue de la deuxième image (position d'arrivée), si présente. */
    val secondImageUrl: String
        get() = images.getOrNull(1)?.let { ExerciseDbService.imageUrl(it) } ?: ""

    val primaryMuscle: String get() = primaryMuscles.firstOrNull().orEmpty()
}

/** Listes de filtres dynamiques (extraites du dataset à l'init). */
data class ExerciseDbMeta(
    val muscles: List<String> = emptyList(),
    val equipments: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val levels: List<String> = emptyList()
)

// ═══════════════════════════════════════
// SERVICE — fetch + cache mémoire + filtrage côté client
// ═══════════════════════════════════════

@Singleton
class ExerciseDbService @Inject constructor(
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val cacheMutex = Mutex()

    /** Cache mémoire — chargé une fois par session. */
    @Volatile private var cachedExercises: List<ExerciseDbExercise>? = null
    @Volatile private var cachedMeta: ExerciseDbMeta? = null

    init {
        Log.i(TAG, "★ ExerciseDbService instancié (Singleton Hilt)")
    }

    companion object {
        const val TAG = "ExoDB"
        /** Dataset complet (1MB, 873 exos) — GitHub raw, infiniment stable. */
        const val DATASET_URL =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
        /** Préfixe pour construire les URLs d'images depuis les chemins relatifs. */
        private const val IMAGE_BASE_URL =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

        /** Construit l'URL absolue d'une image depuis son chemin relatif. */
        fun imageUrl(relativePath: String): String = IMAGE_BASE_URL + relativePath
    }

    // ───────────────────────────────────────
    // Chargement initial (lazy + thread-safe)
    // ───────────────────────────────────────

    /** Force un rechargement du dataset (utile pour bouton refresh manuel). */
    suspend fun reloadDataset(): Result<List<ExerciseDbExercise>> = withContext(Dispatchers.IO) {
        cacheMutex.withLock { cachedExercises = null; cachedMeta = null }
        loadDataset()
    }

    /** Charge le dataset complet (avec cache). Idempotent et thread-safe. */
    private suspend fun loadDataset(): Result<List<ExerciseDbExercise>> = withContext(Dispatchers.IO) {
        cachedExercises?.let {
            Log.d(TAG, "loadDataset: HIT cache (${it.size} exos)")
            return@withContext Result.success(it)
        }
        Log.i(TAG, "loadDataset: cache MISS, acquisition mutex…")
        cacheMutex.withLock {
            cachedExercises?.let {
                Log.d(TAG, "loadDataset: HIT cache après mutex (${it.size} exos)")
                return@withContext Result.success(it)
            }
            try {
                Log.i(TAG, "→ GET $DATASET_URL")
                val t0 = System.currentTimeMillis()
                val request = Request.Builder()
                    .url(DATASET_URL)
                    .header("Accept", "application/json")
                    .header("User-Agent", "ShredCoach-Android/1.0")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val tFetch = System.currentTimeMillis() - t0
                    Log.i(TAG, "← HTTP ${response.code} (${response.message}) en ${tFetch}ms")
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string().orEmpty().take(200)
                        Log.e(TAG, "HTTP non-OK, body début: $errBody")
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code} — ${response.message}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    Log.i(TAG, "Body reçu : ${body.length} bytes (${body.length / 1024} KB)")
                    if (body.isBlank()) {
                        Log.e(TAG, "Body vide !")
                        return@withContext Result.failure(Exception("Réponse vide du serveur"))
                    }
                    val t1 = System.currentTimeMillis()
                    val list = gson.fromJson(body, Array<ExerciseDbExercise>::class.java).toList()
                    val tParse = System.currentTimeMillis() - t1
                    cachedExercises = list
                    cachedMeta = buildMeta(list)
                    Log.i(TAG, "✓ Dataset parsé : ${list.size} exos en ${tParse}ms (total: ${System.currentTimeMillis() - t0}ms)")
                    Log.d(TAG, "Meta : ${cachedMeta?.muscles?.size} muscles, ${cachedMeta?.equipments?.size} equipments, ${cachedMeta?.categories?.size} categories, ${cachedMeta?.levels?.size} levels")
                    if (list.isNotEmpty()) {
                        val first = list.first()
                        Log.d(TAG, "Premier exo : id=${first.id}, name=${first.name}, img=${first.firstImageUrl}")
                    }
                    Result.success(list)
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ loadDataset EXCEPTION: ${e.javaClass.simpleName} — ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private fun buildMeta(list: List<ExerciseDbExercise>): ExerciseDbMeta {
        val muscles = list.flatMap { it.primaryMuscles }.toSortedSet().toList()
        val equipments = list.mapNotNull { it.equipment }.toSortedSet().toList()
        val categories = list.map { it.category }.filter { it.isNotBlank() }.toSortedSet().toList()
        val levels = list.map { it.level }.filter { it.isNotBlank() }.toSortedSet().toList()
        return ExerciseDbMeta(muscles, equipments, categories, levels)
    }

    // ───────────────────────────────────────
    // API publique
    // ───────────────────────────────────────

    /**
     * Filtre le dataset complet côté client (instantané — tout est en mémoire).
     * Tous les paramètres sont optionnels. Les chaînes sont matchées en case-insensitive.
     */
    suspend fun filterExercises(
        search: String? = null,
        muscle: String? = null,
        equipment: String? = null,
        category: String? = null,
        level: String? = null
    ): Result<List<ExerciseDbExercise>> {
        Log.d(TAG, "filterExercises(search=$search, muscle=$muscle, equipment=$equipment, category=$category, level=$level)")
        val datasetResult = loadDataset()
        val dataset = datasetResult.getOrElse {
            Log.e(TAG, "filterExercises: loadDataset FAILED — ${it.message}")
            return Result.failure(it)
        }

        val q = search?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val mu = muscle?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val eq = equipment?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val ca = category?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        val lv = level?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        val filtered = dataset.asSequence()
            .filter { ex -> q == null || ex.name.lowercase().contains(q) }
            .filter { ex -> mu == null || ex.primaryMuscles.any { it.lowercase() == mu } }
            .filter { ex -> eq == null || ex.equipment?.lowercase() == eq }
            .filter { ex -> ca == null || ex.category.lowercase() == ca }
            .filter { ex -> lv == null || ex.level.lowercase() == lv }
            .toList()

        Log.d(TAG, "filterExercises: ${filtered.size} résultats sur ${dataset.size} total")
        return Result.success(filtered)
    }

    /** Récupère un exercice par son id depuis le cache (ou charge le dataset si nécessaire). */
    suspend fun getExerciseById(id: String): Result<ExerciseDbExercise> {
        val datasetResult = loadDataset()
        val dataset = datasetResult.getOrElse { return Result.failure(it) }
        val ex = dataset.firstOrNull { it.id == id }
            ?: return Result.failure(NoSuchElementException("Exercice introuvable : $id"))
        return Result.success(ex)
    }

    /** Récupère les listes de valeurs uniques pour les filtres (depuis le cache). */
    suspend fun getMeta(): Result<ExerciseDbMeta> {
        loadDataset().getOrElse { return Result.failure(it) }
        return Result.success(cachedMeta ?: ExerciseDbMeta())
    }

    /**
     * Retourne l'intégralité du dataset (873 exos), en chargeant depuis le réseau si nécessaire.
     * Utilisé par GymScan pour injecter le catalogue complet dans le prompt du LLM vision.
     */
    suspend fun getAllExercises(): Result<List<ExerciseDbExercise>> = loadDataset()

    /** Total d'exercices disponibles (depuis le cache). */
    val totalCount: Int get() = cachedExercises?.size ?: 0
}
