package com.shredcoach.app.data.remote

/** Types d'assiettes standard (diamètre en cm) — indice optionnel pour l'estimation du poids. */
enum class PlateType(val label: String, val diameterCm: String, val usage: String) {
    NONE("Non spécifié", "", ""),
    LARGE("Assiette plate", "25-28", "Plat principal"),
    DEEP("Assiette creuse", "22-24", "Soupe, pâtes, risotto"),
    DESSERT("Assiette à dessert", "19-21", "Dessert, fromage"),
    SMALL("Petite assiette", "15-17", "Pain, amuse-bouche")
}

/** Types de bols standard (volume en ml) — indice optionnel pour l'estimation du poids. */
enum class BowlType(val label: String, val volumeMl: Int) {
    NONE("Non spécifié", 0),
    MINI("Mini bol", 310),
    SMALL("Petit bol", 420),
    STANDARD("Bol standard", 750),
    SALADIER("Saladier", 1500)
}

/**
 * Construit un bloc d'indices (en français) à injecter dans le prompt Gemini/Groq/Mistral.
 * Retourne une chaîne vide si aucun indice n'est fourni → le prompt reste intact, la qualité inchangée.
 */
fun buildMealHintBlock(
    plate: PlateType = PlateType.NONE,
    bowl: BowlType = BowlType.NONE,
    userDescription: String = ""
): String {
    val parts = mutableListOf<String>()

    if (plate != PlateType.NONE) {
        parts += """- CONTENANT CONFIRMÉ : ${plate.label.lowercase()}, diamètre ${plate.diameterCm} cm (${plate.usage.lowercase()}).
  → Surface utile ≈ π×(${plate.diameterCm.split("-").first().trim()}/2)² cm². Estime quel % de cette surface est couvert par chaque aliment, multiplie par l'épaisseur visible, puis par la densité de l'aliment.
  → Exemple : riz couvrant 40% d'une assiette 26cm, épaisseur 2cm ≈ 0.4 × 530cm² × 2cm × 0.7g/cm³ ≈ 296g"""
    }
    if (bowl != BowlType.NONE) {
        val capacityDesc = if (bowl == BowlType.SALADIER)
            "saladier de grande contenance (environ 1.5 à 3 L, soit 1500-3000 ml)"
        else
            "${bowl.label.lowercase()} de capacité ${bowl.volumeMl} ml"
        parts += """- CONTENANT CONFIRMÉ : $capacityDesc.
  → Estime le taux de remplissage (ex: rempli aux 3/4 = 75% du volume).
  → Poids ≈ volume_rempli_ml × densité_aliment (céréales cuites ~0.7, soupe ~1.0, salade ~0.3)
  → Un ${bowl.label.lowercase()} rempli à 80% de pâtes ≈ ${(bowl.volumeMl * 0.8 * 0.8).toInt()}g"""
    }
    if (userDescription.isNotBlank()) {
        parts += """- PRÉCISIONS UTILISATEUR (AUTORITÉ ABSOLUE, prime sur ta lecture visuelle) : "${userDescription.trim()}"
  → Si l'utilisateur mentionne des quantités (ex: "2 œufs", "grosse portion", "double dose"), applique-les LITTÉRALEMENT.
  → Si l'utilisateur identifie un aliment (ex: "igname" pas "pomme de terre"), c'est CETTE identification qui compte."""
    }

    if (parts.isEmpty()) return ""

    return """

═══ INDICES UTILISATEUR (PRIORITAIRES pour l'estimation des quantités) ═══
${parts.joinToString("\n")}

⚠️ RÈGLES IMPÉRATIVES :
1. Ces indices viennent de la personne qui a le plat devant les yeux — ils PRIMENT sur tes hypothèses.
2. Utilise le contenant comme ANCRE DIMENSIONNELLE : calcule surface/volume réels, puis déduis les poids.
3. Ne retombe JAMAIS sur des poids "standard" de recette — calcule à partir de ce que tu VOIS + le contenant confirmé.
4. Recalcule les macros en cohérence avec les poids estimés (weight_g/100 × valeur pour 100g).
""".trimIndent()
}
