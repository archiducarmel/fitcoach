package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.MealScanEntity
import kotlin.math.max

/**
 * Helper pur (JVM, sans dépendance Android) qui calcule les valeurs
 * **effectives** d'un scan repas en tenant compte des modificateurs de
 * portion :
 *  - `servingMultiplier` : "j'en ai repris" (×1, ×1.5, ×2, ×3, custom)
 *  - `leftover*`         : "j'ai pas fini" — restes rescannés et déduits
 *
 * Toutes les fonctions sont déterministes et symétriques avec la formule
 * SQL utilisée par [com.shredcoach.app.data.local.dao.NutritionDao.getDayTotals].
 * **Ne JAMAIS désynchroniser** les deux formules sans mettre à jour les
 * tests (MealScanModifierMathTest) — un écart ferait diverger la somme
 * journalière vs l'affichage par repas.
 *
 * **Bornes** :
 *  - `servingMultiplier` est clampé dans [MIN_MULTIPLIER, MAX_MULTIPLIER].
 *  - Le facteur effectif ne peut pas être négatif (si l'user déclare avoir
 *    laissé plus que ce qu'il a pris → on borne à 0, le repas n'a pas existé).
 */
object MealScanModifierMath {

    /** Multiplicateur minimum proposé en UI (×0.25 ≈ "j'ai grignoté un quart"). */
    const val MIN_MULTIPLIER = 0.25f

    /** Multiplicateur maximum — au-delà c'est suspect (10 portions d'un coup). */
    const val MAX_MULTIPLIER = 10f

    /**
     * Clamp un multiplicateur saisi par l'utilisateur. Retourne 1f si la valeur
     * n'est pas un float valide (NaN, infini, hors bornes).
     */
    fun clampMultiplier(value: Float): Float {
        if (value.isNaN() || value.isInfinite()) return 1f
        return value.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    /**
     * Calcule le facteur effectif à appliquer aux macros d'un meal_log lié à
     * ce scan. Le facteur encapsule à la fois le multiplicateur de portion et
     * la déduction des restes (ratio calorique).
     *
     *   factor = MAX(0, servingMultiplier − leftoverCalories / totalCalories)
     *
     * **Pourquoi un ratio calorique unique pour toutes les macros** : v1 fait
     * le choix de la simplicité. Le ratio calorique est l'approximation la
     * plus naturelle (les restes ont approximativement la même composition
     * que le plat initial). Une déduction par-macro nécessiterait 4 facteurs
     * distincts et complexifierait significativement la SQL d'agrégation,
     * pour un gain de précision marginal sur des données déjà incertaines
     * (estimation visuelle Gemini).
     *
     * **Edge cases** :
     *  - totalCalories = 0 (rare, scan vide) → facteur = servingMultiplier
     *    (rien à déduire des restes).
     *  - leftover > total × multiplier → facteur clampé à 0 (incohérent, on
     *    refuse d'ajouter du négatif aux agrégations).
     */
    fun effectiveFactor(scan: MealScanEntity): Double {
        val mult = clampMultiplier(scan.servingMultiplier).toDouble()
        val leftoverRatio = if (scan.totalCalories > 0) {
            scan.leftoverCalories.toDouble() / scan.totalCalories.toDouble()
        } else 0.0
        return max(0.0, mult - leftoverRatio)
    }

    /**
     * Facteur effectif pour des valeurs "primitives" (utilisé côté SQL preview
     * et tests). Quand un caller n'a pas l'entity sous la main mais les champs.
     */
    fun effectiveFactor(
        servingMultiplier: Float,
        totalCalories: Int,
        leftoverCalories: Int,
    ): Double {
        val mult = clampMultiplier(servingMultiplier).toDouble()
        val leftoverRatio = if (totalCalories > 0) leftoverCalories.toDouble() / totalCalories.toDouble() else 0.0
        return max(0.0, mult - leftoverRatio)
    }

    /** Calories effectives totales du scan (toutes parts confondues). */
    fun effectiveCalories(scan: MealScanEntity): Int {
        val full = scan.totalCalories * effectiveFactor(scan)
        return full.toInt().coerceAtLeast(0)
    }

    /** Protéines effectives totales du scan. */
    fun effectiveProteins(scan: MealScanEntity): Double =
        (scan.totalProteins * effectiveFactor(scan)).coerceAtLeast(0.0)

    /** Glucides effectifs totaux du scan. */
    fun effectiveCarbs(scan: MealScanEntity): Double =
        (scan.totalCarbs * effectiveFactor(scan)).coerceAtLeast(0.0)

    /** Lipides effectifs totaux du scan. */
    fun effectiveFats(scan: MealScanEntity): Double =
        (scan.totalFats * effectiveFactor(scan)).coerceAtLeast(0.0)

    /** Fibres effectives totales du scan. */
    fun effectiveFibers(scan: MealScanEntity): Double =
        (scan.totalFibers * effectiveFactor(scan)).coerceAtLeast(0.0)

    /** Indique si au moins un modificateur est actif (×N ≠ 1 ou restes > 0). */
    fun hasModifier(scan: MealScanEntity): Boolean =
        scan.servingMultiplier != 1f || scan.leftoverCalories > 0

    /** Indique si l'user a saisi un scan de restes (utilisé pour badges UI). */
    fun hasLeftover(scan: MealScanEntity): Boolean =
        scan.leftoverScannedAt != null || scan.leftoverCalories > 0

    /**
     * Décrément "intelligent" pour le bouton "−" de la UI :
     *  - Si le multiplicateur courant est un entier (×2, ×3, ×4…) → on retire
     *    une portion complète (−1.0). Ramène à l'entier précédent (×3 → ×2).
     *  - Si le multiplicateur est fractionnaire (×1.5, ×2.5…) → on retire une
     *    demi-portion (−0.5). Ramène au demi suivant (×2.5 → ×2.0).
     *
     * Borné à 1.0 minimum (le repas initial existe toujours). Ce contrat
     * garantit que `decrement(increment(x)) == x` pour les operations "+ 1" et
     * "+ ½" — le bouton "−" annule la dernière action.
     */
    fun smartDecrement(current: Float): Float {
        val asInt = current.toInt()
        val isInteger = asInt.toFloat() == current
        val delta = if (isInteger) 1f else 0.5f
        return (current - delta).coerceAtLeast(1f)
    }
}
