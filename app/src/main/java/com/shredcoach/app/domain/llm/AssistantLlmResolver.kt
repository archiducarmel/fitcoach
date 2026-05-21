package com.shredcoach.app.domain.llm

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Résultat de la résolution d'un assistant : tuple (provider, model id).
 */
data class ResolvedLlmConfig(
    val provider: LlmProvider,
    val modelId: String,
)

/**
 * Résolveur central qui mappe un [AiAssistant] vers la config LLM effective
 * (provider + model id) à utiliser maintenant.
 *
 * **Algorithme de résolution** (en cascade) :
 *  1. Lit `profile.llmAssistantOverridesJson` (map JSON {key: {provider, model}})
 *  2. Si la clé de l'assistant est présente ET parseable → retourne l'override
 *  3. Sinon, fallback sur `AiAssistant.fallbackLegacy` :
 *     - `MEAL_SCAN` : `(profile.mealScanProvider, profile.geminiModel)`
 *     - `CHAT` : `(profile.llmProvider, profile.llmModel || provider.defaultModel)`
 *     - `HARDCODED_GEMINI_25` : `(GEMINI, "gemini-2.5-flash")`
 *
 * Cette cascade garantit que **les users qui n'ouvrent jamais l'écran de config
 * "Assistants IA" ne voient AUCUN changement** : les valeurs résolues sont
 * exactement celles que le code utilisait avant ce refactor.
 *
 * **Caching** : pas de cache interne. La résolution est légère (JSON parse <1ms
 * sur une map de 19 entrées max) et les callers peuvent stocker le résultat
 * localement si pertinent pour leur durée de vie.
 */
@Singleton
class AssistantLlmResolver @Inject constructor(
    private val userRepository: UserRepository,
) {
    /**
     * Résout la config pour [assistant]. Lit le profile une fois et retourne.
     * Si le profile n'existe pas (onboarding en cours), retombe sur les
     * defaults hardcodés équivalents au comportement avant evolution.
     */
    suspend fun resolve(assistant: AiAssistant): ResolvedLlmConfig {
        val profile = runCatching { userRepository.getUserProfileOnce() }.getOrNull()
        return resolveWithProfile(assistant, profile)
    }

    /**
     * Version pure (testable) qui prend le profile en param. Utile pour les
     * callers qui ont déjà chargé le profile et veulent éviter une 2e query.
     */
    fun resolveWithProfile(
        assistant: AiAssistant,
        profile: UserProfileEntity?,
    ): ResolvedLlmConfig {
        // ─── 1. Vérifier override per-assistant ──────────────────────────
        if (profile != null) {
            val override = parseOverride(profile.llmAssistantOverridesJson, assistant.key)
            if (override != null) {
                return override
            }
        }

        // ─── 2. Fallback legacy selon la catégorie historique ────────────
        return when (assistant.fallbackLegacy) {
            LegacyConfigSource.MEAL_SCAN -> resolveLegacyMealScan(profile)
            LegacyConfigSource.CHAT -> resolveLegacyChat(profile)
            LegacyConfigSource.HARDCODED_GEMINI_25 -> ResolvedLlmConfig(
                provider = LlmProvider.GEMINI,
                modelId = HARDCODED_GEMINI_MODEL,
            )
        }
    }

    private fun resolveLegacyMealScan(profile: UserProfileEntity?): ResolvedLlmConfig {
        val providerName = profile?.mealScanProvider ?: "GEMINI"
        val provider = runCatching { LlmProvider.valueOf(providerName) }.getOrDefault(LlmProvider.GEMINI)
        val modelId = profile?.geminiModel?.takeIf { it.isNotBlank() } ?: HARDCODED_GEMINI_MODEL
        return ResolvedLlmConfig(provider, modelId)
    }

    private fun resolveLegacyChat(profile: UserProfileEntity?): ResolvedLlmConfig {
        val providerName = profile?.llmProvider ?: "GROQ"
        val provider = runCatching { LlmProvider.valueOf(providerName) }.getOrDefault(LlmProvider.GROQ)
        val modelId = profile?.llmModel?.takeIf { it.isNotBlank() } ?: provider.defaultModel
        return ResolvedLlmConfig(provider, modelId)
    }

    /**
     * Parse un override depuis le JSON. Retourne null si :
     *  - JSON invalide
     *  - Clé absente
     *  - Provider absent / invalide
     *  - Model id absent / vide
     *
     * Robuste aux variations du JSON (espace, ordre des champs, valeurs null).
     */
    private fun parseOverride(json: String, key: String): ResolvedLlmConfig? {
        if (json.isBlank() || json == "{}") return null
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val entry = root.get(key)?.takeIf { !it.isJsonNull }?.asJsonObject ?: return null
            val providerName = entry.get("provider")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val modelId = entry.get("model")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val provider = runCatching { LlmProvider.valueOf(providerName) }.getOrNull() ?: return null
            ResolvedLlmConfig(provider, modelId)
        }.onFailure {
            Log.w(TAG, "Failed to parse override for $key: ${it.message}")
        }.getOrNull()
    }

    /**
     * Écrit un override pour [assistant] dans le JSON map. Si `config` est
     * null, supprime l'override (reset au défaut). Retourne le nouveau JSON
     * à persister sur `UserProfileEntity.llmAssistantOverridesJson`.
     *
     * **Pas de side effect** : pure function, le caller fait l'update DB.
     */
    fun writeOverride(
        currentJson: String,
        assistant: AiAssistant,
        config: ResolvedLlmConfig?,
    ): String {
        val root: JsonObject = runCatching {
            if (currentJson.isBlank()) JsonObject()
            else JsonParser.parseString(currentJson).asJsonObject
        }.getOrDefault(JsonObject())

        if (config == null) {
            root.remove(assistant.key)
        } else {
            val entry = JsonObject().apply {
                addProperty("provider", config.provider.name)
                addProperty("model", config.modelId)
            }
            root.add(assistant.key, entry)
        }
        return root.toString()
    }

    /**
     * Vide TOUS les overrides → tous les assistants reviennent au comportement
     * legacy. Utilisé par le bouton "Reset tous les défauts" du Settings.
     */
    fun resetAll(): String = "{}"

    companion object {
        private const val TAG = "AssistantLlmResolver"
        private const val HARDCODED_GEMINI_MODEL = "gemini-2.5-flash"
    }
}
