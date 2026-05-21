package com.shredcoach.app.domain.glucose

import android.util.Log
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.dao.GlucoseAnalysisDao
import com.shredcoach.app.data.local.entity.AnalysisVerdict
import com.shredcoach.app.data.local.entity.GlucoseAnalysisEntity
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.GeminiMealService
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrateur de l'analyse experte de glycémie quotidienne.
 *
 * Pipeline :
 *  1. Récupère [GlucoseLogEntity] pour la date (DOIT exister, sinon erreur)
 *  2. Récupère les meal logs + résout les noms d'aliments (jointure FoodEntity)
 *  3. Pré-traite la courbe via [GlucoseCurvePreprocessor]
 *  4. Calcule [GlucoseCurvePreprocessor.computeInputHash]
 *  5. Lookup cache : si hash match dans `glucose_analyses`, return cached
 *  6. Sinon : build prompt → callTextLLM Gemini → parse JSON → upsert cache
 *
 * **Forçage** (`force = true`) : ignore le cache et re-déclenche l'inférence
 * LLM. Utilisé par le bouton "Re-analyser" côté UI.
 *
 * **Atomicité** : si le LLM échoue ou si le JSON est malformé, le cache n'est
 * PAS écrit → la prochaine tentative re-déclenche. Pas de poisoning du cache
 * avec des analyses partielles.
 *
 * **Coût** : ~6-10k tokens d'input + ~1-2k d'output → ~$0.005 par analyse
 * sur Gemini 2.5 Flash. Cache 24h via hash → un user actif coûte ~$0.10/an.
 */
