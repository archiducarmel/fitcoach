package com.shredcoach.app.data.local.dao


import androidx.compose.runtime.Immutable
import androidx.room.*
import com.shredcoach.app.data.local.entity.DailyCheckEntity
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.NutritionGoalEntity
import com.shredcoach.app.data.local.entity.NutritionScheduleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

@Immutable
data class DailyMacros(
    val date: String,
    val totalCalories: Double,
    val totalProteins: Double,
    val totalCarbs: Double,
    val totalFats: Double
)

@Immutable
data class DayTotals(
    val totalCalories: Double,
    val totalProteins: Double,
    val totalCarbs: Double,
    val totalFats: Double
)

@Immutable
data class FoodFrequency(
    val foodId: Long,
    val name: String,
    val count: Int,
    val totalGrams: Int
)

@Dao
interface NutritionDao {
    // ── Nutrition Schedule (existing) ──
    @Query("SELECT * FROM nutrition_schedule WHERE enabled = 1 ORDER BY time ASC")
    fun getEnabledSchedules(): Flow<List<NutritionScheduleEntity>>

    @Query("SELECT * FROM nutrition_schedule ORDER BY time ASC")
    fun getAllSchedules(): Flow<List<NutritionScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: NutritionScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<NutritionScheduleEntity>)

    @Update
    suspend fun updateSchedule(schedule: NutritionScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: NutritionScheduleEntity)

    // ── Daily Checks (existing) ──
    @Query("SELECT * FROM daily_checks WHERE date = :date")
    fun getDailyChecks(date: LocalDate): Flow<List<DailyCheckEntity>>

    @Query("SELECT * FROM daily_checks WHERE nutritionScheduleId = :scheduleId AND date = :date")
    suspend fun getDailyCheck(scheduleId: Long, date: LocalDate): DailyCheckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyCheck(check: DailyCheckEntity)

    @Update
    suspend fun updateDailyCheck(check: DailyCheckEntity)

    @Query("DELETE FROM daily_checks WHERE date < :date")
    suspend fun deleteOldChecks(date: LocalDate)

    // ── Foods ──
    @Query("SELECT * FROM foods ORDER BY isFavorite DESC, name ASC")
    fun getAllFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE name LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    suspend fun searchFoods(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE category = :category ORDER BY name ASC")
    suspend fun getFoodsByCategory(category: String): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getFoodById(id: Long): FoodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoods(foods: List<FoodEntity>)

    @Update
    suspend fun updateFood(food: FoodEntity)

    @Delete
    suspend fun deleteFood(food: FoodEntity)

    @Query("UPDATE foods SET isFavorite = :favorite WHERE id = :foodId")
    suspend fun setFavorite(foodId: Long, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun getFoodCount(): Int

    /** Snapshot global pour le backup. */
    @Query("SELECT * FROM daily_checks ORDER BY date DESC")
    suspend fun getAllDailyChecksOnce(): List<DailyCheckEntity>

    // ── Meal Logs ──
    @Query("SELECT * FROM meal_logs WHERE date = :date ORDER BY mealType ASC, time ASC")
    fun getMealsForDate(date: LocalDate): Flow<List<MealLogEntity>>

    /** Snapshot global de tous les meal logs, utilisé par le backup. */
    @Query("SELECT * FROM meal_logs ORDER BY date DESC, time DESC")
    suspend fun getAllMealLogsOnce(): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs WHERE date = :date ORDER BY mealType ASC")
    suspend fun getMealsForDateOnce(date: LocalDate): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(meal: MealLogEntity): Long

    @Delete
    suspend fun deleteMealLog(meal: MealLogEntity)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteMealLogById(id: Long)

    /** Récupère les foodIds liés à un scan donné. */
    @Query("SELECT foodId FROM meal_logs WHERE scanId = :scanId")
    suspend fun getFoodIdsByScanId(scanId: Long): List<Long>

    /** Supprime des foods par leurs IDs. */
    @Query("DELETE FROM foods WHERE id IN (:ids)")
    suspend fun deleteFoodsByIds(ids: List<Long>)

    /** Trouve le scanId associé à un meal log. */
    @Query("SELECT scanId FROM meal_logs WHERE id = :mealLogId")
    suspend fun getScanIdByMealLogId(mealLogId: Long): Long?

    /** Récupère tous les meal logs liés à un scan. */
    @Query("SELECT * FROM meal_logs WHERE scanId = :scanId")
    suspend fun getMealLogsByScanId(scanId: Long): List<MealLogEntity>

    /** Met à jour la date, l'heure et le type de repas pour tous les logs d'un scan. */
    @Query("UPDATE meal_logs SET date = :date, time = :time, mealType = :mealType WHERE scanId = :scanId")
    suspend fun updateMealLogsDateTime(scanId: Long, date: LocalDate, time: LocalTime, mealType: MealType)

    /** Met à jour quantité + macros + nutri-score d'un MealLog (édition manuelle du grammage d'un plat scanné). */
    @Query("""
        UPDATE meal_logs
        SET quantityGrams = :quantityGrams,
            calories = :calories,
            proteins = :proteins,
            carbs = :carbs,
            fats = :fats,
            nutriScoreGrade = :nutriScoreGrade
        WHERE id = :id
    """)
    suspend fun updateMealLogMacros(
        id: Long,
        quantityGrams: Int,
        calories: Double,
        proteins: Double,
        carbs: Double,
        fats: Double,
        nutriScoreGrade: String
    )

    /** Met à jour le Nutri-Score global pour tous les MealLog d'un scan (propagation après édition d'un ingrédient). */
    @Query("UPDATE meal_logs SET nutriScoreGrade = :nutriScoreGrade WHERE scanId = :scanId")
    suspend fun updateMealLogsNutriScoreByScan(scanId: Long, nutriScoreGrade: String)

    /** Met à jour les macros per-100g d'un Food (quand la composition d'un plat scanné change). */
    @Query("""
        UPDATE foods
        SET caloriesPer100g = :caloriesPer100g,
            proteinsPer100g = :proteinsPer100g,
            carbsPer100g = :carbsPer100g,
            fatsPer100g = :fatsPer100g,
            fiberPer100g = :fiberPer100g,
            defaultPortionGrams = :defaultPortionGrams,
            portionLabel = :portionLabel
        WHERE id = :id
    """)
    suspend fun updateFoodMacros(
        id: Long,
        caloriesPer100g: Double,
        proteinsPer100g: Double,
        carbsPer100g: Double,
        fatsPer100g: Double,
        fiberPer100g: Double,
        defaultPortionGrams: Int,
        portionLabel: String
    )

    // ── Daily Macros ──
    //
    // v45 : les agrégations appliquent désormais le **facteur effectif** du
    // scan parent (multiplicateur de portion + déduction des restes).
    //
    //   factor = MAX(0,
    //              servingMultiplier
    //              - leftoverCalories / totalCalories   (si totalCalories > 0)
    //            )
    //
    // Le facteur est calculé UNE FOIS sur le scan (ratio calorique) et appliqué
    // identiquement à toutes les macros : v1 privilégie la simplicité sur la
    // précision per-macro. Quand le scan est absent (meal_log manuel, scanId
    // NULL) ou n'a aucun modificateur, le facteur = 1.0 → comportement
    // strictement identique à v44.
    //
    // Performance : `LEFT JOIN meal_scans s ON ml.scanId = s.id` est satisfait
    // par l'index implicite sur meal_logs.scanId (déclaré au niveau de l'entité).

    @Query("""
        SELECT date(ml.date) as date,
            COALESCE(SUM(ml.calories * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalCalories,
            COALESCE(SUM(ml.proteins * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalProteins,
            COALESCE(SUM(ml.carbs * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalCarbs,
            COALESCE(SUM(ml.fats * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalFats
        FROM meal_logs ml
        LEFT JOIN meal_scans s ON ml.scanId = s.id
        WHERE date(ml.date) >= :startDate AND date(ml.date) <= :endDate
        GROUP BY date(ml.date)
        ORDER BY date ASC
    """)
    suspend fun getDailyMacros(startDate: LocalDate, endDate: LocalDate): List<DailyMacros>

    @Query("""
        SELECT
            COALESCE(SUM(ml.calories * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalCalories,
            COALESCE(SUM(ml.proteins * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalProteins,
            COALESCE(SUM(ml.carbs * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalCarbs,
            COALESCE(SUM(ml.fats * (
                MAX(0.0,
                    COALESCE(s.servingMultiplier, 1.0) -
                    CASE WHEN COALESCE(s.totalCalories, 0) > 0
                        THEN COALESCE(s.leftoverCalories, 0) * 1.0 / s.totalCalories
                        ELSE 0.0
                    END
                )
            )), 0.0) as totalFats
        FROM meal_logs ml
        LEFT JOIN meal_scans s ON ml.scanId = s.id
        WHERE date(ml.date) = :date
    """)
    suspend fun getDayTotals(date: LocalDate): DayTotals

    // ── Top aliments ──
    @Query("""
        SELECT ml.foodId, f.name, COUNT(ml.id) as count, SUM(ml.quantityGrams) as totalGrams
        FROM meal_logs ml
        INNER JOIN foods f ON ml.foodId = f.id
        WHERE date(ml.date) >= :since
        GROUP BY ml.foodId
        ORDER BY count DESC
        LIMIT 10
    """)
    suspend fun getTopFoods(since: LocalDate): List<FoodFrequency>

    // ── Nutrition Goals ──
    @Query("SELECT * FROM nutrition_goals WHERE id = 1")
    fun getNutritionGoal(): Flow<NutritionGoalEntity?>

    @Query("SELECT * FROM nutrition_goals WHERE id = 1")
    suspend fun getNutritionGoalOnce(): NutritionGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNutritionGoal(goal: NutritionGoalEntity)

    @Update
    suspend fun updateNutritionGoal(goal: NutritionGoalEntity)
}
