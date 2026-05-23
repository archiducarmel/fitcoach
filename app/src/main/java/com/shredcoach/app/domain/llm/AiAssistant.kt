package com.shredcoach.app.domain.llm

/**
 * Enum exhaustive des 19 assistants IA de l'app, avec leurs caractéristiques
 * pour la résolution LLM (catégorie, exigence vision, défaut historique).
 *
 * **Pourquoi enum + key string** : l'enum donne l'exhaustivité et le compile-time
 * check côté Kotlin, le `key` string sert de clé stable dans le JSON de profile
 * (résilient au renommage d'enum value).
 *
 * **needsVision** : true pour les assistants qui parsent une image. Filtre les
 * providers à l'UI (seuls les providers vision-capable apparaissent).
 *
 * **defaultProvider / defaultModel** : valeurs utilisées si l'user n'a JAMAIS
 * configuré cet assistant. Calibrés pour préserver le comportement actuel
 * (back-compat absolue).
 *
 * **fallbackLegacy** : pour les assistants qui lisent actuellement les champs
 * legacy `geminiModel` ou `llmModel` du profile. Si l'user n'a pas d'override
 * pour cet assistant, on retombe sur le champ legacy correspondant — donc le
 * Settings actuel "Modèle Gemini" et "Modèle Chat" continue à fonctionner.
 */
enum class AiAssistant(
    val key: String,
    val category: AiCategory,
    val needsVision: Boolean,
    val fallbackLegacy: LegacyConfigSource,
) {
    // ─── VISION SCANNERS ─────────────────────────────────────────────────
    MEAL_SCAN_PHOTO("meal_scan_photo", AiCategory.VISION, needsVision = true,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),
    MEAL_SCAN_TEXT("meal_scan_text", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),
    MEAL_SCAN_LEFTOVER("meal_scan_leftover", AiCategory.VISION, needsVision = true,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),
    BODY_SCAN("body_scan", AiCategory.VISION, needsVision = true,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),
    GYM_SCAN("gym_scan", AiCategory.VISION, needsVision = true,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),
    GLUCOSE_OCR("glucose_ocr", AiCategory.VISION, needsVision = true,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN),

    // ─── ANALYSE LONG-HORIZON / REASONING ────────────────────────────────
    GLUCOSE_ANALYSIS("glucose_analysis", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.HARDCODED_GEMINI_25),
    BODY_INSIGHT("body_insight", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    WEEKLY_RECAP("weekly_recap", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    CALENDAR_RECAP("calendar_recap", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),

    // ─── CHAT CONVERSATIONNEL ────────────────────────────────────────────
    CHAT_SHREDDY("chat_shreddy", AiCategory.CHAT, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    CHAT_DR_GLYKOS("chat_dr_glykos", AiCategory.CHAT, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),

    // ─── BACKGROUND TASKS (workers, notifs proactives) ───────────────────
    PROACTIVE_COACH("proactive_coach", AiCategory.BACKGROUND, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    WORKOUT_DEBRIEF("workout_debrief", AiCategory.BACKGROUND, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    MEAL_DEBRIEF("meal_debrief", AiCategory.BACKGROUND, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    SCHEDULED_REMINDER("scheduled_reminder", AiCategory.BACKGROUND, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),

    // ─── UTILITY ─────────────────────────────────────────────────────────
    GYM_SCAN_RERANK("gym_scan_rerank", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.CHAT),
    INSTRUCTIONS_TRANSLATE("instructions_translate", AiCategory.ANALYSIS, needsVision = false,
        fallbackLegacy = LegacyConfigSource.MEAL_SCAN);

    /**
     * Kind du modele requis par cet assistant. Derivé de [needsVision] pour
     * back-compat : assistant vision → VLM, sinon CHAT. Permet aux pickers
     * (Settings, Debug) de filtrer la liste des modeles candidats.
     *
     * **A faire evoluer en V2** si on ajoute des assistants STT/TTS/etc.
     * (ex : "Transcription voice → meal log" = STT). Pour l'instant tous les
     * 18 assistants existants sont CHAT ou VLM.
     */
    val requiredKind: ModelKind
        get() = if (needsVision) ModelKind.VLM else ModelKind.LANGUAGE

    companion object {
        fun fromKey(key: String?): AiAssistant? =
            values().firstOrNull { it.key == key }
    }
}

/**
 * Catégorie fonctionnelle d'un assistant. Drive le grouping dans l'UI Settings
 * et permet de proposer des presets "tous les Vision = X" / "tous les Chat = Y".
 */
enum class AiCategory(val displayKey: String) {
    VISION("ai_category_vision"),
    CHAT("ai_category_chat"),
    ANALYSIS("ai_category_analysis"),
    BACKGROUND("ai_category_background");
}

/**
 * Source legacy à consulter en l'absence d'override par-assistant. Garantit la
 * back-compat absolue : sans override, le user voit le comportement actuel.
 *
 *  - MEAL_SCAN : utilise les champs `mealScanProvider` + `geminiModel` du profile
 *    (configuré dans Settings → IA Scan section actuelle)
 *  - CHAT : utilise les champs `llmProvider` + `llmModel` du profile
 *    (configuré dans Settings → Chat section actuelle)
 *  - HARDCODED_GEMINI_25 : `GEMINI` + `gemini-2.5-flash`, valeurs hardcodées
 *    actuellement dans GlucoseOcrService / GlucoseAnalysisEngine /
 *    InstructionsTranslator. Pas configurable AVANT cette evolution.
 */
enum class LegacyConfigSource {
    MEAL_SCAN,
    CHAT,
    HARDCODED_GEMINI_25,
}
