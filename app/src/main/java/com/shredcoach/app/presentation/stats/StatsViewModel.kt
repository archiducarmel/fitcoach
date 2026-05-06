package com.shredcoach.app.presentation.stats


import androidx.compose.runtime.Immutable
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
@Immutable
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
    val trend: TrendData? = null,

    // 1RM + plateau par exercice (top 5 par nombre de sessions)
    val exerciseProgressions: List<ExerciseProgressionEntry> = emptyList(),
)

/**
 * Tuple UI-friendly pour la section "Progression par exercice" de [DashboardScreen].
 * Le nom est dénormalisé (résolu via ExerciseDao en amont) pour éviter une lookup
 * dans le composable.
 */
data class ExerciseProgressionEntry(
    val exerciseName: String,
    val progression: com.shredcoach.app.domain.training.ExerciseProgression,
)

/**
 * Tranches horaires utilisées pour la timeline "Quand tu manges".
 * Bornes inclusives sur start, exclusives sur end (sauf NUIT qui boucle).
 */
enum class MealHourBucket(val label: String, val emoji: String, val startHour: Int, val endHour: Int) {
    MORNING("Matin", "🌅", 6, 11),       // 06:00 - 10:59
    LUNCH("Midi", "☀️", 11, 15),               // 11:00 - 14:59
    AFTERNOON("Après-midi", "🍎", 15, 19),// 15:00 - 18:59
    DINNER("Soir", "🌙", 19, 23),         // 19:00 - 22:59
    NIGHT("Nuit", "🌛", 23, 6);           // 23:00 - 05:59 (wrap)

    fun contains(hour: Int): Boolean = if (this == NIGHT) hour >= 23 || hour < 6
        else hour in startHour until endHour
}

