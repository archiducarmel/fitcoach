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
    /**
     * Type/modalité du modèle. Drive l'UI et le service utilisé.
     * Default LANGUAGE pour back-compat (cas le plus fréquent).
     */
    val kind: ModelKind = ModelKind.LANGUAGE,
    /** Tier : ECONOMIC / STANDARD / PREMIUM — drive les presets. */
    val tier: LlmTier = LlmTier.STANDARD,
    /** Notes courtes (max 60 chars) pour info utilisateur dans le picker. */
    val notes: String = "",

    // ───── Input modalities (drive UI debug page + filtres) ─────
    val acceptsTextInput: Boolean = true,
    val acceptsImageInput: Boolean = false,
    val acceptsAudioInput: Boolean = false,
    val acceptsVideoInput: Boolean = false,
    /** Back-compat : alias de acceptsImageInput pour le code legacy. */
    val supportsVision: Boolean = false,

    // ───── Capability flags (orthogonales au kind) ─────
    /** Tokens de raisonnement étendus (o1/o3, Gemini Thinking, DeepSeek-v4-pro). */
    val supportsThinking: Boolean = false,
    /** Function calling natif (OpenAI tools, Anthropic tools). */
    val supportsToolCalling: Boolean = false,
    /** SSE streaming. Default true pour les LANGUAGE/VLM. */
    val supportsStreaming: Boolean = true,
    /** JSON output garanti (response_format). */
    val supportsJsonMode: Boolean = true,
    /** Designed for multi-step agentic workflows. */
    val supportsAgentic: Boolean = false,
    /** Spécialisé code generation (Codestral, StarCoder, etc.). */
    val supportsCodeGen: Boolean = false,
    /** Spécialisé translation (Riva-translate, Canary multilingual). */
    val supportsTranslation: Boolean = false,

    // ───── Metadata (drive filtres avancés + badges UI) ─────
    /** Architecture sous-jacente. */
    val architecture: ModelArchitecture = ModelArchitecture.UNKNOWN,
    /** Source des poids (open/closed). */
    val weightsSource: WeightsSource = WeightsSource.UNKNOWN,
    /** Domaine d'expertise principal. */
    val domain: ModelDomain = ModelDomain.GENERAL,
    /** Région d'origine du publisher. */
    val originRegion: ModelOriginRegion = ModelOriginRegion.UNKNOWN,
    /** Publisher pour les catalogues multi-publisher (GitHub, NIM). */
    val publisher: String? = null,
    /** Tier de rate-limit pour GitHub Models : "low" / "high" / "custom". */
    val rateLimitTier: String? = null,
    /** Gated (Copilot Pro+ requis sur GitHub Models). */
    val isGated: Boolean = false,
    /** Context window max (tokens). 0 = inconnu. */
    val maxContextTokens: Int = 0,
    /** Nombre de paramètres en milliards. 0 = inconnu. Ex: 70 pour 70B. */
    val parameterCountBillions: Double = 0.0,
    /** Année de release publique. 0 = inconnu. */
    val releaseYear: Int = 0,
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
                kind = ModelKind.VLM,
                tier = LlmTier.STANDARD,
                notes = "Rapide, économique, défaut recommandé",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "google",
                maxContextTokens = 1_048_576,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "gemini-3-flash-preview",
                displayName = "Gemini 3 Flash (Preview)",
                kind = ModelKind.VLM,
                tier = LlmTier.PREMIUM,
                notes = "Preview public, reasoning étendu",
                acceptsImageInput = true,
                supportsVision = true,
                supportsThinking = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "google",
                maxContextTokens = 1_048_576,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "gemini-3.5-flash",
                displayName = "Gemini 3.5 Flash",
                kind = ModelKind.VLM,
                tier = LlmTier.PREMIUM,
                notes = "Reasoning premium (lent, 5× plus cher)",
                acceptsImageInput = true,
                supportsVision = true,
                supportsThinking = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "google",
                maxContextTokens = 1_048_576,
                releaseYear = 2026,
            ),
            LlmModelInfo(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                kind = ModelKind.VLM,
                tier = LlmTier.ECONOMIC,
                notes = "Legacy, ultra-rapide",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "google",
                maxContextTokens = 1_048_576,
                releaseYear = 2024,
            ),
        ),
        LlmProvider.GROQ to listOf(
            LlmModelInfo(
                id = "openai/gpt-oss-120b",
                displayName = "GPT-OSS 120B",
                kind = ModelKind.LANGUAGE,
                tier = LlmTier.STANDARD,
                notes = "Open-weights 120B, très rapide sur Groq",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "openai",
                maxContextTokens = 131_072,
                parameterCountBillions = 120.0,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-4-scout-17b-16e-instruct",
                displayName = "Llama 4 Scout 17B",
                kind = ModelKind.VLM,
                tier = LlmTier.ECONOMIC,
                notes = "MoE 17B/109B, vision, économique",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "meta",
                maxContextTokens = 131_072,
                parameterCountBillions = 17.0,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "llama-3.3-70b-versatile",
                displayName = "Llama 3.3 70B Versatile",
                kind = ModelKind.LANGUAGE,
                tier = LlmTier.STANDARD,
                notes = "Tâches longues, polyvalent",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "meta",
                maxContextTokens = 131_072,
                parameterCountBillions = 70.0,
                releaseYear = 2024,
            ),
        ),
        LlmProvider.MISTRAL to listOf(
            LlmModelInfo(
                id = "mistral-small-latest",
                displayName = "Mistral Small",
                kind = ModelKind.VLM,
                tier = LlmTier.STANDARD,
                notes = "Vision + JSON, latence faible",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.FRANCE,
                publisher = "mistralai",
                maxContextTokens = 128_000,
                parameterCountBillions = 24.0,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "pixtral-large-latest",
                displayName = "Pixtral Large",
                kind = ModelKind.VLM,
                tier = LlmTier.PREMIUM,
                notes = "Vision premium 124B",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.FRANCE,
                publisher = "mistralai",
                maxContextTokens = 128_000,
                parameterCountBillions = 124.0,
                releaseYear = 2024,
            ),
        ),
        LlmProvider.OPENAI to listOf(
            LlmModelInfo(
                id = "gpt-4o-mini",
                displayName = "GPT-4o mini",
                kind = ModelKind.VLM,
                tier = LlmTier.STANDARD,
                notes = "Économique, vision et chat",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "openai",
                maxContextTokens = 128_000,
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "gpt-4o",
                displayName = "GPT-4o",
                kind = ModelKind.VLM,
                tier = LlmTier.PREMIUM,
                notes = "Premium, qualité maximale",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "openai",
                maxContextTokens = 128_000,
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "gpt-4.1-mini",
                displayName = "GPT-4.1 mini",
                kind = ModelKind.VLM,
                tier = LlmTier.STANDARD,
                notes = "Successeur 4o-mini",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "openai",
                maxContextTokens = 1_047_576,
                releaseYear = 2025,
            ),
        ),
        LlmProvider.CLAUDE to listOf(
            LlmModelInfo(
                id = "claude-sonnet-4-20250514",
                displayName = "Claude Sonnet 4",
                kind = ModelKind.VLM,
                tier = LlmTier.STANDARD,
                notes = "Reasoning premium",
                acceptsImageInput = true,
                supportsVision = true,
                supportsThinking = true,
                supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "anthropic",
                maxContextTokens = 200_000,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "claude-haiku-4-20250514",
                displayName = "Claude Haiku 4",
                kind = ModelKind.VLM,
                tier = LlmTier.ECONOMIC,
                notes = "Rapide, économique",
                acceptsImageInput = true,
                supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "anthropic",
                maxContextTokens = 200_000,
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "claude-opus-4-20250514",
                displayName = "Claude Opus 4",
                kind = ModelKind.VLM,
                tier = LlmTier.PREMIUM,
                notes = "Haut de gamme, très cher",
                acceptsImageInput = true,
                supportsVision = true,
                supportsThinking = true,
                supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US,
                publisher = "anthropic",
                maxContextTokens = 200_000,
                releaseYear = 2025,
            ),
        ),
    )

    /** Modèles disponibles pour un provider (vide si provider inconnu). */
    fun modelsFor(provider: LlmProvider): List<LlmModelInfo> =
        byProvider[provider] ?: emptyList()

    /** Récupère le LlmModelInfo correspondant à un model id (any provider). */
    fun modelInfo(modelId: String): LlmModelInfo? =
        byProvider.values.flatten().firstOrNull { it.id == modelId }

    /** Tous les modèles d'un kind donné, tous providers confondus. */
    fun modelsForKind(kind: ModelKind): List<Pair<LlmProvider, LlmModelInfo>> =
        byProvider.entries.flatMap { (provider, models) ->
            models.filter { it.kind == kind }.map { provider to it }
        }

    /** Modèles d'un provider filtres par kind. */
    fun modelsFor(provider: LlmProvider, kind: ModelKind): List<LlmModelInfo> =
        modelsFor(provider).filter { it.kind == kind }

    /** Kinds offerts par un provider (utile pour le filtrage UI). */
    fun kindsAvailableFor(provider: LlmProvider): Set<ModelKind> =
        modelsFor(provider).map { it.kind }.toSet()

    /** Providers qui offrent au moins un modèle d'un kind donné. */
    fun providersForKind(kind: ModelKind): List<LlmProvider> =
        byProvider.entries.filter { (_, models) -> models.any { it.kind == kind } }.map { it.key }

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
