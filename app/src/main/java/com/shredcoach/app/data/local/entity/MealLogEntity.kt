package com.shredcoach.app.data.local.entity


import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shredcoach.app.R
import java.time.LocalDate
import java.time.LocalTime

enum class MealType(
    val displayName: String,
    @StringRes val displayNameRes: Int,
    val icon: String,
) {
    BREAKFAST("Petit-déjeuner", R.string.meal_type_breakfast, "🌅"),
    LUNCH("Déjeuner", R.string.meal_type_lunch, "☀️"),
    DINNER("Dîner", R.string.meal_type_dinner, "🌙"),
    SNACK("Snack", R.string.meal_type_snack, "🍎"),
    PRE_WORKOUT("Pré-training", R.string.meal_type_pre_workout, "⚡"),
    POST_WORKOUT("Post-training", R.string.meal_type_post_workout, "💪"),
    SHAKE("Shaker", R.string.meal_type_shake, "🥤")
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
