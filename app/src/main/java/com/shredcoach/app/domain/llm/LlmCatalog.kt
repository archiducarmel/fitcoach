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

        // ─────────────────────────────────────────────────────────────────
        // ───── GITHUB MODELS : proxy unifie OpenAI-compatible ────────────
        // ─────────────────────────────────────────────────────────────────
        // Auth: Bearer ghp_xxx (PAT GitHub) + Accept: application/vnd.github+json
        // Free tier (rate-limited). Modeles GATED (Copilot Pro+ requis) marques.
        // Le 1er modele = defaut recommande pour cette provider.
        // ─────────────────────────────────────────────────────────────────
        LlmProvider.GITHUB_MODELS to listOf(
            // ── OpenAI famille (chat + reasoning) ────────────────────────
            LlmModelInfo(
                id = "openai/gpt-4o-mini",
                displayName = "GPT-4o mini",
                kind = ModelKind.VLM,
                tier = LlmTier.ECONOMIC,
                notes = "Multimodal economique · defaut GitHub",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 128_000, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "openai/gpt-4o",
                displayName = "GPT-4o",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Multimodal premium · texte, image, audio, video",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 128_000, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "openai/gpt-4.1",
                displayName = "GPT-4.1",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "1M de contexte · instruction-following precise",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 1_047_576, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "openai/gpt-4.1-mini",
                displayName = "GPT-4.1 mini",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "1M contexte · economique",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 1_047_576, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "openai/gpt-4.1-nano",
                displayName = "GPT-4.1 nano",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Ultra-rapide classification haut volume",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 1_047_576, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "openai/gpt-5",
                displayName = "GPT-5",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Flagship 2026 · gated Copilot Pro+",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                supportsAgentic = true, supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2026, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/gpt-5-chat",
                displayName = "GPT-5 Chat",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Conversationnel premium · gated",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2026, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/gpt-5-mini",
                displayName = "GPT-5 mini",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Reduit en latence · gated",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2026, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/gpt-5-nano",
                displayName = "GPT-5 nano",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Ultra-leger · gated",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2026, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/o1",
                displayName = "o1",
                kind = ModelKind.LANGUAGE, tier = LlmTier.PREMIUM,
                notes = "Reasoning profond · math/code · gated",
                supportsThinking = true, supportsToolCalling = false,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2024, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/o1-mini",
                displayName = "o1 mini",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Reasoning STEM economique · gated",
                supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 128_000, releaseYear = 2024, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/o3",
                displayName = "o3",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Reasoning avance · vision + outils · gated",
                acceptsImageInput = true, supportsVision = true,
                supportsThinking = true, supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2025, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/o3-mini",
                displayName = "o3 mini",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Reasoning math/sciences · gated",
                supportsThinking = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2025, isGated = true,
                rateLimitTier = "custom",
            ),
            LlmModelInfo(
                id = "openai/o4-mini",
                displayName = "o4 mini",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Derniere gen reasoning compact · gated",
                acceptsImageInput = true, supportsVision = true,
                supportsThinking = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 200_000, releaseYear = 2026, isGated = true,
                rateLimitTier = "custom",
            ),

            // ── Meta Llama famille ────────────────────────────────────────
            LlmModelInfo(
                id = "meta-llama/llama-4-scout-17b-16e-instruct",
                displayName = "Llama 4 Scout 17B",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "MoE multimodal · 5 images",
                acceptsImageInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 17.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-4-maverick-17b-128e-instruct",
                displayName = "Llama 4 Maverick 17B",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "MoE 128 experts · multimodal long contexte",
                acceptsImageInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 524_288, parameterCountBillions = 17.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.3-70b-instruct",
                displayName = "Llama 3.3 70B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Conversation experte · multilingue (8 langues)",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 70.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.2-90b-vision-instruct",
                displayName = "Llama 3.2 90B Vision",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Vision detaillee · graphiques, documents",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 90.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.2-11b-vision-instruct",
                displayName = "Llama 3.2 11B Vision",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Vision legere · graphiques/documents",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 11.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.2-3b-instruct",
                displayName = "Llama 3.2 3B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Compact pour mobile/embarque",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 3.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.2-1b-instruct",
                displayName = "Llama 3.2 1B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Ultra-compact IoT/watches",
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 1.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.1-70b-instruct",
                displayName = "Llama 3.1 70B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Multilingue 8 langues · long doc",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 70.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta-llama/llama-3.1-8b-instruct",
                displayName = "Llama 3.1 8B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Equilibre vitesse/qualite haut volume",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 8.0, releaseYear = 2024,
            ),

            // ── Mistral (sur GitHub Models) ───────────────────────────────
            LlmModelInfo(
                id = "mistral-ai/mistral-medium-2505",
                displayName = "Mistral Medium 2505",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Multimodal frontier abordable",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.FRANCE, publisher = "mistral-ai",
                maxContextTokens = 128_000, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "mistral-ai/mistral-small-2503",
                displayName = "Mistral Small 2503",
                kind = ModelKind.VLM, tier = LlmTier.ECONOMIC,
                notes = "Multimodal compact · mars 2025",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.FRANCE, publisher = "mistral-ai",
                maxContextTokens = 128_000, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "mistral-ai/ministral-3b",
                displayName = "Ministral 3B",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Edge ultra-compact · smartphone/IoT",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.FRANCE, publisher = "mistral-ai",
                maxContextTokens = 128_000, parameterCountBillions = 3.0, releaseYear = 2024,
            ),

            // ── DeepSeek ─────────────────────────────────────────────────
            LlmModelInfo(
                id = "deepseek/deepseek-r1",
                displayName = "DeepSeek R1",
                kind = ModelKind.LANGUAGE, tier = LlmTier.PREMIUM,
                notes = "Reasoning open-source · math/code",
                supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "deepseek",
                maxContextTokens = 131_072, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "deepseek/deepseek-r1-0528",
                displayName = "DeepSeek R1 (0528)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.PREMIUM,
                notes = "R1 mise a jour mai 2025 · AIME 87.5%",
                supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "deepseek",
                maxContextTokens = 131_072, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "deepseek/deepseek-v3-0324",
                displayName = "DeepSeek V3 (0324)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Chat rapide post-training ameliore",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "deepseek",
                maxContextTokens = 131_072, releaseYear = 2025,
            ),
        ),

        // ─────────────────────────────────────────────────────────────────
        // ───── NVIDIA NIM : 150+ modeles open-source heberges sur NVIDIA ──
        // ─────────────────────────────────────────────────────────────────
        // Auth: Bearer nvapi-xxx
        // Free tier avec credits limites (1000 inferences/jour pour les
        // nouveaux comptes) puis billing per-token.
        // Le 1er modele = defaut recommande pour cette provider.
        // ─────────────────────────────────────────────────────────────────
        LlmProvider.NVIDIA_NIM to listOf(
            // ── Meta Llama famille (sur NIM endpoints) ───────────────────
            LlmModelInfo(
                id = "meta/llama-3.3-70b-instruct",
                displayName = "Llama 3.3 70B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Defaut NIM · conversation experte",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 70.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-4-maverick-17b-128e-instruct",
                displayName = "Llama 4 Maverick (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "MoE multimodal nouvelle generation",
                acceptsImageInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 524_288, parameterCountBillions = 17.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "meta/llama-3.2-90b-vision-instruct",
                displayName = "Llama 3.2 90B Vision (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Vision detaillee 90B",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 90.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-3.2-11b-vision-instruct",
                displayName = "Llama 3.2 11B Vision (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Vision legere abordable",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 11.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-3.2-3b-instruct",
                displayName = "Llama 3.2 3B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Compact mobile/embarque",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 3.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-3.2-1b-instruct",
                displayName = "Llama 3.2 1B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Ultra-compact IoT",
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 1.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-3.1-70b-instruct",
                displayName = "Llama 3.1 70B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Multilingue 8 langues",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 70.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-3.1-8b-instruct",
                displayName = "Llama 3.1 8B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Equilibre vitesse/cout",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 131_072, parameterCountBillions = 8.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/codellama-70b",
                displayName = "Code Llama 70B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Specialise code · tous langages",
                supportsCodeGen = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS, domain = ModelDomain.CODE,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                maxContextTokens = 100_000, parameterCountBillions = 70.0, releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "meta/llama-guard-4-12b",
                displayName = "Llama Guard 4 12B (NIM)",
                kind = ModelKind.CLASSIFICATION, tier = LlmTier.STANDARD,
                notes = "Moderation texte+image",
                acceptsImageInput = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS, domain = ModelDomain.GENERAL,
                originRegion = ModelOriginRegion.US, publisher = "meta",
                parameterCountBillions = 12.0, releaseYear = 2025,
            ),

            // ── Google (Gemma + DePlot) ───────────────────────────────────
            LlmModelInfo(
                id = "google/gemma-3-12b-it",
                displayName = "Gemma 3 12B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.STANDARD,
                notes = "Multimodal · 140 langues",
                acceptsImageInput = true, supportsVision = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "google",
                maxContextTokens = 131_072, parameterCountBillions = 12.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "google/gemma-3-4b-it",
                displayName = "Gemma 3 4B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.ECONOMIC,
                notes = "Multimodal mobile/tablette",
                acceptsImageInput = true, supportsVision = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "google",
                maxContextTokens = 131_072, parameterCountBillions = 4.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "google/gemma-4-31b-it",
                displayName = "Gemma 4 31B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Vision native + function calling + long contexte",
                acceptsImageInput = true, supportsVision = true, supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "google",
                maxContextTokens = 262_144, parameterCountBillions = 31.0, releaseYear = 2026,
            ),
            LlmModelInfo(
                id = "google/deplot",
                displayName = "DePlot (NIM)",
                kind = ModelKind.OCR, tier = LlmTier.STANDARD,
                notes = "Lecture graphiques · barres/courbes/camemberts",
                acceptsImageInput = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "google",
                releaseYear = 2022,
            ),

            // ── Mistral (sur NIM endpoints) ───────────────────────────────
            LlmModelInfo(
                id = "mistralai/mistral-large-3-675b-instruct-2512",
                displayName = "Mistral Large 3 675B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Multimodal MoE experts · vision + agents",
                acceptsImageInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.FRANCE, publisher = "mistralai",
                maxContextTokens = 256_000, parameterCountBillions = 675.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "mistralai/mistral-small-4-119b-2603",
                displayName = "Mistral Small 4 119B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "MoE 119B · agents + reasoning configurable",
                supportsToolCalling = true, supportsAgentic = true, supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.FRANCE, publisher = "mistralai",
                maxContextTokens = 128_000, parameterCountBillions = 119.0, releaseYear = 2026,
            ),

            // ── OpenAI (modeles open-source sur NIM) ──────────────────────
            LlmModelInfo(
                id = "openai/gpt-oss-120b",
                displayName = "GPT-OSS 120B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Premier open-source OpenAI",
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 131_072, parameterCountBillions = 120.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "openai/gpt-oss-20b",
                displayName = "GPT-OSS 20B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.ECONOMIC,
                notes = "Open-source compact · 16Go RAM suffit",
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_DENSE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.US, publisher = "openai",
                maxContextTokens = 131_072, parameterCountBillions = 20.0, releaseYear = 2025,
            ),

            // ── Alibaba Qwen ──────────────────────────────────────────────
            LlmModelInfo(
                id = "qwen/qwen3-next-80b-a3b-instruct",
                displayName = "Qwen 3 Next 80B (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Hybride 10x plus rapide · memoire enorme",
                supportsToolCalling = true,
                architecture = ModelArchitecture.HYBRID_MAMBA_TRANSFORMER,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "qwen",
                maxContextTokens = 262_144, parameterCountBillions = 80.0, releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "qwen/qwen3.5-122b-a10b",
                displayName = "Qwen 3.5 122B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Multimodal · texte + images + video",
                acceptsImageInput = true, acceptsVideoInput = true, supportsVision = true,
                supportsToolCalling = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "qwen",
                maxContextTokens = 262_144, parameterCountBillions = 122.0, releaseYear = 2026,
            ),
            LlmModelInfo(
                id = "qwen/qwen3.5-397b-a17b",
                displayName = "Qwen 3.5 397B (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Flagship MoE · multimodal frontier",
                acceptsImageInput = true, acceptsVideoInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "qwen",
                maxContextTokens = 262_144, parameterCountBillions = 397.0, releaseYear = 2026,
            ),

            // ── Z.ai (GLM) ────────────────────────────────────────────────
            LlmModelInfo(
                id = "z-ai/glm-5.1",
                displayName = "GLM 5.1 (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.PREMIUM,
                notes = "Agents autonomes 8h · automatisation",
                supportsToolCalling = true, supportsAgentic = true, supportsThinking = true,
                architecture = ModelArchitecture.HYBRID_MAMBA_TRANSFORMER,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "z-ai",
                maxContextTokens = 131_072, releaseYear = 2026,
            ),

            // ── Moonshot AI (Kimi) ────────────────────────────────────────
            LlmModelInfo(
                id = "moonshotai/kimi-k2.6",
                displayName = "Kimi K2.6 (NIM)",
                kind = ModelKind.VLM, tier = LlmTier.PREMIUM,
                notes = "Agents multimodaux · 262K contexte",
                acceptsImageInput = true, supportsVision = true,
                supportsToolCalling = true, supportsAgentic = true, supportsThinking = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                originRegion = ModelOriginRegion.CHINA, publisher = "moonshotai",
                maxContextTokens = 262_144, releaseYear = 2026,
            ),

            // ── MiniMax ──────────────────────────────────────────────────
            LlmModelInfo(
                id = "minimaxai/minimax-m2.7",
                displayName = "MiniMax M2.7 (NIM)",
                kind = ModelKind.LANGUAGE, tier = LlmTier.STANDARD,
                notes = "Genie logiciel complexe · 196K contexte",
                supportsToolCalling = true, supportsCodeGen = true,
                architecture = ModelArchitecture.TRANSFORMER_MOE,
                weightsSource = WeightsSource.OPEN_WEIGHTS, domain = ModelDomain.CODE,
                originRegion = ModelOriginRegion.CHINA, publisher = "minimaxai",
                maxContextTokens = 196_608, releaseYear = 2025,
            ),
        ),

        // ───── POLLINATIONS : txt2img gratuit, no auth ──────────────────
        LlmProvider.POLLINATIONS to listOf(
            LlmModelInfo(
                id = "flux",
                displayName = "FLUX (Pollinations)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "FLUX hébergé Pollinations · gratuit",
                acceptsImageInput = false,
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.CREATIVE,
                originRegion = ModelOriginRegion.US,
                publisher = "pollinations",
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "turbo",
                displayName = "Turbo (Pollinations)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.ECONOMIC,
                notes = "Génération ultra-rapide",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                publisher = "pollinations",
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "kontext",
                displayName = "Kontext (Pollinations)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "Variante context-aware",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                publisher = "pollinations",
                releaseYear = 2025,
            ),
            LlmModelInfo(
                id = "gptimage",
                displayName = "GPT Image (Pollinations)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.PREMIUM,
                notes = "OpenAI gpt-image-1 via Pollinations",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.CLOSED_SOURCE,
                publisher = "pollinations",
                releaseYear = 2025,
            ),
        ),

        // ───── CLOUDFLARE WORKERS AI : txt2img + img2img ────────────────
        LlmProvider.CLOUDFLARE_AI to listOf(
            // ── TXT2IMG ────────────────────────────────────────────────
            LlmModelInfo(
                id = "@cf/black-forest-labs/flux-1-schnell",
                displayName = "FLUX.1 Schnell (Cloudflare)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "⭐ Le meilleur, ~4 steps, ultra-rapide",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.CREATIVE,
                publisher = "black-forest-labs",
                originRegion = ModelOriginRegion.GERMANY,
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "@cf/bytedance/stable-diffusion-xl-lightning",
                displayName = "SDXL Lightning (Cloudflare)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.ECONOMIC,
                notes = "SDXL accéléré, ultra-rapide",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                publisher = "bytedance",
                originRegion = ModelOriginRegion.CHINA,
                releaseYear = 2024,
            ),
            LlmModelInfo(
                id = "@cf/stabilityai/stable-diffusion-xl-base-1.0",
                displayName = "SDXL Base 1.0 (Cloudflare)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "SDXL classique, qualité photoréaliste",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                publisher = "stabilityai",
                originRegion = ModelOriginRegion.UK,
                releaseYear = 2023,
            ),
            LlmModelInfo(
                id = "@cf/lykon/dreamshaper-8-lcm",
                displayName = "DreamShaper 8 LCM",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "Style artistique, illustration",
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.CREATIVE,
                publisher = "lykon",
                releaseYear = 2024,
            ),
            // ── IMG2IMG (acceptsImageInput=true) ───────────────────────
            LlmModelInfo(
                id = "@cf/runwayml/stable-diffusion-v1-5-img2img",
                displayName = "SD v1.5 Img2Img (Cloudflare)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.STANDARD,
                notes = "Édite une image existante avec un prompt",
                acceptsImageInput = true,
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                publisher = "runwayml",
                originRegion = ModelOriginRegion.US,
                releaseYear = 2022,
            ),
            LlmModelInfo(
                id = "@cf/black-forest-labs/flux-2-klein-9b",
                displayName = "FLUX.2 Klein 9B (Cloudflare)",
                kind = ModelKind.IMAGE_GENERATION,
                tier = LlmTier.PREMIUM,
                notes = "⭐ Img2img premium · input ≤512×512",
                acceptsImageInput = true,
                architecture = ModelArchitecture.DIFFUSION,
                weightsSource = WeightsSource.OPEN_WEIGHTS,
                domain = ModelDomain.CREATIVE,
                publisher = "black-forest-labs",
                originRegion = ModelOriginRegion.GERMANY,
                parameterCountBillions = 9.0,
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
