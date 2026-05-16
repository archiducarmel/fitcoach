package com.shredcoach.app.domain.notification

import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity

/**
 * Snapshot agrégé du contexte utilisateur — base de décision des notifications
 * context-aware.
 *
 * **Pourquoi un snapshot dédié plutôt que [com.shredcoach.app.domain.coach.CoachUserContext]** :
 *  - [CoachUserContext] sert à enrichir des **prompts LLM** (texte libre,
 *    embeddings sémantiques). Il contient des extraits chat, top exos, etc.
 *  - [UserContextSnapshot] sert à **décider si/quel body envoyer pour une notif**
 *    via des règles **déterministes**. Il contient des chiffres comparables
 *    (deltas kcal, streaks, trend poids) et un pattern psy déduit.
 *
 *  Les deux peuvent partager des sources DB mais ont des contrats différents.
 *  Mixer les deux dans la même classe deviendrait un dieu-objet illisible.
 *
 * **Cycle de vie** : reconstruit à chaque dispatch de notif via
 * [NotificationContextEngine.snapshot]. Pas de cache pour rester frais.
 * Coût ~30-80ms (8 queries DAO parallélisables).
 *
 * **Fenêtres temporelles** :
 *  - `today*`        : J0
 *  - `yesterday*`    : J-1 (détection de pattern naissant)
 *  - `*7d`           : J-6 → J0 (réactivité court-terme, peut être bruité)
 *  - `*30d`          : J-29 → J0 (vérité physiologique long-terme)
 */
