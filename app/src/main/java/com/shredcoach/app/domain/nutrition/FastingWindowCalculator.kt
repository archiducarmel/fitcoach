package com.shredcoach.app.domain.nutrition

import androidx.compose.runtime.Immutable
import com.shredcoach.app.data.local.entity.MealLogEntity
import java.time.LocalDate
import java.time.LocalTime

/**
 * Calcul de la fenêtre de jeûne nocturne (intermittent fasting 16-8, 14-10…).
 *
 * Définition retenue : pour chaque journée D, on mesure la durée écoulée
 * entre le DERNIER repas pris à J-1 et le PREMIER repas pris à J. C'est
 * la fenêtre dite "nocturne" qui correspond au jeûne suivi par les
 * pratiquants du 16-8 (sauter le petit-déj, dîner tôt).
 *
 * Pourquoi cette définition (et pas le plus long gap inter-repas dans la
 * journée) : un user qui mange à 7h, 12h, 19h n'est pas en jeûne 12h
 * intermittent même si son gap 12h-19h fait 7h. Ce qui compte pour le
 * 16-8 c'est la traversée nocturne. Si l'user veut un autre style de
 * fasting (OMAD, 23-1), on adaptera.
 *
 * Cas limites :
 *  - Aucun repas hier ou aujourd'hui → on ne compte pas (null) plutôt
 *    que de remonter "24h" trompeur.
 *  - Repas après minuit (ex: 1h du matin) → traité comme appartenant à
 *    la journée précédente côté calcul (car `MealLogEntity.time` est
 *    son heure indépendamment de la date métier).
 */
object FastingWindowCalculator {

    /**
     * Calcule la fenêtre nocturne entre les repas de [yesterday] et [today].
     * Retourne null si l'une des deux journées n'a aucun repas tracké.
     */
    fun nightFastingHours(
        yesterday: List<MealLogEntity>,
        today: List<MealLogEntity>
    ): Double? {
        val lastYesterday = yesterday.mapNotNull { it.time }.maxOrNull() ?: return null
        val firstToday = today.mapNotNull { it.time }.minOrNull() ?: return null
        return diffWrappingMidnight(lastYesterday, firstToday)
    }

    /**
     * Heures écoulées entre [from] et [to] en traversant minuit.
     * `from = 22:30`, `to = 07:30` → 9.0
     */
    private fun diffWrappingMidnight(from: LocalTime, to: LocalTime): Double {
        val fromHours = from.hour + from.minute / 60.0
        val toHours = to.hour + to.minute / 60.0
        val diff = (24.0 - fromHours) + toHours
        return diff.coerceIn(0.0, 24.0)
    }

    /**
     * Agrège les fenêtres de jeûne sur la période start..end (inclusive).
     * Pour chaque jour, on lit ses repas et ceux de la veille via [fetcher].
     */
    suspend fun aggregate(
        start: LocalDate,
        end: LocalDate,
        fetcher: suspend (LocalDate) -> List<MealLogEntity>
    ): FastingStats {
        val series = mutableListOf<Pair<LocalDate, Double>>()
        var d = start
        // On lit la veille de [start] aussi pour pouvoir mesurer le 1er jour.
        var yesterdayMeals = fetcher(start.minusDays(1))
        while (!d.isAfter(end)) {
            val todayMeals = fetcher(d)
            val hours = nightFastingHours(yesterdayMeals, todayMeals)
            if (hours != null) series += d to hours
            yesterdayMeals = todayMeals
            d = d.plusDays(1)
        }

        if (series.isEmpty()) {
            return FastingStats(
                averageHours = 0.0,
                bestHours = 0.0,
                daysWith16h = 0,
                daysWith14h = 0,
                daysMeasured = 0,
                series = emptyList(),
                averageEatingStartHour = null,
                averageEatingEndHour = null,
            )
        }

        val avg = series.sumOf { it.second } / series.size
        val best = series.maxOf { it.second }
        val with16 = series.count { it.second >= 16.0 }
        val with14 = series.count { it.second >= 14.0 }

        // Heures moyennes de début et fin de fenêtre alimentaire (pour le cadran 24h)
        val (avgStart, avgEnd) = computeAverageEatingWindow(start, end, fetcher)

        return FastingStats(
            averageHours = avg,
            bestHours = best,
            daysWith16h = with16,
            daysWith14h = with14,
            daysMeasured = series.size,
            series = series,
            averageEatingStartHour = avgStart,
            averageEatingEndHour = avgEnd,
        )
    }

    /**
     * Heures moyennes du premier et dernier repas dans la fenêtre, en heures
     * décimales (ex: 12.5 = 12h30). Sert au cadran 24h pour positionner
     * l'arc "fenêtre alimentaire" au bon endroit du cercle.
     */
    private suspend fun computeAverageEatingWindow(
        start: LocalDate,
        end: LocalDate,
        fetcher: suspend (LocalDate) -> List<MealLogEntity>
    ): Pair<Double?, Double?> {
        val firsts = mutableListOf<Double>()
        val lasts = mutableListOf<Double>()
        var d = start
        while (!d.isAfter(end)) {
            val meals = fetcher(d).mapNotNull { it.time }
            if (meals.isNotEmpty()) {
                firsts += meals.min().toDecimalHours()
                lasts += meals.max().toDecimalHours()
            }
            d = d.plusDays(1)
        }
        val avgFirst = if (firsts.isEmpty()) null else firsts.average()
        val avgLast = if (lasts.isEmpty()) null else lasts.average()
        return avgFirst to avgLast
    }

    private fun LocalTime.toDecimalHours(): Double = hour + minute / 60.0
}

@Immutable
data class FastingStats(
    /** Moyenne des heures de jeûne nocturne sur la période. */
    val averageHours: Double,
    /** Meilleure journée (jeûne le plus long). */
    val bestHours: Double,
    /** Nb de jours où le jeûne ≥ 16h (format 16-8). */
    val daysWith16h: Int,
    /** Nb de jours où le jeûne ≥ 14h (format 14-10). */
    val daysWith14h: Int,
    /** Nb de jours réellement mesurés (avec données J-1 + J). */
    val daysMeasured: Int,
    /** Série quotidienne (date, heures de jeûne) — pour graphes éventuels. */
    val series: List<Pair<LocalDate, Double>>,
    /** Heure décimale moyenne du PREMIER repas (ex: 12.5 = 12h30). */
    val averageEatingStartHour: Double?,
    /** Heure décimale moyenne du DERNIER repas (ex: 20.0 = 20h00). */
    val averageEatingEndHour: Double?,
) {
    val isEmpty: Boolean get() = daysMeasured == 0

    /**
     * Verdict qualitatif basé sur la moyenne :
     *  - ≥ 16h : format 16-8 atteint en moyenne
     *  - 14-16h : jeûne nocturne respecté
     *  - 12-14h : jeûne court, marge de progression
     *  - < 12h : fenêtre alimentaire trop large
     */
    val verdictText: String
        get() = when {
            isEmpty -> ""
            averageHours >= 16.0 -> "Format 16-8 atteint en moyenne"
            averageHours >= 14.0 -> "Jeûne nocturne bien respecté"
            averageHours >= 12.0 -> "Jeûne court — vise 14h+ pour les bénéfices métaboliques"
            else -> "Fenêtre alimentaire trop large — module les heures de repas"
        }
}
