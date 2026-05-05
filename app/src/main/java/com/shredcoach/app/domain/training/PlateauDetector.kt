package com.shredcoach.app.domain.training

import com.shredcoach.app.data.local.dao.SetWithDate
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Analyse de la progression d'un exercice à partir de l'historique des sets.
 *
 * Pour CHAQUE séance ayant inclus l'exercice, on calcule un **best 1RM par séance**
 * (max sur tous les sets de la séance, via [OneRepMaxCalculator]). On obtient
 * une série temporelle (date → 1RM) qui sert de base à toute analyse :
 * - **Tendance** : régression linéaire simple (kg/semaine).
 * - **Plateau** : aucune amélioration significative sur les N dernières séances ET
 *   pente hebdomadaire faible.
 * - **PR** : meilleure 1RM tout-temps + delta avec le record précédent.
 *
 * Pourquoi pas un calcul intra-set : l'utilisateur peut faire 5 sets dans une
 * même séance avec progression intra-séance (warm-up → top set). Ce qui compte
 * c'est le **top set** de la séance, pas la moyenne. Sinon une grosse séance avec
 * beaucoup de warm-up paraîtrait moins bonne qu'une petite avec peu de warm-up.
 */
@Singleton
class PlateauDetector @Inject constructor(
    private val workoutLogDao: WorkoutLogDao,
) {
    /**
     * Calcule l'état de progression d'un exercice. Retourne null si pas assez
     * de données (< [MIN_SESSIONS] séances complétées avec charge).
     */
    suspend fun analyze(exerciseId: Long, today: LocalDate = LocalDate.now()): ExerciseProgression? {
        val sets = workoutLogDao.getWeightProgressionForExercise(exerciseId)
        val perSession = bestOneRmPerSession(sets)
        if (perSession.size < MIN_SESSIONS) return null

        val latest = perSession.last()
        val allTimeBest = perSession.maxBy { it.oneRm }
        val previousBest = perSession
            .filter { it.date.isBefore(allTimeBest.date) }
            .maxByOrNull { it.oneRm }

        val weeklySlopeKg = computeWeeklySlope(perSession)
        val recentWindow = perSession.takeLast(PLATEAU_WINDOW)
        val recentBest = recentWindow.maxOf { it.oneRm }
        val recentBestDate = recentWindow.first { it.oneRm == recentBest }.date
        val daysSinceRecentBest = ChronoUnit.DAYS.between(recentBestDate, today).toInt()

        // Plateau = pente quasi-nulle sur 4+ séances ET pas de nouveau best
        // récent (>= 21j). Empêche de crier "plateau" à chaque deload.
        val isPlateau = weeklySlopeKg.absoluteValue < PLATEAU_SLOPE_THRESHOLD &&
            daysSinceRecentBest >= PLATEAU_DAYS_WITHOUT_PR &&
            recentWindow.size >= PLATEAU_WINDOW

        // Nouveau PR : top all-time atteint dans les 14 derniers jours
        val daysSinceAllTimeBest = ChronoUnit.DAYS.between(allTimeBest.date, today).toInt()
        val hasFreshPr = daysSinceAllTimeBest <= PR_RECENT_WINDOW_DAYS &&
            (previousBest == null || allTimeBest.oneRm > previousBest.oneRm + PR_DELTA_THRESHOLD)

        val status = when {
            isPlateau -> ProgressStatus.Plateau(weeksFlat = (daysSinceRecentBest / 7).coerceAtLeast(3))
            weeklySlopeKg > PROGRESS_SLOPE_THRESHOLD -> ProgressStatus.Progressing(weeklySlopeKg)
            else -> ProgressStatus.Stable
        }

        return ExerciseProgression(
            exerciseId = exerciseId,
            sessionsCount = perSession.size,
            estimatedOneRmKg = OneRepMaxCalculator.roundToHalfKg(latest.oneRm),
            bestOneRmKg = OneRepMaxCalculator.roundToHalfKg(allTimeBest.oneRm),
            weeklySlopeKg = weeklySlopeKg,
            status = status,
            hasFreshPr = hasFreshPr,
            previousBestKg = previousBest?.let { OneRepMaxCalculator.roundToHalfKg(it.oneRm) },
            sparkline = perSession.takeLast(SPARKLINE_POINTS).map { it.oneRm },
        )
    }

    /**
     * Pour chaque séance (workoutLogId distinct), garde la meilleure estimation
     * 1RM produite par les sets de cette séance. Trié par date croissante.
     *
     * Group by **workoutLogId** (pas par date) : un utilisateur peut faire 2
     * séances le même jour (split push/pull/legs serré). Chaque séance compte
     * comme un point distinct dans la série temporelle. La date du point =
     * date du log de cette séance (premier set rencontré, tous partagent la
     * même date au sein d'un workoutLogId).
     */
    private fun bestOneRmPerSession(sets: List<SetWithDate>): List<SessionPoint> {
        if (sets.isEmpty()) return emptyList()
        return sets
            .mapNotNull { s ->
                val oneRm = OneRepMaxCalculator.estimate(s.weightKg, s.reps) ?: return@mapNotNull null
                Triple(s.workoutLogId, s.date.toLocalDate(), oneRm)
            }
            .groupBy { it.first }
            .map { (_, triplets) ->
                // Tous les triplets du même workoutLogId partagent la même date
                // (la query joint sur workout_logs.date), donc on peut prendre la
                // date du premier sans risque d'incohérence.
                SessionPoint(date = triplets.first().second, oneRm = triplets.maxOf { it.third })
            }
            .sortedBy { it.date }
    }

    /**
     * Régression linéaire simple sur (jours, 1RM). Retourne la pente convertie
     * en kg/semaine. Si une seule valeur ou variance nulle → 0.0.
     */
    private fun computeWeeklySlope(points: List<SessionPoint>): Double {
        if (points.size < 2) return 0.0
        val refDay = points.first().date.toEpochDay()
        val xs = points.map { (it.date.toEpochDay() - refDay).toDouble() }
        val ys = points.map { it.oneRm }
        val xMean = xs.average()
        val yMean = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - xMean) * (ys[i] - yMean)
            den += (xs[i] - xMean) * (xs[i] - xMean)
        }
        if (den == 0.0) return 0.0
        return (num / den) * 7.0
    }

    private data class SessionPoint(val date: LocalDate, val oneRm: Double)

    private companion object {
        /** Nombre minimum de séances pour qu'une analyse ait du sens. */
        const val MIN_SESSIONS = 3
        /** Fenêtre récente (en séances) pour évaluer le plateau. */
        const val PLATEAU_WINDOW = 4
        /** Pente hebdo en dessous de laquelle on considère "plat". */
        const val PLATEAU_SLOPE_THRESHOLD = 0.4
        /** Pente hebdo au-dessus de laquelle on considère "en progression". */
        const val PROGRESS_SLOPE_THRESHOLD = 0.6
        /** Jours sans nouveau best-1RM nécessaires pour parler de plateau. */
        const val PLATEAU_DAYS_WITHOUT_PR = 21
        /** Fenêtre récente pour détecter un PR fraîchement battu. */
        const val PR_RECENT_WINDOW_DAYS = 14
        /** Delta minimum pour considérer un nouveau PR vs l'ancien. */
        const val PR_DELTA_THRESHOLD = 1.0  // kg
        /** Nombre de points à exposer pour la sparkline UI. */
        const val SPARKLINE_POINTS = 12
    }
}

/**
 * Synthèse du suivi pour un exercice — produit unique du [PlateauDetector].
 * Tout est UI-ready (déjà arrondi) sauf [weeklySlopeKg] qui sert au tri/affichage
 * conditionnel et n'a pas vocation à être affiché tel quel.
 */
data class ExerciseProgression(
    val exerciseId: Long,
    val sessionsCount: Int,
    val estimatedOneRmKg: Double,
    val bestOneRmKg: Double,
    val weeklySlopeKg: Double,
    val status: ProgressStatus,
    val hasFreshPr: Boolean,
    val previousBestKg: Double?,
    /** Série de 1RM (kg) sur les N dernières séances pour mini-graph. */
    val sparkline: List<Double>,
)

/** État qualitatif de la progression sur cet exercice. */
sealed interface ProgressStatus {
    data class Progressing(val weeklyDeltaKg: Double) : ProgressStatus
    data object Stable : ProgressStatus
    data class Plateau(val weeksFlat: Int) : ProgressStatus
}
