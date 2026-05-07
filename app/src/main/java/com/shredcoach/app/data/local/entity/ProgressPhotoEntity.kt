package com.shredcoach.app.data.local.entity


import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shredcoach.app.R
import java.time.LocalDate

enum class PhotoType(
    val displayName: String,
    @StringRes val displayNameRes: Int,
) {
    FRONT("Face", R.string.photo_type_front),
    SIDE("Profil", R.string.photo_type_side),
    BACK("Dos", R.string.photo_type_back)
}

@Entity(tableName = "progress_photos", indices = [Index("date")])
@Immutable
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val photoType: PhotoType,
    val filePath: String, // Chemin absolu dans le stockage interne
    val weightAtTime: Double = 0.0 // Poids au moment de la photo
)
