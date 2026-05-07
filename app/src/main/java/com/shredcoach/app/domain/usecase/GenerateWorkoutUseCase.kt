package com.shredcoach.app.domain.usecase

import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.domain.workout.AbdoPolicy
import com.shredcoach.app.domain.workout.CardioPolicy
import com.shredcoach.app.domain.workout.RoutineCatalog
import com.shredcoach.app.domain.workout.WorkoutRoutine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class WorkoutConfig(
    val durationMinutes: Int, // 60, 90, 120, 180
    val fitnessLevel: FitnessLevel = FitnessLevel.INTERMEDIATE,
    val equipmentType: EquipmentType = EquipmentType.FULL_GYM,
    /**
     * Identifiant du [WorkoutRoutine] cible (Push, Pull, Full Body, …). Default
     * `"full_body"` préserve le comportement historique pour les call-sites
     * pré-v37 qui n'auraient pas encore été câblés. Résolu via [RoutineCatalog.byId]
     * — un id inconnu retombe sur Full Body sans crasher.
     */
    val routineId: String = "full_body",
)

data class GeneratedWorkout(
    val exercises: List<ExerciseEntity>, // Exercices de musculation principaux
    val warmupExercises: List<ExerciseEntity> = emptyList(), // Exercices d'échauffement
    val cardioExercises: List<ExerciseEntity> = emptyList(), // Exercices cardio
    val totalDuration: Int,
    val warmupMinutes: Int = 8,
    val cardioMinutes: Int = 0,
    val exerciseCount: Int, // Nombre d'exercices muscu uniquement
    /** Routine cible utilisée pour la génération (pour affichage UI / persistence). */
    val routineId: String = "full_body",
)

class GenerateWorkoutUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) {
    /**
     * Génère une séance routine-aware (Full Body, Push, Pull, Legs, …) basée
     * sur la configuration. Avant v37 cette méthode était hardcodée Full Body —
     * désormais le pipeline interroge [RoutineCatalog] pour piloter sélection
     * des groupes, échauffement, cardio et abdos.
     *
     * Targets exos muscu (par durée) :
     *  - 60 min  → 6 exos
     *  - 90 min  → 8 exos
     *  - 120 min → 10 exos
     *  - 180 min → 12 exos
     *
     * Pour les routines à faible nombre de groupes (ex: Push = 3 primary +
     * 2 accessory), [selectMuscleGroupsForRoutine] augmente progressivement
     * le volume sur les groupes primaires plutôt que d'inventer des groupes
     * étrangers à la routine — un Push reste un Push même à 120 min.
     *
     * Cardio : inclu / skip selon [WorkoutRoutine.cardioPolicy].
     * Abdos : inclus selon [WorkoutRoutine.abdoPolicy] (ex: Push n'a pas d'abdos
     * à 60 min, mais en a à 90+ min).
     */
    suspend fun execute(config: WorkoutConfig): GeneratedWorkout {
        val routine = RoutineCatalog.byId(config.routineId)
        val allExercises = exerciseRepository.getAllExercises().first()

        // Séparer les exercices de musculation des autres
        val muscuExercises = allExercises.filter {
            it.muscleGroup != MuscleGroup.WARMUP && it.muscleGroup != MuscleGroup.CARDIO
        }

        // Filtrer selon équipement disponible
        val availableExercises = filterByEquipment(muscuExercises, config.equipmentType)

        // Filtrer selon niveau
        val levelFilteredExercises = filterByLevel(availableExercises, config.fitnessLevel)

        // Sélectionner les groupes musculaires selon la routine + la durée
        val muscleGroups = selectMuscleGroupsForRoutine(routine, config.durationMinutes)

        // Sélectionner 1 exercice par occurrence de groupe (les doublons —
        // ex: CHEST listé 2x pour Push — donnent 2 exos pec différents).
        val selectedExercises = selectExercisesForMuscleGroups(
            exercises = levelFilteredExercises,
            muscleGroups = muscleGroups,
            equipmentType = config.equipmentType
        )

        // Sélectionner les exercices d'échauffement (cap proportionnel à la
        // durée + priorisation intelligente). Avant : on prenait TOUS les
        // warmups du catalogue (~18 après filtre équipement) — UX cassée pour
        // une séance de 90 min.
        val allWarmups = filterByEquipment(
            allExercises.filter { it.muscleGroup == MuscleGroup.WARMUP },
            config.equipmentType,
        )
        val warmupExercises = selectWarmupExercises(
            allWarmups = allWarmups,
            mainMuscleGroups = muscleGroups,
            durationMinutes = config.durationMinutes,
        )

        // Cardio : skip pour les splits push/pull/upper, inclus pour FB/Legs/Lower.
        val cardioExercises = if (routine.cardioPolicy == CardioPolicy.SKIP) {
            emptyList()
        } else {
            filterByEquipment(
                allExercises.filter { it.muscleGroup == MuscleGroup.CARDIO },
                config.equipmentType
            ).randomOrNull()?.let { listOf(it) } ?: emptyList()
        }

        // Calculer la durée du cardio (0 si SKIP)
        val cardioMinutes = if (routine.cardioPolicy == CardioPolicy.SKIP) 0
            else calculateCardioTime(config.durationMinutes)

        return GeneratedWorkout(
            exercises = selectedExercises,
            warmupExercises = warmupExercises,
            cardioExercises = cardioExercises,
            totalDuration = config.durationMinutes,
            // Estimation : 1 exo échauffement ≈ 1.5 min (30s exec + transitions
            // + pose). Plus aligné avec le nombre réel d'exos qu'un fixe à 8 min.
            warmupMinutes = (warmupExercises.size * 1.5).toInt().coerceAtLeast(3),
            cardioMinutes = cardioMinutes,
            exerciseCount = selectedExercises.size,
            routineId = routine.id,
        )
    }

