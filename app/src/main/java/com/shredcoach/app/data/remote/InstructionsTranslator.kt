package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.shredcoach.app.domain.i18n.PromptLocale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduction des instructions d'exercices (catalogue ExerciseDB en EN) vers la
 * langue UI courante via LLM, avec cache en mémoire.
 *
 * Stratégie :
 *  - Locale EN ⇒ no-op : on retourne directement les instructions sources (déjà EN)
 *  - Locale FR ou autre ⇒ traduction LLM vers la langue cible
 *  - Cache par `exerciseId` + tag de langue → évite de re-traduire un exo déjà vu
 *  - Appel LLM en **JSON** structuré pour garder l'ordre et éviter la dérive
 *  - Fallback silencieux : si la trad échoue, on garde l'anglais sans bloquer l'UX
 */
@Singleton
class InstructionsTranslator @Inject constructor(
    private val visionClient: GeminiMealService
) {

    private val gson = Gson()
    private val cacheMutex = Mutex()

    /**
     * Cache clé=`"$exerciseId|$lang"`, valeur=instructions traduites.
     * On namespace par tag de langue : si l'utilisateur change FR↔EN depuis Settings,
     * on ne sert pas la mauvaise version mise en cache lors d'une session précédente.
     */
    private val cache = mutableMapOf<String, List<String>>()

    companion object {
        private const val TAG = "InstrTrad"
    }

    suspend fun translate(
        exerciseId: String,
        instructionsEn: List<String>,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI"
    ): Result<List<String>> {
        if (instructionsEn.isEmpty()) return Result.success(emptyList())

        // Locale EN : la source ExerciseDB est déjà en anglais, no-op LLM.
        if (PromptLocale.isEn()) {
            Log.d(TAG, "Locale EN — pas de traduction (source déjà EN)")
            return Result.success(instructionsEn)
        }

        if (apiKey.isBlank()) return Result.failure(Exception("Clé API manquante"))

        val lang = PromptLocale.lang()
        val cacheKey = "$exerciseId|$lang"

        // Cache hit ?
        cacheMutex.withLock {
            cache[cacheKey]?.let {
                Log.d(TAG, "HIT cache pour $cacheKey (${it.size} instructions)")
                return Result.success(it)
            }
        }

        Log.i(TAG, "→ Traduction LLM [$lang] pour $exerciseId (${instructionsEn.size} instructions)")
        val t0 = System.currentTimeMillis()

        val targetLanguageName = PromptLocale.languageName()
        val numbered = instructionsEn.mapIndexed { i, s -> "$i. $s" }.joinToString("\n")
        val prompt = """
Traduis ces instructions d'exercice de musculation en $targetLanguageName clair et naturel.
Garde exactement ${instructionsEn.size} entrées dans le même ordre.
Utilise du vocabulaire fitness courant dans la langue cible ($targetLanguageName).
Ne paraphrase pas excessivement, reste fidèle au contenu original.

Instructions EN (numérotées) :
$numbered

Retourne UNIQUEMENT ce JSON (aucun texte autour, aucun markdown) :
{"translations": ["Instruction 1 traduite", "Instruction 2 traduite", ...]}
""".trimIndent()

        val rawResult = visionClient.callTextLLM(
            apiKey = apiKey,
            model = model,
            provider = provider,
            prompt = prompt
        )
        val raw = rawResult.getOrElse {
            Log.e(TAG, "LLM appel échoué : ${it.message}")
            return Result.failure(it)
        }

        val translated = parseTranslations(raw, expectedSize = instructionsEn.size)
            ?: return Result.failure(Exception("Parsing traduction échoué"))

        // Cache update
        cacheMutex.withLock { cache[cacheKey] = translated }
        Log.i(TAG, "✓ Traduction OK en ${System.currentTimeMillis() - t0}ms (${translated.size} éléments)")
        return Result.success(translated)
    }

    private fun parseTranslations(raw: String, expectedSize: Int): List<String>? {
        return try {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val root = JsonParser.parseString(cleaned).asJsonObject
            val arr = root.getAsJsonArray("translations") ?: return null
            val list = arr.mapNotNull { runCatching { it.asString }.getOrNull() }
            if (list.size == expectedSize) list
            else if (list.isNotEmpty()) list // taille différente = on garde quand même, mieux que rien
            else null
        } catch (e: Exception) {
            Log.w(TAG, "Parse fail : ${e.message}")
            null
        }
    }
}
