package com.shredcoach.app.domain.chat

import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.notification.BehaviorPattern
import com.shredcoach.app.domain.notification.NotificationContextEngine
import com.shredcoach.app.domain.notification.UserContextSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Tests du dispatcher + des 4 tools.
 *
 * **Stratégie** : MockK sur les 4 deps. On vérifie :
 *  - dispatch correct par nom de tool
 *  - validation des arguments (rejet propre des invalides)
 *  - effets DB attendus (slot capture pour vérifier le payload)
 *  - safety net : exceptions enveloppées en `ToolResult` (pas de crash)
 */
class ShreddyToolExecutorTest {

    private lateinit var nutritionDao: NutritionDao
    private lateinit var userRepository: UserRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var contextEngine: NotificationContextEngine
    private lateinit var glucoseRepository: GlucoseRepository
    private lateinit var executor: ShreddyToolExecutor

    @Before
    fun setup() {
        nutritionDao = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        workoutRepository = mockk(relaxed = true)
        contextEngine = mockk(relaxed = true)
        glucoseRepository = mockk(relaxed = true)
        executor = ShreddyToolExecutor(
            nutritionDao = nutritionDao,
            userRepository = userRepository,
            workoutRepository = workoutRepository,
            contextEngine = contextEngine,
            glucoseRepository = glucoseRepository,
        )
    }

    // ═══════════════════════════════════════════════
    // DISPATCH : outil inconnu
    // ═══════════════════════════════════════════════

    @Test
    fun `tool inconnu retourne erreur claire`() = runBlocking {
        val result = executor.execute(ToolCall(id = "1", name = "non_existant", argumentsJson = "{}"))
        assertThat(result.content).contains("inconnu")
    }

    @Test
    fun `arguments JSON invalides ne crashent pas`() = runBlocking {
        val result = executor.execute(ToolCall(id = "1", name = ShreddyTools.LOG_MEAL,
            argumentsJson = "{not even json}"))
        assertThat(result.content).contains("Erreur")
    }

    // ═══════════════════════════════════════════════
    // LOG_MEAL — write path
    // ═══════════════════════════════════════════════

