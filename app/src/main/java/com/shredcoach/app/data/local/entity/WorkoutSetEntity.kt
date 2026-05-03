package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutLogId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutLogId"), Index("exerciseId")]
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutLogId: Long,
    val exerciseId: Long,
    val setNumber: Int, // Numéro de série (1, 2, 3, 4)
    val reps: Int, // Répétitions effectuées
    val targetReps: Int = 0, // Répétitions prévues
    val weightKg: Double, // Poids utilisé en kg (0 si poids du corps)
    val targetWeightKg: Double = 0.0, // Poids prévu
    val restSeconds: Int? = null, // Temps de repos RÉEL mesuré
    val targetRestSeconds: Int = 0, // Temps de repos prévu
    val tempoUsed: String? = null, // Tempo utilisé (ex: "3-0-1-0")
    val setDurationSeconds: Int? = null, // Durée réelle de la série
    val exerciseDurationSeconds: Long? = null, // Durée totale de l'exercice (rempli sur dernière série)
    val completed: Boolean = true
)