    /**
     * Sélectionne un nombre raisonnable d'exercices d'échauffement, choisi
     * intelligemment plutôt que "tous" comme avant.
     *
     * **Cap par durée** (basé sur la pratique coach : 5-10% du temps total) :
     *  - 60 min → 3 exos (~5 min)
     *  - 90 min → 4 exos (~6 min)
     *  - 120 min → 5 exos (~8 min)
     *  - 180+ min → 6 exos (~9 min)
     *
     * **Stratégie de sélection** (cascade de priorités) :
     *  1. Une activation cardiovasculaire générale (cardio léger / rameur /
     *     vélo / tapis) si présente — réveille le système sans fatigue.
     *  2. Mobilité ciblée matchée par mots-clés sur les groupes musculaires
     *     du workout principal — un warmup "Étirement psoas" pour un jour
     *     QUADS, un "Étirement pectoraux" pour un jour CHEST, etc.
     *  3. Le reste tiré au shuffle pour varier d'une séance à l'autre.
     *
     * Si le catalogue contient moins d'exos que le cap (rare), on les prend
     * tous sans perdre de slots.
     */
    private fun selectWarmupExercises(
        allWarmups: List<ExerciseEntity>,
        mainMuscleGroups: List<MuscleGroup>,
        durationMinutes: Int,
    ): List<ExerciseEntity> {
        val maxCount = when {
            durationMinutes <= 60 -> 3
            durationMinutes <= 90 -> 4
            durationMinutes <= 120 -> 5
            else -> 6
        }
        if (allWarmups.size <= maxCount) return allWarmups

        val pool = allWarmups.toMutableList()
        val selected = mutableListOf<ExerciseEntity>()

        // 1. Activation cardiovasculaire générale — GARANTIE quand au moins un
        //    exo "activation/cardio" existe dans le pool (post-filtre équipement).
        //    Cascade :
        //    a. Match cardio "vrai" (machine ou bodyweight équivalent)
        //    b. Fallback : "Séries d'activation" / "Étirements dynamiques" /
        //       "Mobilisations" — pas du cardio strict mais ça monte le rythme
        //       cardiaque, mieux que rien
        //    c. Last resort : aucun match → on saute (extrêmement rare, et
        //       les slots suivants combleront)
        val cardioActivation = pool.firstOrNull { exo ->
            val n = exo.name.lowercase()
            n.contains("cardio") ||
                n.contains("rameur") ||
                n.contains("vélo") ||
                n.contains("tapis") ||
                n.contains("jumping") ||
                n.contains("montées de genoux") ||
                n.contains("course sur place")
        } ?: pool.firstOrNull { exo ->
            val n = exo.name.lowercase()
            n.contains("activation") ||
                n.contains("dynamique") ||
                n.contains("mobilisation")
        }
        cardioActivation?.let {
            selected += it
            pool -= it
        }

        // 2. Mobilité ciblée par mots-clés liés aux muscles du workout
        val keywords = buildWarmupKeywords(mainMuscleGroups)
        for (kw in keywords) {
            if (selected.size >= maxCount) break
            pool.firstOrNull { it.name.contains(kw, ignoreCase = true) }?.let {
                selected += it
                pool -= it
            }
        }

        // 3. Compléter par tirage aléatoire (variété inter-séances)
        val remaining = (maxCount - selected.size).coerceAtLeast(0)
        if (remaining > 0) selected += pool.shuffled().take(remaining)

        return selected
    }

