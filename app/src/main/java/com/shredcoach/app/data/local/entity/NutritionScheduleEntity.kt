package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "nutrition_schedule")
data class NutritionScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: NutritionType,
    val time: LocalTime,
    val name: String, // Ex: "Petit-déjeuner", "Shaker protéiné"
    val enabled: Boolean = true,
    val notificationEnabled: Boolean = true
)

enum class NutritionType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    SHAKE,
    WATER
}
