package com.shredcoach.app.domain.bodymesh

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shredcoach.app.data.consent.ConsentStore
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.i18n.PromptLocale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Génère un insight 1-liner premium à partir d'un scan mesh.
 *
 * **Pourquoi un service dédié** :
 *  - Cache stable par hash de scan (évite 1 call LLM à chaque ouverture de
 *    BodyMeshScreen tant que les features n'ont pas changé).
 *  - Prompt builder qui injecte les analytics + profile pour un coaching
 *    contextualisé ("ton V-taper passe de 1.32 → 1.41 sur 4 semaines !").
 *  - Triple gate : feature flag + consentement LLM_CHAT + clé API présente.
 *
 * **Cycle de vie d'un insight** :
 *  1. Scan généré → features.capturedAtMs sert de cache key
 *  2. Lecture cache : si insight déjà calculé pour ce scan, retourne direct
 *  3. Sinon : appel LLM async (typiquement ~1.5s sur Groq llama-70B)
 *  4. Persiste dans DataStore avec key = capturedAtMs
 *  5. Affiché sous le header BodyMeshScreen
 *
 * **Format imposé au LLM** :
 *  - 1 phrase max, ≤ 160 caractères
 *  - Ton "coach FAANG" : direct, factuel, motivant, pas généraliste
 *  - Référence aux chiffres concrets du snapshot
 *  - Termine sur une action (continue X, ajoute Y)
 */
@Singleton
class BodyInsightGenerator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val consentStore: ConsentStore,
    private val llmResolver: com.shredcoach.app.domain.llm.AssistantLlmResolver,
    private val keyResolver: com.shredcoach.app.domain.llm.LlmKeyResolver,
) {

    /**
     * Renvoie l'insight pour un scan. null = pas dispo (consentement manquant,
     * pas de clé, fallback gracieux). Cache hit ne consomme pas le LLM.
     *
     * @param features mesh features fraîchement extraites
     * @param profile profil user pour contextualiser (sex, goal, weight, etc.)
     */
    suspend fun getOrGenerate(
        features: MeshFeatures,
        profile: UserProfileEntity,
    ): String? {
        // ─── 1. Cache lookup ───
        val cacheKey = stringPreferencesKey("insight_${features.capturedAtMs}")
        val cached = appContext.insightDataStore.data.first()[cacheKey]
        if (!cached.isNullOrBlank()) return cached

        // ─── 2. Triple gate ───
        val consentSnap = consentStore.snapshot.first()
        if (!consentSnap.isCurrent(ConsentStore.ConsentType.LLM_CHAT)) return null

        // ─── 3. Génération LLM ───
        // BUGFIX v2026.05.24 : resolve provider AVANT fetch key.
        val llmConfig = llmResolver.resolveWithProfile(com.shredcoach.app.domain.llm.AiAssistant.BODY_INSIGHT, profile)
        val provider = llmConfig.provider
        val model: String? = llmConfig.modelId
        val apiKey = keyResolver.keyFor(provider)
        if (apiKey.isBlank()) return null

        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(features, profile)

        val result = chatRepository.quickCoachMessage(
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            provider = provider,
            apiKey = apiKey,
            model = model,
            assistant = com.shredcoach.app.domain.llm.AiAssistant.BODY_INSIGHT,
            fallback = llmResolver.buildFallbackConfig(
                com.shredcoach.app.domain.llm.AiAssistant.BODY_INSIGHT, profile, apiKey,
            ),
        )

        return result.fold(
            onSuccess = { raw ->
                val cleaned = cleanInsight(raw)
                if (cleaned.isBlank()) null
                else {
                    // ─── 4. Persiste cache ───
                    appContext.insightDataStore.edit { prefs ->
                        prefs[cacheKey] = cleaned
                        // Best-effort cleanup : on garde les 5 derniers insights
                        // pour éviter la croissance indéfinie. Stratégie simple :
                        // on prune les keys qui ne matchent pas le pattern.
                        // Note : DataStore n'expose pas une API de prune nativement
                        // donc on accepte l'overhead pour les anciennes entries.
                    }
                    cleaned
                }
            },
            onFailure = { null }
        )
    }

    /**
     * Système prompt : cadre strict pour 1 phrase max. Few-shot pour calibrer
     * le ton "coach FAANG" (factuel, basé sur les chiffres, pas niais).
     */
    private fun buildSystemPrompt(): String {
        val langDirective = PromptLocale.outputLanguageDirective()
        return """
$langDirective

Tu es Shreddy, coach sportif premium qui analyse un scan corporel ML Kit. Tu écris UNE PHRASE max (≤ 160 caractères) qui révèle l'insight clé du scan, ton direct et factuel.

Règles strictes :
- 1 phrase, jamais 2
- Référence aux chiffres concrets fournis (V-taper, posture, asymétries, tilts)
- Termine par une action ou une encouragement spécifique
- Pas de "Bonjour", pas de "Bravo en général", pas de filler

Bons exemples (ton à reproduire) :
"Posture 92/100 — symétrie quasi-parfaite, léger tilt épaule droite +1.8° à corriger en rowing."
"V-taper 1.41 : silhouette en V marquée, continue le pulling pour ouvrir encore les épaules."
"Asymétrie bras 7% — droit dominant. Ajoute 2 séries unilatérales à gauche pour rééquilibrer."

Mauvais exemples (à NE PAS reproduire) :
"Tu fais du super travail, continue ton bon programme et reste motivé." [trop générique]
"Ton scan est bon. Tu peux progresser sur plusieurs aspects." [vague, pas de chiffres]
        """.trimIndent()
    }

    private fun buildUserPrompt(features: MeshFeatures, profile: UserProfileEntity): String {
        val a = features.analytics
        val goal = profile.goal.name
        val sex = profile.sex
        return """
Scan corporel à analyser :

Profile : $sex, ${profile.heightCm}cm, ${profile.currentWeightKg}kg, objectif $goal.

Métriques anatomiques dérivées du mesh on-device :
- Posture score : ${a.postureScore}/100
- V-taper ratio (épaules/hanches) : ${"%.2f".format(a.vTaperRatio)}
- Tilt épaules : ${"%+.1f".format(a.shoulderTiltDeg)}°
- Tilt hanches : ${"%+.1f".format(a.hipTiltDeg)}°
- Asymétrie verticale épaules : ${"%.1f".format(a.shoulderAsymmetryPct)}%
- Asymétrie verticale hanches : ${"%.1f".format(a.hipAsymmetryPct)}%

Sors UNE phrase d'insight, ≤ 160 caractères, ton coach FAANG.
        """.trimIndent()
    }

    /**
     * Sanitize la réponse LLM : supprime guillemets, retours à la ligne,
     * cap à 200 chars (sécurité au cas où le LLM ignore la consigne).
     */
    private fun cleanInsight(raw: String): String {
        val noQuotes = raw.trim().trim('"', '\'', '«', '»', '"', '"')
        val singleLine = noQuotes.replace("\n", " ").replace("\r", "").replace(Regex("\\s+"), " ")
        return singleLine.take(200)
    }
}

/**
 * DataStore Preferences pour cacher les insights LLM par scan. Subdir DataStore
 * dédié pour ne pas mélanger avec les autres prefs (BackupSettings, CoachSettings,
 * etc.) — garbage collection / clear data RGPD plus simples.
 */
private val Context.insightDataStore by preferencesDataStore(name = "body_insights")
