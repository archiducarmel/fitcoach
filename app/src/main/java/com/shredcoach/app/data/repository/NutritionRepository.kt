package com.shredcoach.app.data.repository

import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.seed.FoodSeedData
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NutritionRepository @Inject constructor(
    private val nutritionDao: NutritionDao
) {
    // ── Foods ──
    fun getAllFoods(): Flow<List<FoodEntity>> = nutritionDao.getAllFoods()
    suspend fun searchFoods(query: String) = nutritionDao.searchFoods(query)
    suspend fun getFoodById(id: Long) = nutritionDao.getFoodById(id)
    suspend fun insertFood(food: FoodEntity) = nutritionDao.insertFood(food)
    suspend fun deleteFood(food: FoodEntity) = nutritionDao.deleteFood(food)
    suspend fun setFavorite(foodId: Long, fav: Boolean) = nutritionDao.setFavorite(foodId, fav)

    // ── Meal Logs ──
    fun getMealsForDate(date: LocalDate) = nutritionDao.getMealsForDate(date)
    suspend fun insertMealLog(meal: MealLogEntity) = nutritionDao.insertMealLog(meal)
    suspend fun deleteMealLog(id: Long) = nutritionDao.deleteMealLogById(id)
    suspend fun getFoodIdsByScanId(scanId: Long) = nutritionDao.getFoodIdsByScanId(scanId)
    suspend fun deleteFoodsByIds(ids: List<Long>) = nutritionDao.deleteFoodsByIds(ids)
    suspend fun getScanIdByMealLogId(mealLogId: Long) = nutritionDao.getScanIdByMealLogId(mealLogId)
    suspend fun getMealLogsByScanId(scanId: Long) = nutritionDao.getMealLogsByScanId(scanId)
    suspend fun updateMealLogsDateTime(scanId: Long, date: java.time.LocalDate, time: java.time.LocalTime, mealType: com.shredcoach.app.data.local.entity.MealType) =
        nutritionDao.updateMealLogsDateTime(scanId, date, time, mealType)

    suspend fun updateMealLogMacros(
        id: Long,
        quantityGrams: Int,
        calories: Double,
        proteins: Double,
        carbs: Double,
        fats: Double,
        nutriScoreGrade: String
    ) = nutritionDao.updateMealLogMacros(id, quantityGrams, calories, proteins, carbs, fats, nutriScoreGrade)

    suspend fun updateMealLogsNutriScoreByScan(scanId: Long, nutriScoreGrade: String) =
        nutritionDao.updateMealLogsNutriScoreByScan(scanId, nutriScoreGrade)

    suspend fun updateFoodMacros(
        id: Long,
        caloriesPer100g: Double,
        proteinsPer100g: Double,
        carbsPer100g: Double,
        fatsPer100g: Double,
        fiberPer100g: Double,
        defaultPortionGrams: Int,
        portionLabel: String
    ) = nutritionDao.updateFoodMacros(id, caloriesPer100g, proteinsPer100g, carbsPer100g, fatsPer100g, fiberPer100g, defaultPortionGrams, portionLabel)

    // ── Macros ──
    suspend fun getDayTotals(date: LocalDate): com.shredcoach.app.data.local.dao.DayTotals = nutritionDao.getDayTotals(date)
    suspend fun getDailyMacros(start: LocalDate, end: LocalDate) = nutritionDao.getDailyMacros(start, end)
    suspend fun getTopFoods(since: LocalDate) = nutritionDao.getTopFoods(since)

    // ── Goals ──
    fun getNutritionGoal() = nutritionDao.getNutritionGoal()
    suspend fun getNutritionGoalOnce() = nutritionDao.getNutritionGoalOnce()
    suspend fun saveNutritionGoal(goal: NutritionGoalEntity) = nutritionDao.insertNutritionGoal(goal)

    // ── Seed ──
    suspend fun seedFoodsIfEmpty() {
        if (nutritionDao.getFoodCount() == 0) {
            nutritionDao.insertFoods(FoodSeedData.getAllFoods())
        }
    }

    // Nutrition Schedule
    fun getEnabledSchedules(): Flow<List<NutritionScheduleEntity>> =
        nutritionDao.getEnabledSchedules()

    fun getAllSchedules(): Flow<List<NutritionScheduleEntity>> =
        nutritionDao.getAllSchedules()

    suspend fun insertSchedule(schedule: NutritionScheduleEntity): Long =
        nutritionDao.insertSchedule(schedule)

    suspend fun insertSchedules(schedules: List<NutritionScheduleEntity>) =
        nutritionDao.insertSchedules(schedules)

    suspend fun updateSchedule(schedule: NutritionScheduleEntity) =
        nutritionDao.updateSchedule(schedule)

    suspend fun deleteSchedule(schedule: NutritionScheduleEntity) =
        nutritionDao.deleteSchedule(schedule)

    // Daily Checks
    fun getDailyChecks(date: LocalDate): Flow<List<DailyCheckEntity>> =
        nutritionDao.getDailyChecks(date)

    suspend fun getDailyCheck(scheduleId: Long, date: LocalDate): DailyCheckEntity? =
        nutritionDao.getDailyCheck(scheduleId, date)

    suspend fun insertDailyCheck(check: DailyCheckEntity) =
        nutritionDao.insertDailyCheck(check)

    suspend fun updateDailyCheck(check: DailyCheckEntity) =
        nutritionDao.updateDailyCheck(check)

    suspend fun deleteOldChecks(date: LocalDate) =
        nutritionDao.deleteOldChecks(date)
}
