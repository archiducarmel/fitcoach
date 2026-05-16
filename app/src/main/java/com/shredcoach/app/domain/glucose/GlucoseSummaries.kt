package com.shredcoach.app.domain.glucose

import java.time.LocalDate
import java.time.LocalTime

/**
 * Snapshot des métriques du jour (ou null si pas de log). Forme légère
 * destinée aux UIs (cards Home/Nutrition) et aux contextes IA.
 */
data class GlucoseDaySummary(
    val date: LocalDate,
    val avgMgdl: Double?,
    val peakMgdl: Double?,
    val peakTime: LocalTime?,
    val minMgdl: Double?,
    val minTime: LocalTime?,
    val timeInRangePct: Int?,
    val hypoCount: Int?,
    val cv: Double?,
    val parseConfidence: Float?,
    val manualOverride: Boolean,
    val imagePath: String?,
    val notes: String?,
) {
    /** Vrai si on a au moins une métrique exploitable (pas juste l'image). */
    val hasMetrics: Boolean
        get() = avgMgdl != null || timeInRangePct != null || peakMgdl != null
}

/**
 * Agrégat sur une fenêtre 7j ou 30j. Tous les champs nullable car certaines
 * fenêtres peuvent ne pas avoir de données suffisantes.
 */
data class GlucoseWindowSummary(
    val daysCovered: Int,
    val avgMgdl: Double?,
    val avgTirPct: Double?,
    val avgCv: Double?,
    val totalHypo: Int,
    /** Slope mg/dL par semaine (régression linéaire). */
    val trendMgdlPerWeek: Double?,
    val pattern: GlucosePattern,
)
