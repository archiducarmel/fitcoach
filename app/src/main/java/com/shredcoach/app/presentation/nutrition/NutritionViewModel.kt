package com.shredcoach.app.presentation.nutrition


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.ScheduledWorkoutRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.nutrition.DailyActivityState
import com.shredcoach.app.domain.nutrition.DailyCalorieTargetCalculator
import com.shredcoach.app.domain.nutrition.IngredientAggregator
import com.shredcoach.app.domain.nutrition.NutritionInsights
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

@Immutable
data class NutritionState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealWithFood> = emptyList(),
    val totalCalories: Double = 0.0,
    val totalProteins: Double = 0.0,
    val totalCarbs: Double = 0.0,
    val totalFats: Double = 0.0,
    val goal: NutritionGoalEntity = NutritionGoalEntity(),
    /**
     * Cible calorique du jour — adaptative.
     * Formule : sedentaryBase + workoutKcalBurned. Pas de bonus calendaire.
     */
    val adjustedTargetCalories: Int = 2200,
    /**
     * État réel d'activité du jour, calculé depuis les WorkoutLogEntity
     * complétés. Distinct de `isTrainingDay` du calendrier prévu.
     */
    val activityState: DailyActivityState = DailyActivityState.PENDING,
    /** Décomposition affichable de la cible calorique (pour l'UI). */
    val energyBreakdown: EnergyBreakdown = EnergyBreakdown(),
    /** Conservé pour compatibilité descendante (UI legacy). À retirer à terme. */
    val isTrainingDay: Boolean = false,
    // Add meal dialog
    val showAddMeal: Boolean = false,
    val selectedMealType: MealType = MealType.LUNCH,
    val searchQuery: String = "",
    val searchResults: List<FoodEntity> = emptyList(),
    val selectedFood: FoodEntity? = null,
    val quantity: String = "",
    // Insights nutrition (30 derniers jours, agrégés depuis les scans)
    val insights: NutritionInsights? = null,
    val isLoading: Boolean = true
)

/**
 * Décomposition de la cible calorique du jour, prête à afficher dans l'UI.
 *
 *  total = sedentaryMaintenance + goalDelta + workoutBonus
 *
 *  - sedentaryMaintenance : BMR × 1.20 (dépense de base, journée assise).
 *  - goalDelta            : -400 (sèche) / 0 (maintien) / +300 (prise masse).
 *  - workoutBonus         : kcal RÉELLEMENT brûlées sur les séances complétées
 *                           du jour (formule MET, lue depuis WorkoutLogEntity).
 *
 * `completedWorkouts` documente la source du bonus pour la transparence UI
 * (ex : tooltip "1 séance de 52 min · 240 kcal").
 */