    @Test
    fun `log_meal sans nom retourne erreur`() = runBlocking {
        val result = executor.execute(ToolCall(id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"calories": 500}"""))
        assertThat(result.content).contains("nom")
        coVerify(exactly = 0) { nutritionDao.insertFood(any()) }
        coVerify(exactly = 0) { nutritionDao.insertMealLog(any()) }
    }

    @Test
    fun `log_meal sans calories retourne erreur`() = runBlocking {
        val result = executor.execute(ToolCall(id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"name": "Poulet riz"}"""))
        assertThat(result.content).contains("calories")
        coVerify(exactly = 0) { nutritionDao.insertMealLog(any()) }
    }

    @Test
    fun `log_meal valide insere food puis meal log`() = runBlocking {
        coEvery { nutritionDao.insertFood(any()) } returns 42L
        val foodSlot = slot<FoodEntity>()
        val mealSlot = slot<MealLogEntity>()
        coEvery { nutritionDao.insertFood(capture(foodSlot)) } returns 42L
        coEvery { nutritionDao.insertMealLog(capture(mealSlot)) } returns 1L

        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"name":"Poulet riz","calories":520,"proteins_g":45,"carbs_g":60,"fats_g":10,"meal_type":"LUNCH"}"""
        ))

        // FoodEntity créé avec les valeurs LLM
        assertThat(foodSlot.captured.name).isEqualTo("Poulet riz")
        assertThat(foodSlot.captured.caloriesPer100g).isEqualTo(520.0)
        assertThat(foodSlot.captured.proteinsPer100g).isEqualTo(45.0)

        // MealLog avec foodId du insert ET mealType respecté
        assertThat(mealSlot.captured.foodId).isEqualTo(42L)
        assertThat(mealSlot.captured.mealType).isEqualTo(MealType.LUNCH)
        assertThat(mealSlot.captured.calories).isEqualTo(520.0)

        // Confirmation humain-lisible
        assertThat(result.content).contains("Poulet riz")
        assertThat(result.content).contains("520")
    }

    @Test
    fun `log_meal sans meal_type infere depuis l'heure courante`() = runBlocking {
        coEvery { nutritionDao.insertFood(any()) } returns 1L
        val mealSlot = slot<MealLogEntity>()
        coEvery { nutritionDao.insertMealLog(capture(mealSlot)) } returns 1L

        executor.execute(ToolCall(
            id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"name":"Snack","calories":200}"""
        ))

        // Le mealType inféré dépend de l'heure de la machine de test — on
        // vérifie juste qu'il en a inféré UN (pas null/absent).
        assertThat(mealSlot.captured.mealType).isAnyOf(
            MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER
        )
    }

    @Test
    fun `log_meal meal_type invalide tombe en infered`() = runBlocking {
        coEvery { nutritionDao.insertFood(any()) } returns 1L
        val mealSlot = slot<MealLogEntity>()
        coEvery { nutritionDao.insertMealLog(capture(mealSlot)) } returns 1L

        executor.execute(ToolCall(
            id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"name":"X","calories":100,"meal_type":"GARBAGE"}"""
        ))

        // Pas de crash — le invalid type doit déclencher fallback inferé
        assertThat(mealSlot.captured.mealType).isNotNull()
    }

    // ═══════════════════════════════════════════════
    // SET_WEIGHT
    // ═══════════════════════════════════════════════

    @Test
    fun `set_weight valide insere WeightLogEntity`() = runBlocking {
        val slot = slot<WeightLogEntity>()
        coEvery { userRepository.insertWeightLog(capture(slot)) } returns Unit

        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.SET_WEIGHT,
            argumentsJson = """{"weight_kg": 79.4}"""
        ))

        assertThat(slot.captured.weightKg).isEqualTo(79.4)
        assertThat(result.content).contains("79.4")
    }

    @Test
    fun `set_weight zero rejete`() = runBlocking {
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.SET_WEIGHT,
            argumentsJson = """{"weight_kg": 0}"""
        ))
        assertThat(result.content).contains("invalide")
        coVerify(exactly = 0) { userRepository.insertWeightLog(any()) }
    }

    @Test
    fun `set_weight trop bas rejete`() = runBlocking {
        // borne stricte > 20
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.SET_WEIGHT,
            argumentsJson = """{"weight_kg": 15}"""
        ))
        assertThat(result.content).contains("invalide")
        coVerify(exactly = 0) { userRepository.insertWeightLog(any()) }
    }

    @Test
    fun `set_weight trop haut rejete`() = runBlocking {
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.SET_WEIGHT,
            argumentsJson = """{"weight_kg": 500}"""
        ))
        assertThat(result.content).contains("invalide")
        coVerify(exactly = 0) { userRepository.insertWeightLog(any()) }
    }

    // ═══════════════════════════════════════════════
    // GET_TODAY_STATS — pas de snapshot
    // ═══════════════════════════════════════════════

    @Test
    fun `get_today_stats sans snapshot remonte un message clair`() = runBlocking {
        coEvery { contextEngine.snapshot() } returns null
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.GET_TODAY_STATS, argumentsJson = "{}"
        ))
        assertThat(result.content).ignoringCase().contains("onboarding")
    }

    @Test
    fun `get_today_stats avec snapshot renvoie JSON utilisable`() = runBlocking {
        coEvery { contextEngine.snapshot() } returns fakeSnapshot()
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.GET_TODAY_STATS, argumentsJson = "{}"
        ))
        // JSON-shaped + champs clés présents
        assertThat(result.content).startsWith("{")
        assertThat(result.content).contains("calories_in")
        assertThat(result.content).contains("calories_target")
        assertThat(result.content).contains("behavior_pattern")
    }

    // ═══════════════════════════════════════════════
    // GET_RECENT_WORKOUTS
    // ═══════════════════════════════════════════════

    @Test
    fun `get_recent_workouts renvoie JSON 7j`() = runBlocking {
        coEvery { workoutRepository.getWorkoutCountInPeriod(any(), any()) } returns 3
        coEvery { workoutRepository.getTotalVolumeInPeriod(any(), any()) } returns 12500.0

        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.GET_RECENT_WORKOUTS, argumentsJson = "{}"
        ))
        assertThat(result.content).startsWith("{")
        assertThat(result.content).contains("last_7_days")
        assertThat(result.content).contains("\"workouts_completed\":3")
        assertThat(result.content).contains("\"total_volume_kg\":12500")
    }

    // ═══════════════════════════════════════════════
    // SAFETY : exception DB ne crashe pas
    // ═══════════════════════════════════════════════

    @Test
    fun `exception DB est enveloppee en ToolResult`() = runBlocking {
        coEvery { nutritionDao.insertFood(any()) } throws RuntimeException("disk full")
        val result = executor.execute(ToolCall(
            id = "x", name = ShreddyTools.LOG_MEAL,
            argumentsJson = """{"name":"X","calories":100}"""
        ))
        assertThat(result.content).contains("Erreur")
        assertThat(result.content).contains("disk full")
    }

    // ═══════════════════════════════════════════════
    // Factory snapshot (mêmes defaults neutres que BehaviorAnalyzerTest)
    // ═══════════════════════════════════════════════

    private fun fakeSnapshot() = UserContextSnapshot(
        todayCaloriesIn = 1200,
        todayTarget = 2000,
        todayDelta = -800,
        todayMealsLogged = setOf(MealType.BREAKFAST, MealType.LUNCH),
        todayWorkoutDone = null,
        todayWorkoutPlanned = null,
        remainingKcalToday = 800,

        yesterdayCaloriesIn = 1900,
        yesterdayTarget = 2000,
        yesterdayDelta = -100,
        yesterdayMealsLogged = emptySet(),
        yesterdayWorkoutDone = true,
        yesterdayWeight = 80.0,

        avgDelta7d = -200,
        daysOnTarget7d = 5,
        daysOverTarget7d = 1,
        consecutiveOnTargetDays = 3,
        workoutCount7d = 3,
        weightTrendKgPerWeek7d = -0.3,

        avgDelta30d = -150,
        daysOnTarget30d = 20,
        daysOverTarget30d = 4,
        biggestStreakOnTarget30d = 8,
        workoutCount30d = 12,
        weightChange30d = -1.5,
        weightTrendKgPerWeek30d = -0.4,
        relapseCount30d = 1,

        weightLatest = 79.5,
        weightGoal = 75.0,
        weightDistanceToGoal = 4.5,

        daysSinceLastWorkout = 1,
        historyDays = 30,
        behaviorPattern = BehaviorPattern.NORMAL,
    )
}
