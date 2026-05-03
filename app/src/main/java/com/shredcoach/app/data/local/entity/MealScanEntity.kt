package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "meal_scans")
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
    val nutriScoreGrade: String = "" // "A", "B", "C", "D", "E" — calculé via algorithme Nutri-Score
)
