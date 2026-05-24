package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolveur centralise de cles API : LlmProvider -> SecureKeyStore.Provider.
 *
 * **Pourquoi ce singleton** : avant v2026.05, chaque ViewModel/Worker faisait
 * `userRepository.getApiKey(SecureKeyStore.Provider.LLM)` hardcode. Avec les
 * overrides per-assistant (Settings → Assistants IA), un user pouvait
 * configurer Shreddy sur GitHub Models gpt-4o mais le code envoyait la cle
 * LLM (= Groq) -> 401 systematique.
 *
 * Ce resolveur garantit qu'on lit TOUJOURS la cle du provider resolu :
 *
 * | LlmProvider     | SecureKeyStore key (priorite 1) | Fallback                |
 * |-----------------|---------------------------------|-------------------------|
 * | GROQ            | GROQ                            | LLM (legacy)            |
 * | OPENAI          | OPENAI                          | LLM (legacy)            |
 * | CLAUDE          | CLAUDE                          | LLM (legacy)            |
 * | GEMINI          | GEMINI                          | —                       |
 * | MISTRAL         | MISTRAL                         | —                       |
 * | GITHUB_MODELS   | GITHUB_MODELS                   | —                       |
 * | NVIDIA_NIM      | NVIDIA_NIM                      | —                       |
 * | POLLINATIONS    | (aucune — no auth)              | —                       |
 * | CLOUDFLARE_AI   | CLOUDFLARE_AI_TOKEN + ACCOUNT_ID| —                       |
 *
 * Le **fallback LLM** pour GROQ/OPENAI/CLAUDE supporte les users existants qui
 * ont stocke leur cle dans le slot LLM unifie. La migration auto au boot
 * (voir [migrateLegacyLlmKey]) copie LLM -> dedicated selon `profile.llmProvider`.
 */
@Singleton
class LlmKeyResolver @Inject constructor(
    private val userRepository: UserRepository,
) {

    /**
     * Retourne la cle API pour ce provider, ou blank si absente.
     *
     * @param provider provider resolu par AssistantLlmResolver
     * @return cle non-blank si disponible, blank sinon (caller doit gerer le
     *  message d'erreur "Configure ta cle")
     */
    fun keyFor(provider: LlmProvider): String {
        return when (provider) {
            LlmProvider.GROQ -> firstNonBlank(
                SecureKeyStore.Provider.GROQ,
                SecureKeyStore.Provider.LLM,  // legacy fallback
            )
            LlmProvider.OPENAI -> firstNonBlank(
                SecureKeyStore.Provider.OPENAI,
                SecureKeyStore.Provider.LLM,  // legacy si chat etait deja OpenAI
            )
            LlmProvider.CLAUDE -> firstNonBlank(
                SecureKeyStore.Provider.CLAUDE,
                SecureKeyStore.Provider.LLM,  // legacy si chat etait deja Claude
            )
            LlmProvider.GEMINI -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
            LlmProvider.MISTRAL -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
            LlmProvider.GITHUB_MODELS -> userRepository.getApiKey(SecureKeyStore.Provider.GITHUB_MODELS)
            LlmProvider.NVIDIA_NIM -> userRepository.getApiKey(SecureKeyStore.Provider.NVIDIA_NIM)
            LlmProvider.CLOUDFLARE_AI -> userRepository.getApiKey(SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN)
            LlmProvider.POLLINATIONS -> "" // no auth requise
        }
    }

    /** Verifie qu'au moins une cle est configuree pour le provider. */
    fun hasKey(provider: LlmProvider): Boolean = keyFor(provider).isNotBlank()

    /** Label user-friendly pour les messages d'erreur "Configure ta cle X". */
    fun displayName(provider: LlmProvider): String = when (provider) {
        LlmProvider.GROQ -> "Groq"
        LlmProvider.OPENAI -> "OpenAI"
        LlmProvider.CLAUDE -> "Claude (Anthropic)"
        LlmProvider.GEMINI -> "Gemini (Google)"
        LlmProvider.MISTRAL -> "Mistral"
        LlmProvider.GITHUB_MODELS -> "GitHub Models"
        LlmProvider.NVIDIA_NIM -> "NVIDIA NIM"
        LlmProvider.CLOUDFLARE_AI -> "Cloudflare AI"
        LlmProvider.POLLINATIONS -> "Pollinations"
    }

    /**
     * Migration legacy : si `profile.llmProvider` est GROQ/OPENAI/CLAUDE et le
     * slot LLM contient une cle, copier vers le slot dedie correspondant pour
     * que les futurs lookups via keyFor() soient propres meme sans fallback.
     *
     * Idempotent : si la cle dediee existe deja, ne fait rien.
     * A appeler au boot (UserRepository init ou app startup).
     */
    fun migrateLegacyLlmKey(legacyProviderName: String?) {
        val legacy = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        if (legacy.isBlank()) return
        val target = when (legacyProviderName?.uppercase()) {
            "GROQ" -> SecureKeyStore.Provider.GROQ
            "OPENAI" -> SecureKeyStore.Provider.OPENAI
            "CLAUDE" -> SecureKeyStore.Provider.CLAUDE
            else -> return  // legacy point a un provider qui a deja sa propre cle
        }
        val existing = userRepository.getApiKey(target)
        if (existing.isBlank()) {
            userRepository.setApiKey(target, legacy)
        }
    }

    private fun firstNonBlank(vararg providers: SecureKeyStore.Provider): String {
        for (p in providers) {
            val k = userRepository.getApiKey(p)
            if (k.isNotBlank()) return k
        }
        return ""
    }
}
