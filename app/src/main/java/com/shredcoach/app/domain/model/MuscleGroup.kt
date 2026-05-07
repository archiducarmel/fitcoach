package com.shredcoach.app.domain.model

import androidx.annotation.StringRes
import com.shredcoach.app.R

/**
 * Groupes musculaires ciblés par les exercices.
 *
 * **Pourquoi 2 champs displayName / displayNameRes** :
 *  - `displayName: String` (FR figé) : conservé pour la sérialisation DB
 *    (champs `routineFocus`, prompts LLM, logs analytics) où la locale ne
 *    s'applique pas — la valeur reste stable côté backend/persistance.
 *  - `displayNameRes: Int` (@StringRes) : utilisé partout en UI via
 *    [localizedName] / `stringResource()` pour s'adapter à la locale courante.
 *
 * **Migration** : à terme `displayName` sera supprimé pour `displayNameRes`
 * partout, mais l'opération nécessite de migrer la sérialisation prompt LLM
 * (Phase 4) — pour l'instant les 2 cohabitent sans conflit.
 */
enum class MuscleGroup(
    val displayName: String,
    @StringRes val displayNameRes: Int,
    val orderIndex: Int,
) {
    WARMUP("Échauffement", R.string.muscle_warmup, 0),
    QUADS("Quadriceps / Fessiers", R.string.muscle_quads, 1),
    HAMSTRINGS("Ischio-jambiers", R.string.muscle_hamstrings, 2),
    CHEST("Pectoraux", R.string.muscle_chest, 3),
    CHEST_UPPER("Pectoraux supérieurs", R.string.muscle_chest_upper, 4),
    BACK_WIDTH("Dos (largeur)", R.string.muscle_back_width, 5),
    BACK_THICKNESS("Dos (épaisseur)", R.string.muscle_back_thickness, 6),
    SHOULDERS("Épaules", R.string.muscle_shoulders, 7),
    BICEPS("Biceps", R.string.muscle_biceps, 8),
    TRICEPS("Triceps", R.string.muscle_triceps, 9),
    ABS_UPPER("Abdos supérieurs", R.string.muscle_abs_upper, 10),
    ABS_LOWER("Abdos inférieurs & Obliques", R.string.muscle_abs_lower, 11),
    CALVES("Mollets", R.string.muscle_calves, 12),
    ADDUCTORS("Adducteurs / Abducteurs", R.string.muscle_adductors, 13),
    TRAPS("Trapèzes", R.string.muscle_traps, 14),
    FOREARMS("Avant-bras", R.string.muscle_forearms, 15),
    LOWER_BACK("Lombaires", R.string.muscle_lower_back, 16),
    CARDIO("Cardio", R.string.muscle_cardio, 17);

    companion object {
        fun getMainGroups(): List<MuscleGroup> = listOf(
            QUADS, CHEST, BACK_WIDTH, SHOULDERS, BICEPS, ABS_UPPER
        )

        fun getOptionalGroups(): List<MuscleGroup> = listOf(
            HAMSTRINGS, CHEST_UPPER, BACK_THICKNESS, TRICEPS, ABS_LOWER, CALVES,
            ADDUCTORS, TRAPS, FOREARMS, LOWER_BACK
        )
    }
}
