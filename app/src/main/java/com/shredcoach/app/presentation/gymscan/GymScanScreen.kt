package com.shredcoach.app.presentation.gymscan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shredcoach.app.R
import com.shredcoach.app.data.remote.ExerciseDbExercise
import com.shredcoach.app.data.remote.GymScanResult
import com.shredcoach.app.presentation.explorer.ExerciseDbTranslations
import com.shredcoach.app.presentation.explorer.levelColor
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GymScanScreen(
    navController: NavController,
    viewModel: GymScanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var cameraPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { viewModel.setImage(it) } }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted; if (granted) cameraLauncher.launch(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it)).copy(Bitmap.Config.ARGB_8888, false)
                else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                viewModel.setImage(bmp)
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.gymscan_title), fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.gymscan_subtitle), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (state.imageBitmap != null) {
                        IconButton(onClick = { viewModel.clear() }) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.gymscan_action_new_cd))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            when {
                state.imageBitmap == null -> CaptureZone(
                    onCamera = {
                        if (cameraPermissionGranted) cameraLauncher.launch(null)
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
                else -> ResultView(
                    state = state,
                    onAnalyze = { viewModel.analyze() },
                    onRetake = {
                        if (cameraPermissionGranted) cameraLauncher.launch(null)
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onPickGallery = { galleryLauncher.launch("image/*") },
                    onExerciseClick = { ex ->
                        navController.navigate(Screen.ExerciseDbDetail.createRoute(ex.id))
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// ZONE CAPTURE (initial)
// ═══════════════════════════════════════

@Composable
private fun CaptureZone(onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hero illustratif ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(
                            Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFE91E63)
                        ))
                    )
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.gymscan_capture_title), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(stringResource(R.string.gymscan_capture_subtitle), style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.92f))
                        }
                    }
                    Text(
                        stringResource(R.string.gymscan_capture_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.92f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // ── Boutons action ──
        Button(
            onClick = onCamera,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.gymscan_btn_camera), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        OutlinedButton(
            onClick = onGallery,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.gymscan_btn_gallery), fontWeight = FontWeight.SemiBold)
        }

        // ── Tips d'utilisation ──
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(16.dp), tint = OrangeVibrant)
                    Text(stringResource(R.string.gymscan_tips_title), fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall)
                }
                TipLine(stringResource(R.string.gymscan_tip_frame))
                TipLine(stringResource(R.string.gymscan_tip_lighting))
                TipLine(stringResource(R.string.gymscan_tip_close))
                TipLine(stringResource(R.string.gymscan_tip_accessories))
            }
        }
    }
}

@Composable
private fun TipLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text("•", color = OrangeVibrant, fontWeight = FontWeight.ExtraBold)
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

// ═══════════════════════════════════════
// RÉSULTAT (après capture)
// ═══════════════════════════════════════

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ResultView(
    state: GymScanState,
    onAnalyze: () -> Unit,
    onRetake: () -> Unit,
    onPickGallery: () -> Unit,
    onExerciseClick: (ExerciseDbExercise) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Photo capturée + overlay scan futuriste si analyse en cours ──
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 320.dp)
            ) {
                state.imageBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.gymscan_image_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Overlay scan holographique pendant tout le pipeline
                if (state.isLoadingDataset || state.isAnalyzing) {
                    MachineScanOverlay(
                        label = if (state.isLoadingDataset) stringResource(R.string.gymscan_overlay_loading) else stringResource(R.string.gymscan_overlay_analyzing),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        when {
            // État 1 : photo chargée, pas encore analysée
            state.llmResult == null && !state.isAnalyzing && !state.isLoadingDataset && state.error == null -> {
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gymscan_btn_analyze), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gymscan_btn_retake), fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onPickGallery, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gymscan_btn_pick_short), fontSize = 13.sp)
                    }
                }
            }

            // État 2 : pipeline en cours
            state.isLoadingDataset -> AnalyzingCard(stringResource(R.string.gymscan_state_loading_db))
            state.isAnalyzing -> AnalyzingCard(stringResource(R.string.gymscan_state_analyzing))

            // État 3 : erreur
            state.error != null && state.llmResult == null -> ErrorCard(state.error, onAnalyze, onRetake)

            // État 4 : succès — résultat complet
            state.llmResult != null -> AnalysisResult(
                result = state.llmResult,
                matchedExercises = state.matchedExercises,
                onExerciseClick = onExerciseClick
            )
        }
    }
}

