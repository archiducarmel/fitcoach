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
    val micronutrients: List<Micronutrient> = emptyList(),
    // ── v49 : Indice glycémique (GI) + Glycemic Load (GL) ──
    // Agrégés au niveau du scan (pas du plat) : GI = moyenne pondérée par
    // les carbs de chaque plat, GL = somme additive des GL individuels.
    // Voir GlycemicMath pour la formule détaillée.
    //  - glycemicIndex : 0-110, null si aucun plat n'a fourni de GI fiable
    //  - glycemicLoad  : GL total raw (sans modifier), scale avec ×reprises
    //  - giCategory    : LOW/MEDIUM/HIGH (dérivé du GI, persisté pour stabilité)
    //  - giConfidence  : HIGH/MEDIUM/LOW agrégé depuis les confidences per-dish
    val glycemicIndex: Int? = null,
    val glycemicLoad: Double? = null,
    @SerializedName("gi_category") val giCategory: String? = null,
    @SerializedName("gi_confidence") val giConfidence: String? = null,
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
    val ingredients: List<Ingredient> = emptyList(),
    // ── v49 : GI per-dish (peuplé par LLM, agrégé au scan via GlycemicMath) ──
    @SerializedName("glycemic_index") val glycemicIndex: Int? = null,
    @SerializedName("gi_confidence") val giConfidence: String? = null,
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
    @com.shredcoach.app.di.NetworkModule.BaseHttpClient baseClient: OkHttpClient,
    /**
     * Recorder de télémétrie LLM. Si non-null, chaque appel emit un event
     * pour le dashboard de consommation IA (Settings → Consommation).
     * Injected via Hilt — pas optionnel à l'execution, mais on garde la
     * possibilité null si jamais un service standalone l'instancie sans Hilt
     * dans le futur.
     */
    private val usageRecorder: com.shredcoach.app.domain.llm.LlmUsageRecorder,
    /**
     * Bus pour notifier l'UI quand on bascule sur un LLM fallback (quota
     * primary epuise). Le banner UI ecoute et affiche un message humouristique.
     */
    private val fallbackBus: com.shredcoach.app.domain.llm.LlmFallbackBus,
    /**
     * LlmApiService injecte pour router les providers OpenAI-compat
     * (OPENAI/GROQ/GITHUB_MODELS/NVIDIA_NIM) vers messageWithImageJson.
     * Permet a l'user de configurer un assistant vision sur GPT-4o, Llama Vision,
     * Qwen Vision, Gemma 3, etc. au lieu de Gemini/Mistral hardcodes.
     */
    private val llmApiService: LlmApiService,
) {

    /**
     * Helper interne : si le fallback est configure ET que l'erreur est
     * classifiee QUOTA_EXHAUSTED, emit l'event et retourne true (caller doit
     * retry avec le fallback). Sinon retourne false (caller doit propager
     * l'erreur, le retry standard suffira pour TRANSIENT).
     */
    private fun shouldTriggerFallback(
        provider: String,
        exception: Throwable,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig?,
        assistant: com.shredcoach.app.domain.llm.AiAssistant?,
    ): Boolean {
        if (fallback == null || assistant == null) return false
        val primaryEnum = runCatching { LlmProvider.valueOf(provider.uppercase()) }.getOrNull() ?: return false
        val classification = com.shredcoach.app.domain.llm.LlmQuotaDetector
            .classifyException(primaryEnum, exception)
        if (classification != com.shredcoach.app.domain.llm.LlmQuotaDetector.Classification.QUOTA_EXHAUSTED) {
            return false
        }
        val fallbackEnum = runCatching { LlmProvider.valueOf(fallback.provider.uppercase()) }.getOrNull()
            ?: return false
        Log.i(TAG, "Quota exhausted on $primaryEnum → falling back to $fallbackEnum for $assistant")
        fallbackBus.emitTrySync(
            com.shredcoach.app.domain.llm.LlmFallbackEvent(assistant, primaryEnum, fallbackEnum)
        )
        return true
    }

    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Parse l'usageMetadata d'une réponse Gemini pour récupérer les tokens.
     * Format de réponse :
     * ```
     * { "usageMetadata": { "promptTokenCount": N, "candidatesTokenCount": M,
     *                      "thoughtsTokenCount": K, "totalTokenCount": ... } }
     * ```
     * Retourne (input, output, thinking). Tous à 0 si parse échoue.
     */
    private fun parseGeminiUsage(responseJson: com.google.gson.JsonObject): Triple<Int, Int, Int> {
        val usage = responseJson.getAsJsonObject("usageMetadata") ?: return Triple(0, 0, 0)
        val input = usage.get("promptTokenCount")?.asInt ?: 0
        val output = usage.get("candidatesTokenCount")?.asInt ?: 0
        val thinking = usage.get("thoughtsTokenCount")?.asInt ?: 0
        return Triple(input, output, thinking)
    }

    /**
     * Parse l'usage block d'une réponse OpenAI-compatible (Groq, Mistral).
     * Format : `{ "usage": { "prompt_tokens": N, "completion_tokens": M } }`.
     */
    private fun parseOpenAiUsage(responseJson: com.google.gson.JsonObject): Triple<Int, Int, Int> {
        val usage = responseJson.getAsJsonObject("usage") ?: return Triple(0, 0, 0)
        val input = usage.get("prompt_tokens")?.asInt ?: 0
        val output = usage.get("completion_tokens")?.asInt ?: 0
        return Triple(input, output, 0)
    }

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

══ ÉTAPE 4 — INDICE GLYCÉMIQUE (GI) PAR PLAT ══
Estime l'indice glycémique de CHAQUE plat (0-110, échelle ISO 26642 / base
Sydney). Le GI dépend de la composition du plat (féculents, sucres, fibres,
gras) et de la cuisson :

  RÉFÉRENCES IG (plat composé, valeurs typiques) :
  - Salade verte + protéine maigre : 15-25 (peu de glucides)
  - Légumineuses cuites (lentilles, pois chiches) : 25-35
  - Riz basmati + légumes + protéine : 50-58
  - Pâtes al dente + sauce tomate : 45-55
  - Pizza pâte fine + garniture : 50-60
  - Burger + frites : 65-75
  - Pain blanc + Nutella : 70-80
  - Sushi (riz vinaigré) : 65-75
  - Smoothie fruits avec banane : 50-60
  - Soupe de légumes : 30-45
  - Yaourt nature + fruits : 30-40

  RÈGLES D'AJUSTEMENT :
  - Présence de fibres (>5g) → IG baisse de ~5-10 points
  - Présence de gras/protéines avec les glucides → IG baisse de ~5-15 points
  - Cuisson longue ou très molle (pâtes trop cuites, purée) → IG monte de 10-15 pts
  - Boissons sucrées, jus, sucre pur → IG très élevé (>70)
  - Plat sans glucides significatifs (<5g carbs) → IG à 0 (non applicable)

  CONFIDENCE :
  - "HIGH" : plat clair, ingrédients identifiés, base CIQUAL/Sydney disponible
  - "MEDIUM" : plat composé classique, estimation raisonnable
  - "LOW" : plat exotique, recette incertaine, ingrédients ambigus