    /**
     * Génère les mots-clés à matcher dans les noms de warmup en fonction des
     * muscles principaux de la séance. Pas de mapping rigide muscle↔warmup
     * (les warmups n'ont pas de muscle group secondaire en DB), juste des
     * keywords FR qui apparaissent dans les noms du seed (ex : "psoas",
     * "hanche", "pectoraux", "dos", "épaule").
     */
    private fun buildWarmupKeywords(groups: List<MuscleGroup>): List<String> {
        val keywords = mutableListOf<String>()
        val hasLegs = groups.any {
            it in listOf(
                MuscleGroup.QUADS, MuscleGroup.HAMSTRINGS,
                MuscleGroup.ADDUCTORS, MuscleGroup.CALVES,
            )
        }
        if (hasLegs) keywords += listOf("hanche", "psoas", "fente", "quadricep", "ischio")
        if (groups.any { it == MuscleGroup.CHEST || it == MuscleGroup.CHEST_UPPER }) {
            keywords += "pectoraux"
        }
        if (groups.any {
                it in listOf(
                    MuscleGroup.BACK_WIDTH, MuscleGroup.BACK_THICKNESS,
                    MuscleGroup.LOWER_BACK, MuscleGroup.TRAPS,
                )
            }) {
            keywords += listOf("dos", "colonne")
        }
        if (groups.contains(MuscleGroup.SHOULDERS)) {
            keywords += listOf("épaule", "rotation")
        }
        if (groups.any { it == MuscleGroup.ABS_UPPER || it == MuscleGroup.ABS_LOWER }) {
            keywords += "buste"
        }
        return keywords.distinct()
    }

    private fun filterByEquipment(
        exercises: List<ExerciseEntity>,
        equipmentType: EquipmentType
    ): List<ExerciseEntity> {
        return when (equipmentType) {
            EquipmentType.FULL_GYM -> exercises // Tout est disponible
            EquipmentType.HOME_GYM -> exercises.filter {
                it.variant == ExerciseVariant.WEIGHTS || it.variant == ExerciseVariant.BODYWEIGHT
            }
            EquipmentType.BODYWEIGHT -> exercises.filter {
                it.variant == ExerciseVariant.BODYWEIGHT
            }
        }
    }

    private fun filterByLevel(
        exercises: List<ExerciseEntity>,
        level: FitnessLevel
    ): List<ExerciseEntity> {
        return when (level) {
            FitnessLevel.BEGINNER -> exercises.filter { it.difficulty <= 2 }
            FitnessLevel.INTERMEDIATE -> exercises.filter { it.difficulty <= 3 }
            FitnessLevel.ADVANCED -> exercises // Tous les exercices
        }
    }

