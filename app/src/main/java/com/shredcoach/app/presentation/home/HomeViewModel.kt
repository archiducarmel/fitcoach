package com.shredcoach.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class GreetingInfo(
    val isTodayWorkoutDay: Boolean = false,
    val hasWorkedOutToday: Boolean = false,
    val lastWorkoutWasYesterday: Boolean = false,
    val lastWorkoutVolume: Double = 0.0,
    val streakDays: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository
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

    init {
        loadUserProfile()
        loadExerciseCount()
        observeWorkoutLogs()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { profile ->
                _userProfile.value = profile
                // Recalculer le greeting avec les nouveaux workoutDays
                val current = _greetingInfo.value
                val todayDayOfWeek = LocalDate.now().dayOfWeek.value
                _greetingInfo.value = current.copy(
                    isTodayWorkoutDay = profile?.workoutDays?.contains(todayDayOfWeek) == true
                )
            }
        }
    }

    private fun loadExerciseCount() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                _exerciseCount.value = exercises.size
            }
        }
    }

    /** Observer les workout logs — valeurs absolues (all time) + greeting */
    private fun observeWorkoutLogs() {
        viewModelScope.launch {
            workoutRepository.getAllWorkoutLogs().collect { logs ->
                val completed = logs.filter { it.completed }
                _totalWorkouts.value = completed.size
                _totalVolume.value = completed.sumOf { it.totalVolume }
                _totalTimeMinutes.value = (completed.sumOf { it.actualDurationSeconds } / 60).toInt()
                updateGreeting(completed)
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

    private fun updateGreeting(completedLogs: List<com.shredcoach.app.data.local.entity.WorkoutLogEntity>) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val profile = _userProfile.value

        // Jour de seance planifie ? (workoutDays : 1=Lundi ... 7=Dimanche en Java DayOfWeek)
        val todayDayOfWeek = today.dayOfWeek.value
        val isTodayWorkoutDay = profile?.workoutDays?.contains(todayDayOfWeek) == true

        // Derniere seance = hier ?
        val lastLog = completedLogs.maxByOrNull { it.date }
        val lastDate = lastLog?.date?.toLocalDate()
        val lastWorkoutWasYesterday = lastDate == yesterday
        val lastVolume = lastLog?.totalVolume ?: 0.0

        // Streak : nombre de jours consecutifs avec au moins 1 seance (en remontant depuis aujourd'hui ou hier)
        val datesWithWorkout = completedLogs.map { it.date.toLocalDate() }.toSet()
        val hasWorkedOutToday = datesWithWorkout.contains(today)
        var streak = 0
        var cursor = if (hasWorkedOutToday) today else yesterday
        while (datesWithWorkout.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }

        _greetingInfo.value = GreetingInfo(
            isTodayWorkoutDay = isTodayWorkoutDay,
            hasWorkedOutToday = hasWorkedOutToday,
            lastWorkoutWasYesterday = lastWorkoutWasYesterday,
            lastWorkoutVolume = lastVolume,
            streakDays = streak
        )
    }
}
