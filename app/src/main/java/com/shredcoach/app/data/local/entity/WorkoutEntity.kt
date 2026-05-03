package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMinutes: Int, // 60, 90, 120, 180
    val exerciseCount: Int,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val isTemplate: Boolean = false,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false // true si créé manuellement par l'utilisateur
)
