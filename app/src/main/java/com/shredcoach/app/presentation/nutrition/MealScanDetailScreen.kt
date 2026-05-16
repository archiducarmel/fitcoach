package com.shredcoach.app.presentation.nutrition


import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.gson.Gson
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.remote.MealAnalysisResult
import com.shredcoach.app.domain.nutrition.MealScanModifierMath
import com.shredcoach.app.domain.nutrition.MealScanModifierService
import com.shredcoach.app.presentation.nutrition.components.LeftoverScanCard
import com.shredcoach.app.presentation.nutrition.components.ServingMultiplierCard
import com.shredcoach.app.presentation.theme.OrangeVibrant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel : observe le scan + expose les actions de modificateurs ──

@Immutable
data class MealScanDetailState(
    val scan: MealScanEntity? = null,
    val result: MealAnalysisResult? = null,
    val isLoading: Boolean = true,
    val isLeftoverAnalyzing: Boolean = false,
    val leftoverError: String? = null,
)

@HiltViewModel
class MealScanDetailViewModel @Inject constructor(
    private val mealScanDao: MealScanDao,
    private val modifierService: MealScanModifierService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(MealScanDetailState())
    val state: StateFlow<MealScanDetailState> = _state.asStateFlow()

    private val scanId: Long = savedStateHandle.get<String>("scanId")?.toLongOrNull() ?: 0L
    private val gson = Gson()

    init {
        // Observe le scan : tout changement (multiplier, leftover) propage l'UI
        // sans qu'on ait à re-trigger manuellement un refresh.
        viewModelScope.launch {
            mealScanDao.observeScanById(scanId).collect { scan ->
                val parsed = scan?.resultJson?.takeIf { it.isNotBlank() }?.let {
                    runCatching { gson.fromJson(it, MealAnalysisResult::class.java) }.getOrNull()
                }
                _state.update { it.copy(scan = scan, result = parsed, isLoading = false) }
            }
        }
    }

    /** Applique un nouveau multiplicateur (×N). Clampé côté service. */
    fun setMultiplier(multiplier: Float) {
        viewModelScope.launch {
            modifierService.setMultiplier(scanId, multiplier)
        }
    }

    /**
     * Lance l'OCR Gemini sur la photo des restes. Met à jour `isLeftoverAnalyzing`
     * pour driver le spinner UI. En cas d'échec, `leftoverError` est rempli ;
     * le ViewModel ne le clear pas automatiquement (l'utilisateur peut rescanner).
     */
    fun scanLeftover(bitmap: Bitmap) {
        if (_state.value.isLeftoverAnalyzing) return
        _state.update { it.copy(isLeftoverAnalyzing = true, leftoverError = null) }
        viewModelScope.launch {
            val result = modifierService.scanAndApplyLeftover(scanId, bitmap)
            _state.update {
                it.copy(
                    isLeftoverAnalyzing = false,
                    leftoverError = result.exceptionOrNull()?.message?.take(180),
                )
            }
        }
    }

    /** Reset les restes (suppression photo + champs à zéro). */
    fun clearLeftover() {
        viewModelScope.launch {
            modifierService.clearLeftover(scanId)
            _state.update { it.copy(leftoverError = null) }
        }
    }
}

