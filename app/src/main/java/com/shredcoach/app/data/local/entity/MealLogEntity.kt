package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

enum class MealType(val displayName: String, val icon: String) {
    BREAKFAST("Petit-déjeuner", "\uD83C\uDF05"),
    LUNCH("Déjeuner", "☀\uFE0F"),
    DINNER("Dîner", "\uD83C\uDF19"),
    SNACK("Snack", "\uD83C\uDF4E"),
    PRE_WORKOUT("Pré-training", "⚡"),
    POST_WORKOUT("Post-training", "\uD83D\uDCAA"),
    SHAKE("Shaker", "\uD83E\uDD64")
}

@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MealScanEntity::class, parentColumns = ["id"], childColumns = ["scanId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("foodId"), Index("date"), Index("scanId")]
)
@Immutable
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val date: LocalDate,
    val mealType: MealType,
    val quantityGrams: Int,
    val calories: Double = 0.0,
    val proteins: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val time: LocalTime? = null,
    val scanId: Long? = null, // Lien vers MealScanEntity si issu d'un scan
    val nutriScoreGrade: String = "" // "A".."E" si calculé, vide sinon
)
