package com.shredcoach.app.domain.model

import androidx.annotation.StringRes
import com.shredcoach.app.R

/**
 * Variante d'exécution d'un exercice. Voir [MuscleGroup] pour la dualité
 * `displayName` (sérialisation) / `displayNameRes` (UI i18n).
 */
enum class ExerciseVariant(
    val displayName: String,
    @StringRes val displayNameRes: Int,
    val description: String,
    @StringRes val descriptionRes: Int,
    val color: Long,
) {
    MACHINE(
        "Machine",
        R.string.variant_machine,
        "Guidé, sécurisé, idéal pour débuter et charger lourd en toute sécurité",
        R.string.variant_machine_desc,
        0xFF3B82F6 // Blue
    ),
    WEIGHTS(
        "Haltères / Barre",
        R.string.variant_weights,
        "Poids libres, recrutement musculaire maximal, exige de la stabilisation",
        R.string.variant_weights_desc,
        0xFFEF4444 // Red
    ),
    BODYWEIGHT(
        "Poids du corps",
        R.string.variant_bodyweight,
        "Aucun équipement, fonctionnel, travaille la coordination et le gainage",
        R.string.variant_bodyweight_desc,
        0xFF10B981 // Green
    ),
    ISOLATION(
        "Isolation",
        R.string.variant_isolation,
        "Cible un seul muscle précisément, finition et sculpture",
        R.string.variant_isolation_desc,
        0xFFF59E0B // Amber
    )
}
