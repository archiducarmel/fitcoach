package com.shredcoach.app.presentation.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.data.repository.ChatRepository
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.domain.session.ActiveSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.Duration
import javax.inject.Inject

// ── Données d'une série complétée ──
data class WorkoutSetData(
    val exerciseId: Long,
    val seriesNumber: Int,
    val reps: Int,
    val targetReps: Int,
    val weight: Double,
    val targetWeight: Double,
    val restSecondsActual: Int? = null,
    val targetRestSeconds: Int = 0,
    val tempoUsed: String? = null,
    val setDurationSeconds: Int? = null,
    val exerciseDurationSeconds: Long? = null,
    val skipped: Boolean = false // Série skippée
)

// ── État complet de la séance ──
data class WorkoutSessionState(
    val workoutLogId: Long? = null,
    val exercises: List<ExerciseEntity> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val currentSeries: Int = 1,
    val completedSets: List<WorkoutSetData> = emptyList(),

    // ── Champs pré-remplis ──
    val currentSetWeight: String = "",
    val currentSetReps: String = "",
    val currentSetTempo: String = "",

    // ── Suggestion poids (dernière séance) ──
    val lastSessionWeight: Double? = null,
    val lastSessionReps: Int? = null,
    val personalRecordWeight: Double? = null, // Record absolu pour l'exercice
    val isPersonalRecord: Boolean = false, // PR si poids actuel > personalRecordWeight

    // ── Chrono global ──
    val globalChronoSeconds: Long = 0,
    val globalChronoRunning: Boolean = false,
    val sessionStartTime: LocalDateTime? = null,

    // ── Chrono exercice ──
    val exerciseChronoSeconds: Long = 0,

    // ── Workflow série ──
    val isSetInProgress: Boolean = false,
    val setStartTime: LocalDateTime? = null,

    // ── Repos ──
    val isRestTimerActive: Boolean = false,
    val restTimeRemaining: Int = 0,
    val restTimeElapsed: Int = 0,

    // ── Décompte pour série chronométrée (isTimeBased : gainage, cardio...) ──
    // timedSetSecondsRemaining > 0 signifie qu'un décompte est en cours
    val timedSetSecondsRemaining: Int = 0,
    val timedSetTotalSeconds: Int = 0,

    // ── Transition exercice ──
    val showExerciseTransition: Boolean = false,
    val transitionFromName: String = "",
    val transitionToName: String = "",
    val transitionExercisesDone: Int = 0,
    val transitionTotalExercises: Int = 0,
    val transitionExerciseSets: Int = 0,
    val transitionExerciseReps: Int = 0,
    val transitionExerciseVolume: Double = 0.0,
    val transitionExerciseDuration: Long = 0,
    val transitionExerciseSkipped: Int = 0,
    val shreddyCoachMessage: String = "",
    val isShreddyThinking: Boolean = false,
    val shreddyMessageSource: String = "",
    val showExerciseOverview: Boolean = false, // Vue d'ensemble des exercices
    val userFirstName: String = "",
    val userGoalName: String = "SHRED",
    val userStreak: Int = 0,

    // ── Fin de séance ──
    val isSessionComplete: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,

    // ── Instrumentation ──
    val exerciseStartTimes: Map<Int, LocalDateTime> = emptyMap(),
    val exerciseDurations: Map<Int, Long> = emptyMap(),
    val skippedExercises: Set<Int> = emptySet(),
    val skippedSeries: Set<String> = emptySet(), // "exoIndex:seriesNum"

    // ── Pending transition ──
    val pendingNextIndex: Int = -1,
    val pendingNextStartTimes: Map<Int, LocalDateTime> = emptyMap(),

    // ── Repos custom par série ──
    val currentRestOverride: Int? = null, // null = utiliser le défaut de l'exercice
    // ── Séries ajoutées à la volée ──
    val extraSeriesMap: Map<Int, Int> = emptyMap(), // exoIndex → nb séries ajoutées
    // ── Post-dernière série : proposer d'ajouter une série avant de passer au suivant ──
    val showPostLastSetPrompt: Boolean = false,

    // ── Mode freestyle (séance libre) ──
    val isFreestyle: Boolean = false,

    // ── Ajout exercice à la volée (2 étapes : groupe musculaire → exercice) ──
    val showAddExerciseDialog: Boolean = false,
    val addExerciseStep: Int = 0, // 0 = choix groupe, 1 = choix exercice
    val addExerciseMuscleGroup: MuscleGroup? = null,
    val addExerciseOptions: List<ExerciseEntity> = emptyList(),
    val addExerciseSearchQuery: String = "", // Recherche par nom
    // ── Confirmation avant ajout ──
    val pendingExerciseToAdd: ExerciseEntity? = null,
    val pendingExercisePlacement: String = "", // "start", "afterCurrent", "end"

    // ── Config (chargé depuis UserProfile) ──
    val autoStartAfterRest: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val voiceEnabled: Boolean = false,
    val suggestBonusSeries: Boolean = false,
    val userBodyWeightKg: Double = 75.0
) {
    val currentExercise: ExerciseEntity?
        get() = exercises.getOrNull(currentExerciseIndex)

    val totalExercises: Int
        get() = exercises.size

    val totalSeriesForCurrentExercise: Int
        get() = (currentExercise?.series ?: 0) + (extraSeriesMap[currentExerciseIndex] ?: 0)

    val isLastSeries: Boolean
        get() = currentSeries >= totalSeriesForCurrentExercise

    /** Repos effectif pour la série courante (override utilisateur ou défaut exercice). */
    val effectiveRestSeconds: Int
        get() = currentRestOverride ?: currentExercise?.restSeconds ?: 90

    val isLastExercise: Boolean
        get() = currentExerciseIndex >= exercises.size - 1

    val progressPercentage: Float
        get() {
            if (exercises.isEmpty()) return 0f
            val totalSets = exercises.mapIndexed { i, ex -> ex.series + (extraSeriesMap[i] ?: 0) }.sum()
            if (totalSets == 0) return 0f
            return (completedSets.size.toFloat() / totalSets.toFloat()).coerceIn(0f, 1f)
        }

    // ── Info Warmup ──
    val isWarmupExercise: Boolean
        get() = currentExercise?.muscleGroup == MuscleGroup.WARMUP

    val isCardioExercise: Boolean
        get() = currentExercise?.muscleGroup == MuscleGroup.CARDIO

    /** Tous les exercices d'échauffement de la séance */
    val warmupExercises: List<ExerciseEntity>
        get() = exercises.filter { it.muscleGroup == MuscleGroup.WARMUP }

    /** Index du premier exercice d'échauffement */
    val warmupStartIndex: Int
        get() = exercises.indexOfFirst { it.muscleGroup == MuscleGroup.WARMUP }

    /** L'index courant dans le bloc warmup (0-based) */
    val warmupStepIndex: Int
        get() {
            val start = warmupStartIndex
            return if (start >= 0 && currentExerciseIndex >= start)
                currentExerciseIndex - start
            else -1
        }

    val isInWarmupBlock: Boolean
        get() = warmupStepIndex in warmupExercises.indices

    fun isSeriesSkipped(exerciseIndex: Int, seriesNumber: Int): Boolean {
        return "$exerciseIndex:$seriesNumber" in skippedSeries
    }

    // ── Métriques ──
    val totalVolume: Double get() = completedSets.filter { !it.skipped }.sumOf { it.weight * it.reps }
    val totalSetsCompleted: Int get() = completedSets.filter { !it.skipped }.size
    val totalRepsCompleted: Int get() = completedSets.filter { !it.skipped }.sumOf { it.reps }
    val totalRestSeconds: Long get() = completedSets.mapNotNull { it.restSecondsActual?.toLong() }.sum()
    val exercisesCompletedCount: Int
        get() = completedSets.filter { !it.skipped }.map { it.exerciseId }.toSet().size
}

