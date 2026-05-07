package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "workout_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("workoutId"), Index("date"), Index("routineId")]
)
@Immutable
data class WorkoutLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long?,
    val date: LocalDateTime = LocalDateTime.now(),
    val durationMinutes: Int, // Durée prévue
    val actualDurationSeconds: Long = 0, // Durée RÉELLE mesurée par le chrono global
    val startTime: LocalDateTime = LocalDateTime.now(), // Heure de début précise
    val endTime: LocalDateTime? = null, // Heure de fin précise
    val totalVolume: Double = 0.0, // Somme (poids × reps)
    val totalSets: Int = 0, // Nombre total de séries effectuées
    val totalReps: Int = 0, // Nombre total de répétitions effectuées
    val totalRestSeconds: Long = 0, // Temps total de repos cumulé
    val exercisesCompleted: Int = 0, // Nombre d'exercices terminés (vs skippés)
    val exercisesSkipped: Int = 0, // Nombre d'exercices skippés
    val notes: String? = null,
    val completed: Boolean = true,
    // ─── Reprise robuste cross-process (v35) ─────────────────────────────
    // Champs mis à jour pendant la séance par ActiveSessionManager. Permettent
    // de restaurer fidèlement l'état UI (chrono d'exo + état "série en cours")
    // après un cold-start ou un retour d'arrière-plan long. Tous null/0 quand
    // la séance n'est pas active (séance terminée OU jamais démarrée).
    /** Wall-clock du début de l'exo courant. Re-stampé à chaque transition. */
    val currentExerciseStartedAt: LocalDateTime? = null,
    /** Wall-clock du `Démarrer la série`. Null = aucune série en cours. */
    val currentSetStartedAt: LocalDateTime? = null,
    /** Pour les exos chronométrés (gainage…), durée cible. 0 = pas timed. */
    val currentSetTimedTotalSeconds: Int = 0,
    // ─── Reprise robuste — v36 ───────────────────────────────────────────
    /**
     * Wall-clock cible de fin du repos en cours. Null = pas de repos actif.
     * `restRemaining = max(0, endsAt - now)`, `restElapsed = totalSec - remaining`.
     * Permet au décompte de continuer correctement après navigation/cold-start.
     */
    val currentRestEndsAt: LocalDateTime? = null,
    /** Durée totale du repos en cours (pour calculer elapsed). 0 = pas de repos. */
    val currentRestTotalSeconds: Int = 0,
    /**
     * JSON du Map<exerciseIndex, extraSeriesCount> pour les séries bonus
     * ajoutées à la volée pendant la séance. Format : `{"0":1,"2":2}` =
     * exo 0 a +1 série, exo 2 a +2 séries. Vide = `{}`. Persisté pour ne
     * pas perdre le slot bonus au retour sur l'écran.
     */
    val extraSeriesJson: String = "{}",
    // ─── v37 : type de séance (Push, Pull, Full Body, …) ───────────────────
    /**
     * Identifiant du [com.shredcoach.app.domain.workout.WorkoutRoutine] de la
     * séance — capturé depuis [WorkoutEntity.routineId] au moment du démarrage.
     * Default `"full_body"` pour les logs pré-v37 (backfill migration).
     * Indexé pour les requêtes de stats par routine type.
     */
    val routineId: String = "full_body",
)
