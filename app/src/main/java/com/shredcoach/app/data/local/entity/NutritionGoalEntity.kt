package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nutrition_goals")
data class NutritionGoalEntity(
    @PrimaryKey val id: Long = 1, // Singleton
    val targetCalories: Int = 2200,
    val targetProteins: Int = 180, // grammes
    val targetCarbs: Int = 220,
    val targetFats: Int = 70,
    val weight: Double = 80.0, // kg (pour calcul TDEE)
    val height: Int = 178, // cm
    val age: Int = 30,
    val activityLevel: Int = 3, // 1=sédentaire, 2=léger, 3=modéré, 4=actif, 5=très actif
    val sex: String = "M",
    val goal: String = "SHRED" // FitnessGoal.name
)
