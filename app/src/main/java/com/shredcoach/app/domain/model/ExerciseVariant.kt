package com.shredcoach.app.domain.model

enum class ExerciseVariant(
    val displayName: String,
    val description: String,
    val color: Long
) {
    MACHINE(
        "Machine",
        "Guidé, sécurisé, idéal pour débuter et charger lourd en toute sécurité",
        0xFF3B82F6 // Blue
    ),
    WEIGHTS(
        "Haltères / Barre",
        "Poids libres, recrutement musculaire maximal, exige de la stabilisation",
        0xFFEF4444 // Red
    ),
    BODYWEIGHT(
        "Poids du corps",
        "Aucun équipement, fonctionnel, travaille la coordination et le gainage",
        0xFF10B981 // Green
    ),
    ISOLATION(
        "Isolation",
        "Cible un seul muscle précisément, finition et sculpture",
        0xFFF59E0B // Amber
    )
}
