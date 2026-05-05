package com.shredcoach.app.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.NutritionGoalEntity
import com.shredcoach.app.data.local.entity.NutritionScheduleEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.streak.StreakMilestoneStore
import com.shredcoach.app.domain.streak.StreakService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository,
    private val streakService: StreakService,
    private val streakMilestoneStore: StreakMilestoneStore,
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
     * Today nutrition card — combine meals (Flow) + goal (Flow) + schedules (Flow).
     * `null` = pas encore initialisé (jamais après le premier emit) ; un goal vide
     * donne quand même une card valide avec target par défaut.
     */
    val todayNutrition: StateFlow<TodayNutrition?> = combine(
        nutritionRepository.getMealsForDate(LocalDate.now()),
        nutritionRepository.getNutritionGoal(),
        nutritionRepository.getEnabledSchedules(),
    ) { meals, goal, schedules ->
        buildTodayNutrition(meals.map { Triple(it.calories, it.proteins, Pair(it.carbs, it.fats)) }, goal, schedules)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
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

    init {
        loadExerciseCount()
        observeProfileAndLogs()
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

    /** Crée une séance libre (workout vide + log) et expose l'ID pour navigation. */
    fun startFreestyleWorkout() {
        viewModelScope.launch {
            val workout = WorkoutEntity(
                name = "Séance libre",
                durationMinutes = 0,
                exerciseCount = 0,
                createdAt = LocalDateTime.now(),
                isCustom = true
            )
            val workoutId = workoutRepository.insertWorkout(workout)
            val log = WorkoutLogEntity(
                workoutId = workoutId,
                date = LocalDateTime.now(),
                durationMinutes = 0,
                completed = false
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

        _greetingInfo.value = GreetingInfo(
            isTodayWorkoutDay = isTodayWorkoutDay,
            hasWorkedOutToday = streakState.hasWorkedOutToday,
            lastWorkoutWasYesterday = lastWorkoutWasYesterday,
            lastWorkoutVolume = lastVolume,
            streakDays = streakState.currentDays,
            bestStreakDays = streakState.bestDays,
            pendingMilestone = nextToCelebrate,
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
    ): TodayNutrition {
        val cal = consumedMacros.sumOf { it.first }
        val prot = consumedMacros.sumOf { it.second }
        val carbs = consumedMacros.sumOf { it.third.first }
        val fats = consumedMacros.sumOf { it.third.second }
        // Cible par défaut si pas de goal en base — l'user peut quand même utiliser l'app.
        val goalSafe = goal ?: NutritionGoalEntity()

        val now = LocalTime.now()
        // Prochain item du planning : premier dont l'heure est > maintenant.
        // Si tout le planning est passé (fin de journée), on retourne null —
        // l'UI affichera "Plus rien de prévu aujourd'hui".
        val next = schedules
            .filter { it.time.isAfter(now) }
            .minByOrNull { it.time }
            ?.let { NextScheduleItem(name = it.name, time = it.time, type = it.type) }

        return TodayNutrition(
            caloriesConsumed = cal.toInt(),
            caloriesTarget = goalSafe.targetCalories,
            proteinsConsumedGrams = prot.toInt(),
            proteinsTargetGrams = goalSafe.targetProteins,
            carbsConsumedGrams = carbs.toInt(),
            fatsConsumedGrams = fats.toInt(),
            next = next,
        )
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
        val totalExercises = workout.exerciseCount.coerceAtLeast(completedExercises)

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
