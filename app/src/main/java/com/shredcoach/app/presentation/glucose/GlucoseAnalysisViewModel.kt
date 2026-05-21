package com.shredcoach.app.presentation.glucose

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.shredcoach.app.data.local.entity.AnalysisVerdict
import com.shredcoach.app.data.local.entity.GlucoseAnalysisEntity
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.repository.GlucoseRepository
import com.shredcoach.app.data.repository.NutritionRepository
import com.shredcoach.app.domain.glucose.GlucoseAnalysisEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Catégorie clinique d'un insight, alignée avec [GlucoseAnalysisPrompt]. Le
 * LLM retourne un de ces strings via le JSON ; on les map ici pour driver
 * l'icône / couleur côté UI.
 */
enum class InsightCategory(val displayName: String) {
    POSTPRANDIAL_PEAK("Pic post-repas"),
    RECOVERY("Récupération"),
    DAWN("Aube"),
    CORTISOL_RISE("Cortisol"),
    STABLE_FASTING("Plateau stable"),
    NIGHT_FASTING("Nuit"),
    HYPO("Hypoglycémie"),
    SPIKE("Pic"),
    EXERCISE_RESPONSE("Activité"),
    UNKNOWN("Observation");

    companion object {
        fun fromRaw(raw: String?): InsightCategory =
            raw?.let { runCatching { valueOf(it.uppercase()) }.getOrNull() } ?: UNKNOWN
    }
}

enum class InsightVerdict { POSITIVE, NEUTRAL, CONCERN }

@Immutable
data class GlucoseInsight(
    val time: LocalTime?,
    val category: InsightCategory,
    val title: String,
    val explanation: String,
    val verdict: InsightVerdict,
    val relatedMealName: String?,
)

@Immutable
data class GlucoseAnalysisState(
    val date: LocalDate = LocalDate.now(),
    /** L'analyse persistée (cache + freshly-computed deux confondus). */
    val analysis: GlucoseAnalysisEntity? = null,
    /** Insights désérialisés du JSON pour rendu UI. */
    val insights: List<GlucoseInsight> = emptyList(),
    /** Le log glucose source — utilisé pour rendre la courbe annotée. */
    val glucoseLog: GlucoseLogEntity? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Vrai si l'écran a déjà tenté un fetch initial (évite double-trigger). */
    val initialFetchDone: Boolean = false,
)

@HiltViewModel
class GlucoseAnalysisViewModel @Inject constructor(
    private val engine: GlucoseAnalysisEngine,
    private val glucoseRepository: GlucoseRepository,
    private val nutritionRepository: NutritionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialDate: LocalDate = savedStateHandle.get<String>("date")
        ?.takeIf { it.isNotBlank() && it != "{date}" }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now().minusDays(1)

    private val _state = MutableStateFlow(GlucoseAnalysisState(date = initialDate))
    val state: StateFlow<GlucoseAnalysisState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. Charge le log glucose (pour rendre la courbe annotée)
            val log = glucoseRepository.getForDate(initialDate)
            _state.update { it.copy(glucoseLog = log) }

            // 2. Déclenche analyse (cache-first)
            triggerAnalyze(force = false)
        }
    }

    /** Bouton "Re-analyser" → force LLM call même si cache présent. */
    fun reanalyze() {
        viewModelScope.launch { triggerAnalyze(force = true) }
    }

    private suspend fun triggerAnalyze(force: Boolean) {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val r = engine.analyze(_state.value.date, force = force)) {
            is GlucoseAnalysisEngine.Result.Success -> {
                val insights = parseInsights(r.entity.insightsJson)
                _state.update {
                    it.copy(
                        analysis = r.entity,
                        insights = insights,
                        isLoading = false,
                        errorMessage = null,
                        initialFetchDone = true,
                    )
                }
            }
            is GlucoseAnalysisEngine.Result.Error -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = r.message,
                        initialFetchDone = true,
                    )
                }
            }
        }
    }

    /**
     * Parsing robuste du JSON array d'insights. On accepte des écarts mineurs
     * du LLM (champs manquants, valeurs nulles, casse différente) plutôt que
     * de planter l'écran.
     */
    private fun parseInsights(json: String): List<GlucoseInsight> = try {
        if (json.isBlank()) emptyList()
        else {
            JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
                val obj = el.asJsonObject
                val title = obj.get("title")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val explanation = obj.get("explanation")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val timeStr = obj.get("time")?.asString
                val time = timeStr?.takeIf { it.matches(Regex("^\\d{2}:\\d{2}$")) }
                    ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val category = InsightCategory.fromRaw(obj.get("category")?.asString)
                val verdict = obj.get("verdict")?.asString
                    ?.let { runCatching { InsightVerdict.valueOf(it.uppercase()) }.getOrNull() }
                    ?: InsightVerdict.NEUTRAL
                val relatedMealName = obj.get("relatedMealName")?.takeIf { !it.isJsonNull }
                    ?.asString?.takeIf { it.isNotBlank() }
                GlucoseInsight(
                    time = time,
                    category = category,
                    title = title,
                    explanation = explanation,
                    verdict = verdict,
                    relatedMealName = relatedMealName,
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
