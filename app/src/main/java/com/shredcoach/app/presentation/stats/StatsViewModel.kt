package com.shredcoach.app.presentation.stats

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.dao.*
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.repository.StatsRepository
import com.shredcoach.app.domain.model.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

// ── Période temporelle ──
enum class TimePeriod(val label: String, val days: Long) {
    WEEK("7j", 7), MONTH("30j", 30), QUARTER("90j", 90), YEAR("1 an", 365), ALL("Tout", 3650)
}

// ── Data classes ──
data class PRDisplay(val exerciseName: String, val weight: Double, val reps: Int, val estimated1RM: Double)
data class WeightPoint(val date: LocalDate, val weight: Double, val reps: Int)
data class VolumeBar(val label: String, val volume: Double)
data class MuscleSlice(val muscleGroup: String, val displayName: String, val count: Int, val percentage: Float)
data class PeriodComparison(
    val currentWorkouts: Int, val previousWorkouts: Int,
    val currentVolume: Double, val previousVolume: Double,
    val workoutDelta: Float, // % change
    val volumeDelta: Float, // % change
    val insight: String
)
data class TrendData(
    val slope: Double, // kg/semaine de progression
    val isPlateauing: Boolean,
    val plateauWeeks: Int,
    val projectedWeight4Weeks: Double,
    val suggestion: String
)

