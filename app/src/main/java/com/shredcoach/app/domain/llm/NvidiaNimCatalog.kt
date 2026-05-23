package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.remote.LlmProvider

/**
 * Catalogue editorialise des modeles NVIDIA NIM. Contrairement a GitHub Models
 * qui a un endpoint /catalog/models avec toutes les metadata, NVIDIA NIM
 * expose seulement `/v1/models` qui retourne juste des IDs. Donc on maintient
 * un catalogue hardcode avec la metadata complete cote app, et on filtre via
 * intersection avec les IDs accessibles a la cle de l'utilisateur.
 *
 * **Source** : edition base sur le script Python valide par l'user
 * (50+ modeles testes fonctionnels via `nvapi-xxx` Bearer).
 *
 * **Reasoning flag** : les modeles avec thinking etendu (deepseek-v4-*,
 * kimi-k2.6, nemotron-ultra, glm-5.1, qwq) requierent **streaming obligatoire
 * + timeout 300s** sinon ils echouent. Marque via `supportsThinking=true`,
 * detecte par [isSlow] cote LlmApiService.
 */
object NvidiaNimCatalog {

    /**
     * True si le modele a besoin de streaming + long timeout (reasoning models).
     * Detection par keywords sur l'id, miroir de la logique Python.
     */
    fun isSlow(modelId: String): Boolean {
        val keywords = listOf("thinking", "reasoning", "v4-pro", "v4-flash",
            "qwq", "nemotron-ultra", "glm-5.1", "kimi-k2.6")
        val lower = modelId.lowercase()
        return keywords.any { lower.contains(it) }
    }

