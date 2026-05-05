package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "workouts")
@Immutable
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMinutes: Int, // 60, 90, 120, 180
    val exerciseCount: Int,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isTemplate: Boolean = false,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false, // true si créé manuellement par l'utilisateur
    /**
     * Marque les "séances libres" (créées via Home → "Séance libre") où
     * l'utilisateur ajoute les exos au fur et à mesure. Différent de `isCustom`
     * (qui s'applique aussi aux workouts créés via CustomWorkoutScreen avec
     * une liste fixe d'exos). Utilisé par WorkoutSessionViewModel pour décider
     * si la "fin du dernier exo" propose la vue d'ensemble (freestyle) ou ferme
     * directement la séance (workout structuré).
     */
    val isFreestyle: Boolean = false
)
