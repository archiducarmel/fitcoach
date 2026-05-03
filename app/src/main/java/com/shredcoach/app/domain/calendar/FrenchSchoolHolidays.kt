package com.shredcoach.app.domain.calendar

import java.time.LocalDate

/**
 * Vacances scolaires françaises par zone A/B/C (métropole).
 *
 * Données officielles rentrée 2025-2026 + 2026-2027 (source data.gouv.fr).
 * Pour les années suivantes, étendre manuellement.
 *
 * Zones :
 *   A = Besançon, Bordeaux, Clermont-Ferrand, Dijon, Grenoble, Limoges, Lyon, Poitiers
 *   B = Aix-Marseille, Amiens, Caen, Lille, Nancy-Metz, Nantes, Nice, Orléans-Tours, Reims, Rennes, Rouen, Strasbourg
 *   C = Créteil, Montpellier, Paris, Toulouse, Versailles
 */
object FrenchSchoolHolidays {

    enum class Zone { A, B, C }

    data class HolidayPeriod(
        val name: String,
        val start: LocalDate,
        val end: LocalDate, // inclusif
        val zone: Zone? = null // null = toutes zones
    )

    /** Périodes de vacances connues (dates inclusives). Sources: data.gouv.fr. */
    private val PERIODS: List<HolidayPeriod> = listOf(
        // ─── Année scolaire 2025-2026 ───
        HolidayPeriod("Toussaint 2025", LocalDate.of(2025, 10, 18), LocalDate.of(2025, 11, 2)),
        HolidayPeriod("Noël 2025",       LocalDate.of(2025, 12, 20), LocalDate.of(2026, 1, 4)),
        // Hiver : dates différentes par zone
        HolidayPeriod("Hiver 2026",      LocalDate.of(2026, 2, 7),   LocalDate.of(2026, 2, 22), Zone.B),
        HolidayPeriod("Hiver 2026",      LocalDate.of(2026, 2, 14),  LocalDate.of(2026, 3, 1),  Zone.A),
        HolidayPeriod("Hiver 2026",      LocalDate.of(2026, 2, 21),  LocalDate.of(2026, 3, 8),  Zone.C),
        // Printemps
        HolidayPeriod("Printemps 2026",  LocalDate.of(2026, 4, 4),   LocalDate.of(2026, 4, 19), Zone.B),
        HolidayPeriod("Printemps 2026",  LocalDate.of(2026, 4, 11),  LocalDate.of(2026, 4, 26), Zone.A),
        HolidayPeriod("Printemps 2026",  LocalDate.of(2026, 4, 18),  LocalDate.of(2026, 5, 3),  Zone.C),
        // Été (fin d'année scolaire)
        HolidayPeriod("Été 2026",        LocalDate.of(2026, 7, 4),   LocalDate.of(2026, 8, 31)),

        // ─── Année scolaire 2026-2027 ───
        HolidayPeriod("Toussaint 2026", LocalDate.of(2026, 10, 17),  LocalDate.of(2026, 11, 1)),
        HolidayPeriod("Noël 2026",       LocalDate.of(2026, 12, 19), LocalDate.of(2027, 1, 3)),
        HolidayPeriod("Hiver 2027",      LocalDate.of(2027, 2, 6),   LocalDate.of(2027, 2, 21), Zone.B),
        HolidayPeriod("Hiver 2027",      LocalDate.of(2027, 2, 13),  LocalDate.of(2027, 2, 28), Zone.A),
        HolidayPeriod("Hiver 2027",      LocalDate.of(2027, 2, 20),  LocalDate.of(2027, 3, 7),  Zone.C),
        HolidayPeriod("Printemps 2027",  LocalDate.of(2027, 4, 3),   LocalDate.of(2027, 4, 18), Zone.B),
        HolidayPeriod("Printemps 2027",  LocalDate.of(2027, 4, 10),  LocalDate.of(2027, 4, 25), Zone.A),
        HolidayPeriod("Printemps 2027",  LocalDate.of(2027, 4, 17),  LocalDate.of(2027, 5, 2),  Zone.C),
        HolidayPeriod("Été 2027",        LocalDate.of(2027, 7, 3),   LocalDate.of(2027, 8, 31))
    )

    /** Retourne les périodes de vacances chevauchant la plage donnée, pour une zone. */
    fun inRange(start: LocalDate, end: LocalDate, zone: Zone = Zone.C): List<HolidayPeriod> {
        return PERIODS.filter { period ->
            (period.zone == null || period.zone == zone) &&
            period.start <= end && period.end >= start
        }
    }

    /** Map pour accès O(1) sur chaque jour de vacances (zone C par défaut = région Paris). */
    fun daysInRange(start: LocalDate, end: LocalDate, zone: Zone = Zone.C): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        for (period in inRange(start, end, zone)) {
            var d = maxOf(period.start, start)
            val last = minOf(period.end, end)
            while (!d.isAfter(last)) {
                result.add(d)
                d = d.plusDays(1)
            }
        }
        return result
    }
}
