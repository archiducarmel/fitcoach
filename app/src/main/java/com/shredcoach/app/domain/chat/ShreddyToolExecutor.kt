package com.shredcoach.app.domain.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.notification.NotificationContextEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exécuteur des tools demandés par le LLM. Chaque méthode publique
 * correspond à un tool de [ShreddyTools], parse les arguments, fait les
 * écritures DB nécessaires, et retourne un [ToolResult] avec un message
 * humain-lisible (que le LLM intégrera dans sa réponse finale).
 *
 * **Important** : le contenu retourné dans `ToolResult.content` est ce que
 * le LLM va voir. Pour les actions WRITE, on confirme avec les valeurs
 * effectivement loggées ("OK, repas 'Poulet riz' (520 kcal) ajouté"). Pour
 * les actions READ, on retourne un JSON structuré pour que le LLM puisse
 * raisonner dessus.
 *
 * **Sécurité** : on ne crash JAMAIS — toute exception est convertie en
 * `ToolResult` avec un message d'erreur compréhensible. Le LLM peut alors
 * en informer l'utilisateur ("désolé, je n'ai pas réussi à logger ton repas").
 */
@Singleton
class ShreddyToolExecutor @Inject constructor(
    private val nutritionDao: NutritionDao,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val contextEngine: NotificationContextEngine,
) {

    /**
     * Dispatcher central — appelé par le repo après parsing d'un [ToolCall].
     * Try-catch pour qu'un tool buggé ne crashe pas la conversation.
     */
    suspend fun execute(call: ToolCall): ToolResult {
        return try {
            val args = if (call.argumentsJson.isBlank()) JsonObject()
                       else JsonParser.parseString(call.argumentsJson).asJsonObject
            when (call.name) {
                ShreddyTools.LOG_MEAL -> logMeal(call.id, args)
                ShreddyTools.SET_WEIGHT -> setWeight(call.id, args)
                ShreddyTools.GET_TODAY_STATS -> getTodayStats(call.id)
                ShreddyTools.GET_RECENT_WORKOUTS -> getRecentWorkouts(call.id)
                else -> ToolResult(call.id, call.name, "Erreur : outil inconnu '${call.name}'")
            }
        } catch (t: Throwable) {
            ToolResult(call.id, call.name, "Erreur d'exécution : ${t.message ?: "inconnue"}")
        }
    }

    // ═══════════════════════════════════════════════════════════
    // LOG_MEAL
    // ═══════════════════════════════════════════════════════════

    private suspend fun logMeal(callId: String, args: JsonObject): ToolResult {
        val name = args.get("name")?.asString?.takeIf { it.isNotBlank() }
            ?: return ToolResult(callId, ShreddyTools.LOG_MEAL, "Erreur : nom du repas manquant")
        val calories = args.get("calories")?.asDouble ?: 0.0
        if (calories <= 0) return ToolResult(callId, ShreddyTools.LOG_MEAL, "Erreur : calories invalides")
        val proteins = args.get("proteins_g")?.asDouble ?: 0.0
        val carbs = args.get("carbs_g")?.asDouble ?: 0.0
        val fats = args.get("fats_g")?.asDouble ?: 0.0
        val mealTypeStr = args.get("meal_type")?.asString
        val mealType = parseMealType(mealTypeStr) ?: inferMealTypeFromTime()

        // 1. Créer un FoodEntity custom (le LLM nous donne un nom, pas un foodId DB).
        //    Les macros sont per-100g — on injecte les valeurs telles quelles en
        //    supposant que `calories` est pour la portion entière (cf. quantityGrams).
        val portionGrams = 100 // par défaut, on logue 100g de "Item LLM"
        val food = FoodEntity(
            name = name.take(60),
            caloriesPer100g = calories,
            proteinsPer100g = proteins,
            carbsPer100g = carbs,
            fatsPer100g = fats,
            fiberPer100g = 0.0,
            defaultPortionGrams = portionGrams,
            portionLabel = "portion",
            category = "Custom",
            isFavorite = false,
        )
        val foodId = nutritionDao.insertFood(food)

        // 2. Logger le repas
        val now = LocalDateTime.now()
        val mealLog = MealLogEntity(
            foodId = foodId,
            date = now.toLocalDate(),
            mealType = mealType,
            quantityGrams = portionGrams,
            calories = calories,
            proteins = proteins,
            carbs = carbs,
            fats = fats,
            time = now.toLocalTime(),
        )
        nutritionDao.insertMealLog(mealLog)

        val mealName = name.take(40)
        val kcalRounded = calories.toInt()
        val mealTypeLabel = mealType.displayName
        return ToolResult(
            callId, ShreddyTools.LOG_MEAL,
            "OK, repas '$mealName' ($kcalRounded kcal, ${proteins.toInt()}g protéines) " +
                "ajouté à ton $mealTypeLabel."
        )
    }

    private fun parseMealType(raw: String?): MealType? {
        if (raw.isNullOrBlank()) return null
        return try { MealType.valueOf(raw.uppercase()) } catch (_: Exception) { null }
    }

    /**
     * Si le LLM oublie le `meal_type`, on infère depuis l'heure courante.
     * Ranges calibrées sur les horaires standards ShredCoach :
     *  - <11h : BREAKFAST
     *  - 11h-15h : LUNCH
     *  - 15h-18h : SNACK
     *  - ≥18h : DINNER
     */
    private fun inferMealTypeFromTime(): MealType {
        val h = LocalTime.now().hour
        return when {
            h < 11 -> MealType.BREAKFAST
            h < 15 -> MealType.LUNCH
            h < 18 -> MealType.SNACK
            else -> MealType.DINNER
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SET_WEIGHT
    // ═══════════════════════════════════════════════════════════

    private suspend fun setWeight(callId: String, args: JsonObject): ToolResult {
        val kg = args.get("weight_kg")?.asDouble ?: 0.0
        if (kg <= 20 || kg > 400) {
            return ToolResult(callId, ShreddyTools.SET_WEIGHT,
                "Erreur : valeur de poids invalide ($kg kg)")
        }
        userRepository.insertWeightLog(WeightLogEntity(date = LocalDate.now(), weightKg = kg))
        return ToolResult(callId, ShreddyTools.SET_WEIGHT,
            "OK, $kg kg enregistré pour aujourd'hui dans ton suivi de poids.")
    }

    // ═══════════════════════════════════════════════════════════
    // GET_TODAY_STATS
    // ═══════════════════════════════════════════════════════════

    /**
     * Renvoie un JSON compact des stats du jour. Contourne le problème du
     * contexte stale : le LLM peut demander ces données à tout turn (pas
     * seulement turn 1) et obtenir une vue fraîche.
     */
    private suspend fun getTodayStats(callId: String): ToolResult {
        val snapshot = contextEngine.snapshot()
            ?: return ToolResult(callId, ShreddyTools.GET_TODAY_STATS,
                "Pas de profil utilisateur — onboarding incomplet.")

        val json = JsonObject().apply {
            addProperty("calories_in", snapshot.todayCaloriesIn)
            addProperty("calories_target", snapshot.todayTarget)
            addProperty("delta_kcal", snapshot.todayDelta)
            addProperty("remaining_kcal", snapshot.remainingKcalToday)
            addProperty("meals_logged",
                snapshot.todayMealsLogged.joinToString(",") { it.name })
            addProperty("workout_done_today", snapshot.todayWorkoutDone != null)
            addProperty("workout_planned_today", snapshot.todayWorkoutPlanned != null)
            snapshot.todayWorkoutPlanned?.title?.takeIf { it.isNotBlank() }?.let {
                addProperty("workout_planned_title", it)
            }
            addProperty("consecutive_on_target_days", snapshot.consecutiveOnTargetDays)
            addProperty("days_since_last_workout", snapshot.daysSinceLastWorkout)
            addProperty("behavior_pattern", snapshot.behaviorPattern.name)
            snapshot.weightLatest?.let { addProperty("weight_latest_kg", it) }
            snapshot.weightTrendKgPerWeek7d?.let { addProperty("weight_trend_kg_per_week_7d", it) }
        }
        return ToolResult(callId, ShreddyTools.GET_TODAY_STATS, json.toString())
    }

    // ═══════════════════════════════════════════════════════════
    // GET_RECENT_WORKOUTS
    // ═══════════════════════════════════════════════════════════

    private suspend fun getRecentWorkouts(callId: String): ToolResult {
        // Réutilise un helper existant (CoachContextBuilder a déjà la
        // logique — on ne dépend pas de lui ici pour éviter cycles DI).
        val since = LocalDate.now().minusDays(7)
        val today = LocalDate.now()
        val count = workoutRepository.getWorkoutCountInPeriod(since, today)
        val volume = workoutRepository.getTotalVolumeInPeriod(since, today)
        val json = JsonObject().apply {
            addProperty("period", "last_7_days")
            addProperty("workouts_completed", count)
            addProperty("total_volume_kg", volume.toInt())
        }
        return ToolResult(callId, ShreddyTools.GET_RECENT_WORKOUTS, json.toString())
    }
}
