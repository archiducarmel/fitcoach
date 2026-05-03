package com.shredcoach.app.domain.calendar

import java.time.LocalDate

/**
 * Jours fériés français (métropole).
 * Calcul dynamique pour toute année via l'algorithme de Gauss (Pâques).
 */
object FrenchHolidays {

    data class Holiday(val date: LocalDate, val name: String)

    /** Retourne tous les jours fériés d'une année (triés par date). */
    fun forYear(year: Int): List<Holiday> {
        val easter = easterSunday(year)
        return listOf(
            Holiday(LocalDate.of(year, 1, 1), "Jour de l'An"),
            Holiday(easter.plusDays(1), "Lundi de Pâques"),
            Holiday(LocalDate.of(year, 5, 1), "Fête du Travail"),
            Holiday(LocalDate.of(year, 5, 8), "Victoire 1945"),
            Holiday(easter.plusDays(39), "Ascension"),
            Holiday(easter.plusDays(50), "Lundi de Pentecôte"),
            Holiday(LocalDate.of(year, 7, 14), "Fête Nationale"),
            Holiday(LocalDate.of(year, 8, 15), "Assomption"),
            Holiday(LocalDate.of(year, 11, 1), "Toussaint"),
            Holiday(LocalDate.of(year, 11, 11), "Armistice 1918"),
            Holiday(LocalDate.of(year, 12, 25), "Noël")
        ).sortedBy { it.date }
    }

    /** Retourne les jours fériés tombant dans une plage (ex: un mois). */
    fun inRange(start: LocalDate, end: LocalDate): List<Holiday> {
        val years = (start.year..end.year).toList()
        return years.flatMap { forYear(it) }
            .filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    }

    /** Map pour accès O(1). */
    fun mapForRange(start: LocalDate, end: LocalDate): Map<LocalDate, String> =
        inRange(start, end).associate { it.date to it.name }

    /** Algorithme de Gauss (Butcher) pour calculer le dimanche de Pâques grégorien. */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}
