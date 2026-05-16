package com.shredcoach.app.domain.glucose

import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import kotlin.math.abs

/**
 * Analyse déterministe d'un historique glycémique : détection de patterns,
 * agrégats, tendances. Pure JVM — pas de dépendance Android, 100% testable.
 *
 * **Pourquoi déterministe et pas LLM-as-analyzer** : on veut pouvoir
 * 1) injecter le pattern dans le contexte IA AVANT l'appel LLM (faire raisonner
 * le LLM, pas faire son raisonnement), 2) tester finement les seuils, 3) ne
 * pas payer un round-trip LLM pour décider d'un body de notif.
 *
 * **Seuils** : calibrés sur la littérature CGM endocrino (ATTD/Battelino
 * consensus 2019, ADA Standards of Care 2024). Pour un athlète en sèche, on
 * applique une fourchette plus stricte (70-140 mg/dL) que la fourchette
 * standard diabétique (70-180), mais les patterns critiques (HYPO, VARIABILITY)
 * gardent les mêmes thresholds médicaux.
 */
object GlucoseAnalyzer {

    // ─── Seuils médicaux (mg/dL) ────────────────────────────────

    /** Fourchette cible standard adulte. */
    const val TARGET_LOW = 70.0
    const val TARGET_HIGH = 180.0

    /** Fourchette athlète strict — pour la TIR "athlétique". */
    const val ATHLETIC_TARGET_LOW = 70.0
    const val ATHLETIC_TARGET_HIGH = 140.0

    /** Seuil hypo (mg/dL). */
    const val HYPO_THRESHOLD = 70.0

    /** Pic postprandial notable. */
    const val POSTPRANDIAL_SPIKE_THRESHOLD = 180.0

    /** CV % au-delà duquel la variabilité est considérée pathologique. */
    const val CV_HIGH_THRESHOLD = 36.0

    /** TIR cible quotidien minimum pour "OK". */
    const val TIR_OK_THRESHOLD = 70

    // ─── Agrégats sur fenêtre ───────────────────────────────────

    /**
     * Calcule moyenne arithmétique des champs non-null d'une fenêtre.
     * Retourne null si aucun jour valide dans la fenêtre.
     */
    fun avgMgdl(logs: List<GlucoseLogEntity>): Double? {
        val vals = logs.mapNotNull { it.avgMgdl }
        return if (vals.isEmpty()) null else vals.average()
    }

    fun avgTir(logs: List<GlucoseLogEntity>): Double? {
        val vals = logs.mapNotNull { it.timeInRangePct?.toDouble() }
        return if (vals.isEmpty()) null else vals.average()
    }

    fun avgCv(logs: List<GlucoseLogEntity>): Double? {
        val vals = logs.mapNotNull { it.cv }
        return if (vals.isEmpty()) null else vals.average()
    }

    fun totalHypo(logs: List<GlucoseLogEntity>): Int =
        logs.mapNotNull { it.hypoCount }.sum()

    fun countWithData(logs: List<GlucoseLogEntity>): Int =
        logs.count { it.avgMgdl != null }

    /**
     * Régression linéaire simple sur les moyennes journalières → slope
     * mg/dL par semaine. Filtre les jours sans `avgMgdl`. Retourne null si
     * <3 points (pas assez stable pour une tendance).
     *
     * x = jour (0..N-1), y = avgMgdl. slope_par_jour × 7 = slope_par_semaine.
     */
    fun trendMgdlPerWeek(logs: List<GlucoseLogEntity>): Double? {
        val pts = logs.sortedBy { it.date }
            .mapIndexedNotNull { idx, log -> log.avgMgdl?.let { idx.toDouble() to it } }
        if (pts.size < 3) return null
        val n = pts.size
        val sumX = pts.sumOf { it.first }
        val sumY = pts.sumOf { it.second }
        val sumXY = pts.sumOf { it.first * it.second }
        val sumX2 = pts.sumOf { it.first * it.first }
        val denom = n * sumX2 - sumX * sumX
        if (abs(denom) < 1e-9) return null
        val slopePerDay = (n * sumXY - sumX * sumY) / denom
        return slopePerDay * 7.0
    }

