package com.shredcoach.app.presentation.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.remote.ExerciseDbExercise
import com.shredcoach.app.data.remote.ExerciseDbService
import com.shredcoach.app.data.remote.InstructionsTranslator
import com.shredcoach.app.data.repository.ExerciseRepository
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ═══════════════════════════════════════
// VIEWMODEL DÉTAIL
// ═══════════════════════════════════════

data class ExerciseDbDetailState(
    val exercise: ExerciseDbExercise? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val savedToFavorites: Boolean = false,
    val isSaving: Boolean = false,
    // Traduction FR des instructions (asynchrone, cache partagé)
    val translatedInstructions: List<String>? = null,
    val isTranslating: Boolean = false
)

@HiltViewModel
class ExerciseDbDetailViewModel @Inject constructor(
    private val service: ExerciseDbService,
    private val exerciseRepository: ExerciseRepository,
    private val userRepository: UserRepository,
    private val instructionsTranslator: InstructionsTranslator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseDbDetailState())
    val state: StateFlow<ExerciseDbDetailState> = _state.asStateFlow()

    init {
        val rawId = savedStateHandle.get<String>("exerciseId").orEmpty()
        val id = try { java.net.URLDecoder.decode(rawId, "UTF-8") } catch (_: Exception) { rawId }
        if (id.isNotBlank()) load(id)
    }

    private fun load(id: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            service.getExerciseById(id)
                .onSuccess { ex ->
                    _state.update { it.copy(exercise = ex, isLoading = false) }
                    // Déclenche la traduction en tâche de fond (non-bloquant)
                    if (ex.instructions.isNotEmpty()) translateInBackground(ex)
                }
                .onFailure { err -> _state.update { it.copy(isLoading = false, error = err.message ?: "Erreur") } }
        }
    }

    /**
     * Lance la traduction LLM des instructions en arrière-plan.
     * Ne bloque PAS l'affichage : l'utilisateur voit l'anglais d'abord, puis le français apparaît.
     */
    private fun translateInBackground(ex: ExerciseDbExercise) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce() ?: return@launch
            val provider = profile.mealScanProvider
            val apiKey = when (provider) {
                "GROQ" -> userRepository.getApiKey(com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.GROQ_MEAL)
                "MISTRAL" -> userRepository.getApiKey(com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.MISTRAL)
                else -> userRepository.getApiKey(com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.GEMINI)
            }
            if (apiKey.isBlank()) return@launch // Pas de clé → on reste en anglais silencieusement

            _state.update { it.copy(isTranslating = true) }
            instructionsTranslator.translate(
                exerciseId = ex.id,
                instructionsEn = ex.instructions,
                apiKey = apiKey,
                model = profile.geminiModel,
                provider = provider
            ).onSuccess { translated ->
                _state.update { it.copy(translatedInstructions = translated, isTranslating = false) }
            }.onFailure {
                _state.update { it.copy(isTranslating = false) }
                // Silencieux : fallback anglais
            }
        }
    }

    fun saveToFavorites() {
        val ex = _state.value.exercise ?: return
        if (_state.value.isSaving || _state.value.savedToFavorites) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val muscleGroup = mapMuscleToGroup(ex.primaryMuscle)
                val variant = mapEquipmentToVariant(ex.equipment.orEmpty())
                // Si une traduction FR est dispo (ou cache), on l'utilise pour la sauvegarde locale
                val instructionsToSave = _state.value.translatedInstructions ?: ex.instructions
                val entity = ExerciseEntity(
                    name = com.shredcoach.app.presentation.explorer.ExerciseDbTranslations.translateExerciseName(ex.name),
                    muscleGroup = muscleGroup,
                    variant = variant,
                    equipment = ex.equipment?.let {
                        com.shredcoach.app.presentation.explorer.ExerciseDbTranslations.displayEquipment(it)
                    } ?: "—",
                    executionKey = instructionsToSave.joinToString("\n") { "• $it" }
                        .ifBlank { "Voir les images de démonstration." },
                    startingWeight = "—",
                    series = 3,
                    repsMin = 8,
                    repsMax = 12,
                    restSeconds = 90,
                    tips = listOfNotNull(
                        ex.category.takeIf { it.isNotBlank() }?.let { "Catégorie : ${it.replaceFirstChar { c -> c.uppercase() }}" },
                        ex.mechanic?.let { "Mécanique : ${it.replaceFirstChar { c -> c.uppercase() }}" },
                        ex.force?.let { "Force : ${it.replaceFirstChar { c -> c.uppercase() }}" },
                        ex.level.takeIf { it.isNotBlank() }?.let { "Niveau : ${it.replaceFirstChar { c -> c.uppercase() }}" }
                    ).joinToString("\n"),
                    tempo = "2-0-1-0",
                    gifUrl = ex.firstImageUrl,
                    difficulty = mapLevelToDifficulty(ex.level),
                    isTimeBased = ex.category.equals("stretching", ignoreCase = true)
                            || ex.category.equals("cardio", ignoreCase = true)
                )
                exerciseRepository.insertExercise(entity)
                _state.update { it.copy(savedToFavorites = true, isSaving = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    private fun mapMuscleToGroup(muscle: String): MuscleGroup = when (muscle.lowercase()) {
        "chest" -> MuscleGroup.CHEST
        "lats" -> MuscleGroup.BACK_WIDTH
        "middle back", "traps" -> MuscleGroup.BACK_THICKNESS
        "lower back" -> MuscleGroup.LOWER_BACK
        "shoulders", "neck" -> MuscleGroup.SHOULDERS
        "biceps" -> MuscleGroup.BICEPS
        "triceps" -> MuscleGroup.TRICEPS
        "forearms" -> MuscleGroup.FOREARMS
        "quadriceps", "glutes" -> MuscleGroup.QUADS
        "hamstrings" -> MuscleGroup.HAMSTRINGS
        "calves" -> MuscleGroup.CALVES
        "abductors", "adductors" -> MuscleGroup.ADDUCTORS
        "abdominals" -> MuscleGroup.ABS_UPPER
        else -> MuscleGroup.WARMUP
    }

    private fun mapEquipmentToVariant(equipment: String): ExerciseVariant {
        val eq = equipment.lowercase()
        return when {
            "body only" in eq || "bands" in eq || "foam roll" in eq -> ExerciseVariant.BODYWEIGHT
            "machine" in eq || "cable" in eq -> ExerciseVariant.MACHINE
            "barbell" in eq || "dumbbell" in eq || "kettlebells" in eq
                    || "e-z curl" in eq || "medicine ball" in eq -> ExerciseVariant.WEIGHTS
            "exercise ball" in eq -> ExerciseVariant.ISOLATION
            else -> ExerciseVariant.MACHINE
        }
    }

    private fun mapLevelToDifficulty(level: String): Int = when (level.lowercase()) {
        "beginner" -> 1
        "intermediate" -> 2
        "expert" -> 3
        else -> 2
    }
}

// ═══════════════════════════════════════
// SCREEN
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseDbDetailScreen(
    navController: NavController,
    viewModel: ExerciseDbDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val ex = state.exercise

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ex?.name?.let { ExerciseDbTranslations.translateExerciseName(it) } ?: "Détail",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Retour")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrangeVibrant)
            }
            state.error != null && ex == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error ?: "Erreur", color = MaterialTheme.colorScheme.error)
                }
            }
            ex != null -> DetailContent(
                modifier = Modifier.padding(padding),
                ex = ex,
                state = state,
                onSave = { viewModel.saveToFavorites() }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    modifier: Modifier = Modifier,
    ex: ExerciseDbExercise,
    state: ExerciseDbDetailState,
    onSave: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── HERO : animation type GIF (alterne départ/arrivée) + 2 photos statiques en dessous ──
        Box(Modifier.fillMaxWidth().height(320.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (ex.images.isNotEmpty()) {
                AnimatedExerciseImage(
                    firstUrl = ex.firstImageUrl,
                    secondUrl = ex.secondImageUrl,
                    contentDescription = ex.name,
                    modifier = Modifier.fillMaxSize(),
                    frameDurationMs = 800L
                )
            } else {
                Icon(Icons.Default.BrokenImage, null,
                    modifier = Modifier.size(64.dp).align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)))))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OrangeVibrant,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Text("Démonstration animée", style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // ── Vignettes statiques départ/arrivée pour référence détaillée ──
        if (ex.firstImageUrl.isNotBlank() && ex.secondImageUrl.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StillFrameThumbnail(ex.firstImageUrl, "Position de départ", Modifier.weight(1f))
                StillFrameThumbnail(ex.secondImageUrl, "Position d'arrivée", Modifier.weight(1f))
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(ExerciseDbTranslations.translateExerciseName(ex.name),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            // ── BADGES (libellés FR) ──
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (ex.level.isNotBlank())
                    TagBadge(ExerciseDbTranslations.displayLevel(ex.level),
                        Icons.Default.Star, levelColor(ex.level))
                if (ex.category.isNotBlank())
                    TagBadge(ExerciseDbTranslations.displayCategory(ex.category),
                        Icons.Default.Category, Color(0xFF3B82F6))
                ex.mechanic?.let {
                    TagBadge(ExerciseDbTranslations.displayMechanic(it),
                        Icons.Default.Build, Color(0xFF8B5CF6))
                }
                ex.force?.let {
                    TagBadge(ExerciseDbTranslations.displayForce(it),
                        Icons.Default.Bolt, Color(0xFFEAB308))
                }
                ex.equipment?.let {
                    TagBadge(ExerciseDbTranslations.displayEquipment(it),
                        Icons.Default.SportsGymnastics, OrangeVibrant)
                }
            }

            // ── BOUTON SAUVEGARDER ──
            Button(
                onClick = onSave,
                enabled = !state.savedToFavorites && !state.isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.savedToFavorites) NeonGreen else OrangeVibrant
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                when {
                    state.isSaving -> CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    state.savedToFavorites -> {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouté à ma bibliothèque", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    else -> {
                        Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sauvegarder dans ma bibliothèque", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ── MUSCLES PRINCIPAUX (FR) ──
            if (ex.primaryMuscles.isNotEmpty()) {
                SectionHeader("Muscles principaux", Icons.Default.SelfImprovement, NeonGreen)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ex.primaryMuscles.forEach {
                        Surface(shape = RoundedCornerShape(8.dp), color = NeonGreen.copy(alpha = 0.13f)) {
                            Text(ExerciseDbTranslations.displayMuscle(it),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = NeonGreen)
                        }
                    }
                }
            }
            if (ex.secondaryMuscles.isNotEmpty()) {
                SectionHeader("Muscles secondaires", Icons.Default.Tune, Color(0xFF8B5CF6))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ex.secondaryMuscles.forEach {
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF8B5CF6).copy(alpha = 0.12f)) {
                            Text(ExerciseDbTranslations.displayMuscle(it),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF8B5CF6))
                        }
                    }
                }
            }

            // ── INSTRUCTIONS (traduites auto par IA, fallback anglais) ──
            if (ex.instructions.isNotEmpty()) {
                SectionHeader("Exécution étape par étape", Icons.Default.FormatListNumbered, OrangeVibrant)

                val displayInstructions = state.translatedInstructions ?: ex.instructions
                val isFrench = state.translatedInstructions != null

                // Indicateur de traduction (léger, sous le header)
                Row(
                    modifier = Modifier.padding(start = 36.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        state.isTranslating -> {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(10.dp),
                                color = OrangeVibrant
                            )
                            Text("Traduction en cours…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp)
                        }
                        isFrench -> {
                            Text("🇫🇷", fontSize = 10.sp)
                            Text("Traduit automatiquement par IA",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreen.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp)
                        }
                        else -> {
                            Text("🇺🇸", fontSize = 10.sp)
                            Text("Instructions en anglais (traduction indisponible)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp)
                        }
                    }
                }

                displayInstructions.forEachIndexed { idx, step -> InstructionStep(idx + 1, step) }
            }
        }
    }
}

@Composable
private fun StillFrameThumbnail(url: String, label: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = OrangeVibrant)
                        }
                    },
                    error = {
                        Icon(Icons.Default.BrokenImage, null,
                            modifier = Modifier.size(32.dp).align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                )
            }
            Text(label,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun TagBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.13f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
            Text(label.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = color, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun InstructionStep(index: Int, step: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = OrangeVibrant, modifier = Modifier.size(28.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("$index", color = Color.White, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(step, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}
