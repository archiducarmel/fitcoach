package com.shredcoach.app.presentation.bodyscanner

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.BodyAnalysisResult
import com.shredcoach.app.data.remote.BodyAnalysisService
import com.shredcoach.app.data.remote.BodyMeshService
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * State du Body Scanner.
 *
 * Phases visuelles :
 *  1. imageBitmap == null → empty state (zone capture)
 *  2. isAnalyzing == true → scan overlay animé
 *  3. result != null → affichage résultat éditable
 *  4. meshBitmap != null → cta vers BodyMeshScreen
 */
data class BodyScannerState(
    val imageBitmap: Bitmap? = null,
    val isAnalyzing: Boolean = false,
    val result: BodyAnalysisResult? = null,
    val error: String? = null,
    val isConfigured: Boolean = false, // Au moins une clé API dispo
    // Valeurs éditables par l'utilisateur (init depuis result)
    val editHeightCm: String = "",
    val editWeightKg: String = "",
    val editSex: String = "M",
    val editWaistCm: String = "",
    val editChestCm: String = "",
    val editHipCm: String = "",
    val editArmCm: String = "",
    val editThighCm: String = "",
    val editCalfCm: String = "",
    val editBodyFatPercent: String = "",
    val applied: Boolean = false, // true quand les mesures ont été sauvegardées au profil
    // Mesh generation
    val isGeneratingMesh: Boolean = false,
    val meshImagePath: String? = null,
    val meshError: String? = null,
    // Photos
    val originalImagePath: String? = null,
    val bodyScanTimestamp: LocalDateTime? = null
) {
    /** BMI calculé à la volée depuis les valeurs d'édition. */
    val computedBmi: Double
        get() {
            val h = editHeightCm.toDoubleOrNull() ?: 0.0
            val w = editWeightKg.toDoubleOrNull() ?: 0.0
            if (h <= 0 || w <= 0) return 0.0
            val hm = h / 100.0
            return w / (hm * hm)
        }

    val bmiLabel: String
        get() {
            val b = computedBmi
            return when {
                b == 0.0 -> ""
                b < 18.5 -> "Maigreur"
                b < 25.0 -> "Normal"
                b < 30.0 -> "Surpoids"
                else -> "Obésité"
            }
        }
}

