package com.shredcoach.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class PhotoType(val displayName: String) {
    FRONT("Face"),
    SIDE("Profil"),
    BACK("Dos")
}

@Entity(tableName = "progress_photos", indices = [Index("date")])
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val photoType: PhotoType,
    val filePath: String, // Chemin absolu dans le stockage interne
    val weightAtTime: Double = 0.0 // Poids au moment de la photo
)
