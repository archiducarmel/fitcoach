package com.shredcoach.app.notification

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealType
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.coach.CoachHistoryStore
import com.shredcoach.app.domain.coach.CoachSettingsStore
import com.shredcoach.app.domain.nutrition.MealTypeClassifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.max

/**
 * Worker déclenché après un scan de repas pour générer un débrief contextualisé.
 *
 * Refonte V6 :
 * - **Anti-stale** : skip si le repas date de plus de [STALE_THRESHOLD]. Permet
 *   de scanner J-3 sans recevoir une notif "il y a 45 min" mensongère.
 * - **Quiet hours** : skip si exécution hors fenêtre QUIET_START..QUIET_END.
 * - **Cooldown** : skip si un débrief repas a déjà été émis récemment
 *   (anti-spam scans successifs entrée/plat/dessert).
 * - **Contexte enrichi** : autres repas du jour, séance faite, comparaison veille,
 *   Nutri-Score, fibres/sucres/sat fat/sel, position dans la journée.
 * - **Ton aligné** sur [CoachSettingsStore.Tone] (cohérence avec coach proactif).
 * - **Title contextualisé** par [MealTypeClassifier.Category].
 * - **Deeplink + action** vers l'écran nutrition.
 * - **Logging structuré** : skip raison / source LLM ou local / longueur body.
 */
