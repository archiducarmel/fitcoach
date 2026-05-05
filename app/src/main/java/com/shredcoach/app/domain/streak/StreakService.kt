package com.shredcoach.app.domain.streak

import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source de vérité pour le calcul du streak (jours consécutifs avec ≥1 séance).
 *
 * **Centralisé pour deux raisons** :
 * 1. Trois callers calculaient leur propre streak indépendamment (HomeViewModel,
 *    CoachTriggerEngine, WorkoutDebriefWorker) → divergence possible.
 * 2. Le passage à la persistance (UserProfile.currentStreakDays) impose une
 *    seule fonction de vérité pour éviter la dérive entre la valeur calculée
 *    et la valeur stockée.
 *
 * **Définition du streak** :
 * - Au moins une séance complétée par jour, sans gap.
 * - Le compteur démarre à aujourd'hui SI séance faite, sinon à hier (logique
 *   "j'ai jusqu'à 23h59 pour ma séance d'aujourd'hui").
 * - On remonte le temps tant qu'il y a au moins une séance dans la date du jour.
 *
 * **Best-streak** dérivé : on parcourt tous les logs une fois et on trouve
 * la plus longue séquence consécutive. O(N log N) à cause du tri ; sur
 * 1000 séances c'est 5ms négligeable face à un I/O DB.
 *
 * **Détection de milestone atteint** : on compare currentStreak avant/après
 * la séance pour produire l'évènement "milestone X atteint" — utilisé par
 * l'UI pour déclencher la dialog de célébration. Couplé à [StreakMilestoneStore]
 * qui mémorise les milestones déjà célébrés (anti double-pop).
 */
@Singleton
class StreakService @Inject constructor() {

    /**
     * Calcule l'état courant du streak à partir d'une liste de logs (déjà
     * filtrée sur `completed = true` côté caller).
     */
    fun compute(completedLogs: List<WorkoutLogEntity>, today: LocalDate = LocalDate.now()): StreakState {
        if (completedLogs.isEmpty()) {
            return StreakState(currentDays = 0, bestDays = 0, hasWorkedOutToday = false)
        }

        val datesWithWorkout = completedLogs.map { it.date.toLocalDate() }.toSet()
        val hasWorkedOutToday = today in datesWithWorkout

        var current = 0
        var cursor = if (hasWorkedOutToday) today else today.minusDays(1)
        while (cursor in datesWithWorkout) {
            current++
            cursor = cursor.minusDays(1)
        }

        val best = computeBestStreak(datesWithWorkout)
        return StreakState(
            currentDays = current,
            bestDays = best.coerceAtLeast(current),
            hasWorkedOutToday = hasWorkedOutToday,
        )
    }

    /**
     * Détecte le plus haut milestone atteint par [currentDays] et qui n'a pas
     * encore été célébré (selon [alreadyCelebrated]). Retourne null si aucun
     * milestone à célébrer maintenant.
     */
    fun nextMilestoneToCelebrate(
        currentDays: Int,
        alreadyCelebrated: Set<Int>,
    ): Int? = MILESTONES
        .filter { it <= currentDays && it !in alreadyCelebrated }
        .maxOrNull()

    /**
     * Le PROCHAIN palier que vise l'utilisateur (utile pour afficher "encore
     * X jours avant le palier 30"). Retourne null si tous les milestones
     * définis ont été dépassés.
     */
    fun upcomingMilestone(currentDays: Int): Int? =
        MILESTONES.firstOrNull { it > currentDays }

    /**
     * Plus longue séquence de jours consécutifs dans l'historique.
     * Implémentation : tri ASC, parcours linéaire avec compteur de run.
     */
    private fun computeBestStreak(dates: Set<LocalDate>): Int {
        if (dates.isEmpty()) return 0
        val sorted = dates.sorted()
        var longest = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }
        return longest
    }

    companion object {
        /**
         * Paliers visualisés (en jours). Choisis pour rythmer la motivation :
         *  - 3j : preuve d'amorçage (bypass la difficulté de démarrage).
         *  - 7j : première semaine complète (validation hebdo).
         *  - 14, 30, 60, 100 : paliers ronds bien connus de la psycho-motivation
         *    sport. Apple Fitness, Strava et Whoop utilisent des grilles similaires.
         */
        val MILESTONES: List<Int> = listOf(3, 7, 14, 30, 60, 100)
    }
}

/**
 * État courant + historique du streak. Immutable : recomputé entièrement à
 * chaque appel de [StreakService.compute].
 */
data class StreakState(
    val currentDays: Int,
    val bestDays: Int,
    val hasWorkedOutToday: Boolean,
) {
    val isAtRisk: Boolean get() = currentDays > 0 && !hasWorkedOutToday
    val isPersonalBest: Boolean get() = currentDays > 0 && currentDays == bestDays
}
