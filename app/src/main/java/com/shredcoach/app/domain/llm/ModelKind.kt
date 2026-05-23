package com.shredcoach.app.domain.llm

/**
 * Type/modalité d'un modèle IA. Output-driven : le kind décrit ce que produit
 * le modèle, ce qui détermine l'endpoint API et l'UI de rendu côté debug page.
 *
 * Les modalités d'INPUT sont des flags séparés (`acceptsImageInput`, etc.)
 * sur [LlmModelInfo] pour éviter l'explosion combinatoire (un modèle qui
 * accepte text+image+audio aurait 2³=8 kinds sinon).
 *
 * **Taxonomie élargie V2** couvrant les 150+ modèles NVIDIA NIM + GitHub Models :
 *  - Modèles conversationnels et code → LANGUAGE
 *  - Vision-language → VLM (LANGUAGE + acceptsImageInput)
 *  - Embeddings text-only ou multimodaux → EMBEDDING / MULTIMODAL_EMBEDDING
 *  - Rerankers → RERANKER
 *  - Génération média → IMAGE_GENERATION / VIDEO_GENERATION
 *  - Audio bidirectionnel → TTS / STT
 *  - Vision spécialisée → OBJECT_DETECTION / OCR
 *  - Modèles d'évaluation → CLASSIFICATION / REWARD_MODEL
 *  - Domaines scientifiques → SCIENTIFIC (catch-all)
 *  - Optimisation combinatoire → OPTIMIZATION
 */
enum class ModelKind(
    /** Clé pour les string resources d'affichage UI. */
    val labelKey: String,
    /** Icône emoji synthétique pour le picker debug. */
    val emoji: String,
    /** True si le modèle produit du texte (drive si on stream en SSE). */
    val producesText: Boolean,
    /** True si le modèle accepte un prompt texte en entrée. */
    val acceptsTextInput: Boolean,
) {
    /**
     * Conversationnel/generation texte. Endpoint : /chat/completions (ou équivalent).
     * Couvre : chat, code generation, translation, reasoning, safety judging.
     * Différencier finement via [LlmModelInfo.supportsThinking/CodeGen/Translation].
     */
    LANGUAGE(
        labelKey = "model_kind_chat",
        emoji = "💬",
        producesText = true,
        acceptsTextInput = true,
    ),

    /**
     * Vision-Language Model : accepte text + image en entrée, produit du texte.
     * Endpoint : /chat/completions avec content blocks. Sous-type fonctionnel
     * de LANGUAGE mais distinct dans le picker UI.
     */
    VLM(
        labelKey = "model_kind_vlm",
        emoji = "👁️",
        producesText = true,
        acceptsTextInput = true,
    ),

    /** Embedding texte → vecteur dense. Endpoint /embeddings. */
    EMBEDDING(
        labelKey = "model_kind_embedding",
        emoji = "🔢",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Embedding multimodal text+image → vecteur unifié (CLIP-like). */
    MULTIMODAL_EMBEDDING(
        labelKey = "model_kind_mm_embedding",
        emoji = "🔢👁️",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Reranker : query + documents → scores ordonnés. Endpoint /rerank. */
    RERANKER(
        labelKey = "model_kind_reranker",
        emoji = "📊",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Image Generation : texte → image. Endpoint /images/generations. */
    IMAGE_GENERATION(
        labelKey = "model_kind_image_gen",
        emoji = "🎨",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Video / 3D generation : image ou texte → vidéo/3D. Endpoints custom. */
    VIDEO_GENERATION(
        labelKey = "model_kind_video_gen",
        emoji = "🎬",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Text-To-Speech : texte → audio. Endpoint /audio/speech. */
    TTS(
        labelKey = "model_kind_tts",
        emoji = "🔊",
        producesText = false,
        acceptsTextInput = true,
    ),

    /** Speech-To-Text : audio → texte. Endpoint /audio/transcriptions. */
    STT(
        labelKey = "model_kind_stt",
        emoji = "🎙️",
        producesText = true,
        acceptsTextInput = false,
    ),

    /** Détection d'objets : image → bounding boxes + labels. Endpoint custom. */
    OBJECT_DETECTION(
        labelKey = "model_kind_object_detection",
        emoji = "🎯",
        producesText = false,
        acceptsTextInput = false,
    ),

    /** OCR / Document parsing : image/PDF → texte structuré. Endpoint custom. */
    OCR(
        labelKey = "model_kind_ocr",
        emoji = "📄",
        producesText = true,
        acceptsTextInput = false,
    ),

    /**
     * Classification : input (texte/image/vidéo) → catégories.
     * Inclut safety/moderation (llama-guard, nemoguard), PII detection,
     * AI-generated / deepfake detection. Endpoint custom.
     */
    CLASSIFICATION(
        labelKey = "model_kind_classification",
        emoji = "🛡️",
        producesText = false,
        acceptsTextInput = true,
    ),

    /**
     * Reward model : (prompt, response) → score de qualité.
     * Utilisé pour RLHF/training. Endpoint /chat/completions avec scoring.
     */
    REWARD_MODEL(
        labelKey = "model_kind_reward",
        emoji = "🏆",
        producesText = false,
        acceptsTextInput = true,
    ),

    /**
     * Modèles scientifiques spécialisés (biologie, chimie, climat, médecine
     * 3D). Catch-all pour AlphaFold, ESMFold, DiffDock, MolMIM, FourCastNet,
     * MAISI, Vista3D, etc. Endpoints custom NVIDIA NIM.
     *
     * **Hors-scope FitCoach** : classifiés pour exhaustivité de l'inventaire,
     * pas exposés dans la debug page V1 (interactions trop spécialisées).
     */
    SCIENTIFIC(
        labelKey = "model_kind_scientific",
        emoji = "🧬",
        producesText = false,
        acceptsTextInput = true,
    ),

    /**
     * Optimisation combinatoire : VRP, routing, scheduling (cuOpt).
     * Hors-scope FitCoach V1.
     */
    OPTIMIZATION(
        labelKey = "model_kind_optimization",
        emoji = "🗺️",
        producesText = false,
        acceptsTextInput = false,
    );

    /** True si ce kind utilise l'endpoint OpenAI-compatible /chat/completions. */
    val usesChatEndpoint: Boolean
        get() = this == LANGUAGE || this == VLM || this == REWARD_MODEL

    /** True si ce kind accepte une image en input par défaut. */
    val acceptsImageInputByDefault: Boolean
        get() = this == VLM || this == OBJECT_DETECTION || this == OCR ||
                this == MULTIMODAL_EMBEDDING

    /** True si ce kind accepte de l'audio en input. */
    val acceptsAudioInputByDefault: Boolean
        get() = this == STT

    /**
     * Kinds dont l'UI debug page est implémentée en V1. Les autres
     * (SCIENTIFIC, OPTIMIZATION, REWARD_MODEL) sont classifiables mais pas
     * interactifs dans cette première version (UI custom V2 si besoin).
     */
    val isInteractiveInDebugV1: Boolean
        get() = when (this) {
            LANGUAGE, VLM, EMBEDDING, MULTIMODAL_EMBEDDING, RERANKER,
            IMAGE_GENERATION, TTS, STT, OBJECT_DETECTION, OCR, CLASSIFICATION,
            VIDEO_GENERATION -> true
            SCIENTIFIC, OPTIMIZATION, REWARD_MODEL -> false
        }

    companion object {
        /** Set des kinds "language" qui partagent le pipeline /chat/completions. */
        val CHAT_COMPLETION_KINDS: Set<ModelKind> = setOf(LANGUAGE, VLM, REWARD_MODEL)
    }
}
