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

    /**
     * Resout le fallback LLM pour [assistant]. Retourne null si :
     *  - Aucun fallback configure par l'user (cas par defaut, back-compat absolue)
     *  - Le JSON est invalide ou la cle absente
     *
     * **Difference cle avec resolveWithProfile** : pas de cascade de fallback
     * legacy. Le fallback est PUREMENT opt-in via UI. Si l'user n'a rien
     * configure, on retourne null et le service propage l'erreur primary normalement.
     */
    fun resolveFallbackWithProfile(
        assistant: AiAssistant,
        profile: UserProfileEntity?,
    ): ResolvedLlmConfig? {
        if (profile == null) return null
        return parseFallback(profile.llmAssistantOverridesJson, assistant.key)
    }

    suspend fun resolveFallback(assistant: AiAssistant): ResolvedLlmConfig? {
        val profile = runCatching { userRepository.getUserProfileOnce() }.getOrNull()
        return resolveFallbackWithProfile(assistant, profile)
    }

    /**
     * Helper qui construit directement un [com.shredcoach.app.domain.llm.FallbackConfig]
     * pret a etre passe a un service LLM. Retourne null si :
     *  - Aucun fallback configure pour [assistant]
     *  - L'apiKey fourni est blank (caller doit la fetch depuis le bon slot)
     *
     * **Pourquoi le caller passe l'apiKey** : le slot SecureKeyStore depend du
     * provider (GEMINI/GROQ_MEAL/MISTRAL pour vision, LLM unique pour chat).
     * Le caller a la responsabilite de fetcher la bonne cle pour le provider
     * de fallback resolu — il connait son contexte (vision vs chat) mieux que
     * le resolver. Cf. doc des callers pour le pattern type.
     */
    fun buildFallbackConfig(
        assistant: AiAssistant,
        profile: UserProfileEntity?,
        apiKeyForFallbackProvider: String,
    ): com.shredcoach.app.domain.llm.FallbackConfig? {
        if (apiKeyForFallbackProvider.isBlank()) return null
        val fb = resolveFallbackWithProfile(assistant, profile) ?: return null
        return com.shredcoach.app.domain.llm.FallbackConfig(
            apiKey = apiKeyForFallbackProvider,
            model = fb.modelId,
            provider = fb.provider.name,
        )
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
     * **Preserve le fallback existant** : si l'user a configure un fallback
     * pour cet assistant, on garde les champs `fallback_provider`/`fallback_model`
     * intacts en n'ecrivant que les champs primary.
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
            // Reset complet : supprime la cle (fallback inclus)
            root.remove(assistant.key)
        } else {
            // Preserve le fallback existant si present
            val existing = root.get(assistant.key)?.takeIf { !it.isJsonNull }?.asJsonObject
            val fbProvider = existing?.get("fallback_provider")?.asString
            val fbModel = existing?.get("fallback_model")?.asString
            val entry = JsonObject().apply {
                addProperty("provider", config.provider.name)
                addProperty("model", config.modelId)
                if (!fbProvider.isNullOrBlank() && !fbModel.isNullOrBlank()) {
                    addProperty("fallback_provider", fbProvider)
                    addProperty("fallback_model", fbModel)
                }
            }
            root.add(assistant.key, entry)
        }
        return root.toString()
    }

    /**
     * Écrit le FALLBACK override pour [assistant]. Si `config` est null,
     * supprime le fallback (mais garde l'override primary intact si present).
     *
     * Format JSON final pour une entree : `{provider, model, fallback_provider, fallback_model}`.
     */
    fun writeFallbackOverride(
        currentJson: String,
        assistant: AiAssistant,
        config: ResolvedLlmConfig?,
    ): String {
        val root: JsonObject = runCatching {
            if (currentJson.isBlank()) JsonObject()
            else JsonParser.parseString(currentJson).asJsonObject
        }.getOrDefault(JsonObject())

        // Recupere l'entry existante OU cree une vide
        val entry = root.get(assistant.key)?.takeIf { !it.isJsonNull }?.asJsonObject ?: JsonObject()

        if (config == null) {
            entry.remove("fallback_provider")
            entry.remove("fallback_model")
        } else {
            entry.addProperty("fallback_provider", config.provider.name)
            entry.addProperty("fallback_model", config.modelId)
        }

        // Si l'entry est vide (pas de primary ni de fallback), on remove la cle
        if (entry.size() == 0) {
            root.remove(assistant.key)
        } else {
            root.add(assistant.key, entry)
        }
        return root.toString()
    }

    /**
     * Parse le fallback depuis le JSON. Symetrique a parseOverride mais sur
     * les champs `fallback_provider` / `fallback_model`. Retourne null si
     * absent ou invalide.
     */
    private fun parseFallback(json: String, key: String): ResolvedLlmConfig? {
        if (json.isBlank() || json == "{}") return null
        return runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            val entry = root.get(key)?.takeIf { !it.isJsonNull }?.asJsonObject ?: return null
            val providerName = entry.get("fallback_provider")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val modelId = entry.get("fallback_model")?.asString?.takeIf { it.isNotBlank() } ?: return null
            val provider = runCatching { LlmProvider.valueOf(providerName) }.getOrNull() ?: return null
            ResolvedLlmConfig(provider, modelId)
        }.onFailure {
            Log.w(TAG, "Failed to parse fallback for $key: ${it.message}")
        }.getOrNull()
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