@HiltViewModel
class BodyScannerViewModel @Inject constructor(
    private val bodyAnalysisService: BodyAnalysisService,
    private val bodyMeshService: BodyMeshService,
    private val userRepository: UserRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(BodyScannerState())
    val state: StateFlow<BodyScannerState> = _state.asStateFlow()

    init {
        // Charger les valeurs existantes du profil pour pré-remplir les champs
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val hasKey = userRepository.hasApiKey(SecureKeyStore.Provider.GEMINI)
                || userRepository.hasApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                || userRepository.hasApiKey(SecureKeyStore.Provider.MISTRAL)
            _state.update {
                it.copy(
                    isConfigured = hasKey,
                    editHeightCm = profile?.heightCm?.takeIf { h -> h > 0 }?.toString() ?: "",
                    editWeightKg = profile?.currentWeightKg?.takeIf { w -> w > 0 }?.toString() ?: "",
                    editSex = profile?.sex ?: "M",
                    editWaistCm = profile?.waistCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editChestCm = profile?.chestCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editHipCm = profile?.hipCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editArmCm = profile?.armCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editThighCm = profile?.thighCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editCalfCm = profile?.calfCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editBodyFatPercent = profile?.bodyFatPercent?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    meshImagePath = profile?.bodyMeshImagePath,
                    originalImagePath = profile?.bodyScanImagePath,
                    bodyScanTimestamp = profile?.bodyScanTimestamp
                )
            }
        }
    }

    fun setImage(bitmap: Bitmap) {
        _state.update { it.copy(imageBitmap = bitmap, result = null, error = null, applied = false) }
    }

    fun clear() {
        _state.update { it.copy(imageBitmap = null, result = null, error = null, applied = false) }
    }

    fun analyze() {
        val bitmap = _state.value.imageBitmap ?: return
        _state.update { it.copy(isAnalyzing = true, error = null, result = null) }

        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            // On réutilise le provider du Meal Scanner (même pipeline multi-provider vision)
            val provider = profile?.mealScanProvider ?: "GEMINI"
            val apiKey = when (provider) {
                "GROQ" -> userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                "MISTRAL" -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
                else -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
            }
            val model = profile?.geminiModel ?: "gemini-2.5-flash"

            if (apiKey.isBlank()) {
                val providerName = when (provider) { "GROQ" -> "Groq"; "MISTRAL" -> "Mistral"; else -> "Gemini" }
                _state.update { it.copy(isAnalyzing = false, error = "Configure ta clé API $providerName dans Réglages → Meal Scanner") }
                return@launch
            }

            // Sauvegarder la photo originale
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val originalPath = saveImageToFile(stream.toByteArray(), "original")

            val result = bodyAnalysisService.analyzeBody(
                stream.toByteArray(), "image/jpeg", apiKey, model, provider
            )

            result.fold(
                onSuccess = { analysis ->
                    val now = LocalDateTime.now()
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            result = analysis,
                            originalImagePath = originalPath,
                            bodyScanTimestamp = now,
                            // Auto-remplir les champs éditables avec les valeurs IA
                            editSex = analysis.sex.takeIf { s -> s.isNotBlank() } ?: it.editSex,
                            editHeightCm = analysis.heightCm.takeIf { h -> h > 0 }?.toString() ?: it.editHeightCm,
                            editWeightKg = analysis.weightEstimateKg.takeIf { w -> w > 0 }?.toString() ?: it.editWeightKg,
                            editWaistCm = analysis.waistCm.takeIf { v -> v > 0 }?.toString() ?: it.editWaistCm,
                            editChestCm = analysis.chestCm.takeIf { v -> v > 0 }?.toString() ?: it.editChestCm,
                            editHipCm = analysis.hipCm.takeIf { v -> v > 0 }?.toString() ?: it.editHipCm,
                            editArmCm = analysis.armCm.takeIf { v -> v > 0 }?.toString() ?: it.editArmCm,
                            editThighCm = analysis.thighCm.takeIf { v -> v > 0 }?.toString() ?: it.editThighCm,
                            editCalfCm = analysis.calfCm.takeIf { v -> v > 0 }?.toString() ?: it.editCalfCm,
                            editBodyFatPercent = analysis.bodyFatPercent.takeIf { v -> v > 0 }?.toString() ?: it.editBodyFatPercent
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isAnalyzing = false, error = error.message ?: "Erreur d'analyse") }
                }
            )
        }
    }

    // ── Setters pour l'édition manuelle ──
    fun setSex(v: String) = _state.update { it.copy(editSex = v) }
    fun setHeight(v: String) = _state.update { it.copy(editHeightCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setWeight(v: String) = _state.update { it.copy(editWeightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) }
    fun setWaist(v: String) = _state.update { it.copy(editWaistCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setChest(v: String) = _state.update { it.copy(editChestCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setHip(v: String) = _state.update { it.copy(editHipCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setArm(v: String) = _state.update { it.copy(editArmCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setThigh(v: String) = _state.update { it.copy(editThighCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setCalf(v: String) = _state.update { it.copy(editCalfCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setBodyFat(v: String) = _state.update { it.copy(editBodyFatPercent = v.filter { c -> c.isDigit() }.take(3)) }

    /** Sauvegarde les mesures au profil utilisateur. */
    fun applyToProfile() {
        viewModelScope.launch {
            val s = _state.value
            val current = userRepository.getUserProfileOnce() ?: return@launch
            // Normaliser les entrées (accepter virgule comme séparateur décimal)
            fun String.toDouble2(): Double? = replace(',', '.').toDoubleOrNull()
            fun String.toIntClean(): Int? = replace(',', '.').toDoubleOrNull()?.toInt()
            val updated = current.copy(
                sex = s.editSex,
                heightCm = s.editHeightCm.toIntClean() ?: current.heightCm,
                currentWeightKg = s.editWeightKg.toDouble2() ?: current.currentWeightKg,
                waistCm = s.editWaistCm.toDouble2() ?: current.waistCm,
                chestCm = s.editChestCm.toDouble2() ?: current.chestCm,
                hipCm = s.editHipCm.toDouble2() ?: current.hipCm,
                armCm = s.editArmCm.toDouble2() ?: current.armCm,
                thighCm = s.editThighCm.toDouble2() ?: current.thighCm,
                calfCm = s.editCalfCm.toDouble2() ?: current.calfCm,
                bodyFatPercent = s.editBodyFatPercent.toDouble2() ?: current.bodyFatPercent,
                bodyScanImagePath = s.originalImagePath ?: current.bodyScanImagePath,
                bodyScanTimestamp = s.bodyScanTimestamp ?: current.bodyScanTimestamp,
                bodyScanConfidence = s.result?.confidence ?: current.bodyScanConfidence,
                bodyScanNotes = s.result?.notes ?: current.bodyScanNotes
            )
            userRepository.updateUserProfile(updated)
            _state.update { it.copy(applied = true) }
        }
    }

    /** Génère l'image mesh futuriste via Gemini Image Generation. */
    fun generateMesh() {
        val bitmap = _state.value.imageBitmap
        if (bitmap == null) {
            _state.update { it.copy(meshError = "Recharge ta photo pour générer un nouveau mesh") }
            return
        }
        _state.update { it.copy(isGeneratingMesh = true, meshError = null) }

        viewModelScope.launch {
            val apiKey = userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
            if (apiKey.isBlank()) {
                _state.update { it.copy(isGeneratingMesh = false, meshError = "Clé API Gemini requise pour générer le mesh") }
                return@launch
            }

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)

            val result = bodyMeshService.generateMesh(stream.toByteArray(), "image/jpeg", apiKey)

            result.fold(
                onSuccess = { pngBytes ->
                    // Supprimer l'ancien mesh file pour éviter les orphelins sur regénération
                    val oldMesh = _state.value.meshImagePath
                    if (!oldMesh.isNullOrBlank()) {
                        try { java.io.File(oldMesh).delete() } catch (_: Exception) {}
                    }
                    val path = saveImageToFile(pngBytes, "mesh", "png")
                    // Sauver le chemin dans le profil
                    val current = userRepository.getUserProfileOnce()
                    if (current != null) {
                        userRepository.updateUserProfile(current.copy(bodyMeshImagePath = path))
                    }
                    _state.update { it.copy(isGeneratingMesh = false, meshImagePath = path, meshError = null) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isGeneratingMesh = false, meshError = error.message ?: "Erreur génération mesh") }
                }
            )
        }
    }

    /** Sauvegarde l'image dans `filesDir/body_scans/`. */
    private fun saveImageToFile(bytes: ByteArray, prefix: String, ext: String = "jpg"): String? {
        return try {
            val dir = java.io.File(appContext.filesDir, "body_scans")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Exception) { null }
    }
}
