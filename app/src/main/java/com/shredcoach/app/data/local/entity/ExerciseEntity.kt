package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val variant: ExerciseVariant,
    val equipment: String,
    val executionKey: String, // Conseils d'exécution
    val startingWeight: String, // Ex: "60-80 kg", "Poids du corps", "Barre vide 20 kg"
    val series: Int, // Nombre de séries
    val repsMin: Int, // Minimum de répétitions
    val repsMax: Int, // Maximum de répétitions
    val restSeconds: Int, // Temps de repos en secondes
    val tips: String, // Astuces et notes supplémentaires
    val tempo: String = "3-0-1-0", // Format: excentrique-pause basse-concentrique-pause haute (en secondes)
    val gifUrl: String? = null, // URL ou nom du fichier GIF dans assets
    val difficulty: Int = 1, // 1=Débutant, 2=Intermédiaire, 3=Avancé
    val isTimeBased: Boolean = false // true = durée au lieu de reps (gainage, mountain climber...)
)
