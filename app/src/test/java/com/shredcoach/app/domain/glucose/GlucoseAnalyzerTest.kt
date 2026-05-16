package com.shredcoach.app.domain.glucose

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tests des règles déterministes de [GlucoseAnalyzer.detectPattern] et des
 * agrégats. Pure JVM — pas de DAO, pas de DB.
 *
 * **Stratégie** : factory [log] qui crée un GlucoseLogEntity avec les seuils
 * voulus. Chaque test cible UN pattern + ses seuils.
 */
class GlucoseAnalyzerTest {

    // ─── INSUFFICIENT_DATA ───────────────────────────────

    @Test
    fun `INSUFFICIENT_DATA si moins de 7 jours de data sur 30`() {
        val logs = (1..6).map { log(date = today.minusDays(it.toLong()), avg = 110.0) }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.INSUFFICIENT_DATA)
    }

    @Test
    fun `INSUFFICIENT_DATA si data uniquement sans avgMgdl`() {
        val logs = (1..10).map { log(date = today.minusDays(it.toLong()), avg = null) }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.INSUFFICIENT_DATA)
    }

    // ─── HYPO_RISK ────────────────────────────────────────

    @Test
    fun `HYPO_RISK quand 3 hypoglycemies cumulees sur 30j`() {
        val logs = (1..10).map { i ->
            log(date = today.minusDays(i.toLong()),
                avg = 110.0, hypoCount = if (i in 1..3) 1 else 0)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.HYPO_RISK)
    }

    @Test
    fun `pas HYPO_RISK si seulement 2 hypos`() {
        val logs = (1..10).map { i ->
            log(date = today.minusDays(i.toLong()),
                avg = 110.0, hypoCount = if (i <= 2) 1 else 0)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isNotEqualTo(GlucosePattern.HYPO_RISK)
    }

    // ─── HIGH_VARIABILITY ─────────────────────────────────

    @Test
    fun `HIGH_VARIABILITY quand CV moyen au seuil de 36 pct`() {
        val logs = (1..10).map { i ->
            log(date = today.minusDays(i.toLong()), avg = 110.0, cv = 36.0)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.HIGH_VARIABILITY)
    }

    @Test
    fun `pas HIGH_VARIABILITY si CV en dessous du seuil`() {
        val logs = (1..10).map { i ->
            log(date = today.minusDays(i.toLong()), avg = 110.0, cv = 34.5)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isNotEqualTo(GlucosePattern.HIGH_VARIABILITY)
    }

    // ─── POSTPRANDIAL_SPIKES ──────────────────────────────

    @Test
    fun `POSTPRANDIAL_SPIKES quand 3 jours sur 7 ont pic 180 ou plus`() {
        // 3 derniers jours avec pic 195, 4 autres avec pic 130
        val logs = (1..10).map { i ->
            log(
                date = today.minusDays(i.toLong()),
                avg = 120.0,
                peak = if (i in 1..3) 195.0 else 130.0
            )
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.POSTPRANDIAL_SPIKES)
    }

    // ─── DAWN_PHENOMENON ──────────────────────────────────

    @Test
    fun `DAWN_PHENOMENON quand 4 matins ont min eleve avant 9h`() {
        val logs = (1..10).map { i ->
            log(
                date = today.minusDays(i.toLong()),
                avg = 120.0,
                min = if (i in 1..4) 105.0 else 75.0,
                minTime = if (i in 1..4) LocalTime.of(6, 30) else LocalTime.of(14, 0),
            )
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.DAWN_PHENOMENON)
    }

    // ─── STABLE_OPTIMAL ──────────────────────────────────

    @Test
    fun `STABLE_OPTIMAL quand TIR 80 pct et CV inferieur a 30 pct`() {
        val logs = (1..10).map { i ->
            log(
                date = today.minusDays(i.toLong()),
                avg = 110.0, tir = 85, cv = 25.0,
                peak = 145.0, hypoCount = 0,
            )
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.STABLE_OPTIMAL)
    }

    // ─── TREND ────────────────────────────────────────────

    @Test
    fun `RISING_TREND quand slope au dela de 5 mgdl par semaine`() {
        // valeurs croissantes : 110, 115, 120, ... slope ~+5/jour donc +35/sem
        val logs = (0..9).map { i ->
            log(date = today.minusDays((9 - i).toLong()), avg = 110.0 + i * 5.0)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.RISING_TREND)
    }

    @Test
    fun `FALLING_TREND quand slope sous moins 5 mgdl par semaine`() {
        val logs = (0..9).map { i ->
            log(date = today.minusDays((9 - i).toLong()), avg = 160.0 - i * 5.0)
        }
        assertThat(GlucoseAnalyzer.detectPattern(logs)).isEqualTo(GlucosePattern.FALLING_TREND)
    }

    // ─── AGRÉGATS ────────────────────────────────────────

    @Test
    fun `avgMgdl retourne la moyenne des avg non null`() {
        val logs = listOf(
            log(date = today, avg = 100.0),
            log(date = today.minusDays(1), avg = 120.0),
            log(date = today.minusDays(2), avg = null),
        )
        assertThat(GlucoseAnalyzer.avgMgdl(logs)).isEqualTo(110.0)
    }

    @Test
    fun `trendMgdlPerWeek null si moins de 3 points`() {
        val logs = listOf(
            log(date = today, avg = 100.0),
            log(date = today.minusDays(1), avg = 110.0),
        )
        assertThat(GlucoseAnalyzer.trendMgdlPerWeek(logs)).isNull()
    }

    @Test
    fun `totalHypo additionne les nombres d hypoglycemies`() {
        val logs = listOf(
            log(date = today, avg = 100.0, hypoCount = 1),
            log(date = today.minusDays(1), avg = 105.0, hypoCount = 2),
            log(date = today.minusDays(2), avg = 110.0, hypoCount = null),
        )
        assertThat(GlucoseAnalyzer.totalHypo(logs)).isEqualTo(3)
    }

    // ─── Helpers ─────────────────────────────────────────

    private val today: LocalDate = LocalDate.of(2026, 5, 16)

    private fun log(
        date: LocalDate,
        avg: Double? = null,
        peak: Double? = null,
        min: Double? = null,
        minTime: LocalTime? = null,
        tir: Int? = null,
        cv: Double? = null,
        hypoCount: Int? = null,
    ) = GlucoseLogEntity(
        date = date,
        imagePath = null,
        avgMgdl = avg,
        peakMgdl = peak,
        minMgdl = min,
        minTime = minTime,
        timeInRangePct = tir,
        cv = cv,
        hypoCount = hypoCount,
    )
}
