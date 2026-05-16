package com.shredcoach.app.domain.notification

import kotlin.math.abs

/**
 * Déduit un [BehaviorPattern] à partir d'un [UserContextSnapshot].
 *
 * **Pourquoi déterministe et non LLM** :
 *  - Les notifs partent à heure fixe, on ne peut pas se permettre un timeout
 *    LLM 25s pour décider de leur contenu.
 *  - Les règles psy sont reproductibles : le même contexte donne le même pattern,
 *    ce qui rend l'app prévisible et testable.
 *  - On peut tuner les seuils empiriquement (par retour utilisateur) sans
 *    re-entraîner un modèle.
 *
 * **Ordre des règles** : du plus spécifique au plus général. Un user peut
 * matcher plusieurs critères ; on retient le pattern le plus **actionnable**
 * (= celui qui mène à un body de notif distinct et utile).
 *
 * **Seuils calibrés** sur la base de la littérature coaching/nutrition :
 *  - 200 kcal de tolérance autour de la cible = "on-target"
 *  - 5j de streak = signal psychologique fort (l'user a investi de l'énergie)
 *  - 500 kcal de dépassement = vrai "off the rails", pas un dîner copieux
 *  - 1.2 kg/sem de perte = limite supérieure saine (cf. ACSM)
 */
object BehaviorAnalyzer {

    // ─── Seuils (constantes nommées pour tuning facile + tests) ───

    /** Tolérance autour de la cible pour être considéré "on-target". */
    private const val ON_TARGET_KCAL_TOLERANCE = 200

    /** Dépassement aujourd'hui qui matche un décrochage si streak ≥ 5j. */
    private const val DECROCHAGE_TODAY_DELTA_KCAL = 500

    /** Streak minimum pour qu'un décrochage soit psychologiquement significatif. */
    private const val DECROCHAGE_STREAK_DAYS = 5

    /** Streak on-target pour décrocher MOMENTUM. */
    private const val MOMENTUM_STREAK_DAYS = 7

    /** Trend poids favorable (kg/sem) — perte saine. */
    private const val MOMENTUM_WEIGHT_TREND_KG_PER_WEEK = -0.3

    /** Nb jours over target sur 7 pour matcher SLIPPING. */
    private const val SLIPPING_DAYS_OVER_7D = 3

    /** Trend 30j max avant red flag perte trop rapide (kg/sem). */
    private const val WEIGHT_LOSS_TOO_FAST_KG_PER_WEEK = -1.2

    /** Plateau 30j : trend absolue inférieure à ce seuil. */
    private const val PLATEAU_WEIGHT_TREND_KG_PER_WEEK = 0.1

    /** Streak workout 30j pour matcher PLATEAU_REAL (régulier sur la durée). */
    private const val PLATEAU_MIN_WORKOUTS_30D = 12

    /** Nb relapses pour CYCLE_BREAKER (pattern restriction/binge). */
    private const val CYCLE_BREAKER_MIN_RELAPSES = 4

    /** Nb jours on-target / 30 pour CONSISTENT_30D. */
    private const val CONSISTENT_30D_MIN_DAYS = 22

    /** Max séances 30j pour GHOST_USER. */
    private const val GHOST_USER_MAX_WORKOUTS_30D = 4

    /** Historique min pour déduire (sinon STARTING). */
    private const val MIN_HISTORY_DAYS = 7

    fun deduce(s: UserContextSnapshot): BehaviorPattern {
        // ─── Garde : pas assez de data ───
        if (s.historyDays < MIN_HISTORY_DAYS) return BehaviorPattern.STARTING

        // ─── 1. DÉCROCHAGE (très spécifique, prioritaire) ───
        // 5j+ on-target juste avant + today significativement off → signal
        // émotionnel à prendre en charge.
        if (s.consecutiveOnTargetDays >= DECROCHAGE_STREAK_DAYS &&
            s.todayDelta > DECROCHAGE_TODAY_DELTA_KCAL) {
            return BehaviorPattern.DECROCHAGE
        }

        // ─── 2. WEIGHT_LOSS_TOO_FAST (red flag santé, prioritaire) ───
        // Perte > 1.2 kg/sem sur 30j = risque catabolisme musculaire.
        // À détecter même si tout le reste est "bon" — c'est un signal santé
        // qu'on doit communiquer.
        s.weightTrendKgPerWeek30d?.let { trend ->
            if (trend < WEIGHT_LOSS_TOO_FAST_KG_PER_WEEK) {
                return BehaviorPattern.WEIGHT_LOSS_TOO_FAST
            }
        }

        // ─── 3. PLATEAU_REAL (basé sur 30j, plus stable que 7j) ───
        // S'entraîne régulièrement sur 30j MAIS poids ne bouge plus.
        // Distinct de PLATEAU_HEBDO court-terme (bruit).
        val weightTrend30d = s.weightTrendKgPerWeek30d
        if (s.workoutCount30d >= PLATEAU_MIN_WORKOUTS_30D &&
            weightTrend30d != null &&
            abs(weightTrend30d) < PLATEAU_WEIGHT_TREND_KG_PER_WEEK) {
            return BehaviorPattern.PLATEAU_REAL
        }

        // ─── 4. CYCLE_BREAKER (pattern restriction/binge) ───
        // 4+ relapses sur 30j = l'user oscille entre restriction et craquage.
        // Suggestion : programmer un refeed hebdo plutôt que subir le craquage.
        if (s.relapseCount30d >= CYCLE_BREAKER_MIN_RELAPSES) {
            return BehaviorPattern.CYCLE_BREAKER
        }

        // ─── 5. GHOST_USER (côté sport) ───
        // Très peu de séances sur 30j — relance prioritaire avant tout
        // conseil nutrition (sans entraînement, la sèche se fait au prix muscle).
        if (s.workoutCount30d <= GHOST_USER_MAX_WORKOUTS_30D) {
            return BehaviorPattern.GHOST_USER
        }

        // ─── 6. CONSISTENT_30D (célébration mensuelle) ───
        // 22+ jours on-target sur 30 = excellent. Mérite un message de
        // reconnaissance (un peu rare, donc impactant).
        if (s.daysOnTarget30d >= CONSISTENT_30D_MIN_DAYS) {
            return BehaviorPattern.CONSISTENT_30D
        }

        // ─── 7. MOMENTUM_HIGH (court-terme positif) ───
        // 7j on-target ET trend poids favorable.
        val weightTrend7d = s.weightTrendKgPerWeek7d
        if (s.consecutiveOnTargetDays >= MOMENTUM_STREAK_DAYS &&
            weightTrend7d != null &&
            weightTrend7d < MOMENTUM_WEIGHT_TREND_KG_PER_WEEK) {
            return BehaviorPattern.MOMENTUM_HIGH
        }

        // ─── 8. SLIPPING (glissement progressif court-terme) ───
        // Plus de 3 jours over target sur 7 — pas encore catastrophe, mais
        // trajectoire à corriger.
        if (s.daysOverTarget7d >= SLIPPING_DAYS_OVER_7D) {
            return BehaviorPattern.SLIPPING
        }

        // ─── Default ───
        return BehaviorPattern.NORMAL
    }

    /**
     * Helper pour les builders : true si le delta du jour est "on-target".
     * Évite que chaque builder réimporte la constante de tolérance.
     */
    fun isOnTargetToday(s: UserContextSnapshot): Boolean =
        abs(s.todayDelta) < ON_TARGET_KCAL_TOLERANCE
}
