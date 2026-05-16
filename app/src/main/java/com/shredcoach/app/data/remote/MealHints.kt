package com.shredcoach.app.data.remote

import androidx.annotation.StringRes
import com.shredcoach.app.R
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary

/** Types d'assiettes standard (diamètre en cm) — indice optionnel pour l'estimation du poids. */
enum class PlateType(
    val label: String,
    @StringRes val labelRes: Int,
    val diameterCm: String,
    val usage: String,
) {
    NONE("Non spécifié", R.string.plate_type_none, "", ""),
    LARGE("Assiette plate", R.string.plate_type_large, "25-28", "Plat principal"),
    DEEP("Assiette creuse", R.string.plate_type_deep, "22-24", "Soupe, pâtes, risotto"),
    DESSERT("Assiette à dessert", R.string.plate_type_dessert, "19-21", "Dessert, fromage"),
    SMALL("Petite assiette", R.string.plate_type_small, "15-17", "Pain, amuse-bouche")
}

/** Types de bols standard (volume en ml) — indice optionnel pour l'estimation du poids. */
enum class BowlType(
    val label: String,
    @StringRes val labelRes: Int,
    val volumeMl: Int,
) {
    NONE("Non spécifié", R.string.bowl_type_none, 0),
    MINI("Mini bol", R.string.bowl_type_mini, 310),
    SMALL("Petit bol", R.string.bowl_type_small, 420),
    STANDARD("Bol standard", R.string.bowl_type_standard, 750),
    SALADIER("Saladier", R.string.bowl_type_saladier, 1500)
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

/**
 * Variante adaptée au mode TEXTE (pas de photo) : le LLM ne voit rien, donc
 * les indices servent à dimensionner les portions à partir d'un référentiel
 * de contenant. Les références à "ce que tu vois" sont retirées et
 * remplacées par des règles d'estimation de remplissage standard.
 */
fun buildMealHintBlockForText(
    plate: PlateType = PlateType.NONE,
    bowl: BowlType = BowlType.NONE
): String {
    val parts = mutableListOf<String>()

    if (plate != PlateType.NONE) {
        val avgDiameter = plate.diameterCm.split("-").let {
            ((it.first().trim().toIntOrNull() ?: 25) + (it.last().trim().toIntOrNull() ?: 27)) / 2
        }
        parts += """- CONTENANT UTILISÉ : ${plate.label.lowercase()}, diamètre ${plate.diameterCm} cm (${plate.usage.lowercase()}).
  → Surface utile ≈ π×(${avgDiameter}/2)² ≈ ${(Math.PI * (avgDiameter / 2.0) * (avgDiameter / 2.0)).toInt()} cm².
  → Référentiel de portion typique : assiette plate bien remplie ≈ 350-500 g, demi-remplie ≈ 200-300 g, simple accompagnement ≈ 100-150 g."""
    }
    if (bowl != BowlType.NONE) {
        val capacityDesc = if (bowl == BowlType.SALADIER)
            "saladier de grande contenance (1.5 à 3 L, soit 1500-3000 ml)"
        else
            "${bowl.label.lowercase()} de capacité ${bowl.volumeMl} ml"
        val refPasta = if (bowl == BowlType.SALADIER) 1500 else (bowl.volumeMl * 0.8 * 0.8).toInt()
        parts += """- CONTENANT UTILISÉ : $capacityDesc.
  → Référentiel de remplissage : 80% rempli en pâtes/riz cuits ≈ ${refPasta} g, en soupe ≈ ${(bowl.volumeMl * 0.8).toInt()} g, en salade ≈ ${(bowl.volumeMl * 0.8 * 0.3).toInt()} g.
  → Si l'utilisateur dit "1 bol" sans préciser, suppose un remplissage standard à 75-85%."""
    }

    if (parts.isEmpty()) return ""

    return """

═══ INDICES SUR LE CONTENANT (calibrent les portions) ═══
${parts.joinToString("\n")}

⚠️ RÈGLES :
1. Les quantités EXPLICITES de la description (g, unités, "2 œufs") priment toujours sur ces indices.
2. Quand la description est vague ("1 portion", "1 assiette"), utilise ces indices pour dimensionner.
3. Recalcule les macros en cohérence (weight_g/100 × valeur pour 100g).
""".trimIndent()
}

/**
 * Bloc de contexte glycémique injecté dans le prompt MealScanner pour
 * calibrer les recommandations nutrition selon le pattern CGM du user.
 *
 * Retourne une chaîne vide si insuffisamment de data (pas de bruit dans le
 * prompt). Sinon ~3-5 lignes minimalistes — on n'envahit pas le prompt
 * d'analyse de repas avec de l'endocrino lourd.
 *
 * **Comportement attendu** : le LLM ajustera le verdict / les recommandations
 * en cohérence ("ce repas pourrait pic ta glycémie — alterne avec basmati").
 */
fun buildGlucoseHintBlock(summary: GlucoseWindowSummary): String {
    if (summary.daysCovered < 3 || summary.pattern == GlucosePattern.INSUFFICIENT_DATA) return ""
    val patternHint = when (summary.pattern) {
        GlucosePattern.HYPO_RISK -> "⚠ Risque hypoglycémique récent — privilégier les apports glucidiques réguliers et complexes."
        GlucosePattern.HIGH_VARIABILITY -> "⚠ Variabilité glycémique élevée — favoriser charge glycémique modérée et fibres."
        GlucosePattern.POSTPRANDIAL_SPIKES -> "⚠ Pics postprandiaux répétés — recommander aliments à index glycémique bas et ordre des bouchées (légumes/protéines d'abord)."
        GlucosePattern.DAWN_PHENOMENON -> "Élévation matinale chronique — réduire les glucides au petit-déjeuner."
        GlucosePattern.RISING_TREND -> "Tendance glycémique haussière sur 30j — vigilance carbs."
        GlucosePattern.STABLE_OPTIMAL -> "Glycémie stable optimale — pas d'ajustement spécifique."
        GlucosePattern.FALLING_TREND -> "Tendance glycémique baissière favorable."
        else -> "Régulation glycémique correcte."
    }
    val parts = mutableListOf<String>()
    summary.avgMgdl?.let { parts += "moy mg/dL : ${"%.0f".format(it)}" }
    summary.avgTirPct?.let { parts += "TIR : ${"%.0f".format(it)}%" }
    summary.avgCv?.let { parts += "CV : ${"%.1f".format(it)}%" }
    return """

═══ CONTEXTE GLYCÉMIQUE 30j (Dr. Glykos) ═══
${parts.joinToString(" · ")}
Pattern : ${summary.pattern.name}. $patternHint
→ Intègre cette dimension dans tes recommandations nutrition (charge glycémique,
  timing carbs, ordre des bouchées) sans diagnostic médical.
""".trimIndent()
}
