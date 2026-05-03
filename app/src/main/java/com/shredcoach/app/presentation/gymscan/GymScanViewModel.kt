package com.shredcoach.app.presentation.gymscan

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.remote.ExerciseDbExercise
import com.shredcoach.app.data.remote.ExerciseDbService
import com.shredcoach.app.data.remote.GymScanMatcher
import com.shredcoach.app.data.remote.GymScanResult
import com.shredcoach.app.data.remote.GymScanService
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class GymScanState(
    val imageBitmap: Bitmap? = null,
    val isLoadingDataset: Boolean = false,
    val isAnalyzing: Boolean = false,
    val llmResult: GymScanResult? = null,
    val matchedExercises: List<ExerciseDbExercise> = emptyList(),
    val error: String? = null,
    val isConfigured: Boolean = false
)

@HiltViewModel
class GymScanViewModel @Inject constructor(
    private val gymScanService: GymScanService,
    private val exerciseDbService: ExerciseDbService,
    private val gymScanMatcher: GymScanMatcher, // Fallback si sélection LLM échoue
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GymScanState())
    val state: StateFlow<GymScanState> = _state.asStateFlow()

    companion object { private const val TAG = "GymScan-VM" }

    init {
        Log.i(TAG, "★ ViewModel créé")
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val hasKey = when (profile?.mealScanProvider) {
                "GROQ" -> !profile.groqMealApiKey.isNullOrBlank()
                "MISTRAL" -> !profile.mistralApiKey.isNullOrBlank()
                else -> !profile?.geminiApiKey.isNullOrBlank()
            }
            _state.update { it.copy(isConfigured = hasKey) }
            Log.d(TAG, "isConfigured=$hasKey")
        }
    }

    fun setImage(bitmap: Bitmap) {
        Log.i(TAG, "setImage : ${bitmap.width}×${bitmap.height}")
        _state.update {
            it.copy(
                imageBitmap = bitmap,
                llmResult = null,
                matchedExercises = emptyList(),
                error = null
            )
        }
    }

    fun analyze() {
        val bitmap = _state.value.imageBitmap ?: return
        _state.update { it.copy(isLoadingDataset = true, error = null, llmResult = null, matchedExercises = emptyList()) }

        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val provider = profile?.mealScanProvider ?: "GEMINI"
            val apiKey = when (provider) {
                "GROQ" -> profile?.groqMealApiKey ?: ""
                "MISTRAL" -> profile?.mistralApiKey ?: ""
                else -> profile?.geminiApiKey ?: ""
            }
            val model = profile?.geminiModel ?: "gemini-2.5-flash"

            if (apiKey.isBlank()) {
                val providerName = when (provider) { "GROQ" -> "Groq"; "MISTRAL" -> "Mistral"; else -> "Gemini" }
                _state.update {
                    it.copy(isLoadingDataset = false, isAnalyzing = false,
                        error = "Configure ta clé API $providerName dans Réglages → Meal Scanner")
                }
                return@launch
            }

            // 1. S'assurer que le dataset est chargé (cache mémoire partagé avec Découvrir)
            val datasetRes = exerciseDbService.getAllExercises()
            val dataset = datasetRes.getOrElse { err ->
                Log.e(TAG, "Dataset load échoué : ${err.message}", err)
                _state.update {
                    it.copy(isLoadingDataset = false, isAnalyzing = false,
                        error = "Impossible de charger le catalogue d'exercices : ${err.message}")
                }
                return@launch
            }
            Log.i(TAG, "Dataset prêt : ${dataset.size} exos, on lance le LLM vision…")

            // 2. Compression JPEG
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()

            _state.update { it.copy(isLoadingDataset = false, isAnalyzing = true) }

            // 3. UN SEUL appel LLM : vision + sélection d'IDs dans le dataset injecté
            val llmRes = gymScanService.analyzeMachine(
                imageBytes = bytes,
                mimeType = "image/jpeg",
                apiKey = apiKey,
                model = model,
                provider = provider,
                dataset = dataset
            )
            llmRes.fold(
                onSuccess = { result ->
                    Log.i(TAG, "✓ LLM all-in-one : ${result.machineName} (${result.confidence}%) · ${result.selectedExerciseIds.size} IDs")

                    // Map IDs → ExerciseDbExercise objets
                    val idMap = dataset.associateBy { it.id }
                    val selected = result.selectedExerciseIds.mapNotNull { idMap[it] }

                    // Fallback heuristique si le LLM n'a rien sélectionné
                    val finalList = if (selected.isNotEmpty()) {
                        selected
                    } else {
                        Log.w(TAG, "LLM n'a sélectionné aucun ID valide → fallback matcher heuristique")
                        gymScanMatcher.findMatches(result, topN = 6).getOrElse { emptyList() }
                    }

                    _state.update {
                        it.copy(
                            llmResult = result,
                            matchedExercises = finalList,
                            isAnalyzing = false
                        )
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "✗ LLM échoué : ${err.message}", err)
                    _state.update {
                        it.copy(isAnalyzing = false, error = err.message ?: "Erreur d'analyse")
                    }
                }
            )
        }
    }

    fun clear() {
        Log.i(TAG, "clear")
        _state.update {
            it.copy(
                imageBitmap = null,
                llmResult = null,
                matchedExercises = emptyList(),
                error = null,
                isLoadingDataset = false,
                isAnalyzing = false
            )
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
