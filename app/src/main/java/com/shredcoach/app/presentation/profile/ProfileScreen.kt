package com.shredcoach.app.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.EquipmentType
import com.shredcoach.app.data.local.entity.FitnessGoal
import com.shredcoach.app.data.local.entity.FitnessLevel
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = com.shredcoach.app.presentation.navigation.LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    fun snack(msg: String) { scope.launch { snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short) } }

    // ── Launchers photo de profil (camera + galerie) ──
    var pendingPhotoPath by remember { mutableStateOf("") }
    var showPhotoChoice by remember { mutableStateOf(false) }
    val photoUpdatedMsg = stringResource(R.string.profile_snack_photo_updated)
    val photoImportErrorMsg = stringResource(R.string.profile_snack_import_error)
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoPath.isNotBlank()) {
            viewModel.updateProfilePhoto(pendingPhotoPath); snack(photoUpdatedMsg)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val photoDir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
                val file = java.io.File(photoDir, "profile_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.updateProfilePhoto(file.absolutePath); snack(photoUpdatedMsg)
            } catch (_: Exception) { snack(photoImportErrorMsg) }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val photoDir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
            val file = java.io.File(photoDir, "profile_${System.currentTimeMillis()}.jpg")
            pendingPhotoPath = file.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraLauncher.launch(uri)
        }
    }
    fun launchCamera() {
        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            val photoDir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
            val file = java.io.File(photoDir, "profile_${System.currentTimeMillis()}.jpg")
            pendingPhotoPath = file.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraLauncher.launch(uri)
        } else permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    // BottomSheet choix photo
    val photoRemovedMsg = stringResource(R.string.profile_snack_photo_removed)
    if (showPhotoChoice) {
        ModalBottomSheet(onDismissRequest = { showPhotoChoice = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.profile_photo_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showPhotoChoice = false; launchCamera() }, Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                    Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.profile_photo_sheet_camera), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { showPhotoChoice = false; galleryLauncher.launch("image/*") }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.profile_photo_sheet_gallery), fontWeight = FontWeight.Bold)
                }
                if (state.profile?.profilePhotoPath != null) {
                    TextButton(onClick = { showPhotoChoice = false; viewModel.updateProfilePhoto(""); snack(photoRemovedMsg) }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.profile_photo_sheet_remove), color = Color(0xFFEF4444))
                    }
                }
            }
        }
    }

    // Dialog ajout poids
    // BottomSheet pesée
    if (state.showAddWeight) {
        ModalBottomSheet(onDismissRequest = { viewModel.hideAddWeight() }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.profile_weight_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(state.newWeight, { viewModel.onNewWeightChanged(it) }, label = { Text(stringResource(R.string.profile_weight_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.hideAddWeight() }, Modifier.weight(1f)) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = {
                        val w = state.newWeight
                        viewModel.addWeightLog()
                        snack(context.getString(R.string.profile_snack_weight_added, w))
                    }, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // BottomSheet suppression
    if (state.showDeleteConfirm) {
        ModalBottomSheet(onDismissRequest = { viewModel.hideDeleteConfirm() }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.profile_delete_dialog_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.profile_delete_dialog_body),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.hideDeleteConfirm() }, Modifier.weight(1f)) { Text(stringResource(R.string.common_cancel)) }
                    Button(onClick = { viewModel.deleteAllData(context) }, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text(stringResource(R.string.profile_delete_dialog_confirm), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    val tabTitles = listOf(
        stringResource(R.string.profile_tab_infos),
        stringResource(R.string.profile_tab_weight),
        stringResource(R.string.profile_tab_measures)
    )
    @OptIn(ExperimentalFoundationApi::class)
    val pagerState = rememberPagerState(pageCount = { 3 })

    val exportedMsg = stringResource(R.string.profile_snack_exported)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_screen_title), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
            actions = {
                IconButton(onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Settings.route) }) { Icon(Icons.Default.Settings, stringResource(R.string.profile_action_settings_cd)) }
                // Menu overflow : Photos + Danger zone + Export
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.profile_action_more_cd)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.profile_menu_photos)) }, onClick = {
                        menuExpanded = false
                        navController.navigate(com.shredcoach.app.presentation.navigation.Screen.ProgressPhotos.route)
                    }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.profile_menu_export)) }, onClick = {
                        menuExpanded = false; viewModel.exportBackup(context); snack(exportedMsg)
                    }, leadingIcon = { Icon(Icons.Default.Backup, null) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.profile_menu_delete), color = Color(0xFFEF4444)) }, onClick = {
                        menuExpanded = false; viewModel.showDeleteConfirm()
                    }, leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFEF4444)) })
                }
            })
    }) { pad ->
        if (state.isLoading) { Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() } }
        else Column(Modifier.fillMaxSize().padding(pad)) {

            // ── Avatar compact (cliquable pour changer la photo) ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.2f))
                        .clickable { showPhotoChoice = true },
                    contentAlignment = Alignment.Center
                ) {
                    val photoPath = state.profile?.profilePhotoPath
                    if (photoPath != null) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(context).data(java.io.File(photoPath)).crossfade(true).build(),
                            contentDescription = stringResource(R.string.profile_photo_avatar_cd),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            state.editFirstName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = OrangeVibrant
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    val displayName = "${state.editFirstName.replaceFirstChar { it.uppercase() }} ${state.editLastName.replaceFirstChar { it.uppercase() }}".trim()
                    Text(displayName.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.profile?.let { p ->
                        Text(stringResource(R.string.profile_avatar_meta, p.currentWeightKg.toString(), p.heightCm, p.age),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                // Badge photo pour indication
                Icon(Icons.Default.CameraAlt, stringResource(R.string.profile_change_photo_cd), Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            // ── Tabs ──
            @OptIn(ExperimentalFoundationApi::class)
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = OrangeVibrant
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title, fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = OrangeVibrant,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // ── Pager ──
            @OptIn(ExperimentalFoundationApi::class)
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (page) {
                        0 -> { // Infos
                            val profileSavedMsg = stringResource(R.string.profile_snack_profile_saved)
                            SectionCard(stringResource(R.string.profile_section_info), Icons.Default.Person) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(state.editFirstName, { viewModel.onFirstNameChanged(it) }, label = { Text(stringResource(R.string.profile_field_firstname)) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(state.editLastName, { viewModel.onLastNameChanged(it) }, label = { Text(stringResource(R.string.profile_field_lastname)) },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(state.editAge, { viewModel.onAgeChanged(it) }, label = { Text(stringResource(R.string.profile_field_age)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(state.editHeight, { viewModel.onHeightChanged(it) }, label = { Text(stringResource(R.string.profile_field_height)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("M" to stringResource(R.string.profile_sex_male), "F" to stringResource(R.string.profile_sex_female)).forEach { (code, label) ->
                                        val sel = state.editSex == code
                                        Surface(
                                            onClick = { viewModel.onSexChanged(code) },
                                            modifier = Modifier.weight(1f).height(44.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (sel) OrangeVibrant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                            border = if (sel) BorderStroke(1.5.dp, OrangeVibrant) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                Button(onClick = { viewModel.saveProfile(); snack(profileSavedMsg) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                                    Icon(Icons.Default.Save, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
                                }
                            }

                            // Objectifs
                            SectionCard(stringResource(R.string.profile_section_goals), Icons.Default.Flag) {
                                state.profile?.let { p ->
                                    Text(stringResource(R.string.profile_label_level), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            FitnessLevel.BEGINNER to stringResource(R.string.profile_level_beginner),
                                            FitnessLevel.INTERMEDIATE to stringResource(R.string.profile_level_intermediate),
                                            FitnessLevel.ADVANCED to stringResource(R.string.profile_level_advanced)
                                        ).forEach { (level, label) ->
                                            val sel = p.level == level
                                            Surface(onClick = { viewModel.updateLevel(level) }, modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                                                shape = RoundedCornerShape(8.dp), color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant, tonalElevation = if (sel) 0.dp else 1.dp) {
                                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                    Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            }
                                        }
                                    }
                                    Text(stringResource(R.string.profile_label_equipment), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            EquipmentType.FULL_GYM to stringResource(R.string.profile_equip_full_gym),
                                            EquipmentType.HOME_GYM to stringResource(R.string.profile_equip_home_gym),
                                            EquipmentType.BODYWEIGHT to stringResource(R.string.profile_equip_bodyweight)
                                        ).forEach { (equip, label) ->
                                            val sel = p.equipment == equip
                                            Surface(onClick = { viewModel.updateEquipment(equip) }, modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                                                shape = RoundedCornerShape(8.dp), color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant, tonalElevation = if (sel) 0.dp else 1.dp) {
                                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                    Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            }
                                        }
                                    }
                                    Text(stringResource(R.string.profile_label_goal), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            FitnessGoal.SHRED to stringResource(R.string.profile_goal_shred),
                                            FitnessGoal.BULK to stringResource(R.string.profile_goal_bulk),
                                            FitnessGoal.MAINTAIN to stringResource(R.string.profile_goal_maintain)
                                        ).forEach { (goal, label) ->
                                            val sel = p.goal == goal
                                            Surface(onClick = { viewModel.updateGoal(goal) }, modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                                                shape = RoundedCornerShape(8.dp), color = if (sel) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant, tonalElevation = if (sel) 0.dp else 1.dp) {
                                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                                    Text(label, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> { // Poids — refonte premium dans WeightTrackingWidgets
                            WeightTrackingTab(state = state, viewModel = viewModel)
                        }
                        2 -> { // Mesures
                            val measuresSavedMsg = stringResource(R.string.profile_snack_measures_saved)
                            // ─── CTA Body Scanner (premium IA) ───
                            Card(
                                onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.BodyScanner.route) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF000814)),
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                androidx.compose.ui.graphics.Brush.radialGradient(
                                                    listOf(Color(0xFF00E5FF).copy(alpha = 0.4f), Color.Transparent)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Accessibility, null, Modifier.size(26.dp), tint = Color(0xFF00E5FF))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.profile_bodyscan_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White)
                                        Text(stringResource(R.string.profile_bodyscan_subtitle),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF00E5FF).copy(alpha = 0.7f))
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF00E5FF))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                                    Text(stringResource(R.string.profile_measure_hint), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField(stringResource(R.string.profile_measure_waist), state.editWaist, { viewModel.onMeasureChanged("waist", it) }, Modifier.weight(1f))
                                MField(stringResource(R.string.profile_measure_chest), state.editChest, { viewModel.onMeasureChanged("chest", it) }, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField(stringResource(R.string.profile_measure_arm), state.editArm, { viewModel.onMeasureChanged("arm", it) }, Modifier.weight(1f))
                                MField(stringResource(R.string.profile_measure_thigh), state.editThigh, { viewModel.onMeasureChanged("thigh", it) }, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField(stringResource(R.string.profile_measure_hip), state.editHip, { viewModel.onMeasureChanged("hip", it) }, Modifier.weight(1f))
                                MField(stringResource(R.string.profile_measure_calf), state.editCalf, { viewModel.onMeasureChanged("calf", it) }, Modifier.weight(1f))
                            }
                            Button(onClick = { viewModel.saveProfile(); snack(measuresSavedMsg) }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) {
                                Icon(Icons.Default.Save, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.profile_btn_save_measures), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ═══ Composants ═══

@Composable private fun Pill(text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = OrangeVibrant)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable private fun MField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier, singleLine = true, suffix = { Text(stringResource(R.string.profile_measure_unit_cm), style = MaterialTheme.typography.labelSmall) })
}
