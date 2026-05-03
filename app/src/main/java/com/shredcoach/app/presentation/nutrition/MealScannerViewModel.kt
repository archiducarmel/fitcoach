package com.shredcoach.app.presentation.nutrition

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.BowlType
import com.shredcoach.app.data.remote.GeminiMealService
import com.shredcoach.app.data.remote.MealAnalysisResult
import com.shredcoach.app.data.remote.PlateType
import com.shredcoach.app.data.remote.buildMealHintBlock
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.nutrition.NutriScoreCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class MealScannerState(
    val imageBitmap: Bitmap? = null,
    val isAnalyzing: Boolean = false,
    val result: MealAnalysisResult? = null,
    val savedScanId: Long? = null,
    val addedToTracking: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = false,
    // Historique
    val scanHistory: List<MealScanEntity> = emptyList(),
    val showHistory: Boolean = false,
    // ── Indices optionnels pour l'analyse ──
    val hintPlate: PlateType = PlateType.NONE,
    val hintBowl: BowlType = BowlType.NONE,
    val hintDescription: String = "",
    val showHintsPanel: Boolean = false,
    // ── Override date/heure du repas (si scan tardif) ──
    val mealDateTime: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    val showDatePicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val mealCategory: com.shredcoach.app.domain.nutrition.MealTypeClassifier.Category? = null,
    // ── Édition des grammages d'ingrédients ──
    /** Snapshot immuable du résultat initial du LLM — sert de base pour scaler les macros proportionnellement. */
    val baselineResult: MealAnalysisResult? = null,
    /** Mapping dishIndex → foodId inséré en DB (pour mettre à jour FoodEntity). */
    val foodIdsPerDish: Map<Int, Long> = emptyMap(),
    /** Mapping dishIndex → mealLogId inséré en DB (pour mettre à jour MealLogEntity). */
    val mealLogIdsPerDish: Map<Int, Long> = emptyMap()
)

