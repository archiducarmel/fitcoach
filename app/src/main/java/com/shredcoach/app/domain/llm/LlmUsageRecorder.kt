package com.shredcoach.app.domain.llm

import android.util.Log
import com.shredcoach.app.data.local.dao.LlmUsageDao
import com.shredcoach.app.data.local.entity.LlmUsageEventEntity
import com.shredcoach.app.data.remote.LlmProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enregistreur de télémétrie LLM. Singleton injecté dans les services qui
 * font des appels LLM (GeminiMealService, LlmApiService) — émet un event en
 * DB pour chaque appel après son retour.
 *
 * **Fire-and-forget** : l'écriture DB se fait sur un scope dédié non-bloquant
 * pour ne pas ajouter de latence à l'appel LLM en cours. Si l'écriture
 * échoue (DB locked, etc.), on log et on swallow — la télémétrie n'est pas
 * critique au flow.
 *
 * **Privacy** : aucune donnée personnelle stockée (pas de prompts, pas de
 * réponses) — seulement les compteurs aggregables.
 */
@Singleton
class LlmUsageRecorder @Inject constructor(
    private val dao: LlmUsageDao,
) {
    // Scope dédié hors viewModelScope/lifecycleScope — la télémétrie survit
    // au caller. SupervisorJob pour qu'un échec d'insert n'annule pas les
    // suivants.
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Enregistre un event LLM. Fire-and-forget (retourne immédiatement).
     *
     * @param assistant L'assistant qui a déclenché l'appel. Null = appel non
     *   attribué (legacy ou caller direct sans contexte) — non enregistré.
     */
    fun record(
        assistant: AiAssistant?,
        provider: LlmProvider,
        model: String,
        tokensInput: Int,
        tokensOutput: Int,
        tokensThinking: Int = 0,
        latencyMs: Long,
        success: Boolean,
    ) {
        if (assistant == null) {
            Log.v(TAG, "Skip recording — no assistant context")
            return
        }
        val costUsd = LlmPricing.estimate(
            provider = provider,
            model = model,
            tokensInput = tokensInput,
            tokensOutput = tokensOutput,
            tokensThinking = tokensThinking,
        )
        val event = LlmUsageEventEntity(
            assistantKey = assistant.key,
            provider = provider.name,
            model = model,
            tokensInput = tokensInput,
            tokensOutput = tokensOutput,
            tokensThinking = tokensThinking,
            latencyMs = latencyMs,
            timestamp = LocalDateTime.now(),
            success = success,
            costUsd = costUsd,
        )
        recorderScope.launch {
            try {
                dao.insert(event)
                Log.v(TAG, "Recorded ${assistant.key}/$model — tokens=${tokensInput + tokensOutput + tokensThinking}, cost=$${"%.4f".format(costUsd)}")
            } catch (e: Exception) {
                Log.w(TAG, "Insert telemetry failed (swallowed): ${e.message}")
            }
        }
    }

    /** Reset complet de la télémétrie (bouton "vider l'historique" du dashboard). */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    companion object {
        private const val TAG = "LlmUsageRecorder"
    }
}