@Immutable
data class EnergyBreakdown(
    val sedentaryMaintenance: Int = 0,
    val goalDelta: Int = 0,
    val workoutBonus: Int = 0,
    val total: Int = 0,
    val completedWorkouts: Int = 0,
    val totalWorkoutMinutes: Int = 0,
)

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repo: NutritionRepository,
    private val mealScanDao: MealScanDao,
    @Suppress("unused") private val scheduledRepo: ScheduledWorkoutRepository,
    private val workoutLogDao: WorkoutLogDao,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NutritionState())
    val state: StateFlow<NutritionState> = _state.asStateFlow()

    /**
     * Cutoff horaire pour considérer la journée comme "terminée" (état RESTED
     * définitif). Avant 22h, l'absence de séance reste PENDING — l'user a
     * encore le temps de bouger, donc pas de verdict figé.
     */
    private val DAY_CUTOFF_HOUR = 22

    init {
        viewModelScope.launch { repo.seedFoodsIfEmpty() }
        viewModelScope.launch {
            // Recalculer dès que le profil est dispo (calcul dépend du poids)
            recalcDailyTarget(_state.value.selectedDate)
        }
        loadGoal()
        loadDay(LocalDate.now())
        loadInsights()
        observeWorkoutsForActiveDate()
    }

    /**
     * Observe les WorkoutLogEntity pour la date sélectionnée et déclenche
     * le recalcul adaptatif quand quelque chose change (séance terminée,
     * édition de durée, suppression…). Garantit que la cible nutrition
     * reflète l'activité réelle SANS pull-to-refresh manuel.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeWorkoutsForActiveDate() {
        viewModelScope.launch {
            // Re-collect chaque fois que selectedDate change
            _state
                .map { it.selectedDate }
                .distinctUntilChanged()
                .flatMapLatest { date -> workoutLogDao.getWorkoutLogsBetween(date, date) }
                .collect {
                    recalcDailyTarget(_state.value.selectedDate)
                }
        }
    }

    fun refresh() {
        loadGoal()
        loadDay(_state.value.selectedDate)
        loadInsights()
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
     * Recalcul adaptatif de la cible calorique du jour.
     *
     * Modèle : BMR × 1.20 (sédentaire) + ajustement objectif + kcal RÉELLEMENT
     * brûlées par les séances complétées (lecture WorkoutLogEntity, formule MET).
     * Aucun bonus calendaire fantôme : si l'user n'a pas bougé, le bonus = 0.
     *
     * Détermine également [DailyActivityState] depuis la réalité observée :
     *  - TRAINED si ≥1 séance complétée aujourd'hui
     *  - RESTED si aucune séance + (date passée OU heure ≥ DAY_CUTOFF_HOUR)
     *  - PENDING si aucune séance + journée encore active
     *
     * Robuste si profil pas encore chargé (target = défaut state, pas de crash).
     */
    private fun recalcDailyTarget(date: LocalDate) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            val completedLogs = workoutLogDao.getCompletedLogsOnDateOnce(date)

            // Cible adaptative — helper UNIQUE partagé avec HomeViewModel
            // → garantit que les 2 pages affichent strictement la même
            // valeur. Cf. [DailyCalorieTargetCalculator].
            val adjusted = DailyCalorieTargetCalculator.adaptiveTarget(profile, completedLogs)

            // Décomposition pour l'UI (transparence : montrer base + bonus)
            val sedentaryMaintenance = TdeeCalculator.sedentaryMaintenance(
                profile.sex, profile.currentWeightKg, profile.heightCm, profile.age
            )
            val goalDelta = TdeeCalculator.goalAdjustment(profile.goal)
            val workoutBonus = TdeeCalculator.totalWorkoutKcalForDay(
                completedLogs = completedLogs,
                userWeightKg = profile.currentWeightKg
            )
            val totalWorkoutMinutes = completedLogs.sumOf { log ->
                if (log.actualDurationSeconds > 60L) (log.actualDurationSeconds / 60).toInt()
                else log.durationMinutes
            }

            val state = computeActivityState(date, completedLogs.isNotEmpty())

            val breakdown = EnergyBreakdown(
                sedentaryMaintenance = sedentaryMaintenance,
                goalDelta = goalDelta,
                workoutBonus = workoutBonus,
                total = adjusted,
                completedWorkouts = completedLogs.size,
                totalWorkoutMinutes = totalWorkoutMinutes
            )

            _state.update { it.copy(
                adjustedTargetCalories = adjusted,
                activityState = state,
                energyBreakdown = breakdown,
                isTrainingDay = state == DailyActivityState.TRAINED
            ) }
        }
    }

    /**
     * État du jour basé sur la réalité observée + la position dans le temps.
     * - Date passée + 0 séance → RESTED (verdict figé).
     * - Date future → PENDING (rien ne s'est encore passé).
     * - Aujourd'hui : ≥1 séance → TRAINED ; sinon PENDING avant 22h, RESTED après.
     */
    private fun computeActivityState(date: LocalDate, hasTrainedToday: Boolean): DailyActivityState {
        if (hasTrainedToday) return DailyActivityState.TRAINED
        val today = LocalDate.now()
        return when {
            date.isBefore(today) -> DailyActivityState.RESTED
            date.isAfter(today) -> DailyActivityState.PENDING
            LocalTime.now().hour >= DAY_CUTOFF_HOUR -> DailyActivityState.RESTED
            else -> DailyActivityState.PENDING
        }
    }

    /**
     * Charge les insights nutrition sur 30 jours glissants. Lit les scans
     * depuis MealScanDao, déserialise leur resultJson et passe la liste à
     * IngredientAggregator. Calcul léger (<10ms typique pour 100 scans),
     * pas de cache → recalculé à chaque pull-to-refresh.
     */
    private fun loadInsights() {
        viewModelScope.launch {
            val periodDays = 30
            val sinceDate = LocalDate.now().minusDays(periodDays.toLong()).toString()
            val scans = mealScanDao.getScansSince(sinceDate)
            val insights = IngredientAggregator.aggregate(scans, periodDays = periodDays)
            _state.update { it.copy(insights = insights) }
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