// ── État ──
data class StatsState(
    val isLoading: Boolean = true,
    val selectedPeriod: TimePeriod = TimePeriod.MONTH,

    // Summary période
    val workoutCount: Int = 0,
    val totalVolume: Double = 0.0,
    val totalDuration: Long = 0,
    val totalReps: Int = 0,
    val estimatedCalories: Int = 0,

    // Summary ce mois
    val monthWorkouts: Int = 0,
    val monthVolume: Double = 0.0,

    // All time
    val allTimeWorkouts: Int = 0,
    val allTimeVolume: Double = 0.0,
    val allTimeDuration: Long = 0,
    val mostTrainedMuscle: String = "",
    val mostDoneExercise: String = "",

    // Temps par catégorie (en secondes, all time)
    val warmupSeconds: Long = 0,
    val cardioSeconds: Long = 0,
    val strengthSeconds: Long = 0,

    // PRs
    val personalRecords: List<PRDisplay> = emptyList(),

    // Weight progression
    val exercises: List<ExerciseEntity> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val weightProgression: List<WeightPoint> = emptyList(),

    // Volume chart
    val weeklyVolume: List<VolumeBar> = emptyList(),
    val volumeChangePercent: Float = 0f, // vs semaine précédente
    val movingAverage: List<Float> = emptyList(), // moyenne mobile 4 semaines

    // Muscle distribution
    val muscleDistribution: List<MuscleSlice> = emptyList(),

    // Training frequency
    val trainingDays: Map<LocalDate, Int> = emptyMap(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weeklyCompliance: Float = 0f, // % par rapport à l'objectif

    // Comparaison
    val comparison: PeriodComparison? = null,

    // Tendances
    val trend: TrendData? = null
)

data class NutritionStatsData(
    val avgCalories: Int = 0,
    val avgProteins: Int = 0,
    val avgCarbs: Int = 0,
    val avgFats: Int = 0,
    val daysTracked: Int = 0,
    val targetCalories: Int = 2200,
    val targetProteins: Int = 180,
    val complianceDays: Int = 0,
    val totalScans: Int = 0,
    val avgHealthScore: Int = 0,
    val weeklyCalories: List<Pair<String, Int>> = emptyList(),
    val protPerKg: Double = 0.0,
    val isLoading: Boolean = true
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val nutritionRepository: com.shredcoach.app.data.repository.NutritionRepository,
    private val mealScanDao: com.shredcoach.app.data.local.dao.MealScanDao,
    private val userRepository: com.shredcoach.app.data.repository.UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    private val _nutritionStats = MutableStateFlow(NutritionStatsData())
    val nutritionStats: StateFlow<NutritionStatsData> = _nutritionStats.asStateFlow()

    init { loadExercises(); loadStats(); loadNutritionStats() }

    fun refresh() { loadExercises(); loadStats(); loadNutritionStats() }

    fun selectPeriod(period: TimePeriod) {
        _state.update { it.copy(selectedPeriod = period) }
        loadStats()
    }

    fun selectExercise(exerciseId: Long, name: String) {
        _state.update { it.copy(selectedExerciseId = exerciseId, selectedExerciseName = name) }
        loadWeightProgression(exerciseId)
    }

    private fun loadExercises() {
        viewModelScope.launch {
            try {
                val exercises = statsRepository.getAllExercisesOnce()
                    .filter { it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO }
                _state.update { it.copy(exercises = exercises) }
                exercises.firstOrNull()?.let { selectExercise(it.id, it.name) }
            } catch (_: Exception) {}
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val period = _state.value.selectedPeriod
                val startDate = LocalDate.now().minusDays(period.days)
                val endDate = LocalDate.now()
                val monthStart = LocalDate.now().withDayOfMonth(1)

                // Summary période
                val workoutCount = statsRepository.getWorkoutCountInPeriod(startDate, endDate)
                val totalVolume = statsRepository.getTotalVolumeInPeriod(startDate, endDate)

                // Ce mois
                val monthWorkouts = statsRepository.getWorkoutCountInPeriod(monthStart, endDate)
                val monthVolume = statsRepository.getTotalVolumeInPeriod(monthStart, endDate)

                // All time
                val allTimeWorkouts = statsRepository.getTotalWorkoutCount()
                val allTimeVolume = statsRepository.getTotalVolumeAllTime()
                val allTimeDuration = statsRepository.getTotalDurationAllTime()

                // Calories estimées (~5 kcal/min de muscu)
                val estimatedCalories = (allTimeDuration / 60 * 5).toInt()

                // PRs
                val prs = loadPersonalRecords()

                // Volume hebdo + moyenne mobile + delta
                val dailyVolumes = statsRepository.getDailyVolume(startDate)
                val weeklyVolume = aggregateWeeklyVolume(dailyVolumes)
                val movingAvg = calculateMovingAverage(weeklyVolume, 4)
                val volumeChange = if (weeklyVolume.size >= 2) {
                    val curr = weeklyVolume.last().volume
                    val prev = weeklyVolume[weeklyVolume.size - 2].volume
                    if (prev > 0) ((curr - prev) / prev * 100).toFloat() else 0f
                } else 0f

                // Muscle distribution (exclure WARMUP et CARDIO qui ne sont pas des groupes musculaires)
                val muscleData = statsRepository.getMuscleGroupDistribution(startDate)
                    .filter { it.muscleGroup != MuscleGroup.WARMUP.name && it.muscleGroup != MuscleGroup.CARDIO.name }
                val totalSets = muscleData.sumOf { it.setCount }.coerceAtLeast(1)
                val muscleDistribution = muscleData.map { data ->
                    val mg = try { MuscleGroup.valueOf(data.muscleGroup) } catch (_: Exception) { null }
                    MuscleSlice(data.muscleGroup, mg?.displayName ?: data.muscleGroup, data.setCount, data.setCount.toFloat() / totalSets)
                }
                val mostTrainedMuscle = muscleDistribution.maxByOrNull { it.count }?.displayName ?: ""

                // Training frequency + compliance
                val frequency = statsRepository.getTrainingFrequency(startDate)
                val trainingDays = frequency.associate { LocalDate.parse(it.day) to it.count }
                val (current, longest) = calculateStreaks(trainingDays)
                val weeksInPeriod = (period.days / 7).coerceAtLeast(1)
                val compliance = if (weeksInPeriod > 0) (workoutCount.toFloat() / (weeksInPeriod * 4) * 100).coerceAtMost(100f) else 0f

                // Comparaison période vs période précédente
                val comparison = calculateComparison(startDate, endDate, period.days)

                // Temps par catégorie (WARMUP / CARDIO / Strength)
                val durationByGroup = try { statsRepository.getDurationByMuscleGroup() } catch (_: Exception) { emptyList() }
                val warmupSeconds = durationByGroup.firstOrNull { it.muscleGroup == MuscleGroup.WARMUP.name }?.totalSeconds ?: 0L
                val cardioSeconds = durationByGroup.firstOrNull { it.muscleGroup == MuscleGroup.CARDIO.name }?.totalSeconds ?: 0L
                val strengthSeconds = durationByGroup
                    .filter { it.muscleGroup != MuscleGroup.WARMUP.name && it.muscleGroup != MuscleGroup.CARDIO.name }
                    .sumOf { it.totalSeconds }

                _state.update {
                    it.copy(
                        isLoading = false, workoutCount = workoutCount, totalVolume = totalVolume,
                        estimatedCalories = estimatedCalories,
                        monthWorkouts = monthWorkouts, monthVolume = monthVolume,
                        allTimeWorkouts = allTimeWorkouts, allTimeVolume = allTimeVolume, allTimeDuration = allTimeDuration,
                        mostTrainedMuscle = mostTrainedMuscle,
                        personalRecords = prs, weeklyVolume = weeklyVolume,
                        volumeChangePercent = volumeChange, movingAverage = movingAvg,
                        muscleDistribution = muscleDistribution,
                        trainingDays = trainingDays, currentStreak = current, longestStreak = longest,
                        weeklyCompliance = compliance, comparison = comparison,
                        warmupSeconds = warmupSeconds, cardioSeconds = cardioSeconds, strengthSeconds = strengthSeconds
                    )
                }
            } catch (_: Exception) { _state.update { it.copy(isLoading = false) } }
        }
    }

    private fun loadWeightProgression(exerciseId: Long) {
        viewModelScope.launch {
            try {
                val data = statsRepository.getWeightProgression(exerciseId)
                val points = data.map { WeightPoint(it.date.toLocalDate(), it.weightKg, it.reps) }
                    .distinctBy { "${it.date}:${it.weight}" }

                // Calculer tendance
                val trend = calculateTrend(points)

                _state.update { it.copy(weightProgression = points, trend = trend) }
            } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════
    // COMPARAISON MULTI-PÉRIODES
    // ══════════════════════════════════════════

    private suspend fun calculateComparison(startDate: LocalDate, endDate: LocalDate, periodDays: Long): PeriodComparison? {
        try {
            val prevStart = startDate.minusDays(periodDays)
            val prevEnd = startDate.minusDays(1)

            val currWorkouts = statsRepository.getWorkoutCountInPeriod(startDate, endDate)
            val prevWorkouts = statsRepository.getWorkoutCountInPeriod(prevStart, prevEnd)
            val currVolume = statsRepository.getTotalVolumeInPeriod(startDate, endDate)
            val prevVolume = statsRepository.getTotalVolumeInPeriod(prevStart, prevEnd)

            if (prevWorkouts == 0 && currWorkouts == 0) return null

            val workoutDelta: Float = if (prevWorkouts > 0) ((currWorkouts - prevWorkouts).toFloat() / prevWorkouts.toFloat() * 100f) else if (currWorkouts > 0) 100f else 0f
            val volumeDelta: Float = if (prevVolume > 0) ((currVolume - prevVolume).toFloat() / prevVolume.toFloat() * 100f) else if (currVolume > 0) 100f else 0f

            val vd = volumeDelta.toInt()
            val insight: String = if (vd > 20) "Tu as progressé de +${vd}% en volume !"
                else if (vd > 5) "Belle progression : +${vd}% de volume"
                else if (vd > -5) "Volume stable, bonne régularité"
                else if (vd > -20) "Léger recul de ${vd}%. Fatigue ?"
                else "Volume en baisse de ${vd}%. Pense à une décharge"

            return PeriodComparison(currWorkouts, prevWorkouts, currVolume, prevVolume, workoutDelta, volumeDelta, insight)
        } catch (_: Exception) { return null }
    }

    // ══════════════════════════════════════════
    // TENDANCES & PRÉDICTIONS
    // ══════════════════════════════════════════

    private fun calculateTrend(points: List<WeightPoint>): TrendData? {
        if (points.size < 3) return null

        // Régression linéaire simple (x = index de semaine, y = poids max de la semaine)
        val weeklyMax = points.groupBy { it.date.minusDays(it.date.dayOfWeek.value.toLong() - 1) }
            .entries.sortedBy { it.key }
            .mapIndexed { i, entry -> i.toDouble() to entry.value.maxOf { it.weight } }

        if (weeklyMax.size < 2) return null

        val n = weeklyMax.size.toDouble()
        val sumX = weeklyMax.sumOf { it.first }
        val sumY = weeklyMax.sumOf { it.second }
        val sumXY = weeklyMax.sumOf { it.first * it.second }
        val sumX2 = weeklyMax.sumOf { it.first * it.first }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)

        // Détection plateau : slope < 0.5 kg/semaine sur les 4 dernières semaines
        val recentWeeks = weeklyMax.takeLast(4)
        val isPlateauing = recentWeeks.size >= 3 && abs(slope) < 0.5
        val plateauWeeks = if (isPlateauing) recentWeeks.size else 0

        // Projection 4 semaines
        val lastWeight = weeklyMax.last().second
        val projected = lastWeight + slope * 4

        val suggestion = when {
            isPlateauing && plateauWeeks >= 4 -> "Plateau détecté ! Essaie de varier l'exercice ou augmente le volume"
            slope > 2.0 -> "Progression rapide ! Attention à ne pas brûler les étapes"
            slope > 0.5 -> "Bonne progression régulière, continue comme ça !"
            slope > 0 -> "Progression lente mais constante"
            slope < -1 -> "Régression détectée. Fatigue ? Pense à une semaine de décharge"
            else -> "Stagnation. Essaie d'augmenter progressivement les charges"
        }

        return TrendData(slope, isPlateauing, plateauWeeks, projected.coerceAtLeast(0.0), suggestion)
    }

    // ══════════════════════════════════════════
    // EXPORT CSV
    // ══════════════════════════════════════════

    fun exportCSV(context: Context) {
        viewModelScope.launch {
            try {
                val sb = StringBuilder()
                sb.appendLine("Date,Exercice,Serie,Reps,Poids(kg),Repos(s),Tempo,Volume")

                val startDate = LocalDate.now().minusDays(_state.value.selectedPeriod.days)
                val exercises = _state.value.exercises

                for (exercise in exercises) {
                    val sets = statsRepository.getWeightProgression(exercise.id)
                    for (set in sets) {
                        if (set.date.toLocalDate() >= startDate) {
                            val volume = set.weightKg * set.reps
                            sb.appendLine("${set.date.toLocalDate()},${exercise.name},${set.setNumber},${set.reps},${set.weightKg},,,${volume}")
                        }
                    }
                }

                val file = File(context.cacheDir, "shredcoach_export_${LocalDate.now()}.csv")
                file.writeText(sb.toString())

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ShredCoach - Export données")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Partager l'export"))
            } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════
    // UTILITAIRES
    // ══════════════════════════════════════════

    private suspend fun loadPersonalRecords(): List<PRDisplay> {
        return statsRepository.getPersonalRecords().take(10).mapNotNull { pr ->
            statsRepository.getExerciseById(pr.exerciseId)?.let { exercise ->
                if (pr.maxWeight > 0) PRDisplay(exercise.name, pr.maxWeight, pr.reps, pr.maxWeight * (1 + pr.reps / 30.0)) else null
            }
        }.sortedByDescending { it.weight }
    }

    private fun aggregateWeeklyVolume(daily: List<DailyVolume>): List<VolumeBar> {
        if (daily.isEmpty()) return emptyList()
        val weeks = mutableMapOf<String, Double>()
        daily.forEach { d ->
            try {
                val date = LocalDate.parse(d.day)
                val weekStart = date.minusDays(date.dayOfWeek.value.toLong() - 1)
                val label = "${weekStart.dayOfMonth}/${weekStart.monthValue}"
                weeks[label] = (weeks[label] ?: 0.0) + d.volume
            } catch (_: Exception) {}
        }
        return weeks.entries.map { VolumeBar(it.key, it.value) }.takeLast(12)
    }

    private fun calculateMovingAverage(bars: List<VolumeBar>, window: Int): List<Float> {
        if (bars.size < window) return emptyList()
        return (window - 1 until bars.size).map { i ->
            bars.subList(i - window + 1, i + 1).map { it.volume.toFloat() }.average().toFloat()
        }
    }

    private fun calculateStreaks(days: Map<LocalDate, Int>): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val today = LocalDate.now()
        var current = 0; var longest = 0; var streak = 0
        var checkDate = today
        while (true) {
            if (days.containsKey(checkDate)) { current++; checkDate = checkDate.minusDays(1) }
            else if (checkDate == today) { checkDate = checkDate.minusDays(1) }
            else break
        }
        val sortedDays = days.keys.sorted()
        streak = 1
        for (i in 1 until sortedDays.size) {
            if (ChronoUnit.DAYS.between(sortedDays[i - 1], sortedDays[i]) == 1L) streak++
            else { longest = maxOf(longest, streak); streak = 1 }
        }
        longest = maxOf(longest, streak, current)
        return current to longest
    }

    // ═══════════════════════════════════════
    // NUTRITION STATS
    // ═══════════════════════════════════════

    private fun loadNutritionStats() {
        viewModelScope.launch {
            try {
                val profile = userRepository.getUserProfileOnce()
                val goal = nutritionRepository.getNutritionGoalOnce()
                val targetCal = goal?.targetCalories ?: 2200
                val targetProt = goal?.targetProteins ?: 180
                val bodyWeight = profile?.currentWeightKg ?: 80.0
                val today = java.time.LocalDate.now()

                // Moyennes 7 jours
                var totalCal = 0.0; var totalProt = 0.0; var totalCarbs = 0.0; var totalFats = 0.0
                var daysTracked = 0; var complianceDays = 0
                val weeklyData = mutableListOf<Pair<String, Int>>()
                val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.FRANCE)

                for (d in 6 downTo 0) {
                    val date = today.minusDays(d.toLong())
                    val totals = nutritionRepository.getDayTotals(date)
                    val dayCal = totals.totalCalories.toInt()
                    weeklyData.add(date.format(dayFmt).replaceFirstChar { it.uppercase() } to dayCal)
                    if (totals.totalCalories > 0) {
                        totalCal += totals.totalCalories; totalProt += totals.totalProteins
                        totalCarbs += totals.totalCarbs; totalFats += totals.totalFats
                        daysTracked++
                        if (totals.totalCalories >= targetCal * 0.9 && totals.totalCalories <= targetCal * 1.1) complianceDays++
                    }
                }

                // Scans
                val scans = mealScanDao.getAllScans().first()
                val avgScore = if (scans.isNotEmpty()) scans.sumOf { it.healthScore } / scans.size else 0

                val avgCal = if (daysTracked > 0) (totalCal / daysTracked).toInt() else 0
                val avgProt = if (daysTracked > 0) (totalProt / daysTracked).toInt() else 0
                val protKg = if (bodyWeight > 0 && daysTracked > 0) totalProt / daysTracked / bodyWeight else 0.0

                _nutritionStats.update {
                    NutritionStatsData(
                        avgCalories = avgCal, avgProteins = avgProt,
                        avgCarbs = if (daysTracked > 0) (totalCarbs / daysTracked).toInt() else 0,
                        avgFats = if (daysTracked > 0) (totalFats / daysTracked).toInt() else 0,
                        daysTracked = daysTracked, targetCalories = targetCal, targetProteins = targetProt,
                        complianceDays = complianceDays, totalScans = scans.size,
                        avgHealthScore = avgScore, weeklyCalories = weeklyData,
                        protPerKg = protKg, isLoading = false
                    )
                }
            } catch (_: Exception) {
                _nutritionStats.update { it.copy(isLoading = false) }
            }
        }
    }
}
