package com.shredcoach.app.presentation.nutrition


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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.google.gson.Gson
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.remote.MealAnalysisResult
import com.shredcoach.app.presentation.theme.OrangeVibrant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel inline (petit, spécifique à cet écran) ──

@Immutable
data class MealScanDetailState(
    val result: MealAnalysisResult? = null,
    val scanName: String = "",
    val photoPath: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class MealScanDetailViewModel @Inject constructor(
    private val mealScanDao: MealScanDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(MealScanDetailState())
    val state: StateFlow<MealScanDetailState> = _state.asStateFlow()

    init {
        val scanId = savedStateHandle.get<String>("scanId")?.toLongOrNull() ?: 0L
        viewModelScope.launch {
            val scan = mealScanDao.getScanById(scanId)
            if (scan != null && scan.resultJson.isNotBlank()) {
                val result = try { Gson().fromJson(scan.resultJson, MealAnalysisResult::class.java) } catch (_: Exception) { null }
                _state.update { it.copy(result = result, scanName = scan.dishName, photoPath = scan.photoPath, isLoading = false) }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }
}

// ── Screen : réutilise les composables de MealScannerScreen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScanDetailScreen(
    navController: NavController,
    viewModel: MealScanDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Détail repas", fontWeight = FontWeight.Bold)
                        if (state.scanName.isNotBlank()) {
                            Text(state.scanName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
            state.result == null -> Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("Analyse introuvable", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Photo originale en haut
                    if (state.photoPath != null) {
                        item {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(
                                    androidx.compose.ui.platform.LocalContext.current
                                ).data(java.io.File(state.photoPath!!)).crossfade(true).build(),
                                contentDescription = "Photo du repas analysé",
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 260.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                    // Résultats identiques au MealScanner
                    val r = state.result!!
                    item { ScoreHeroCard(r) }
                    item { MacrosCard(r) }
                    r.dishes.forEachIndexed { idx, dish ->
                        item(key = "detail_d_$idx") { DishCard(dish) }
                    }
                    if (r.micronutrients.isNotEmpty()) {
                        item { MicronutrientsCard(r.micronutrients) }
                    }
                    if (r.allergens.isNotEmpty()) {
                        item { AllergensCard(r.allergens) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
