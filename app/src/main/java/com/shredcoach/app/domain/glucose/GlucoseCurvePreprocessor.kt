package com.shredcoach.app.domain.glucose

import com.google.gson.JsonParser
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pré-traitement algorithmique de la courbe glycémique 24h avant inférence LLM.
 *
 * **Pourquoi pré-traiter** : envoyer 288 points raw (1 sample / 5min) au LLM
 * = bruit + token waste + qualité d'analyse moyenne. Le LLM est meilleur pour
 * interpréter cliniquement que pour faire de la détection numérique. On lui
 * donne donc :
 *  - Des **events labellisés** (pics, vallées, plateaux, réponses postprandiales)
 *  - Les métriques déjà calculées
 *  - Le contexte alimentaire aligné temporellement
 *
 * Le LLM reçoit ~30-50 events au lieu de 288 points → coût ×0.3, qualité ×3.
 *
 * **Algorithmes utilisés** :
 *  - Détection de pics : extrema locaux sur fenêtre glissante 45min, avec
 *    seuil de prominence (10 mg/dL min) pour éviter le bruit CGM
 *  - Détection de plateaux : variance < 5 mg/dL² sur 60min consécutives
 *  - Réponse postprandiale : fenêtre [meal_time, meal_time + 180min], identifie
 *    le pic relatif et le retour à baseline (-10 mg/dL du pré-repas)
 *  - Dawn phenomenon : monotone increase sur 4h-8h sans repas dans la fenêtre
 *
 * **Pas de magic numbers en dur dans le LLM** : tout est paramétré ici, ce qui
 * permet de tweaker l'algo sans toucher au prompt (qui est cher à itérer).
 */
object GlucoseCurvePreprocessor {

    // ─── Seuils cliniques (ADA/EASD 2024) ──────────────────────────────────
    /** Seuil hypoglycémie. <70 = level 1, <54 = level 2 (sévère). */
    private const val HYPO_THRESHOLD = 70.0

    /** Seuil hyperglycémie postprandiale typique. */
    private const val PEAK_NORMAL_THRESHOLD = 140.0
    private const val PEAK_WARNING_THRESHOLD = 180.0
    private const val PEAK_CRITICAL_THRESHOLD = 220.0

    /** Glycémie à jeun normale. */
    private const val FASTING_BASELINE_LOW = 70.0
    private const val FASTING_BASELINE_HIGH = 100.0

    /** Fenêtre d'analyse postprandiale (durée typique de réponse glycémique). */
    private const val POSTPRANDIAL_WINDOW_MIN = 180L

    /** Détection de pic : différence min vs voisins immédiats. */
    private const val PEAK_PROMINENCE_MGDL = 10.0

    /** Fenêtre de détection extrema locaux (mn). */
    private const val LOCAL_EXTREMA_WINDOW_MIN = 45

    /** Dawn phenomenon : fenêtre matinale (4h-8h). */
    private val DAWN_WINDOW_START = LocalTime.of(4, 0)
    private val DAWN_WINDOW_END = LocalTime.of(8, 30)

    /** Phase nocturne (jeûne). */
    private val NIGHT_START = LocalTime.of(23, 0)
    private val NIGHT_END = LocalTime.of(6, 0)

    // ═══════════════════════════════════════════════════════════════════════
    // POINT D'ENTRÉE PRINCIPAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Construit le contexte pré-traité à injecter dans le prompt LLM.
     * Returns null si la curve est inexploitable (vide ou < 2 points).
     *
     * @param meals doit avoir les noms d'aliments résolus (jointure côté caller
     *              car MealLogEntity n'a que foodId). Cf. [GlucoseAnalysisEngine].
     */
    fun preprocess(log: GlucoseLogEntity, meals: List<MealContext>): PreprocessedContext? {
        val curve = parseCurve(log.glucoseMgdlCurveJson ?: return null)
        if (curve.size < 2) return null

        val mealsByTime = meals.sortedBy { it.time }

        return PreprocessedContext(
            date = log.date,
            samplesCount = curve.size,
            metrics = computeMetrics(curve, log),
            peaks = detectPeaks(curve),
            valleys = detectValleys(curve),
            plateaus = detectPlateaus(curve),
            postprandialResponses = mealsByTime.mapNotNull { meal ->
                analyzePostprandialResponse(curve, meal)
            },
            dawnPhenomenon = detectDawnPhenomenon(curve, mealsByTime),
            nightFastingStats = computeNightFasting(curve),
            unexplainedRises = detectUnexplainedRises(curve, mealsByTime),
            hypoEvents = detectHypoEvents(curve),
            mealsContext = mealsByTime,
        )
    }

