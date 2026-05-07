package com.shredcoach.app.presentation.workout


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.domain.workout.RoutineCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

enum class CreationMode { TEMPLATE, BLANK }

data class CustomExerciseSlot(
    val muscleGroup: MuscleGroup,
    val selectedExercise: ExerciseEntity? = null,
    val series: Int = 5,
    val repsMin: Int = 8,
    val repsMax: Int = 12,
    val restSeconds: Int = 90,
    val weight: String = "",
    /** Durée en minutes pour warmup/cardio (null pour muscu, car calculé à partir des sets). */
    val durationMinutes: Int? = null
)

@Immutable
data class CustomWorkoutState(
    val name: String = "Ma séance du ${java.time.LocalDate.now().let { "${it.dayOfMonth}/${it.monthValue}" }}",
    val durationMinutes: Int = 90,
    val creationMode: CreationMode = CreationMode.TEMPLATE,
    val slots: List<CustomExerciseSlot> = emptyList(),
    val availableExercises: Map<MuscleGroup, List<ExerciseEntity>> = emptyMap(),
    val showExercisePicker: Boolean = false,
    val pickerMuscleGroup: MuscleGroup? = null,
    val pickerSlotIndex: Int = -1,
    val showAddSlotPicker: Boolean = false,
    val addSlotSection: WorkoutSection? = null,
    val pendingModeSwitch: CreationMode? = null,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val savedWorkoutLogId: Long? = null,
    val isFavorite: Boolean = false,
    val savedFavoriteId: Long? = null,
    val vibrationEnabled: Boolean = true,
    /**
     * Routine cible (Full Body, Push, Pull, …). Utilisée à la création du
     * [WorkoutEntity] et du [WorkoutLogEntity] pour que la séance custom
     * apparaisse correctement dans les stats par routine.
     */
    val routineId: String = "full_body",
) {
    /** Nombre d'exos muscu recommandé pour la durée choisie (hors warmup + cardio). */
    val recommendedStrengthCount: Int
        get() = when (durationMinutes) {
            60 -> 5
            90 -> 7
            120 -> 9
            150 -> 10
            180 -> 12
            else -> (durationMinutes / 13).coerceIn(4, 14)
        }
    /** Nombre d'exos muscu actuellement dans la séance. */
    val currentStrengthCount: Int
        get() = slots.count { it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO }

    /** Secondes estimées pour un slot individuel. */
    fun slotDurationSeconds(slot: CustomExerciseSlot): Int = when (slot.muscleGroup) {
        MuscleGroup.WARMUP -> (slot.durationMinutes ?: 3) * 60
        MuscleGroup.CARDIO -> (slot.durationMinutes ?: 15) * 60
        else -> {
            val avgReps = ((slot.repsMin + slot.repsMax) / 2).coerceAtLeast(1)
            val effortPerRep = 4 // sec (2s concentrique + 2s excentrique)
            val workSec = slot.series * avgReps * effortPerRep
            val restSec = (slot.series - 1).coerceAtLeast(0) * slot.restSeconds
            workSec + restSec
        }
    }

    /** Durée estimée par section (warmup, strength, cardio). */
    val warmupSeconds: Int get() = slots.filter { it.muscleGroup == MuscleGroup.WARMUP }.sumOf { slotDurationSeconds(it) }
    val strengthSeconds: Int get() = slots.filter { it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO }.sumOf { slotDurationSeconds(it) }
    val cardioSeconds: Int get() = slots.filter { it.muscleGroup == MuscleGroup.CARDIO }.sumOf { slotDurationSeconds(it) }
    /** Temps de transition entre exos (setup, déplacement) : 60s par slot muscu. */
    val transitionSeconds: Int get() = slots.count { it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO } * 60

    /** Durée totale estimée (secondes). */
    val estimatedTotalSeconds: Int get() = warmupSeconds + strengthSeconds + cardioSeconds + transitionSeconds
    /** Durée totale estimée (minutes, arrondie). */
    val estimatedTotalMinutes: Int get() = (estimatedTotalSeconds + 30) / 60

    /** Nombre de slots par section. */
    fun slotsInSection(section: WorkoutSection): List<IndexedSlot> {
        val result = mutableListOf<IndexedSlot>()
        slots.forEachIndexed { i, slot ->
            val matches = when (section) {
                WorkoutSection.WARMUP -> slot.muscleGroup == MuscleGroup.WARMUP
                WorkoutSection.STRENGTH -> slot.muscleGroup != MuscleGroup.WARMUP && slot.muscleGroup != MuscleGroup.CARDIO
                WorkoutSection.CARDIO -> slot.muscleGroup == MuscleGroup.CARDIO
            }
            if (matches) result.add(IndexedSlot(i, slot))
        }
        return result
    }
}

