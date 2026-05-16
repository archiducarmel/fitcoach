package com.shredcoach.app.domain.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Catalogue des outils que Shreddy peut appeler via function-calling LLM.
 *
 * **Pourquoi tool calling** : sans tools, le bot ne peut que SUGGÉRER une
 * action ("tu devrais logger ce repas"). Avec tools, il peut **exécuter**
 * directement l'action en DB. Saut qualitatif majeur — c'est ce qui sépare
 * un chatbot générique d'un vrai assistant.
 *
 * **Format** : OpenAI tools schema (`type: "function"`, `function: { name, description, parameters }`).
 * Compatible Groq + OpenAI natively. Pour Claude, on convertit au format
 * `{ name, description, input_schema }` (cf. [LlmApiService] sérialisation).
 *
 * **Conception V1** — 4 tools concrets, hauts impacts :
 *  - [LOG_MEAL] : repas explicitement décrit par l'user ("j'ai mangé 200g de poulet")
 *  - [SET_WEIGHT] : "je pèse 87 kg ce matin" → enregistré dans weight_logs
 *  - [GET_TODAY_STATS] : "où j'en suis aujourd'hui ?" → fresh data, contourne le
 *    contexte stale entre les turns (résout en partie le problème awareness)
 *  - [GET_RECENT_WORKOUTS] : "rappelle-moi ce que j'ai fait cette semaine"
 *
 * **Pas dans V1** :
 *  - schedule_workout : nécessite un mapping nom→workoutId complexe
 *  - log_workout_set : trop d'arguments (exoId, weight, reps, restSec…), UX dédiée existe
 */
object ShreddyTools {

    // ─── Identifiants (sources de vérité utilisées en parse + exec) ───
    const val LOG_MEAL = "log_meal"
    const val SET_WEIGHT = "set_weight"
    const val GET_TODAY_STATS = "get_today_stats"
    const val GET_RECENT_WORKOUTS = "get_recent_workouts"

    // ─── Tools Dr. Glykos (lecture glucose uniquement, V44+) ───
    const val GET_GLUCOSE_TODAY = "get_glucose_today"
    const val GET_GLUCOSE_RANGE_SUMMARY = "get_glucose_range_summary"
    const val GET_GLUCOSE_CORRELATIONS = "get_glucose_correlations"

    /**
     * Schémas JSON (format OpenAI tools) que l'on envoie au LLM dans la
     * requête. Le LLM s'en sert pour décider quand/comment appeler.
     *
     * Convention : `description` doit être assez explicite pour que le LLM
     * comprenne SANS exemple ; les noms de paramètres sont snake_case (norme
     * OpenAI). Pas d'enum lourd — on validera côté executor.
     */
    val ALL_OPENAI: List<JsonObject> by lazy { buildList { add(logMealSchema()); add(setWeightSchema()); add(getTodayStatsSchema()); add(getRecentWorkoutsSchema()) } }

    /** Variante Claude : même semantics, schema wrapping différent. */
    val ALL_CLAUDE: List<JsonObject> by lazy { ALL_OPENAI.map { toAnthropicSchema(it) } }

    /**
     * Sous-ensemble Dr. Glykos : pas de log_meal / set_weight (Shreddy gère
     * ces actions). Uniquement de la lecture pour analyse endocrino.
     * Contient aussi `get_today_stats` car Dr. Glykos a besoin du contexte
     * nutrition pour corréler glucose ↔ repas.
     */
    val DR_GLYKOS_OPENAI: List<JsonObject> by lazy {
        buildList {
            add(getGlucoseTodaySchema())
            add(getGlucoseRangeSummarySchema())
            add(getGlucoseCorrelationsSchema())
            add(getTodayStatsSchema())
        }
    }
    val DR_GLYKOS_CLAUDE: List<JsonObject> by lazy { DR_GLYKOS_OPENAI.map { toAnthropicSchema(it) } }

    // ═══════════════════════════════════════════════════════════
    // Schémas individuels — format OpenAI
    // ═══════════════════════════════════════════════════════════