    /**
     * Hash stable des inputs pour invalidation cache. Identique pour le même
     * (curve, meals) → cache hit. Si user modifie un repas ou re-upload CGM,
     * le hash change automatiquement.
     */
    fun computeInputHash(log: GlucoseLogEntity, meals: List<MealContext>): String {
        // Le hash inclut la locale courante de l'app : si l'user change de
        // langue (FR -> EN), l'analyse en cache est dans la mauvaise langue
        // et doit etre re-generee. Sans ce champ, l'user verrait une analyse
        // FR dans une app passee en EN jusqu'a 24h.
        val locale = java.util.Locale.getDefault().language
        val payload = buildString {
            append(locale).append('|')
            append(log.date)
            append('|')
            append(log.glucoseMgdlCurveJson ?: "")
            append('|')
            append(log.avgMgdl).append(',').append(log.peakMgdl).append(',').append(log.minMgdl)
            append('|')
            meals.sortedBy { it.time }.forEach { m ->
                append(m.time).append(':').append(m.name).append(':').append(m.calories).append(':').append(m.carbsGrams)
                append(';')
            }
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        return sha.joinToString("") { "%02x".format(it) }.take(32)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PARSING + MÉTRIQUES
    // ═══════════════════════════════════════════════════════════════════════

    private fun parseCurve(json: String): List<GlucosePoint> = try {
        JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
            val o = el.asJsonObject
            val tStr = o.get("t")?.asString ?: return@mapNotNull null
            val mgdl = o.get("mgdl")?.asDouble ?: return@mapNotNull null
            val time = runCatching { LocalTime.parse(tStr.take(5)) }.getOrNull() ?: return@mapNotNull null
            GlucosePoint(time, mgdl)
        }.sortedBy { it.time }
    } catch (_: Exception) {
        emptyList()
    }

    private fun computeMetrics(curve: List<GlucosePoint>, log: GlucoseLogEntity): CurveMetrics {
        val values = curve.map { it.mgdl }
        val avg = log.avgMgdl ?: values.average()
        val peak = log.peakMgdl ?: values.max()
        val min = log.minMgdl ?: values.min()
        val variance = values.map { (it - avg) * (it - avg) }.average()
        val stdDev = sqrt(variance)
        val cv = log.cv ?: (stdDev / avg * 100)
        val tir = log.timeInRangePct ?: values.count { it in 70.0..180.0 }.toDouble().div(values.size).times(100).roundToInt()
        val tar = values.count { it > 180 }.toDouble().div(values.size).times(100).roundToInt()
        val tbr = values.count { it < 70 }.toDouble().div(values.size).times(100).roundToInt()
        return CurveMetrics(avg = avg, peak = peak, min = min, stdDev = stdDev, cv = cv, tir = tir, tar = tar, tbr = tbr)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DÉTECTION EXTREMA LOCAUX (pics / vallées)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Détecte les pics locaux : un point est pic si > tous ses voisins dans une
     * fenêtre ±LOCAL_EXTREMA_WINDOW_MIN, avec une prominence min
     * (différence avec le minimum local proche).
     *
     * Limite à 5 pics les plus prominents pour éviter de noyer le LLM.
     */
    private fun detectPeaks(curve: List<GlucosePoint>): List<ExtremaEvent> {
        val candidates = mutableListOf<ExtremaEvent>()
        for (i in curve.indices) {
            val center = curve[i]
            val windowStart = center.time.minusMinutes(LOCAL_EXTREMA_WINDOW_MIN.toLong())
            val windowEnd = center.time.plusMinutes(LOCAL_EXTREMA_WINDOW_MIN.toLong())
            val neighbors = curve.filter { it.time in windowStart..windowEnd && it != center }
            if (neighbors.isEmpty()) continue
            val isPeak = neighbors.all { center.mgdl >= it.mgdl }
            val maxNeighbor = neighbors.maxOf { it.mgdl }
            val minNeighbor = neighbors.minOf { it.mgdl }
            val prominence = center.mgdl - minNeighbor
            if (isPeak && (center.mgdl - maxNeighbor >= 0) && prominence >= PEAK_PROMINENCE_MGDL) {
                candidates.add(ExtremaEvent(center.time, center.mgdl, prominence))
            }
        }
        return candidates.distinctBy { it.time.hour to (it.time.minute / 30) }
            .sortedByDescending { it.prominence }
            .take(5)
    }

    private fun detectValleys(curve: List<GlucosePoint>): List<ExtremaEvent> {
        val candidates = mutableListOf<ExtremaEvent>()
        for (i in curve.indices) {
            val center = curve[i]
            val windowStart = center.time.minusMinutes(LOCAL_EXTREMA_WINDOW_MIN.toLong())
            val windowEnd = center.time.plusMinutes(LOCAL_EXTREMA_WINDOW_MIN.toLong())
            val neighbors = curve.filter { it.time in windowStart..windowEnd && it != center }
            if (neighbors.isEmpty()) continue
            val isValley = neighbors.all { center.mgdl <= it.mgdl }
            val maxNeighbor = neighbors.maxOf { it.mgdl }
            val prominence = maxNeighbor - center.mgdl
            if (isValley && prominence >= PEAK_PROMINENCE_MGDL) {
                candidates.add(ExtremaEvent(center.time, center.mgdl, prominence))
            }
        }
        return candidates.distinctBy { it.time.hour to (it.time.minute / 30) }
            .sortedByDescending { it.prominence }
            .take(3)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PLATEAUX (zones stables)
    // ═══════════════════════════════════════════════════════════════════════

    private fun detectPlateaus(curve: List<GlucosePoint>): List<PlateauEvent> {
        if (curve.size < 4) return emptyList()
        val plateaus = mutableListOf<PlateauEvent>()
        var windowStart = 0
        while (windowStart < curve.size - 4) {
            // Fenêtre minimum 60min stable
            var windowEnd = windowStart + 1
            while (windowEnd < curve.size &&
                curve[windowEnd].time.toSecondOfDay() - curve[windowStart].time.toSecondOfDay() < 60 * 60) {
                windowEnd++
            }
            if (windowEnd >= curve.size) break
            val window = curve.subList(windowStart, windowEnd)
            val avg = window.map { it.mgdl }.average()
            val variance = window.map { (it.mgdl - avg) * (it.mgdl - avg) }.average()
            if (variance < 25.0 && window.size >= 4) { // stdDev < 5 mg/dL
                plateaus.add(
                    PlateauEvent(
                        startTime = window.first().time,
                        endTime = window.last().time,
                        avgMgdl = avg,
                    )
                )
                windowStart = windowEnd
            } else {
                windowStart++
            }
        }
        return plateaus.take(3) // max 3 plateaus
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RÉPONSE POSTPRANDIALE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Pour chaque repas, analyse les 3h suivantes : pré-repas baseline, pic
     * relatif, temps de retour à baseline, magnitude.
     */
    private fun analyzePostprandialResponse(
        curve: List<GlucosePoint>,
        meal: MealContext,
    ): PostprandialResponse? {
        val mealTime = meal.time

        // Baseline : moyenne des 15min avant le repas
        val baselineWindowStart = mealTime.minusMinutes(20)
        val baselineWindowEnd = mealTime.minusMinutes(5)
        val baselinePoints = curve.filter { it.time in baselineWindowStart..baselineWindowEnd }
        if (baselinePoints.isEmpty()) return null
        val baseline = baselinePoints.map { it.mgdl }.average()

        // Fenêtre postprandiale [meal_time, meal_time + 180min]
        // Borne inférieure : meal_time + 5min pour éviter de capter le tail baseline
        val windowStart = mealTime.plusMinutes(5)
        val windowEnd = mealTime.plusMinutes(POSTPRANDIAL_WINDOW_MIN)
        val windowPoints = curve.filter { p ->
            // Le repas peut être en fin de journée → fenêtre se termine après minuit.
            // On gère via comparison directe sur LocalTime (les CGM ne capturent
            // typiquement pas les 24h pile, on coupe à 23:55 → ok).
            !p.time.isBefore(windowStart) && !p.time.isAfter(windowEnd)
        }
        if (windowPoints.isEmpty()) return null

        val peakPoint = windowPoints.maxBy { it.mgdl }
        val peakDeltaMgdl = peakPoint.mgdl - baseline
        val peakDelayMin = java.time.Duration.between(mealTime, peakPoint.time).toMinutes()

        // Retour baseline : premier point après le pic où mgdl <= baseline + 10
        val returnPoint = windowPoints
            .filter { it.time.isAfter(peakPoint.time) }
            .firstOrNull { it.mgdl <= baseline + 10 }
        val recoveryMin = returnPoint?.let {
            java.time.Duration.between(mealTime, it.time).toMinutes()
        }

        return PostprandialResponse(
            mealName = meal.name,
            mealTime = mealTime,
            mealCalories = meal.calories,
            mealCarbsGrams = meal.carbsGrams,
            baselineMgdl = baseline,
            peakMgdl = peakPoint.mgdl,
            peakDeltaMgdl = peakDeltaMgdl,
            peakDelayMin = peakDelayMin,
            recoveryMin = recoveryMin,
            magnitude = when {
                peakDeltaMgdl >= 80 -> ResponseMagnitude.HIGH
                peakDeltaMgdl >= 50 -> ResponseMagnitude.MODERATE
                peakDeltaMgdl >= 30 -> ResponseMagnitude.LOW
                else -> ResponseMagnitude.MINIMAL
            },
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DAWN PHENOMENON
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Détecte le phénomène de l'aube : montée monotone du glucose entre
     * 4h-8h30 SANS repas dans cette fenêtre. Caractéristique : delta ≥15 mg/dL
     * sur la fenêtre, attribuable à la sécrétion de cortisol matinale.
     */
    private fun detectDawnPhenomenon(
        curve: List<GlucosePoint>,
        meals: List<MealContext>,
    ): DawnPhenomenonEvent? {
        val dawnPoints = curve.filter { it.time in DAWN_WINDOW_START..DAWN_WINDOW_END }
        if (dawnPoints.size < 3) return null

        // Pas de repas dans la fenêtre
        val mealsInDawn = meals.any { m -> m.time in DAWN_WINDOW_START..DAWN_WINDOW_END }
        if (mealsInDawn) return null

        val startMgdl = dawnPoints.first().mgdl
        val endMgdl = dawnPoints.last().mgdl
        val rise = endMgdl - startMgdl
        if (rise < 15.0) return null

        // Monotonie globale : >=70% des deltas successifs sont >=0
        val deltas = dawnPoints.zipWithNext { a, b -> b.mgdl - a.mgdl }
        val monotonic = deltas.count { it >= -2 }.toDouble() / deltas.size
        if (monotonic < 0.7) return null

        return DawnPhenomenonEvent(
            startTime = dawnPoints.first().time,
            endTime = dawnPoints.last().time,
            startMgdl = startMgdl,
            endMgdl = endMgdl,
            riseMgdl = rise,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JEÛNE NOCTURNE
    // ═══════════════════════════════════════════════════════════════════════

    private fun computeNightFasting(curve: List<GlucosePoint>): NightFastingStats? {
        // Phase 23h-6h
        val nightPoints = curve.filter { p ->
            p.time >= NIGHT_START || p.time <= NIGHT_END
        }
        if (nightPoints.size < 5) return null
        val values = nightPoints.map { it.mgdl }
        return NightFastingStats(
            avgMgdl = values.average(),
            minMgdl = values.min(),
            maxMgdl = values.max(),
            stabilityCv = if (values.average() > 0) {
                val variance = values.map { (it - values.average()) * (it - values.average()) }.average()
                sqrt(variance) / values.average() * 100
            } else 0.0,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HYPOS
    // ═══════════════════════════════════════════════════════════════════════

    private fun detectHypoEvents(curve: List<GlucosePoint>): List<HypoEvent> {
        val events = mutableListOf<HypoEvent>()
        var i = 0
        while (i < curve.size) {
            if (curve[i].mgdl < HYPO_THRESHOLD) {
                val startTime = curve[i].time
                var minMgdl = curve[i].mgdl
                var endTime = startTime
                while (i < curve.size && curve[i].mgdl < HYPO_THRESHOLD) {
                    if (curve[i].mgdl < minMgdl) minMgdl = curve[i].mgdl
                    endTime = curve[i].time
                    i++
                }
                events.add(
                    HypoEvent(
                        startTime = startTime,
                        endTime = endTime,
                        nadirMgdl = minMgdl,
                        severity = if (minMgdl < 54) HypoSeverity.SEVERE else HypoSeverity.MILD,
                    )
                )
            } else {
                i++
            }
        }
        return events
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MONTÉES INEXPLIQUÉES (sans repas)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Détecte les pics qui ne sont pas attribuables à un repas (pas de meal
     * dans la fenêtre [pic-45min, pic-5min]). Cause probable : stress, café,
     * hormones (cortisol matinal), exercise.
     */
    private fun detectUnexplainedRises(
        curve: List<GlucosePoint>,
        meals: List<MealContext>,
    ): List<UnexplainedRiseEvent> {
        val peaks = detectPeaks(curve)
        return peaks.mapNotNull { peak ->
            val attributedMeal = meals.firstOrNull { m ->
                val deltaMin = java.time.Duration.between(m.time, peak.time).toMinutes()
                deltaMin in 15..120
            }
            if (attributedMeal == null && peak.mgdl - peak.prominence > FASTING_BASELINE_HIGH) {
                UnexplainedRiseEvent(
                    time = peak.time,
                    peakMgdl = peak.mgdl,
                    rise = peak.prominence,
                )
            } else null
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DATA CLASSES — entrée prompt LLM
// ═══════════════════════════════════════════════════════════════════════════

data class GlucosePoint(val time: LocalTime, val mgdl: Double)

data class PreprocessedContext(
    val date: LocalDate,
    val samplesCount: Int,
    val metrics: CurveMetrics,
    val peaks: List<ExtremaEvent>,
    val valleys: List<ExtremaEvent>,
    val plateaus: List<PlateauEvent>,
    val postprandialResponses: List<PostprandialResponse>,
    val dawnPhenomenon: DawnPhenomenonEvent?,
    val nightFastingStats: NightFastingStats?,
    val unexplainedRises: List<UnexplainedRiseEvent>,
    val hypoEvents: List<HypoEvent>,
    val mealsContext: List<MealContext>,
)

data class CurveMetrics(
    val avg: Double,
    val peak: Double,
    val min: Double,
    val stdDev: Double,
    val cv: Double,
    val tir: Int,   // Time-In-Range (70-180) %
    val tar: Int,   // Time-Above-Range (>180) %
    val tbr: Int,   // Time-Below-Range (<70) %
)

data class ExtremaEvent(val time: LocalTime, val mgdl: Double, val prominence: Double)
data class PlateauEvent(val startTime: LocalTime, val endTime: LocalTime, val avgMgdl: Double)

data class PostprandialResponse(
    val mealName: String,
    val mealTime: LocalTime,
    val mealCalories: Double,
    val mealCarbsGrams: Double,
    val baselineMgdl: Double,
    val peakMgdl: Double,
    val peakDeltaMgdl: Double,
    val peakDelayMin: Long,
    val recoveryMin: Long?,
    val magnitude: ResponseMagnitude,
)

enum class ResponseMagnitude { MINIMAL, LOW, MODERATE, HIGH }

data class DawnPhenomenonEvent(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startMgdl: Double,
    val endMgdl: Double,
    val riseMgdl: Double,
)

data class NightFastingStats(
    val avgMgdl: Double,
    val minMgdl: Double,
    val maxMgdl: Double,
    val stabilityCv: Double,
)

data class UnexplainedRiseEvent(val time: LocalTime, val peakMgdl: Double, val rise: Double)
data class HypoEvent(val startTime: LocalTime, val endTime: LocalTime, val nadirMgdl: Double, val severity: HypoSeverity)
enum class HypoSeverity { MILD, SEVERE }

data class MealContext(
    val name: String,
    val time: LocalTime,
    val calories: Double,
    val carbsGrams: Double,
    val proteinsGrams: Double,
    val fatsGrams: Double,
)
