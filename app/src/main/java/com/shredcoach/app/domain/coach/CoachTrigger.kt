package com.shredcoach.app.domain.coach

import java.time.Duration

/**
 * Déclencheurs du coach proactif. Chaque trigger représente une raison
 * **factuelle** (donc défendable, non-spammeuse) d'envoyer une notification.
 *
 * Convention :
 * - [score] : 0-100, urgence + pertinence. Le moteur sélectionne le plus
 *   élevé pour la fenêtre du jour.
 * - [category] : sert au cooldown anti-fatigue (cf. [CoachHistoryStore]).
 *   Stable cross-instances : si le contexte change mais la catégorie reste,
 *   la fenêtre de cooldown s'applique.
 * - [cooldown] : durée min entre deux émissions de la même catégorie.
 *   Tunable par trigger (un PR mérite de revenir vite, un récap hebdo non).
 * - [context] : texte factuel français court, injecté dans le prompt LLM.
 *   Pas destiné à l'utilisateur final (le LLM le reformule).
 * - [primaryDeeplink] : route Compose vers l'écran d'action (ex: "workout_generator").
 *   Null = pas d'action contextuelle, tap → inbox notifs.
 */
sealed interface CoachTrigger {
    val score: Int
    val category: String
    val cooldown: Duration
    val context: String
    val primaryDeeplink: String?
    /** Libellé du bouton d'action principal dans la notif. Null = pas de bouton. */
    val primaryActionLabel: String?

    /**
     * Streak actif mais pas de séance dans la fenêtre habituelle. Score
     * grimpe avec la longueur du streak (plus on a accumulé, plus on a à perdre).
     */
    data class StreakAtRisk(
        val streakDays: Int,
        val daysSinceLastWorkout: Int,
        val plannedWorkoutDays: Int,
    ) : CoachTrigger {
        override val score: Int = (75 + streakDays.coerceAtMost(20)).coerceAtMost(95)
        override val category: String = "streak_at_risk"
        override val cooldown: Duration = Duration.ofDays(2)
        override val context: String =
            "L'utilisateur a un streak de $streakDays jours d'entraînement. Sa dernière séance " +
                "remonte à $daysSinceLastWorkout jours. Il s'entraîne $plannedWorkoutDays fois par semaine."
        override val primaryDeeplink: String? = "workout_generator"
        override val primaryActionLabel: String? = "Faire une séance"
    }

    /** Séance planifiée non exécutée. Plus c'est récent plus le score est haut. */
    data class MissedScheduledWorkout(
        val workoutName: String,
        val daysSinceMissed: Int,
        val scheduledId: Long,
    ) : CoachTrigger {
        override val score: Int = (70 - daysSinceMissed * 5).coerceAtLeast(40)
        override val category: String = "missed_workout"
        override val cooldown: Duration = Duration.ofDays(3)
        override val context: String =
            "L'utilisateur avait planifié '$workoutName' il y a $daysSinceMissed jour(s) " +
                "et ne l'a jamais fait. Statut PLANNED dans le calendrier."
        override val primaryDeeplink: String? = "calendar"
        override val primaryActionLabel: String? = "Reprogrammer"
    }

    /** PR battu hier. Note positive, pas de cooldown long pour ne pas étouffer la célébration. */
    data class PersonalRecordCelebration(
        val exerciseName: String,
        val newWeightKg: Double,
        val previousWeightKg: Double,
        val workoutLogId: Long,
    ) : CoachTrigger {
        override val score: Int = 65
        override val category: String = "pr_celebration"
        override val cooldown: Duration = Duration.ofDays(1)
        override val context: String =
            "L'utilisateur a battu son record sur '$exerciseName' hier : ${newWeightKg}kg " +
                "(précédent : ${previousWeightKg}kg, soit +${(newWeightKg - previousWeightKg).format1()}kg)."
        override val primaryDeeplink: String? = "workout_history_detail/$workoutLogId"
        override val primaryActionLabel: String? = "Voir la séance"
    }

    /** Apport protéine < 70% objectif hier (uniquement en sèche). */
    data class ProteinDeficit(
        val gramsConsumed: Int,
        val goalGrams: Int,
    ) : CoachTrigger {
        override val score: Int = 55
        override val category: String = "protein_deficit"
        override val cooldown: Duration = Duration.ofDays(4)
        override val context: String =
            "Hier, ${gramsConsumed}g de protéines sur ${goalGrams}g d'objectif. " +
                "L'utilisateur est en sèche (SHRED) — la protéine protège la masse musculaire."
        override val primaryDeeplink: String? = "meal_scanner"
        override val primaryActionLabel: String? = "Logger un repas"
    }

    /**
     * Volume hebdomadaire stable ou en baisse sur 3 semaines consécutives.
     * Signal d'un plateau qui justifie un ajustement (intensité, exos nouveaux,
     * deload).
     */
    data class PlateauVolume(
        val weeksFlat: Int,
        val recentWeeklyVolume: Int,
    ) : CoachTrigger {
        override val score: Int = 50
        override val category: String = "plateau_volume"
        override val cooldown: Duration = Duration.ofDays(7)
        override val context: String =
            "Le volume hebdomadaire de l'utilisateur stagne ou baisse depuis $weeksFlat semaines. " +
                "Volume actuel ~${recentWeeklyVolume}kg/semaine. Plateau détecté → besoin de " +
                "varier (intensité, exo nouveau, deload)."
        override val primaryDeeplink: String? = "workout_generator"
        override val primaryActionLabel: String? = "Nouvelle séance"
    }

