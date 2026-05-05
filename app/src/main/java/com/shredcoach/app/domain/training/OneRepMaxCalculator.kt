package com.shredcoach.app.domain.training

import kotlin.math.roundToInt

/**
 * Calculateur de 1-rep-max (1RM) estimé à partir d'un set (poids × reps).
 *
 * Le 1RM est l'invariant qu'on cherche à suivre dans le temps : il abstrait la
 * combinaison poids/reps utilisée à chaque séance. Comparer "8 reps à 80kg" vs
 * "5 reps à 90kg" est ambigu en brut ; comparer leurs 1RM estimés (~98kg vs
 * ~104kg) donne une lecture directionnelle propre — c'est la base de toute
 * détection de progression / plateau.
 *
 * **Formule retenue : Epley** `1RM = w × (1 + r/30)`
 * - Stable et bien étudiée pour 1-10 reps (sweet spot du strength training).
 * - Légère sur-estimation au-delà de 10 reps mais bornée si on cap les reps.
 * - Vs Brzycki (`w × 36 / (37-r)`) : Brzycki diverge à r=36 (asymptote), Epley
 *   ne diverge jamais → plus robuste face à des données atypiques en input
 *   (ex: 50 reps de calisthenics au poids du corps, qui exploserait Brzycki).
 *
 * **Cap reps à [REPS_CAP]** : au-delà, la formule perd sa pertinence biomécanique
 * (endurance ≠ force max). On clamp pour éviter d'inventer des valeurs.
 *
 * **Filtre poids ≤ 0** : un set au poids du corps (BW + 0kg externe) ne peut pas
 * produire un 1RM "haltère" interprétable — on retourne null pour que le caller
 * skip ces sets dans l'agrégation.
 */
object OneRepMaxCalculator {

    private const val REPS_CAP = 12
    private const val EPLEY_DIVISOR = 30.0

    /**
     * Estime le 1RM en kg pour un set donné. Retourne null si le set ne porte
     * pas de charge externe pertinente (poids du corps pur, reps invalides).
     */
    fun estimate(weightKg: Double, reps: Int): Double? {
        if (weightKg <= 0.0) return null
        if (reps <= 0) return null
        // 1 rep = 1RM brut, on retourne tel quel (la formule donnerait
        // weightKg × (1 + 1/30) = +3.3% qui est faux — Epley calibré pour
        // r >= 2 en pratique).
        if (reps == 1) return weightKg
        val cappedReps = reps.coerceAtMost(REPS_CAP)
        return weightKg * (1.0 + cappedReps / EPLEY_DIVISOR)
    }

    /**
     * Arrondit le 1RM au demi-kilo le plus proche (granularité salle de sport :
     * disques 1.25kg côté plus petit). Pratique pour l'affichage UI.
     */
    fun roundToHalfKg(value: Double): Double = (value * 2.0).roundToInt() / 2.0
}