@HiltViewModel
class WorkoutSessionViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository,
    private val exerciseRepository: ExerciseRepository,
    private val chatRepository: ChatRepository,
    private val scheduledWorkoutRepository: com.shredcoach.app.data.repository.ScheduledWorkoutRepository,
    val sessionManager: ActiveSessionManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> = _state.asStateFlow()

    private var exerciseChronoJob: Job? = null
    private var restTimerJob: Job? = null
    private var timedSetJob: Job? = null

    init {
        val workoutId = savedStateHandle.get<String>("workoutId")?.toLongOrNull()
        if (workoutId != null) {
            loadWorkout(workoutId)
        }

        // Charger les préférences utilisateur
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            if (profile != null) {
                _state.update {
                    it.copy(
                        autoStartAfterRest = profile.autoStartAfterRest,
                        vibrationEnabled = profile.vibrationEnabled,
                        soundEnabled = profile.soundEnabled,
                        voiceEnabled = profile.voiceEnabled,
                        suggestBonusSeries = profile.suggestBonusSeries,
                        userBodyWeightKg = profile.currentWeightKg,
                        userFirstName = profile.firstName,
                        userGoalName = profile.goal.name,
                        userStreak = profile.currentStreakDays
                    )
                }
            }
        }

        // Observer le chrono global du SessionManager
        viewModelScope.launch {
            sessionManager.session.collect { session ->
                if (session != null) {
                    _state.update {
                        it.copy(
                            globalChronoSeconds = session.globalChronoSeconds,
                            globalChronoRunning = session.isRunning
                        )
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════
    // CHARGEMENT
    // ══════════════════════════════════════════

    private fun loadWorkout(workoutLogId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val workoutLog = workoutRepository.getWorkoutLogById(workoutLogId)
                if (workoutLog != null) {
                    val exercises = workoutRepository.getExercisesForWorkoutLog(workoutLogId)
                    val first = exercises.firstOrNull()
                    val now = LocalDateTime.now()
                    val freestyle = exercises.isEmpty()

                    // Poids du corps pour exos time-based
                    val profile = userRepository.getUserProfileOnce()
                    val bodyWeight = profile?.currentWeightKg ?: 75.0

                    if (freestyle) {
                        // Mode Freestyle : séance vide, l'utilisateur ajoute les exercices au fur et à mesure
                        _state.update {
                            it.copy(
                                workoutLogId = workoutLog.id,
                                exercises = emptyList(),
                                sessionStartTime = now,
                                userBodyWeightKg = bodyWeight,
                                isFreestyle = true,
                                isLoading = false,
                                showAddExerciseDialog = true, // Ouvrir le picker directement
                                addExerciseStep = 0
                            )
                        }
                    } else {
                        // Mode normal
                        val lastSets = first?.let { loadLastSetsForExercise(it.id) }
                        val prWeight = first?.let { runCatching { workoutRepository.getMaxWeightForExercise(it.id) }.getOrNull() }
                        val initialWeight = if (first?.isTimeBased == true) fmtWeightValue(bodyWeight)
                            else lastSets?.firstOrNull()?.let { s -> s.weightKg.toString() } ?: extractWeight(first)

                        _state.update {
                            it.copy(
                                workoutLogId = workoutLog.id,
                                exercises = exercises,
                                sessionStartTime = now,
                                exerciseStartTimes = mapOf(0 to now),
                                userBodyWeightKg = bodyWeight,
                                currentSetWeight = initialWeight,
                                currentSetReps = first?.repsMin?.toString() ?: "",
                                currentSetTempo = first?.tempo ?: "3-0-1-0",
                                lastSessionWeight = lastSets?.firstOrNull()?.weightKg,
                                lastSessionReps = lastSets?.firstOrNull()?.reps,
                                personalRecordWeight = prWeight,
                                isLoading = false
                            )
                        }
                        sessionManager.updateExerciseInfo(first?.name ?: "", 0)
                        startExerciseChrono()
                    }
                    startGlobalChrono()
                } else {
                    _state.update { it.copy(error = "Séance introuvable", isLoading = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Erreur", isLoading = false) }
            }
        }
    }

    // ══════════════════════════════════════════
    // CHRONOS (global délégué au SessionManager)
    // ══════════════════════════════════════════

    private fun startGlobalChrono() {
        // Le SessionManager gère le chrono — il tourne même hors de cet écran
        if (!sessionManager.isSessionActive) {
            sessionManager.startSession(
                workoutLogId = _state.value.workoutLogId ?: 0,
                totalExercises = _state.value.totalExercises
            )
        }
    }

    fun stopGlobalChrono() { sessionManager.pauseChrono() }
    fun resumeGlobalChrono() { sessionManager.resumeChrono() }

    private fun startExerciseChrono() {
        exerciseChronoJob?.cancel()
        _state.update { it.copy(exerciseChronoSeconds = 0) }
        exerciseChronoJob = viewModelScope.launch {
            while (true) { delay(1000); _state.update { it.copy(exerciseChronoSeconds = it.exerciseChronoSeconds + 1) } }
        }
    }

    private fun stopExerciseChrono(): Long { exerciseChronoJob?.cancel(); return _state.value.exerciseChronoSeconds }

    // ══════════════════════════════════════════
    // INPUTS
    // ══════════════════════════════════════════

    fun onWeightChanged(weight: String) {
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _state.update {
                val w = weight.toDoubleOrNull()
                val pr = it.personalRecordWeight
                it.copy(
                    currentSetWeight = weight,
                    isPersonalRecord = (w != null && pr != null && w > pr)
                )
            }
        }
    }

    /** Suggere un poids base sur progression lineaire : dernier poids + 5kg (clamp a 100) */
    fun suggestWeight() {
        val s = _state.value
        val base = s.lastSessionWeight ?: s.currentSetWeight.toDoubleOrNull() ?: return
        val suggested = (base + 5.0).coerceAtMost(100.0)
        onWeightChanged(fmtWeightValue(suggested))
    }

    private fun fmtWeightValue(w: Double): String =
        if (w == w.toLong().toDouble()) w.toLong().toString()
        else String.format(java.util.Locale.US, "%.1f", w)

    fun onRepsChanged(reps: String) {
        if (reps.isEmpty() || reps.matches(Regex("^\\d+$")))
            _state.update { it.copy(currentSetReps = reps) }
    }

    fun onTempoChanged(tempo: String) { _state.update { it.copy(currentSetTempo = tempo) } }

    // ══════════════════════════════════════════
    // AJOUT EXERCICE EN COURS DE SÉANCE
    // ══════════════════════════════════════════

    fun openAddExerciseDialog() {
        _state.update { it.copy(showAddExerciseDialog = true, addExerciseStep = 0, addExerciseMuscleGroup = null, addExerciseOptions = emptyList(), addExerciseSearchQuery = "") }
    }
    fun closeAddExerciseDialog() {
        _state.update { it.copy(showAddExerciseDialog = false, addExerciseSearchQuery = "") }
    }
    /** Étape 1 → 2 : l'utilisateur choisit un groupe musculaire. */
    fun selectAddExerciseMuscleGroup(mg: MuscleGroup) {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().first().let { all ->
                val usedIds = _state.value.exercises.map { it.id }.toSet()
                val options = all.filter { it.muscleGroup == mg && it.id !in usedIds }
                _state.update { it.copy(addExerciseStep = 1, addExerciseMuscleGroup = mg, addExerciseOptions = options, addExerciseSearchQuery = "") }
            }
        }
    }
    /** Retour étape 2 → 1 */
    fun backToMuscleGroupStep() {
        _state.update { it.copy(addExerciseStep = 0, addExerciseMuscleGroup = null, addExerciseOptions = emptyList(), addExerciseSearchQuery = "") }
    }
    /** Recherche par nom d'exercice (filtre les options affichées). */
    fun onAddExerciseSearchQuery(query: String) {
        _state.update { it.copy(addExerciseSearchQuery = query) }
        if (query.length >= 2) {
            viewModelScope.launch {
                exerciseRepository.getAllExercises().first().let { all ->
                    val usedIds = _state.value.exercises.map { it.id }.toSet()
                    val mg = _state.value.addExerciseMuscleGroup
                    val options = all.filter { ex ->
                        ex.id !in usedIds &&
                        ex.name.contains(query, ignoreCase = true) &&
                        (mg == null || ex.muscleGroup == mg)
                    }
                    _state.update { it.copy(addExerciseOptions = options, addExerciseStep = 1) }
                }
            }
        } else if (query.isBlank()) {
            // Reset vers la sélection par groupe musculaire
            _state.update { it.copy(addExerciseStep = 0, addExerciseMuscleGroup = null, addExerciseOptions = emptyList()) }
        }
    }

    /** Demande confirmation avant d'ajouter un exercice. */
    fun requestAddExercise(exercise: ExerciseEntity, placement: String) {
        _state.update { it.copy(pendingExerciseToAdd = exercise, pendingExercisePlacement = placement) }
    }
    fun cancelAddExercise() {
        _state.update { it.copy(pendingExerciseToAdd = null, pendingExercisePlacement = "") }
    }
    fun confirmAddExercise() {
        val exercise = _state.value.pendingExerciseToAdd ?: return
        val placement = _state.value.pendingExercisePlacement
        _state.update { it.copy(pendingExerciseToAdd = null, pendingExercisePlacement = "") }
        when (placement) {
            "start" -> insertExerciseAtStart(exercise)
            "afterCurrent" -> insertExerciseAfterCurrent(exercise)
            else -> insertExerciseAtEnd(exercise)
        }
    }

    /** Insère un exercice au DÉBUT de la séance (avant l'index 0). */
    fun insertExerciseAtStart(exercise: ExerciseEntity) {
        val s = _state.value
        val newExercises = s.exercises.toMutableList()
        newExercises.add(0, exercise)

        // Décaler TOUTES les clés de +1
        val newExtraSeriesMap = s.extraSeriesMap.mapKeys { (k, _) -> k + 1 }
        val newStartTimes = s.exerciseStartTimes.mapKeys { (k, _) -> k + 1 }
        val newDurations = s.exerciseDurations.mapKeys { (k, _) -> k + 1 }
        val newSkipped = s.skippedExercises.map { it + 1 }.toSet()
        val newSkippedSeries = s.skippedSeries.map { key ->
            val parts = key.split(":")
            if (parts.size == 2) "${(parts[0].toIntOrNull() ?: 0) + 1}:${parts[1]}" else key
        }.toSet()

        _state.update {
            it.copy(
                exercises = newExercises,
                currentExerciseIndex = it.currentExerciseIndex + 1, // Décaler l'index courant
                extraSeriesMap = newExtraSeriesMap,
                exerciseStartTimes = newStartTimes,
                exerciseDurations = newDurations,
                skippedExercises = newSkipped,
                skippedSeries = newSkippedSeries,
                showAddExerciseDialog = false,
                showExerciseOverview = false
            )
        }
        sessionManager.updateTotalExercises(newExercises.size)
        persistAddedExercise(exercise, 0)
    }

    /** Insère un exercice APRÈS l'exercice courant dans la liste de la séance. */
    fun insertExerciseAfterCurrent(exercise: ExerciseEntity) {
        val s = _state.value
        val insertAt = s.currentExerciseIndex + 1
        val newExercises = s.exercises.toMutableList()
        newExercises.add(insertAt, exercise)

        // Décaler les clés des maps qui utilisent des indices >= insertAt
        val newExtraSeriesMap = s.extraSeriesMap.mapKeys { (k, _) -> if (k >= insertAt) k + 1 else k }
        val newStartTimes = s.exerciseStartTimes.mapKeys { (k, _) -> if (k >= insertAt) k + 1 else k }
        val newDurations = s.exerciseDurations.mapKeys { (k, _) -> if (k >= insertAt) k + 1 else k }
        val newSkipped = s.skippedExercises.map { if (it >= insertAt) it + 1 else it }.toSet()
        // Décaler les clés "exoIndex:seriesNum" dans skippedSeries
        val newSkippedSeries = s.skippedSeries.map { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val idx = parts[0].toIntOrNull() ?: return@map key
                val ser = parts[1]
                if (idx >= insertAt) "${idx + 1}:$ser" else key
            } else key
        }.toSet()

        _state.update {
            it.copy(
                exercises = newExercises,
                extraSeriesMap = newExtraSeriesMap,
                exerciseStartTimes = newStartTimes,
                exerciseDurations = newDurations,
                skippedExercises = newSkipped,
                skippedSeries = newSkippedSeries,
                showAddExerciseDialog = false,
                showExerciseOverview = false
            )
        }
        sessionManager.updateTotalExercises(newExercises.size)
        persistAddedExercise(exercise, insertAt)
    }

    /** Insère un exercice à la fin de la séance (après tous les exos restants). */
    fun insertExerciseAtEnd(exercise: ExerciseEntity) {
        val s = _state.value
        val wasEmpty = s.exercises.isEmpty()
        val newExercises = s.exercises + exercise
        val newIndex = newExercises.size - 1

        sessionManager.updateTotalExercises(newExercises.size)
        persistAddedExercise(exercise, newIndex)

        // Déterminer si on doit NAVIGUER au nouvel exo ou juste l'ajouter à la queue :
        // - Séance vide : forcément naviguer (c'est le 1er exo)
        // - Freestyle avec exo courant TERMINÉ (all sets done) : naviguer (post-overview)
        // - Freestyle en cours d'overview : naviguer
        // - Freestyle mid-exercice (pas terminé) : NE PAS naviguer, juste ajouter à la queue
        // - Mode normal : ne jamais naviguer (juste ajouter)
        val currentExoDone = s.currentExercise?.let { cur ->
            val totalSets = cur.series + (s.extraSeriesMap[s.currentExerciseIndex] ?: 0)
            val setsDone = s.completedSets.count { it.exerciseId == cur.id && !it.skipped }
            totalSets > 0 && setsDone >= totalSets
        } ?: false
        val shouldNavigate = wasEmpty || (s.isFreestyle && (currentExoDone || s.showExerciseOverview))

        if (shouldNavigate) {
            // Freestyle : tout dans une seule coroutine pour navigation atomique
            viewModelScope.launch {
                val lastSets = loadLastSetsForExercise(exercise.id)
                val prWeight = runCatching { workoutRepository.getMaxWeightForExercise(exercise.id) }.getOrNull()
                val bodyWeight = s.userBodyWeightKg
                val initialWeight = if (exercise.isTimeBased) fmtWeightValue(bodyWeight)
                    else lastSets?.firstOrNull()?.let { w -> w.weightKg.toString() } ?: extractWeight(exercise)
                val now = LocalDateTime.now()
                val startTimes = s.exerciseStartTimes.toMutableMap()
                startTimes[newIndex] = now

                // Un seul state update atomique : ajoute l'exo + navigue + ferme dialog/overview
                _state.update {
                    it.copy(
                        exercises = newExercises,
                        showAddExerciseDialog = false,
                        addExerciseSearchQuery = "",
                        showExerciseOverview = false,
                        currentExerciseIndex = newIndex,
                        currentSeries = 1,
                        isSetInProgress = false,
                        setStartTime = null,
                        isRestTimerActive = false,
                        restTimeRemaining = 0,
                        restTimeElapsed = 0,
                        currentRestOverride = null,
                        exerciseStartTimes = startTimes,
                        currentSetWeight = initialWeight,
                        currentSetReps = exercise.repsMin.toString(),
                        currentSetTempo = exercise.tempo,
                        lastSessionWeight = lastSets?.firstOrNull()?.weightKg,
                        lastSessionReps = lastSets?.firstOrNull()?.reps,
                        personalRecordWeight = prWeight,
                        isPersonalRecord = false,
                        showPostLastSetPrompt = false
                    )
                }
                if (wasEmpty) {
                    sessionManager.startSession(
                        workoutLogId = s.workoutLogId ?: 0,
                        totalExercises = newExercises.size
                    )
                }
                sessionManager.updateExerciseInfo(exercise.name, newIndex)
                startExerciseChrono()
            }
        } else {
            // Mode normal OU freestyle mid-exercice : juste ajouter à la fin sans naviguer.
            // L'utilisateur continue son exercice courant.
            _state.update { it.copy(exercises = newExercises, showAddExerciseDialog = false, addExerciseSearchQuery = "") }
        }
    }

    /** Sauvegarde l'exercice ajouté à la volée dans le template pour cohérence DB. */
    private fun persistAddedExercise(exercise: ExerciseEntity, orderIndex: Int) {
        val s = _state.value
        val workoutLogId = s.workoutLogId ?: return
        viewModelScope.launch {
            val log = workoutRepository.getWorkoutLogById(workoutLogId) ?: return@launch
            val workoutId = log.workoutId ?: return@launch
            workoutRepository.insertWorkoutExercise(
                com.shredcoach.app.data.local.entity.WorkoutExerciseEntity(
                    workoutId = workoutId, exerciseId = exercise.id, orderIndex = orderIndex
                )
            )
        }
    }

    /** L'utilisateur ajuste le repos pour la série courante. Mis à jour pour le timer. */
    fun onRestSecondsChanged(rest: Int) {
        _state.update { it.copy(currentRestOverride = rest.coerceIn(15, 300)) }
    }

    /** Ajoute une série supplémentaire à l'exercice courant (à la volée, à tout moment). */
    fun addExtraSeries() {
        val s = _state.value
        s.currentExercise ?: return
        val extras = s.extraSeriesMap.toMutableMap()
        val current = extras[s.currentExerciseIndex] ?: 0
        extras[s.currentExerciseIndex] = current + 1
        // Si on était sur le prompt post-dernière-série, avancer à la nouvelle série
        val newCurrentSeries = if (s.showPostLastSetPrompt) s.currentSeries + 1 else s.currentSeries
        _state.update { it.copy(
            extraSeriesMap = extras,
            showPostLastSetPrompt = false,
            currentSeries = newCurrentSeries
        ) }
    }

    /** L'utilisateur confirme qu'il ne veut pas ajouter de série → passer à l'exo suivant. */
    fun confirmMoveToNextExercise() {
        // Ne pas reset showPostLastSetPrompt séparément : moveToNextExercise fait le reset
        // atomiquement dans le même state update que la transition → évite le flash de la session view.
        moveToNextExercise()
    }

    // ══════════════════════════════════════════
    // WORKFLOW SÉRIE
    // ══════════════════════════════════════════

    fun onSetStarted() {
        val exo = _state.value.currentExercise ?: return
        _state.update { it.copy(isSetInProgress = true, setStartTime = LocalDateTime.now()) }
        // Démarrer le décompte auto pour les exos chronométrés (gainage, etc.)
        // On exclut le cardio qui utilise son propre UI (chrono montant + "TERMINER LE CARDIO"),
        // car CardioSessionCard ne rend PAS le TimedSetCountdownHero → le décompte tournerait invisible.
        if (exo.isTimeBased && exo.muscleGroup != MuscleGroup.CARDIO) {
            // Fallback : exo.repsMin (qui stocke la durée cible pour les time-based) si le champ est vide
            val duration = _state.value.currentSetReps.toIntOrNull()?.coerceAtLeast(1)
                ?: exo.repsMin.coerceAtLeast(1)
            startTimedSet(duration)
        }
    }

    /**
     * Démarre un décompte pour une série chronométrée. Quand le décompte atteint 0,
     * `onSetCompleted` est appelé automatiquement → la série est validée.
     */
    private fun startTimedSet(durationSec: Int) {
        timedSetJob?.cancel()
        _state.update { it.copy(
            timedSetTotalSeconds = durationSec,
            timedSetSecondsRemaining = durationSec
        ) }
        timedSetJob = viewModelScope.launch {
            while (_state.value.timedSetSecondsRemaining > 0) {
                delay(1000)
                _state.update { it.copy(timedSetSecondsRemaining = (it.timedSetSecondsRemaining - 1).coerceAtLeast(0)) }
            }
            // Décompte terminé (secondsRemaining = 0 mais totalSeconds reste > 0 pour que
            // onSetCompleted sache qu'on vient d'un décompte auto-validé). onSetCompleted
            // réinitialise timedSet* à 0 lui-même dans son update de complétion.
            onSetCompleted()
        }
    }

    fun onSetCompleted() {
        val s = _state.value
        val exercise = s.currentExercise ?: return
        // Garde anti double-complétion (race auto-complete vs click user)
        if (!s.isSetInProgress) return

        // Arrêter le décompte de série chronométrée s'il est actif (user a terminé manuellement
        // ou décompte a atteint 0 et s'est auto-validé)
        val wasTimedSetRunning = s.timedSetTotalSeconds > 0
        timedSetJob?.cancel()
        if (wasTimedSetRunning) {
            _state.update { it.copy(timedSetSecondsRemaining = 0, timedSetTotalSeconds = 0) }
        }

        // Pour les exos au poids du corps : la moitié du poids du user est utilisée
        // dans le volume (l'user ne saisit pas de poids dans l'UI).
        val weight = if (exercise.variant == ExerciseVariant.BODYWEIGHT) {
            s.userBodyWeightKg / 2.0
        } else {
            s.currentSetWeight.toDoubleOrNull() ?: 0.0
        }
        val inputReps = s.currentSetReps.toIntOrNull() ?: exercise.repsMin
        val targetWeight = extractWeight(exercise).toDoubleOrNull() ?: 0.0
        val setDuration = s.setStartTime?.let { Duration.between(it, LocalDateTime.now()).seconds.toInt() }

        // Pour les exos chronométrés : si l'user a arrêté avant la fin, `reps` = durée réellement tenue.
        // Si le décompte a atteint 0 (auto-complétion), `reps` = cible (= durée tenue complète).
        val reps = if (exercise.isTimeBased && wasTimedSetRunning) {
            val elapsed = s.timedSetTotalSeconds - s.timedSetSecondsRemaining
            elapsed.coerceAtLeast(0).coerceAtMost(s.timedSetTotalSeconds)
        } else inputReps

        val isLastOfExercise = s.isLastSeries
        val exerciseDuration = if (isLastOfExercise) stopExerciseChrono() else null

        val setData = WorkoutSetData(
            exerciseId = exercise.id, seriesNumber = s.currentSeries,
            reps = reps, targetReps = exercise.repsMin,
            weight = weight, targetWeight = targetWeight,
            targetRestSeconds = exercise.restSeconds,
            tempoUsed = s.currentSetTempo, setDurationSeconds = setDuration,
            exerciseDurationSeconds = exerciseDuration
        )

        val updatedSets = s.completedSets + setData
        saveWorkoutSet(setData)

        if (isLastOfExercise) {
            val durations = s.exerciseDurations.toMutableMap()
            durations[s.currentExerciseIndex] = exerciseDuration ?: 0
            val isStrength = exercise.muscleGroup != MuscleGroup.WARMUP && exercise.muscleGroup != MuscleGroup.CARDIO
            if (isStrength && s.suggestBonusSeries) {
                // Exo muscu + réglage activé : proposer d'ajouter une série bonus (atomique)
                _state.update { it.copy(
                    completedSets = updatedSets, isSetInProgress = false, setStartTime = null,
                    exerciseDurations = durations, showPostLastSetPrompt = true
                ) }
            } else {
                // Muscu sans prompt OU warmup/cardio : transition directe
                // IMPORTANT : on passe updatedSets et durations à moveToNextExercise pour qu'il
                // fasse UN SEUL state update atomique (évite le flash "dernière série").
                moveToNextExercise(pendingCompletedSets = updatedSets, pendingDurations = durations)
            }
        } else {
            val effectiveRest = _state.value.effectiveRestSeconds
            _state.update {
                it.copy(
                    completedSets = updatedSets,
                    currentSeries = it.currentSeries + 1,
                    isSetInProgress = false, setStartTime = null,
                    isRestTimerActive = true,
                    restTimeRemaining = effectiveRest,
                    restTimeElapsed = 0
                )
            }
            startRestTimer()
        }
    }

    /** Refaire la dernière série (échec, mauvaise exécution) */
    fun redoLastSeries() {
        val s = _state.value
        if (s.completedSets.isEmpty()) return
        val exercise = s.currentExercise ?: return

        // Annuler le repos en cours
        restTimerJob?.cancel()

        // Retirer la dernière série
        val lastSet = s.completedSets.last()
        val updatedSets = s.completedSets.dropLast(1)

        _state.update {
            it.copy(
                completedSets = updatedSets,
                currentSeries = lastSet.seriesNumber,
                isSetInProgress = false,
                setStartTime = null,
                isRestTimerActive = false,
                restTimeRemaining = 0,
                restTimeElapsed = 0,
                // Remettre les valeurs de la série ratée pour correction
                currentSetWeight = lastSet.weight.toString(),
                currentSetReps = lastSet.reps.toString()
            )
        }
    }

    /** Skip une série (trop fatigué) */
    fun skipCurrentSeries() {
        val s = _state.value
        val exercise = s.currentExercise ?: return
        // Annuler le décompte chronométré s'il est actif + reset l'état
        timedSetJob?.cancel()
        if (s.timedSetTotalSeconds > 0) {
            _state.update { it.copy(timedSetSecondsRemaining = 0, timedSetTotalSeconds = 0) }
        }

        val skippedKey = "${s.currentExerciseIndex}:${s.currentSeries}"
        val setData = WorkoutSetData(
            exerciseId = exercise.id, seriesNumber = s.currentSeries,
            reps = 0, targetReps = exercise.repsMin,
            weight = 0.0, targetWeight = extractWeight(exercise).toDoubleOrNull() ?: 0.0,
            targetRestSeconds = exercise.restSeconds, skipped = true
        )
        val updatedSets = s.completedSets + setData

        if (s.isLastSeries) {
            val exerciseDuration = stopExerciseChrono()
            val durations = s.exerciseDurations.toMutableMap()
            durations[s.currentExerciseIndex] = exerciseDuration
            // Pré-update UNIQUEMENT les champs spécifiques au skip (skippedSeries)
            // puis passer updatedSets/durations à moveToNextExercise pour fusion atomique.
            _state.update { it.copy(skippedSeries = it.skippedSeries + skippedKey) }
            moveToNextExercise(pendingCompletedSets = updatedSets, pendingDurations = durations)
        } else {
            _state.update {
                it.copy(completedSets = updatedSets, skippedSeries = it.skippedSeries + skippedKey,
                    currentSeries = it.currentSeries + 1, isSetInProgress = false, setStartTime = null)
            }
        }
    }

    // ══════════════════════════════════════════
    // REPOS
    // ══════════════════════════════════════════

    private fun startRestTimer() {
        restTimerJob?.cancel()
        restTimerJob = viewModelScope.launch {
            while (_state.value.restTimeRemaining > 0) {
                delay(1000)
                _state.update { it.copy(restTimeRemaining = it.restTimeRemaining - 1, restTimeElapsed = it.restTimeElapsed + 1) }
            }
            // Repos terminé
            saveActualRest()
            _state.update { it.copy(isRestTimerActive = false) }
            // Auto-start série suivante si config activée
            if (_state.value.autoStartAfterRest) {
                onSetStarted()
            }
        }
    }

    fun skipRestTimer() {
        restTimerJob?.cancel()
        saveActualRest()
        _state.update { it.copy(isRestTimerActive = false, restTimeRemaining = 0) }
        // Auto-start
        if (_state.value.autoStartAfterRest) {
            onSetStarted()
        }
    }

    fun pauseRestTimer() { restTimerJob?.cancel() }

    fun resumeRestTimer() {
        if (_state.value.restTimeRemaining > 0) startRestTimer()
    }

    fun onRestComplete() { saveActualRest() }

    private fun saveActualRest() {
        val actualRest = _state.value.restTimeElapsed
        val sets = _state.value.completedSets.toMutableList()
        if (sets.isNotEmpty()) {
            val last = sets.last()
            sets[sets.lastIndex] = last.copy(restSecondsActual = actualRest)
        }
        _state.update { it.copy(completedSets = sets) }
    }

    // ══════════════════════════════════════════
    // NAVIGATION EXERCICES
    // ══════════════════════════════════════════

    /**
     * Passe à l'exercice suivant (ou à la fin / overview freestyle).
     * @param pendingCompletedSets si non-null, ces sets remplacent state.completedSets dans l'update atomique
     * @param pendingDurations si non-null, ces durées remplacent state.exerciseDurations dans l'update atomique
     * Ces paramètres permettent à onCompleteSet de fusionner sa mise à jour "complétion" avec la transition
     * en un SEUL state update → évite le flash visuel de la session view entre les deux updates.
     */
    private fun moveToNextExercise(
        pendingCompletedSets: List<WorkoutSetData>? = null,
        pendingDurations: Map<Int, Long>? = null
    ) {
        val s = _state.value

        // Annuler tout repos en cours + tout décompte chronométré en cours
        restTimerJob?.cancel()
        timedSetJob?.cancel()

        if (s.isLastExercise) {
            if (s.isFreestyle) {
                // Freestyle : afficher la transition puis l'overview (TOUT en UN update atomique)
                showFreestyleOverviewAfterExercise(pendingCompletedSets, pendingDurations)
                return
            }
            _state.update { it.copy(
                completedSets = pendingCompletedSets ?: it.completedSets,
                exerciseDurations = pendingDurations ?: it.exerciseDurations,
                isSetInProgress = false,
                setStartTime = null,
                showPostLastSetPrompt = false,
                isRestTimerActive = false,
                restTimeRemaining = 0,
                restTimeElapsed = 0,
                timedSetSecondsRemaining = 0,
                timedSetTotalSeconds = 0
            ) }
            completeWorkout()
            return
        }

        val nextIndex = s.currentExerciseIndex + 1
        val nextExercise = s.exercises.getOrNull(nextIndex)
        val now = LocalDateTime.now()
        val startTimes = s.exerciseStartTimes.toMutableMap()
        startTimes[nextIndex] = now

        // Informer le SessionManager
        sessionManager.updateExerciseInfo(nextExercise?.name ?: "", nextIndex)

        // Utiliser les données pending si fournies (fusion avec l'update)
        val effectiveCompletedSets = pendingCompletedSets ?: s.completedSets
        val effectiveDurations = pendingDurations ?: s.exerciseDurations

        // ─── Warmup → warmup : avancer DIRECTEMENT sans overlay de transition ───
        val currentIsWarmup = s.currentExercise?.muscleGroup == MuscleGroup.WARMUP
        val nextIsWarmup = nextExercise?.muscleGroup == MuscleGroup.WARMUP
        if (currentIsWarmup && nextIsWarmup) {
            _state.update {
                it.copy(
                    completedSets = effectiveCompletedSets,
                    exerciseDurations = effectiveDurations,
                    showPostLastSetPrompt = false,
                    currentExerciseIndex = nextIndex,
                    currentSeries = 1,
                    isSetInProgress = false, setStartTime = null,
                    isRestTimerActive = false, restTimeRemaining = 0, restTimeElapsed = 0,
                    timedSetSecondsRemaining = 0, timedSetTotalSeconds = 0,
                    currentSetWeight = extractWeight(nextExercise),
                    currentSetReps = nextExercise?.repsMin?.toString() ?: "",
                    currentRestOverride = null,
                    exerciseStartTimes = startTimes
                )
            }
            startExerciseChrono()
            return
        }

        // ─── Tous les autres cas : afficher la transition ───
        val currentExoId = s.currentExercise?.id ?: 0
        val exoSets = effectiveCompletedSets.filter { it.exerciseId == currentExoId }
        val doneSets = exoSets.filter { !it.skipped }
        val skippedSets = exoSets.filter { it.skipped }
        val exoDuration = effectiveDurations[s.currentExerciseIndex] ?: s.exerciseChronoSeconds
        val doneCount = effectiveCompletedSets.filter { !it.skipped }.map { it.exerciseId }.toSet().size
        val totalReps = doneSets.sumOf { set -> set.reps }
        val totalVol = doneSets.sumOf { set -> set.weight * set.reps }

        val firstName = s.userFirstName.ifBlank { "Champion" }
        val exoName = s.currentExercise?.name ?: ""

        // UN SEUL state update atomique : complétion + reset prompt/rest + transition → pas de flash
        _state.update {
            it.copy(
                // Complétion (fusion depuis onCompleteSet)
                completedSets = effectiveCompletedSets,
                exerciseDurations = effectiveDurations,
                // Reset prompt + rest + décompte chronométré
                showPostLastSetPrompt = false,
                isRestTimerActive = false,
                restTimeRemaining = 0,
                restTimeElapsed = 0,
                timedSetSecondsRemaining = 0,
                timedSetTotalSeconds = 0,
                isSetInProgress = false,
                setStartTime = null,
                // Transition
                showExerciseTransition = true,
                isShreddyThinking = true,
                shreddyCoachMessage = "",
                transitionFromName = s.currentExercise?.name ?: "",
                // En freestyle, on passe toujours par l'overview → afficher "Vue d'ensemble"
                // au lieu du nom du prochain exo (sinon "PROCHAIN EXERCICE: Y" est trompeur).
                transitionToName = if (s.isFreestyle) "Vue d'ensemble" else (nextExercise?.name ?: ""),
                transitionExercisesDone = doneCount,
                transitionTotalExercises = it.totalExercises,
                transitionExerciseSets = doneSets.size,
                transitionExerciseReps = totalReps,
                transitionExerciseVolume = totalVol,
                transitionExerciseDuration = exoDuration,
                transitionExerciseSkipped = skippedSets.size,
                pendingNextIndex = nextIndex,
                pendingNextStartTimes = startTimes
            )
        }

        // Appel LLM synchrone dans une coroutine (bloque l'affichage du message, pas la transition)
        viewModelScope.launch {
            val fallback = ShreddyCoachMessages.exerciseTransition(
                firstName = firstName, exerciseName = exoName,
                sets = doneSets.size, reps = totalReps,
                volume = totalVol, skipped = skippedSets.size,
                duration = exoDuration,
                exercisesDone = doneCount, totalExercises = s.totalExercises,
                isPersonalRecord = s.isPersonalRecord, goalName = s.userGoalName
            )

            val profile = userRepository.getUserProfileOnce()
            val apiKey = profile?.llmApiKey ?: ""

            if (apiKey.isNotBlank()) {
                val provider = try { LlmProvider.valueOf(profile?.llmProvider ?: "GROQ") } catch (_: Exception) { LlmProvider.GROQ }
                val model = profile?.llmModel?.takeIf { it.isNotBlank() }
                val prompt = ShreddyCoachMessages.buildExercisePrompt(
                    firstName = firstName, exerciseName = exoName,
                    sets = doneSets.size, reps = totalReps,
                    volume = totalVol, skipped = skippedSets.size,
                    duration = exoDuration,
                    exercisesDone = doneCount, totalExercises = s.totalExercises,
                    isPersonalRecord = s.isPersonalRecord, goalName = s.userGoalName
                )
                try {
                    val result = kotlinx.coroutines.withTimeout(15000) {
                        chatRepository.quickCoachMessage(prompt, ShreddyCoachMessages.COACH_SYSTEM_PROMPT, provider, apiKey, model)
                    }
                    result.fold(
                        onSuccess = { llmMsg ->
                            val msg = llmMsg.takeIf { it.isNotBlank() } ?: fallback
                            val source = if (llmMsg.isNotBlank()) "llm" else "local"
                            _state.update { it.copy(shreddyCoachMessage = msg, isShreddyThinking = false, shreddyMessageSource = source) }
                        },
                        onFailure = { error ->
                            // Erreur API — afficher l'erreur en debug pour diagnostiquer
                            android.util.Log.e("Shreddy", "LLM coach error: ${error.message}", error)
                            _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false, shreddyMessageSource = "local (erreur: ${error.message?.take(50)})") }
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("Shreddy", "LLM timeout/crash: ${e.message}", e)
                    _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false, shreddyMessageSource = "local (timeout)") }
                }
            } else {
                _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false, shreddyMessageSource = "local (pas de clé API)") }
            }
        }
    }

    /** Freestyle : après le dernier exercice, affiche la transition Shreddy puis redirige vers l'overview. */
    private fun showFreestyleOverviewAfterExercise(
        pendingCompletedSets: List<WorkoutSetData>? = null,
        pendingDurations: Map<Int, Long>? = null
    ) {
        val s = _state.value
        restTimerJob?.cancel()
        timedSetJob?.cancel()

        // Utiliser les données pending si fournies
        val effectiveCompletedSets = pendingCompletedSets ?: s.completedSets
        val effectiveDurations = pendingDurations ?: s.exerciseDurations

        val currentExoId = s.currentExercise?.id ?: 0
        val exoSets = effectiveCompletedSets.filter { it.exerciseId == currentExoId }
        val doneSets = exoSets.filter { !it.skipped }
        val skippedSets = exoSets.filter { it.skipped }
        val exoDuration = effectiveDurations[s.currentExerciseIndex] ?: s.exerciseChronoSeconds
        val doneCount = effectiveCompletedSets.filter { !it.skipped }.map { it.exerciseId }.toSet().size
        val totalReps = doneSets.sumOf { set -> set.reps }
        val totalVol = doneSets.sumOf { set -> set.weight * set.reps }
        val firstName = s.userFirstName.ifBlank { "Champion" }
        val exoName = s.currentExercise?.name ?: ""

        // UN SEUL state update atomique : complétion + reset prompt/rest + transition
        _state.update {
            it.copy(
                // Complétion (fusion depuis onCompleteSet)
                completedSets = effectiveCompletedSets,
                exerciseDurations = effectiveDurations,
                // Reset complet
                showPostLastSetPrompt = false,
                isRestTimerActive = false,
                restTimeRemaining = 0,
                restTimeElapsed = 0,
                timedSetSecondsRemaining = 0,
                timedSetTotalSeconds = 0,
                isSetInProgress = false,
                setStartTime = null,
                // Transition freestyle
                showExerciseTransition = true,
                isShreddyThinking = true,
                shreddyCoachMessage = "",
                transitionFromName = exoName,
                transitionToName = "Vue d'ensemble",
                transitionExercisesDone = doneCount,
                transitionTotalExercises = it.totalExercises,
                transitionExerciseSets = doneSets.size,
                transitionExerciseReps = totalReps,
                transitionExerciseVolume = totalVol,
                transitionExerciseDuration = exoDuration,
                transitionExerciseSkipped = skippedSets.size,
                pendingNextIndex = -1
            )
        }

        // Coaching LLM
        viewModelScope.launch {
            val fallback = ShreddyCoachMessages.exerciseTransition(
                firstName = firstName, exerciseName = exoName,
                sets = doneSets.size, reps = totalReps, volume = totalVol,
                skipped = skippedSets.size, duration = exoDuration,
                exercisesDone = doneCount, totalExercises = s.totalExercises,
                isPersonalRecord = s.isPersonalRecord, goalName = s.userGoalName
            )
            val profile = userRepository.getUserProfileOnce()
            val apiKey = profile?.llmApiKey ?: ""
            if (apiKey.isNotBlank()) {
                val provider = try { LlmProvider.valueOf(profile?.llmProvider ?: "GROQ") } catch (_: Exception) { LlmProvider.GROQ }
                val model = profile?.llmModel?.takeIf { it.isNotBlank() }
                val prompt = ShreddyCoachMessages.buildExercisePrompt(
                    firstName = firstName, exerciseName = exoName,
                    sets = doneSets.size, reps = totalReps, volume = totalVol,
                    skipped = skippedSets.size, duration = exoDuration,
                    exercisesDone = doneCount, totalExercises = s.totalExercises,
                    isPersonalRecord = s.isPersonalRecord, goalName = s.userGoalName
                )
                try {
                    val result = kotlinx.coroutines.withTimeout(15000) {
                        chatRepository.quickCoachMessage(prompt, ShreddyCoachMessages.COACH_SYSTEM_PROMPT, provider, apiKey, model)
                    }
                    result.fold(
                        onSuccess = { llmMsg ->
                            _state.update { it.copy(shreddyCoachMessage = llmMsg.takeIf { m -> m.isNotBlank() } ?: fallback, isShreddyThinking = false) }
                        },
                        onFailure = {
                            _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false) }
                        }
                    )
                } catch (_: Exception) {
                    _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false) }
                }
            } else {
                _state.update { it.copy(shreddyCoachMessage = fallback, isShreddyThinking = false) }
            }
        }
    }

    fun skipToNextExercise() {
        val s = _state.value
        val exerciseDuration = stopExerciseChrono()
        val skipped = s.skippedExercises + s.currentExerciseIndex
        val durations = s.exerciseDurations.toMutableMap()
        durations[s.currentExerciseIndex] = exerciseDuration
        // Mettre à jour skippedExercises d'abord (hors transition atomique car c'est un champ spécifique au skip)
        _state.update { it.copy(skippedExercises = skipped, isSetInProgress = false) }
        restTimerJob?.cancel()
        // Passer durations à moveToNextExercise pour qu'il les fusionne dans l'update atomique
        moveToNextExercise(pendingDurations = durations)
    }

    fun dismissTransition() {
        val s = _state.value
        val nextIndex = s.pendingNextIndex
        if (nextIndex < 0) {
            // Freestyle : après transition du dernier exercice → overview
            if (s.isFreestyle) {
                _state.update { it.copy(showExerciseTransition = false, shreddyCoachMessage = "", isShreddyThinking = false, showExerciseOverview = true) }
                return
            }
            _state.update { it.copy(showExerciseTransition = false, shreddyCoachMessage = "", isShreddyThinking = false) }
            return
        }

        // Freestyle : toujours passer par l'overview entre les exercices
        if (s.isFreestyle) {
            _state.update { it.copy(
                showExerciseTransition = false, shreddyCoachMessage = "", isShreddyThinking = false,
                showExerciseOverview = true, pendingNextIndex = -1, pendingNextStartTimes = emptyMap()
            ) }
            return
        }

        val nextExercise = s.exercises.getOrNull(nextIndex)

        // Annuler tout timer de repos résiduel
        restTimerJob?.cancel()

        // Charger les suggestions de poids + PR pour le prochain exercice
        viewModelScope.launch {
            val lastSets = nextExercise?.let { loadLastSetsForExercise(it.id) }
            val suggestedWeight = lastSets?.firstOrNull()?.weightKg
            val prWeight = nextExercise?.let { runCatching { workoutRepository.getMaxWeightForExercise(it.id) }.getOrNull() }

            _state.update {
                it.copy(
                    showExerciseTransition = false,
                    shreddyCoachMessage = "",
                    isShreddyThinking = false, // Reset après dismiss
                    currentExerciseIndex = nextIndex,
                    currentSeries = 1,
                    isSetInProgress = false, setStartTime = null,
                    // Reset repos obligatoire + décompte chronométré
                    isRestTimerActive = false,
                    restTimeRemaining = 0,
                    restTimeElapsed = 0,
                    timedSetSecondsRemaining = 0,
                    timedSetTotalSeconds = 0,
                    // Pré-remplissage
                    currentSetWeight = if (nextExercise?.isTimeBased == true) fmtWeightValue(s.userBodyWeightKg)
                        else suggestedWeight?.toString() ?: extractWeight(nextExercise),
                    currentSetReps = nextExercise?.repsMin?.toString() ?: "",
                    currentSetTempo = nextExercise?.tempo ?: "3-0-1-0",
                    currentRestOverride = null, // Reset pour le nouvel exercice
                    exerciseStartTimes = s.pendingNextStartTimes,
                    lastSessionWeight = suggestedWeight,
                    lastSessionReps = lastSets?.firstOrNull()?.reps,
                    personalRecordWeight = prWeight,
                    isPersonalRecord = false,
                    pendingNextIndex = -1,
                    pendingNextStartTimes = emptyMap()
                )
            }
            startExerciseChrono()
        }
    }

    // ══════════════════════════════════════════
    // FIN DE SÉANCE
    // ══════════════════════════════════════════

    /** Appelé depuis l'overview en mode freestyle pour terminer la séance. */
    fun completeWorkoutFromOverview() {
        // NE PAS reset showExerciseOverview ici → évite le flash de la session view de
        // l'exercice terminé. L'overview reste visible jusqu'à la navigation vers le summary.
        completeWorkout()
    }

    private fun completeWorkout() {
        val s = _state.value
        // Lire le chrono directement depuis la source (pas le state qui peut être en retard)
        val actualDuration = sessionManager.getCurrentSeconds()
        // Sauver les stats AVANT de détruire la session
        sessionManager.saveSessionStats(
            duration = actualDuration,
            volume = s.totalVolume,
            sets = s.totalSetsCompleted,
            reps = s.totalRepsCompleted,
            restSeconds = s.totalRestSeconds,
            skipped = s.skippedExercises.size,
            workoutLogId = s.workoutLogId ?: 0
        )
        sessionManager.stopSession()

        viewModelScope.launch {
            try {
                val workoutLogId = s.workoutLogId ?: return@launch
                val endTime = LocalDateTime.now()
                val log = workoutRepository.getWorkoutLogById(workoutLogId) ?: return@launch
                val updatedLog = log.copy(
                    completed = true, endTime = endTime,
                    actualDurationSeconds = actualDuration,
                    totalVolume = s.totalVolume, totalSets = s.totalSetsCompleted,
                    totalReps = s.totalRepsCompleted, totalRestSeconds = s.totalRestSeconds,
                    exercisesCompleted = s.exercisesCompletedCount,
                    exercisesSkipped = s.skippedExercises.size
                )
                workoutRepository.updateWorkoutLog(updatedLog)
                // Incrémenter le compteur de séances du profil
                userRepository.incrementTotalWorkouts()

                // ─── Calendar integration : marquer une séance planifiée aujourd'hui comme complétée ───
                try {
                    val today = java.time.LocalDate.now()
                    val now = java.time.LocalTime.now()
                    val todaySchedules = scheduledWorkoutRepository.getBetweenOnce(today, today)
                        .filter { it.status == "PLANNED" }
                    // Si plusieurs : prendre la plus proche de maintenant (celle que l'user
                    // vient de terminer le plus probablement). Les séances sans heure passent en dernier.
                    val bestMatch = todaySchedules.minByOrNull { sched ->
                        val t = sched.time
                        if (t == null) Long.MAX_VALUE
                        else kotlin.math.abs(java.time.Duration.between(now, t).toMinutes())
                    }
                    bestMatch?.let { sched ->
                        scheduledWorkoutRepository.markCompleted(sched.id, workoutLogId)
                        com.shredcoach.app.notification.NotificationScheduler
                            .cancelWorkoutReminders(appContext, sched.id)
                    }
                } catch (_: Exception) { /* non bloquant */ }

                // Charger le profil UNE SEULE FOIS pour la suite (débrief delay + LLM coaching)
                val profileFresh = userRepository.getUserProfileOnce()
                // Programmer le débrief IA avec délai configuré dans user settings
                val debriefDelay = (profileFresh?.workoutDebriefDelayMinutes ?: 30).toLong()
                com.shredcoach.app.notification.NotificationScheduler
                    .scheduleWorkoutDebrief(appContext, workoutLogId, debriefDelay)

                val firstName = s.userFirstName.ifBlank { "Champion" }
                val fallbackMsg = ShreddyCoachMessages.sessionComplete(
                    firstName = firstName,
                    totalSets = s.totalSetsCompleted, totalReps = s.totalRepsCompleted,
                    totalVolume = s.totalVolume, durationMinutes = actualDuration / 60,
                    exercisesCompleted = s.exercisesCompletedCount,
                    exercisesSkipped = s.skippedExercises.size,
                    streak = s.userStreak, goalName = s.userGoalName
                )

                // Naviguer IMMÉDIATEMENT vers le summary (pas de blocage LLM)
                sessionManager.lastShreddyMessage = fallbackMsg
                _state.update { it.copy(isSessionComplete = true, shreddyCoachMessage = fallbackMsg) }

                // Appel LLM en arrière-plan — met à jour le summary si encore visible
                val profile2 = profileFresh
                val apiKey = profile2?.llmApiKey ?: ""
                if (apiKey.isNotBlank()) {
                    viewModelScope.launch {
                        val provider = try { LlmProvider.valueOf(profile2?.llmProvider ?: "GROQ") } catch (_: Exception) { LlmProvider.GROQ }
                        val model = profile2?.llmModel?.takeIf { it.isNotBlank() }
                        val prompt = ShreddyCoachMessages.buildSessionPrompt(
                            firstName = firstName,
                            totalSets = s.totalSetsCompleted, totalReps = s.totalRepsCompleted,
                            totalVolume = s.totalVolume, durationMinutes = actualDuration / 60,
                            exercisesCompleted = s.exercisesCompletedCount,
                            exercisesSkipped = s.skippedExercises.size,
                            streak = s.userStreak, goalName = s.userGoalName
                        )
                        try {
                            val result = kotlinx.coroutines.withTimeout(5000) {
                                chatRepository.quickCoachMessage(prompt, ShreddyCoachMessages.COACH_SYSTEM_PROMPT, provider, apiKey, model)
                            }
                            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { llmMsg ->
                                sessionManager.lastShreddyMessage = llmMsg
                            }
                        } catch (_: Exception) { /* fallback déjà en place */ }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Erreur sauvegarde: ${e.message}") }
            }
        }
    }

    fun stopSessionEarly() { stopExerciseChrono(); completeWorkout() }

    // ══════════════════════════════════════════
    // SHREDDY COACHING LLM (async)
    // ══════════════════════════════════════════

    // ══════════════════════════════════════════
    // NAVIGATION LIBRE (sauter à un exo / supprimer)
    // ══════════════════════════════════════════

    /** Saute directement à l'exercice à l'index donné (skip tous les intermédiaires). */
    fun jumpToExercise(targetIndex: Int) {
        val s = _state.value
        if (targetIndex < 0 || targetIndex >= s.exercises.size) return
        if (targetIndex == s.currentExerciseIndex) return

        // Arrêter le chrono exercice courant + repos + décompte chronométré
        stopExerciseChrono()
        restTimerJob?.cancel()
        timedSetJob?.cancel()

        val now = LocalDateTime.now()
        val startTimes = s.exerciseStartTimes.toMutableMap()
        startTimes[targetIndex] = now
        val nextExercise = s.exercises[targetIndex]

        sessionManager.updateExerciseInfo(nextExercise.name, targetIndex)

        viewModelScope.launch {
            val lastSets = loadLastSetsForExercise(nextExercise.id)
            val suggestedWeight = lastSets.firstOrNull()?.weightKg
            val prWeight = runCatching { workoutRepository.getMaxWeightForExercise(nextExercise.id) }.getOrNull()

            _state.update {
                it.copy(
                    showExerciseOverview = false,
                    currentExerciseIndex = targetIndex,
                    currentSeries = 1,
                    isSetInProgress = false, setStartTime = null,
                    isRestTimerActive = false, restTimeRemaining = 0, restTimeElapsed = 0,
                    timedSetSecondsRemaining = 0, timedSetTotalSeconds = 0,
                    showExerciseTransition = false, showPostLastSetPrompt = false,
                    currentRestOverride = null,
                    currentSetWeight = if (nextExercise.isTimeBased) fmtWeightValue(it.userBodyWeightKg)
                        else suggestedWeight?.toString() ?: extractWeight(nextExercise),
                    currentSetReps = nextExercise.repsMin.toString(),
                    currentSetTempo = nextExercise.tempo,
                    exerciseStartTimes = startTimes,
                    lastSessionWeight = suggestedWeight,
                    lastSessionReps = lastSets.firstOrNull()?.reps,
                    personalRecordWeight = prWeight,
                    isPersonalRecord = false,
                    shreddyCoachMessage = "", isShreddyThinking = false
                )
            }
            startExerciseChrono()
        }
    }

    /** Supprime un exercice à venir de la liste. Ne peut pas supprimer l'exercice en cours. */
    fun removeExercise(targetIndex: Int) {
        val s = _state.value
        if (targetIndex <= s.currentExerciseIndex) return // Pas les exos passés/courant
        if (targetIndex >= s.exercises.size) return

        val newExercises = s.exercises.toMutableList()
        newExercises.removeAt(targetIndex)

        // Décaler les maps d'indices
        val newExtras = s.extraSeriesMap.filterKeys { it != targetIndex }
            .mapKeys { (k, _) -> if (k > targetIndex) k - 1 else k }
        val newStartTimes = s.exerciseStartTimes.filterKeys { it != targetIndex }
            .mapKeys { (k, _) -> if (k > targetIndex) k - 1 else k }
        val newDurations = s.exerciseDurations.filterKeys { it != targetIndex }
            .mapKeys { (k, _) -> if (k > targetIndex) k - 1 else k }
        val newSkipped = s.skippedExercises.map { if (it > targetIndex) it - 1 else it }.toSet()
        val newSkippedSeries = s.skippedSeries.map { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val idx = parts[0].toIntOrNull() ?: return@map key
                if (idx > targetIndex) "${idx - 1}:${parts[1]}" else key
            } else key
        }.toSet()

        _state.update {
            it.copy(
                exercises = newExercises,
                extraSeriesMap = newExtras,
                exerciseStartTimes = newStartTimes,
                exerciseDurations = newDurations,
                skippedExercises = newSkipped,
                skippedSeries = newSkippedSeries
            )
        }
        sessionManager.updateTotalExercises(newExercises.size)
    }

    fun toggleExerciseOverview() {
        val s = _state.value
        // En freestyle, si l'exercice courant est terminé (tous ses sets faits), on NE peut PAS
        // fermer l'overview (ça retournerait sur la session d'un exo déjà terminé).
        if (s.showExerciseOverview && s.isFreestyle) {
            val currentExo = s.currentExercise
            if (currentExo != null) {
                val totalSets = currentExo.series + (s.extraSeriesMap[s.currentExerciseIndex] ?: 0)
                val setsDone = s.completedSets.count { it.exerciseId == currentExo.id && !it.skipped }
                if (totalSets > 0 && setsDone >= totalSets) return // Bloquer la fermeture
            }
        }
        _state.update { it.copy(showExerciseOverview = !it.showExerciseOverview) }
    }

    // fetchLlmCoachMessage supprimé — appels LLM intégrés directement dans moveToNextExercise/completeWorkout

    // ══════════════════════════════════════════
    // SAUVEGARDE DB
    // ══════════════════════════════════════════

    private fun saveWorkoutSet(setData: WorkoutSetData) {
        viewModelScope.launch {
            try {
                val workoutLogId = _state.value.workoutLogId ?: return@launch
                val set = WorkoutSetEntity(
                    workoutLogId = workoutLogId, exerciseId = setData.exerciseId,
                    setNumber = setData.seriesNumber, reps = setData.reps,
                    targetReps = setData.targetReps, weightKg = setData.weight,
                    targetWeightKg = setData.targetWeight, restSeconds = setData.restSecondsActual,
                    targetRestSeconds = setData.targetRestSeconds, tempoUsed = setData.tempoUsed,
                    setDurationSeconds = setData.setDurationSeconds,
                    exerciseDurationSeconds = setData.exerciseDurationSeconds,
                    completed = !setData.skipped
                )
                workoutRepository.insertWorkoutSet(set)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getSessionDuration(): Duration? {
        val startTime = _state.value.sessionStartTime ?: return null
        return Duration.between(startTime, LocalDateTime.now())
    }

    private suspend fun loadLastSetsForExercise(exerciseId: Long): List<WorkoutSetEntity> {
        return try {
            workoutRepository.getRecentSetsForExercise(exerciseId)
                .filter { it.completed && it.workoutLogId != _state.value.workoutLogId }
        } catch (_: Exception) { emptyList() }
    }

    private fun extractWeight(exercise: ExerciseEntity?): String {
        if (exercise == null) return ""
        val match = Regex("\\d+\\.?\\d*").find(exercise.startingWeight)
        return match?.value ?: "0"
    }

    override fun onCleared() {
        super.onCleared()
        // NE PAS arrêter le SessionManager — le chrono global continue
        exerciseChronoJob?.cancel(); restTimerJob?.cancel(); timedSetJob?.cancel()
    }
}
