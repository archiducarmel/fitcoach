package com.shredcoach.app.domain.nutrition

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.MealScanEntity
import org.junit.Test
import kotlin.math.abs

/**
 * Tests purs JVM de [MealScanModifierMath] :
 *  - Bornes du clamp multiplicateur.
 *  - Facteur effectif (×N + ratio leftover).
 *  - Symétrie avec la formule SQL utilisée par NutritionDao.getDayTotals.
 *  - Edge cases : totalCalories=0, leftover>total*mult, multiplier extrême.
 */
class MealScanModifierMathTest {

    private fun scan(
        totalCalories: Int = 1000,
        totalProteins: Double = 60.0,
        totalCarbs: Double = 100.0,
        totalFats: Double = 40.0,
        totalFibers: Double = 8.0,
        servingMultiplier: Float = 1f,
        leftoverCalories: Int = 0,
    ) = MealScanEntity(
        totalCalories = totalCalories,
        totalProteins = totalProteins,
        totalCarbs = totalCarbs,
        totalFats = totalFats,
        totalFibers = totalFibers,
        servingMultiplier = servingMultiplier,
        leftoverCalories = leftoverCalories,
    )

    // ─── clampMultiplier ────────────────────────────────────

    @Test
    fun `clampMultiplier garde une valeur valide`() {
        assertThat(MealScanModifierMath.clampMultiplier(1f)).isEqualTo(1f)
        assertThat(MealScanModifierMath.clampMultiplier(2.5f)).isEqualTo(2.5f)
    }

    @Test
    fun `clampMultiplier borne en dessous de MIN`() {
        assertThat(MealScanModifierMath.clampMultiplier(0.1f))
            .isEqualTo(MealScanModifierMath.MIN_MULTIPLIER)
        assertThat(MealScanModifierMath.clampMultiplier(-1f))
            .isEqualTo(MealScanModifierMath.MIN_MULTIPLIER)
    }

    @Test
    fun `clampMultiplier borne au-dessus de MAX`() {
        assertThat(MealScanModifierMath.clampMultiplier(50f))
            .isEqualTo(MealScanModifierMath.MAX_MULTIPLIER)
    }

    @Test
    fun `clampMultiplier transforme NaN et Infinity en 1`() {
        assertThat(MealScanModifierMath.clampMultiplier(Float.NaN)).isEqualTo(1f)
        assertThat(MealScanModifierMath.clampMultiplier(Float.POSITIVE_INFINITY)).isEqualTo(1f)
        assertThat(MealScanModifierMath.clampMultiplier(Float.NEGATIVE_INFINITY)).isEqualTo(1f)
    }

    // ─── effectiveFactor ────────────────────────────────────

    @Test
    fun `facteur neutre quand multiplier=1 et leftover=0`() {
        val f = MealScanModifierMath.effectiveFactor(scan())
        assertThat(f).isEqualTo(1.0)
    }

    @Test
    fun `facteur 2 quand multiplier=2 sans restes`() {
        val f = MealScanModifierMath.effectiveFactor(scan(servingMultiplier = 2f))
        assertThat(f).isEqualTo(2.0)
    }

    @Test
    fun `facteur reduit quand leftover sur multiplier=1`() {
        // Repas 1000 kcal, restes 200 kcal → 80% consommé
        val f = MealScanModifierMath.effectiveFactor(
            scan(servingMultiplier = 1f, leftoverCalories = 200)
        )
        assertThat(f).isWithin(0.001).of(0.8)
    }

    @Test
    fun `facteur combine multiplier et leftover`() {
        // 2x portion (2000 kcal) − 500 kcal restes = 1500 effectifs sur 1000 base → 1.5x
        val f = MealScanModifierMath.effectiveFactor(
            scan(servingMultiplier = 2f, leftoverCalories = 500)
        )
        assertThat(f).isWithin(0.001).of(1.5)
    }

    @Test
    fun `facteur clampe a 0 si leftover depasse multiplier x total`() {
        // 1x mais l'user dit avoir laissé 1500 sur 1000 base (incohérent)
        val f = MealScanModifierMath.effectiveFactor(
            scan(servingMultiplier = 1f, leftoverCalories = 1500)
        )
        assertThat(f).isEqualTo(0.0)
    }

    @Test
    fun `facteur fallback sur multiplier si totalCalories=0`() {
        // Scan dégénéré (très rare) : pas de base pour calculer un ratio
        val f = MealScanModifierMath.effectiveFactor(
            scan(totalCalories = 0, servingMultiplier = 2f, leftoverCalories = 500)
        )
        assertThat(f).isEqualTo(2.0)
    }

    // ─── effective* (calories + macros) ─────────────────────

    @Test
    fun `effectiveCalories applique le facteur a totalCalories`() {
        val s = scan(totalCalories = 1000, servingMultiplier = 2f, leftoverCalories = 300)
        // factor = 2 - 0.3 = 1.7 → 1000 * 1.7 = 1700
        assertThat(MealScanModifierMath.effectiveCalories(s)).isEqualTo(1700)
    }

