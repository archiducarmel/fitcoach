package com.shredcoach.app.presentation.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.ScheduledWorkoutRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.nutrition.TdeeCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class MealWithFood(
    val meal: MealLogEntity,
    val food: FoodEntity,
    val photoPath: String? = null // Photo du scan (si repas issu d'un scan)
)

data class NutritionState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealWithFood> = emptyList(),
    val totalCalories: Double = 0.0,
    val totalProteins: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val goal: NutritionGoalEntity = NutritionGoalEntity(),
    val adjustedTargetCalories: Int = 2200, // Target ajusté jour training vs repos
    val isTrainingDay: Boolean = false,
    // Add meal dialog
    val showAddMeal: Boolean = false,
    val selectedMealType: MealType = MealType.LUNCH,
    val searchQuery: String = "",
    val searchResults: List<FoodEntity> = emptyList(),
    val selectedFood: FoodEntity? = null,
    val quantity: String = "",
    // Top foods
    val topFoods: List<TopFoodDisplay> = emptyList(),
    val isLoading: Boolean = true
)

data class TopFoodDisplay(val name: String, val count: Int, val totalGrams: Int)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repo: NutritionRepository,
    private val mealScanDao: MealScanDao,
    private val scheduledRepo: ScheduledWorkoutRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionState())
    val state: StateFlow<NutritionState> = _state.asStateFlow()

    /** Jours d'entraînement préférés (1=Lun … 7=Dim), chargés une fois au init. */
    private var workoutDays: Set<Int> = setOf(1, 3, 5)

    init {
        viewModelScope.launch { repo.seedFoodsIfEmpty() }
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            workoutDays = profile?.workoutDays ?: setOf(1, 3, 5)
            // Recalculer après chargement du profil (workoutDays maintenant correct)
            recalcDailyTarget(_state.value.selectedDate)
        }
        loadGoal()
        loadDay(LocalDate.now())
        loadTopFoods()
    }

    fun refresh() {
        loadGoal()
        loadDay(_state.value.selectedDate)
        loadTopFoods()
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        loadDay(date)
        recalcDailyTarget(date)
    }

    fun previousDay() = selectDate(_state.value.selectedDate.minusDays(1))
    fun nextDay() = selectDate(_state.value.selectedDate.plusDays(1))

    // ── Chargement données du jour ──
    private fun loadDay(date: LocalDate) {
        viewModelScope.launch {
            repo.getMealsForDate(date).collect { meals ->
                val mealsWithFood = meals.mapNotNull { meal ->
                    repo.getFoodById(meal.foodId)?.let { food ->
                        val photo = meal.scanId?.let { sid -> mealScanDao.getScanById(sid)?.photoPath }
                        MealWithFood(meal, food, photoPath = photo)
                    }
                }
                val totals = repo.getDayTotals(date)
                _state.update {
                    it.copy(
                        meals = mealsWithFood,
                        totalCalories = totals.totalCalories,
                        totalProteins = totals.totalProteins,
                        totalCarbs = totals.totalCarbs,
                        totalFats = totals.totalFats,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadGoal() {
        viewModelScope.launch {
            repo.getNutritionGoal().collect { goal ->
                if (goal != null) {
                    _state.update { it.copy(goal = goal) }
                    recalcDailyTarget(_state.value.selectedDate)
                }
            }
        }
    }

    /**
     * Détermine si [date] est un jour d'entraînement (séance planifiée OU jour habituel)
     * et ajuste le target calories en conséquence.
     */
    private fun recalcDailyTarget(date: LocalDate) {
        viewModelScope.launch {
            val scheduled = scheduledRepo.getBetweenOnce(date, date)
            val hasScheduled = scheduled.any { it.status == "PLANNED" || it.status == "COMPLETED" }
            // Fallback : jours d'entraînement habituels du profil
            val isTraining = hasScheduled || (date.dayOfWeek.value in workoutDays)

            val goal = _state.value.goal
            val adjusted = TdeeCalculator.dailyAdjustedCalories(
                weeklyBaseTarget = goal.targetCalories,
                isTrainingDay = isTraining,
                trainingDaysPerWeek = workoutDays.size
            )
            _state.update { it.copy(adjustedTargetCalories = adjusted, isTrainingDay = isTraining) }
        }
    }

    private fun loadTopFoods() {
        viewModelScope.launch {
            val top = repo.getTopFoods(LocalDate.now().minusDays(30))
            _state.update { it.copy(topFoods = top.map { f -> TopFoodDisplay(f.name, f.count, f.totalGrams) }) }
        }
    }

    // ── Dialog ajout repas ──
    fun openAddMeal(mealType: MealType) {
        _state.update { it.copy(showAddMeal = true, selectedMealType = mealType, searchQuery = "", searchResults = emptyList(), selectedFood = null, quantity = "") }
    }

    fun closeAddMeal() { _state.update { it.copy(showAddMeal = false) } }

    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            viewModelScope.launch {
                val results = repo.searchFoods(query)
                _state.update { it.copy(searchResults = results) }
            }
        } else {
            _state.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun selectFood(food: FoodEntity) {
        _state.update { it.copy(selectedFood = food, quantity = food.defaultPortionGrams.toString()) }
    }

    fun onQuantityChanged(q: String) {
        if (q.isEmpty() || q.matches(Regex("^\\d+$"))) _state.update { it.copy(quantity = q) }
    }

    fun confirmAddMeal() {
        val s = _state.value
        val food = s.selectedFood ?: return
        val qty = s.quantity.toIntOrNull() ?: return
        val factor = qty / 100.0

        viewModelScope.launch {
            val meal = MealLogEntity(
                foodId = food.id,
                date = s.selectedDate,
                mealType = s.selectedMealType,
                quantityGrams = qty,
                calories = food.caloriesPer100g * factor,
                proteins = food.proteinsPer100g * factor,
                carbs = food.carbsPer100g * factor,
                fats = food.fatsPer100g * factor,
                time = LocalTime.now()
            )
            repo.insertMealLog(meal)
            _state.update { it.copy(showAddMeal = false) }
        }
    }

    fun deleteMeal(mealId: Long, food: FoodEntity? = null, scanId: Long? = null) {
        viewModelScope.launch {
            if (scanId != null) {
                // Repas issu d'un scan : supprimer le scan entier + tous ses repas + foods
                val foodIds = repo.getFoodIdsByScanId(scanId)
                // Récupérer le scan pour supprimer sa photo
                val scan = mealScanDao.getScanById(scanId)
                // CASCADE : supprime le scan → supprime tous les MealLogEntity liés
                mealScanDao.deleteScanById(scanId)
                // Nettoyer les FoodEntity orphelins
                if (foodIds.isNotEmpty()) repo.deleteFoodsByIds(foodIds)
                // Supprimer la photo
                scan?.photoPath?.let { path ->
                    try { java.io.File(path).delete() } catch (_: Exception) {}
                }
            } else {
                // Repas manuel : supprimer juste le log
                repo.deleteMealLog(mealId)
                // Supprimer le FoodEntity orphelin si c'est un aliment scanné (préfixe 📷)
                if (food != null && food.name.startsWith("📷 ")) {
                    repo.deleteFood(food)
                }
            }
        }
    }

    fun toggleFavorite(foodId: Long, current: Boolean) {
        viewModelScope.launch { repo.setFavorite(foodId, !current) }
    }

    // ── Objectifs ──
    fun updateGoal(goal: NutritionGoalEntity) {
        viewModelScope.launch { repo.saveNutritionGoal(goal) }
    }
}
