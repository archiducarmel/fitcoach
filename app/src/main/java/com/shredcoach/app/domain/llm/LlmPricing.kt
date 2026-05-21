package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.remote.LlmProvider

/**
 * Tarification LLM par modèle (USD per million tokens). Sources publiques au
 * moment de l'écriture (mai 2026) — à actualiser quand les providers changent
 * leurs prix.
 *
 * **Hardcodé volontairement** : pas de call API pour récupérer les prix
 * en runtime (les providers exposent rarement ça via API). L'écart entre les
 * prix réels facturés et notre estimation est typiquement <5% : suffisant
 * pour le dashboard utilisateur (donner un ordre de grandeur, pas une
 * facture précise).
 *
 * **Thinking tokens** : Gemini facture les thinking tokens au tarif output
 * (visible dans la doc Gemini pricing). On les inclut dans le coût output.
 */
object LlmPricing {

    /** Prix d'un modèle en USD par million de tokens. */
    data class ModelPricing(
        val inputPerMtok: Double,
        val outputPerMtok: Double,
        /** Si null, on suppose output_per_Mtok (cas Gemini). */
        val thinkingPerMtok: Double? = null,
    )

    private val FALLBACK = ModelPricing(inputPerMtok = 0.5, outputPerMtok = 1.5)

    /**
     * Catalogue : (provider, model) → tarif. Si miss, on retourne [FALLBACK]
     * (gauge raisonnable pour les modèles inconnus) plutôt que 0.0 qui
     * sous-estimerait massivement les coûts.
     */
    private val PRICES: Map<Pair<LlmProvider, String>, ModelPricing> = mapOf(
        // ─── GEMINI ─────────────────────────────────────────────────────
        (LlmProvider.GEMINI to "gemini-2.5-flash") to ModelPricing(0.30, 2.50),
        (LlmProvider.GEMINI to "gemini-3.5-flash") to ModelPricing(1.50, 9.00),
        (LlmProvider.GEMINI to "gemini-2.0-flash") to ModelPricing(0.10, 0.40),

        // ─── GROQ ───────────────────────────────────────────────────────
        (LlmProvider.GROQ to "openai/gpt-oss-120b") to ModelPricing(0.15, 0.75),
        (LlmProvider.GROQ to "meta-llama/llama-4-scout-17b-16e-instruct") to ModelPricing(0.11, 0.34),
        (LlmProvider.GROQ to "llama-3.3-70b-versatile") to ModelPricing(0.59, 0.79),

        // ─── MISTRAL ────────────────────────────────────────────────────
        (LlmProvider.MISTRAL to "mistral-small-latest") to ModelPricing(0.20, 0.60),
        (LlmProvider.MISTRAL to "pixtral-large-latest") to ModelPricing(2.00, 6.00),

        // ─── OPENAI ─────────────────────────────────────────────────────
        (LlmProvider.OPENAI to "gpt-4o-mini") to ModelPricing(0.15, 0.60),
        (LlmProvider.OPENAI to "gpt-4o") to ModelPricing(2.50, 10.00),
        (LlmProvider.OPENAI to "gpt-4.1-mini") to ModelPricing(0.40, 1.60),

        // ─── CLAUDE ─────────────────────────────────────────────────────
        (LlmProvider.CLAUDE to "claude-sonnet-4-20250514") to ModelPricing(3.00, 15.00),
        (LlmProvider.CLAUDE to "claude-haiku-4-20250514") to ModelPricing(0.80, 4.00),
        (LlmProvider.CLAUDE to "claude-opus-4-20250514") to ModelPricing(15.00, 75.00),
    )

    /**
     * Estime le coût en USD d'un appel LLM.
     *
     * @param tokensInput Prompt + system tokens
     * @param tokensOutput Output tokens uniquement
     * @param tokensThinking Tokens de chain-of-thought (Gemini 2.5+)
     */
    fun estimate(
        provider: LlmProvider,
        model: String,
        tokensInput: Int,
        tokensOutput: Int,
        tokensThinking: Int = 0,
    ): Double {
        val pricing = PRICES[provider to model] ?: FALLBACK
        val thinkingRate = pricing.thinkingPerMtok ?: pricing.outputPerMtok
        val inputCost = tokensInput / 1_000_000.0 * pricing.inputPerMtok
        val outputCost = tokensOutput / 1_000_000.0 * pricing.outputPerMtok
        val thinkingCost = tokensThinking / 1_000_000.0 * thinkingRate
        return inputCost + outputCost + thinkingCost
    }

    /** Tarif pour affichage UI (provider + model). Retourne null si inconnu. */
    fun pricingFor(provider: LlmProvider, model: String): ModelPricing? =
        PRICES[provider to model]
}
