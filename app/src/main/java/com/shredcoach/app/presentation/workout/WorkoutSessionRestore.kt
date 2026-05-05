package com.shredcoach.app.presentation.workout

import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity
import java.time.LocalDateTime

/**
 * Container pour les champs reconstruits depuis les sets persistés.
 * Permet d'appliquer la restauration en UN SEUL state update atomique
 * dans [WorkoutSessionViewModel.loadWorkout].
 */
internal data class RestoredProgress(
    val completedSets: List<WorkoutSetData>,
    val currentExerciseIndex: Int,
    val currentSeries: Int,
    val exerciseStartTimes: Map<Int, LocalDateTime>,
    val exerciseDurations: Map<Int, Long>
)

/**
 * Reconstitue l'état de progression UI à partir des [WorkoutSetEntity]
 * persistés. Utilisé quand on rouvre une séance déjà commencée (banner,
 * card "Reprendre" sur Home, cold-start après process death).
 *
 * **Pourquoi ce helper existe** : avant ce fix, `loadWorkout` resettait
 * brutalement `currentExerciseIndex=0, completedSets=[]` à chaque entrée
 * sur l'écran de session — peu importe que la DB contienne déjà N séries
 * loggées. L'utilisateur voyait alors sa séance "redémarrer" alors que les
 * sets étaient bien persistés. Cette fonction lit la DB et reconstruit
 * l'état UI cohérent avec les données déjà présentes.
 *
 * **Algorithme** :
 *  - `completedSets` : map 1-1 des entités vers [WorkoutSetData] (préserve
 *    skipped/reps/weight/durations) → métriques (volume, reps totales)
 *    identiques à l'historique DB.
 *  - `currentExerciseIndex` : 1er exo dont (sets faits, skipés inclus) <
 *    (séries prévues). Si tous remplis → dernier exo (state.isLastExercise
 *    = true → l'UI proposera de finaliser la séance).
 *  - `currentSeries` : (sets faits pour l'exo courant) + 1, capé à `series`
 *    pour ne pas dépasser le nombre prévu.
 *  - `exerciseDurations` : récupéré depuis les sets dont `exerciseDurationSeconds`
 *    est non-null (ce champ est rempli sur la dernière série d'un exo terminé).
 *  - `exerciseStartTimes` : best-effort — pas de wall-clock par exo en DB,
 *    on stamp `now()` pour l'exo courant. Les exos précédents n'en ont pas
 *    besoin (leurs durées viennent de `exerciseDurations`).
 *
 * **Usage** : appel depuis `loadWorkout` UNIQUEMENT si `existingSets` est
 * non-vide. Pour une séance fraîche (0 set), [WorkoutSessionViewModel] doit
 * conserver son comportement initial (state par défaut).
 */
internal fun rebuildProgressFromSets(
    exercises: List<ExerciseEntity>,
    sets: List<WorkoutSetEntity>,
    nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    extraSeriesMap: Map<Int, Int> = emptyMap()
): RestoredProgress {
    require(exercises.isNotEmpty()) { "rebuildProgressFromSets ne doit pas être appelé sur une liste d'exos vide" }

    val completedSets = sets.map { s ->
        WorkoutSetData(
            exerciseId = s.exerciseId,
            seriesNumber = s.setNumber,
            reps = s.reps,
            targetReps = s.targetReps,
            weight = s.weightKg,
            targetWeight = s.targetWeightKg,
            restSecondsActual = s.restSeconds,
            targetRestSeconds = s.targetRestSeconds,
            tempoUsed = s.tempoUsed,
            setDurationSeconds = s.setDurationSeconds,
            exerciseDurationSeconds = s.exerciseDurationSeconds,
            skipped = !s.completed
        )
    }

    // Index : 1er exo non-fini. Les skips comptent dans le total — un set
    // skippé occupe un slot, donc la prochaine série démarre au n+1.
    // **Bonus series** : on inclut les séries bonus (extraSeriesMap) dans le
    // total cible par exo. Sans ça, restaurer une séance où l'user avait
    // ajouté +1 série à l'exo 0 nous ferait croire qu'il est déjà passé à
    // l'exo suivant alors qu'il a juste fait sa série bonus.
    val countByExoId = sets.groupingBy { it.exerciseId }.eachCount()
    var currentIndex = -1
    for ((idx, exo) in exercises.withIndex()) {
        val target = exo.series + (extraSeriesMap[idx] ?: 0)
        if ((countByExoId[exo.id] ?: 0) < target) {
            currentIndex = idx
            break
        }
    }
    if (currentIndex < 0) currentIndex = exercises.size - 1
    val currentExo = exercises[currentIndex]
    val currentTarget = currentExo.series + (extraSeriesMap[currentIndex] ?: 0)
    val currentSeries = ((countByExoId[currentExo.id] ?: 0) + 1)
        .coerceAtMost(currentTarget.coerceAtLeast(1))

    // exerciseDurations : depuis les sets qui ont stampé exerciseDurationSeconds
    // (= dernière série d'un exo terminé). Mappé par exoId → idx dans la liste.
    val durationByExoId = sets
        .mapNotNull { s -> s.exerciseDurationSeconds?.let { s.exerciseId to it } }
        .toMap()
    val exerciseDurations = exercises.mapIndexedNotNull { idx, exo ->
        durationByExoId[exo.id]?.let { idx to it }
    }.toMap()

    // L'exo courant reçoit un startTime "maintenant" — le chrono d'exo
    // démarre au moment où l'écran s'affiche. Les exos précédents n'ont
    // pas besoin de startTime puisque leur durée est déjà figée.
    val exerciseStartTimes = mapOf(currentIndex to nowProvider())

    return RestoredProgress(
        completedSets = completedSets,
        currentExerciseIndex = currentIndex,
        currentSeries = currentSeries,
        exerciseStartTimes = exerciseStartTimes,
        exerciseDurations = exerciseDurations
    )
}
