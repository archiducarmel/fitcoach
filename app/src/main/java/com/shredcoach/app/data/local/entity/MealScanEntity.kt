package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "meal_scans")
@Immutable
data class MealScanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val mealType: String = "dejeuner", // petit_dejeuner, dejeuner, gouter, diner, collation, shaker, grignotage
    val dishName: String = "",
    val cuisine: String = "",
    val totalCalories: Int = 0,
    val totalProteins: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val totalFibers: Double = 0.0,
    val totalWeight: Int = 0,
    val healthScore: Int = 0,
    val verdict: String = "",
    val ingredientCount: Int = 0,
    val resultJson: String = "",
    val photoPath: String? = null, // Chemin fichier photo (internal storage)
    val addedToTracking: Boolean = false,
    val nutriScoreGrade: String = "", // "A", "B", "C", "D", "E" — calculé via algorithme Nutri-Score
    // ── v45 : Modificateurs de portion (× reprises) + restes ──
    //
    // `servingMultiplier` : multiplicateur de portion appliqué au scan
    // (1.0 = normal, 1.5 / 2.0 / 3.0 = a repris une demi, double, triple portion).
    // Stocké sur le scan (pas sur les meal_logs) : c'est une décision sémantique
    // au niveau du "repas pris", pas de l'ingrédient individuel. Tous les
    // meal_logs liés à ce scan héritent du facteur.
    //
    // `leftover*` : restes rescannés après le repas. `leftoverCalories` est
    // déduit du total. On stocke les macros séparément pour permettre une
    // déduction proportionnelle par macro côté UI/agrégation, mais l'agrégation
    // SQL utilise un facteur unique calorique pour la simplicité (cf.
    // NutritionDao.getDayTotals).
    //
    // **Invariants v45** :
    //  - servingMultiplier ∈ [0.25, 10.0]  (clampé côté Repository)
    //  - leftoverCalories ≥ 0
    //  - leftoverCalories ≤ totalCalories * servingMultiplier (sinon le repas
    //    "n'a pas existé" → suspect, clampé à 0 côté UI)
    val servingMultiplier: Float = 1f,
    val leftoverPhotoPath: String? = null,
    val leftoverCalories: Int = 0,
    val leftoverProteins: Double = 0.0,
    val leftoverCarbs: Double = 0.0,
    val leftoverFats: Double = 0.0,
    val leftoverFibers: Double = 0.0,
    val leftoverWeight: Int = 0,
    val leftoverResultJson: String = "",
    val leftoverScannedAt: LocalDateTime? = null,
)