    /** Tous les modeles editorialises, dans l'ordre des categories Python. */
    val ALL_MODELS: List<LlmModelInfo> = buildList {
        // ─── 🧠 Reasoning (slow, streaming obligatoire) ─────────────────────
        addAll(REASONING_MODELS)
        // ─── ⚡ Generalistes rapides ─────────────────────────────────────────
        addAll(GENERALIST_MODELS)
        // ─── 👨‍💻 Coding ──────────────────────────────────────────────────
        addAll(CODING_MODELS)
        // ─── 🎯 Petits & rapides ────────────────────────────────────────────
        addAll(SMALL_FAST_MODELS)
        // ─── ✍️ Specialises metier ──────────────────────────────────────────
        addAll(SPECIALIZED_MODELS)
        // ─── 🌍 Multilingues ────────────────────────────────────────────────
        addAll(MULTILINGUAL_MODELS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REASONING (slow, streaming + 300s timeout obligatoire)
    // ─────────────────────────────────────────────────────────────────────────

    private val REASONING_MODELS = listOf(
        model(
            id = "deepseek-ai/deepseek-v4-pro",
            displayName = "DeepSeek V4 Pro",
            tier = LlmTier.PREMIUM,
            notes = "1.6T MoE — ⭐ Top reasoning",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 1600.0,
            region = ModelOriginRegion.CHINA,
            publisher = "deepseek-ai",
        ),
        model(
            id = "deepseek-ai/deepseek-v4-flash",
            displayName = "DeepSeek V4 Flash",
            tier = LlmTier.PREMIUM,
            notes = "284B MoE — Reasoning rapide",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 284.0,
            region = ModelOriginRegion.CHINA,
            publisher = "deepseek-ai",
        ),
        model(
            id = "moonshotai/kimi-k2.6",
            displayName = "Kimi K2.6",
            tier = LlmTier.PREMIUM,
            notes = "1T MoE — Top open-weight reasoning",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 1000.0,
            region = ModelOriginRegion.CHINA,
            publisher = "moonshotai",
        ),
        model(
            id = "nvidia/llama-3.1-nemotron-ultra-253b-v1",
            displayName = "Llama 3.1 Nemotron Ultra 253B",
            tier = LlmTier.PREMIUM,
            notes = "253B — NVIDIA flagship reasoning",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_DENSE,
            paramsB = 253.0,
            region = ModelOriginRegion.US,
            publisher = "nvidia",
        ),
        model(
            id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning",
            displayName = "Nemotron 3 Nano Omni 30B Reasoning",
            tier = LlmTier.STANDARD,
            notes = "30B MoE — NVIDIA compact reasoning + multimodal",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 30.0,
            region = ModelOriginRegion.US,
            publisher = "nvidia",
        ),
        model(
            id = "z-ai/glm-5.1",
            displayName = "GLM 5.1",
            tier = LlmTier.PREMIUM,
            notes = "GLM reasoning",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_DENSE,
            region = ModelOriginRegion.CHINA,
            publisher = "z-ai",
        ),
        model(
            id = "qwen/qwen3.5-397b-a17b",
            displayName = "Qwen 3.5 397B",
            tier = LlmTier.PREMIUM,
            notes = "397B MoE — Qwen XL",
            supportsThinking = true,
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 397.0,
            region = ModelOriginRegion.CHINA,
            publisher = "qwen",
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // GENERALISTES RAPIDES (defaut)
    // ─────────────────────────────────────────────────────────────────────────

    private val GENERALIST_MODELS = listOf(
        model(
            id = "meta/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B Instruct",
            tier = LlmTier.STANDARD,
            notes = "70B — ⭐ Défaut conseillé",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "meta",
            supportsToolCalling = true,
        ),
        model(
            id = "meta/llama-3.1-70b-instruct",
            displayName = "Llama 3.1 70B Instruct",
            tier = LlmTier.STANDARD,
            notes = "70B — Solide",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "meta",
            supportsToolCalling = true,
        ),
        model(
            id = "meta/llama-3.1-8b-instruct",
            displayName = "Llama 3.1 8B Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "8B — Léger",
            paramsB = 8.0, region = ModelOriginRegion.US, publisher = "meta",
        ),
        model(
            id = "openai/gpt-oss-120b",
            displayName = "GPT-OSS 120B",
            tier = LlmTier.STANDARD,
            notes = "120B MoE — OpenAI open-weight",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 120.0, region = ModelOriginRegion.US, publisher = "openai",
            supportsToolCalling = true,
            weightsSource = WeightsSource.OPEN_WEIGHTS,
        ),
        model(
            id = "openai/gpt-oss-20b",
            displayName = "GPT-OSS 20B",
            tier = LlmTier.ECONOMIC,
            notes = "20B MoE — Compact",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 20.0, region = ModelOriginRegion.US, publisher = "openai",
            weightsSource = WeightsSource.OPEN_WEIGHTS,
        ),
        model(
            id = "qwen/qwen3-next-80b-a3b-instruct",
            displayName = "Qwen 3 Next 80B",
            tier = LlmTier.STANDARD,
            notes = "80B MoE — Qwen rapide",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 80.0, region = ModelOriginRegion.CHINA, publisher = "qwen",
        ),
        model(
            id = "qwen/qwen3.5-122b-a10b",
            displayName = "Qwen 3.5 122B",
            tier = LlmTier.STANDARD,
            notes = "122B MoE — Qwen plus gros",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 122.0, region = ModelOriginRegion.CHINA, publisher = "qwen",
        ),
        model(
            id = "mistralai/mistral-large-3-675b-instruct-2512",
            displayName = "Mistral Large 3 675B",
            tier = LlmTier.PREMIUM,
            notes = "675B — Mistral flagship",
            paramsB = 675.0, region = ModelOriginRegion.FRANCE, publisher = "mistralai",
            supportsToolCalling = true,
        ),
        model(
            id = "mistralai/mistral-medium-3.5-128b",
            displayName = "Mistral Medium 3.5 128B",
            tier = LlmTier.STANDARD,
            notes = "128B — Mistral medium",
            paramsB = 128.0, region = ModelOriginRegion.FRANCE, publisher = "mistralai",
            supportsToolCalling = true,
        ),
        model(
            id = "mistralai/mistral-nemotron",
            displayName = "Mistral Nemotron",
            tier = LlmTier.STANDARD,
            notes = "Mistral × NVIDIA",
            region = ModelOriginRegion.FRANCE, publisher = "mistralai",
        ),
        model(
            id = "mistralai/ministral-14b-instruct-2512",
            displayName = "Ministral 14B Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "14B — Mistral léger",
            paramsB = 14.0, region = ModelOriginRegion.FRANCE, publisher = "mistralai",
        ),
        model(
            id = "mistralai/mixtral-8x7b-instruct-v0.1",
            displayName = "Mixtral 8x7B",
            tier = LlmTier.STANDARD,
            notes = "56B MoE — Mixtral classique",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 56.0, region = ModelOriginRegion.FRANCE, publisher = "mistralai",
        ),
        model(
            id = "minimaxai/minimax-m2.7",
            displayName = "MiniMax M2.7",
            tier = LlmTier.STANDARD,
            notes = "MiniMax",
            region = ModelOriginRegion.CHINA, publisher = "minimaxai",
        ),
        model(
            id = "nvidia/nemotron-4-340b-instruct",
            displayName = "Nemotron 4 340B Instruct",
            tier = LlmTier.PREMIUM,
            notes = "340B — NVIDIA flagship",
            paramsB = 340.0, region = ModelOriginRegion.US, publisher = "nvidia",
        ),
        model(
            id = "nvidia/llama-3.1-nemotron-70b-instruct",
            displayName = "Llama 3.1 Nemotron 70B",
            tier = LlmTier.STANDARD,
            notes = "70B — NVIDIA Llama variant",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "nvidia",
        ),
        model(
            id = "nvidia/llama-3.3-nemotron-super-49b-v1.5",
            displayName = "Llama 3.3 Nemotron Super 49B v1.5",
            tier = LlmTier.STANDARD,
            notes = "49B — NVIDIA super",
            paramsB = 49.0, region = ModelOriginRegion.US, publisher = "nvidia",
        ),
        model(
            id = "01-ai/yi-large",
            displayName = "Yi Large",
            tier = LlmTier.STANDARD,
            notes = "Yi",
            region = ModelOriginRegion.CHINA, publisher = "01-ai",
        ),
        model(
            id = "databricks/dbrx-instruct",
            displayName = "DBRX Instruct",
            tier = LlmTier.STANDARD,
            notes = "132B MoE — Databricks",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 132.0, region = ModelOriginRegion.US, publisher = "databricks",
        ),
        model(
            id = "ai21labs/jamba-1.5-large-instruct",
            displayName = "Jamba 1.5 Large",
            tier = LlmTier.STANDARD,
            notes = "Hybride Mamba+Transformer",
            architecture = ModelArchitecture.HYBRID_MAMBA_TRANSFORMER,
            region = ModelOriginRegion.ISRAEL, publisher = "ai21labs",
        ),
        model(
            id = "stepfun-ai/step-3.5-flash",
            displayName = "Step 3.5 Flash",
            tier = LlmTier.ECONOMIC,
            notes = "StepFun rapide",
            region = ModelOriginRegion.CHINA, publisher = "stepfun-ai",
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // CODING
    // ─────────────────────────────────────────────────────────────────────────

    private val CODING_MODELS = listOf(
        model(
            id = "qwen/qwen3-coder-480b-a35b-instruct",
            displayName = "Qwen3 Coder 480B",
            tier = LlmTier.PREMIUM,
            notes = "480B MoE — ⭐ SOTA code",
            architecture = ModelArchitecture.TRANSFORMER_MOE,
            paramsB = 480.0, region = ModelOriginRegion.CHINA, publisher = "qwen",
            domain = ModelDomain.CODE,
            supportsCodeGen = true, supportsAgentic = true,
        ),
        model(
            id = "mistralai/codestral-22b-instruct-v0.1",
            displayName = "Codestral 22B",
            tier = LlmTier.STANDARD,
            notes = "22B — Mistral code",
            paramsB = 22.0, region = ModelOriginRegion.FRANCE, publisher = "mistralai",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
        model(
            id = "meta/codellama-70b",
            displayName = "Code Llama 70B",
            tier = LlmTier.STANDARD,
            notes = "70B — Llama code",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "meta",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
        model(
            id = "deepseek-ai/deepseek-coder-6.7b-instruct",
            displayName = "DeepSeek Coder 6.7B",
            tier = LlmTier.ECONOMIC,
            notes = "6.7B — DeepSeek petit",
            paramsB = 6.7, region = ModelOriginRegion.CHINA, publisher = "deepseek-ai",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
        model(
            id = "bigcode/starcoder2-15b",
            displayName = "StarCoder 2 15B",
            tier = LlmTier.STANDARD,
            notes = "15B — StarCoder 2",
            paramsB = 15.0, region = ModelOriginRegion.US, publisher = "bigcode",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
        model(
            id = "ibm/granite-34b-code-instruct",
            displayName = "Granite 34B Code Instruct",
            tier = LlmTier.STANDARD,
            notes = "34B — IBM code",
            paramsB = 34.0, region = ModelOriginRegion.US, publisher = "ibm",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
        model(
            id = "google/codegemma-7b",
            displayName = "Code Gemma 7B",
            tier = LlmTier.ECONOMIC,
            notes = "7B — Code Gemma",
            paramsB = 7.0, region = ModelOriginRegion.US, publisher = "google",
            domain = ModelDomain.CODE,
            supportsCodeGen = true,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // PETITS & RAPIDES (≤ 12B)
    // ─────────────────────────────────────────────────────────────────────────

    private val SMALL_FAST_MODELS = listOf(
        model(
            id = "microsoft/phi-4-mini-instruct",
            displayName = "Phi 4 Mini Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "4B — ⭐ Efficace",
            paramsB = 4.0, region = ModelOriginRegion.US, publisher = "microsoft",
        ),
        model(
            id = "meta/llama-3.2-3b-instruct",
            displayName = "Llama 3.2 3B Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "3B — Llama light",
            paramsB = 3.0, region = ModelOriginRegion.US, publisher = "meta",
        ),
        model(
            id = "meta/llama-3.2-1b-instruct",
            displayName = "Llama 3.2 1B Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "1B — Ultra léger",
            paramsB = 1.0, region = ModelOriginRegion.US, publisher = "meta",
        ),
        model(
            id = "google/gemma-3-12b-it",
            displayName = "Gemma 3 12B IT",
            tier = LlmTier.STANDARD,
            notes = "12B — Gemma 3",
            paramsB = 12.0, region = ModelOriginRegion.US, publisher = "google",
        ),
        model(
            id = "google/gemma-3-4b-it",
            displayName = "Gemma 3 4B IT",
            tier = LlmTier.ECONOMIC,
            notes = "4B — Gemma 3 petit",
            paramsB = 4.0, region = ModelOriginRegion.US, publisher = "google",
        ),
        model(
            id = "google/gemma-2-2b-it",
            displayName = "Gemma 2 2B IT",
            tier = LlmTier.ECONOMIC,
            notes = "2B — Gemma 2",
            paramsB = 2.0, region = ModelOriginRegion.US, publisher = "google",
        ),
        model(
            id = "ibm/granite-3.0-8b-instruct",
            displayName = "Granite 3.0 8B",
            tier = LlmTier.ECONOMIC,
            notes = "8B — IBM Granite",
            paramsB = 8.0, region = ModelOriginRegion.US, publisher = "ibm",
        ),
        model(
            id = "nvidia/nemotron-mini-4b-instruct",
            displayName = "Nemotron Mini 4B",
            tier = LlmTier.ECONOMIC,
            notes = "4B — NVIDIA mini",
            paramsB = 4.0, region = ModelOriginRegion.US, publisher = "nvidia",
        ),
        model(
            id = "zyphra/zamba2-7b-instruct",
            displayName = "Zamba 2 7B Instruct",
            tier = LlmTier.ECONOMIC,
            notes = "7B — Mamba hybride",
            architecture = ModelArchitecture.HYBRID_MAMBA_TRANSFORMER,
            paramsB = 7.0, region = ModelOriginRegion.US, publisher = "zyphra",
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SPECIALISES METIER
    // ─────────────────────────────────────────────────────────────────────────

    private val SPECIALIZED_MODELS = listOf(
        model(
            id = "writer/palmyra-creative-122b",
            displayName = "Palmyra Creative 122B",
            tier = LlmTier.PREMIUM,
            notes = "122B — Écriture créative",
            paramsB = 122.0, region = ModelOriginRegion.US, publisher = "writer",
            domain = ModelDomain.CREATIVE,
        ),
        model(
            id = "writer/palmyra-med-70b-32k",
            displayName = "Palmyra Med 70B (32K ctx)",
            tier = LlmTier.PREMIUM,
            notes = "70B — Médical long contexte",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "writer",
            domain = ModelDomain.MEDICAL,
            maxContextTokens = 32_000,
        ),
        model(
            id = "writer/palmyra-fin-70b-32k",
            displayName = "Palmyra Fin 70B (32K ctx)",
            tier = LlmTier.PREMIUM,
            notes = "70B — Finance long contexte",
            paramsB = 70.0, region = ModelOriginRegion.US, publisher = "writer",
            domain = ModelDomain.FINANCE,
            maxContextTokens = 32_000,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // MULTILINGUES
    // ─────────────────────────────────────────────────────────────────────────

    private val MULTILINGUAL_MODELS = listOf(
        model(
            id = "sarvamai/sarvam-m",
            displayName = "Sarvam M",
            tier = LlmTier.STANDARD,
            notes = "Inde — multilingue",
            region = ModelOriginRegion.INDIA, publisher = "sarvamai",
            supportsTranslation = true,
        ),
        model(
            id = "aisingapore/sea-lion-7b-instruct",
            displayName = "Sea Lion 7B",
            tier = LlmTier.ECONOMIC,
            notes = "7B — Asie du SE multilingue",
            paramsB = 7.0, region = ModelOriginRegion.SINGAPORE, publisher = "aisingapore",
            supportsTranslation = true,
        ),
        model(
            id = "nvidia/riva-translate-4b-instruct-v1.1",
            displayName = "Riva Translate 4B v1.1",
            tier = LlmTier.STANDARD,
            notes = "4B — Traduction 12 langues",
            paramsB = 4.0, region = ModelOriginRegion.US, publisher = "nvidia",
            domain = ModelDomain.TRANSLATION,
            supportsTranslation = true,
        ),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER : factory avec defaults sensibles pour NVIDIA NIM CHAT models
    // ─────────────────────────────────────────────────────────────────────────

    private fun model(
        id: String,
        displayName: String,
        tier: LlmTier,
        notes: String,
        paramsB: Double = 0.0,
        region: ModelOriginRegion = ModelOriginRegion.US,
        publisher: String? = null,
        architecture: ModelArchitecture = ModelArchitecture.TRANSFORMER_DENSE,
        weightsSource: WeightsSource = WeightsSource.OPEN_WEIGHTS,
        domain: ModelDomain = ModelDomain.GENERAL,
        supportsThinking: Boolean = false,
        supportsToolCalling: Boolean = false,
        supportsCodeGen: Boolean = false,
        supportsAgentic: Boolean = false,
        supportsTranslation: Boolean = false,
        maxContextTokens: Int = 0,
    ): LlmModelInfo = LlmModelInfo(
        id = id,
        displayName = displayName,
        kind = ModelKind.LANGUAGE,
        tier = tier,
        notes = notes,
        supportsThinking = supportsThinking,
        supportsToolCalling = supportsToolCalling,
        supportsCodeGen = supportsCodeGen,
        supportsAgentic = supportsAgentic,
        supportsTranslation = supportsTranslation,
        architecture = architecture,
        weightsSource = weightsSource,
        domain = domain,
        originRegion = region,
        publisher = publisher,
        maxContextTokens = maxContextTokens,
        parameterCountBillions = paramsB,
    )
}