    // ─── Détection de pattern dominant ───────────────────────────

    /**
     * Déduit un [GlucosePattern] dominant à partir d'un historique 30j.
     * Priorité du plus actionnable / spécifique au plus général. Première
     * règle qui match wins (early-return).
     */
    fun detectPattern(history30d: List<GlucoseLogEntity>): GlucosePattern {
        val daysWithData = countWithData(history30d)
        if (daysWithData < 7) return GlucosePattern.INSUFFICIENT_DATA

        val totalHypo = totalHypo(history30d)
        // 3 hypos+ sur 30j = pattern HYPO_RISK
        if (totalHypo >= 3) return GlucosePattern.HYPO_RISK

        val avgCv = avgCv(history30d) ?: 0.0
        if (avgCv >= CV_HIGH_THRESHOLD) return GlucosePattern.HIGH_VARIABILITY

        // Compte des jours avec pic >180 sur la fenêtre 7j la plus récente
        val recent7 = history30d.sortedByDescending { it.date }.take(7)
        val spikeDays = recent7.count { (it.peakMgdl ?: 0.0) >= POSTPRANDIAL_SPIKE_THRESHOLD }
        if (spikeDays >= 3) return GlucosePattern.POSTPRANDIAL_SPIKES

        // Élévation matinale chronique : min ≥ 100 ET min_time avant 09:00 sur ≥4j
        val dawnDays = history30d.count { log ->
            val mt = log.minTime ?: return@count false
            (log.minMgdl ?: 0.0) >= 100.0 && mt.hour < 9
        }
        if (dawnDays >= 4) return GlucosePattern.DAWN_PHENOMENON

        val trend = trendMgdlPerWeek(history30d) ?: 0.0
        if (trend >= 5.0) return GlucosePattern.RISING_TREND
        if (trend <= -5.0) return GlucosePattern.FALLING_TREND

        val avgTir = avgTir(history30d) ?: 0.0
        if (avgTir >= 80.0 && avgCv < 30.0) return GlucosePattern.STABLE_OPTIMAL

        return GlucosePattern.NORMAL
    }
}

/**
 * Pattern glycémique dominant sur ~30j, déduit par [GlucoseAnalyzer.detectPattern].
 *
 * Sert à :
 *  - L'orchestration de notifs (GlucoseRecapBuilder choisira un body par pattern).
 *  - L'injection dans les contextes IA (UserContextBuilder, Dr. Glykos system prompt).
 *  - L'affichage UI (badge pattern sur les screens stats/home).
 *
 * **Ordre de priorité** dans `detectPattern` : du plus actionnable au plus
 * général. HYPO_RISK > HIGH_VARIABILITY > POSTPRANDIAL_SPIKES > DAWN > TREND > STABLE > NORMAL.
 */
enum class GlucosePattern {
    /** <7 jours de data sur 30. Pas assez pour conclure. */
    INSUFFICIENT_DATA,

    /** ≥3 hypoglycémies (<70 mg/dL) sur 30j. Priorité absolue. */
    HYPO_RISK,

    /** CV ≥36% sur 30j — variabilité pathologique. */
    HIGH_VARIABILITY,

    /** ≥3 jours sur 7 avec pic ≥180 — réponse postprandiale exagérée. */
    POSTPRANDIAL_SPIKES,

    /** ≥4 matins avec min ≥100 mg/dL avant 09h — production hépatique nocturne. */
    DAWN_PHENOMENON,

    /** Slope ≥+5 mg/dL/semaine sur 30j — détérioration tendancielle. */
    RISING_TREND,

    /** Slope ≤−5 mg/dL/semaine — amélioration tendancielle. */
    FALLING_TREND,

    /** TIR moy ≥80% ET CV <30% sur 30j — régulation excellente. */
    STABLE_OPTIMAL,

    /** Rien de notable, régulation correcte sans pattern remarquable. */
    NORMAL,
}
