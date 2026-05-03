package com.shredcoach.app.domain.model

enum class MuscleGroup(val displayName: String, val orderIndex: Int) {
    WARMUP("Échauffement", 0),
    QUADS("Quadriceps / Fessiers", 1),
    HAMSTRINGS("Ischio-jambiers", 2),
    CHEST("Pectoraux", 3),
    CHEST_UPPER("Pectoraux supérieurs", 4),
    BACK_WIDTH("Dos (largeur)", 5),
    BACK_THICKNESS("Dos (épaisseur)", 6),
    SHOULDERS("Épaules", 7),
    BICEPS("Biceps", 8),
    TRICEPS("Triceps", 9),
    ABS_UPPER("Abdos supérieurs", 10),
    ABS_LOWER("Abdos inférieurs & Obliques", 11),
    CALVES("Mollets", 12),
    ADDUCTORS("Adducteurs / Abducteurs", 13),
    TRAPS("Trapèzes", 14),
    FOREARMS("Avant-bras", 15),
    LOWER_BACK("Lombaires", 16),
    CARDIO("Cardio", 17);

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
