package com.shredcoach.app.domain.notification

import android.content.Context
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.entity.MealType
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Tous les builders de notifications context-aware regroupés dans un seul
 * fichier. Chaque builder est un `object` stateless qui prend [Context] +
 * [UserContextSnapshot] et retourne un [NotifDecision].
 *
 * **Pattern de décision** : on évalue les conditions du plus spécifique au
 * plus général. Le premier match retourne `Send(...)`. Si rien ne match,
 * fallback sur le body par défaut existant (rétro-compat).
 *
 * **Skip rules** : chaque builder décide indépendamment quand SKIP. Pas de
 * règle globale "skip si user en surplus" — c'est aux builders de juger.
 */

// ═══════════════════════════════════════════════════════════════════════
// MEAL REMINDERS
// ═══════════════════════════════════════════════════════════════════════

object BreakfastBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        if (MealType.BREAKFAST in s.todayMealsLogged) {
            return NotifDecision.Skip("breakfast_already_logged")
        }

        val title = ctx.getString(R.string.notif_meal_breakfast_title)
        val channel = ShredCoachApplication.CHANNEL_MEALS

        // Pre-training : séance planifiée dans les 3h
        val planned = s.todayWorkoutPlanned
        if (planned?.time != null) {
            val now = LocalDateTime.now()
            val plannedAt = planned.date.atTime(planned.time)
            val hoursUntil = Duration.between(now, plannedAt).toHours().toInt()
            if (hoursUntil in 0..3) {
                return NotifDecision.Send(
                    title = title,
                    body = ctx.getString(R.string.notif_breakfast_pre_training, hoursUntil.coerceAtLeast(1)),
                    channelId = channel,
                )
            }
        }

        // Pattern : décrochage hier → message de retour
        if (s.behaviorPattern == BehaviorPattern.DECROCHAGE) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_breakfast_decrochage),
                channelId = channel,
            )
        }

        // Pattern : 22+ jours consistant → message de reconnaissance
        if (s.behaviorPattern == BehaviorPattern.CONSISTENT_30D) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_breakfast_consistent, s.daysOnTarget30d),
                channelId = channel,
            )
        }

        // Rest day (pas de séance prévue et pas faite)
        if (s.todayWorkoutDone == null && s.todayWorkoutPlanned == null) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_breakfast_rest_day),
                channelId = channel,
            )
        }

        // Default
        return NotifDecision.Send(
            title = title,
            body = ctx.getString(R.string.notif_meal_breakfast_body),
            channelId = channel,
        )
    }
}

object LunchBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        if (MealType.LUNCH in s.todayMealsLogged) {
            return NotifDecision.Skip("lunch_already_logged")
        }

        val title = ctx.getString(R.string.notif_meal_lunch_title)
        val channel = ShredCoachApplication.CHANNEL_MEALS

        // Surplus déjà installé en début de journée (>+300)
        if (s.todayDelta > 300) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(
                    R.string.notif_lunch_light_overdelta,
                    s.todayDelta,
                    s.remainingKcalToday.coerceAtLeast(0),
                ),
                channelId = channel,
            )
        }

        // Récup après séance déjà faite ce matin
        if (s.todayWorkoutDone != null) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_lunch_recovery),
                channelId = channel,
            )
        }

        // Décrochage → recadrage
        if (s.behaviorPattern == BehaviorPattern.DECROCHAGE) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_lunch_recadrage),
                channelId = channel,
            )
        }

        // Streak on-target ≥ 5 jours → continuité
        if (s.consecutiveOnTargetDays >= 5) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_lunch_keepcap, s.consecutiveOnTargetDays),
                channelId = channel,
            )
        }

        return NotifDecision.Send(
            title = title,
            body = ctx.getString(R.string.notif_meal_lunch_body),
            channelId = channel,
        )
    }
}

object SnackBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        if (MealType.SNACK in s.todayMealsLogged) {
            return NotifDecision.Skip("snack_already_logged")
        }

        // Skip si déjà clairement en surplus — pas besoin d'inciter à grignoter
        if (s.todayDelta > 200) {
            return NotifDecision.Skip("snack_skip_surplus")
        }

        val title = ctx.getString(R.string.notif_meal_snack_title)
        val channel = ShredCoachApplication.CHANNEL_MEALS

        // Pre-training : si séance dans les 3h après le snack, message dédié
        val planned = s.todayWorkoutPlanned
        if (planned?.time != null) {
            val now = LocalDateTime.now()
            val plannedAt = planned.date.atTime(planned.time)
            val hoursUntil = Duration.between(now, plannedAt).toHours().toInt()
            if (hoursUntil in 0..3) {
                return NotifDecision.Send(
                    title = title,
                    body = ctx.getString(R.string.notif_snack_pre_training, hoursUntil.coerceAtLeast(1)),
                    channelId = channel,
                )
            }
        }

        return NotifDecision.Send(
            title = title,
            body = ctx.getString(R.string.notif_meal_snack_body),
            channelId = channel,
        )
    }
}

object DinnerBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        if (MealType.DINNER in s.todayMealsLogged) {
            return NotifDecision.Skip("dinner_already_logged")
        }

        val title = ctx.getString(R.string.notif_meal_dinner_title)
        val channel = ShredCoachApplication.CHANNEL_MEALS

        // Décrochage en cours (5j+ + today >+500)
        if (s.behaviorPattern == BehaviorPattern.DECROCHAGE) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_dinner_decrochage),
                channelId = channel,
            )
        }

        // Surplus déjà installé → soir ultra-léger
        if (s.todayDelta > 400) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_dinner_light_overdelta, s.todayDelta),
                channelId = channel,
            )
        }

        // Récup après séance
        if (s.todayWorkoutDone != null) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_dinner_recovery),
                channelId = channel,
            )
        }

        // Streak on-target ≥ 5 jours → garder le cap
        if (s.consecutiveOnTargetDays >= 5 && s.remainingKcalToday > 100) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(
                    R.string.notif_dinner_keepcap,
                    s.remainingKcalToday,
                    s.consecutiveOnTargetDays,
                ),
                channelId = channel,
            )
        }

        return NotifDecision.Send(
            title = title,
            body = ctx.getString(R.string.notif_meal_dinner_body),
            channelId = channel,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// SHAKERS (context-driven : skip si pas pertinent)
// ═══════════════════════════════════════════════════════════════════════

object ShakerMorningBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        val planned = s.todayWorkoutPlanned

        // Pas de séance planifiée → pas de shaker pre-training (le user fait
        // de la muscu, pas du shake de petit-déj systématique)
        if (planned == null || planned.time == null) {
            return NotifDecision.Skip("shaker_morning_no_workout_planned")
        }

        val now = LocalDateTime.now()
        val plannedAt = planned.date.atTime(planned.time)
        val hoursUntil = Duration.between(now, plannedAt).toHours().toInt()

        // Séance trop tard dans la journée — pas pertinent maintenant
        if (hoursUntil > 4) {
            return NotifDecision.Skip("shaker_morning_workout_too_far")
        }
        if (hoursUntil < 0) {
            return NotifDecision.Skip("shaker_morning_workout_passed")
        }

        return NotifDecision.Send(
            title = ctx.getString(R.string.notif_shaker_morning_title),
            body = ctx.getString(R.string.notif_shaker_pre_workout_today, hoursUntil.coerceAtLeast(1)),
            channelId = ShredCoachApplication.CHANNEL_MEALS,
        )
    }
}

object ShakerEveningBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        // Pas de séance effectuée aujourd'hui → caséine inutile
        if (s.todayWorkoutDone == null) {
            return NotifDecision.Skip("shaker_evening_no_workout_done")
        }
        // Surplus calorique → on n'ajoute pas de kcal supplémentaires
        if (s.todayDelta > 500) {
            return NotifDecision.Skip("shaker_evening_already_over")
        }

        return NotifDecision.Send(
            title = ctx.getString(R.string.notif_shaker_evening_title),
            body = ctx.getString(R.string.notif_shaker_evening_post_workout),
            channelId = ShredCoachApplication.CHANNEL_MEALS,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// BEDTIME
// ═══════════════════════════════════════════════════════════════════════

object BedtimeBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        val title = ctx.getString(R.string.notif_bedtime_title)
        val channel = ShredCoachApplication.CHANNEL_BEDTIME

        // Post-workout : sommeil = récup
        if (s.todayWorkoutDone != null) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_bedtime_post_workout),
                channelId = channel,
            )
        }

        // Slipping : le sommeil régule l'appétit
        if (s.behaviorPattern == BehaviorPattern.SLIPPING) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_bedtime_slipping),
                channelId = channel,
            )
        }

        return NotifDecision.Send(
            title = title,
            body = ctx.getString(R.string.notif_bedtime_body),
            channelId = channel,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MOTIVATION
// ═══════════════════════════════════════════════════════════════════════

object MotivationBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        val title = ctx.getString(R.string.notif_motivation_title)
        val channel = ShredCoachApplication.CHANNEL_WORKOUT

        // Red flag santé : perte trop rapide
        if (s.behaviorPattern == BehaviorPattern.WEIGHT_LOSS_TOO_FAST) {
            val rate = s.weightTrendKgPerWeek30d ?: 0.0
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_motivation_too_fast, abs(rate)),
                channelId = channel,
            )
        }

        // Plateau détecté
        if (s.behaviorPattern == BehaviorPattern.PLATEAU_REAL) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_motivation_plateau),
                channelId = channel,
            )
        }

        // Décrochage hier ou aujourd'hui
        if (s.behaviorPattern == BehaviorPattern.DECROCHAGE) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(R.string.notif_motivation_decrochage),
                channelId = channel,
            )
        }

        // Ghost user
        if (s.behaviorPattern == BehaviorPattern.GHOST_USER ||
            s.daysSinceLastWorkout >= 3) {
            return NotifDecision.Send(
                title = title,
                body = ctx.getString(
                    R.string.notif_motivation_ghost,
                    s.daysSinceLastWorkout.coerceAtMost(30),
                ),
                channelId = channel,
            )
        }

        return NotifDecision.Skip("motivation_user_active")
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MORNING_BRIEF (P5) — synthèse contextuelle 07:00
// ═══════════════════════════════════════════════════════════════════════

