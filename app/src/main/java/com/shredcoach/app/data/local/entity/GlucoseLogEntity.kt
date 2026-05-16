package com.shredcoach.app.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Une entrée de suivi glycémique correspondant à un screenshot CGM journalier
 * (FreeStyle Libre, Dexcom, Diabox, Nightscout, Medtronic).
 *
 * **UNIQUE date** : on impose un log unique par jour. Un nouvel upload pour
 * la même date écrase l'existant (UPSERT via `OnConflictStrategy.REPLACE` côté
 * DAO). C'est le bon contrat car la courbe CGM 24h est inhérente au jour.
 *
 * **Champs optionnels** : tout sauf [id], [date], [imagePath] est nullable.
 * Justification : l'OCR Gemini peut échouer à parser certains champs (image
 * floue, format inconnu), on dégrade gracieusement plutôt que de tout rejeter.
 * Le ViewModel proposera "Corriger manuellement" pour les champs vides.
 *
 * **manualOverride** : true si l'user a corrigé une valeur OCR — utile pour
 * la télémétrie de qualité OCR et pour ne pas re-parser à chaque save.
 *
 * **glucoseMgdlCurveJson** : sérialisation JSON `[{"t":"08:00","mgdl":118},...]`
 * de la courbe 24h si l'OCR a réussi à l'extraire. Stockée en JSON brut pour
 * simplicité — la parse côté domain via Gson quand utilisée pour graphes/correl.
 *
 * **parseConfidence** : 0..1, retournée par l'OCR. <0.7 = afficher un warning
 * "Vérifie les valeurs" à l'user.
 *
 * **Cycle de vie** : créé par GlucoseRepository.uploadScreenshot. Lu par les
 * contextes IA (UserContextBuilder, NotifContextEngine, Dr. Glykos tools).
 */
@Entity(
    tableName = "glucose_logs",
    indices = [Index(value = ["date"], unique = true)],
)
@Immutable
data class GlucoseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    /** Chemin absolu du screenshot dans `filesDir/glucose/`. */
    val imagePath: String? = null,

    // ─── Métriques parsées par l'OCR ─────────────────────────
    /** Glycémie moyenne journalière en mg/dL. Typique adulte sain : 90-110. */
    val avgMgdl: Double? = null,
    /** Pic maximum mg/dL sur 24h. >180 = postprandial spike notable. */
    val peakMgdl: Double? = null,
    val peakTime: LocalTime? = null,
    /** Minimum mg/dL sur 24h. <70 = hypoglycémie. */
    val minMgdl: Double? = null,
    val minTime: LocalTime? = null,
    /** % du temps dans la fourchette cible (par défaut 70-180 standard, 70-140 athlète). */
    val timeInRangePct: Int? = null,
    /** % temps au-dessus de la fourchette haute. */
    val timeAboveRangePct: Int? = null,
    /** % temps en dessous de la fourchette basse (hypo). */
    val timeBelowRangePct: Int? = null,
    /** Nombre d'épisodes hypoglycémiques (mg/dL < 70) sur la journée. */
    val hypoCount: Int? = null,
    /**
     * Coefficient de variation (%) = écart-type / moyenne × 100.
     * Référence : <36% = stabilité acceptable, >36% = variabilité élevée
     * (Battelino consensus 2019).
     */
    val cv: Double? = null,
    /**
     * Courbe 24h sérialisée JSON `[{"t":"HH:MM","mgdl":N},...]`. Échantillonnage
     * typique 15 min (96 points). Null si l'OCR n'a pas réussi à l'extraire
     * (cas fréquent : screenshot avec stats seulement, sans courbe visible).
     */
    val glucoseMgdlCurveJson: String? = null,

    // ─── Méta OCR ────────────────────────────────────────────
    /** Confiance OCR 0..1. <0.7 → afficher banner "Vérifie". */
    val parseConfidence: Float? = null,
    val parsedAt: LocalDateTime? = null,
    /** True si l'user a corrigé une valeur OCR manuellement. */
    val manualOverride: Boolean = false,
    val notes: String? = null,
)
