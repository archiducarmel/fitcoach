package com.shredcoach.app.domain.nutrition

import com.shredcoach.app.data.local.entity.MealType
import java.time.LocalTime

/**
 * Classification du type de repas basée sur l'heure de scan/consommation.
 * Règles (imposées par l'utilisateur) :
 *   - 05:30 → 11:00  : petit déjeuner
 *   - 11:30 → 15:30  : déjeuner
 *   - 16:00 → 18:30  : goûter
 *   - 19:00 → 23:00  : dîner
 *   - entre les plages ou très tardif : grignotage
 *
 * Override : si le repas contient whey/protéine en poudre ET heure entre 12h et 20h,
 * c'est un pre-training snack (prioritaire sur la classification horaire).
 */
object MealTypeClassifier {

    /**
     * Représentation d'un type de repas pour :
     *   - stockage en DB (`mealType: String` sur MealScanEntity) : snake_case
     *   - affichage UI  : `displayName`
     *   - catégorie nutrition : enum `MealType` pour l'auto-ajout au tracking
     */
    data class Category(
        val id: String,           // stocké dans MealScanEntity.mealType
        val displayName: String,  // affiché dans l'UI + notifications (FR — DB-stable)
        @androidx.annotation.StringRes val displayNameRes: Int,
        val trackingType: MealType
    )

    val PETIT_DEJEUNER = Category("petit_dejeuner", "Petit-déjeuner", com.shredcoach.app.R.string.meal_cat_breakfast, MealType.BREAKFAST)
    val DEJEUNER       = Category("dejeuner",       "Déjeuner",       com.shredcoach.app.R.string.meal_cat_lunch, MealType.LUNCH)
    val GOUTER         = Category("gouter",         "Goûter",         com.shredcoach.app.R.string.meal_cat_snack_afternoon, MealType.SNACK)
    val DINER          = Category("diner",          "Dîner",          com.shredcoach.app.R.string.meal_cat_dinner, MealType.DINNER)
    val GRIGNOTAGE     = Category("grignotage",     "Grignotage",     com.shredcoach.app.R.string.meal_cat_snacking, MealType.SNACK)
    val PRETRAINING    = Category("pretraining",    "Pré-training",   com.shredcoach.app.R.string.meal_cat_pretraining, MealType.PRE_WORKOUT)

    /**
     * Classifie un repas en fonction de l'heure de scan et (optionnellement) des noms
     * d'ingrédients/plats pour détecter un pré-training à base de whey.
     *
     * @param time Heure de scan (default = maintenant)
     * @param dishKeywords Liste à plat de tous les noms (plats + ingrédients) du scan,
     *        utilisée pour détecter la whey.
     */
    fun classify(
        time: LocalTime = LocalTime.now(),
        dishKeywords: List<String> = emptyList()
    ): Category {
        // 1) Override whey (12h-20h) → PRE_WORKOUT
        val isWheyWindow = time >= LocalTime.of(12, 0) && time <= LocalTime.of(20, 0)
        if (isWheyWindow && containsWhey(dishKeywords)) {
            return PRETRAINING
        }

        // 2) Classification par plages horaires
        return when {
            time >= LocalTime.of(5, 30)  && time <= LocalTime.of(11, 0)  -> PETIT_DEJEUNER
            time >= LocalTime.of(11, 30) && time <= LocalTime.of(15, 30) -> DEJEUNER
            time >= LocalTime.of(16, 0)  && time <= LocalTime.of(18, 30) -> GOUTER
            time >= LocalTime.of(19, 0)  && time <= LocalTime.of(23, 0)  -> DINER
            else -> GRIGNOTAGE
        }
    }

    /** Detecte whey/protéine en poudre dans les noms d'aliments. */
    private fun containsWhey(keywords: List<String>): Boolean {
        val normalized = keywords.joinToString(" ") { it.lowercase() }
        return listOf("whey", "protéine", "proteine", "caséine", "caseine",
            "shaker", "isolat", "protein powder", "prot en poudre")
            .any { normalized.contains(it) }
    }

    /** Retrouve une category à partir de son id stocké. Fallback = GRIGNOTAGE. */
    fun fromId(id: String?): Category = when (id?.lowercase()) {
        "petit_dejeuner" -> PETIT_DEJEUNER
        "dejeuner" -> DEJEUNER
        "gouter" -> GOUTER
        "diner", "dîner" -> DINER
        // "shaker" legacy (ancien LLM) → pretraining car c'est la sémantique intentée
        "pretraining", "pre_workout", "shaker" -> PRETRAINING
        "grignotage", "collation", "snack" -> GRIGNOTAGE
        else -> GRIGNOTAGE
    }
}
