package com.shredcoach.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Analyse experte de la courbe glycémique d'une journée, produite par le LLM
 * Dr. Glykos à partir de la curve CGM + repas logués.
 *
 * **Cache durable** : on persiste l'analyse pour éviter de re-payer l'inférence
 * LLM à chaque ouverture de l'écran. Invalidation par [inputHash] — un hash
 * stable des données d'entrée (curve + meals). Si l'user modifie un repas ou
 * re-upload son CGM, le hash change → re-analyze automatique.
 *
 * **UNIQUE date** : 1 analyse par jour, refonte propre via `REPLACE` côté DAO
 * si re-génération demandée par l'user.
 *
 * **Schéma insightsJson** :
 * ```
 * [
 *   {
 *     "time": "HH:mm",
 *     "category": "DAWN | POSTPRANDIAL_PEAK | RECOVERY | HYPO | SPIKE | STABLE_FASTING | CORTISOL_RISE | NIGHT_FASTING | EXERCISE_RESPONSE",
 *     "title": "Pic post-déjeuner contrôlé",
 *     "explanation": "Vers 13h25 ton glucose monte à 165 mg/dL...",
 *     "verdict": "POSITIVE | NEUTRAL | CONCERN",
 *     "relatedMealName": "Nouilles sautées" (optional)
 *   },
 *   ...
 * ]
 * ```
 *
 * Pas de FK vers `glucose_logs` car la relation est par date (déjà unique côté
 * glucose_logs). Évite les cascades complexes — si l'user supprime son log
 * glucose, l'analyse devient orpheline mais inoffensive (cleanup batch possible
 * en background).
 */
@Entity(
    tableName = "glucose_analyses",
    indices = [Index(value = ["date"], unique = true)],
)
@Immutable
data class GlucoseAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val createdAt: LocalDateTime,

    /** Verdict global — drive la couleur du hero verdict pill côté UI. */
    val verdict: AnalysisVerdict,

    /** Phrase résumé 1-2 lignes — utilisée pour la notif quotidienne. */
    val summary: String,

    /**
     * Conseil global actionnable pour demain. Optionnel — vide si l'analyse
     * juge la journée "parfaite" et qu'il n'y a rien à conseiller.
     */
    val globalAdvice: String,

    /** JSON array des insights (cf. KDoc classe). */
    val insightsJson: String,

    /**
     * Hash SHA-256 des inputs (curve + meals + métriques) → invalidation cache.
     * Si user modifie un repas, le hash change et on re-déclenche l'analyse au
     * prochain open.
     */
    val inputHash: String,

    /** Modèle LLM utilisé. Utile pour la rétro-analyse de qualité par version. */
    val llmModel: String,

    /** Tokens consommés (si l'API les retourne). Pour la télémétrie coût. */
    val tokensUsed: Int? = null,

    /** Latence d'inférence en ms (telemetry). */
    val latencyMs: Long? = null,
)

/**
 * Verdict global d'une journée glycémique. Drive la couleur du hero card et
 * priorise le ton du résumé (felicitation, normal, attention).
 *
 * Critères (calculables côté Kotlin pré-LLM pour cohérence, le LLM peut sur-
 * classer si patterns détectés) :
 *  - EXCELLENT : TIR>=85, peak<160, 0 hypo, CV<30
 *  - GOOD     : TIR>=70, peak<180, 0-1 hypo, CV<36
 *  - FAIR     : TIR>=50 ou peak<220, 1-2 hypos
 *  - CONCERN  : TIR<50, peak>=220, >=3 hypos, ou pattern HYPO_RISK
 */
enum class AnalysisVerdict { EXCELLENT, GOOD, FAIR, CONCERN }