data class NutritionStatsData(
    // ── Période sélectionnée (7j / 30j / 90j) ──
    val period: TimePeriod = TimePeriod.WEEK,

    // ── Moyennes sur la période courante ──
    val avgCalories: Int = 0,
    val avgProteins: Int = 0,
    val avgCarbs: Int = 0,
    val avgFats: Int = 0,
    val daysTracked: Int = 0,
    val daysInPeriod: Int = 7,
    val targetCalories: Int = 2200,
    val targetProteins: Int = 180,
    val complianceDays: Int = 0,
    val totalScans: Int = 0,
    val avgHealthScore: Int = 0,
    val protPerKg: Double = 0.0,

    // ── Graphique calories sur la période ──
    /** Pour 7j : (jourCourt, kcal). Pour 30j+ : (date ISO, kcal). */
    val weeklyCalories: List<Pair<String, Int>> = emptyList(),
    /** Calories quotidiennes brutes triées chronologiquement (pour smooth curve). */
    val dailyCaloriesSeries: List<Pair<LocalDate, Int>> = emptyList(),

    // ── Macro split % du total kcal ──
    /** % du total kcal apportés par les protéines (1g prot = 4 kcal). */
    val proteinKcalPct: Float = 0f,
    val carbsKcalPct: Float = 0f,
    val fatsKcalPct: Float = 0f,
    /** Verdict qualitatif sur le split (ex: "Split optimal pour la sèche"). */
    val macroSplitVerdict: String = "",

    // ── Distribution Nutri-Score sur la période (depuis MealScanEntity) ──
    val nutriCountA: Int = 0,
    val nutriCountB: Int = 0,
    val nutriCountC: Int = 0,
    val nutriCountD: Int = 0,
    val nutriCountE: Int = 0,

    // ── Comparaison vs période précédente ──
    val prevAvgCalories: Int = 0,
    val caloriesDelta: Int = 0,
    val prevAvgProteins: Int = 0,
    val proteinsDelta: Int = 0,
    val prevComplianceDays: Int = 0,
    val complianceDelta: Int = 0,

    // ── Timeline heures de repas (5 buckets sur la période) ──
    val mealsByHourBucket: Map<MealHourBucket, Int> = emptyMap(),

    // ── Insights auto-générés (2-3 phrases coachées) ──
    val insights: List<String> = emptyList(),

    // ── Streak de tracking (jours consécutifs avec ≥1 repas) ──
    val trackingStreak: Int = 0,

    // ── Fenêtre de jeûne intermittent (16-8, 14-10) ──
    val fasting: com.shredcoach.app.domain.nutrition.FastingStats =
        com.shredcoach.app.domain.nutrition.FastingStats(
            averageHours = 0.0, bestHours = 0.0,
            daysWith16h = 0, daysWith14h = 0,
            daysMeasured = 0, series = emptyList(),
            averageEatingStartHour = null, averageEatingEndHour = null,
        ),

    val isLoading: Boolean = true
) {
    val nutriTotal: Int get() = nutriCountA + nutriCountB + nutriCountC + nutriCountD + nutriCountE
    val nutriHighQualityShare: Float
        get() = if (nutriTotal == 0) 0f else (nutriCountA + nutriCountB).toFloat() / nutriTotal
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val nutritionRepository: com.shredcoach.app.data.repository.NutritionRepository,
    private val mealScanDao: com.shredcoach.app.data.local.dao.MealScanDao,
    private val userRepository: com.shredcoach.app.data.repository.UserRepository,
    private val workoutLogDao: WorkoutLogDao,
    private val plateauDetector: com.shredcoach.app.domain.training.PlateauDetector,
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

    /**
     * Sélection de la période pour les stats nutrition (indépendante des stats sport).
     * Limitée à 7j / 30j / 90j — au-delà la lecture des MealLog devient lourde
     * et l'insight perd en pertinence (régime "actuel" plus tendance long-terme).
     */
    fun selectNutritionPeriod(period: TimePeriod) {
        if (period !in listOf(TimePeriod.WEEK, TimePeriod.MONTH, TimePeriod.QUARTER)) return
        if (_nutritionStats.value.period == period) return
        _nutritionStats.update { it.copy(period = period, isLoading = true) }
        loadNutritionStats()
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

                // Top exercices : 5 plus pratiqués (par count de sets), suivis par
                // PlateauDetector. On reste sur 5 pour ne pas faire 30 queries
                // sur cold start si l'utilisateur a un grand catalogue d'exos.
                val exerciseProgressions = loadExerciseProgressions(top = 5)

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
                        warmupSeconds = warmupSeconds, cardioSeconds = cardioSeconds, strengthSeconds = strengthSeconds,
                        exerciseProgressions = exerciseProgressions,
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

    /**
     * Identifie les top exercices par nombre de sets (proxy de "exercices les
     * plus pratiqués"), puis lance [PlateauDetector] sur chacun.
     *
     * Stratégie : on lit getAllWorkoutSetsOnce() (ALL sets, qu'on filtre
     * sur weight > 0 pour ne pas remonter du cardio), on group-by exerciseId,
     * trie par count desc, on garde les [top] premiers. Coût : 1 query DB +
     * O(N) en mémoire. Ensuite N appels PlateauDetector → N queries indexées
     * (rapides, ~5ms chacune).
     */
    private suspend fun loadExerciseProgressions(top: Int): List<ExerciseProgressionEntry> {
        return try {
            val allSets = workoutLogDao.getAllWorkoutSetsOnce()
                .filter { it.completed && it.weightKg > 0 }
            if (allSets.isEmpty()) return emptyList()

            val exerciseSetCounts = allSets.groupingBy { it.exerciseId }.eachCount()
                .toList()
                .sortedByDescending { it.second }
                .take(top)

            // Lookup one-shot des noms d'exercices (vs N requêtes en boucle).
            val exercisesById = statsRepository.getAllExercisesOnce().associateBy { it.id }

            exerciseSetCounts.mapNotNull { (exerciseId, _) ->
                val progression = plateauDetector.analyze(exerciseId) ?: return@mapNotNull null
                val name = exercisesById[exerciseId]?.name ?: return@mapNotNull null
                ExerciseProgressionEntry(exerciseName = name, progression = progression)
            }.sortedByDescending { it.progression.sessionsCount }
        } catch (_: Exception) {
            emptyList()
        }
    }

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
                val today = LocalDate.now()
                val period = _nutritionStats.value.period
                val daysInPeriod = when (period) {
                    TimePeriod.WEEK -> 7
                    TimePeriod.MONTH -> 30
                    TimePeriod.QUARTER -> 90
                    else -> 7
                }

                // ─── Période courante : agrégation jour par jour ───
                val current = aggregatePeriod(today.minusDays((daysInPeriod - 1).toLong()), today, targetCal)
                // ─── Période précédente (pour la comparaison) ───
                val prevEnd = today.minusDays(daysInPeriod.toLong())
                val prevStart = prevEnd.minusDays((daysInPeriod - 1).toLong())
                val previous = aggregatePeriod(prevStart, prevEnd, targetCal)

                // ─── Macro split % en calories (4/4/9 kcal/g) ───
                val (protPct, carbPct, fatPct) = computeMacroSplit(
                    current.totalProt, current.totalCarbs, current.totalFats
                )
                val macroVerdict = computeMacroVerdict(protPct, carbPct, fatPct, profile?.goal)

                // ─── Heures de repas (timeline buckets) ───
                val mealsByBucket = computeMealHourBuckets(today.minusDays((daysInPeriod - 1).toLong()), today)

                // ─── Distribution Nutri-Score sur la période ───
                val scansAll = mealScanDao.getAllScans().first()
                val sinceCutoff = today.minusDays((daysInPeriod - 1).toLong())
                val scansInPeriod = scansAll.filter { it.timestamp.toLocalDate() >= sinceCutoff }
                var nA = 0; var nB = 0; var nC = 0; var nD = 0; var nE = 0
                for (scan in scansInPeriod) {
                    when (scan.nutriScoreGrade.firstOrNull()) {
                        'A' -> nA++; 'B' -> nB++; 'C' -> nC++; 'D' -> nD++; 'E' -> nE++
                    }
                }
                val avgScore = if (scansInPeriod.isNotEmpty()) scansInPeriod.sumOf { it.healthScore } / scansInPeriod.size else 0

                // ─── Streak tracking (jours consécutifs avec ≥1 repas, en remontant depuis aujourd'hui) ───
                var streak = 0
                var cursor = today
                while (true) {
                    val dt = nutritionRepository.getDayTotals(cursor)
                    if (dt.totalCalories > 0) { streak++; cursor = cursor.minusDays(1) }
                    else break
                }

                // ─── Fenêtre de jeûne intermittent ───
                val fastingStart = today.minusDays((daysInPeriod - 1).toLong())
                val fasting = com.shredcoach.app.domain.nutrition.FastingWindowCalculator.aggregate(
                    start = fastingStart,
                    end = today,
                ) { date -> nutritionRepository.getMealsForDateOnce(date) }

                // ─── Calculs agrégés ───
                val avgCal = if (current.daysTracked > 0) (current.totalCal / current.daysTracked).toInt() else 0
                val avgProt = if (current.daysTracked > 0) (current.totalProt / current.daysTracked).toInt() else 0
                val avgCarbs = if (current.daysTracked > 0) (current.totalCarbs / current.daysTracked).toInt() else 0
                val avgFats = if (current.daysTracked > 0) (current.totalFats / current.daysTracked).toInt() else 0
                val protKg = if (bodyWeight > 0 && current.daysTracked > 0) current.totalProt / current.daysTracked / bodyWeight else 0.0

                val prevAvgCal = if (previous.daysTracked > 0) (previous.totalCal / previous.daysTracked).toInt() else 0
                val prevAvgProt = if (previous.daysTracked > 0) (previous.totalProt / previous.daysTracked).toInt() else 0

                // ─── Insights auto ───
                val insights = generateInsights(
                    avgCal = avgCal, targetCal = targetCal,
                    daysTracked = current.daysTracked, daysInPeriod = daysInPeriod,
                    complianceDays = current.complianceDays,
                    protKg = protKg, profileGoal = profile?.goal,
                    nutriHighShare = if (scansInPeriod.isNotEmpty()) (nA + nB).toFloat() / scansInPeriod.size else 0f,
                    caloriesDelta = avgCal - prevAvgCal, proteinsDelta = avgProt - prevAvgProt,
                    streak = streak,
                    fasting = fasting,
                )

                _nutritionStats.update {
                    NutritionStatsData(
                        period = period,
                        avgCalories = avgCal, avgProteins = avgProt,
                        avgCarbs = avgCarbs, avgFats = avgFats,
                        daysTracked = current.daysTracked, daysInPeriod = daysInPeriod,
                        targetCalories = targetCal, targetProteins = targetProt,
                        complianceDays = current.complianceDays, totalScans = scansInPeriod.size,
                        avgHealthScore = avgScore,
                        weeklyCalories = current.dailyForBars,
                        dailyCaloriesSeries = current.dailySeries,
                        protPerKg = protKg,
                        proteinKcalPct = protPct, carbsKcalPct = carbPct, fatsKcalPct = fatPct,
                        macroSplitVerdict = macroVerdict,
                        nutriCountA = nA, nutriCountB = nB, nutriCountC = nC, nutriCountD = nD, nutriCountE = nE,
                        prevAvgCalories = prevAvgCal,
                        caloriesDelta = avgCal - prevAvgCal,
                        prevAvgProteins = prevAvgProt,
                        proteinsDelta = avgProt - prevAvgProt,
                        prevComplianceDays = previous.complianceDays,
                        complianceDelta = current.complianceDays - previous.complianceDays,
                        mealsByHourBucket = mealsByBucket,
                        insights = insights,
                        trackingStreak = streak,
                        fasting = fasting,
                        isLoading = false
                    )
                }
            } catch (_: Exception) {
                _nutritionStats.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Snapshot agrégé d'une fenêtre de jours (pour stats courantes ET précédentes).
     */
    private data class PeriodAggregate(
        val totalCal: Double,
        val totalProt: Double,
        val totalCarbs: Double,
        val totalFats: Double,
        val daysTracked: Int,
        val complianceDays: Int,
        /** Liste pour le graphe à barres (label court "Lun 12"). */
        val dailyForBars: List<Pair<String, Int>>,
        /** Liste pour smooth curve (date, kcal). */
        val dailySeries: List<Pair<LocalDate, Int>>,
    )

    private suspend fun aggregatePeriod(start: LocalDate, end: LocalDate, targetCal: Int): PeriodAggregate {
        var totalCal = 0.0; var totalProt = 0.0; var totalCarbs = 0.0; var totalFats = 0.0
        var daysTracked = 0; var complianceDays = 0
        val barFmt = DateTimeFormatter.ofPattern("EEE", java.util.Locale.FRANCE)
        val barFmtLong = DateTimeFormatter.ofPattern("d/M", java.util.Locale.FRANCE)
        val barData = mutableListOf<Pair<String, Int>>()
        val series = mutableListOf<Pair<LocalDate, Int>>()
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val useShortDay = days <= 7

        var d = start
        while (!d.isAfter(end)) {
            val totals = nutritionRepository.getDayTotals(d)
            val dayCal = totals.totalCalories.toInt()
            val label = if (useShortDay) d.format(barFmt).replaceFirstChar { it.uppercase() }
                else d.format(barFmtLong)
            barData += label to dayCal
            series += d to dayCal
            if (totals.totalCalories > 0) {
                totalCal += totals.totalCalories; totalProt += totals.totalProteins
                totalCarbs += totals.totalCarbs; totalFats += totals.totalFats
                daysTracked++
                if (totals.totalCalories >= targetCal * 0.9 && totals.totalCalories <= targetCal * 1.1) {
                    complianceDays++
                }
            }
            d = d.plusDays(1)
        }
        return PeriodAggregate(
            totalCal, totalProt, totalCarbs, totalFats,
            daysTracked, complianceDays, barData, series
        )
    }

    /** Split macro en % du total kcal (4 kcal/g pour P et G, 9 kcal/g pour L). */
    private fun computeMacroSplit(totalProt: Double, totalCarbs: Double, totalFats: Double): Triple<Float, Float, Float> {
        val protKcal = totalProt * 4
        val carbKcal = totalCarbs * 4
        val fatKcal = totalFats * 9
        val total = (protKcal + carbKcal + fatKcal).coerceAtLeast(1.0)
        return Triple(
            (protKcal / total).toFloat(),
            (carbKcal / total).toFloat(),
            (fatKcal / total).toFloat()
        )
    }

    /**
     * Verdict qualitatif sur la répartition macro selon l'objectif fitness.
     *  - Sèche : vise 35-40% prot, 30-40% carb, 20-30% lip
     *  - Prise de masse : 25-30% prot, 45-55% carb, 20-25% lip
     *  - Maintien : 25-30% prot, 40-50% carb, 25-30% lip
     */
    private fun computeMacroVerdict(
        protPct: Float, carbPct: Float, fatPct: Float,
        goal: com.shredcoach.app.data.local.entity.FitnessGoal?
    ): String {
        if (protPct + carbPct + fatPct < 0.5f) return ""  // pas assez de données
        return when (goal) {
            com.shredcoach.app.data.local.entity.FitnessGoal.SHRED -> when {
                protPct < 0.30f -> "Pas assez de protéines pour la sèche"
                fatPct > 0.40f -> "Trop de lipides — réduis pour creuser le déficit"
                carbPct > 0.50f -> "Glucides un peu hauts — module-les autour des séances"
                protPct >= 0.35f && fatPct <= 0.30f -> "Split optimal pour la sèche"
                else -> "Bon équilibre, marge de progression"
            }
            com.shredcoach.app.data.local.entity.FitnessGoal.BULK -> when {
                carbPct < 0.40f -> "Manque de glucides pour soutenir la prise de masse"
                protPct < 0.20f -> "Plus de protéines pour la synthèse musculaire"
                else -> "Split adapté à la prise de masse"
            }
            else -> when {
                protPct < 0.20f -> "Plus de protéines pour la satiété et le muscle"
                fatPct > 0.40f -> "Lipides un peu hauts — diversifie les sources"
                else -> "Équilibre macro correct"
            }
        }
    }

    /**
     * Compte les MealLogEntity de la période bucketés par tranche horaire.
     * Permet la timeline "Quand tu manges" — révèle les patterns (skipping
     * petit-déj, grignotage soir, etc.).
     */
    private suspend fun computeMealHourBuckets(start: LocalDate, end: LocalDate): Map<MealHourBucket, Int> {
        val counts = MealHourBucket.values().associateWith { 0 }.toMutableMap()
        var d = start
        while (!d.isAfter(end)) {
            val meals = try {
                nutritionRepository.getMealsForDate(d).first()
            } catch (_: Exception) { emptyList() }
            for (meal in meals) {
                val hour = meal.time?.hour ?: continue
                val bucket = MealHourBucket.values().firstOrNull { it.contains(hour) }
                if (bucket != null) counts[bucket] = (counts[bucket] ?: 0) + 1
            }
            d = d.plusDays(1)
        }
        return counts
    }

    /**
     * Génère 2-4 insights coachés en français, courts et actionnables.
     * Priorisés par sévérité : alerte protéines/déficit avant félicitations.
     */
    private fun generateInsights(
        avgCal: Int, targetCal: Int,
        daysTracked: Int, daysInPeriod: Int,
        complianceDays: Int,
        protKg: Double, profileGoal: com.shredcoach.app.data.local.entity.FitnessGoal?,
        nutriHighShare: Float,
        caloriesDelta: Int, proteinsDelta: Int,
        streak: Int,
        fasting: com.shredcoach.app.domain.nutrition.FastingStats,
    ): List<String> {
        if (daysTracked == 0) return listOf("Suis tes repas pour débloquer des insights personnalisés")
        val list = mutableListOf<String>()

        // 1. Alertes protéines (priorité haute en sèche)
        if (profileGoal == com.shredcoach.app.data.local.entity.FitnessGoal.SHRED && protKg in 0.01..1.4) {
            list += "🍗 Sous l'objectif protéines (${"%.1f".format(protKg)} g/kg) — vise ≥ 1.6 g/kg"
        }

        // 2. Compliance / trends calories
        val complianceShare = if (daysInPeriod > 0) complianceDays.toFloat() / daysInPeriod else 0f
        when {
            complianceShare >= 0.7f -> list += "🎯 $complianceDays/$daysInPeriod jours dans ta cible — excellente régularité"
            complianceShare >= 0.4f -> list += "📈 $complianceDays/$daysInPeriod jours dans la cible — continue comme ça"
            avgCal < targetCal * 0.8 -> list += "⚠️ Déficit moyen ${targetCal - avgCal} kcal/jour — risque de perte musculaire"
            avgCal > targetCal * 1.2 -> list += "⚠️ ${avgCal - targetCal} kcal au-dessus de la cible en moyenne"
        }

        // 3. Comparaison vs période précédente
        if (kotlin.math.abs(caloriesDelta) > 50) {
            val sign = if (caloriesDelta >= 0) "+" else ""
            list += "📊 ${sign}$caloriesDelta kcal/jour vs période précédente"
        }
        if (proteinsDelta >= 15) list += "💪 +${proteinsDelta}g protéines/jour vs période précédente"

        // 4. Qualité Nutri-Score
        if (nutriHighShare >= 0.7f) list += "✨ ${(nutriHighShare * 100).toInt()}% de tes repas notés A ou B"
        else if (nutriHighShare in 0.01f..0.3f) list += "🥗 Vise plus d'aliments notés A et B (légumes, fruits, légumineuses)"

        // 5. Streak
        if (streak >= 7) list += "🔥 $streak jours d'affilée à tracker tes repas"

        // 6. Jeûne intermittent
        if (!fasting.isEmpty && fasting.daysMeasured >= 3) {
            val avg = fasting.averageHours
            when {
                avg >= 16.0 -> list += "🌙 Jeûne moyen ${formatHours(avg)} — format 16-8 atteint"
                avg >= 14.0 && fasting.daysWith16h >= 1 -> list += "🌙 Jeûne moyen ${formatHours(avg)} (${fasting.daysWith16h}j ≥ 16h)"
                avg >= 14.0 -> list += "🌙 Jeûne moyen ${formatHours(avg)} — vise 16h+ pour le 16-8"
                avg < 12.0 -> list += "🌙 Jeûne ${formatHours(avg)}/jour — ouvre une fenêtre de jeûne plus large"
            }
        }

        return list.take(4)
    }

    private fun formatHours(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return if (m < 5) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
    }
}
