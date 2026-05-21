package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.remote.LlmProvider

/**
 * Catalogue des modèles LLM disponibles par provider, avec leurs capacités.
 *
 * **Source de vérité** pour :
 *  - L'UI Settings (filtrer les modèles selon les besoins d'un assistant)
 *  - Le resolver (valider qu'un override est cohérent avec les capacités requises)
 *  - Les presets (sélectionner le modèle "premium" / "économique" par provider)
 *
 * **Maintenir cette liste à jour** quand un nouveau modèle est annoncé par un
 * provider. Une entrée obsolète n'empêche pas de fonctionner (le model id passe
 * en string à l'API) mais l'UI peut proposer du dead-code.
 */
data class LlmModelInfo(
    /** Model ID à passer à l'API (e.g., "gemini-3-flash-preview"). */
    val id: String,
    /** Nom court affiché à l'user (e.g., "Gemini 3 Flash (Preview)"). */
    val displayName: String,
    val supportsVision: Boolean = true,
    val supportsJsonMode: Boolean = true,
    /** Tier : ECONOMIC / STANDARD / PREMIUM — drive les presets. */
    val tier: LlmTier = LlmTier.STANDARD,
    /** Notes courtes (max 60 chars) pour info utilisateur dans le picker. */
    val notes: String = "",
)

enum class LlmTier { ECONOMIC, STANDARD, PREMIUM }

object LlmCatalog {

    /**
     * Modèles disponibles par provider. Le 1er modèle de chaque liste est le
     * défaut recommandé pour ce provider.
     */
    val byProvider: Map<LlmProvider, List<LlmModelInfo>> = mapOf(
        LlmProvider.GEMINI to listOf(
            LlmModelInfo(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                tier = LlmTier.STANDARD,
                notes = "Rapide, économique, défaut recommandé",
            ),
            LlmModelInfo(
                id = "gemini-3-flash-preview",
                displayName = "Gemini 3 Flash (Preview)",
                tier = LlmTier.PREMIUM,
                notes = "Preview public, reasoning étendu",
            ),
            LlmModelInfo(
                id = "gemini-3.5-flash",
                displayName = "Gemini 3.5 Flash",
                tier = LlmTier.PREMIUM,
                notes = "Reasoning premium (lent, 5× plus cher)",
            ),
            LlmModelInfo(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                tier = LlmTier.ECONOMIC,
                notes = "Legacy, ultra-rapide",
            ),
        ),
        LlmProvider.GROQ to listOf(
            LlmModelInfo(
                id = "openai/gpt-oss-120b",
                displayName = "GPT-OSS 120B",
                supportsVision = false,
                tier = LlmTier.STANDARD,
                notes = "Open-source 120B, très rapide",
            ),
            LlmModelInfo(
                id = "meta-llama/llama-4-scout-17b-16e-instruct",
                displayName = "Llama 4 Scout 17B",
                supportsVision = true,
                tier = LlmTier.ECONOMIC,
                notes = "Vision-capable, économique",
            ),
            LlmModelInfo(
                id = "llama-3.3-70b-versatile",
                displayName = "Llama 3.3 70B Versatile",
                supportsVision = false,
                tier = LlmTier.STANDARD,
                notes = "Tâches longues, polyvalent",
            ),
        ),
        LlmProvider.MISTRAL to listOf(
            LlmModelInfo(
                id = "mistral-small-latest",
                displayName = "Mistral Small",
                supportsVision = true,
                tier = LlmTier.STANDARD,
                notes = "Vision + JSON, latence faible",
            ),
            LlmModelInfo(
                id = "pixtral-large-latest",
                displayName = "Pixtral Large",
                supportsVision = true,
                tier = LlmTier.PREMIUM,
                notes = "Vision premium",
            ),
        ),
        LlmProvider.OPENAI to listOf(
            LlmModelInfo(
                id = "gpt-4o-mini",
                displayName = "GPT-4o mini",
                supportsVision = true,
                tier = LlmTier.STANDARD,
                notes = "Économique, vision et chat",
            ),
            LlmModelInfo(
                id = "gpt-4o",
                displayName = "GPT-4o",
                supportsVision = true,
                tier = LlmTier.PREMIUM,
                notes = "Premium, qualité maximale",
            ),
            LlmModelInfo(
                id = "gpt-4.1-mini",
                displayName = "GPT-4.1 mini",
                supportsVision = true,
                tier = LlmTier.STANDARD,
                notes = "Successeur 4o-mini",
            ),
        ),
        LlmProvider.CLAUDE to listOf(
            LlmModelInfo(
                id = "claude-sonnet-4-20250514",
                displayName = "Claude Sonnet 4",
                supportsVision = true,
                tier = LlmTier.STANDARD,
                notes = "Reasoning premium",
            ),
            LlmModelInfo(
                id = "claude-haiku-4-20250514",
                displayName = "Claude Haiku 4",
                supportsVision = true,
                tier = LlmTier.ECONOMIC,
                notes = "Rapide, économique",
            ),
            LlmModelInfo(
                id = "claude-opus-4-20250514",
                displayName = "Claude Opus 4",
                supportsVision = true,
                tier = LlmTier.PREMIUM,
                notes = "Haut de gamme, très cher",
            ),
        ),
    )

    /** Modèles disponibles pour un provider (vide si provider inconnu). */
    fun modelsFor(provider: LlmProvider): List<LlmModelInfo> =
        byProvider[provider] ?: emptyList()

    /** Récupère le LlmModelInfo correspondant à un model id (any provider). */
    fun modelInfo(modelId: String): LlmModelInfo? =
        byProvider.values.flatten().firstOrNull { it.id == modelId }

    /**
     * Providers capables de vision (utile pour filtrer dans l'UI quand
     * l'assistant a needsVision=true). Un provider est vision-capable si au
     * moins un de ses modèles déclare `supportsVision=true`.
     */
    fun visionCapableProviders(): List<LlmProvider> =
        byProvider.entries
            .filter { (_, models) -> models.any { it.supportsVision } }
            .map { it.key }

    /** Modèles vision pour un provider donné. */
    fun visionModelsFor(provider: LlmProvider): List<LlmModelInfo> =
        modelsFor(provider).filter { it.supportsVision }

    /** Préselection par tier (utile pour les presets one-tap). */
    fun pickByTier(provider: LlmProvider, tier: LlmTier): LlmModelInfo? =
        modelsFor(provider).firstOrNull { it.tier == tier }
            ?: modelsFor(provider).firstOrNull()
}