    /**
     * Sélectionne la liste de groupes musculaires (en respectant les doublons
     * pour `volumeDistribution`) pour la routine et la durée donnés.
     *
     * Algorithme :
     *  1. Calcule un target d'exos en fonction de la durée (6 / 8 / 10 / 12).
     *  2. Ajoute les `primaryGroups` répétés selon `volumeDistribution`.
     *  3. Ajoute les `accessoryGroups` (filtrés par `abdoPolicy`) jusqu'à
     *     atteindre le target.
     *  4. Si target non atteint (cas typique : Push à 90+ min, peu de groupes
     *     dispo), bump le volume sur les primary en round-robin pour rester
     *     dans la philosophie de la routine — un Push reste un Push.
     */
    internal fun selectMuscleGroupsForRoutine(
        routine: WorkoutRoutine,
        durationMinutes: Int,
    ): List<MuscleGroup> {
        val target = when {
            durationMinutes <= 60 -> 6
            durationMinutes <= 90 -> 8
            durationMinutes <= 120 -> 10
            else -> 12
        }
        val isLongSession = durationMinutes >= 90

        fun isAllowed(group: MuscleGroup): Boolean {
            val isAbs = group == MuscleGroup.ABS_UPPER || group == MuscleGroup.ABS_LOWER
            if (!isAbs) return true
            return when (routine.abdoPolicy) {
                AbdoPolicy.ALWAYS -> true
                AbdoPolicy.NEVER -> false
                AbdoPolicy.LONG_SESSIONS_ONLY -> isLongSession
            }
        }

        fun volumeOf(group: MuscleGroup): Int =
            (routine.volumeDistribution[group] ?: 1).coerceAtLeast(1)

        val result = mutableListOf<MuscleGroup>()

        // 1. Primary groups (avec volume distribution)
        for (group in routine.primaryGroups) {
            if (!isAllowed(group)) continue
            repeat(volumeOf(group)) {
                if (result.size < target) result += group
            }
            if (result.size >= target) return result
        }

        // 2. Accessory groups
        for (group in routine.accessoryGroups) {
            if (!isAllowed(group)) continue
            repeat(volumeOf(group)) {
                if (result.size < target) result += group
            }
            if (result.size >= target) return result
        }

        // 3. Fallback : bump volume sur primary en round-robin si encore court.
        //    Préserve l'identité de la routine (Push reste Push même à 120 min).
        val primaryPool = routine.primaryGroups.filter { isAllowed(it) }
        if (primaryPool.isNotEmpty()) {
            var i = 0
            val safetyCap = target * 2
            while (result.size < target && i < safetyCap) {
                result += primaryPool[i % primaryPool.size]
                i++
            }
        }

        return result
    }

    private fun selectExercisesForMuscleGroups(
        exercises: List<ExerciseEntity>,
        muscleGroups: List<MuscleGroup>,
        equipmentType: EquipmentType
    ): List<ExerciseEntity> {
        // `muscleGroups` peut contenir des doublons (volumeDistribution = 2 pour
        // CHEST sur Push). On consomme le pool d'exos sans répétition pour ne
        // pas placer 2x le même développé couché.
        val selectedExercises = mutableListOf<ExerciseEntity>()
        val usedIds = mutableSetOf<Long>()

        for (muscleGroup in muscleGroups) {
            val groupExercises = exercises.filter {
                it.muscleGroup == muscleGroup && it.id !in usedIds
            }

            if (groupExercises.isEmpty()) continue

            // Stratégie de sélection : variante préférée + randomisation
            val selectedExercise = when (equipmentType) {
                EquipmentType.FULL_GYM -> {
                    if (muscleGroup in listOf(MuscleGroup.QUADS, MuscleGroup.CHEST, MuscleGroup.BACK_WIDTH)) {
                        groupExercises.filter { it.variant == ExerciseVariant.MACHINE }.randomOrNull()
                            ?: groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                            ?: groupExercises.random()
                    } else {
                        groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                            ?: groupExercises.filter { it.variant == ExerciseVariant.ISOLATION }.randomOrNull()
                            ?: groupExercises.random()
                    }
                }
                EquipmentType.HOME_GYM -> {
                    groupExercises.filter { it.variant == ExerciseVariant.WEIGHTS }.randomOrNull()
                        ?: groupExercises.random()
                }
                EquipmentType.BODYWEIGHT -> {
                    groupExercises.random()
                }
            }

            selectedExercises.add(selectedExercise)
            usedIds += selectedExercise.id
        }

        return selectedExercises
    }

    private fun calculateCardioTime(totalDuration: Int): Int {
        return when (totalDuration) {
            60 -> 12
            90 -> 25
            120 -> 30
            180 -> 40
            else -> 20
        }
    }

    /**
     * Calcule le temps estimé pour les exercices
     */
    fun calculateExerciseTime(exercises: List<ExerciseEntity>): Int {
        return exercises.sumOf { exercise ->
            val setTime = 30 // Temps moyen par série (30 secondes)
            val restTime = exercise.restSeconds
            val totalTime = (setTime + restTime) * exercise.series
            totalTime / 60 // Convertir en minutes
        }
    }
}
