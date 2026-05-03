package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "", // "Protéines", "Glucides", "Lipides", "Légumes", "Fruits", "Laitiers", "Snacks", "Boissons"
    val caloriesPer100g: Double, // kcal pour 100g
    val proteinsPer100g: Double, // grammes pour 100g
    val carbsPer100g: Double,
    val fatsPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val defaultPortionGrams: Int = 100, // Portion par défaut en grammes
    val portionLabel: String = "100g", // "1 tranche", "1 cuillère", etc.
    val isCustom: Boolean = false, // Ajouté par l'utilisateur
    val isFavorite: Boolean = false
)
