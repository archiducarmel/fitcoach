package com.shredcoach.app.domain.llm

/**
 * Helper NVIDIA NIM — detection des modeles "slow" (reasoning) qui necessitent
 * streaming + long timeout (300s).
 *
 * **Historique** : ce fichier contenait avant un catalogue editorial avec 49
 * IDs fictifs (deepseek-v4-pro, kimi-k2.6, etc. qui n'existent PAS dans
 * NVIDIA NIM reel). Ces IDs etaient ajoutees dans `buildList { addAll(...) }`
 * mais en raison de l'ordre d'initialisation Kotlin (val initialisees dans
 * l'ordre source), `ALL_MODELS` (declare ligne 34) lisait les listes
 * particulieres (declarees ligne 53+) AVANT qu'elles soient initialisees,
 * provoquant `NullPointerException` dans `<clinit>` et `NoClassDefFoundError`
 * a tous les usages ulterieurs de l'objet.
 *
 * **Fix** : suppression complete du catalogue editorial. Le catalogue NVIDIA
 * est maintenant construit DYNAMIQUEMENT depuis les vrais IDs renvoyes par
 * /v1/models (cf. NvidiaNimCatalogService.classifyNvidiaModel). Seul reste
 * cet helper `isSlow` qui detecte les reasoning models via patterns d'id.
 */
object NvidiaNimCatalog {

    /**
     * True si le modele a besoin de streaming + long timeout (reasoning models).
     * Detection par keywords sur l'id, miroir de la logique Python utilisee
     * par le script test.
     */
    fun isSlow(modelId: String): Boolean {
        val keywords = listOf(
            "thinking", "reasoning", "v4-pro", "v4-flash",
            "qwq", "nemotron-ultra", "glm-5.1", "kimi-k2.6",
            "deepseek-r1",  // DeepSeek R1 reasoning
            "o1-preview", "o1-mini",  // OpenAI o-series
        )
        val lower = modelId.lowercase()
        return keywords.any { lower.contains(it) }
    }
}
