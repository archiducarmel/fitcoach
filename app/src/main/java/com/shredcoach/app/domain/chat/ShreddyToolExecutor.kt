package com.shredcoach.app.domain.chat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
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
    private val mealScanDao: com.shredcoach.app.data.local.dao.MealScanDao,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val contextEngine: NotificationContextEngine,
    private val glucoseRepository: GlucoseRepository,
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
                ShreddyTools.GET_GLUCOSE_TODAY -> getGlucoseToday(call.id)
                ShreddyTools.GET_GLUCOSE_RANGE_SUMMARY -> getGlucoseRangeSummary(call.id, args)
                ShreddyTools.GET_GLUCOSE_CORRELATIONS -> getGlucoseCorrelations(call.id, args)
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

    // ═══════════════════════════════════════════════════════════
    // GLUCOSE (Dr. Glykos)
    // ═══════════════════════════════════════════════════════════

    private suspend fun getGlucoseToday(callId: String): ToolResult {
        val today = LocalDate.now()
        val summary = glucoseRepository.getDaySummary(today)
            ?: return ToolResult(callId, ShreddyTools.GET_GLUCOSE_TODAY,
                """{"glucose_logged":false,"message":"Aucun log CGM pour aujourd'hui."}""")
        val json = JsonObject().apply {
            addProperty("date", today.toString())
            addProperty("glucose_logged", true)
            summary.avgMgdl?.let { addProperty("avg_mgdl", it) }
            summary.peakMgdl?.let { addProperty("peak_mgdl", it) }
            summary.peakTime?.let { addProperty("peak_time", it.toString()) }
            summary.minMgdl?.let { addProperty("min_mgdl", it) }
            summary.minTime?.let { addProperty("min_time", it.toString()) }
            summary.timeInRangePct?.let { addProperty("time_in_range_pct", it) }
            summary.hypoCount?.let { addProperty("hypo_count", it) }
            summary.cv?.let { addProperty("cv", it) }
            summary.parseConfidence?.let { addProperty("parse_confidence", it.toDouble()) }
            summary.manualOverride.let { addProperty("manual_override", it) }
        }
        return ToolResult(callId, ShreddyTools.GET_GLUCOSE_TODAY, json.toString())
    }

    private suspend fun getGlucoseRangeSummary(callId: String, args: JsonObject): ToolResult {
        val days = args.get("days")?.takeIf { !it.isJsonNull }?.asInt?.coerceIn(2, 90) ?: 7
        val today = LocalDate.now()
        val s = glucoseRepository.getWindowSummary(today, days)
        if (s.daysCovered == 0) {
            return ToolResult(callId, ShreddyTools.GET_GLUCOSE_RANGE_SUMMARY,
                """{"days_requested":$days,"days_covered":0,"message":"Pas de data CGM sur cette fenêtre."}""")
        }
        val json = JsonObject().apply {
            addProperty("days_requested", days)
            addProperty("days_covered", s.daysCovered)
            s.avgMgdl?.let { addProperty("avg_mgdl", it) }
            s.avgTirPct?.let { addProperty("avg_tir_pct", it) }
            s.avgCv?.let { addProperty("avg_cv", it) }
            s.trendMgdlPerWeek?.let { addProperty("trend_mgdl_per_week", it) }
            addProperty("total_hypo", s.totalHypo)
            addProperty("pattern", s.pattern.name)
        }
        return ToolResult(callId, ShreddyTools.GET_GLUCOSE_RANGE_SUMMARY, json.toString())
    }

    /**
     * Croise pics glycémiques d'un jour avec meals et workouts loggés à
     * proximité (±120 min). Détaille la mécanique :
     *  - Lit la courbe 24h JSON si présente, sinon utilise peak_time du log.
     *  - Cherche meals dans une fenêtre [pic-90min, pic+30min] (pic = ~30-60min post-meal).
     *  - Cherche workouts qui chevauchent le pic (impact metabolic).
     *  - Retourne fact-pack JSON exploitable par le LLM Dr. Glykos.
     */
    private suspend fun getGlucoseCorrelations(callId: String, args: JsonObject): ToolResult {
        val dateStr = args.get("date")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        val date = try { dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now() } catch (_: Exception) { LocalDate.now() }
        val log = glucoseRepository.getForDate(date)
            ?: return ToolResult(callId, ShreddyTools.GET_GLUCOSE_CORRELATIONS,
                """{"date":"$date","correlations":[],"message":"Pas de log CGM pour cette date."}""")
        val meals = try { nutritionDao.getMealsForDateOnce(date) } catch (_: Exception) { emptyList() }
        val workouts = try { workoutRepository.getCompletedWorkoutsOnDate(date) } catch (_: Exception) { emptyList() }

        val correlations = JsonObject().apply {
            addProperty("date", date.toString())
            val arr = com.google.gson.JsonArray()
            // Pic principal
            log.peakMgdl?.let { peak ->
                val peakObj = JsonObject().apply {
                    addProperty("type", "peak")
                    addProperty("mgdl", peak)
                    log.peakTime?.let { addProperty("time", it.toString()) }
                    // Repas associés (dans la fenêtre -90min → +30min vs pic)
                    val nearMeals = log.peakTime?.let { pt ->
                        meals.filter { m ->
                            val mt = m.time ?: return@filter false
                            val diff = java.time.Duration.between(mt, pt).toMinutes()
                            diff in 30..90
                        }
                    } ?: emptyList()
                    if (nearMeals.isNotEmpty()) {
                        val mealsArr = com.google.gson.JsonArray()
                        nearMeals.take(3).forEach { m ->
                            // v45 : applique le facteur effectif si le meal_log
                            // provient d'un scan avec modificateur (×N + restes).
                            // Sinon factor = 1.0 (manuel ou sans modifs).
                            val factor = m.scanId?.let { sid ->
                                mealScanDao.getScanById(sid)?.let {
                                    com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveFactor(it)
                                }
                            } ?: 1.0
                            mealsArr.add(JsonObject().apply {
                                addProperty("food_id", m.foodId)
                                addProperty("time", m.time?.toString() ?: "")
                                addProperty("calories", m.calories * factor)
                                addProperty("carbs_g", m.carbs * factor)
                            })
                        }
                        add("associated_meals", mealsArr)
                    }
                }
                arr.add(peakObj)
            }
            // Workouts intersectant le jour
            if (workouts.isNotEmpty()) {
                val woArr = com.google.gson.JsonArray()
                workouts.take(3).forEach { w ->
                    woArr.add(JsonObject().apply {
                        addProperty("date", w.date.toString())
                        addProperty("duration_min", w.actualDurationSeconds / 60)
                        addProperty("volume_kg", w.totalVolume)
                        addProperty("completed", w.completed)
                    })
                }
                add("workouts_same_day", woArr)
            }
            add("correlations", arr)
        }
        return ToolResult(callId, ShreddyTools.GET_GLUCOSE_CORRELATIONS, correlations.toString())
    }
}
