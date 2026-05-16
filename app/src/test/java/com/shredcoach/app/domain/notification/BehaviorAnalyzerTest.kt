package com.shredcoach.app.domain.notification

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.MealType
import org.junit.Test

/**
 * Tests des règles déterministes de [BehaviorAnalyzer.deduce].
 *
 * **Stratégie** : factory [snapshot] qui produit un [UserContextSnapshot] avec
 * des valeurs "neutres" (NORMAL), surchargées par-test pour matcher un pattern
 * spécifique. Chaque test cible UN seuil ou UN ordre de priorité.
 *
 * **Pourquoi tester les seuils** : les valeurs (`>= 5`, `> 500`, etc.) sont
 * empiriques. Si on les ajuste suite à du retour utilisateur, les tests doivent
 * garantir qu'on ne casse pas le pattern attendu pour les chiffres pivots.
 */
class BehaviorAnalyzerTest {

    // ═══════════════════════════════════════
    // STARTING : pas assez d'historique
    // ═══════════════════════════════════════

    @Test
    fun `STARTING quand moins de 7 jours d'historique`() {
        val s = snapshot(historyDays = 6)
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.STARTING)
    }

    @Test
    fun `STARTING prioritaire meme si autres conditions matchent`() {
        // 5j on-target + today off : matcherait DECROCHAGE normalement
        val s = snapshot(
            historyDays = 5,
            consecutiveOnTargetDays = 5,
            todayDelta = 600,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.STARTING)
    }

    // ═══════════════════════════════════════
    // DECROCHAGE : 5j on-target + today crack
    // ═══════════════════════════════════════

    @Test
    fun `DECROCHAGE quand 5j streak + today plus 500`() {
        val s = snapshot(
            historyDays = 10,
            consecutiveOnTargetDays = 5,
            todayDelta = 501,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.DECROCHAGE)
    }

    @Test
    fun `pas DECROCHAGE si streak inferieur a 5`() {
        val s = snapshot(
            historyDays = 10,
            consecutiveOnTargetDays = 4,
            todayDelta = 600,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.DECROCHAGE)
    }

    @Test
    fun `pas DECROCHAGE si today delta egal ou inferieur a 500`() {
        val s = snapshot(
            historyDays = 10,
            consecutiveOnTargetDays = 5,
            todayDelta = 500, // strict greater-than → exclu
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.DECROCHAGE)
    }

    // ═══════════════════════════════════════
    // WEIGHT_LOSS_TOO_FAST
    // ═══════════════════════════════════════

    @Test
    fun `WEIGHT_LOSS_TOO_FAST quand trend 30j inferieur a -1-2 kg sem`() {
        val s = snapshot(
            historyDays = 30,
            weightTrendKgPerWeek30d = -1.5,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.WEIGHT_LOSS_TOO_FAST)
    }

    @Test
    fun `pas WEIGHT_LOSS_TOO_FAST a -1-0 kg sem (perte saine)`() {
        val s = snapshot(
            historyDays = 30,
            weightTrendKgPerWeek30d = -1.0,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.WEIGHT_LOSS_TOO_FAST)
    }

    @Test
    fun `WEIGHT_LOSS_TOO_FAST prioritaire sur PLATEAU_REAL`() {
        // Conditions logiquement incompatibles (un trend ne peut être à la fois
        // <-1.2 et |x|<0.1) — mais on teste l'ordre de priorité au cas où l'un
        // des seuils change.
        val s = snapshot(
            historyDays = 30,
            weightTrendKgPerWeek30d = -1.5,
            workoutCount30d = 15,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.WEIGHT_LOSS_TOO_FAST)
    }

    // ═══════════════════════════════════════
    // PLATEAU_REAL : workout régulier + poids stagne
    // ═══════════════════════════════════════

    @Test
    fun `PLATEAU_REAL quand 12+ workouts 30j et trend tres faible`() {
        val s = snapshot(
            historyDays = 30,
            workoutCount30d = 12,
            weightTrendKgPerWeek30d = 0.05,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.PLATEAU_REAL)
    }

    @Test
    fun `pas PLATEAU_REAL si moins de 12 workouts`() {
        val s = snapshot(
            historyDays = 30,
            workoutCount30d = 10,
            weightTrendKgPerWeek30d = 0.0,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.PLATEAU_REAL)
    }

    @Test
    fun `pas PLATEAU_REAL si trend pas null mais hors plateau`() {
        val s = snapshot(
            historyDays = 30,
            workoutCount30d = 15,
            weightTrendKgPerWeek30d = -0.5,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.PLATEAU_REAL)
    }

    @Test
    fun `pas PLATEAU_REAL si trend 30j null (pas de pesees)`() {
        val s = snapshot(
            historyDays = 30,
            workoutCount30d = 20,
            weightTrendKgPerWeek30d = null,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.PLATEAU_REAL)
    }

    // ═══════════════════════════════════════
    // CYCLE_BREAKER : restriction/binge pattern
    // ═══════════════════════════════════════

    @Test
    fun `CYCLE_BREAKER quand 4+ relapses sur 30j`() {
        val s = snapshot(historyDays = 30, relapseCount30d = 4)
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.CYCLE_BREAKER)
    }

    @Test
    fun `pas CYCLE_BREAKER avec 3 relapses`() {
        val s = snapshot(historyDays = 30, relapseCount30d = 3)
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.CYCLE_BREAKER)
    }

    // ═══════════════════════════════════════
    // GHOST_USER
    // ═══════════════════════════════════════

    @Test
    fun `GHOST_USER quand workout count 30j inferieur ou egal a 4`() {
        val s = snapshot(historyDays = 30, workoutCount30d = 4)
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.GHOST_USER)
    }

    @Test
    fun `GHOST_USER prioritaire sur CONSISTENT_30D`() {
        // User mange bien (CONSISTENT_30D=22) mais ne bouge pas (GHOST_USER=4).
        // Verdict: priorise GHOST_USER car sèche sans muscle = perte muscle.
        val s = snapshot(
            historyDays = 30,
            workoutCount30d = 3,
            daysOnTarget30d = 25,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.GHOST_USER)
    }

    @Test
    fun `pas GHOST_USER a 5 workouts 30j (juste au-dessus du seuil)`() {
        val s = snapshot(historyDays = 30, workoutCount30d = 5)
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.GHOST_USER)
    }

    // ═══════════════════════════════════════
    // CONSISTENT_30D
    // ═══════════════════════════════════════

    @Test
    fun `CONSISTENT_30D quand 22+ jours on-target sur 30j`() {
        val s = snapshot(
            historyDays = 30,
            daysOnTarget30d = 22,
            workoutCount30d = 12, // > GHOST threshold pour ne pas être préempté
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.CONSISTENT_30D)
    }

    @Test
    fun `pas CONSISTENT_30D a 21 jours (juste sous le seuil)`() {
        val s = snapshot(
            historyDays = 30,
            daysOnTarget30d = 21,
            workoutCount30d = 12,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.CONSISTENT_30D)
    }

    // ═══════════════════════════════════════
    // MOMENTUM_HIGH
    // ═══════════════════════════════════════

    @Test
    fun `MOMENTUM_HIGH quand 7j streak + perte 0-3 kg sem`() {
        val s = snapshot(
            historyDays = 14,
            consecutiveOnTargetDays = 7,
            weightTrendKgPerWeek7d = -0.31,
            workoutCount30d = 8, // pas ghost
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.MOMENTUM_HIGH)
    }

    @Test
    fun `pas MOMENTUM_HIGH si pas de trend 7j`() {
        val s = snapshot(
            historyDays = 14,
            consecutiveOnTargetDays = 7,
            weightTrendKgPerWeek7d = null,
            workoutCount30d = 8,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.MOMENTUM_HIGH)
    }

    @Test
    fun `pas MOMENTUM_HIGH si trend pas suffisament negatif`() {
        val s = snapshot(
            historyDays = 14,
            consecutiveOnTargetDays = 7,
            weightTrendKgPerWeek7d = -0.2, // pas < -0.3
            workoutCount30d = 8,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.MOMENTUM_HIGH)
    }

    // ═══════════════════════════════════════
    // SLIPPING
    // ═══════════════════════════════════════

    @Test
    fun `SLIPPING quand 3+ jours over target sur 7`() {
        val s = snapshot(
            historyDays = 10,
            daysOverTarget7d = 3,
            workoutCount30d = 8,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.SLIPPING)
    }

    @Test
    fun `pas SLIPPING a 2 jours over (juste sous)`() {
        val s = snapshot(
            historyDays = 10,
            daysOverTarget7d = 2,
            workoutCount30d = 8,
        )
        assertThat(BehaviorAnalyzer.deduce(s)).isNotEqualTo(BehaviorPattern.SLIPPING)
    }

    // ═══════════════════════════════════════
    // NORMAL (default)
    // ═══════════════════════════════════════

    @Test
    fun `NORMAL par defaut quand aucune condition specifique`() {
        val s = snapshot(historyDays = 10)
        assertThat(BehaviorAnalyzer.deduce(s)).isEqualTo(BehaviorPattern.NORMAL)
    }

    // ═══════════════════════════════════════
    // Helper : isOnTargetToday
    // ═══════════════════════════════════════

    @Test
    fun `isOnTargetToday vrai si abs delta inferieur a 200`() {
        assertThat(BehaviorAnalyzer.isOnTargetToday(snapshot(todayDelta = 199))).isTrue()
        assertThat(BehaviorAnalyzer.isOnTargetToday(snapshot(todayDelta = -199))).isTrue()
        assertThat(BehaviorAnalyzer.isOnTargetToday(snapshot(todayDelta = 0))).isTrue()
    }

    @Test
    fun `isOnTargetToday faux a 200 ou plus (strict less-than)`() {
        assertThat(BehaviorAnalyzer.isOnTargetToday(snapshot(todayDelta = 200))).isFalse()
        assertThat(BehaviorAnalyzer.isOnTargetToday(snapshot(todayDelta = -201))).isFalse()
    }

    // ═══════════════════════════════════════════════════════════
    // Factory : snapshot "neutre" → NORMAL, override via paramètres
    // ═══════════════════════════════════════════════════════════

    private fun snapshot(
        historyDays: Int = 10,
        todayCaloriesIn: Int = 2000,
        todayTarget: Int = 2000,
        todayDelta: Int = 0,
        todayMealsLogged: Set<MealType> = emptySet(),
        consecutiveOnTargetDays: Int = 2,
        daysOnTarget7d: Int = 4,
        daysOverTarget7d: Int = 0,
        daysOnTarget30d: Int = 15,
        daysOverTarget30d: Int = 2,
        relapseCount30d: Int = 0,
        workoutCount7d: Int = 2,
        workoutCount30d: Int = 10,
        weightTrendKgPerWeek7d: Double? = -0.1,
        weightTrendKgPerWeek30d: Double? = -0.2,
        weightChange30d: Double? = -1.0,
        weightLatest: Double? = 80.0,
        weightGoal: Double? = 75.0,
        daysSinceLastWorkout: Int = 1,
    ) = UserContextSnapshot(
        todayCaloriesIn = todayCaloriesIn,
        todayTarget = todayTarget,
        todayDelta = todayDelta,
        todayMealsLogged = todayMealsLogged,
        todayWorkoutDone = null,
        todayWorkoutPlanned = null,
        remainingKcalToday = todayTarget - todayCaloriesIn,

        yesterdayCaloriesIn = todayTarget,
        yesterdayTarget = todayTarget,
        yesterdayDelta = 0,
        yesterdayMealsLogged = emptySet(),
        yesterdayWorkoutDone = false,
        yesterdayWeight = null,

        avgDelta7d = 0,
        daysOnTarget7d = daysOnTarget7d,
        daysOverTarget7d = daysOverTarget7d,
        consecutiveOnTargetDays = consecutiveOnTargetDays,
        workoutCount7d = workoutCount7d,
        weightTrendKgPerWeek7d = weightTrendKgPerWeek7d,

        avgDelta30d = 0,
        daysOnTarget30d = daysOnTarget30d,
        daysOverTarget30d = daysOverTarget30d,
        biggestStreakOnTarget30d = 5,
        workoutCount30d = workoutCount30d,
        weightChange30d = weightChange30d,
        weightTrendKgPerWeek30d = weightTrendKgPerWeek30d,
        relapseCount30d = relapseCount30d,

        weightLatest = weightLatest,
        weightGoal = weightGoal,
        weightDistanceToGoal = if (weightLatest != null && weightGoal != null)
            weightLatest - weightGoal else null,

        daysSinceLastWorkout = daysSinceLastWorkout,
        historyDays = historyDays,
        behaviorPattern = BehaviorPattern.NORMAL,
    )
}
