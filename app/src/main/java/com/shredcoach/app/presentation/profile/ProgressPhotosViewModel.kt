package com.shredcoach.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.PhotoType
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class ProgressPhotosState(
    val photos: List<ProgressPhotoEntity> = emptyList(),
    val viewingPhoto: ProgressPhotoEntity? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ProgressPhotosViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressPhotosState())
    val state: StateFlow<ProgressPhotosState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getAllPhotos().collect { photos ->
                _state.update { it.copy(photos = photos, isLoading = false) }
            }
        }
    }

    fun onPhotoCaptured(filePath: String, type: PhotoType) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val weight = profile?.currentWeightKg ?: 0.0
            userRepository.insertPhoto(
                ProgressPhotoEntity(
                    date = LocalDate.now(),
                    photoType = type,
                    filePath = filePath,
                    weightAtTime = weight
                )
            )
        }
    }

    fun viewPhoto(photo: ProgressPhotoEntity) { _state.update { it.copy(viewingPhoto = photo) } }
    fun closeViewer() { _state.update { it.copy(viewingPhoto = null) } }

    fun deletePhoto(photo: ProgressPhotoEntity) {
        viewModelScope.launch {
            // Supprimer le fichier
            try { File(photo.filePath).delete() } catch (_: Exception) {}
            userRepository.deletePhoto(photo)
        }
    }
}