    /**
     * Reprise après inactivité longue (> 7 jours sans séance, mais historique
     * existant). Moment psychologique critique pour ré-engager sans culpabiliser.
     */
    data class Comeback(
        val daysAway: Int,
        val totalWorkoutsBefore: Int,
    ) : CoachTrigger {
        override val score: Int = 78
        override val category: String = "comeback"
        override val cooldown: Duration = Duration.ofDays(1)
        override val context: String =
            "L'utilisateur n'a pas fait de séance depuis $daysAway jours mais avait un " +
                "historique de $totalWorkoutsBefore séances. Reprise = priorité absolue, " +
                "encourager une séance courte/légère pour recréer l'élan, sans culpabilisation."
        override val primaryDeeplink: String? = "workout_generator"
        override val primaryActionLabel: String? = "Reprendre en douceur"
    }

    /**
     * Le BodyScanner n'a pas été utilisé depuis [daysSince] jours. Suggérer
     * une nouvelle mesure pour ajuster TDEE et metrics corporelles.
     */
    data class BodyScanStale(
        val daysSince: Int,
    ) : CoachTrigger {
        override val score: Int = 38
        override val category: String = "body_scan_stale"
        override val cooldown: Duration = Duration.ofDays(14)
        override val context: String =
            "Dernière mesure corporelle (BodyScanner) il y a $daysSince jours. " +
                "Une mise à jour permet d'ajuster TDEE et de visualiser la progression de la sèche."
        override val primaryDeeplink: String? = "body_scanner"
        override val primaryActionLabel: String? = "Faire un scan"
    }

    /**
     * Récap dimanche soir : nb séances, volume cumulé, jours respectés vs
     * objectif. **Score le plus haut du dimanche soir** pour s'imposer face
     * aux autres triggers.
     */
    data class WeeklyRecap(
        val workoutsThisWeek: Int,
        val targetWorkouts: Int,
        val totalVolumeKg: Int,
        val proteinAdherence: Int,    // 0-100% moyenne sur la semaine
    ) : CoachTrigger {
        override val score: Int = 90
        override val category: String = "weekly_recap"
        override val cooldown: Duration = Duration.ofDays(6)  // 6 pour permettre le dimanche suivant
        override val context: String =
            "Récap de la semaine : $workoutsThisWeek séances sur $targetWorkouts prévues. " +
                "Volume total ${totalVolumeKg}kg. Adhérence protéique moyenne $proteinAdherence%. " +
                "Dimanche soir = moment de réflexion, ton bilan + cap sur la semaine suivante."
        override val primaryDeeplink: String? = "stats"
        override val primaryActionLabel: String? = "Voir les stats"
    }

    /**
     * Proximité d'objectif : poids actuel vs cible, ETA en semaines au rythme
     * actuel. Données : weight_logs des 4 dernières semaines pour la pente.
     */
    data class GoalProximityETA(
        val currentWeightKg: Double,
        val targetWeightKg: Double,
        val weeklyDeltaKg: Double,    // négatif si perte (sèche), positif si prise
        val etaWeeks: Int,
    ) : CoachTrigger {
        override val score: Int = 60
        override val category: String = "goal_eta"
        override val cooldown: Duration = Duration.ofDays(14)
        override val context: String =
            "Poids actuel ${currentWeightKg.format1()}kg, objectif ${targetWeightKg.format1()}kg " +
                "(écart ${(currentWeightKg - targetWeightKg).format1()}kg). " +
                "Rythme récent : ${weeklyDeltaKg.format2()}kg/semaine. ETA : ~$etaWeeks semaines."
        override val primaryDeeplink: String? = "profile"
        override val primaryActionLabel: String? = "Voir mon profil"
    }

    /**
     * Fallback générique. Score bas pour ne JAMAIS battre un trigger spécifique.
     * **Anti-pattern à éviter** : si c'est le seul trigger restant après filtres,
     * le worker skip silencieusement (pas de notif). Cf. [CoachTriggerEngine].
     */
    data class GeneralMotivation(
        val recentWorkoutCount: Int,
        val targetWorkoutCount: Int,
    ) : CoachTrigger {
        override val score: Int = 15
        override val category: String = "motivation_general"
        override val cooldown: Duration = Duration.ofDays(7)
        override val context: String =
            "$recentWorkoutCount séances cette semaine sur $targetWorkoutCount prévues. " +
                "Aucun signal fort à pointer — check-in amical."
        override val primaryDeeplink: String? = "home"
        override val primaryActionLabel: String? = null
    }
}

/* Utilitaires de formatage privés au fichier — évitent les "%.1f" verbeux. */
private fun Double.format1(): String = String.format(java.util.Locale.FRENCH, "%.1f", this)
private fun Double.format2(): String = String.format(java.util.Locale.FRENCH, "%.2f", this)