Si tu ne peux PAS estimer le GI d'un plat avec confiance raisonnable
(recette inconnue, mélange trop complexe), mets `glycemic_index: null` et
`gi_confidence: "LOW"`. NE DEVINE PAS au hasard.

JSON (mêmes champs que l'analyse photo, plus glycemic_index + gi_confidence par plat) :
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
      "glycemic_index": 55,
      "gi_confidence": "MEDIUM",
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
healthScore = note 0-10 (entier). AJR EFSA 2000kcal, 8-12 micronutriments
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

══ ÉTAPE 4 — INDICE GLYCÉMIQUE (GI) PAR PLAT ══
Estime l'indice glycémique de CHAQUE plat (0-110, échelle ISO 26642 / base Sydney).

  RÉFÉRENCES IG (plat composé, valeurs typiques) :
  - Salade verte + protéine maigre : 15-25 (peu de glucides)
  - Légumineuses cuites (lentilles, pois chiches) : 25-35
  - Riz basmati + légumes + protéine : 50-58
  - Pâtes al dente + sauce tomate : 45-55
  - Pizza pâte fine + garniture : 50-60
  - Burger + frites : 65-75
  - Pain blanc + Nutella : 70-80
  - Sushi (riz vinaigré) : 65-75
  - Smoothie fruits avec banane : 50-60
  - Soupe de légumes : 30-45
  - Yaourt nature + fruits : 30-40

  AJUSTEMENTS : fibres élevées → IG baisse ~5-10 pts | gras/protéines présents → IG baisse ~5-15 pts |
  cuisson longue/molle → IG monte 10-15 pts | sucres rapides → IG > 70 | <5g carbs → IG = 0 (non applicable).

  CONFIDENCE :
  - "HIGH" : plat clair, ingrédients identifiés, base CIQUAL/Sydney disponible
  - "MEDIUM" : plat composé classique, estimation raisonnable
  - "LOW" : plat exotique, recette incertaine, ingrédients ambigus

Si tu ne peux PAS estimer avec confiance raisonnable : `glycemic_index: null` + `gi_confidence: "LOW"`.
NE DEVINE PAS au hasard.

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
      "glycemic_index": 55,
      "gi_confidence": "MEDIUM",
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
        prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val raw = when (provider.uppercase()) {
                "GROQ" -> callGroqText(apiKey, prompt, assistant)
                "MISTRAL" -> callMistralText(apiKey, prompt, assistant)
                "OPENAI", "GITHUB_MODELS", "NVIDIA_NIM" ->
                    callOpenAiCompatText(provider, apiKey, model, prompt, assistant)
                else -> callGeminiText(apiKey, model, prompt, assistant)
            }
            if (raw.isBlank()) Result.failure(Exception("Réponse LLM vide")) else Result.success(raw)
        } catch (e: Exception) {
            Log.e(TAG, "callTextLLM failed", e)
            // Emit failure event pour le dashboard (success est emit dans la private).
            // SAUF si OpenAI-compat (LlmApiService.messageJson a deja record le failure).
            if (!isOpenAiCompatProvider(provider)) {
                val effectiveProvider = runCatching { LlmProvider.valueOf(provider.uppercase()) }
                    .getOrDefault(LlmProvider.GEMINI)
                usageRecorder.record(
                    assistant = assistant, provider = effectiveProvider, model = model,
                    tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                    latencyMs = System.currentTimeMillis() - startMs, success = false,
                )
            }
            // Try fallback si quota epuise (pas pour engorgement transient)
            if (shouldTriggerFallback(provider, e, fallback, assistant)) {
                return@withContext callTextLLM(
                    apiKey = fallback!!.apiKey, model = fallback.model,
                    provider = fallback.provider, prompt = prompt,
                    assistant = assistant, fallback = null,
                )
            }
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
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val raw = callGeminiJsonAnalysis(
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                maxOutputTokens = maxOutputTokens,
                disableThinking = disableThinking,
                assistant = assistant,
            )
            if (raw.isBlank()) Result.failure(Exception("Réponse LLM vide"))
            else Result.success(raw)
        } catch (e: Exception) {
            Log.e(TAG, "callJsonAnalysisLLM failed", e)
            usageRecorder.record(
                assistant = assistant, provider = com.shredcoach.app.data.remote.LlmProvider.GEMINI,
                model = model, tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                latencyMs = System.currentTimeMillis() - startMs, success = false,
            )
            Result.failure(e)
        }
    }

    private fun callGeminiJsonAnalysis(
        apiKey: String,
        model: String,
        prompt: String,
        maxOutputTokens: Int,
        disableThinking: Boolean,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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

        // ─── Telemetrie : emit usage event pour le dashboard ──
        val (tIn, tOut, tThink) = parseGeminiUsage(json)
        usageRecorder.record(
            assistant = assistant,
            provider = com.shredcoach.app.data.remote.LlmProvider.GEMINI,
            model = model,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = tThink,
            latencyMs = System.currentTimeMillis() - startMs,
            success = true,
        )
        return raw
    }

    private fun callGeminiText(
        apiKey: String, model: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, tThink) = parseGeminiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GEMINI, model = model,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = tThink,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return textParts.joinToString("").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callGroqText(
        apiKey: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GROQ, model = GROQ_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callMistralText(
        apiKey: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.MISTRAL, model = MISTRAL_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
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
        prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroq(imageBytes, mimeType, apiKey, prompt, assistant)
                "MISTRAL" -> callMistral(imageBytes, mimeType, apiKey, prompt, assistant)
                "OPENAI", "GITHUB_MODELS", "NVIDIA_NIM" ->
                    callOpenAiCompatVision(provider, apiKey, model, imageBytes, mimeType, prompt, assistant)
                else -> callGemini(imageBytes, mimeType, apiKey, model, prompt, assistant)
            }
            if (rawJson.isBlank()) Result.failure(Exception("Réponse LLM vide"))
            else Result.success(rawJson)
        } catch (e: Exception) {
            // Eviter double-telemetrie : LlmApiService.messageWithImageJson record
            // deja le failure pour OpenAI-compat providers. Ne record ici que
            // pour les paths qui ne passent PAS par LlmApiService (Gemini/Groq/Mistral).
            if (!isOpenAiCompatProvider(provider)) {
                val effectiveProvider = runCatching { LlmProvider.valueOf(provider.uppercase()) }
                    .getOrDefault(LlmProvider.GEMINI)
                usageRecorder.record(
                    assistant = assistant, provider = effectiveProvider, model = model,
                    tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                    latencyMs = System.currentTimeMillis() - startMs, success = false,
                )
            }
            if (shouldTriggerFallback(provider, e, fallback, assistant)) {
                return@withContext callVisionLLM(
                    imageBytes = imageBytes, mimeType = mimeType,
                    apiKey = fallback!!.apiKey, model = fallback.model,
                    provider = fallback.provider, prompt = prompt,
                    assistant = assistant, fallback = null,
                )
            }
            Result.failure(e)
        }
    }

    /** True si provider passe par LlmApiService (qui telemetered en interne).
     *  Evite la double-record dans les catch outer de GeminiMealService. */
    private fun isOpenAiCompatProvider(providerName: String): Boolean =
        providerName.uppercase() in setOf("OPENAI", "GITHUB_MODELS", "NVIDIA_NIM")

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
        hintBlock: String = "",
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Result<MealAnalysisResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
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
                "GROQ" -> callGroqTextMeal(apiKey, finalPrompt, assistant)
                "MISTRAL" -> callMistralTextMeal(apiKey, finalPrompt, assistant)
                "OPENAI", "GITHUB_MODELS", "NVIDIA_NIM" ->
                    callOpenAiCompatText(provider, apiKey, model, finalPrompt, assistant)
                else -> callGeminiTextMeal(apiKey, model, finalPrompt, assistant)
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
            // Skip telemetrie outer si OpenAI-compat (LlmApiService.messageJson l'a deja fait)
            if (!isOpenAiCompatProvider(provider)) {
                val effectiveProvider = runCatching { LlmProvider.valueOf(provider.uppercase()) }
                    .getOrDefault(LlmProvider.GEMINI)
                usageRecorder.record(
                    assistant = assistant, provider = effectiveProvider, model = model,
                    tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                    latencyMs = System.currentTimeMillis() - startMs, success = false,
                )
            }
            if (shouldTriggerFallback(provider, e, fallback, assistant)) {
                return@withContext analyzeMealFromText(
                    description = description, apiKey = fallback!!.apiKey,
                    model = fallback.model, provider = fallback.provider,
                    hintBlock = hintBlock, assistant = assistant, fallback = null,
                )
            }
            Result.failure(e)
        }
    }

    suspend fun analyzeMeal(
        imageBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        model: String = "gemini-2.5-flash",
        provider: String = "GEMINI",
        hintBlock: String = "",
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
        fallback: com.shredcoach.app.domain.llm.FallbackConfig? = null,
    ): Result<MealAnalysisResult> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            // Construit le prompt final : prompt de base + bloc d'indices utilisateur (vide si aucun indice)
            val withHints = if (hintBlock.isBlank()) MEAL_PROMPT else MEAL_PROMPT + "\n" + hintBlock
            // i18n : directive d'override de langue (cf. analyzeMealFromText).
            val finalPrompt = com.shredcoach.app.domain.i18n.PromptLocale.outputLanguageDirective() + withHints

            val rawJson = when (provider.uppercase()) {
                "GROQ" -> callGroq(imageBytes, mimeType, apiKey, finalPrompt, assistant)
                "MISTRAL" -> callMistral(imageBytes, mimeType, apiKey, finalPrompt, assistant)
                "OPENAI", "GITHUB_MODELS", "NVIDIA_NIM" ->
                    callOpenAiCompatVision(provider, apiKey, model, imageBytes, mimeType, finalPrompt, assistant)
                else -> callGemini(imageBytes, mimeType, apiKey, model, finalPrompt, assistant)
            }

            if (rawJson.isBlank()) return@withContext Result.failure(Exception("Analyse vide"))

            // Vérifier erreur image non alimentaire
            if (rawJson.contains("\"error\"") && rawJson.length < 200) {
                return@withContext Result.failure(Exception("Image non alimentaire"))
            }

            // Parser avec le même pipeline robuste pour les 4 providers
            val result = parseRobust(rawJson)
            if (result == null) {
                val preview = rawJson.take(300).replace("\n", "↵")
                return@withContext Result.failure(Exception("Parsing échoué. Début réponse: $preview"))
            }
            Result.success(result)
        } catch (e: Exception) {
            // Skip telemetrie outer si OpenAI-compat (deja record par LlmApiService.messageWithImageJson)
            if (!isOpenAiCompatProvider(provider)) {
                val effectiveProvider = runCatching { LlmProvider.valueOf(provider.uppercase()) }
                    .getOrDefault(LlmProvider.GEMINI)
                usageRecorder.record(
                    assistant = assistant, provider = effectiveProvider, model = model,
                    tokensInput = 0, tokensOutput = 0, tokensThinking = 0,
                    latencyMs = System.currentTimeMillis() - startMs, success = false,
                )
            }
            if (shouldTriggerFallback(provider, e, fallback, assistant)) {
                return@withContext analyzeMeal(
                    imageBytes = imageBytes, mimeType = mimeType,
                    apiKey = fallback!!.apiKey, model = fallback.model,
                    provider = fallback.provider, hintBlock = hintBlock,
                    assistant = assistant, fallback = null,
                )
            }
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

    private fun callGeminiTextMeal(
        apiKey: String, model: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, tThink) = parseGeminiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GEMINI, model = model,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = tThink,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return textParts.joinToString("").trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callGroqTextMeal(
        apiKey: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GROQ, model = GROQ_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun callMistralTextMeal(
        apiKey: String, prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.MISTRAL, model = MISTRAL_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // GEMINI
    // ═══════════════════════════════════════

    private fun callGemini(
        imageBytes: ByteArray, mimeType: String, apiKey: String, model: String,
        prompt: String = MEAL_PROMPT,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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

        // Telemetrie success — parsing usage avant return
        val (tIn, tOut, tThink) = parseGeminiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GEMINI, model = model,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = tThink,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // GROQ (Llama 4 Scout — OpenAI-compatible)
    // ═══════════════════════════════════════

    private fun callGroq(
        imageBytes: ByteArray, mimeType: String, apiKey: String,
        prompt: String = MEAL_PROMPT,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.GROQ, model = GROQ_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
        return rawJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    // ═══════════════════════════════════════
    // MISTRAL (Mistral Small — OpenAI-compatible)
    // ═══════════════════════════════════════

    /**
     * Route vers LlmApiService.messageWithImageJson pour les providers
     * OpenAI-compatibles (OPENAI, GROQ, GITHUB_MODELS, NVIDIA_NIM).
     *
     * **Difference avec callGroq/callMistral** : ces 2 derniers utilisent l'API
     * d'endpoint chat-completions specifique a chaque provider, hardcodes dans
     * GeminiMealService historiquement. La nouvelle voie passe par
     * LlmApiService qui :
     *  - gere les headers provider-specific (Bearer + Accept GitHub)
     *  - applique adaptive timeout (60s normal, 360s thinking)
     *  - emit telemetrie unifiee
     *  - supporte response_format=json_object (JSON garanti, fini les retours
     *    en markdown ` ```json ```)
     *
     * Pour MEAL_SCAN, l'user peut donc maintenant pick GPT-4o (GitHub Models)
     * ou Llama 3.2 Vision (NVIDIA NIM) ou Qwen 3.5 Vision (NIM) au lieu de
     * Gemini/Mistral hardcodes.
     */
    /** Variante TEXT-ONLY de [callOpenAiCompatVision] pour analyzeMealFromText
     *  et callTextLLM. Delegue a LlmApiService.messageJson. */
    private suspend fun callOpenAiCompatText(
        providerName: String,
        apiKey: String,
        model: String,
        prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant?,
    ): String {
        val provider = runCatching { LlmProvider.valueOf(providerName.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Provider inconnu : $providerName") }
        require(provider in LlmApiService.OPENAI_COMPAT_VISION_PROVIDERS) {
            "callOpenAiCompatText requiert un provider OpenAI-compat, recu $provider"
        }
        return llmApiService.messageJson(
            prompt = prompt, provider = provider, apiKey = apiKey,
            model = model, assistant = assistant,
        ).getOrElse { throw it }
    }

    private suspend fun callOpenAiCompatVision(
        providerName: String,
        apiKey: String,
        model: String,
        imageBytes: ByteArray,
        mimeType: String,
        prompt: String,
        assistant: com.shredcoach.app.domain.llm.AiAssistant?,
    ): String {
        val provider = runCatching { LlmProvider.valueOf(providerName.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Provider inconnu : $providerName") }
        require(provider in LlmApiService.OPENAI_COMPAT_VISION_PROVIDERS) {
            "callOpenAiCompatVision requiert un provider OpenAI-compat, recu $provider"
        }
        val result = llmApiService.messageWithImageJson(
            prompt = prompt,
            imageBytes = imageBytes,
            imageMimeType = mimeType,
            provider = provider,
            apiKey = apiKey,
            model = model,
            assistant = assistant,
        )
        return result.getOrElse { throw it }
    }

    private fun callMistral(
        imageBytes: ByteArray, mimeType: String, apiKey: String,
        prompt: String = MEAL_PROMPT,
        assistant: com.shredcoach.app.domain.llm.AiAssistant? = null,
    ): String {
        val startMs = System.currentTimeMillis()
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
        // Telemetrie success
        val (tIn, tOut, _) = parseOpenAiUsage(json)
        usageRecorder.record(
            assistant = assistant, provider = LlmProvider.MISTRAL, model = MISTRAL_MODEL,
            tokensInput = tIn, tokensOutput = tOut, tokensThinking = 0,
            latencyMs = System.currentTimeMillis() - startMs, success = true,
        )
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
            return enrichWithGlycemicAggregation(r)
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 1: ${e.message}")
        }

        // Tentative 2 : nettoyage des strings puis parse
        val cleaned = sanitizeJsonStrings(repaired)
        try {
            val r = gson.fromJson(cleaned, MealAnalysisResult::class.java)
            Log.d(TAG, "✓ Tentative 2 (sanitized) OK")
            return enrichWithGlycemicAggregation(r)
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 2: ${e.message}")
        }

        // Tentative 3 : Gson avec JsonReader lenient
        try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(cleaned))
            reader.isLenient = true
            val r = gson.getAdapter(MealAnalysisResult::class.java).read(reader)
            Log.d(TAG, "✓ Tentative 3 (lenient) OK")
            return enrichWithGlycemicAggregation(r)
        } catch (e: Exception) {
            Log.w(TAG, "✗ Tentative 3: ${e.message}")
        }

        // Tentative 4 : extraction manuelle
        try {
            val r = extractManual(cleaned)
            if (r != null) {
                Log.d(TAG, "✓ Tentative 4 (manual) OK — ${r.dishes.size} plats")
                return enrichWithGlycemicAggregation(r)
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Tentative 4: ${e.message}", e)
        }

        return null
    }

    /**
     * Calcule le GI/GL/catégorie/confidence agrégés au niveau du scan à partir
     * des plats individuels. Single source of truth pour la projection
     * per-dish → scan-level (cf. [com.shredcoach.app.domain.nutrition.GlycemicMath]).
     *
     * **Pourquoi en post-parse plutôt que demander au LLM les agrégats** :
     *  - Cohérence garantie : pas de risque que le LLM additionne mal les GL
     *  - Resilience : marche aussi sur les anciens LLMs/providers qui ne
     *    fournissent que les valeurs per-dish
     *  - Single source of truth : la formule pondérée vit dans GlycemicMath
     */
    private fun enrichWithGlycemicAggregation(result: MealAnalysisResult): MealAnalysisResult {
        if (result.dishes.isEmpty()) return result
        // Pair<GI per dish, carbs per dish> pour la moyenne pondérée
        val dishGis = result.dishes.map { it.glycemicIndex to it.carbs }
        val aggregatedGi = com.shredcoach.app.domain.nutrition.GlycemicMath
            .weightedAverageGi(dishGis)

        // GL raw du scan = somme additive des GL per-dish (additive sur les carbs)
        val aggregatedGl: Double? = result.dishes
            .mapNotNull { dish ->
                val gi = dish.glycemicIndex
                if (gi == null || dish.carbs <= 0.0) null
                else (gi * dish.carbs / 100.0)
            }
            .takeIf { it.isNotEmpty() }
            ?.sum()

        // Confidence agrégée : règle "chaîne la plus faible"
        val confidences = result.dishes.map {
            com.shredcoach.app.domain.nutrition.GIConfidence.fromString(it.giConfidence)
        }
        val aggregatedConfidence = com.shredcoach.app.domain.nutrition.GlycemicMath
            .aggregateConfidence(confidences)

        // Catégorie : depuis le GI agrégé
        val category = com.shredcoach.app.domain.nutrition.GICategory.fromGi(aggregatedGi)

        return result.copy(
            glycemicIndex = aggregatedGi,
            glycemicLoad = aggregatedGl,
            giCategory = if (category == com.shredcoach.app.domain.nutrition.GICategory.UNKNOWN) null else category.name,
            giConfidence = if (aggregatedConfidence == com.shredcoach.app.domain.nutrition.GIConfidence.UNKNOWN) null else aggregatedConfidence.name,
        )
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
                    glycemicIndex = try {
                        d.get("glycemic_index")?.takeIf { !it.isJsonNull }?.asInt
                    } catch (_: Exception) { null },
                    giConfidence = try {
                        d.get("gi_confidence")?.takeIf { !it.isJsonNull }?.asString
                    } catch (_: Exception) { null },
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
