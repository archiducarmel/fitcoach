package com.shredcoach.app.domain.training

import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.domain.model.ExerciseVariant

/**
 * Single source of truth pour formater "ce qu'on affiche d'une série / d'un PR"
 * en fonction de la **nature** de l'exercice.
 *
 * Trois natures :
 *  - [ExerciseKind.WEIGHTED]        : barre, haltère, machine → poids × reps
 *  - [ExerciseKind.BODYWEIGHT_REPS] : pompes, tractions, dips → reps seuls
 *  - [ExerciseKind.TIMED]           : gainage, planche, mountain climber → durée
 *
 * **Pourquoi un helper centralisé** : sans ça, chaque écran (session card,
 * history detail, dashboard PR, progression card) ré-implémente sa logique de
 * formatage avec sa propre règle "if (weight > 0) then..." → divergence et
 * regressions garanties dès qu'on touche à la classification. Ici, *un seul*
 * endroit décide : si l'exo est time-based on parle en secondes, si bodyweight
 * on cache le poids, sinon format classique kg×reps.
 *
 * **Cas limite "bodyweight lesté"** (tractions avec ceinture lestée) :
 * détecté quand l'exercice a `variant = BODYWEIGHT` MAIS le set a `weightKg > 0`.
 * On affiche alors le poids additionnel pour ne pas perdre l'info user.
 */
object SetMetricFormatter {

    enum class ExerciseKind { WEIGHTED, BODYWEIGHT_REPS, TIMED }

    /** Détermine la nature d'un exercice à partir de [ExerciseEntity]. */
    fun kindOf(exercise: ExerciseEntity?): ExerciseKind = when {
        exercise == null -> ExerciseKind.WEIGHTED
        exercise.isTimeBased -> ExerciseKind.TIMED
        exercise.variant == ExerciseVariant.BODYWEIGHT -> ExerciseKind.BODYWEIGHT_REPS
        else -> ExerciseKind.WEIGHTED
    }

    /**
     * Formate la métrique principale d'un set complété, à afficher dans une
     * card de série. Convention :
     *  - WEIGHTED         → "80 kg × 10"
     *  - BODYWEIGHT_REPS  → "12 reps"  (ou "12 reps · +10 kg" si lesté)
     *  - TIMED            → "1m30" / "45s"
     */
    fun formatSetMetric(
        kind: ExerciseKind,
        weightKg: Double,
        reps: Int,
    ): String = when (kind) {
        ExerciseKind.WEIGHTED -> "${formatWeight(weightKg)} kg × $reps"
        ExerciseKind.BODYWEIGHT_REPS ->
            if (weightKg > 0.0) "$reps reps · +${formatWeight(weightKg)} kg"
            else "$reps reps"
        ExerciseKind.TIMED -> formatDuration(reps)
    }

    /**
     * Formate le label de la "métrique cible" d'un input session (ex: stepper).
     * Utile pour aligner les labels avec le format affiché.
     */
    fun targetLabel(kind: ExerciseKind): String = when (kind) {
        ExerciseKind.WEIGHTED -> "Reps"
        ExerciseKind.BODYWEIGHT_REPS -> "Reps"
        ExerciseKind.TIMED -> "Durée (s)"
    }

    /**
     * Formate le PR/record d'un exercice :
     *  - WEIGHTED         → "100 kg × 5"
     *  - BODYWEIGHT_REPS  → "25 reps"
     *  - TIMED            → "1m45"
     */
    fun formatPersonalRecord(
        kind: ExerciseKind,
        weightKg: Double,
        reps: Int,
    ): String = formatSetMetric(kind, weightKg, reps)

    /**
     * Formate la "métrique de référence" pour les hero / sparkline
     * (ExerciseProgressionCard). Pour TIMED on n'a pas de 1RM → on retourne
     * la meilleure durée. Pour BODYWEIGHT_REPS → meilleures reps. Pour
     * WEIGHTED → 1RM Brzycki en kg.
     *
     * Retourne (valeurFormatée, unitéSecondaire) pour permettre au caller de
     * styler le hero (gros chiffre + unité plus petite).
     */
    fun formatHero(
        kind: ExerciseKind,
        weightKg: Double,
        reps: Int,
    ): Pair<String, String> = when (kind) {
        ExerciseKind.WEIGHTED -> {
            val oneRm = weightKg * (1.0 + reps / 30.0)
            formatWeight(oneRm) to "kg 1RM"
        }
        ExerciseKind.BODYWEIGHT_REPS -> "$reps" to "reps max"
        ExerciseKind.TIMED -> formatDuration(reps) to "tenue max"
    }

    /**
     * Formate la "Dernière fois" / "Record" affiché dans la session pendant
     * la saisie d'une série. Renvoie null si rien de pertinent à afficher
     * (ex: pas d'historique ou kind sans intérêt comparatif).
     */
    fun formatLastTime(
        kind: ExerciseKind,
        weightKg: Double?,
        reps: Int?,
    ): String? {
        if (reps == null) return null
        return when (kind) {
            ExerciseKind.WEIGHTED -> {
                if (weightKg == null) return null
                "${formatWeight(weightKg)} kg × $reps"
            }
            ExerciseKind.BODYWEIGHT_REPS ->
                if (weightKg != null && weightKg > 0.0) "$reps reps · +${formatWeight(weightKg)} kg"
                else "$reps reps"
            ExerciseKind.TIMED -> formatDuration(reps)
        }
    }

    /**
     * "60" → "1m", "90" → "1m30", "45" → "45s", "0" → "0s".
     * Compact, sans zéros parasites (pas "1m00s" mais "1m").
     */
    fun formatDuration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        if (s < 60) return "${s}s"
        val m = s / 60
        val r = s % 60
        return if (r == 0) "${m}m" else "${m}m${r.toString().padStart(2, '0')}"
    }

    /** "80.0" → "80", "80.5" → "80.5". Pas de zéro parasite après le point. */
    fun formatWeight(kg: Double): String {
        val rounded = (kg * 10).toInt() / 10.0
        return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}"
        else "%.1f".format(java.util.Locale.FRANCE, rounded)
    }
}