@HiltWorker
class MealDebriefWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val mealScanDao: MealScanDao,
    private val nutritionDao: NutritionDao,
    private val workoutLogDao: WorkoutLogDao,
    private val chatRepository: ChatRepository,
    private val coachSettings: CoachSettingsStore,
    private val coachHistory: CoachHistoryStore,
    private val dispatcher: AppNotificationDispatcher,
    private val llmResolver: com.shredcoach.app.domain.llm.AssistantLlmResolver,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scanId = inputData.getLong(KEY_SCAN_ID, -1L)
        if (scanId <= 0) {
            Log.w(TAG, "Pas de scanId fourni (input=$inputData), abandon")
            return Result.failure()
        }

        // ─── Gates 1-4 : profile, notifs, scan-stale, quiet-hours, cooldown ──────
        val profile = userRepository.getUserProfileOnce() ?: run {
            Log.i(TAG, "Skip scan=$scanId : aucun profil")
            return Result.success()
        }
        if (!profile.notificationsEnabled || !profile.notifMealDebrief) {
            Log.i(TAG, "Skip scan=$scanId : notifs désactivées (global=${profile.notificationsEnabled}, mealDebrief=${profile.notifMealDebrief})")
            return Result.success()
        }

        val scan = mealScanDao.getScanById(scanId) ?: run {
            Log.i(TAG, "Skip scan=$scanId : scan introuvable (supprimé ?)")
            return Result.success()
        }

        val now = LocalDateTime.now()
        val timeSinceMeal = Duration.between(scan.timestamp, now)
        // F1 : repas trop ancien → silence radio. Cas typique : utilisateur scanne
        // aujourd'hui un repas qu'il a pris il y a 3 jours via l'override datetime.
        if (timeSinceMeal > STALE_THRESHOLD) {
            Log.i(TAG, "Skip scan=$scanId : repas trop ancien (${timeSinceMeal.toHours()}h écoulées > seuil ${STALE_THRESHOLD.toHours()}h)")
            return Result.success()
        }
        // Cas symétrique : si le mealDateTime est dans le futur (utilisateur planifie
        // un repas pas encore pris), on n'envoie rien — c'est un débrief, pas un rappel.
        if (timeSinceMeal.isNegative) {
            Log.i(TAG, "Skip scan=$scanId : repas dans le futur (${(-timeSinceMeal.toMinutes())}min)")
            return Result.success()
        }

        // F3 : quiet hours — pas de notif débrief en pleine nuit ou tôt le matin.
        val nowTime = now.toLocalTime()
        if (nowTime.isBefore(QUIET_START) || nowTime.isAfter(QUIET_END)) {
            Log.i(TAG, "Skip scan=$scanId : hors quiet hours ($nowTime hors [$QUIET_START..$QUIET_END])")
            return Result.success()
        }

        // F4 : cooldown 90min — anti-spam quand l'utilisateur scanne plusieurs plats
        // en succession (entrée → plat → dessert).
        if (coachHistory.isOnCooldown(CoachHistoryStore.MEAL_DEBRIEF_CATEGORY, COOLDOWN)) {
            Log.i(TAG, "Skip scan=$scanId : cooldown actif (${COOLDOWN.toMinutes()}min depuis dernier débrief repas)")
            return Result.success()
        }

        // ─── Contexte enrichi ───────────────────────────────────────────────────
        val today = LocalDate.now()
        val mealDate = scan.timestamp.toLocalDate()
        val ctx = buildContext(scan, profile, today, mealDate, now)

        // ─── Ton + prompts ──────────────────────────────────────────────────────
        val tone = coachSettings.snapshot.first().tone
        val systemPrompt = DebriefPrompts.buildMealSystemPrompt(tone)
        // v45 : passe les valeurs EFFECTIVES (×N portions − restes) au LLM.
        // Si l'user a tapé "+ portion" ou scanné des restes entre le scan et le
        // débrief (45min plus tard par défaut), le coach doit raisonner sur ce
        // qui a été réellement consommé. Facteur = 1.0 si aucun modificateur.
        val factor = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveFactor(scan)
        val effectiveCalories = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveCalories(scan)
        val effectiveProteins = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveProteins(scan)
        val effectiveCarbs = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveCarbs(scan)
        val effectiveFats = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveFats(scan)
        val effectiveFibers = com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveFibers(scan)
        val userPrompt = DebriefPrompts.buildMealDebriefPrompt(
            firstName = profile.firstName.ifBlank { "" },
            dishName = scan.dishName,
            dishCount = ctx.dishCount,
            mealTypeDisplay = MealTypeClassifier.fromId(scan.mealType).displayName,
            minutesSinceMeal = max(1L, timeSinceMeal.toMinutes()),
            calories = effectiveCalories,
            proteins = effectiveProteins,
            carbs = effectiveCarbs,
            fats = effectiveFats,
            fibers = effectiveFibers,
            // Sucres / sat fat / sel : aussi scaled par le facteur (cohérence
            // avec la composition du repas que l'utilisateur a réellement
            // consommé). Si pas de modificateur, factor=1.0 → valeurs raw.
            sugars = (ctx.sugars * factor).coerceAtLeast(0.0),
            saturatedFat = (ctx.saturatedFat * factor).coerceAtLeast(0.0),
            saltG = (ctx.saltG * factor).coerceAtLeast(0.0),
            nutriScoreGrade = scan.nutriScoreGrade,
            healthScore = scan.healthScore,
            mealsLoggedToday = ctx.mealsLoggedToday,
            otherMealsToday = ctx.otherMealsToday,
            dailyCaloriesSoFar = ctx.dailyCalories,
            dailyCaloriesTarget = ctx.targetCalories,
            dailyProteinsSoFar = ctx.dailyProteins,
            dailyProteinsTarget = ctx.targetProteins,
            workoutDoneToday = ctx.workoutDoneToday,
            workoutVolumeKg = ctx.workoutVolumeKg,
            yesterdayCalsAtSamePoint = ctx.yesterdayCalsAtSamePoint,
            remainingMealSlots = ctx.remainingMealSlots,
            isEndOfDay = ctx.isEndOfDay,
            goalName = profile.goal.name,
        )

        // ─── LLM (avec timeout + fallback) ──────────────────────────────────────
        val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM)
        val llmMessage = if (apiKey.isNotBlank()) {
            try {
                // Resolver per-assistant : MEAL_DEBRIEF configurable via Settings.
                val llmConfig = llmResolver.resolveWithProfile(com.shredcoach.app.domain.llm.AiAssistant.MEAL_DEBRIEF, profile)
                val provider = llmConfig.provider
                val model: String? = llmConfig.modelId
                withTimeout(25_000) {
                    chatRepository.quickCoachMessage(
                        prompt = userPrompt,
                        systemPrompt = systemPrompt,
                        provider = provider,
                        apiKey = apiKey,
                        model = model,
                        assistant = com.shredcoach.app.domain.llm.AiAssistant.MEAL_DEBRIEF,
                        fallback = llmResolver.buildFallbackConfig(
                            com.shredcoach.app.domain.llm.AiAssistant.MEAL_DEBRIEF, profile, apiKey,
                        ),
                    )
                }.getOrNull()?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w(TAG, "Appel LLM échoué (fallback local activé)", e)
                null
            }
        } else null

        val body = llmMessage ?: fallbackMessage(scan.dishName, ctx, profile.goal.name)
        val source = if (llmMessage != null) "llm" else "local"
        val title = buildTitle(MealTypeClassifier.fromId(scan.mealType), ctx.dishCount)

        // F10 : deeplink vers l'écran nutrition + bouton d'action
        dispatcher.dispatch(
            type = NotifType.MEAL_DEBRIEF,
            title = title,
            body = body,
            channelId = ShredCoachApplication.CHANNEL_DEBRIEF,
            source = source,
            deeplink = DEEPLINK_NUTRITION,
            actions = listOf(
                AppNotificationDispatcher.NotificationAction(
                    label = context.getString(R.string.meal_debrief_action_view_day),
                    deeplinkRoute = DEEPLINK_NUTRITION,
                )
            ),
        )

        coachHistory.recordEmission(CoachHistoryStore.MEAL_DEBRIEF_CATEGORY)
        Log.i(
            TAG,
            "Débrief émis scan=$scanId source=$source mealType=${scan.mealType} " +
                "kcal=$effectiveCalories(×${scan.servingMultiplier}, leftover=${scan.leftoverCalories}) " +
                "bodyLen=${body.length} title='$title'"
        )
        return Result.success()
    }

    // ════════════════════════ Construction du contexte ═══════════════════════════

    /**
     * Snapshot read-only des infos nécessaires au prompt + au fallback. Tout est
     * calculé ici en une passe pour qu'un éventuel échec d'agrégation ne bloque
     * pas le débrief (les valeurs ont des defaults sûrs).
     */
    private suspend fun buildContext(
        scan: com.shredcoach.app.data.local.entity.MealScanEntity,
        profile: UserProfileEntity,
        today: LocalDate,
        mealDate: LocalDate,
        now: LocalDateTime,
    ): DebriefContext {
        val mealsForDate = runCatching { nutritionDao.getMealsForDateOnce(mealDate) }
            .getOrDefault(emptyList())
        val dayTotals = runCatching { nutritionDao.getDayTotals(mealDate) }.getOrNull()
        val goal = runCatching { nutritionDao.getNutritionGoalOnce() }.getOrNull()

        val targetCalories = goal?.targetCalories ?: 2200
        val targetProteins = goal?.targetProteins ?: 150
        val dailyCalories = (dayTotals?.totalCalories ?: 0.0).toInt()
        val dailyProteins = dayTotals?.totalProteins ?: 0.0

        val mealsLoggedToday = mealsForDate.distinctBy { it.scanId ?: it.id }.size
        val otherMealsToday = describeOtherMeals(mealsForDate, scan.id)

        val workoutsToday = runCatching {
            workoutLogDao.getRecentWorkoutLogs(10).first()
        }.getOrDefault(emptyList())
            .filter { it.completed && it.date.toLocalDate() == mealDate }
        val workoutDoneToday = workoutsToday.isNotEmpty()
        val workoutVolumeKg = workoutsToday.sumOf { it.totalVolume }.toInt()

        val yesterdayCalsAtSamePoint = if (mealDate == today) {
            // On ne compare qu'au cumul d'hier à la même heure (utile à midi/soir).
            // v45 : applique le facteur effectif par meal_log via lookup du scan
            // parent — sinon "hier à 14h tu avais consommé X" ne reflète pas les
            // ×N portions / restes appliqués depuis.
            runCatching {
                val yest = today.minusDays(1)
                val mealsYest = nutritionDao.getMealsForDateOnce(yest)
                val cutoffTime = now.toLocalTime()
                val scanCache = mutableMapOf<Long, com.shredcoach.app.data.local.entity.MealScanEntity?>()
                mealsYest
                    .filter { it.time?.isBefore(cutoffTime) ?: true }
                    .sumOf { m ->
                        val factor = m.scanId?.let { sid ->
                            scanCache.getOrPut(sid) { mealScanDao.getScanById(sid) }
                        }?.let {
                            com.shredcoach.app.domain.nutrition.MealScanModifierMath.effectiveFactor(it)
                        } ?: 1.0
                        m.calories * factor
                    }
                    .toInt()
            }.getOrDefault(0)
        } else 0

        // Position dans la journée : combien de slots de repas restent après celui-ci.
        // On se base sur les notifs activées par l'utilisateur (sa routine déclarée).
        val expectedSlots = expectedMealSlots(profile)
        val takenSlots = mealsForDate.map { categoryDisplayName(it.mealType) }.toSet()
        val remainingMealSlots = expectedSlots.count { it !in takenSlots }
        val isEndOfDay = remainingMealSlots == 0 ||
            scan.mealType == MealTypeClassifier.DINER.id ||
            now.toLocalTime().isAfter(LocalTime.of(21, 0))

        // Détail micro (sucres, sat fat, sel) — extrait du resultJson si dispo,
        // sinon defaults 0.0 pour ne pas faire halluciner le LLM.
        val (sugars, satFat, saltG) = parseMicroDetails(scan.resultJson)

        return DebriefContext(
            dishCount = parseDishCount(scan.resultJson, scan.ingredientCount),
            mealsLoggedToday = mealsLoggedToday,
            otherMealsToday = otherMealsToday,
            dailyCalories = dailyCalories,
            targetCalories = targetCalories,
            dailyProteins = dailyProteins,
            targetProteins = targetProteins,
            workoutDoneToday = workoutDoneToday,
            workoutVolumeKg = workoutVolumeKg,
            yesterdayCalsAtSamePoint = yesterdayCalsAtSamePoint,
            remainingMealSlots = remainingMealSlots,
            isEndOfDay = isEndOfDay,
            sugars = sugars,
            saturatedFat = satFat,
            saltG = saltG,
        )
    }

    private fun describeOtherMeals(meals: List<MealLogEntity>, currentScanId: Long): List<String> =
        meals
            .filter { it.scanId != currentScanId }
            .map { categoryDisplayName(it.mealType) }
            .distinct()

    /**
     * Mapping [MealType] (enum DB des MealLog) → displayName de [MealTypeClassifier]
     * (sémantique journée). On force la cohérence d'affichage entre les meal logs
     * (potentiellement créés via tracking manuel hors scan) et la classification
     * horaire du scanner. Sans ça `it.mealType.displayName` renverrait "Snack"
     * (anglais via [MealType.SNACK]) au lieu de "Goûter".
     */
    private fun categoryDisplayName(mealType: MealType): String = when (mealType) {
        MealType.BREAKFAST -> MealTypeClassifier.PETIT_DEJEUNER.displayName
        MealType.LUNCH -> MealTypeClassifier.DEJEUNER.displayName
        MealType.SNACK -> MealTypeClassifier.GOUTER.displayName
        MealType.DINNER -> MealTypeClassifier.DINER.displayName
        MealType.PRE_WORKOUT, MealType.SHAKE -> MealTypeClassifier.PRETRAINING.displayName
        MealType.POST_WORKOUT -> MealTypeClassifier.GRIGNOTAGE.displayName
    }

    /**
     * Slots de repas attendus dans la routine déclarée par l'utilisateur via
     * les flags notifBreakfast / notifLunch / etc. Sert à calculer combien de
     * repas restent à prendre dans la journée.
     */
    private fun expectedMealSlots(profile: UserProfileEntity): Set<String> = buildSet {
        if (profile.notifBreakfast) add(MealTypeClassifier.PETIT_DEJEUNER.displayName)
        if (profile.notifLunch) add(MealTypeClassifier.DEJEUNER.displayName)
        if (profile.notifSnack) add(MealTypeClassifier.GOUTER.displayName)
        if (profile.notifDinner) add(MealTypeClassifier.DINER.displayName)
    }

    /**
     * Compte le nombre de plats dans le scan via le resultJson (champ "dishes").
     * Fallback : ingredientCount (peut surestimer mais reste le moins faux dispo).
     */
    private fun parseDishCount(resultJson: String, ingredientCount: Int): Int {
        if (resultJson.isBlank()) return 1
        return try {
            val regex = Regex("\"dishes\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(resultJson) ?: return 1
            val nameMatches = Regex("\"name\"\\s*:").findAll(match.groupValues[1]).count()
            // Évite de confondre name d'ingrédient et name de plat — on garde
            // le min entre nameMatches et un plafond raisonnable.
            nameMatches.coerceIn(1, ingredientCount.coerceAtLeast(1))
        } catch (_: Exception) {
            1
        }
    }

    /**
     * Extrait sucres / saturated fat / salt agrégés depuis le JSON du scan.
     * Si parsing échoue ou champs absents → triple de zéros (le prompt s'adaptera).
     */
    private fun parseMicroDetails(resultJson: String): Triple<Double, Double, Double> {
        if (resultJson.isBlank()) return Triple(0.0, 0.0, 0.0)
        return try {
            var sugars = 0.0
            var satFat = 0.0
            var salt = 0.0
            Regex("\"carbsSugar\"\\s*:\\s*([0-9.]+)").findAll(resultJson)
                .forEach { sugars += it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            Regex("\"fatsSaturated\"\\s*:\\s*([0-9.]+)").findAll(resultJson)
                .forEach { satFat += it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            Regex("\"salt\"\\s*:\\s*([0-9.]+)").findAll(resultJson)
                .forEach { salt += it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            // Approximation : on a la somme sur tous les plats ET tous les ingrédients
            // (doublon car chaque plat agrège ses ingrédients). On divise par 2 pour
            // approximer le total au niveau plat.
            Triple(sugars / 2.0, satFat / 2.0, salt / 2.0)
        } catch (_: Exception) {
            Triple(0.0, 0.0, 0.0)
        }
    }

    // ════════════════════════ Fallback enrichi ═══════════════════════════════════

    /**
     * Fallback local appelé si le LLM est indispo (clé absente, timeout, erreur).
     * 6 variations selon mealType × position dans la journée × objectif. Toutes
     * factuelles, chiffrées, alignées sur les règles "constat → action".
     *
     * i18n : tous les littéraux sont passés par R.string (Phase 4b).
     */
    private fun fallbackMessage(
        dishName: String,
        ctx: DebriefContext,
        goalName: String,
    ): String {
        val deficitCals = (ctx.targetCalories - ctx.dailyCalories).coerceAtLeast(0)
        val deficitProt = (ctx.targetProteins - ctx.dailyProteins.toInt()).coerceAtLeast(0)
        val dishLabel = dishName.ifBlank { context.getString(R.string.meal_debrief_default_dish) }
        val proteinsInt = ctx.dailyProteins.toInt()

        return when {
            ctx.isEndOfDay && goalName == "SHRED" -> {
                val verdict = if (ctx.dailyCalories > ctx.targetCalories) {
                    context.getString(
                        R.string.meal_debrief_fallback_eod_shred_over,
                        ctx.dailyCalories, ctx.targetCalories,
                    )
                } else {
                    context.getString(
                        R.string.meal_debrief_fallback_eod_shred_aligned,
                        ctx.dailyCalories, ctx.targetCalories,
                    )
                }
                context.getString(R.string.meal_debrief_fallback_eod_shred, verdict, proteinsInt)
            }
            ctx.isEndOfDay -> context.getString(
                R.string.meal_debrief_fallback_eod_general,
                ctx.dailyCalories, ctx.targetCalories,
                proteinsInt, ctx.targetProteins,
            )
            ctx.workoutDoneToday && deficitProt > 30 -> context.getString(
                R.string.meal_debrief_fallback_post_workout,
                dishLabel, proteinsInt, deficitProt,
            )
            deficitProt > 50 -> context.getString(
                R.string.meal_debrief_fallback_low_prot,
                dishLabel, proteinsInt, ctx.targetProteins, deficitProt,
            )
            deficitCals < 200 && goalName == "SHRED" -> context.getString(
                R.string.meal_debrief_fallback_near_target_shred,
                ctx.dailyCalories, ctx.targetCalories,
            )
            else -> context.getString(
                R.string.meal_debrief_fallback_default,
                dishLabel,
                ctx.dailyCalories, ctx.targetCalories,
                proteinsInt, ctx.targetProteins,
                ctx.remainingMealSlots,
            )
        }
    }

    /**
     * Title contextualisé selon le type de repas et le nombre de plats.
     * Valeurs UTF-8 directes (les emojis fonctionnent en push notif Android).
     */
    private fun buildTitle(category: MealTypeClassifier.Category, dishCount: Int): String {
        val baseRes = when (category.id) {
            MealTypeClassifier.PETIT_DEJEUNER.id -> R.string.meal_debrief_title_breakfast
            MealTypeClassifier.DEJEUNER.id       -> R.string.meal_debrief_title_lunch
            MealTypeClassifier.GOUTER.id         -> R.string.meal_debrief_title_snack
            MealTypeClassifier.DINER.id          -> R.string.meal_debrief_title_dinner
            MealTypeClassifier.PRETRAINING.id    -> R.string.meal_debrief_title_pretraining
            MealTypeClassifier.GRIGNOTAGE.id     -> R.string.meal_debrief_title_grignotage
            else -> R.string.meal_debrief_title_default
        }
        val base = context.getString(baseRes)
        return if (dishCount > 1) {
            context.getString(R.string.meal_debrief_title_with_count, base, dishCount)
        } else base
    }

    private data class DebriefContext(
        val dishCount: Int,
        val mealsLoggedToday: Int,
        val otherMealsToday: List<String>,
        val dailyCalories: Int,
        val targetCalories: Int,
        val dailyProteins: Double,
        val targetProteins: Int,
        val workoutDoneToday: Boolean,
        val workoutVolumeKg: Int,
        val yesterdayCalsAtSamePoint: Int,
        val remainingMealSlots: Int,
        val isEndOfDay: Boolean,
        val sugars: Double,
        val saturatedFat: Double,
        val saltG: Double,
    )

    companion object {
        const val KEY_SCAN_ID = "scan_id"
        const val DEFAULT_DELAY_MINUTES = 45L
        fun uniqueTag(scanId: Long) = "meal_debrief_$scanId"

        private const val TAG = "MealDebriefWorker"
        private const val DEEPLINK_NUTRITION = "nutrition"

        /**
         * Au-delà de cette ancienneté, le repas est considéré "trop vieux" et
         * le débrief est skippé. 4h couvre les cas où le worker s'est exécuté
         * en retard (constraints réseau / batterie) sans tomber dans l'absurde.
         */
        private val STALE_THRESHOLD: Duration = Duration.ofHours(4)

        /**
         * Cooldown global entre 2 débriefs repas. Empêche le spam quand
         * l'utilisateur scanne plusieurs plats à la suite (ex: entrée + plat
         * + dessert d'un même repas, ou plusieurs photos de la même assiette).
         */
        private val COOLDOWN: Duration = Duration.ofMinutes(90)

        /** Plage horaire pendant laquelle on accepte d'émettre. */
        private val QUIET_START: LocalTime = LocalTime.of(8, 0)
        private val QUIET_END: LocalTime = LocalTime.of(21, 30)
    }
}