@Singleton
class GlucoseAnalysisEngine @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    private val nutritionRepository: NutritionRepository,
    private val userRepository: UserRepository,
    private val analysisDao: GlucoseAnalysisDao,
    private val geminiService: GeminiMealService,
) {

    sealed class Result {
        data class Success(val entity: GlucoseAnalysisEntity, val fromCache: Boolean) : Result()
        data class Error(val reason: ErrorReason, val message: String) : Result()
    }

    enum class ErrorReason {
        NO_GLUCOSE_LOG,         // L'user n'a pas uploadé son CGM
        NO_CURVE_DATA,          // Log existe mais pas de courbe parsée
        NO_API_KEY,             // Clé Gemini non configurée
        LLM_FAILURE,            // Erreur réseau / API
        PARSE_FAILURE,          // JSON invalide retourné par le LLM
    }

    /**
     * Récupère l'analyse pour [date]. Cache-first avec fallback LLM.
     */
    suspend fun analyze(date: LocalDate, force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val log = glucoseRepository.getForDate(date)
            ?: return@withContext Result.Error(ErrorReason.NO_GLUCOSE_LOG, "Pas de log CGM pour cette date")

        // 1. Résoudre les meals avec noms d'aliments
        val mealLogs = runCatching { nutritionRepository.getMealsForDateOnce(date) }
            .getOrDefault(emptyList())
        val mealsWithNames = mealLogs.mapNotNull { ml ->
            val time = ml.time ?: return@mapNotNull null
            val food = runCatching { nutritionRepository.getFoodById(ml.foodId) }.getOrNull()
            MealContext(
                name = food?.name ?: "Repas",
                time = time,
                calories = ml.calories,
                carbsGrams = ml.carbs,
                proteinsGrams = ml.proteins,
                fatsGrams = ml.fats,
            )
        }

        // 2. Hash + cache lookup
        val inputHash = GlucoseCurvePreprocessor.computeInputHash(log, mealsWithNames)
        if (!force) {
            val cached = analysisDao.getForDate(date)
            if (cached != null && cached.inputHash == inputHash) {
                Log.i(TAG, "Cache hit for $date (hash=$inputHash)")
                return@withContext Result.Success(cached, fromCache = true)
            }
        }

        // 3. Pré-traitement
        val context = GlucoseCurvePreprocessor.preprocess(log, mealsWithNames)
            ?: return@withContext Result.Error(ErrorReason.NO_CURVE_DATA, "Pas de courbe glycémique exploitable")

        // 4. API key
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
        if (apiKey.isBlank()) {
            return@withContext Result.Error(ErrorReason.NO_API_KEY, "Clé Gemini non configurée")
        }

        // 5. Prompt + LLM call
        val profile = runCatching { userRepository.getUserProfileOnce() }.getOrNull()
        val prompt = GlucoseAnalysisPrompt.build(
            context = context,
            userFirstName = profile?.firstName?.takeIf { it.isNotBlank() },
            athleteGoal = profile?.goal?.name,
        )
        Log.d(TAG, "Prompt length: ${prompt.length} chars")

        val startMs = System.currentTimeMillis()
        // Appel dédié structured-JSON (maxOutputTokens=8192 + thinking off)
        // au lieu de callTextLLM (2048 tokens, thinking on) qui truncait
        // systématiquement la réponse → PARSE_FAILURE.
        val callResult = geminiService.callJsonAnalysisLLM(
            apiKey = apiKey,
            prompt = prompt,
            model = LLM_MODEL,
        )
        val latencyMs = System.currentTimeMillis() - startMs

        val rawJson = callResult.getOrElse { e ->
            Log.e(TAG, "LLM call failed", e)
            return@withContext Result.Error(
                ErrorReason.LLM_FAILURE,
                "Analyse Dr. Glykos indisponible : ${e.message?.take(120) ?: "erreur réseau"}",
            )
        }

        // 6. Parse + cache
        val parsed = runCatching { parseLlmResponse(rawJson) }.getOrElse { e ->
            // Log généreux : on conserve les 800 premiers + 200 derniers caractères
            // pour diagnostiquer si le LLM tronque ou prefix avec du texte. Sur
            // une truncation, le tail révèle un objet JSON inachevé.
            val head = rawJson.take(800).replace("\n", "\\n")
            val tail = rawJson.takeLast(200).replace("\n", "\\n")
            Log.e(TAG, "Parse failed (len=${rawJson.length}). Head: $head ... Tail: $tail", e)
            return@withContext Result.Error(
                ErrorReason.PARSE_FAILURE,
                "Réponse Dr. Glykos malformée. Réessaie dans quelques instants.",
            )
        }

        val entity = GlucoseAnalysisEntity(
            date = date,
            createdAt = LocalDateTime.now(),
            verdict = parsed.verdict,
            summary = parsed.summary,
            globalAdvice = parsed.globalAdvice,
            insightsJson = parsed.insightsJson,
            inputHash = inputHash,
            llmModel = LLM_MODEL,
            tokensUsed = null,  // Gemini ne renvoie pas les tokens via callTextLLM
            latencyMs = latencyMs,
        )
        val id = analysisDao.upsert(entity)
        Log.i(TAG, "Analysis saved id=$id for $date (verdict=${parsed.verdict}, latency=${latencyMs}ms)")
        Result.Success(entity.copy(id = id), fromCache = false)
    }

    /**
     * Variante "fire-and-cache" pour le worker quotidien : retourne l'entité
     * stockée, ou null si erreur. Le worker doit lire son propre Result.
     */
    suspend fun analyzeAndCache(date: LocalDate): GlucoseAnalysisEntity? {
        return when (val r = analyze(date, force = false)) {
            is Result.Success -> r.entity
            is Result.Error -> {
                Log.w(TAG, "analyzeAndCache for $date failed: ${r.reason}")
                null
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PARSING JSON LLM
    // ═══════════════════════════════════════════════════════════════════════

    private data class ParsedAnalysis(
        val verdict: AnalysisVerdict,
        val summary: String,
        val globalAdvice: String,
        val insightsJson: String,  // JSON array kept as-is for storage
    )

    private fun parseLlmResponse(raw: String): ParsedAnalysis {
        // Strip code fences si le LLM les ajoute (rare avec responseMimeType=
        // application/json mais on garde la sécurité).
        var cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        // Extra robustness : certains LLMs prefix un "Voici l'analyse:" ou
        // similaire malgré l'instruction de ne pas le faire. On cherche le
        // premier `{` ouvrant et on tronque tout ce qui précède.
        val firstBrace = cleaned.indexOf('{')
        if (firstBrace > 0) cleaned = cleaned.substring(firstBrace)

        // Si le JSON est tronqué (Gemini hit maxOutputTokens) → on tente de
        // réparer en fermant proprement les structures ouvertes.
        cleaned = repairTruncatedJson(cleaned)

        val root = JsonParser.parseString(cleaned).asJsonObject

        val verdict = root.get("verdict")?.asString
            ?.let { runCatching { AnalysisVerdict.valueOf(it.uppercase()) }.getOrNull() }
            ?: AnalysisVerdict.GOOD

        val summary = root.get("summary")?.asString?.trim().orEmpty()
        val globalAdvice = root.get("globalAdvice")?.asString?.trim().orEmpty()

        // Re-sérialise les insights pour normaliser (le LLM peut produire
        // des variations d'espacement / null absents). On garde un array JSON
        // strict avec les champs attendus.
        val insightsArr = root.getAsJsonArray("insights")
        val insightsJson = insightsArr?.toString() ?: "[]"

        return ParsedAnalysis(
            verdict = verdict,
            summary = summary,
            globalAdvice = globalAdvice,
            insightsJson = insightsJson,
        )
    }

    /**
     * Répare un JSON tronqué en fermant proprement les structures ouvertes
     * (objects `{}`, arrays `[]`, strings `"`). Algorithme : on parcourt
     * caractère par caractère en suivant l'état de la pile de structures.
     *
     * À la fin :
     *  - Si on est dans une string non-fermée, on la ferme par un `"`
     *  - Si la dernière virgule traîne (`,]` ou `,}`), on la retire
     *  - On ferme la pile restante avec les bonnes accolades dans l'ordre
     *
     * **Pourquoi pas Gson lenient mode** : Gson lenient ne ferme PAS les
     * structures, il rend juste le parser plus tolérant aux espaces / quotes.
     * Inutile pour notre cas (truncation pure).
     *
     * Identique à `GeminiMealService.repairTruncatedJson` (pattern éprouvé).
     */
    private fun repairTruncatedJson(json: String): String {
        var trimmed = json.trimEnd()
        while (trimmed.endsWith(",") || trimmed.endsWith(":")) {
            trimmed = trimmed.dropLast(1).trimEnd()
        }
        val stack = mutableListOf<Char>()
        var inString = false
        var escaped = false
        for (c in trimmed) {
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && (c == '{' || c == '[') -> stack.add(c)
                !inString && c == '}' -> { if (stack.lastOrNull() == '{') stack.removeAt(stack.size - 1) }
                !inString && c == ']' -> { if (stack.lastOrNull() == '[') stack.removeAt(stack.size - 1) }
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

    companion object {
        private const val TAG = "GlucoseAnalysisEngine"
        private const val LLM_MODEL = "gemini-2.5-flash"
    }
}
