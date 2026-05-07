package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Séance PLANIFIÉE dans le calendrier (distincte de `WorkoutLogEntity` = séances réalisées).
 *
 * Cycle de vie :
 *  1. `PLANNED`   — créée par l'user ou suggérée par IA
 *  2. `COMPLETED` — une `WorkoutLogEntity` a été créée et terminée à partir de cette planif
 *  3. `SKIPPED`   — l'user a indiqué "non fait"
 *  4. `CANCELED`  — annulée avant l'heure (ex: réunion qui tombe)
 *
 * Liée à `WorkoutEntity` optionnellement pour utiliser un template (favori, généré, custom).
 * Si `workoutId == null` → séance freestyle à choisir au moment de lancer.
 */
@Entity(
    tableName = "scheduled_workouts",
    indices = [Index("date"), Index("status"), Index("routineId")]
)
@Immutable
data class ScheduledWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val time: LocalTime? = null, // Heure optionnelle (null = toute la journée)
    val workoutId: Long? = null, // FK vers WorkoutEntity (template)
    val workoutLogId: Long? = null, // Rempli quand la séance est lancée
    val status: String = "PLANNED", // PLANNED, COMPLETED, SKIPPED, CANCELED
    val title: String = "", // Ex: "Full Body 90min" ou note libre
    val note: String = "",
    val reminderShakerSent: Boolean = false,  // 2h avant — notif shaker
    val reminderStartSent: Boolean = false,   // 30min avant — notif "c'est l'heure"
    val source: String = "manual", // manual | ai_suggestion | auto_recurring
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /**
     * Identifiant du [com.shredcoach.app.domain.workout.WorkoutRoutine] prévu
     * pour cette planif. Default `"full_body"` (pré-v37). Permet au calendrier
     * d'afficher "Pull mercredi" et au coach de raisonner sur la programmation
     * hebdo par split.
     */
    val routineId: String = "full_body",
)

enum class ScheduleStatus { PLANNED, COMPLETED, SKIPPED, CANCELED }
