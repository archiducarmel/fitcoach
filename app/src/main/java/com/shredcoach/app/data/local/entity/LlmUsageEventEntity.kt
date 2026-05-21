package com.shredcoach.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Une entrée de télémétrie pour un appel LLM. Capture qui (assistant), quoi
 * (provider/model), combien (tokens input/output/thinking), quand (timestamp)
 * et combien ça a coûté (estimation USD via [com.shredcoach.app.domain.llm.LlmPricing]).
 *
 * **Indexée** sur timestamp + assistantKey + model pour les agrégations
 * frequentes du dashboard (group by + filter par fenêtre temporelle).
 *
 * **Capture point** : émis par [com.shredcoach.app.domain.llm.LlmUsageRecorder]
 * après chaque appel LLM réussi ou échoué (success=false sur erreur réseau /
 * 5xx). Les callsites sont instrumentés via wrapping dans GeminiMealService
 * et LlmApiService — les ViewModels passent l'AiAssistant en hint.
 *
 * **Rétention** : pas d'auto-purge V1. À ~200 bytes par row, 365 jours × 100
 * events/jour = ~7 MB → acceptable. Une purge optionnelle peut être ajoutée
 * en V2 si les users power se plaignent.
 *
 * **Privacy** : pas de payload (prompt / response) stocké, uniquement les
 * compteurs aggregables. RGPD-safe : aucune donnée personnelle au-delà du
 * profil métabolique de consommation.
 */
@Entity(
    tableName = "llm_usage_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["assistantKey"]),
        Index(value = ["model"]),
    ],
)
@Immutable
data class LlmUsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [com.shredcoach.app.domain.llm.AiAssistant.key] qui a déclenché l'appel. */
    val assistantKey: String,
    /** Nom du [com.shredcoach.app.data.remote.LlmProvider] (e.g., "GEMINI"). */
    val provider: String,
    /** Model ID (e.g., "gemini-2.5-flash"). */
    val model: String,
    /** Tokens en entrée (prompt + system). */
    val tokensInput: Int,
    /** Tokens en sortie (texte généré). */
    val tokensOutput: Int,
    /**
     * Tokens de "thinking" / chain-of-thought (Gemini 2.5+). 0 si provider ne
     * facture pas séparément ou si thinking désactivé.
     */
    val tokensThinking: Int = 0,
    /** Latence réseau end-to-end en millisecondes. */
    val latencyMs: Long,
    val timestamp: LocalDateTime,
    /** False si l'appel a échoué (erreur réseau, 5xx, parse fail). */
    val success: Boolean,
    /** Coût estimé en USD (depuis [com.shredcoach.app.domain.llm.LlmPricing]). */
    val costUsd: Double,
)