// ═══════════════════════════════════════
// CARTES D'ÉTAT
// ═══════════════════════════════════════

@Composable
private fun AnalyzingCard(label: String) {
    val infinite = rememberInfiniteTransition(label = "spin")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = OrangeVibrant, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(alpha))
                Text(stringResource(R.string.gymscan_state_subtitle), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onRetake: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.gymscan_error_title), fontWeight = FontWeight.ExtraBold)
            }
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.gymscan_btn_retry))
                }
                OutlinedButton(onClick = onRetake) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.gymscan_btn_other_photo))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// AFFICHAGE ANALYSE COMPLÈTE
// ═══════════════════════════════════════

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AnalysisResult(
    result: GymScanResult,
    matchedExercises: List<ExerciseDbExercise>,
    onExerciseClick: (ExerciseDbExercise) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ─── CARTE IDENTIFICATION (avec CTA cliquable vers le détail du meilleur match) ───
        val topMatch = matchedExercises.firstOrNull()
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            modifier = if (topMatch != null) {
                Modifier.clickable { onExerciseClick(topMatch) }
            } else Modifier
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(OrangeVibrant, Color(0xFFE91E63))))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(stringResource(R.string.gymscan_result_machine_label), style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        ConfidenceBadge(result.confidence)
                    }
                    Text(result.machineName, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold, color = Color.White, lineHeight = 28.sp)
                    if (result.description.isNotBlank()) {
                        Text(result.description, style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f), lineHeight = 20.sp)
                    }

                    // ─── CTA bas de card : navigue vers le détail complet (GIF + instructions FR) ───
                    if (topMatch != null) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayCircle, null,
                                        tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.gymscan_cta_view_demo),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        fontSize = 12.sp)
                                    Text(ExerciseDbTranslations.translateExerciseName(topMatch.name),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // ─── BADGES : niveau / muscles / équipement ───
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (result.difficulty.isNotBlank()) {
                TagBadge(
                    ExerciseDbTranslations.displayLevel(result.difficulty),
                    Icons.Default.Star, levelColor(result.difficulty)
                )
            }
            if (result.equipmentKeyword.isNotBlank()) {
                TagBadge(
                    ExerciseDbTranslations.displayEquipment(result.equipmentKeyword),
                    Icons.Default.SportsGymnastics, OrangeVibrant
                )
            }
            result.primaryMuscles.forEach { m ->
                TagBadge(ExerciseDbTranslations.displayMuscle(m),
                    Icons.Default.SelfImprovement, NeonGreen)
            }
        }

        // ─── SETUP ───
        if (result.setupSteps.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.gymscan_section_setup), icon = Icons.Default.Build, color = Color(0xFF3B82F6)) {
                result.setupSteps.forEachIndexed { idx, step ->
                    NumberedStep(idx + 1, step, Color(0xFF3B82F6))
                }
            }
        }

        // ─── SÉCURITÉ ───
        if (result.safetyTips.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.gymscan_section_safety), icon = Icons.Default.Shield, color = Color(0xFFEF4444)) {
                result.safetyTips.forEach { tip ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text("⚠", color = Color(0xFFEF4444), fontWeight = FontWeight.ExtraBold)
                        Text(tip, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                }
            }
        }

        // ─── MUSCLES SECONDAIRES ───
        if (result.secondaryMuscles.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.gymscan_section_secondary_muscles), icon = Icons.Default.Tune, color = Color(0xFF8B5CF6)) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.secondaryMuscles.forEach { m ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF8B5CF6).copy(alpha = 0.12f)) {
                            Text(ExerciseDbTranslations.displayMuscle(m),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF8B5CF6))
                        }
                    }
                }
            }
        }

        // ─── EXERCICES MATCHÉS (carousel horizontal cliquable) ───
        if (matchedExercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FitnessCenter, null, modifier = Modifier.size(16.dp), tint = OrangeVibrant)
                    }
                    Text(stringResource(R.string.gymscan_section_matched_exercises, matchedExercises.size), fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
                Text(stringResource(R.string.gymscan_matched_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(matchedExercises, key = { it.id }) { ex ->
                        MatchedExerciseCard(ex = ex, onClick = { onExerciseClick(ex) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Int) {
    val (bg, label) = when {
        confidence >= 80 -> Color.White.copy(alpha = 0.28f) to stringResource(R.string.gymscan_conf_high)
        confidence >= 50 -> Color.White.copy(alpha = 0.20f) to stringResource(R.string.gymscan_conf_medium)
        else -> Color.White.copy(alpha = 0.15f) to stringResource(R.string.gymscan_conf_low)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$confidence%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
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
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = color, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = color)
                }
                Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
private fun NumberedStep(index: Int, step: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(24.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("$index", color = Color.White, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(step, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 2.dp))
    }
}

// ═══════════════════════════════════════
// OVERLAY SCAN MACHINE (holographique futuriste)
// ═══════════════════════════════════════

/**
 * Overlay scan holographique type "analyse machine" :
 *  - 4 crochets de détection aux coins qui respirent
 *  - Faisceau horizontal balayant verticalement avec dégradé cyan
 *  - Grille technique en fond très léger
 *  - Points de détection qui apparaissent/disparaissent
 *  - Réticule central pulsant
 *  - HUD texte en bas ("ANALYSE IA · 01001…")
 *
 * Palette : cyan néon (#00E5FF) + bleu électrique, sur fond sombre semi-transparent.
 */
@Composable
private fun MachineScanOverlay(label: String, modifier: Modifier = Modifier) {
    val cyan = Color(0xFF00E5FF)
    val cyanSoft = Color(0xFF00E5FF).copy(alpha = 0.3f)
    val deepBlue = Color(0xFF0A1628)

    // Animations
    val transition = rememberInfiniteTransition(label = "scan_fx")

    // Balayage vertical (0 → 1 → 0)
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    // Pulse des coins (0.4 ↔ 1)
    val cornerPulse by transition.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "corners"
    )
    // Crosshair pulse
    val crossPulse by transition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "cross"
    )
    // Rotation lente des marqueurs de détection
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot"
    )
    // Progression du texte HUD
    val progressCount by transition.animateFloat(
        initialValue = 0f, targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "prog"
    )

    Box(modifier.background(deepBlue.copy(alpha = 0.35f))) {
        // ── 1. GRILLE TECHNIQUE ──
        Canvas(Modifier.fillMaxSize()) {
            val gridSpacing = 28.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(cyan.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridSpacing
            }
            var y = 0f
            while (y < size.height) {
                drawLine(cyan.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridSpacing
            }
        }

        // ── 2. FAISCEAU DE BALAYAGE (beam horizontal avec glow) ──
        Canvas(Modifier.fillMaxSize()) {
            val beamY = size.height * sweep
            val beamHeight = 60f
            // Dégradé vertical du faisceau (halo)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        cyan.copy(alpha = 0.15f),
                        cyan.copy(alpha = 0.5f),
                        cyan.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    startY = beamY - beamHeight,
                    endY = beamY + beamHeight
                ),
                topLeft = Offset(0f, beamY - beamHeight),
                size = androidx.compose.ui.geometry.Size(size.width, beamHeight * 2)
            )
            // Ligne centrale nette
            drawLine(cyan, Offset(0f, beamY), Offset(size.width, beamY), strokeWidth = 2f, cap = StrokeCap.Round)
        }

        // ── 3. CROCHETS DE DÉTECTION aux 4 coins (animés) ──
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val bracketSize = 28.dp.toPx()
            val strokeWidth = 3.dp.toPx()
            val color = cyan.copy(alpha = cornerPulse)

            // Haut-gauche
            drawLine(color, Offset(0f, 0f), Offset(bracketSize, 0f), strokeWidth)
            drawLine(color, Offset(0f, 0f), Offset(0f, bracketSize), strokeWidth)
            // Haut-droite
            drawLine(color, Offset(size.width - bracketSize, 0f), Offset(size.width, 0f), strokeWidth)
            drawLine(color, Offset(size.width, 0f), Offset(size.width, bracketSize), strokeWidth)
            // Bas-gauche
            drawLine(color, Offset(0f, size.height - bracketSize), Offset(0f, size.height), strokeWidth)
            drawLine(color, Offset(0f, size.height), Offset(bracketSize, size.height), strokeWidth)
            // Bas-droite
            drawLine(color, Offset(size.width, size.height - bracketSize), Offset(size.width, size.height), strokeWidth)
            drawLine(color, Offset(size.width - bracketSize, size.height), Offset(size.width, size.height), strokeWidth)
        }

        // ── 4. RÉTICULE CENTRAL (croix + cercle pulsant) ──
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val ringRadius = 22.dp.toPx()
            val innerRadius = 4.dp.toPx()

            // Anneau pulsant
            drawCircle(
                color = cyan.copy(alpha = crossPulse),
                radius = ringRadius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Point central
            drawCircle(cyan, radius = innerRadius, center = Offset(cx, cy))
            // Croix de visée
            val crossLen = 14.dp.toPx()
            val crossColor = cyan.copy(alpha = 0.7f)
            val s = 1.5.dp.toPx()
            drawLine(crossColor, Offset(cx - ringRadius - crossLen, cy), Offset(cx - ringRadius - 2.dp.toPx(), cy), s)
            drawLine(crossColor, Offset(cx + ringRadius + 2.dp.toPx(), cy), Offset(cx + ringRadius + crossLen, cy), s)
            drawLine(crossColor, Offset(cx, cy - ringRadius - crossLen), Offset(cx, cy - ringRadius - 2.dp.toPx()), s)
            drawLine(crossColor, Offset(cx, cy + ringRadius + 2.dp.toPx()), Offset(cx, cy + ringRadius + crossLen), s)
        }

        // ── 5. MARQUEURS DE DÉTECTION rotatifs (3 positions fixes, rotation sur eux-mêmes) ──
        Canvas(Modifier.fillMaxSize()) {
            val markerPositions = listOf(
                Offset(size.width * 0.22f, size.height * 0.3f),
                Offset(size.width * 0.78f, size.height * 0.45f),
                Offset(size.width * 0.35f, size.height * 0.78f)
            )
            markerPositions.forEachIndexed { idx, pos ->
                val angleOffset = idx * 120f
                val rotRad = Math.toRadians((rotation + angleOffset).toDouble())
                val r = 12.dp.toPx()
                val c = cyan.copy(alpha = 0.7f)

                // 4 segments courts formant un "+" rotatif
                for (i in 0..3) {
                    val a = rotRad + i * Math.PI / 2
                    val inner = Offset(
                        pos.x + (r * 0.4f * kotlin.math.cos(a)).toFloat(),
                        pos.y + (r * 0.4f * kotlin.math.sin(a)).toFloat()
                    )
                    val outer = Offset(
                        pos.x + (r * kotlin.math.cos(a)).toFloat(),
                        pos.y + (r * kotlin.math.sin(a)).toFloat()
                    )
                    drawLine(c, inner, outer, 2f)
                }
                // Petit point central
                drawCircle(cyan, radius = 2.dp.toPx(), center = pos)
            }
        }

        // ── 6. HUD TEXTE en bas ──
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = deepBlue.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, cyan.copy(alpha = 0.5f))
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Point "LIVE" clignotant
                    Box(
                        Modifier.size(6.dp).clip(CircleShape)
                            .background(cyan.copy(alpha = crossPulse))
                    )
                    Text(
                        label,
                        color = cyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "· ${progressCount.toInt().toString().padStart(3, '0')}%",
                        color = cyan.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Ligne de data fictive façon console
            Text(
                "› identifier.detect[${(progressCount * 8.73).toInt()}] match.score=${(progressCount * 0.95).toInt()}",
                color = cyan.copy(alpha = 0.55f),
                fontSize = 8.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        // ── 7. BORDURE EXTÉRIEURE néon ──
        Box(
            Modifier.fillMaxSize()
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(
                        cyan.copy(alpha = cornerPulse),
                        cyanSoft,
                        cyan.copy(alpha = cornerPulse)
                    )),
                    RoundedCornerShape(20.dp)
                )
        )
    }
}

@Composable
private fun MatchedExerciseCard(ex: ExerciseDbExercise, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(160.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (ex.firstImageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(ex.firstImageUrl).crossfade(true).build(),
                        contentDescription = ex.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = OrangeVibrant)
                            }
                        }
                    )
                } else {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(32.dp).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
                Surface(
                    shape = RoundedCornerShape(bottomStart = 10.dp),
                    color = levelColor(ex.level).copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(ExerciseDbTranslations.displayLevel(ex.level),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(ExerciseDbTranslations.translateExerciseName(ex.name),
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp, fontSize = 12.sp)
                Text(ExerciseDbTranslations.displayMuscle(ex.primaryMuscle),
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeVibrant, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
            }
        }
    }
}

