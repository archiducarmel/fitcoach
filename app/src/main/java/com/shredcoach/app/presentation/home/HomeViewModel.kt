package com.shredcoach.app.presentation.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.NutritionGoalEntity
import com.shredcoach.app.data.local.entity.NutritionScheduleEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.domain.locale.withCurrentLocale
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import com.shredcoach.app.domain.nutrition.DailyCalorieTargetCalculator
import com.shredcoach.app.domain.streak.StreakMilestoneStore
import com.shredcoach.app.domain.streak.StreakService
import com.shredcoach.app.domain.training.PlateauDetector
import com.shredcoach.app.domain.training.ProgressStatus
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.domain.wellness.WellnessStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class GreetingInfo(
    val isTodayWorkoutDay: Boolean = false,
    val hasWorkedOutToday: Boolean = false,
    val lastWorkoutWasYesterday: Boolean = false,
    val lastWorkoutVolume: Double = 0.0,
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val pendingMilestone: Int? = null,
    /**
     * Compte des séances complétées cette semaine (lundi → dimanche, semaine courante).
     * Affichage hero : "Full Body · {sessionsThisWeek}/{totalSessionsPerWeek} cette semaine".
     */
    val sessionsThisWeek: Int = 0,
    val totalSessionsPerWeek: Int = 0,
    /**
     * Décomposition des routines pratiquées cette semaine, ordonnée par
     * fréquence décroissante. Ex: `[("push", 2), ("pull", 1), ("legs", 1)]`.
     * Vide si une seule routine. Affiché en chip row sur la home pour les
     * users qui font du split.
     */
    val routinesBreakdownThisWeek: List<Pair<String, Int>> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository,
    private val scheduledWorkoutRepository: com.shredcoach.app.data.repository.ScheduledWorkoutRepository,
    private val streakService: StreakService,
    private val streakMilestoneStore: StreakMilestoneStore,
    private val plateauDetector: PlateauDetector,
    private val workoutLogDao: WorkoutLogDao,
    private val wellnessStore: WellnessStore,
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserProfileEntity?>(null)
    val userProfile: StateFlow<UserProfileEntity?> = _userProfile.asStateFlow()

    private val _exerciseCount = MutableStateFlow(0)
    val exerciseCount: StateFlow<Int> = _exerciseCount.asStateFlow()

    private val _totalWorkouts = MutableStateFlow(0)
    val totalWorkouts: StateFlow<Int> = _totalWorkouts.asStateFlow()

    private val _totalVolume = MutableStateFlow(0.0)
    val totalVolume: StateFlow<Double> = _totalVolume.asStateFlow()

    private val _totalTimeMinutes = MutableStateFlow(0)
    val totalTimeMinutes: StateFlow<Int> = _totalTimeMinutes.asStateFlow()

    private val _greetingInfo = MutableStateFlow(GreetingInfo())
    val greetingInfo: StateFlow<GreetingInfo> = _greetingInfo.asStateFlow()

    /** ID du log créé pour la séance libre, observé par le screen pour naviguer. */
    private val _freestyleLogId = MutableStateFlow<Long?>(null)
    val freestyleLogId: StateFlow<Long?> = _freestyleLogId.asStateFlow()

    /**
     * Flow qui émet la date courante et re-emit à chaque passage à minuit.
     * Évite que les flows dépendants (nutrition / mood) restent figés sur la
     * date capturée à la construction du ViewModel quand l'app reste ouverte
     * across minuit.
     *
     * Implémentation : on dort jusqu'à 00:00:01 (avec un floor de 60s pour ne pas
     * busy-loop si l'horloge système recule). À chaque tick on re-emit la nouvelle
     * date — `distinctUntilChanged` côté caller garantit qu'on ne déclenche pas
     * de recomputation inutile sur le même jour.
     */
    @Suppress("MagicNumber")
    private val todayDateFlow: Flow<LocalDate> = flow {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val now = LocalDate.now()
            emit(now)
            // Délai jusqu'au prochain minuit + 1s de marge pour éviter de retomber
            // sur la même date à cause d'imprécisions.
            val nextMidnight = now.plusDays(1).atStartOfDay().plusSeconds(1)
            val nowDt = LocalDateTime.now()
            val delayMs = Duration.between(nowDt, nextMidnight).toMillis().coerceAtLeast(60_000L)
            delay(delayMs)
        }
    }.distinctUntilChanged()

    /**
     * Today nutrition card — combine meals (Flow) + goal (Flow) + schedules (Flow).
     * `null` = pas encore initialisé (jamais après le premier emit) ; un goal vide
     * donne quand même une card valide avec target par défaut.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayNutrition: StateFlow<TodayNutrition?> = todayDateFlow.flatMapLatest { date ->
        combine(
            nutritionRepository.getMealsForDate(date),
            nutritionRepository.getNutritionGoal(),
            nutritionRepository.getEnabledSchedules(),
            workoutLogDao.getWorkoutLogsBetween(date, date),
            _userProfile,
        ) { meals, goal, schedules, workouts, profile ->
            buildTodayNutrition(
                consumedMacros = meals.map { Triple(it.calories, it.proteins, Pair(it.carbs, it.fats)) },
                goal = goal,
                schedules = schedules,
                completedWorkoutsToday = workouts.filter { it.completed },
                profile = profile,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * Jeûne nocturne — combine les repas de J + ceux de J-1 pour exposer les
     * timestamps que la card consomme (avec ticker live côté Composable).
     * Re-emit à minuit via [todayDateFlow] et à chaque ajout/suppression de
     * repas sur l'une des 2 journées.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nightFasting: StateFlow<com.shredcoach.app.presentation.home.components.NightFastingDisplay?> =
        todayDateFlow.flatMapLatest { date ->
            val yesterday = date.minusDays(1)
            combine(
                nutritionRepository.getMealsForDate(date),
                nutritionRepository.getMealsForDate(yesterday),
            ) { todayMeals, yesterdayMeals ->
                val firstToday = todayMeals.mapNotNull { it.time }.minOrNull()
                val lastYesterday = yesterdayMeals.mapNotNull { it.time }.maxOrNull()
                com.shredcoach.app.presentation.home.components.NightFastingDisplay(
                    lastMealAt = lastYesterday?.let { LocalDateTime.of(yesterday, it) },
                    firstMealAt = firstToday?.let { LocalDateTime.of(date, it) },
                    isToday = true,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Top 3 des prochaines séances planifiées (toutes périodes, statut PLANNED).
     * Utilisé par la card calendrier sur la home pour rendre la feature visible
     * et offrir un raccourci direct vers le screen calendrier.
     */
    val upcomingSessions: StateFlow<List<com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity>> =
        scheduledWorkoutRepository.getAll()
            .map { all ->
                val today = LocalDate.now()
                all.filter { it.status == "PLANNED" && !it.date.isBefore(today) }
                    .sortedWith(compareBy({ it.date }, { it.time ?: java.time.LocalTime.MIN }))
                    .take(3)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * Reprise de séance — observe le dernier log non complété, le filtre <24h
     * (auto-hide au-delà), et enrichit avec workout name + sets count.
     */
    val resumableSession: StateFlow<ResumableSession?> = workoutRepository
        .observeLatestUncompletedLog()
        .map { log -> if (log == null) null else buildResumable(log) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /**
     * Mood du jour (0..4) ou null si pas encore tapé. La card check-in
     * s'affiche/disparaît reactive à ce flow.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayMood: StateFlow<Int?> = todayDateFlow
        .flatMapLatest { date -> wellnessStore.observeMood(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Enregistre le mood du jour — la card disparaît automatiquement. */
    fun saveMood(index: Int) {
        viewModelScope.launch {
            wellnessStore.saveMood(LocalDate.now(), index)
        }
    }

    /**
     * Insight de la semaine — recomputé à chaque ajout de séance complétée.
     * Trigger via Flow sur `getAllWorkoutLogs()` filtré complétées : on observe
     * uniquement le *count* (distinctUntilChanged côté composition) pour éviter
     * de relancer 5 queries Plateau à chaque tick de chrono qui touche un set.
     */
    val weeklyInsight: StateFlow<WeeklyInsight?> = workoutRepository
        .getAllWorkoutLogs()
        .map { logs -> logs.count { it.completed } }
        .distinctUntilChanged()
        .map { _ -> computeWeeklyInsight() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        loadExerciseCount()
        observeProfileAndLogs()
        // Sync silencieux : si la valeur stockée en DB diverge de ce que
        // calcule la nouvelle formule sédentaire, on rafraîchit la DB. Comme
        // ça les pages qui lisent `goal.targetCalories` direct (Stats) voient
        // une valeur cohérente avec Home/Nutrition.
        viewModelScope.launch { syncGoalCacheWithProfile() }
    }

    /**
     * Aligne `NutritionGoalEntity.targetCalories` (cache DB) avec ce que
     * calcule [DailyCalorieTargetCalculator] depuis le profil actuel.
     *
     * Pourquoi : un user qui était sur une ancienne version (multiplicateur
     * d'activité fixe ×1.55) avait une valeur DB ~2980 kcal. Après le passage
     * au modèle sédentaire, la valeur correcte est ~2230 kcal. Tant que
     * l'user n'avait pas modifié son profil, la DB restait stale → la page
     * Stats affichait 2980 alors que Home/Nutrition affichaient 2230.
     *
     * On corrige ça en rafraîchissant silencieusement à chaque ouverture
     * de la home (idempotent : no-op si déjà à jour).
     */
    private suspend fun syncGoalCacheWithProfile() {
        val profile = userRepository.getUserProfileOnce() ?: return
        val existing = nutritionRepository.getNutritionGoalOnce() ?: return
        val expected = DailyCalorieTargetCalculator.sedentaryBaseTarget(profile)
        if (existing.targetCalories != expected) {
            nutritionRepository.saveNutritionGoal(
                existing.copy(
                    targetCalories = expected,
                    weight = profile.currentWeightKg,
                    height = profile.heightCm,
                    age = profile.age,
                    sex = profile.sex,
                    goal = profile.goal.name,
                )
            )
        }
    }

    private fun loadExerciseCount() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                _exerciseCount.value = exercises.size
            }
        }
    }

    /**
     * Observe profile + logs ensemble via [combine]. Tous les writes vers
     * [_greetingInfo] et [_userProfile] passent par UN SEUL collecteur séquentiel,
     * éliminant la race condition possible quand profile et logs émettent en
     * parallèle (chacun écrasait `_greetingInfo` avec un état partiel — flicker UI).
     */
    private fun observeProfileAndLogs() {
        viewModelScope.launch {
            combine(
                userRepository.getUserProfile(),
                workoutRepository.getAllWorkoutLogs(),
            ) { profile, logs -> profile to logs }
                .collect { (profile, logs) ->
                    _userProfile.value = profile
                    val completed = logs.filter { it.completed }
                    _totalWorkouts.value = completed.size
                    _totalVolume.value = completed.sumOf { it.totalVolume }
                    _totalTimeMinutes.value = (completed.sumOf { it.actualDurationSeconds } / 60).toInt()
                    updateGreeting(profile, completed)
                }
        }
    }

    /**
     * Crée une séance libre (workout vide + log) et expose l'ID pour navigation.
     *
     * @param routineId Routine cible (ex: `"push"`). `null` → fallback sur le
     *                  `lastUsedRoutineId` du profil (default `"full_body"`).
     *                  Permet à la home de proposer un mini-picker au lancement
     *                  ou de simplement reprendre la routine habituelle.
     */
    fun startFreestyleWorkout(routineId: String? = null) {
        viewModelScope.launch {
            val effectiveRoutineId = routineId
                ?: userRepository.getUserProfileOnce()?.lastUsedRoutineId
                ?: RoutineCatalog.Default.id
            // Garde-fou : un id inconnu est résolu en Default (jamais d'exception).
            val resolved = RoutineCatalog.byId(effectiveRoutineId).id

            val workout = WorkoutEntity(
                name = appContext.withCurrentLocale().getString(R.string.history_freestyle_session_name),
                durationMinutes = 0,
                exerciseCount = 0,
                createdAt = LocalDateTime.now(),
                isCustom = true,
                isFreestyle = true,
                routineId = resolved,
            )
            val workoutId = workoutRepository.insertWorkout(workout)
            val log = WorkoutLogEntity(
                workoutId = workoutId,
                date = LocalDateTime.now(),
                durationMinutes = 0,
                completed = false,
                routineId = resolved,
            )
            val logId = workoutRepository.insertWorkoutLog(log)
            _freestyleLogId.value = logId
        }
    }

    fun clearFreestyleLogId() { _freestyleLogId.value = null }

    /**
     * Suspend pour pouvoir lire `streakMilestoneStore.snapshot()` séquentiellement
     * (au lieu d'un launch séparé qui ouvrait une race). Appelée depuis le
     * collecteur unique [observeProfileAndLogs].
     */
    private suspend fun updateGreeting(
        profile: UserProfileEntity?,
        completedLogs: List<WorkoutLogEntity>,
    ) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Jour de seance planifie ? (workoutDays : 1=Lundi ... 7=Dimanche en Java DayOfWeek)
        val todayDayOfWeek = today.dayOfWeek.value
        val isTodayWorkoutDay = profile?.workoutDays?.contains(todayDayOfWeek) == true

        // Derniere seance = hier ?
        val lastLog = completedLogs.maxByOrNull { it.date }
        val lastDate = lastLog?.date?.toLocalDate()
        val lastWorkoutWasYesterday = lastDate == yesterday
        val lastVolume = lastLog?.totalVolume ?: 0.0

        // Streak : délégué à StreakService (source unique de vérité partagée
        // avec StreakUpdateWorker, CoachTriggerEngine, WorkoutDebriefWorker).
        val streakState = streakService.compute(completedLogs, today)
        val celebrated = streakMilestoneStore.snapshot()
        val nextToCelebrate = streakService.nextMilestoneToCelebrate(
            currentDays = streakState.currentDays,
            alreadyCelebrated = celebrated,
        )

        // Séances cette semaine — semaine ISO (lundi → dimanche).
        // weekFields.firstDayOfWeek = MONDAY en Locale.FRANCE (cohérent avec la culture user).
        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        val weekLogs = completedLogs.filter { log ->
            val d = log.date.toLocalDate()
            !d.isBefore(weekStart) && !d.isAfter(today)
        }
        val sessionsThisWeek = weekLogs.size
        val totalSessionsPerWeek = profile?.workoutDays?.size ?: 3

        // Breakdown routines de la semaine : Pair(routineId, count).
        // Affiché côté UI seulement si l'user a fait > 1 routine cette semaine
        // (sinon le hero "Push · 2/3" suffit, pas besoin de chips redondants).
        val routinesBreakdown = weekLogs
            .groupingBy { it.routineId }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
            .takeIf { it.size > 1 } ?: emptyList()

        _greetingInfo.value = GreetingInfo(
            isTodayWorkoutDay = isTodayWorkoutDay,
            hasWorkedOutToday = streakState.hasWorkedOutToday,
            lastWorkoutWasYesterday = lastWorkoutWasYesterday,
            lastWorkoutVolume = lastVolume,
            streakDays = streakState.currentDays,
            bestStreakDays = streakState.bestDays,
            pendingMilestone = nextToCelebrate,
            sessionsThisWeek = sessionsThisWeek,
            totalSessionsPerWeek = totalSessionsPerWeek,
            routinesBreakdownThisWeek = routinesBreakdown,
        )
        Log.i(
            TAG,
            "greeting: streak=${streakState.currentDays}/${streakState.bestDays} " +
                "today=${streakState.hasWorkedOutToday} pendingMilestone=$nextToCelebrate"
        )
    }

    /** Marque le palier comme célébré (l'UI a fermé la dialog). */
    fun acknowledgeMilestone(milestone: Int) {
        viewModelScope.launch {
            streakMilestoneStore.markCelebrated(milestone)
            _greetingInfo.value = _greetingInfo.value.copy(pendingMilestone = null)
        }
    }

    /**
     * Construit l'état nutrition de la journée à partir des macros consommés
     * (déjà arrondis depuis MealLog), de la cible et du planning.
     *
     * Pourquoi prendre les MealLog en `Triple` plutôt que la entity complète :
     * découplage, on n'a besoin que des macros — facilite les tests futurs.
     */
    private fun buildTodayNutrition(
        consumedMacros: List<Triple<Double, Double, Pair<Double, Double>>>,
        goal: NutritionGoalEntity?,
        schedules: List<NutritionScheduleEntity>,
        completedWorkoutsToday: List<WorkoutLogEntity>,
        profile: UserProfileEntity?,
    ): TodayNutrition {
        val cal = consumedMacros.sumOf { it.first }
        val prot = consumedMacros.sumOf { it.second }
        val carbs = consumedMacros.sumOf { it.third.first }
        val fats = consumedMacros.sumOf { it.third.second }
        val goalSafe = goal ?: NutritionGoalEntity()

        // Cible adaptative — calculée via le helper UNIQUE
        // [DailyCalorieTargetCalculator]. Garantit la cohérence avec la
        // page Nutrition (NutritionViewModel.recalcDailyTarget utilise le
        // même helper). Plus de mismatch possible entre les 2 pages.
        //
        // Fallback si profil pas encore chargé : on utilise la base DB
        // (goalSafe.targetCalories) — valeur certes stale mais évite
        // d'afficher 0 le temps du premier emit.
        val adaptiveTarget = if (profile != null) {
            DailyCalorieTargetCalculator.adaptiveTarget(profile, completedWorkoutsToday)
        } else {
            goalSafe.targetCalories
        }

        val now = LocalTime.now()
        val next = schedules
            .filter { it.time.isAfter(now) }
            .minByOrNull { it.time }
            ?.let { NextScheduleItem(name = it.name, time = it.time, type = it.type) }

        return TodayNutrition(
            caloriesConsumed = cal.toInt(),
            caloriesTarget = adaptiveTarget,
            proteinsConsumedGrams = prot.toInt(),
            proteinsTargetGrams = goalSafe.targetProteins,
            carbsConsumedGrams = carbs.toInt(),
            fatsConsumedGrams = fats.toInt(),
            next = next,
        )
    }

    /**
     * Calcule l'Insight de la semaine — choisit UN exercice à mettre en avant.
     *
     * Algo :
     *  1. Top 5 exercices par nombre de sets (proxy "exercices les plus pratiqués")
     *  2. PlateauDetector.analyze() sur chacun → 5 ExerciseProgression
     *  3. Tri par priorité métier :
     *     a. PR récent → priorité absolue
     *     b. Sinon, plus forte progression positive
     *     c. Sinon, plateau le plus long (nudge actionnable)
     *     d. Sinon, null (Stable → on n'affiche rien, évite le bruit)
     *
     * Coût : 1 query DB initiale + 5 queries indexées via PlateauDetector.
     * Recomputé seulement quand le nombre de logs complétés change (cf.
     * `distinctUntilChanged` dans le Flow), pas à chaque tick de chrono.
     */
    private suspend fun computeWeeklyInsight(): WeeklyInsight? {
        return try {
            val allSets = workoutLogDao.getAllWorkoutSetsOnce()
                .filter { it.completed && it.weightKg > 0 }
            if (allSets.isEmpty()) return null

            val topExerciseIds = allSets.groupingBy { it.exerciseId }.eachCount()
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { it.first }

            val candidates = topExerciseIds.mapNotNull { exerciseId ->
                val progression = plateauDetector.analyze(exerciseId) ?: return@mapNotNull null
                val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return@mapNotNull null
                val name = com.shredcoach.app.domain.exercise.ExerciseI18n.resolveName(appContext, exercise)
                val tone = when {
                    progression.hasFreshPr -> InsightTone.PR
                    progression.status is ProgressStatus.Progressing -> InsightTone.PROGRESS
                    progression.status is ProgressStatus.Plateau -> InsightTone.PLATEAU
                    else -> null  // Stable → on filtre
                } ?: return@mapNotNull null
                WeeklyInsight(name, progression, tone)
            }

            // Hiérarchie : PR > top progression > plateau le plus long
            candidates.firstOrNull { it.tone == InsightTone.PR }
                ?: candidates.filter { it.tone == InsightTone.PROGRESS }
                    .maxByOrNull { it.progression.weeklySlopeKg }
                ?: candidates.filter { it.tone == InsightTone.PLATEAU }
                    .maxByOrNull {
                        (it.progression.status as? ProgressStatus.Plateau)?.weeksFlat ?: 0
                    }
        } catch (e: Exception) {
            Log.w(TAG, "Insight computation failed", e)
            null
        }
    }

    /**
     * Construit la session reprenable depuis un log non terminé. Filtre <24h
     * appliqué ici (auto-hide) — la donnée reste en base pour ne pas perdre
     * les sets déjà loggés, mais sort de la home.
     */
    private suspend fun buildResumable(log: WorkoutLogEntity): ResumableSession? {
        val started = log.startTime
        val elapsed = Duration.between(started, LocalDateTime.now())
        if (elapsed.toHours() >= 24) return null  // auto-clean côté UI

        val workoutId = log.workoutId ?: return null  // log orphelin → on ignore
        val workout = workoutRepository.getWorkoutById(workoutId) ?: return null

        val completedExercises = workoutRepository.getCompletedExerciseCount(log.id)
        // Freestyle (workout.exerciseCount = 0) → on garde 0 pour signaler à l'UI
        // qu'il n'y a pas de plan total. Sinon on prend la valeur du workout.
        val totalExercises = workout.exerciseCount

        return ResumableSession(
            workoutLogId = log.id,
            workoutName = workout.name,
            startedAt = started,
            elapsedMinutes = elapsed.toMinutes().toInt().coerceAtLeast(0),
            completedExercises = completedExercises,
            totalExercises = totalExercises,
        )
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
