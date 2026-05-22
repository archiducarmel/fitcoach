package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.MealScanEntity

/**
 * Catégorie d'indice glycémique selon les seuils ISO 26642:2010 (standard
 * international, repris par l'Université de Sydney qui maintient la base GI
 * officielle).
 *
 *  - LOW    < 55  : IG bas — fruits, légumes, légumineuses, céréales complètes
 *  - MEDIUM 55-69 : IG moyen — pain complet, riz basmati, certains fruits mûrs
 *  - HIGH   ≥ 70  : IG élevé — pain blanc, pommes de terre, sucre, sodas
 *  - UNKNOWN     : LLM n'a pas pu estimer (legacy scans pre-v49, recettes complexes)
 */
enum class GICategory {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN;

    companion object {
        /** Catégorise un GI numérique selon les seuils ISO. */
        fun fromGi(gi: Int?): GICategory = when {
            gi == null -> UNKNOWN
            gi < 55 -> LOW
            gi < 70 -> MEDIUM
            else -> HIGH
        }

        /** Parse une string persistée en DB. Tolérant (UNKNOWN si null/invalide). */
        fun fromString(value: String?): GICategory = when (value?.uppercase()) {
            "LOW" -> LOW
            "MEDIUM" -> MEDIUM
            "HIGH" -> HIGH
            else -> UNKNOWN
        }
    }
}

/**
 * Catégorie de charge glycémique (GL) par repas. Seuils standard (Foster-Powell
 * et al., American Journal of Clinical Nutrition 2002) :
 *
 *  - LOW    ≤ 10  : charge faible
 *  - MEDIUM 11-19 : charge modérée
 *  - HIGH   ≥ 20  : charge élevée — pic glycémique probable
 */
enum class GLCategory {
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN;

    companion object {
        fun fromGl(gl: Double?): GLCategory = when {
            gl == null -> UNKNOWN
            gl <= 10.0 -> LOW
            gl < 20.0 -> MEDIUM
            else -> HIGH
        }
    }
}

/**
 * Confidence de l'estimation GI fournie par le LLM. Persisté tel quel, utilisé
 * pour graduer l'affichage (badge plein vs estompé).
 */
enum class GIConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): GIConfidence = when (value?.uppercase()) {
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> UNKNOWN
        }
    }
}

/**
 * Helper pur pour les calculs d'indice glycémique (GI) et de charge glycémique
 * (GL) sur un scan repas.
 *
 * **Modèle nutritionnel** :
 *  - GI (Glycemic Index) = intrinsèque au plat, indépendant de la portion. Une
 *    pomme reste IG 36 que tu en manges une ou trois.
 *  - GL (Glycemic Load) = (GI × carbs_g) / 100. Scale linéairement avec les
 *    glucides effectivement consommés → donc avec les modificateurs v45
 *    (×reprises et restes).
 *
 * **Source de vérité unique** (cf. [[feedback_single_source_of_truth]]) : tout
 * affichage GI/GL dans l'app DOIT passer par les helpers ci-dessous. Les
 * calculs raw inline sont interdits — un futur changement de modèle (ex :
 * passage à GL moyen pondéré) doit toucher un seul point.
 *
 * **Bornes pratiques** :
 *  - GI ∈ [0, 110] : 0 = glucose pur n'existe pas en repas, 110 = maltose pur
 *  - GL > 0 si carbs > 0 et GI connu
 */
object GlycemicMath {

    /** Seuil IG bas (strict) — sous ce seuil, catégorie LOW. */
    const val GI_LOW_MAX = 55

    /** Seuil IG haut (strict) — au-dessus ou égal, catégorie HIGH. */
    const val GI_HIGH_MIN = 70

    /** Seuil GL bas par repas. */
    const val GL_LOW_MAX = 10.0

    /** Seuil GL haut par repas. */
    const val GL_HIGH_MIN = 20.0

    /**
     * GI effectif d'un scan : valeur intrinsèque non scalée. Retourne null si
     * le LLM n'a pas estimé (scan legacy ou recette trop incertaine).
     */
    fun effectiveGi(scan: MealScanEntity): Int? = scan.glycemicIndex

