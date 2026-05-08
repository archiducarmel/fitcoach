package com.shredcoach.app.presentation.bodyscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScannerScreen(
    navController: NavController,
    viewModel: BodyScannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // ── Permission caméra + launchers ──
    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> bitmap?.let { viewModel.setImage(it) } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) cameraLauncher.launch(null)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                        .copy(Bitmap.Config.ARGB_8888, false)
                else @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                viewModel.setImage(bmp)
            } catch (_: Exception) {}
        }
    }

    fun launchCamera() {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        cameraPermissionGranted = has
        if (has) cameraLauncher.launch(null) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ── Auto-navigation post-génération mesh ──
    // Quand l'user déclenche `generateMeshAndNavigate`, on attend que la
    // génération aboutisse (`!isGeneratingMesh && meshFeaturesPath != null`)
    // puis on navigue. Le flag pendingNavigateToMesh distingue ce flow d'une
    // navigation utilisateur classique (bouton "Voir le mesh").
    LaunchedEffect(state.pendingNavigateToMesh, state.meshFeaturesPath, state.isGeneratingMesh) {
        if (state.pendingNavigateToMesh
            && !state.isGeneratingMesh
            && state.meshFeaturesPath != null
        ) {
            // Reset AVANT navigation pour éviter qu'au retour sur l'écran le
            // LaunchedEffect re-fire (les conditions seraient encore vraies).
            viewModel.consumeMeshNavigation()
            navController.navigate(Screen.BodyMesh.route)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Accessibility, null, Modifier.size(24.dp), tint = OrangeVibrant)
                        Column {
                            Text(stringResource(R.string.bodyscan_title), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.bodyscan_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Affiché dès qu'une photo OU un mesh existant est dispo.
                    // Click :
                    //  - mesh déjà généré → navigation directe
                    //  - photo dispo, mesh pas encore → trigger génération + nav auto
                    //  - sinon : invisible (rien à montrer)
                    if (state.imageBitmap != null || state.meshFeaturesPath != null || state.result != null) {
                        val hasMesh = state.meshFeaturesPath != null
                        IconButton(
                            enabled = !state.isGeneratingMesh,
                            onClick = { viewModel.generateMeshAndNavigate() },
                        ) {
                            if (state.isGeneratingMesh) {
                                CircularProgressIndicator(
                                    color = NeonGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Default.GridOn,
                                    stringResource(R.string.bodyscan_action_mesh_cd),
                                    tint = if (hasMesh) NeonGreen else NeonGreen.copy(alpha = 0.65f),
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Zone photo ───
            if (state.imageBitmap != null) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                    Image(
                        bitmap = state.imageBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp),
                        contentScale = ContentScale.Crop
                    )
                    if (state.isAnalyzing) BodyScanOverlay(Modifier.matchParentSize())
                    if (!state.isAnalyzing) {
                        Surface(
                            onClick = { viewModel.clear() },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color.White)
                            }
                        }
                    }
                }
            } else {
                BodyCaptureZone(
                    onCamera = { launchCamera() },
                    onGallery = { galleryLauncher.launch("image/*") }
                )
            }

            // ─── Bouton analyser ───
            if (state.imageBitmap != null && state.result == null && !state.isAnalyzing) {
                Button(
                    onClick = { viewModel.analyze() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bodyscan_btn_analyze), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            // ─── État analysing ───
            if (state.isAnalyzing) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.bodyscan_analyzing_title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                    Text(stringResource(R.string.bodyscan_analyzing_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center)
                }
            }

            // ─── Erreur ───
            state.error?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ─── Résultats : mesures éditables ───
            if (state.result != null || state.editHeightCm.isNotBlank()) {
                MeasurementsEditableCard(state, viewModel)
                BmiBodyFatCard(state)

                // Bouton appliquer au profil
                Button(
                    onClick = { viewModel.applyToProfile() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.applied) NeonGreen else OrangeVibrant
                    )
                ) {
                    Icon(
                        if (state.applied) Icons.Default.CheckCircle else Icons.Default.Save,
                        null, Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.applied) stringResource(R.string.bodyscan_btn_applied) else stringResource(R.string.bodyscan_btn_apply),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Générer mesh
                if (state.imageBitmap != null) {
                    GenerateMeshCard(state, viewModel, onViewMesh = {
                        navController.navigate(Screen.BodyMesh.route)
                    })
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════
// ZONE DE CAPTURE
// ═══════════════════════════════════════

@Composable
private fun BodyCaptureZone(onCamera: () -> Unit, onGallery: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.06f)),
        border = BorderStroke(1.5.dp, OrangeVibrant.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Accessibility, null, Modifier.size(56.dp), tint = OrangeVibrant)
            Text(stringResource(R.string.bodyscan_capture_title),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.bodyscan_capture_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onCamera,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = OrangeVibrant.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(20.dp), tint = OrangeVibrant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.bodyscan_btn_camera),
                        fontWeight = FontWeight.Bold,
                        color = OrangeVibrant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = onGallery,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Image, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.bodyscan_btn_gallery),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            // Note de confidentialité
            Text(
                stringResource(R.string.bodyscan_capture_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════
// SCAN OVERLAY (animation futuriste)
// ═══════════════════════════════════════

@Composable
private fun BodyScanOverlay(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "bodyScan")
    val scanY by inf.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "sl")
    val scanColor = NeonGreen
    val cornerColor = scanColor.copy(alpha = 0.8f)

    Box(modifier) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Canvas(Modifier.fillMaxSize()) {
            val lineY = size.height * scanY
            drawRect(brush = Brush.verticalGradient(
                listOf(Color.Transparent, scanColor.copy(alpha = 0.15f), scanColor.copy(alpha = 0.5f),
                    scanColor.copy(alpha = 0.15f), Color.Transparent),
                startY = (lineY - 70f).coerceAtLeast(0f), endY = lineY + 70f
            ))
            drawLine(scanColor, Offset(0f, lineY), Offset(size.width, lineY), 3f, cap = StrokeCap.Round)
        }
        // Corners
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val cs = 28.dp.toPx(); val sw = 3f
            drawLine(cornerColor, Offset(0f,0f), Offset(cs,0f), sw); drawLine(cornerColor, Offset(0f,0f), Offset(0f,cs), sw)
            drawLine(cornerColor, Offset(size.width,0f), Offset(size.width-cs,0f), sw); drawLine(cornerColor, Offset(size.width,0f), Offset(size.width,cs), sw)
            drawLine(cornerColor, Offset(0f,size.height), Offset(cs,size.height), sw); drawLine(cornerColor, Offset(0f,size.height), Offset(0f,size.height-cs), sw)
            drawLine(cornerColor, Offset(size.width,size.height), Offset(size.width-cs,size.height), sw); drawLine(cornerColor, Offset(size.width,size.height), Offset(size.width,size.height-cs), sw)
        }
    }
}

// ═══════════════════════════════════════
// CARD MESURES ÉDITABLES
// ═══════════════════════════════════════

@Composable
private fun MeasurementsEditableCard(state: BodyScannerState, viewModel: BodyScannerViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Straighten, null, Modifier.size(22.dp), tint = OrangeVibrant)
                Text(stringResource(R.string.bodyscan_card_measures_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (state.result != null) {
                    Spacer(Modifier.weight(1f))
                    ConfidenceBadge(state.result.confidence)
                }
            }

            // Sex selector
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("M" to stringResource(R.string.profile_sex_male), "F" to stringResource(R.string.profile_sex_female)).forEach { (code, label) ->
                    val sel = state.editSex == code
                    Surface(
                        onClick = { viewModel.setSex(code) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(label,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Height + Weight
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeasureField(stringResource(R.string.bodyscan_field_height), state.editHeightCm, "cm", Modifier.weight(1f)) { viewModel.setHeight(it) }
                MeasureField(stringResource(R.string.bodyscan_field_weight), state.editWeightKg, "kg", Modifier.weight(1f)) { viewModel.setWeight(it) }
            }

            // Waist + Chest
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeasureField(stringResource(R.string.profile_measure_waist), state.editWaistCm, "cm", Modifier.weight(1f)) { viewModel.setWaist(it) }
                MeasureField(stringResource(R.string.profile_measure_chest), state.editChestCm, "cm", Modifier.weight(1f)) { viewModel.setChest(it) }
            }

            // Hip + Arm
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeasureField(stringResource(R.string.profile_measure_hip), state.editHipCm, "cm", Modifier.weight(1f)) { viewModel.setHip(it) }
                MeasureField(stringResource(R.string.profile_measure_arm), state.editArmCm, "cm", Modifier.weight(1f)) { viewModel.setArm(it) }
            }

            // Thigh + Calf
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeasureField(stringResource(R.string.profile_measure_thigh), state.editThighCm, "cm", Modifier.weight(1f)) { viewModel.setThigh(it) }
                MeasureField(stringResource(R.string.profile_measure_calf), state.editCalfCm, "cm", Modifier.weight(1f)) { viewModel.setCalf(it) }
            }

            // Notes IA
            if (!state.result?.notes.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OrangeVibrant.copy(alpha = 0.08f)
                ) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = OrangeVibrant)
                        Text(state.result?.notes.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasureField(label: String, value: String, unit: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        suffix = { Text(unit, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    val (label, color) = when (confidence.lowercase()) {
        "high" -> stringResource(R.string.bodyscan_confidence_high) to NeonGreen
        "low" -> stringResource(R.string.bodyscan_confidence_low) to MaterialTheme.colorScheme.error
        else -> stringResource(R.string.bodyscan_confidence_medium) to OrangeVibrant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color)
    }
}

// ═══════════════════════════════════════
// CARD BMI + BODY FAT
// ═══════════════════════════════════════

@Composable
private fun BmiBodyFatCard(state: BodyScannerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // BMI
        Card(
            Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Speed, null, Modifier.size(16.dp), tint = OrangeVibrant)
                    Text(stringResource(R.string.bodyscan_bmi_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                }
                Text(
                    if (state.computedBmi > 0) String.format(java.util.Locale.getDefault(), "%.1f", state.computedBmi) else "—",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold
                )
                Text(state.bmiLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
        // Body fat
        Card(
            Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.PieChart, null, Modifier.size(16.dp), tint = NeonGreen)
                    Text(stringResource(R.string.bodyscan_bodyfat_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeonGreen)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        state.editBodyFatPercent.ifBlank { "—" },
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold
                    )
                    if (state.editBodyFatPercent.isNotBlank()) {
                        Text("%", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Text(stringResource(R.string.bodyscan_bodyfat_caption), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }
    }
}

// ═══════════════════════════════════════
// CARD GÉNÉRATION MESH
// ═══════════════════════════════════════

@Composable
private fun GenerateMeshCard(
    state: BodyScannerState,
    viewModel: BodyScannerViewModel,
    onViewMesh: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E27)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.GridOn, null, Modifier.size(22.dp), tint = Color(0xFF00E5FF))
                Column {
                    Text(stringResource(R.string.bodyscan_mesh_title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(stringResource(R.string.bodyscan_mesh_subtitle),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF00E5FF).copy(alpha = 0.7f))
                }
            }

            state.meshError?.let { error ->
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }

            if (state.meshFeaturesPath != null && !state.isGeneratingMesh) {
                // Déjà généré : bouton voir
                Button(
                    onClick = onViewMesh,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bodyscan_btn_view_mesh), fontWeight = FontWeight.Bold, color = Color.Black)
                }
                OutlinedButton(
                    onClick = { viewModel.generateMeshAndNavigate() },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = Color(0xFF00E5FF))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bodyscan_btn_regenerate), color = Color(0xFF00E5FF), style = MaterialTheme.typography.labelLarge)
                }
            } else if (state.isGeneratingMesh) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(stringResource(R.string.bodyscan_mesh_generating),
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF00E5FF))
                }
            } else {
                Button(
                    onClick = { viewModel.generateMeshAndNavigate() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bodyscan_btn_generate_mesh), fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Text(
                    stringResource(R.string.bodyscan_mesh_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