/** Slot avec son index dans la liste globale — nécessaire pour les updates. */
data class IndexedSlot(val index: Int, val slot: CustomExerciseSlot)

/** Les 3 grandes sections d'une séance. */
enum class WorkoutSection(
    val displayName: String,
    @androidx.annotation.StringRes val displayNameRes: Int,
) {
    WARMUP("Échauffement", com.shredcoach.app.R.string.workout_section_warmup),
    STRENGTH("Musculation", com.shredcoach.app.R.string.workout_section_strength),
    CARDIO("Cardio", com.shredcoach.app.R.string.workout_section_cardio)
}

@HiltViewModel
class CustomWorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CustomWorkoutState())
    val state: StateFlow<CustomWorkoutState> = _state.asStateFlow()

    init { loadExercises(); loadVibrationSetting() }

    private fun loadVibrationSetting() {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            _state.update { it.copy(
                vibrationEnabled = profile?.vibrationEnabled ?: true,
                // Pré-sélection de la routine sur le dernier choix utilisateur.
                routineId = profile?.lastUsedRoutineId
                    ?.let { id -> RoutineCatalog.byId(id).id }
                    ?: RoutineCatalog.Default.id,
            ) }
        }
    }

    /** Change la routine cible. Résolution défensive via [RoutineCatalog.byId]. */
    fun selectRoutine(routineId: String) {
        _state.update { it.copy(routineId = RoutineCatalog.byId(routineId).id) }
    }

    private fun loadExercises() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().first().let { exercises ->
                val byGroup = exercises.groupBy { it.muscleGroup }
                _state.update { it.copy(
                    availableExercises = byGroup,
                    slots = buildTemplateSlots(byGroup),
                    isLoading = false
                ) }
            }
        }
    }

    /** Construit les slots pré-remplis du mode TEMPLATE (warmup + 7 muscu + cardio). */
    private fun buildTemplateSlots(byGroup: Map<MuscleGroup, List<ExerciseEntity>>): List<CustomExerciseSlot> {
        val defaultSlots = mutableListOf<CustomExerciseSlot>()
        byGroup[MuscleGroup.WARMUP]?.forEach { warmupExo ->
            defaultSlots.add(CustomExerciseSlot(MuscleGroup.WARMUP, warmupExo, durationMinutes = 3))
        }
        listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK_WIDTH, MuscleGroup.SHOULDERS,
               MuscleGroup.BICEPS, MuscleGroup.TRICEPS, MuscleGroup.ABS_UPPER).forEach { mg ->
            val defaultExo = byGroup[mg]?.firstOrNull()
            defaultSlots.add(CustomExerciseSlot(mg, defaultExo,
                series = defaultExo?.series ?: 4,
                repsMin = defaultExo?.repsMin ?: 8, repsMax = defaultExo?.repsMax ?: 12,
                restSeconds = defaultExo?.restSeconds ?: 90))
        }
        defaultSlots.add(CustomExerciseSlot(MuscleGroup.CARDIO, byGroup[MuscleGroup.CARDIO]?.firstOrNull(), durationMinutes = 15))
        return defaultSlots
    }

    /** Bascule entre TEMPLATE et BLANK. En BLANK, vide les slots. En TEMPLATE, les repeuple.
     *  Force=true pour skipper la confirmation quand l'utilisateur a déjà validé. */
    fun switchCreationMode(mode: CreationMode, force: Boolean = false) {
        val current = _state.value
        if (current.creationMode == mode) return
        // Protection : si l'utilisateur a des slots et veut switcher, demander confirmation
        if (!force && current.slots.isNotEmpty()) {
            _state.update { it.copy(pendingModeSwitch = mode) }
            return
        }
        val newSlots = when (mode) {
            CreationMode.TEMPLATE -> buildTemplateSlots(current.availableExercises)
            CreationMode.BLANK -> emptyList()
        }
        _state.update { it.copy(creationMode = mode, slots = newSlots, pendingModeSwitch = null) }
    }

    fun confirmModeSwitch() {
        val pending = _state.value.pendingModeSwitch ?: return
        switchCreationMode(pending, force = true)
    }
    fun cancelModeSwitch() {
        _state.update { it.copy(pendingModeSwitch = null) }
    }

    fun onNameChanged(name: String) { _state.update { it.copy(name = name) } }

    /** Change la durée cible. Les slots ne sont PAS tronqués automatiquement (libre à l'utilisateur). */
    fun onDurationChanged(minutes: Int) {
        _state.update { it.copy(durationMinutes = minutes.coerceIn(45, 180)) }
    }

    /**
     * Ajuste intelligemment le nombre d'exercices muscu pour coller à la durée choisie.
     * - Ajoute des slots depuis les groupes musculaires sous-représentés (ordre canonique).
     * - Retire les slots muscu excédentaires en commençant par la fin (les derniers ajoutés).
     * Conserve toujours warmup + cardio.
     */
    fun autoAdjustSlotsToDuration() {
        val s = _state.value
        val target = s.recommendedStrengthCount
        val current = s.currentStrengthCount
        if (current == target) return

        val mutableSlots = s.slots.toMutableList()

        if (current > target) {
            // Retirer les exos muscu excédentaires (les derniers)
            var toRemove = current - target
            val strengthIndices = mutableSlots.mapIndexedNotNull { i, slot ->
                if (slot.muscleGroup != MuscleGroup.WARMUP && slot.muscleGroup != MuscleGroup.CARDIO) i else null
            }
            // On retire à partir de la fin
            strengthIndices.reversed().take(toRemove).sortedDescending().forEach { idx ->
                mutableSlots.removeAt(idx)
            }
        } else {
            // Ajouter des exos muscu manquants
            val needed = target - current
            // Ordre canonique de rotation pour varier les muscles
            val canonicalOrder = listOf(
                MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK_WIDTH,
                MuscleGroup.SHOULDERS, MuscleGroup.BICEPS, MuscleGroup.TRICEPS,
                MuscleGroup.HAMSTRINGS, MuscleGroup.BACK_THICKNESS, MuscleGroup.CHEST_UPPER,
                MuscleGroup.ABS_UPPER, MuscleGroup.ABS_LOWER, MuscleGroup.CALVES,
                MuscleGroup.TRAPS, MuscleGroup.FOREARMS
            )
            // Compte les occurrences actuelles par groupe
            val counts = mutableMapOf<MuscleGroup, Int>().apply {
                mutableSlots.forEach { slot ->
                    if (slot.muscleGroup != MuscleGroup.WARMUP && slot.muscleGroup != MuscleGroup.CARDIO) {
                        this[slot.muscleGroup] = (this[slot.muscleGroup] ?: 0) + 1
                    }
                }
            }
            // Position d'insertion : juste avant le cardio (dernier slot) ou à la fin
            val cardioIdx = mutableSlots.indexOfLast { it.muscleGroup == MuscleGroup.CARDIO }
            val insertAt = if (cardioIdx >= 0) cardioIdx else mutableSlots.size

            // Trouve les groupes les moins représentés dans l'ordre canonique et ajoute
            val available = s.availableExercises
            var added = 0
            var round = 0
            while (added < needed && round < 3) {
                canonicalOrder.forEach { mg ->
                    if (added >= needed) return@forEach
                    val currentCount = counts[mg] ?: 0
                    if (currentCount <= round) {
                        val defaultExo = available[mg]?.firstOrNull()
                        if (defaultExo != null) {
                            mutableSlots.add(insertAt + added, CustomExerciseSlot(
                                muscleGroup = mg, selectedExercise = defaultExo,
                                series = defaultExo.series, repsMin = defaultExo.repsMin,
                                repsMax = defaultExo.repsMax, restSeconds = defaultExo.restSeconds
                            ))
                            counts[mg] = currentCount + 1
                            added++
                        }
                    }
                }
                round++
            }
        }

        _state.update { it.copy(slots = mutableSlots) }
    }

    fun toggleFavorite() {
        val newValue = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = newValue) }
        // Sauvegarder immédiatement en DB si activé
        if (newValue) {
            saveFavoriteNow()
        } else {
            _state.value.savedFavoriteId?.let { id ->
                viewModelScope.launch { workoutRepository.setFavorite(id, false) }
                _state.update { it.copy(savedFavoriteId = null) }
            }
        }
    }

    private fun saveFavoriteNow() {
        val s = _state.value
        val slotsWithExo = s.slots.filter { it.selectedExercise != null }
        if (slotsWithExo.isEmpty()) return
        viewModelScope.launch {
            val entity = WorkoutEntity(
                name = s.name, durationMinutes = s.durationMinutes, exerciseCount = slotsWithExo.size,
                isTemplate = true, isCustom = true, isFavorite = true,
                routineId = s.routineId,
            )
            val workoutId = workoutRepository.insertWorkout(entity)
            workoutRepository.insertWorkoutExercises(slotsWithExo.mapIndexed { i, slot ->
                val exo = slot.selectedExercise!!
                WorkoutExerciseEntity(
                    workoutId = workoutId, exerciseId = exo.id, orderIndex = i,
                    customSeries = if (slot.series != exo.series) slot.series else null,
                    customRepsMin = if (slot.repsMin != exo.repsMin) slot.repsMin else null,
                    customRepsMax = if (slot.repsMax != exo.repsMax) slot.repsMax else null,
                    customRestSeconds = if (slot.restSeconds != exo.restSeconds) slot.restSeconds else null
                )
            })
            _state.update { it.copy(savedFavoriteId = workoutId) }
        }
    }

    fun openExercisePicker(slotIndex: Int) {
        val slot = _state.value.slots.getOrNull(slotIndex) ?: return
        _state.update { it.copy(showExercisePicker = true, pickerMuscleGroup = slot.muscleGroup, pickerSlotIndex = slotIndex) }
    }

    fun closePicker() { _state.update { it.copy(showExercisePicker = false) } }

    fun selectExercise(exercise: ExerciseEntity) {
        val idx = _state.value.pickerSlotIndex
        if (idx < 0) return
        val slots = _state.value.slots.toMutableList()
        slots[idx] = slots[idx].copy(
            selectedExercise = exercise,
            series = exercise.series,
            repsMin = exercise.repsMin,
            repsMax = exercise.repsMax,
            restSeconds = exercise.restSeconds
        )
        _state.update { it.copy(slots = slots, showExercisePicker = false) }
    }

    fun updateSlotSeries(index: Int, delta: Int) {
        val slots = _state.value.slots.toMutableList()
        val s = slots[index]
        slots[index] = s.copy(series = (s.series + delta).coerceIn(1, 10))
        _state.update { it.copy(slots = slots) }
    }

    fun updateSlotReps(index: Int, delta: Int) {
        val slots = _state.value.slots.toMutableList()
        val s = slots[index]
        slots[index] = s.copy(repsMin = (s.repsMin + delta).coerceIn(1, 30), repsMax = (s.repsMax + delta).coerceIn(1, 30))
        _state.update { it.copy(slots = slots) }
    }

    fun updateSlotRest(index: Int, delta: Int) {
        val slots = _state.value.slots.toMutableList()
        val s = slots[index]
        slots[index] = s.copy(restSeconds = (s.restSeconds + delta).coerceIn(15, 300))
        _state.update { it.copy(slots = slots) }
    }

    fun removeSlot(index: Int) {
        val slots = _state.value.slots.toMutableList()
        slots.removeAt(index)
        _state.update { it.copy(slots = slots) }
    }

    fun moveSlot(from: Int, to: Int) {
        val slots = _state.value.slots.toMutableList()
        if (from !in slots.indices || to !in slots.indices) return
        // Empêche de traverser les frontières de section (warmup / muscu / cardio)
        val fromGroup = slots[from].muscleGroup
        val toGroup = slots[to].muscleGroup
        val sameSection = when {
            fromGroup == MuscleGroup.WARMUP -> toGroup == MuscleGroup.WARMUP
            fromGroup == MuscleGroup.CARDIO -> toGroup == MuscleGroup.CARDIO
            else -> toGroup != MuscleGroup.WARMUP && toGroup != MuscleGroup.CARDIO
        }
        if (!sameSection) return
        val item = slots.removeAt(from)
        slots.add(to, item)
        _state.update { it.copy(slots = slots) }
    }

    /**
     * Ajoute un slot VIDE (sans exercice) pour le groupe musculaire,
     * puis ouvre automatiquement le picker pour que l'utilisateur choisisse.
     */
    fun addSlot(muscleGroup: MuscleGroup) {
        val slots = _state.value.slots.toMutableList()
        val duration = when (muscleGroup) {
            MuscleGroup.WARMUP -> 3
            MuscleGroup.CARDIO -> 15
            else -> null
        }
        val insertAt = when (muscleGroup) {
            MuscleGroup.WARMUP -> slots.indexOfLast { it.muscleGroup == MuscleGroup.WARMUP }.let { if (it >= 0) it + 1 else 0 }
            MuscleGroup.CARDIO -> slots.size
            else -> {
                val firstCardio = slots.indexOfFirst { it.muscleGroup == MuscleGroup.CARDIO }
                if (firstCardio >= 0) firstCardio else slots.size
            }
        }
        // Slot vide — l'utilisateur choisira l'exercice dans le picker
        slots.add(insertAt, CustomExerciseSlot(
            muscleGroup = muscleGroup, selectedExercise = null,
            durationMinutes = duration
        ))
        _state.update { it.copy(slots = slots) }
        // Ouvrir immédiatement le picker pour ce slot
        openExercisePicker(insertAt)
    }

    /** Met à jour la durée (en minutes) pour un slot warmup ou cardio. */
    fun updateSlotDuration(index: Int, delta: Int) {
        val slots = _state.value.slots.toMutableList()
        if (index !in slots.indices) return
        val s = slots[index]
        val current = s.durationMinutes ?: when (s.muscleGroup) {
            MuscleGroup.WARMUP -> 3; MuscleGroup.CARDIO -> 15; else -> return
        }
        val newValue = (current + delta).coerceIn(1, 60)
        slots[index] = s.copy(durationMinutes = newValue)
        _state.update { it.copy(slots = slots) }
    }

    /** Ouvre le picker pour ajouter un slot dans une section donnée.
     *  Pour warmup et cardio (section à choix unique), ajoute directement sans dialog. */
    fun openAddSlotPicker(section: WorkoutSection) {
        when (section) {
            WorkoutSection.WARMUP -> addSlot(MuscleGroup.WARMUP)
            WorkoutSection.CARDIO -> addSlot(MuscleGroup.CARDIO)
            WorkoutSection.STRENGTH -> _state.update {
                it.copy(showAddSlotPicker = true, addSlotSection = section)
            }
        }
    }
    fun closeAddSlotPicker() {
        _state.update { it.copy(showAddSlotPicker = false, addSlotSection = null) }
    }

    fun saveAndStart() {
        val s = _state.value
        val exercises = s.slots.mapNotNull { it.selectedExercise }
        if (exercises.isEmpty()) return

        viewModelScope.launch {
            // Réutiliser le favori existant si déjà sauvé
            val workoutId = s.savedFavoriteId ?: run {
                val workout = WorkoutEntity(
                    name = s.name, durationMinutes = s.durationMinutes, exerciseCount = exercises.size,
                    isTemplate = true, isCustom = true, isFavorite = s.isFavorite,
                    routineId = s.routineId,
                )
                val newId = workoutRepository.insertWorkout(workout)
                // Sauver les overrides utilisateur (séries/reps/repos) dans WorkoutExerciseEntity
                val slotsWithExo = s.slots.filter { it.selectedExercise != null }
                workoutRepository.insertWorkoutExercises(slotsWithExo.mapIndexed { i, slot ->
                    val exo = slot.selectedExercise!!
                    WorkoutExerciseEntity(
                        workoutId = newId, exerciseId = exo.id, orderIndex = i,
                        customSeries = if (slot.series != exo.series) slot.series else null,
                        customRepsMin = if (slot.repsMin != exo.repsMin) slot.repsMin else null,
                        customRepsMax = if (slot.repsMax != exo.repsMax) slot.repsMax else null,
                        customRestSeconds = if (slot.restSeconds != exo.restSeconds) slot.restSeconds else null
                    )
                })
                newId
            }

            val now = LocalDateTime.now()
            val log = WorkoutLogEntity(
                workoutId = workoutId, date = now,
                startTime = now, durationMinutes = s.durationMinutes, completed = false,
                routineId = s.routineId,
            )
            val workoutLogId = workoutRepository.insertWorkoutLog(log)
            _state.update { it.copy(isSaved = true, savedWorkoutLogId = workoutLogId) }
        }
    }
}
