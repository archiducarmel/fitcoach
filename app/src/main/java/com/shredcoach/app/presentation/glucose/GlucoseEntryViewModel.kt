package com.shredcoach.app.presentation.glucose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.presentation.common.IncomingShareIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class GlucoseEntryState(
    val date: LocalDate = LocalDate.now(),
    val previewBitmap: Bitmap? = null,
    val isUploading: Boolean = false,
    val log: GlucoseLogEntity? = null,
    val error: String? = null,
    val showManualOverride: Boolean = false,
    // Champs du form de correction manuelle
    val manualAvg: String = "",
    val manualPeak: String = "",
    val manualTir: String = "",
    val manualHypoCount: String = "",
)

@HiltViewModel
class GlucoseEntryViewModel @Inject constructor(
    private val glucoseRepository: GlucoseRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Date cible lue depuis le query arg "date" de la nav route. Permet à
     * l'écran d'agir sur une date arbitraire (J-1, J-2…), pas seulement today.
     * **Fix critique v45.1** : sans ce param, tous les uploads retombaient
     * sur LocalDate.now() → overwrite silent du log précédent du day.
     */
    private val initialDate: LocalDate = savedStateHandle.get<String>("date")
        ?.takeIf { it.isNotBlank() && it != "{date}" }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()

    private val _state = MutableStateFlow(GlucoseEntryState(date = initialDate))
    val state: StateFlow<GlucoseEntryState> = _state.asStateFlow()

    init {
        // Charge le log existant pour la date cible (peut être today OU J-N).
        viewModelScope.launch {
            val existing = glucoseRepository.getForDate(initialDate)
            _state.update { it.copy(log = existing) }
        }

        // Si l'écran est ouvert via un share intent (Partage → ShredCoach
        // Glycémie), MainActivity a déposé l'Uri dans IncomingShareIntent.
        // On la consomme dès que ce VM se réveille, et on déclenche la
        // preview comme un picker galerie classique.
        viewModelScope.launch {
            IncomingShareIntent.pending
                .filterNotNull()
                .collect { pending ->
                    if (pending.target == IncomingShareIntent.Target.GLUCOSE) {
                        onImageSelected(pending.uri)
                        IncomingShareIntent.consume()
                    }
                }
        }
    }

    /** Change la date cible (utile pour uploader un screenshot J-1 oublié). */
    fun setDate(date: LocalDate) {
        viewModelScope.launch {
            val existing = glucoseRepository.getForDate(date)
            _state.update { it.copy(date = date, log = existing, previewBitmap = null, error = null) }
        }
    }

    /** Annule la preview en cours (l'user clique sur "Annuler"). */
    fun cancelPreview() {
        _state.update { it.copy(previewBitmap = null, error = null) }
    }

    /** Charge l'image sélectionnée par l'user (galerie) en preview avant upload. */
    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val bmp = appContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@launch
                _state.update { it.copy(previewBitmap = bmp, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Image illisible : ${e.message?.take(80)}") }
            }
        }
    }

    /** Lance l'OCR sur le bitmap en preview et insère l'entrée en DB. */
    fun analyzeAndSave() {
        val bmp = _state.value.previewBitmap ?: return
        val date = _state.value.date
        _state.update { it.copy(isUploading = true, error = null) }
        viewModelScope.launch {
            val result = glucoseRepository.uploadScreenshot(bmp, date)
            result.fold(
                onSuccess = { entity ->
                    _state.update { it.copy(isUploading = false, log = entity, previewBitmap = null) }
                },
                onFailure = { e ->
                    // Le repo a déjà persisté un placeholder (avec imagePath seul).
                    // On recharge le log pour montrer ce qui a été sauvé.
                    val reloaded = glucoseRepository.getForDate(date)
                    _state.update {
                        it.copy(
                            isUploading = false,
                            log = reloaded,
                            previewBitmap = null,
                            error = e.message?.take(160),
                        )
                    }
                }
            )
        }
    }

    fun openManualOverride() {
        val log = _state.value.log
        _state.update {
            it.copy(
                showManualOverride = true,
                manualAvg = log?.avgMgdl?.toInt()?.toString().orEmpty(),
                manualPeak = log?.peakMgdl?.toInt()?.toString().orEmpty(),
                manualTir = log?.timeInRangePct?.toString().orEmpty(),
                manualHypoCount = log?.hypoCount?.toString().orEmpty(),
            )
        }
    }

    fun closeManualOverride() {
        _state.update { it.copy(showManualOverride = false) }
    }

    fun setManualAvg(s: String) = _state.update { it.copy(manualAvg = s.filter { c -> c.isDigit() || c == '.' }.take(6)) }
    fun setManualPeak(s: String) = _state.update { it.copy(manualPeak = s.filter { c -> c.isDigit() || c == '.' }.take(6)) }
    fun setManualTir(s: String) = _state.update { it.copy(manualTir = s.filter { c -> c.isDigit() }.take(3)) }
    fun setManualHypoCount(s: String) = _state.update { it.copy(manualHypoCount = s.filter { c -> c.isDigit() }.take(2)) }

    fun submitManualOverride() {
        val s = _state.value
        viewModelScope.launch {
            val avg = s.manualAvg.toDoubleOrNull()?.takeIf { it in 30.0..600.0 }
            val peak = s.manualPeak.toDoubleOrNull()?.takeIf { it in 30.0..600.0 }
            val tir = s.manualTir.toIntOrNull()?.coerceIn(0, 100)
            val hypo = s.manualHypoCount.toIntOrNull()?.coerceAtLeast(0)
            glucoseRepository.manualOverride(
                date = s.date,
                avgMgdl = avg,
                peakMgdl = peak,
                timeInRangePct = tir,
                hypoCount = hypo,
            ).fold(
                onSuccess = { entity ->
                    _state.update {
                        it.copy(
                            log = entity,
                            showManualOverride = false,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message?.take(160)) }
                }
            )
        }
    }

    fun deleteToday() {
        viewModelScope.launch {
            glucoseRepository.deleteForDate(_state.value.date)
            _state.update { it.copy(log = null, previewBitmap = null) }
        }
    }
}
