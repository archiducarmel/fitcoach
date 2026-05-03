package com.shredcoach.app.data.local.entity

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
    val completed: Boolean = true
)
