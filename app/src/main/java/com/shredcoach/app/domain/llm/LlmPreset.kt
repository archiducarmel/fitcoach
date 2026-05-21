package com.shredcoach.app.domain.llm

import com.google.gson.JsonObject
import com.shredcoach.app.data.remote.LlmProvider

/**
 * Presets de configuration LLM par assistant, applicables en un tap depuis
 * l'écran Settings. Chaque preset configure les 19 assistants en cohérence
 * avec une intention utilisateur (économie / équilibre / qualité maximum).
 *
 * **Pourquoi des presets** : configurer 19 assistants un par un est fastidieux.
 * Les presets donnent un point d'entrée rapide pour l'user qui veut une
 * politique cohérente, puis il peut ajuster individuellement quelques
 * assistants si besoin.
 *
 * **DEFAULT** = retour aux fallbacks legacy (vide la map). C'est l'état d'un
 * user qui n'a jamais touché ce screen.
 */
enum class LlmPreset(
    val displayKey: String,
    val descriptionKey: String,
) {
    /** Tout sur les modèles les moins chers de chaque catégorie. */
    ECONOMIC("llm_preset_economic", "llm_preset_economic_desc"),
    /** Équilibre coût/qualité — defaults raisonnables par assistant. */
    BALANCED("llm_preset_balanced", "llm_preset_balanced_desc"),
    /** Top qualité partout, coût élevé (Gemini 3.5 Flash sur analyse, Claude Sonnet sur chat). */
    PREMIUM("llm_preset_premium", "llm_preset_premium_desc");

    /**
     * Construit le JSON d'overrides pour ce preset. Le format est le même que
     * `UserProfileEntity.llmAssistantOverridesJson` :
     * `{"assistant_key": {"provider": "X", "model": "Y"}}`.
     *
     * Couvre les 19 AiAssistant. Le caller fait ensuite
     * `profile.copy(llmAssistantOverridesJson = ...)` et `updateUserProfile`.
     */
    fun buildOverridesJson(): String {
        val root = JsonObject()
        AiAssistant.values().forEach { assistant ->
            val config = pickFor(assistant)
            if (config != null) {
                val entry = JsonObject().apply {
                    addProperty("provider", config.provider.name)
                    addProperty("model", config.model)
                }
                root.add(assistant.key, entry)
            }
        }
        return root.toString()
    }

    private data class Choice(val provider: LlmProvider, val model: String)

    /**
     * Mapping preset × assistant → (provider, model). Tient compte de :
     *  - Les contraintes vision (vision-only assistants peuvent pas utiliser GROQ gpt-oss)
     *  - Les sweet spots qualité/coût documentés (Gemini 2.5 Flash pour vision rapide,
     *    Gemini 3.5 Flash pour reasoning long, Claude Sonnet pour chat médical)
     */
    private fun pickFor(assistant: AiAssistant): Choice? = when (this) {
        ECONOMIC -> economicFor(assistant)
        BALANCED -> balancedFor(assistant)
        PREMIUM -> premiumFor(assistant)
    }

    private fun economicFor(assistant: AiAssistant): Choice = when {
        assistant.needsVision -> Choice(LlmProvider.GEMINI, "gemini-2.0-flash")
        assistant.category == AiCategory.CHAT -> Choice(LlmProvider.GROQ, "openai/gpt-oss-120b")
        else -> Choice(LlmProvider.GEMINI, "gemini-2.0-flash")
    }

    private fun balancedFor(assistant: AiAssistant): Choice = when {
        assistant.needsVision -> Choice(LlmProvider.GEMINI, "gemini-2.5-flash")
        assistant.category == AiCategory.CHAT -> Choice(LlmProvider.GROQ, "openai/gpt-oss-120b")
        else -> Choice(LlmProvider.GEMINI, "gemini-2.5-flash")
    }

    private fun premiumFor(assistant: AiAssistant): Choice = when (assistant) {
        // Vision : pas de Gemini 3 Preview sur vision (pas de gain), reste sur 2.5-flash
        AiAssistant.MEAL_SCAN_PHOTO,
        AiAssistant.MEAL_SCAN_LEFTOVER,
        AiAssistant.BODY_SCAN,
        AiAssistant.GYM_SCAN,
        AiAssistant.GLUCOSE_OCR -> Choice(LlmProvider.GEMINI, "gemini-2.5-flash")

        // Reasoning long-horizon : Gemini 3 Flash Preview brille
        AiAssistant.GLUCOSE_ANALYSIS,
        AiAssistant.WEEKLY_RECAP,
        AiAssistant.BODY_INSIGHT,
        AiAssistant.CALENDAR_RECAP -> Choice(LlmProvider.GEMINI, "gemini-3-flash-preview")

        // Chat : Claude Sonnet pour la qualité de conversation (Dr. Glykos
        // surtout — medical reasoning premium).
        AiAssistant.CHAT_SHREDDY -> Choice(LlmProvider.CLAUDE, "claude-sonnet-4-20250514")
        AiAssistant.CHAT_DR_GLYKOS -> Choice(LlmProvider.CLAUDE, "claude-sonnet-4-20250514")

        // Background : Gemini 2.5 Flash suffit (court, rapide)
        AiAssistant.PROACTIVE_COACH,
        AiAssistant.WORKOUT_DEBRIEF,
        AiAssistant.MEAL_DEBRIEF,
        AiAssistant.SCHEDULED_REMINDER -> Choice(LlmProvider.GEMINI, "gemini-2.5-flash")

        // Utility : economic
        AiAssistant.MEAL_SCAN_TEXT,
        AiAssistant.GYM_SCAN_RERANK,
        AiAssistant.INSTRUCTIONS_TRANSLATE -> Choice(LlmProvider.GEMINI, "gemini-2.5-flash")
    }
}