@HiltViewModel
class MealScannerViewModel @Inject constructor(
    private val geminiService: GeminiMealService,
    private val userRepository: UserRepository,
    private val mealScanDao: MealScanDao,
    private val nutritionRepository: NutritionRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MealScannerState())
    val state: StateFlow<MealScannerState> = _state.asStateFlow()
    private val gson = Gson()

    init {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val hasKey = when (profile?.mealScanProvider) {
                "GROQ" -> userRepository.hasApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                "MISTRAL" -> userRepository.hasApiKey(SecureKeyStore.Provider.MISTRAL)
                else -> userRepository.hasApiKey(SecureKeyStore.Provider.GEMINI)
            }
            _state.update { it.copy(isConfigured = hasKey) }
        }
        // Observer l'historique
        viewModelScope.launch {
            mealScanDao.getAllScans().collect { scans ->
                _state.update { it.copy(scanHistory = scans) }
            }
        }
    }

    fun setImage(bitmap: Bitmap) {
        _state.update { it.copy(
            imageBitmap = bitmap, result = null, error = null, savedScanId = null, addedToTracking = false,
            baselineResult = null, foodIdsPerDish = emptyMap(), mealLogIdsPerDish = emptyMap(),
            // Reset les indices quand on charge une nouvelle image
            hintPlate = PlateType.NONE, hintBowl = BowlType.NONE, hintDescription = "", showHintsPanel = false
        ) }
    }

    // ── Indices optionnels ──
    fun toggleHintsPanel() { _state.update { it.copy(showHintsPanel = !it.showHintsPanel) } }
    fun setHintPlate(type: PlateType) {
        // Sélectionner une assiette désélectionne le bol (on choisit l'un OU l'autre)
        _state.update { it.copy(hintPlate = type, hintBowl = if (type != PlateType.NONE) BowlType.NONE else it.hintBowl) }
    }
    fun setHintBowl(type: BowlType) {
        _state.update { it.copy(hintBowl = type, hintPlate = if (type != BowlType.NONE) PlateType.NONE else it.hintPlate) }
    }
    fun setHintDescription(desc: String) { _state.update { it.copy(hintDescription = desc) } }
    fun clearHints() {
        _state.update { it.copy(hintPlate = PlateType.NONE, hintBowl = BowlType.NONE, hintDescription = "") }
    }

    fun analyze() {
        val bitmap = _state.value.imageBitmap ?: return
        _state.update { it.copy(isAnalyzing = true, error = null, result = null) }

        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val provider = profile?.mealScanProvider ?: "GEMINI"
            val apiKey = when (provider) {
                "GROQ" -> userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                "MISTRAL" -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
                else -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
            }
            val model = profile?.geminiModel ?: "gemini-2.5-flash"

            if (apiKey.isBlank()) {
                val providerName = when (provider) { "GROQ" -> "Groq"; "MISTRAL" -> "Mistral"; else -> "Gemini" }
                _state.update { it.copy(isAnalyzing = false, error = "Configure ta clé API $providerName dans Réglages → Meal Scanner") }
                return@launch
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)

            // Construire le bloc d'indices (vide si rien n'est renseigné → qualité préservée)
            val s = _state.value
            val hintBlock = buildMealHintBlock(
                plate = s.hintPlate,
                bowl = s.hintBowl,
                userDescription = s.hintDescription
            )

            val result = geminiService.analyzeMeal(stream.toByteArray(), "image/jpeg", apiKey, model, provider, hintBlock)

            result.fold(
                onSuccess = { analysis ->
                    // Classifier le type de repas par HEURE DE SCAN (pas le LLM)
                    // avec override whey (12h-20h) → pretraining
                    val scanDateTime = java.time.LocalDateTime.now()
                    val dishKeywords = analysis.dishes.flatMap { d ->
                        listOf(d.name) + d.ingredients.map { it.name }
                    }
                    val category = com.shredcoach.app.domain.nutrition.MealTypeClassifier
                        .classify(scanDateTime.toLocalTime(), dishKeywords)

                    _state.update { it.copy(
                        isAnalyzing = false, result = analysis,
                        baselineResult = analysis,  // snapshot immuable pour le scaling futur
                        mealDateTime = scanDateTime, mealCategory = category
                    ) }

                    // Auto-save en DB puis auto-ajout au suivi nutrition
                    val scanId = saveScanToDb(analysis, category)
                    autoAddToTracking(analysis, scanId, category)

                    // Programmer le débrief IA avec le délai configuré
                    val delay = (profile?.mealDebriefDelayMinutes ?: 45).toLong()
                    com.shredcoach.app.notification.NotificationScheduler
                        .scheduleMealDebrief(appContext, scanId, delay)
                },
                onFailure = { error ->
                    _state.update { it.copy(isAnalyzing = false, error = error.message ?: "Erreur d'analyse") }
                }
            )
        }
    }

    /** Sauvegarde le scan en DB et retourne son ID. Le type de repas est imposé par le classifier (heure). */
    private suspend fun saveScanToDb(
        analysis: MealAnalysisResult,
        category: com.shredcoach.app.domain.nutrition.MealTypeClassifier.Category
    ): Long {
        val photoPath = _state.value.imageBitmap?.let { bmp ->
            try {
                val dir = java.io.File(appContext.filesDir, "meal_scans")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, "scan_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                file.absolutePath
            } catch (_: Exception) { null }
        }

        val firstDish = analysis.dishes.firstOrNull()
        // Calcul Nutri-Score réel via algorithme (sucres, sat fat, sel, fibres, protéines)
        val nutriResult = NutriScoreCalculator.fromTotals(
            calories = analysis.totalCalories,
            sugars = analysis.dishes.sumOf { it.carbsSugar },
            saturatedFat = analysis.dishes.sumOf { it.fatsSaturated },
            saltG = analysis.dishes.sumOf { it.salt },
            fibers = analysis.totalFibers,
            proteins = analysis.totalProteins,
            weightG = analysis.totalWeight
        )
        val scan = MealScanEntity(
            timestamp = _state.value.mealDateTime,
            mealType = category.id, // IMPOSÉ par l'heure de scan (override du LLM)
            dishName = firstDish?.name ?: "Repas scanné",
            cuisine = firstDish?.cuisine ?: "",
            totalCalories = analysis.totalCalories,
            totalProteins = analysis.totalProteins,
            totalCarbs = analysis.totalCarbs,
            totalFats = analysis.totalFats,
            totalFibers = analysis.totalFibers,
            totalWeight = analysis.totalWeight,
            healthScore = analysis.healthScore,
            verdict = analysis.verdict,
            ingredientCount = analysis.dishes.sumOf { it.ingredients.size },
            resultJson = gson.toJson(analysis),
            photoPath = photoPath,
            nutriScoreGrade = nutriResult.grade.toString()
        )
        val id = mealScanDao.insertScan(scan)
        _state.update { it.copy(savedScanId = id) }
        return id
    }

    /** Ajoute automatiquement les macros du scan au suivi nutrition quotidien. */
    private suspend fun autoAddToTracking(
        result: MealAnalysisResult,
        scanId: Long,
        category: com.shredcoach.app.domain.nutrition.MealTypeClassifier.Category
    ) {
        val mealType = category.trackingType

        // Calcul Nutri-Score global pour tous les MealLog issus de ce scan
        val nutriGrade = NutriScoreCalculator.fromTotals(
            calories = result.totalCalories,
            sugars = result.dishes.sumOf { it.carbsSugar },
            saturatedFat = result.dishes.sumOf { it.fatsSaturated },
            saltG = result.dishes.sumOf { it.salt },
            fibers = result.totalFibers,
            proteins = result.totalProteins,
            weightG = result.totalWeight
        ).grade.toString()

        // Créer un FoodEntity + MealLogEntity par plat scanné
        val foodIds = mutableMapOf<Int, Long>()
        val mealLogIds = mutableMapOf<Int, Long>()
        result.dishes.forEachIndexed { dishIdx, dish ->
            val weight = dish.weightG.coerceAtLeast(1)
            val per100 = 100.0 / weight

            val food = FoodEntity(
                name = "📷 ${dish.name}",
                category = "Scan",
                caloriesPer100g = dish.calories * per100,
                proteinsPer100g = dish.proteins * per100,
                carbsPer100g = dish.carbs * per100,
                fatsPer100g = dish.fats * per100,
                fiberPer100g = dish.fibers * per100,
                defaultPortionGrams = weight,
                portionLabel = "${weight}g"
            )
            val foodId = nutritionRepository.insertFood(food)
            foodIds[dishIdx] = foodId

            val mealDt = _state.value.mealDateTime
            val mealLog = MealLogEntity(
                foodId = foodId,
                scanId = scanId,
                date = mealDt.toLocalDate(),
                mealType = mealType,
                quantityGrams = weight,
                calories = dish.calories.toDouble(),
                proteins = dish.proteins,
                carbs = dish.carbs,
                fats = dish.fats,
                time = mealDt.toLocalTime(),
                nutriScoreGrade = nutriGrade
            )
            val logId = nutritionRepository.insertMealLog(mealLog)
            mealLogIds[dishIdx] = logId
        }

        // Marquer le scan comme ajouté au tracking
        val scan = mealScanDao.getScanById(scanId)
        if (scan != null) mealScanDao.updateScan(scan.copy(addedToTracking = true))
        _state.update { it.copy(
            addedToTracking = true,
            foodIdsPerDish = foodIds.toMap(),
            mealLogIdsPerDish = mealLogIds.toMap()
        ) }
    }

    fun toggleHistory() {
        _state.update { it.copy(showHistory = !it.showHistory) }
    }

    fun deleteScan(scan: MealScanEntity) {
        viewModelScope.launch { mealScanDao.deleteScan(scan) }
    }

    fun clear() {
        _state.update { it.copy(
            imageBitmap = null, result = null, error = null, savedScanId = null, addedToTracking = false,
            hintPlate = PlateType.NONE, hintBowl = BowlType.NONE, hintDescription = "", showHintsPanel = false,
            mealDateTime = java.time.LocalDateTime.now(), mealCategory = null,
            baselineResult = null, foodIdsPerDish = emptyMap(), mealLogIdsPerDish = emptyMap()
        ) }
    }

    // ── Override date/heure du repas (pour scans tardifs) ──

    fun openDatePicker() { _state.update { it.copy(showDatePicker = true) } }
    fun closeDatePicker() { _state.update { it.copy(showDatePicker = false) } }
    fun openTimePicker() { _state.update { it.copy(showTimePicker = true) } }
    fun closeTimePicker() { _state.update { it.copy(showTimePicker = false) } }

    /**
     * Applique une nouvelle date/heure au repas scanné.
     * Reclassifie le mealType en fonction de la nouvelle heure et propage en DB
     * (MealScanEntity + tous les MealLogEntity liés via scanId).
     */
    fun applyMealDateTime(newDateTime: java.time.LocalDateTime) {
        val scanId = _state.value.savedScanId ?: return
        val analysis = _state.value.result ?: return

        viewModelScope.launch {
            // Reclassifier selon la nouvelle heure
            val dishKeywords = analysis.dishes.flatMap { d ->
                listOf(d.name) + d.ingredients.map { it.name }
            }
            val newCategory = com.shredcoach.app.domain.nutrition.MealTypeClassifier
                .classify(newDateTime.toLocalTime(), dishKeywords)

            // Update MealScanEntity (timestamp + mealType)
            val scan = mealScanDao.getScanById(scanId)
            if (scan != null) {
                mealScanDao.updateScan(scan.copy(
                    timestamp = newDateTime,
                    mealType = newCategory.id
                ))
            }

            // Update tous les MealLogEntity liés (date + time + mealType)
            nutritionRepository.updateMealLogsDateTime(
                scanId = scanId,
                date = newDateTime.toLocalDate(),
                time = newDateTime.toLocalTime(),
                mealType = newCategory.trackingType
            )

            // Update state local pour refléter immédiatement
            _state.update { it.copy(
                mealDateTime = newDateTime,
                mealCategory = newCategory,
                showDatePicker = false,
                showTimePicker = false
            ) }
        }
    }

    // ══════════════════════════════════════════
    // ÉDITION DES GRAMMAGES D'INGRÉDIENTS
    // ══════════════════════════════════════════

    /**
     * Met à jour le poids (grammes) d'un ingrédient d'un plat.
     *
     * Logique :
     * 1. Scale les macros de l'ingrédient proportionnellement au nouveau poids (ratio depuis la baseline)
     * 2. Recalcule les totaux du plat à partir de la somme de ses ingrédients
     * 3. Scale carbsSugar / fatsSaturated / salt du plat proportionnellement au changement de poids total
     * 4. Recalcule les totaux globaux (calories, proteins, carbs, fats, fibers, totalWeight)
     * 5. Recalcule le Nutri-Score global
     * 6. Persiste en DB : MealScanEntity (totaux + resultJson + nutriScoreGrade) + FoodEntity (per-100g)
     *    + MealLogEntity (quantité + macros + nutriScoreGrade) du plat concerné
     *
     * Note : healthScore (jugement subjectif du LLM sur la qualité du repas) n'est PAS recalculé.
     */
    fun updateIngredientWeight(dishIndex: Int, ingredientIndex: Int, newWeightG: Int) {
        val current = _state.value.result ?: return
        val baseline = _state.value.baselineResult ?: return
        val clamped = newWeightG.coerceIn(1, 9999)

        if (dishIndex !in current.dishes.indices) return
        if (dishIndex !in baseline.dishes.indices) return
        val baseDish = baseline.dishes[dishIndex]
        if (ingredientIndex !in baseDish.ingredients.indices) return

        val baseIng = baseDish.ingredients[ingredientIndex]
        val baseIngWeight = baseIng.weightG.coerceAtLeast(1)
        val ratio = clamped.toDouble() / baseIngWeight

        val scaledIng = baseIng.copy(
            weightG = clamped,
            calories = (baseIng.calories * ratio).toInt().coerceAtLeast(0),
            proteins = baseIng.proteins * ratio,
            carbs = baseIng.carbs * ratio,
            fats = baseIng.fats * ratio,
            fibers = baseIng.fibers * ratio
        )

        // Ingrédients mis à jour du plat (on conserve les edits précédents des autres ingrédients)
        val updatedIngredients = current.dishes[dishIndex].ingredients.toMutableList().apply {
            this[ingredientIndex] = scaledIng
        }

        // Recompute dish totals = somme des ingrédients
        val sumWeight = updatedIngredients.sumOf { it.weightG }
        val sumCals = updatedIngredients.sumOf { it.calories }
        val sumProt = updatedIngredients.sumOf { it.proteins }
        val sumCarb = updatedIngredients.sumOf { it.carbs }
        val sumFat = updatedIngredients.sumOf { it.fats }
        val sumFib = updatedIngredients.sumOf { it.fibers }

        // Pour sucres / sat fat / sel : pas de valeur per-ingrédient → on scale depuis la baseline
        // dish par le ratio de poids total du plat
        val baseDishWeight = baseDish.weightG.coerceAtLeast(1)
        val dishWeightRatio = sumWeight.toDouble() / baseDishWeight

        val updatedDish = current.dishes[dishIndex].copy(
            weightG = sumWeight,
            calories = sumCals,
            proteins = sumProt,
            carbs = sumCarb,
            fats = sumFat,
            fibers = sumFib,
            carbsSugar = baseDish.carbsSugar * dishWeightRatio,
            fatsSaturated = baseDish.fatsSaturated * dishWeightRatio,
            salt = baseDish.salt * dishWeightRatio,
            ingredients = updatedIngredients
        )

        val updatedDishes = current.dishes.toMutableList().apply { this[dishIndex] = updatedDish }

        // Recompute global totals
        val totalCalories = updatedDishes.sumOf { it.calories }
        val totalProteins = updatedDishes.sumOf { it.proteins }
        val totalCarbs = updatedDishes.sumOf { it.carbs }
        val totalFats = updatedDishes.sumOf { it.fats }
        val totalFibers = updatedDishes.sumOf { it.fibers }
        val totalWeight = updatedDishes.sumOf { it.weightG }

        val updatedResult = current.copy(
            dishes = updatedDishes,
            totalCalories = totalCalories,
            totalProteins = totalProteins,
            totalCarbs = totalCarbs,
            totalFats = totalFats,
            totalFibers = totalFibers,
            totalWeight = totalWeight
        )

        // Recompute Nutri-Score global
        val nutriGrade = NutriScoreCalculator.fromTotals(
            calories = totalCalories,
            sugars = updatedDishes.sumOf { it.carbsSugar },
            saturatedFat = updatedDishes.sumOf { it.fatsSaturated },
            saltG = updatedDishes.sumOf { it.salt },
            fibers = totalFibers,
            proteins = totalProteins,
            weightG = totalWeight
        ).grade.toString()

        // MAJ state immédiate (UI réactive) — même si la persistance DB échoue ou n'est pas encore prête
        _state.update { it.copy(result = updatedResult) }

        // Persistance DB (seulement si le scan a déjà été inséré)
        val scanId = _state.value.savedScanId ?: return
        viewModelScope.launch {
            // 1. MealScanEntity : totaux + resultJson + nutriScoreGrade
            val scan = mealScanDao.getScanById(scanId)
            if (scan != null) {
                mealScanDao.updateScan(scan.copy(
                    totalCalories = totalCalories,
                    totalProteins = totalProteins,
                    totalCarbs = totalCarbs,
                    totalFats = totalFats,
                    totalFibers = totalFibers,
                    totalWeight = totalWeight,
                    ingredientCount = updatedDishes.sumOf { it.ingredients.size },
                    resultJson = gson.toJson(updatedResult),
                    nutriScoreGrade = nutriGrade
                ))
            }

            // 2. FoodEntity du plat concerné : nouvelle densité per-100g
            val foodId = _state.value.foodIdsPerDish[dishIndex]
            if (foodId != null && sumWeight > 0) {
                val per100 = 100.0 / sumWeight
                nutritionRepository.updateFoodMacros(
                    id = foodId,
                    caloriesPer100g = sumCals * per100,
                    proteinsPer100g = sumProt * per100,
                    carbsPer100g = sumCarb * per100,
                    fatsPer100g = sumFat * per100,
                    fiberPer100g = sumFib * per100,
                    defaultPortionGrams = sumWeight,
                    portionLabel = "${sumWeight}g"
                )
            }

            // 3. Propagation Nutri-Score global à TOUS les MealLog du scan (le grade global a pu changer)
            nutritionRepository.updateMealLogsNutriScoreByScan(scanId, nutriGrade)

            // 4. MealLogEntity du plat concerné : quantité + macros (nutriScoreGrade déjà propagé à l'étape 3)
            val mealLogId = _state.value.mealLogIdsPerDish[dishIndex]
            if (mealLogId != null) {
                nutritionRepository.updateMealLogMacros(
                    id = mealLogId,
                    quantityGrams = sumWeight,
                    calories = sumCals.toDouble(),
                    proteins = sumProt,
                    carbs = sumCarb,
                    fats = sumFat,
                    nutriScoreGrade = nutriGrade
                )
            }
        }
    }
}
