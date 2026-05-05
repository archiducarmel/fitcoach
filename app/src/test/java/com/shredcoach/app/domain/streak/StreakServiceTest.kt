package com.shredcoach.app.domain.streak

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests pour le calcul de streak — règles de continuité, edge cases (séance d'aujourd'hui
 * pas encore faite, gap, séance dans le futur, historique vide).
 */
class StreakServiceTest {

    private val service = StreakService()
    private val today: LocalDate = LocalDate.of(2026, 5, 15) // jeudi (jour fixe pour reproductibilité)

    @Test
    fun `aucun log retourne streak 0`() {
        val state = service.compute(emptyList(), today)
        assertThat(state.currentDays).isEqualTo(0)
        assertThat(state.bestDays).isEqualTo(0)
        assertThat(state.hasWorkedOutToday).isFalse()
    }

    @Test
    fun `une seance aujourdhui donne streak 1`() {
        val logs = listOf(logOn(today))
        val state = service.compute(logs, today)
        assertThat(state.currentDays).isEqualTo(1)
        assertThat(state.hasWorkedOutToday).isTrue()
    }

    @Test
    fun `une seance hier mais pas aujourdhui maintient le streak`() {
        // Logique "jusqu'à 23h59 pour ma séance d'aujourd'hui" : on remonte
        // depuis hier si pas de séance today.
        val logs = listOf(logOn(today.minusDays(1)))
        val state = service.compute(logs, today)
        assertThat(state.currentDays).isEqualTo(1)
        assertThat(state.hasWorkedOutToday).isFalse()
        assertThat(state.isAtRisk).isTrue()
    }

    @Test
    fun `streak de 7 jours consecutifs`() {
        val logs = (0..6).map { logOn(today.minusDays(it.toLong())) }
        val state = service.compute(logs, today)
        assertThat(state.currentDays).isEqualTo(7)
        assertThat(state.bestDays).isEqualTo(7)
    }

    @Test
    fun `un gap d'un jour casse le streak courant`() {
        // J-3, J-2 (gap J-1), aujourd'hui → streak courant = 1 (today only)
        val logs = listOf(
            logOn(today),
            logOn(today.minusDays(2)),
            logOn(today.minusDays(3)),
        )
        val state = service.compute(logs, today)
        assertThat(state.currentDays).isEqualTo(1)
        // Mais le best historique reste 2 (J-3, J-2 consécutifs)
        assertThat(state.bestDays).isEqualTo(2)
    }

    @Test
    fun `bestDays prend la meilleure sequence historique meme si plus longue que current`() {
        // Une grosse séquence (10 jours) il y a longtemps + un récent court (2j)
        val oldStart = today.minusDays(50)
        val oldStreak = (0..9).map { logOn(oldStart.minusDays(it.toLong())) }  // 10 jours consécutifs
        val recent = listOf(logOn(today), logOn(today.minusDays(1)))           // 2 jours consécutifs
        val state = service.compute(oldStreak + recent, today)
        assertThat(state.currentDays).isEqualTo(2)
        assertThat(state.bestDays).isEqualTo(10)
    }

    @Test
    fun `plusieurs seances le meme jour comptent pour 1 jour de streak`() {
        // User fait push le matin + pull le soir → 2 logs même date, mais 1 seul jour.
        val logs = listOf(
            logOn(today, hour = 8),
            logOn(today, hour = 19),
            logOn(today.minusDays(1), hour = 18),
        )
        val state = service.compute(logs, today)
        assertThat(state.currentDays).isEqualTo(2)
    }

    @Test
    fun `nextMilestoneToCelebrate retourne le plus haut palier non celebre`() {
        // User vient d'atteindre 100 fresh — célébrons 100, pas 3 ou 7.
        val next = service.nextMilestoneToCelebrate(currentDays = 100, alreadyCelebrated = emptySet())
        assertThat(next).isEqualTo(100)
    }

    @Test
    fun `nextMilestoneToCelebrate exclut les paliers deja celebres`() {
        val next = service.nextMilestoneToCelebrate(
            currentDays = 14,
            alreadyCelebrated = setOf(3, 7, 14),
        )
        assertThat(next).isNull()
    }

    @Test
    fun `nextMilestoneToCelebrate retourne null si pas encore atteint le palier minimal`() {
        val next = service.nextMilestoneToCelebrate(currentDays = 2, alreadyCelebrated = emptySet())
        assertThat(next).isNull()
    }

    @Test
    fun `upcomingMilestone retourne le palier suivant`() {
        assertThat(service.upcomingMilestone(currentDays = 5)).isEqualTo(7)
        assertThat(service.upcomingMilestone(currentDays = 14)).isEqualTo(30)
        assertThat(service.upcomingMilestone(currentDays = 99)).isEqualTo(100)
    }

    @Test
    fun `upcomingMilestone retourne null au-dela du dernier palier`() {
        assertThat(service.upcomingMilestone(currentDays = 100)).isNull()
        assertThat(service.upcomingMilestone(currentDays = 999)).isNull()
    }

    @Test
    fun `isAtRisk faux si pas de streak`() {
        val state = StreakState(currentDays = 0, bestDays = 5, hasWorkedOutToday = false)
        assertThat(state.isAtRisk).isFalse()
    }

    @Test
    fun `isPersonalBest vrai quand current egale best et current positif`() {
        val state = StreakState(currentDays = 30, bestDays = 30, hasWorkedOutToday = true)
        assertThat(state.isPersonalBest).isTrue()
    }

    @Test
    fun `isPersonalBest faux quand current egale best mais current zero`() {
        val state = StreakState(currentDays = 0, bestDays = 0, hasWorkedOutToday = false)
        assertThat(state.isPersonalBest).isFalse()
    }

    private fun logOn(date: LocalDate, hour: Int = 18): WorkoutLogEntity = WorkoutLogEntity(
        workoutId = 1,
        date = LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)),
        durationMinutes = 60,
        completed = true,
    )
}
