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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoPath.isNotBlank()) {
            viewModel.updateProfilePhoto(pendingPhotoPath); snack("Photo de profil mise à jour")
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
                viewModel.updateProfilePhoto(file.absolutePath); snack("Photo de profil mise à jour")
            } catch (_: Exception) { snack("Erreur lors de l'import") }
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
    if (showPhotoChoice) {
        ModalBottomSheet(onDismissRequest = { showPhotoChoice = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Photo de profil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showPhotoChoice = false; launchCamera() }, Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                    Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(8.dp)); Text("Prendre une photo", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { showPhotoChoice = false; galleryLauncher.launch("image/*") }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choisir dans la galerie", fontWeight = FontWeight.Bold)
                }
                if (state.profile?.profilePhotoPath != null) {
                    TextButton(onClick = { showPhotoChoice = false; viewModel.updateProfilePhoto(""); snack("Photo supprimée") }, Modifier.fillMaxWidth()) {
                        Text("Supprimer la photo", color = Color(0xFFEF4444))
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
                Text("Nouvelle pesée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(state.newWeight, { viewModel.onNewWeightChanged(it) }, label = { Text("Poids (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.hideAddWeight() }, Modifier.weight(1f)) { Text("Annuler") }
                    Button(onClick = {
                        val w = state.newWeight
                        viewModel.addWeightLog()
                        snack("Pesée ajoutée : $w kg")
                    }, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) { Text("Enregistrer", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // BottomSheet suppression
    if (state.showDeleteConfirm) {
        ModalBottomSheet(onDismissRequest = { viewModel.hideDeleteConfirm() }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Supprimer toutes les données ?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Cette action est IRRÉVERSIBLE. Toutes tes séances, tes repas, ton profil seront supprimés.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { viewModel.hideDeleteConfirm() }, Modifier.weight(1f)) { Text("Annuler") }
                    Button(onClick = { viewModel.deleteAllData(context) }, Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Text("Tout supprimer", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    val tabTitles = listOf("Infos", "Poids", "Mesures")
    @OptIn(ExperimentalFoundationApi::class)
    val pagerState = rememberPagerState(pageCount = { 3 })

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mon Profil", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.Default.ArrowBack, "Retour") } },
            actions = {
                IconButton(onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Settings.route) }) { Icon(Icons.Default.Settings, "Paramètres") }
                // Menu overflow : Photos + Danger zone + Export
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Plus") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Photos progression") }, onClick = {
                        menuExpanded = false
                        navController.navigate(com.shredcoach.app.presentation.navigation.Screen.ProgressPhotos.route)
                    }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) })
                    DropdownMenuItem(text = { Text("Exporter / Sauvegarder") }, onClick = {
                        menuExpanded = false; viewModel.exportBackup(context); snack("Données exportées")
                    }, leadingIcon = { Icon(Icons.Default.Backup, null) })
                    Divider()
                    DropdownMenuItem(text = { Text("Supprimer les données", color = Color(0xFFEF4444)) }, onClick = {
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
                            contentDescription = "Photo de profil",
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
                    Text(displayName.ifBlank { "Ton nom" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    state.profile?.let { p ->
                        Text("${p.currentWeightKg} kg • ${p.heightCm} cm • ${p.age} ans", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                // Badge photo pour indication
                Icon(Icons.Default.CameraAlt, "Changer photo", Modifier.size(18.dp),
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
                            SectionCard("Informations", Icons.Default.Person) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(state.editFirstName, { viewModel.onFirstNameChanged(it) }, label = { Text("Prénom") },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(state.editLastName, { viewModel.onLastNameChanged(it) }, label = { Text("Nom") },
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                        modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(state.editAge, { viewModel.onAgeChanged(it) }, label = { Text("Âge") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(state.editHeight, { viewModel.onHeightChanged(it) }, label = { Text("Taille (cm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("M" to "Homme", "F" to "Femme").forEach { (code, label) ->
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
                                Button(onClick = { viewModel.saveProfile(); snack("Profil mis à jour") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)) {
                                    Icon(Icons.Default.Save, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Enregistrer", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Objectifs
                            SectionCard("Mes objectifs", Icons.Default.Flag) {
                                state.profile?.let { p ->
                                    Text("Niveau", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(FitnessLevel.BEGINNER to "Débutant", FitnessLevel.INTERMEDIATE to "Inter\nmédiaire", FitnessLevel.ADVANCED to "Avancé").forEach { (level, label) ->
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
                                    Text("Équipement", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(EquipmentType.FULL_GYM to "Salle\ncomplète", EquipmentType.HOME_GYM to "Home\ngym", EquipmentType.BODYWEIGHT to "Poids du\ncorps").forEach { (equip, label) ->
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
                                    Text("Objectif", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(FitnessGoal.SHRED to "Sèche", FitnessGoal.BULK to "Prise de\nmasse", FitnessGoal.MAINTAIN to "Maintien").forEach { (goal, label) ->
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
                        1 -> { // Poids
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Suivi du poids", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                FilledTonalButton(onClick = { viewModel.showAddWeight() }) {
                                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Peser")
                                }
                            }
                            state.profile?.let { p ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    WStat("Actuel", String.format(java.util.Locale.US, "%.1f kg", p.currentWeightKg), OrangeVibrant)
                                    WStat("Objectif", String.format(java.util.Locale.US, "%.1f kg", p.targetWeightKg), NeonGreen)
                                    WStat("Reste", String.format(java.util.Locale.US, "%.1f kg", abs(p.currentWeightKg - p.targetWeightKg)),
                                        if (p.currentWeightKg > p.targetWeightKg) Color(0xFFEF4444) else NeonGreen)
                                }
                                val wc = state.weeklyChange
                                if (abs(wc) > 0.01) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = (if (wc < 0) NeonGreen else Color(0xFFEF4444)).copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(if (wc < 0) Icons.Default.TrendingDown else Icons.Default.TrendingUp, null, tint = if (wc < 0) NeonGreen else Color(0xFFEF4444))
                                            Text("${if (wc > 0) "+" else ""}${String.format(java.util.Locale.US, "%.2f", wc)} kg/semaine", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                OutlinedTextField(state.editTargetWeight, { viewModel.onTargetWeightChanged(it) }, label = { Text("Objectif poids (kg)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                            if (state.weightLogs.size >= 2) {
                                WChart(state.weightLogs.sortedBy { it.date }.map { it.weightKg.toFloat() }, Modifier.fillMaxWidth().height(160.dp))
                            } else {
                                Card(colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.06f))) {
                                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.ShowChart, null, Modifier.size(32.dp), tint = OrangeVibrant.copy(alpha = 0.4f))
                                        Text("Pèse-toi pour commencer le suivi !", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        FilledTonalButton(onClick = { viewModel.showAddWeight() }) {
                                            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ma première pesée")
                                        }
                                    }
                                }
                            }
                            state.weightLogs.take(5).forEach { log ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.bodyMedium)
                                    Text("${log.weightKg} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                                }
                            }
                        }
                        2 -> { // Mesures
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
                                        Text("Body Scanner IA",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White)
                                        Text("Photo → mesures auto + visualisation 3D",
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
                                    Text("Mesure au mètre-ruban, le matin à jeun.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField("Tour de taille", state.editWaist, { viewModel.onMeasureChanged("waist", it) }, Modifier.weight(1f))
                                MField("Poitrine", state.editChest, { viewModel.onMeasureChanged("chest", it) }, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField("Bras", state.editArm, { viewModel.onMeasureChanged("arm", it) }, Modifier.weight(1f))
                                MField("Cuisse", state.editThigh, { viewModel.onMeasureChanged("thigh", it) }, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MField("Hanches", state.editHip, { viewModel.onMeasureChanged("hip", it) }, Modifier.weight(1f))
                                MField("Mollet", state.editCalf, { viewModel.onMeasureChanged("calf", it) }, Modifier.weight(1f))
                            }
                            Button(onClick = { viewModel.saveProfile(); snack("Mesures sauvegardées") }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)) {
                                Icon(Icons.Default.Save, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Sauvegarder mesures", fontWeight = FontWeight.Bold)
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

@Composable private fun WStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable private fun MField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value, onChange, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier, singleLine = true, suffix = { Text("cm", style = MaterialTheme.typography.labelSmall) })
}

@Composable private fun WChart(points: List<Float>, modifier: Modifier) {
    val lineColor = OrangeVibrant
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(8.dp)) {
        if (points.size < 2) return@Canvas
        val maxV = points.maxOrNull() ?: return@Canvas; val minV = points.minOrNull() ?: return@Canvas
        val range = (maxV - minV).coerceAtLeast(0.5f)
        val pL = 50f; val pT = 12f; val pB = 12f; val pR = 12f
        val cW = size.width - pL - pR; val cH = size.height - pT - pB
        val stepX = cW / (points.size - 1).coerceAtLeast(1)
        for (i in 0..3) {
            val y = pT + cH * (1f - i / 3f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(pL, y), Offset(size.width - pR, y))
            drawContext.canvas.nativeCanvas.drawText(String.format(java.util.Locale.US, "%.1f",minV + range * i / 3f), 2f, y + 4f,
                android.graphics.Paint().apply { color = 0x99FFFFFF.toInt(); textSize = 18f })
        }
        val path = Path()
        points.forEachIndexed { i, v -> val x = pL + i * stepX; val y = pT + cH * (1f - (v - minV) / range); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, lineColor, style = Stroke(3f, cap = StrokeCap.Round))
        points.forEachIndexed { i, v -> val x = pL + i * stepX; val y = pT + cH * (1f - (v - minV) / range)
            drawCircle(lineColor, 5f, Offset(x, y)); drawCircle(Color.White, 2.5f, Offset(x, y)) }
    }
}