    private fun logMealSchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$LOG_MEAL",
            "description": "Enregistre un repas dans le journal nutrition de l'utilisateur. À utiliser quand l'utilisateur décrit explicitement ce qu'il vient de manger ou ce qu'il a mangé (ex: 'j'ai pris 200g de poulet et du riz au déjeuner'). NE PAS l'utiliser si l'utilisateur demande seulement conseil ou veut connaître ses kcal.",
            "parameters": {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "Nom court du repas ou de l'aliment principal (ex: 'Poulet riz brocolis')"
                },
                "calories": {
                  "type": "number",
                  "description": "Calories totales estimées du repas en kcal"
                },
                "proteins_g": {
                  "type": "number",
                  "description": "Protéines en grammes (estimation)"
                },
                "carbs_g": {
                  "type": "number",
                  "description": "Glucides en grammes (estimation)"
                },
                "fats_g": {
                  "type": "number",
                  "description": "Lipides en grammes (estimation)"
                },
                "meal_type": {
                  "type": "string",
                  "description": "Type de repas : BREAKFAST, LUNCH, SNACK, DINNER. Déduire de l'heure courante si non précisé."
                }
              },
              "required": ["name", "calories", "meal_type"]
            }
          }
        }
    """.trimIndent()).asJsonObject

    private fun setWeightSchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$SET_WEIGHT",
            "description": "Enregistre une nouvelle mesure de poids dans le journal de suivi de poids. À utiliser quand l'utilisateur communique son poids actuel (ex: 'je pèse 87,3 kg ce matin', 'j'ai pris 200g').",
            "parameters": {
              "type": "object",
              "properties": {
                "weight_kg": {
                  "type": "number",
                  "description": "Poids en kilogrammes (peut être décimal, ex: 87.3)"
                }
              },
              "required": ["weight_kg"]
            }
          }
        }
    """.trimIndent()).asJsonObject

    private fun getTodayStatsSchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$GET_TODAY_STATS",
            "description": "Récupère un snapshot FRAIS des stats nutrition et sport de l'utilisateur pour AUJOURD'HUI : calories ingérées vs cible, repas déjà loggés, séance prévue/faite, delta, pattern comportemental. À utiliser dès que l'utilisateur pose une question sur 'aujourd'hui', 'ce que j'ai mangé', 'combien je dois encore manger', ou si tu as besoin de données fraîches pour répondre précisément. Préfère cet appel au début du turn plutôt que de répondre à l'aveugle.",
            "parameters": { "type": "object", "properties": {} }
          }
        }
    """.trimIndent()).asJsonObject

    private fun getRecentWorkoutsSchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$GET_RECENT_WORKOUTS",
            "description": "Récupère les 5 dernières séances complétées (nom, date, volume, durée, top exos). À utiliser pour répondre à 'qu'est-ce que j'ai fait cette semaine ?', 'rappelle-moi ma dernière séance', 'quels muscles j'ai bossé récemment ?'.",
            "parameters": { "type": "object", "properties": {} }
          }
        }
    """.trimIndent()).asJsonObject

    private fun getGlucoseTodaySchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$GET_GLUCOSE_TODAY",
            "description": "Récupère les métriques glycémiques (CGM) du JOUR : moyenne mg/dL, pic + heure, min + heure, time-in-range %, nb hypoglycémies. À utiliser dès que l'user demande son état glycémique du jour ou pour analyser un repas/séance récente.",
            "parameters": { "type": "object", "properties": {} }
          }
        }
    """.trimIndent()).asJsonObject

    private fun getGlucoseRangeSummarySchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$GET_GLUCOSE_RANGE_SUMMARY",
            "description": "Récupère un agrégat glycémique sur une fenêtre temporelle (typiquement 7 ou 30 jours) : avg mg/dL, TIR%, CV%, slope tendance, pattern dominant (POSTPRANDIAL_SPIKES / DAWN_PHENOMENON / HIGH_VARIABILITY / STABLE_OPTIMAL / etc.). À utiliser pour interpréter des tendances long terme.",
            "parameters": {
              "type": "object",
              "properties": {
                "days": {
                  "type": "integer",
                  "description": "Largeur de la fenêtre en jours (typiquement 7 ou 30)"
                }
              },
              "required": ["days"]
            }
          }
        }
    """.trimIndent()).asJsonObject

    private fun getGlucoseCorrelationsSchema(): JsonObject = JsonParser.parseString("""
        {
          "type": "function",
          "function": {
            "name": "$GET_GLUCOSE_CORRELATIONS",
            "description": "Croise les pics glycémiques d'un jour donné avec les repas et séances loggés à proximité (±120 min). Retourne une liste structurée des associations détectées (ex: 'pic 195 mg/dL à 13h32 ↔ repas pâtes/sauce à 12h45'). À utiliser pour analyser ce qui a causé un pic ou une baisse.",
            "parameters": {
              "type": "object",
              "properties": {
                "date": {
                  "type": "string",
                  "description": "Date au format ISO YYYY-MM-DD. Si vide, utilise aujourd'hui."
                }
              }
            }
          }
        }
    """.trimIndent()).asJsonObject

    /**
     * Conversion OpenAI → Anthropic format. Anthropic attend
     * `{name, description, input_schema}` au top level, pas wrapped dans `function`.
     */
    private fun toAnthropicSchema(openAi: JsonObject): JsonObject {
        val fn = openAi.getAsJsonObject("function")
        val out = JsonObject()
        out.addProperty("name", fn.get("name").asString)
        out.addProperty("description", fn.get("description").asString)
        out.add("input_schema", fn.getAsJsonObject("parameters"))
        return out
    }
}

/**
 * Représentation parsée d'un appel de tool demandé par le LLM. Le caller
 * exécute l'action puis renvoie un [ToolResult] dans le tour suivant.
 */
data class ToolCall(
    /** ID unique de l'appel (fourni par le LLM, à renvoyer dans le résultat). */
    val id: String,
    /** Nom du tool (cf. [ShreddyTools.LOG_MEAL] etc.). */
    val name: String,
    /** Arguments JSON bruts — le caller parse selon le tool. */
    val argumentsJson: String,
)

/**
 * Résultat de l'exécution d'un tool. Sera renvoyé au LLM dans le tour
 * suivant pour qu'il intègre l'information dans sa réponse finale.
 */
data class ToolResult(
    val toolCallId: String,
    val name: String,
    /** Contenu du résultat (texte ou JSON) — visible par le LLM. */
    val content: String,
)
