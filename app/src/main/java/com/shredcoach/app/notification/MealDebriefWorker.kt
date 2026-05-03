package com.shredcoach.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * Worker déclenché 45 min après chaque scan de repas (ou ajout manuel).
 * Génère un débrief humoristique personnalisé via LLM avec le contexte complet
 * (repas + progression quotidienne + objectif).
 */
@HiltWorker
class MealDebriefWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val nutritionRepository: NutritionRepository,
    private val mealScanDao: MealScanDao,
    private val chatRepository: ChatRepository,
    private val dispatcher: AppNotificationDispatcher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scanId = inputData.getLong(KEY_SCAN_ID, -1L)
        if (scanId <= 0) return Result.failure()

        val profile = userRepository.getUserProfileOnce() ?: return Result.success()
        if (!profile.notificationsEnabled || !profile.notifMealDebrief) return Result.success()

        // Retrouver le scan d'origine
        val scan = mealScanDao.getScanById(scanId) ?: return Result.success()

        // Contexte nutrition du jour en cours
        val today = LocalDate.now()
        val dayTotals = try { nutritionRepository.getDayTotals(today) } catch (_: Exception) { null }
        val goal = try { nutritionRepository.getNutritionGoalOnce() } catch (_: Exception) { null }

        // Construire le prompt avec contexte
        val prompt = DebriefPrompts.buildMealDebriefPrompt(
            firstName = profile.firstName.ifBlank { "toi" },
            dishName = scan.dishName.ifBlank { "le repas" },
            calories = scan.totalCalories,
            proteins = scan.totalProteins,
            carbs = scan.totalCarbs,
            fats = scan.totalFats,
            healthScore = scan.healthScore,
            mealType = com.shredcoach.app.domain.nutrition.MealTypeClassifier
                .fromId(scan.mealType).displayName.lowercase(),
            dailyCaloriesSoFar = (dayTotals?.totalCalories ?: 0.0).toInt(),
            dailyCaloriesTarget = goal?.targetCalories ?: 2200,
            dailyProteinsSoFar = dayTotals?.totalProteins ?: 0.0,
            dailyProteinsTarget = goal?.targetProteins ?: 150,
            goalName = profile.goal.name
        )

        // Appeler le LLM (fallback local si échec/timeout/no-key)
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val llmMessage = if (apiKey.isNotBlank()) {
            try {
                val provider = runCatching { LlmProvider.valueOf(profile.llmProvider) }.getOrDefault(LlmProvider.GROQ)
                val model = profile.llmModel.takeIf { it.isNotBlank() }
                val result = withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = prompt,
                        systemPrompt = DebriefPrompts.DEBRIEF_SYSTEM_PROMPT,
                        provider = provider,
                        apiKey = apiKey,
                        model = model
                    )
                }
                result.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (_: Exception) { null }
        } else null

        val body = llmMessage ?: fallbackMealMessage(scan.dishName, scan.totalCalories)
        val source = if (llmMessage != null) "llm" else "local"

        dispatcher.dispatch(
            type = NotifType.MEAL_DEBRIEF,
            title = "🍽 Débrief de ${scan.dishName.ifBlank { "ton repas" }}",
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source
        )
        return Result.success()
    }

    private fun fallbackMealMessage(dishName: String, cal: Int): String =
        if (cal > 0) "Ton $dishName à ${cal}kcal est digéré. Prochain repas : pense à la balance protéines/légumes."
        else "Repas digéré. Reste hydraté et pense à ton prochain apport en protéines."

    companion object {
        const val KEY_SCAN_ID = "scan_id"
        /** Délai par défaut si aucune valeur custom dans les settings. */
        const val DEFAULT_DELAY_MINUTES = 45L
        /** Tag unique par scan pour éviter les doublons. */
        fun uniqueTag(scanId: Long) = "meal_debrief_$scanId"
    }
}