/**
 * Brief du jour : un seul message dont le contenu dépend 100 % du pattern psy.
 * C'est la notif la plus "coach proactif" — elle remplace l'idée de plusieurs
 * notifs spécifiques le matin par une seule synthèse adaptée.
 *
 * **Skip si STARTING** : pas assez de données historiques pour un brief utile,
 * l'utilisateur recevrait du vide.
 */
object MorningBriefBuilder {
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        if (s.behaviorPattern == BehaviorPattern.STARTING) {
            return NotifDecision.Skip("morning_brief_no_history")
        }

        val title = ctx.getString(R.string.notif_morning_brief_title)
        val channel = ShredCoachApplication.CHANNEL_WORKOUT
        val trendKgWeek = s.weightTrendKgPerWeek30d ?: s.weightTrendKgPerWeek7d ?: 0.0

        val body = when (s.behaviorPattern) {
            BehaviorPattern.DECROCHAGE -> ctx.getString(
                R.string.notif_morning_brief_decrochage,
                s.yesterdayDelta ?: 0,
            )

            BehaviorPattern.MOMENTUM_HIGH -> ctx.getString(
                R.string.notif_morning_brief_momentum,
                s.consecutiveOnTargetDays,
                trendKgWeek,
                s.todayTarget,
            )

            BehaviorPattern.CONSISTENT_30D -> ctx.getString(
                R.string.notif_morning_brief_consistent,
                s.daysOnTarget30d,
                s.todayTarget,
            )

            BehaviorPattern.SLIPPING -> ctx.getString(
                R.string.notif_morning_brief_slipping,
                s.avgDelta7d.coerceAtLeast(0),
                (s.todayTarget - 200).coerceAtLeast(1200),
            )

            BehaviorPattern.PLATEAU_REAL -> ctx.getString(
                R.string.notif_morning_brief_plateau,
                s.todayTarget,
            )

            BehaviorPattern.CYCLE_BREAKER -> ctx.getString(
                R.string.notif_morning_brief_cycle,
            )

            BehaviorPattern.GHOST_USER -> ctx.getString(
                R.string.notif_morning_brief_ghost,
                s.workoutCount30d,
            )

            BehaviorPattern.WEIGHT_LOSS_TOO_FAST -> ctx.getString(
                R.string.notif_morning_brief_too_fast,
                trendKgWeek,
            )

            BehaviorPattern.NORMAL -> {
                val workoutSuffix = if (s.todayWorkoutPlanned != null)
                    ctx.getString(R.string.notif_morning_brief_normal_with_workout)
                else
                    ctx.getString(R.string.notif_morning_brief_normal_no_workout)
                ctx.getString(R.string.notif_morning_brief_normal, s.todayTarget, workoutSuffix)
            }

            BehaviorPattern.STARTING -> {
                // Garde déjà gérée plus haut, ne devrait pas arriver ici.
                return NotifDecision.Skip("morning_brief_starting_unreachable")
            }
        }

        return NotifDecision.Send(
            title = title,
            body = body,
            channelId = channel,
        )
    }
}

