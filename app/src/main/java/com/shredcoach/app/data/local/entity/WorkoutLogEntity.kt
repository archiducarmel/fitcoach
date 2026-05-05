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
    indices = [Index("workoutId"), Index("date")]
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
)
