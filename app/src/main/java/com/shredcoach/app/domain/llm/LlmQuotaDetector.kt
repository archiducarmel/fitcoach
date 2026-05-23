package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.remote.LlmProvider

/**
 * Classifie une erreur LLM pour decider si on doit basculer sur le fallback
 * ou simplement laisser le retry standard tenter sa chance.
 *
 * **Pourquoi cette distinction est critique** : on NE VEUT PAS basculer sur le
 * fallback (qui consomme de l'API d'un autre provider) pour un simple
 * engorgement temporaire — le retry suffit. On veut basculer UNIQUEMENT quand
 * le quota free tier du provider est epuise et que le service refusera tous
 * les calls jusqu'au reset (souvent J+1 minimum).
 *
 * **Signatures par provider** (basees sur les docs officielles et tests
 * empiriques) :
 *
 *  - **Gemini** : HTTP 429 + message contenant "quota" ou "RESOURCE_EXHAUSTED"
 *    + ABSENCE du marqueur "overloaded" (qui signale juste un engorgement)
 *  - **Groq** : HTTP 429 + message contenant "daily" ou "Rate limit reached
 *    for daily" → quota journalier epuise
 *  - **OpenAI** : HTTP 429 + error.code = "insufficient_quota" (vraie limite
 *    de billing) vs "rate_limit_exceeded" (transient)
 *  - **Claude** : HTTP 429 + retry-after > 3600s (les vrais quotas sont en
 *    heures/jours, les rate limits en secondes)
 *  - **Mistral** : HTTP 429 + message contenant "quota" ou code 1100x
 */
object LlmQuotaDetector {

    /**
     * Resultat de la classification d'une erreur LLM.
     *
     * - [TRANSIENT] : engorgement, surcharge, rate limit minute → retry possible
     * - [QUOTA_EXHAUSTED] : free tier epuise → basculer sur fallback
     * - [AUTH_ERROR] : cle API invalide → user-facing error, pas de fallback
     * - [OTHER] : tout le reste → propagate
     */
    enum class Classification {
        TRANSIENT,
        QUOTA_EXHAUSTED,
        AUTH_ERROR,
        OTHER,
    }

    /**
     * Classifie une erreur. Inputs disponibles :
     *  - `httpCode` : code HTTP retourne (peut etre -1 si network error)
     *  - `responseBody` : body de la response (peut etre vide ou null)
     *  - `retryAfterSec` : header Retry-After parse (null si absent)
     *
     * **Heuristique conservatrice** : en cas de doute, on retourne [TRANSIENT]
     * plutot que [QUOTA_EXHAUSTED]. Mieux vaut retry inutilement que basculer
     * sur le fallback alors que le primary aurait fonctionne en retry.
     */
    fun classify(
        provider: LlmProvider,
        httpCode: Int,
        responseBody: String?,
        retryAfterSec: Long? = null,
    ): Classification {
        val body = responseBody?.lowercase().orEmpty()

        // ─── 401/403 → auth error ───────────────────────────────────────────
        if (httpCode == 401 || httpCode == 403) return Classification.AUTH_ERROR
        if (body.contains("invalid_api_key") || body.contains("authentication_error")) {
            return Classification.AUTH_ERROR
        }

        // ─── Non-429 → pas de quota issue (sauf provider-specific) ──────────
        if (httpCode != 429 && httpCode != 529) {
            return Classification.OTHER
        }

        // ─── Provider-specific quota detection ──────────────────────────────
        return when (provider) {
            LlmProvider.GEMINI -> classifyGemini(httpCode, body, retryAfterSec)
            LlmProvider.GROQ -> classifyGroq(httpCode, body, retryAfterSec)
            LlmProvider.OPENAI -> classifyOpenAi(httpCode, body)
            LlmProvider.CLAUDE -> classifyClaude(httpCode, body, retryAfterSec)
            LlmProvider.MISTRAL -> classifyMistral(httpCode, body)
            LlmProvider.GITHUB_MODELS -> classifyGitHubModels(httpCode, body, retryAfterSec)
            LlmProvider.NVIDIA_NIM -> classifyNvidiaNim(httpCode, body, retryAfterSec)
        }
    }

