package com.shredcoach.app.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests pour la formule Epley + edge cases.
 *
 * Référence formule : `1RM = w × (1 + r/30)` pour r ≥ 2, retourne w pour r=1.
 */
class OneRepMaxCalculatorTest {

    @Test
    fun `single rep retourne le poids brut sans appliquer Epley`() {
        // Epley appliqué donnerait 100 × (1 + 1/30) = 103.33 — faux pour 1RM réel.
        val result = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 1)
        assertThat(result).isEqualTo(100.0)
    }

    @Test
    fun `5 reps a 100kg donne environ 116-67kg via Epley`() {
        // 100 × (1 + 5/30) = 116.666...
        val result = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 5)
        assertThat(result).isWithin(0.01).of(116.666666)
    }

    @Test
    fun `10 reps a 80kg donne 106-67kg`() {
        val result = OneRepMaxCalculator.estimate(weightKg = 80.0, reps = 10)
        assertThat(result).isWithin(0.01).of(106.666666)
    }

    @Test
    fun `reps au-dessus de 12 sont cappees a 12`() {
        // Cap à 12 pour éviter l'extrapolation au-delà du sweet-spot biomécanique.
        // 100 × (1 + 12/30) = 140
        val result = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 50)
        assertThat(result).isWithin(0.01).of(140.0)
    }

    @Test
    fun `reps egales au cap retournent la meme valeur que les reps superieures`() {
        val at12 = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 12)
        val at100 = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 100)
        assertThat(at12).isEqualTo(at100)
    }

    @Test
    fun `weight zero retourne null`() {
        val result = OneRepMaxCalculator.estimate(weightKg = 0.0, reps = 5)
        assertThat(result).isNull()
    }

    @Test
    fun `weight negatif retourne null`() {
        val result = OneRepMaxCalculator.estimate(weightKg = -10.0, reps = 5)
        assertThat(result).isNull()
    }

    @Test
    fun `reps zero retourne null`() {
        val result = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = 0)
        assertThat(result).isNull()
    }

    @Test
    fun `reps negatives retournent null`() {
        val result = OneRepMaxCalculator.estimate(weightKg = 100.0, reps = -1)
        assertThat(result).isNull()
    }

    @Test
    fun `roundToHalfKg arrondit au demi-kilo le plus proche`() {
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.0)).isEqualTo(100.0)
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.2)).isEqualTo(100.0)
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.3)).isEqualTo(100.5)
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.5)).isEqualTo(100.5)
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.7)).isEqualTo(100.5)
        assertThat(OneRepMaxCalculator.roundToHalfKg(100.8)).isEqualTo(101.0)
    }

    @Test
    fun `roundToHalfKg gere les valeurs negatives`() {
        // Les Doubles négatifs ne devraient pas exister en pratique (1RM positif),
        // mais on garantit que la fonction ne crashe pas.
        assertThat(OneRepMaxCalculator.roundToHalfKg(-100.3)).isEqualTo(-100.5)
    }

    @Test
    fun `light weight charge externe nominale`() {
        // 50kg x 8 reps = 50 × (1 + 8/30) = 63.333
        val result = OneRepMaxCalculator.estimate(weightKg = 50.0, reps = 8)
        assertThat(result).isWithin(0.01).of(63.333333)
    }
}