// ── Screen : compose photo + score + modificateurs + macros + ingrédients ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScanDetailScreen(
    navController: NavController,
    viewModel: MealScanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.mealdetail_title), fontWeight = FontWeight.Bold)
                        val name = state.scan?.dishName
                        if (!name.isNullOrBlank()) {
                            Text(name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            state.result == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text(stringResource(R.string.mealdetail_not_found), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            else -> {
                val scan = state.scan!!
                val baseResult = state.result!!
                // Construit un résultat "effectif" en appliquant le facteur (multiplicateur
                // + déduction restes) à toutes les valeurs affichées. Le baseResult reste
                // intact en mémoire pour permettre des édits ultérieurs basés sur le 1x.
                val effectiveResult = remember(baseResult, scan.servingMultiplier, scan.leftoverCalories, scan.totalCalories) {
                    applyEffectiveFactor(baseResult, scan)
                }

                LazyColumn(
                    Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Photo originale en haut
                    if (scan.photoPath != null) {
                        item {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(
                                    androidx.compose.ui.platform.LocalContext.current
                                ).data(java.io.File(scan.photoPath!!)).crossfade(true).build(),
                                contentDescription = stringResource(R.string.mealdetail_photo_cd),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }

                    // ── Score (basé sur les valeurs effectives) ──
                    item { ScoreHeroCard(effectiveResult) }

                    // ── Multiplicateur de portion ("j'en ai repris") ──
                    item {
                        ServingMultiplierCard(
                            current = scan.servingMultiplier,
                            baseCalories = scan.totalCalories,
                            leftoverCalories = scan.leftoverCalories,
                            onMultiplierChange = viewModel::setMultiplier,
                        )
                    }

                    // ── Restes ("j'ai pas fini") ──
                    item {
                        LeftoverScanCard(
                            leftoverPhotoPath = scan.leftoverPhotoPath,
                            leftoverCalories = scan.leftoverCalories,
                            leftoverProteins = scan.leftoverProteins,
                            leftoverCarbs = scan.leftoverCarbs,
                            leftoverFats = scan.leftoverFats,
                            isAnalyzing = state.isLeftoverAnalyzing,
                            errorMessage = state.leftoverError,
                            onScanLeftover = viewModel::scanLeftover,
                            onClearLeftover = viewModel::clearLeftover,
                        )
                    }

                    // ── Macros et plats (valeurs effectives) ──
                    item { MacrosCard(effectiveResult) }
                    effectiveResult.dishes.forEachIndexed { idx, dish ->
                        item(key = "detail_d_$idx") { DishCard(dish) }
                    }
                    if (effectiveResult.micronutrients.isNotEmpty()) {
                        item { MicronutrientsCard(effectiveResult.micronutrients) }
                    }
                    if (effectiveResult.allergens.isNotEmpty()) {
                        item { AllergensCard(effectiveResult.allergens) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * Applique le facteur effectif (multiplicateur − ratio leftover calorique) à
 * toutes les valeurs d'un [MealAnalysisResult]. Le ratio unique est appliqué
 * uniformément à toutes les macros — symétrique avec la formule SQL et
 * [MealScanModifierMath].
 *
 * Si aucun modificateur n'est actif (mult=1, leftover=0), retourne le baseResult
 * tel quel (référence stable, pas de copie).
 */
private fun applyEffectiveFactor(
    base: MealAnalysisResult,
    scan: MealScanEntity,
): MealAnalysisResult {
    if (!MealScanModifierMath.hasModifier(scan)) return base
    val factor = MealScanModifierMath.effectiveFactor(scan)
    if (factor == 1.0) return base
    return base.copy(
        totalCalories = (base.totalCalories * factor).toInt().coerceAtLeast(0),
        totalProteins = (base.totalProteins * factor).coerceAtLeast(0.0),
        totalCarbs = (base.totalCarbs * factor).coerceAtLeast(0.0),
        totalFats = (base.totalFats * factor).coerceAtLeast(0.0),
        totalFibers = (base.totalFibers * factor).coerceAtLeast(0.0),
        totalWeight = (base.totalWeight * factor).toInt().coerceAtLeast(0),
        dishes = base.dishes.map { dish ->
            dish.copy(
                calories = (dish.calories * factor).toInt().coerceAtLeast(0),
                proteins = (dish.proteins * factor).coerceAtLeast(0.0),
                carbs = (dish.carbs * factor).coerceAtLeast(0.0),
                carbsSugar = (dish.carbsSugar * factor).coerceAtLeast(0.0),
                fats = (dish.fats * factor).coerceAtLeast(0.0),
                fatsSaturated = (dish.fatsSaturated * factor).coerceAtLeast(0.0),
                fibers = (dish.fibers * factor).coerceAtLeast(0.0),
                salt = (dish.salt * factor).coerceAtLeast(0.0),
                weightG = (dish.weightG * factor).toInt().coerceAtLeast(0),
                ingredients = dish.ingredients.map { ing ->
                    ing.copy(
                        calories = (ing.calories * factor).toInt().coerceAtLeast(0),
                        proteins = (ing.proteins * factor).coerceAtLeast(0.0),
                        carbs = (ing.carbs * factor).coerceAtLeast(0.0),
                        fats = (ing.fats * factor).coerceAtLeast(0.0),
                        fibers = (ing.fibers * factor).coerceAtLeast(0.0),
                        weightG = (ing.weightG * factor).toInt().coerceAtLeast(0),
                    )
                },
            )
        },
    )
}