    @Test
    fun `effectiveProteins applique le facteur a totalProteins`() {
        val s = scan(totalCalories = 1000, totalProteins = 60.0,
            servingMultiplier = 1f, leftoverCalories = 250)
        // factor = 0.75 → 60 * 0.75 = 45
        assertThat(MealScanModifierMath.effectiveProteins(s)).isWithin(0.01).of(45.0)
    }

    @Test
    fun `effectiveCalories ne peut pas etre negatif`() {
        val s = scan(totalCalories = 100, servingMultiplier = 1f, leftoverCalories = 500)
        assertThat(MealScanModifierMath.effectiveCalories(s)).isAtLeast(0)
    }

    // ─── helpers booleen ────────────────────────────────────

    @Test
    fun `hasModifier false quand neutre`() {
        assertThat(MealScanModifierMath.hasModifier(scan())).isFalse()
    }

    @Test
    fun `hasModifier true quand multiplier ne 1`() {
        assertThat(MealScanModifierMath.hasModifier(scan(servingMultiplier = 1.5f))).isTrue()
        assertThat(MealScanModifierMath.hasModifier(scan(servingMultiplier = 0.5f))).isTrue()
    }

    @Test
    fun `hasModifier true quand leftover en grammes`() {
        assertThat(MealScanModifierMath.hasModifier(scan(leftoverCalories = 50))).isTrue()
    }

    @Test
    fun `hasLeftover true uniquement si leftover existe`() {
        assertThat(MealScanModifierMath.hasLeftover(scan())).isFalse()
        assertThat(MealScanModifierMath.hasLeftover(scan(leftoverCalories = 100))).isTrue()
    }

    // ─── smartDecrement ─────────────────────────────────────

    @Test
    fun `smartDecrement entier retire une portion complete`() {
        // ×3 → ×2 (annule un "+1")
        assertThat(MealScanModifierMath.smartDecrement(3f)).isEqualTo(2f)
        // ×2 → ×1
        assertThat(MealScanModifierMath.smartDecrement(2f)).isEqualTo(1f)
        // ×5 → ×4
        assertThat(MealScanModifierMath.smartDecrement(5f)).isEqualTo(4f)
    }

    @Test
    fun `smartDecrement fractionnaire retire une demi-portion`() {
        // ×2.5 → ×2 (annule un "+½")
        assertThat(MealScanModifierMath.smartDecrement(2.5f)).isEqualTo(2f)
        // ×1.5 → ×1
        assertThat(MealScanModifierMath.smartDecrement(1.5f)).isEqualTo(1f)
    }

    @Test
    fun `smartDecrement plancher a 1`() {
        assertThat(MealScanModifierMath.smartDecrement(1f)).isEqualTo(1f)
        // Cas paranoïaque : valeur < 1 (legacy) → reste clampé à 1
        assertThat(MealScanModifierMath.smartDecrement(0.5f)).isEqualTo(1f)
    }

    // ─── parité avec la formule SQL d'agrégation ────────────

    /**
     * Vérifie que le facteur Kotlin matche exactement la formule SQL :
     *
     *   factor = MAX(0, mult - leftoverCal/totalCal)
     *
     * Si cette parité casse, les totaux jour (côté SQL) divergeraient de
     * l'affichage par repas (côté Kotlin) — symptôme: "ma somme du jour
     * ne fait pas la somme des repas".
     */
    @Test
    fun `parite formule Kotlin et SQL sur un panel de cas`() {
        val cases = listOf(
            Triple(1f, 1000, 0),       // neutre
            Triple(2f, 1000, 0),       // ×2
            Triple(1f, 1000, 300),     // 30% restes
            Triple(1.5f, 800, 200),    // ×1.5 - 25% restes
            Triple(3f, 1500, 600),     // ×3 - 40% restes (= ×2.6 effectif)
            Triple(0.5f, 1000, 800),   // grignotage moitié + presque tout laissé (→ borne 0)
        )
        for ((mult, total, leftover) in cases) {
            val kotlinFactor = MealScanModifierMath.effectiveFactor(mult, total, leftover)
            val sqlFactor = sqlLikeFactor(mult, total, leftover)
            val msg = "mult=$mult, total=$total, leftover=$leftover"
            assertThat(abs(kotlinFactor - sqlFactor)).named(msg).isLessThan(0.0001)
        }
    }

    /** Reproduction Kotlin de la formule SQL — référence pour la parité. */
    private fun sqlLikeFactor(mult: Float, totalCal: Int, leftoverCal: Int): Double {
        val m = mult.coerceIn(MealScanModifierMath.MIN_MULTIPLIER, MealScanModifierMath.MAX_MULTIPLIER)
            .toDouble()
        val r = if (totalCal > 0) leftoverCal.toDouble() / totalCal.toDouble() else 0.0
        return maxOf(0.0, m - r)
    }
}