data class UserContextSnapshot(
    // ═══════════════════════════════════════════
    // AUJOURD'HUI (J0)
    // ═══════════════════════════════════════════
    val todayCaloriesIn: Int,
    val todayTarget: Int,
    /** `caloriesIn - target` — négatif = déficit (objectif SHRED OK), positif = surplus. */
    val todayDelta: Int,
    val todayMealsLogged: Set<MealType>,
    val todayWorkoutDone: WorkoutLogEntity?,
    val todayWorkoutPlanned: ScheduledWorkoutEntity?,
    /** `target - caloriesIn`, peut être négatif si déjà au-dessus. */
    val remainingKcalToday: Int,

    // ═══════════════════════════════════════════
    // HIER (J-1) — détection pattern naissant
    // ═══════════════════════════════════════════
    val yesterdayCaloriesIn: Int?,
    val yesterdayTarget: Int?,
    val yesterdayDelta: Int?,
    val yesterdayMealsLogged: Set<MealType>,
    val yesterdayWorkoutDone: Boolean,
    val yesterdayWeight: Double?,

    // ═══════════════════════════════════════════
    // FENÊTRE 7 JOURS — réactivité court terme
    // ═══════════════════════════════════════════
    /** Moyenne des deltas quotidiens sur 7j (positif = tendance surplus). */
    val avgDelta7d: Int,
    /** Nb jours où `|delta| < 200` sur 7j. */
    val daysOnTarget7d: Int,
    /** Nb jours où `delta > +300` sur 7j. */
    val daysOverTarget7d: Int,
    /**
     * Streak du **jour le plus récent** "on-target" — compte depuis aujourd'hui
     * (inclus si on-target) ou hier (exclu si today off, mais incrémente sur les
     * jours précédents qui étaient on-target). Sert à détecter le décrochage
     * post-streak.
     */
    val consecutiveOnTargetDays: Int,
    val workoutCount7d: Int,
    val weightTrendKgPerWeek7d: Double?,

    // ═══════════════════════════════════════════
    // FENÊTRE 30 JOURS — vérité physiologique
    // ═══════════════════════════════════════════
    val avgDelta30d: Int,
    val daysOnTarget30d: Int,
    val daysOverTarget30d: Int,
    /** Meilleur enchaînement on-target du mois (motivation chips). */
    val biggestStreakOnTarget30d: Int,
    val workoutCount30d: Int,
    /** Delta poids brut sur 30j (kg). Négatif = perte (bon pour SHRED). */
    val weightChange30d: Double?,
    /** Slope régression linéaire sur 30 points (plus stable que 7j). */
    val weightTrendKgPerWeek30d: Double?,
    /**
     * Nb de "ruptures" : passages d'une streak on-target ≥ 3j à un dérapage
     * sur les 30 derniers jours. Indique un pattern restriction/binge.
     */
    val relapseCount30d: Int,

    // ═══════════════════════════════════════════
    // POIDS (consolidé)
    // ═══════════════════════════════════════════
    val weightLatest: Double?,
    val weightGoal: Double?,
    /** `latest - goal` — positif = encore à perdre (pour SHRED), 0 = atteint. */
    val weightDistanceToGoal: Double?,

    // ═══════════════════════════════════════════
    // ACTIVITÉ (consolidée)
    // ═══════════════════════════════════════════
    val daysSinceLastWorkout: Int,

    // ═══════════════════════════════════════════
    // GLYCÉMIE — CGM (v43+)
    // ═══════════════════════════════════════════
    /** Moyenne mg/dL du jour. Null si pas de log CGM. */
    val todayGlucoseAvgMgdl: Double? = null,
    val todayTirPct: Int? = null,
    val todayPeakMgdl: Double? = null,
    val todayHypoCount: Int? = null,
    /** Vrai si l'user a uploadé un screenshot CGM aujourd'hui. */
    val todayGlucoseLogged: Boolean = false,
    val yesterdayGlucoseAvgMgdl: Double? = null,
    val yesterdayTirPct: Int? = null,
    val yesterdayPeakMgdl: Double? = null,
    val yesterdayHypoCount: Int? = null,
    val yesterdayGlucoseLogged: Boolean = false,
    val glucose7dAvgMgdl: Double? = null,
    val glucose7dAvgTir: Double? = null,
    val glucose30dTrendPerWeek: Double? = null,
    val glucose30dCv: Double? = null,
    val glucose30dTotalHypo: Int = 0,
    /** Pattern dominant 30j (Dr. Glykos analyzer). */
    val glucosePattern: com.shredcoach.app.domain.glucose.GlucosePattern =
        com.shredcoach.app.domain.glucose.GlucosePattern.INSUFFICIENT_DATA,
    /** Nb de jours avec data CGM sur la fenêtre 30j. */
    val glucoseDaysCovered30d: Int = 0,

    // ═══════════════════════════════════════════
    // META
    // ═══════════════════════════════════════════
    /** Nb de jours d'historique nutrition disponibles (saturation à 30). */
    val historyDays: Int,
    /** Pattern psy déduit par [BehaviorAnalyzer]. Pour gating + body selection. */
    val behaviorPattern: BehaviorPattern,
)

/**
 * Pattern comportemental déduit du snapshot. Sert aux builders à choisir
 * un body adapté plutôt qu'un texte fixe.
 *
 * **Ordre de priorité** dans [BehaviorAnalyzer.deduce] : du plus spécifique
 * au plus général. Un user peut matcher plusieurs critères, on prend le
 * pattern le plus actionnable.
 */
enum class BehaviorPattern {
    /** < 7 jours d'historique : pas assez de data pour déduire. */
    STARTING,

    /** Aucun pattern remarquable. État neutre par défaut. */
    NORMAL,

    /** 5j+ on-target ET today's delta > +500 : signal de décrochage post-streak. */
    DECROCHAGE,

    /** 7j+ on-target ET trend poids favorable. État "en flow". */
    MOMENTUM_HIGH,

    /** 3j+ over target sur 7. Glissement progressif. */
    SLIPPING,

    /** Streak workout 5+ mais poids stagne sur 30j. Plateau confirmé. */
    PLATEAU_REAL,

    /** Pattern cyclique : 4+ relapses sur 30j (restriction/binge). */
    CYCLE_BREAKER,

    /** ≥22 jours on-target sur 30j. Très consistent — célébration mensuelle. */
    CONSISTENT_30D,

    /** Trend 30j > -1.2 kg/sem : perte trop rapide, risque perte muscle. */
    WEIGHT_LOSS_TOO_FAST,

    /** ≤4 séances sur 30j : user fantôme côté sport. */
    GHOST_USER,
}