    /**
     * Helper convenience : classifie a partir d'une exception. La plupart des
     * services throwent une Exception avec le message inclus dans le httpCode/body.
     * On extrait via regex le code HTTP si present dans le message.
     */
    fun classifyException(provider: LlmProvider, exception: Throwable): Classification {
        val msg = exception.message?.lowercase().orEmpty()

        // Extract HTTP code via regex (formats courants : "Erreur 429", "HTTP 429", "code 429")
        val httpCode = Regex("""\b(4\d\d|5\d\d)\b""").find(msg)?.value?.toIntOrNull() ?: -1

        return classify(provider, httpCode, msg, retryAfterSec = null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classifieurs par provider (private)
    // ─────────────────────────────────────────────────────────────────────────

    private fun classifyGemini(httpCode: Int, body: String, retryAfterSec: Long?): Classification {
        // Engorgement explicite : ne PAS basculer (retry standard suffit).
        if (body.contains("overloaded") || body.contains("currently experiencing high demand")) {
            return Classification.TRANSIENT
        }
        // Quota detection
        if (body.contains("quota") || body.contains("resource_exhausted") ||
            body.contains("exceeded your current quota")) {
            // Si retry-after court (< 10min), c'est un rate limit transient
            if (retryAfterSec != null && retryAfterSec < 600) return Classification.TRANSIENT
            return Classification.QUOTA_EXHAUSTED
        }
        return Classification.TRANSIENT // 429 sans message clair → retry
    }

    private fun classifyGroq(httpCode: Int, body: String, retryAfterSec: Long?): Classification {
        // Groq differencie clairement les daily quotas des minute rate limits
        if (body.contains("daily") || body.contains("rate limit reached for daily")) {
            return Classification.QUOTA_EXHAUSTED
        }
        // Si retry-after > 1h, c'est probablement quota
        if (retryAfterSec != null && retryAfterSec > 3600) return Classification.QUOTA_EXHAUSTED
        return Classification.TRANSIENT
    }

    private fun classifyOpenAi(httpCode: Int, body: String): Classification {
        // OpenAI distingue clairement insufficient_quota (billing/free tier) vs
        // rate_limit_exceeded (transient minute/second limits)
        if (body.contains("insufficient_quota") || body.contains("quota_exceeded") ||
            body.contains("billing_hard_limit_reached") ||
            body.contains("you exceeded your current quota")) {
            return Classification.QUOTA_EXHAUSTED
        }
        return Classification.TRANSIENT
    }

    private fun classifyClaude(httpCode: Int, body: String, retryAfterSec: Long?): Classification {
        // Claude : 529 = overloaded (transient garanti)
        if (httpCode == 529) return Classification.TRANSIENT
        // 429 + retry-after long → quota (les rate limits Claude sont en secondes)
        if (retryAfterSec != null && retryAfterSec > 600) return Classification.QUOTA_EXHAUSTED
        // Message specifique
        if (body.contains("monthly") || body.contains("usage limit") ||
            body.contains("credits exhausted")) {
            return Classification.QUOTA_EXHAUSTED
        }
        return Classification.TRANSIENT
    }

    private fun classifyMistral(httpCode: Int, body: String): Classification {
        if (body.contains("quota") || body.contains("monthly limit")) {
            return Classification.QUOTA_EXHAUSTED
        }
        return Classification.TRANSIENT
    }

    /**
     * GitHub Models — distingue clairement 3 cas :
     *  - `unavailable_model` : modele gated (Copilot Pro+ requis) sur free tier
     *    → AUTH_ERROR (pas de fallback, l'user doit upgrade ou changer de modele)
     *  - `rate_limit_exceeded` avec retry-after > 1h → QUOTA_EXHAUSTED
     *    (quota daily epuise du tier — 30 req/jour sur custom tier minimum)
     *  - `rate_limit_exceeded` court → TRANSIENT (rate limit minute)
     *  - 404 model_not_found → OTHER (model id invalide, pas un quota issue)
     */
    private fun classifyGitHubModels(httpCode: Int, body: String, retryAfterSec: Long?): Classification {
        // Gated model on free tier → erreur "unavailable_model"
        if (body.contains("unavailable_model") || body.contains("not available for your tier")) {
            return Classification.AUTH_ERROR
        }
        if (body.contains("model_not_found")) return Classification.OTHER
        // Rate limit : long retry-after = quota daily, court = minute
        if (body.contains("rate_limit") || body.contains("too many requests")) {
            if (retryAfterSec != null && retryAfterSec > 3600) return Classification.QUOTA_EXHAUSTED
            return Classification.TRANSIENT
        }
        return Classification.TRANSIENT
    }

    /**
     * NVIDIA NIM — endpoint cloud unifie. Format d'erreur OpenAI-like
     * generalement, mais quelques specificites :
     *  - 429 + "free credits exhausted" → QUOTA_EXHAUSTED
     *  - 429 standard → TRANSIENT (rate limit minute)
     *  - 401 + "invalid api key" → AUTH_ERROR (deja capture en amont)
     */
    private fun classifyNvidiaNim(httpCode: Int, body: String, retryAfterSec: Long?): Classification {
        if (body.contains("free credits exhausted") || body.contains("credits depleted")) {
            return Classification.QUOTA_EXHAUSTED
        }
        if (retryAfterSec != null && retryAfterSec > 3600) return Classification.QUOTA_EXHAUSTED
        return Classification.TRANSIENT
    }
}
