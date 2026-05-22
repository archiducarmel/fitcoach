package com.shredcoach.app.domain.llm

import com.shredcoach.app.data.remote.LlmProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event bus pour signaler une bascule sur le LLM fallback. Pattern equivalent
 * a [com.shredcoach.app.data.remote.GeminiRetryBus] : SharedFlow avec replay=0
 * pour ne pas montrer un message stale apres rotation d'ecran.
 *
 * Emis depuis les services LLM (GeminiMealService, LlmApiService,
 * BodyAnalysisService) quand le primary refuse pour cause de quota free tier
 * et qu'un fallback est configure. Le banner UI ([LlmFallbackBanner]) ecoute
 * et affiche un message humouristique pendant ~4 secondes.
 *
 * **Pourquoi un singleton bus** : decouple les services (qui n'ont pas de
 * Context UI) du banner (qui vit dans la Compose tree). N'importe quel
 * composant UI peut s'abonner.
 */
@Singleton
class LlmFallbackBus @Inject constructor() {

    private val _events = MutableSharedFlow<LlmFallbackEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val events: SharedFlow<LlmFallbackEvent> = _events.asSharedFlow()

    /**
     * Emit synchronously. Non-blocking, drop si buffer plein (rare —
     * un fallback se produit rarement plus de 4x par seconde).
     */
    fun emitTrySync(event: LlmFallbackEvent) {
        _events.tryEmit(event)
    }
}

/**
 * Payload d'un event de bascule. Contient les infos necessaires pour composer
 * un message contextuel : quel assistant, quel provider est tombe, sur quel
 * provider on bascule.
 */
data class LlmFallbackEvent(
    val assistant: AiAssistant,
    val primaryProvider: LlmProvider,
    val fallbackProvider: LlmProvider,
)

/**
 * Configuration du LLM fallback a utiliser si le primary epuise son quota.
 * Passe en parametre aux methodes des services (GeminiMealService, etc.).
 *
 * `null` = pas de fallback configure → comportement actuel (l'erreur primary
 * remonte au caller). C'est le cas par defaut absolu : back-compat preservee.
 */
data class FallbackConfig(
    val apiKey: String,
    val model: String,
    val provider: String,
)

/**
 * Messages humouristiques par couple (primary, fallback). Conserves dans le
 * domain layer (pas dans strings.xml) car ils dependent du couple specifique
 * de providers — trop combinatoire pour des string resources.
 *
 * **Tone** : decontracte mais clair. L'utilisateur DOIT comprendre que
 * (1) son LLM principal est temporairement HS, (2) on a bascule sur le
 * fallback, (3) c'est transparent pour lui.
 *
 * Locale-aware via [com.shredcoach.app.domain.i18n.PromptLocale].
 */
object LlmFallbackMessages {

    /**
     * Construit un message court (max ~80 chars) pour le snackbar/banner.
     * Format : "Provider X HS, je bascule sur Y" avec un peu d'humour.
     */
    fun shortMessage(event: LlmFallbackEvent): String {
        // FR par defaut, EN/ES/IT/PT/DE retombent sur la variante EN (langue
        // vehiculaire — meilleure que pas de message).
        return com.shredcoach.app.domain.i18n.PromptLocale.pick(
            fr = buildFr(event),
            en = buildEn(event),
        )
    }

    private fun buildFr(event: LlmFallbackEvent): String {
        val primary = event.primaryProvider.displayName
        val fallback = event.fallbackProvider.displayName
        // Quelques variantes humouristiques choisies aleatoirement-ish (hash sur l'assistant)
        val variants = listOf(
            "$primary a craqué — je passe sur $fallback 🥲",
            "$primary fait grève, $fallback prend le relais ✨",
            "Quota $primary épuisé — coucou $fallback 👋",
            "$primary KO, $fallback assure la suite 💪",
            "Pause café chez $primary — $fallback enchaîne ☕",
        )
        return variants[event.assistant.ordinal % variants.size]
    }

    private fun buildEn(event: LlmFallbackEvent): String {
        val primary = event.primaryProvider.displayName
        val fallback = event.fallbackProvider.displayName
        val variants = listOf(
            "$primary tapped out — switching to $fallback 🥲",
            "$primary on strike, $fallback takes over ✨",
            "$primary quota done — hi $fallback 👋",
            "$primary KO, $fallback got this 💪",
            "Coffee break at $primary — $fallback steps in ☕",
        )
        return variants[event.assistant.ordinal % variants.size]
    }
}
