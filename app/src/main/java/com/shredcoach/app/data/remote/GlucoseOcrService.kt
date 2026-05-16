package com.shredcoach.app.data.remote

import android.util.Log
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat structuré du parsing OCR d'un screenshot CGM.
 *
 * **Champs nullable** : l'OCR peut échouer à extraire certains champs (image
 * floue, layout inhabituel). On dégrade gracieusement plutôt que de tout
 * rejeter. Le ViewModel proposera "Corriger manuellement" pour les champs vides.
 *
 * **curve24h** : courbe JSON brute si présente sur le screenshot (pas toujours
 * — beaucoup d'apps affichent juste les stats). Format : `[{"t":"HH:MM","mgdl":N},...]`
 *
 * **confidence** : 0..1, estimée par le LLM. <0.7 → afficher banner "vérifier".
 */
data class GlucoseParseResult(
    val avgMgdl: Double? = null,
    val peakMgdl: Double? = null,
    val peakTime: String? = null,    // "HH:MM"
    val minMgdl: Double? = null,
    val minTime: String? = null,     // "HH:MM"
    val timeInRangePct: Int? = null,
    val timeAboveRangePct: Int? = null,
    val timeBelowRangePct: Int? = null,
    val hypoCount: Int? = null,
    val cv: Double? = null,
    val curve24hJson: String? = null,
    val confidence: Float = 0f,
    val errorReason: String? = null,
)

/**
 * Service OCR spécialisé sur les screenshots CGM (FreeStyle Libre, Dexcom,
 * Diabox, Nightscout, Medtronic, etc.). Délègue le HTTP / Gemini Vision call
 * à [GeminiMealService.callVisionLLM] pour réutiliser la plomberie existante
 * (multi-provider Gemini/Groq/Mistral, gestion erreurs, timeout).
 *
 * **Pourquoi un service dédié plutôt qu'un méthode de GeminiMealService** :
 *  - Prompt spécialisé endocrino (vocabulaire CGM, ranges médicaux)
 *  - Schéma de retour propre ([GlucoseParseResult]) sans pollution meal
 *  - Permet d'évoluer indépendamment (modèle dédié, fine-tuning futur)
 *
 * **Robustesse** : 4 niveaux de fallback en cascade comme MealScanner :
 *  1. JSON direct parsé par Gson
 *  2. Strip des backticks markdown
 *  3. Extraction de la première substring `{...}` valide
 *  4. Retour `errorReason` non-null si tout échoue
 *
 * **Coût** : 1 appel Gemini Vision (~1-3s, ~0.001€). Si l'user upload 1
 * screenshot/jour → ~0.03€/mois — négligeable.
 */
@Singleton
class GlucoseOcrService @Inject constructor(
    private val geminiMealService: GeminiMealService,
) {

    companion object {
        private const val TAG = "GlucoseOCR"

        /**
         * Prompt OCR CGM. Conçu pour fonctionner sur tous les formats de
         * screenshot grand public (FreeStyle Libre, Dexcom Clarity, Dexcom G6/G7,
         * Diabox, Nightscout web, Medtronic CareLink, xDrip+).
         *
         * **Important** : on demande JSON strict, on tolère les champs absents
         * en les laissant à `null` plutôt qu'à 0. Confidence reflète
         * notre certitude globale (image lisible vs floue).
         */
        val GLUCOSE_OCR_PROMPT_FR = """
Tu es un OCR médical spécialisé sur les screenshots de capteurs de glucose en
continu (CGM). Tu reçois une image qui provient probablement de l'une de ces
apps : FreeStyle Libre, FreeStyle LibreLink, Dexcom Clarity, Dexcom G6/G7,
Diabox, Nightscout, Medtronic CareLink, xDrip+, Tomato, GlucoMen.

OBJECTIF : extraire les métriques glycémiques visibles sur l'écran. Tu rends
UNIQUEMENT du JSON valide, sans markdown, sans texte d'introduction, sans
backticks. JAMAIS de prose.

UNITÉ : mg/dL par défaut (France/USA). Si tu vois des valeurs en mmol/L
(typique UK/CA, valeurs ~3-20), CONVERTIS automatiquement en mg/dL via
mg_dl = mmol_l × 18. Toutes les valeurs de sortie sont en mg/dL.

CHAMPS À EXTRAIRE (tous OPTIONNELS — laisse `null` si absent ou illisible) :
- `avg_mgdl`        : glycémie moyenne sur la période affichée (mg/dL, entier ou décimal)
- `peak_mgdl`       : pic maximum mg/dL
- `peak_time`       : heure du pic au format "HH:MM" (24h) si visible
- `min_mgdl`        : minimum mg/dL
- `min_time`        : heure du min "HH:MM"
- `time_in_range_pct` : pourcentage de temps dans la fourchette cible (typiquement 70-180 mg/dL standard, ou 70-140 mg/dL athlète/strict). ENTIER 0-100.
- `time_above_range_pct` : pourcentage de temps au-dessus de la fourchette haute. ENTIER 0-100.
- `time_below_range_pct` : pourcentage de temps en dessous de la fourchette basse (hypo). ENTIER 0-100.
- `hypo_count`      : nombre d'épisodes hypoglycémiques (mg/dL < 70). ENTIER ≥0.
- `cv`              : coefficient de variation glycémique en pourcentage (écart-type / moyenne × 100). DÉCIMAL.
- `curve_24h`       : tableau JSON `[{"t":"HH:MM","mgdl":N},...]` si une courbe est lisible sur le screenshot. ÉCHANTILLONNAGE : 15min à 1h selon ce que tu peux lire (max 96 points). Si pas de courbe visible : `null`.
- `confidence`      : 0.0 à 1.0, ta confiance dans l'ensemble du parsing.
- `error_reason`    : string ou null. Mets `"not_a_cgm"` si l'image ne ressemble pas à un screenshot CGM. `"unreadable"` si trop floue.

EXEMPLES DE VALEURS PLAUSIBLES (sanity check) :
- avg_mgdl : 80-200 (95-110 = excellent, 130+ = élevé, 180+ = critique)
- TIR : 0-100 (>70% = bon, >80% = excellent pour athlète)
- CV : 15-50 (<36% = stable, ≥36% = variabilité élevée)
- hypo_count : 0-5 (0-1 = idéal, ≥3 = préoccupant)
- peak_mgdl : 100-250 (<140 = excellent, >180 = postprandial spike notable)

RÈGLE DE COHÉRENCE :
- Si `avg_mgdl` est présent, vérifie : `min_mgdl ≤ avg_mgdl ≤ peak_mgdl`
- Si `time_in_range_pct + time_above_range_pct + time_below_range_pct > 102`, c'est incohérent → ajuste ou laisse les 3 à null.
- Si l'image ne contient AUCUN chiffre interprétable comme glucose → retourne `{"error_reason":"not_a_cgm","confidence":0}`

JSON DE SORTIE (exemple) :
{
  "avg_mgdl": 118,
  "peak_mgdl": 165,
  "peak_time": "12:32",
  "min_mgdl": 78,
  "min_time": "04:15",
  "time_in_range_pct": 82,
  "time_above_range_pct": 16,
  "time_below_range_pct": 2,
  "hypo_count": 0,
  "cv": 28.5,
  "curve_24h": null,
  "confidence": 0.85,
  "error_reason": null
}
""".trimIndent()
    }

    /**
     * @param imageBytes screenshot CGM (JPEG/PNG)
     * @param mimeType "image/jpeg" ou "image/png"
     * @param apiKey clé Gemini/Groq/Mistral selon provider
     * @param model nom du modèle (par défaut Gemini Flash)
     * @param provider "GEMINI" | "GROQ" | "MISTRAL"
     */
    suspend fun parseScreenshot(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
    ): Result<GlucoseParseResult> {
        val raw = geminiMealService.callVisionLLM(
            imageBytes = imageBytes,
            mimeType = mimeType,
            apiKey = apiKey,
            model = model,
            provider = provider,
            prompt = GLUCOSE_OCR_PROMPT_FR,
        )
        if (raw.isFailure) return Result.failure(raw.exceptionOrNull() ?: Exception("OCR call failed"))
        val rawJson = raw.getOrNull() ?: return Result.failure(Exception("Empty OCR response"))

        return try {
            Result.success(parse(rawJson))
        } catch (e: Exception) {
            Log.e(TAG, "Glucose OCR parse failed. Preview: ${rawJson.take(300)}", e)
            Result.success(GlucoseParseResult(
                confidence = 0f,
                errorReason = "parse_failed: ${e.message?.take(80)}",
            ))
        }
    }

    /**
     * Pipeline de parsing robuste avec fallbacks en cascade. Identique en
     * esprit au [GeminiMealService.parseRobust] pour MealAnalysisResult.
     */
    internal fun parse(rawJson: String): GlucoseParseResult {
        val cleaned = rawJson
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        // Cherche la première substring `{...}` valide si du texte traîne autour.
        val jsonText = if (cleaned.startsWith("{")) cleaned else extractFirstJsonObject(cleaned) ?: cleaned

        val obj = JsonParser.parseString(jsonText).asJsonObject

        fun double(key: String): Double? = obj.get(key)?.takeIf { !it.isJsonNull }?.asDouble
        fun int(key: String): Int? = obj.get(key)?.takeIf { !it.isJsonNull }?.asInt
        fun str(key: String): String? = obj.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

        val curveJson = obj.get("curve_24h")?.takeIf { !it.isJsonNull && it.isJsonArray }?.toString()

        return GlucoseParseResult(
            avgMgdl = double("avg_mgdl"),
            peakMgdl = double("peak_mgdl"),
            peakTime = str("peak_time"),
            minMgdl = double("min_mgdl"),
            minTime = str("min_time"),
            timeInRangePct = int("time_in_range_pct")?.coerceIn(0, 100),
            timeAboveRangePct = int("time_above_range_pct")?.coerceIn(0, 100),
            timeBelowRangePct = int("time_below_range_pct")?.coerceIn(0, 100),
            hypoCount = int("hypo_count")?.coerceAtLeast(0),
            cv = double("cv"),
            curve24hJson = curveJson,
            confidence = (double("confidence") ?: 0.0).toFloat().coerceIn(0f, 1f),
            errorReason = str("error_reason"),
        )
    }

    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