    /**
     * GL effectif d'un scan, prenant en compte les modificateurs de portion.
     *
     *   GL_effectif = (GI × carbs_effectifs) / 100
     *               = (GI × carbs_raw × factor) / 100
     *               = GL_raw × factor
     *
     * Retourne null si GI manquant ou carbs = 0. Cohérent avec [GICategory.UNKNOWN]
     * côté UI.
     */
    fun effectiveGl(scan: MealScanEntity): Double? {
        val gi = scan.glycemicIndex ?: return null
        val effectiveCarbs = MealScanModifierMath.effectiveCarbs(scan)
        if (effectiveCarbs <= 0.0) return null
        return (gi * effectiveCarbs / 100.0).coerceAtLeast(0.0)
    }

    /**
     * GL raw (sans modifier) d'un scan. Utile pour persister la valeur stable
     * en DB et pour les comparaisons inter-repas.
     */
    fun rawGl(scan: MealScanEntity): Double? {
        val gi = scan.glycemicIndex ?: return null
        if (scan.totalCarbs <= 0.0) return null
        return (gi * scan.totalCarbs / 100.0).coerceAtLeast(0.0)
    }

    /**
     * Catégorie GI persistée (ou re-dérivée si null en DB). Permet de
     * gracefully tolérer un legacy scan où le LLM a fourni le GI mais pas
     * encore la catégorie (cas hypothétique de migration partielle).
     */
    fun category(scan: MealScanEntity): GICategory {
        // Préférence : catégorie persistée (autoritative — calculée au moment
        // du scan avec potentiellement plus de contexte). Fallback : redériver
        // depuis le GI numérique.
        val persisted = GICategory.fromString(scan.giCategory)
        if (persisted != GICategory.UNKNOWN) return persisted
        return GICategory.fromGi(scan.glycemicIndex)
    }

    /** Catégorie GL calculée à partir du GL effectif. */
    fun glCategory(scan: MealScanEntity): GLCategory =
        GLCategory.fromGl(effectiveGl(scan))

    /** Confidence du GI (HIGH/MEDIUM/LOW/UNKNOWN). */
    fun confidence(scan: MealScanEntity): GIConfidence =
        GIConfidence.fromString(scan.giConfidence)

    /**
     * Agrégation pondérée par les carbs sur une liste de plats (dish-level
     * GI). Utilisé après parsing LLM pour calculer le GI au niveau du scan :
     *
     *   GI_scan = Σ(GI_dish × carbs_dish) / Σ(carbs_dish)
     *
     * Symétrique avec la formule de GL additive (Σ GL_dish = GL_scan), donc
     * cohérent : reconstruire GL_scan depuis GI_scan + carbs_total redonne la
     * même valeur que sommer les GL_dish individuels.
     *
     * **Robustesse** :
     *  - Liste vide ou tous carbs = 0 → null (rien à pondérer)
     *  - Plats sans GI (null) → exclus de la moyenne (pas de pénalité)
     *  - Si AUCUN plat n'a de GI → null (incertitude totale)
     */
    fun weightedAverageGi(dishGis: List<Pair<Int?, Double>>): Int? {
        val withGi = dishGis.filter { (gi, carbs) -> gi != null && carbs > 0.0 }
        if (withGi.isEmpty()) return null
        var weightedSum = 0.0
        var totalCarbs = 0.0
        for ((gi, carbs) in withGi) {
            weightedSum += gi!! * carbs
            totalCarbs += carbs
        }
        if (totalCarbs <= 0.0) return null
        return (weightedSum / totalCarbs).toInt().coerceIn(0, 110)
    }

    /**
     * Détermine la confidence agrégée d'un scan multi-plat. Règle :
     *  - Si TOUS les plats sont HIGH confidence → HIGH
     *  - Si au moins un est LOW → LOW (chaîne de fiabilité au plus faible)
     *  - Sinon → MEDIUM
     *  - Liste vide ou tous UNKNOWN → UNKNOWN
     */
    fun aggregateConfidence(confidences: List<GIConfidence>): GIConfidence {
        val known = confidences.filter { it != GIConfidence.UNKNOWN }
        if (known.isEmpty()) return GIConfidence.UNKNOWN
        if (known.any { it == GIConfidence.LOW }) return GIConfidence.LOW
        if (known.all { it == GIConfidence.HIGH }) return GIConfidence.HIGH
        return GIConfidence.MEDIUM
    }
}
