package com.shredcoach.app.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shredcoach.app.R
import com.shredcoach.app.data.local.entity.PhotoType
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressPhotosScreen(navController: NavController, viewModel: ProgressPhotosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var pendingPhotoType by remember { mutableStateOf(PhotoType.FRONT) }
    var pendingFilePath by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Permission caméra
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }

    // Launcher caméra
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingFilePath.isNotBlank()) {
            viewModel.onPhotoCaptured(pendingFilePath, pendingPhotoType)
        }
    }

    // Launcher galerie
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
                val file = File(photoDir, "progress_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.onPhotoCaptured(file.absolutePath, pendingPhotoType)
            } catch (_: Exception) {}
        }
    }

    fun launchCamera() {
        val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(photoDir, "progress_${System.currentTimeMillis()}.jpg")
        pendingFilePath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        cameraLauncher.launch(uri)
    }

    fun takePhoto(type: PhotoType) {
        pendingPhotoType = type
        // Vérifier permission caméra
        val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            launchCamera()
        } else {
            pendingAction = { launchCamera() }
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    fun pickFromGallery(type: PhotoType) {
        pendingPhotoType = type
        galleryLauncher.launch("image/*")
    }

    // Photo viewer dialog
    if (state.viewingPhoto != null) {
        PhotoViewerDialog(state.viewingPhoto!!, onDismiss = { viewModel.closeViewer() }, onDelete = { viewModel.deletePhoto(it) })
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.photos_screen_title), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } })
    }) { pad ->
        val cameraCd = stringResource(R.string.photos_btn_camera_cd)
        val galleryCd = stringResource(R.string.photos_btn_gallery_cd)
        val sectionCountTpl = stringResource(R.string.photos_section_count)
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            // Boutons capture
            item {
                Card(colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f))) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.photos_card_new_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.photos_card_new_desc), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PhotoType.values().forEach { type ->
                                Card(
                                    Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(type.displayNameRes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalIconButton(onClick = { takePhoto(type) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.CameraAlt, cameraCd, Modifier.size(18.dp))
                                            }
                                            FilledTonalIconButton(onClick = { pickFromGallery(type) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Default.Photo, galleryCd, Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Galerie par type
            PhotoType.values().forEach { type ->
                val photos = state.photos.filter { it.photoType == type }.sortedByDescending { it.date }
                if (photos.isNotEmpty()) {
                    item {
                        val typeName = stringResource(type.displayNameRes)
                        Text(String.format(sectionCountTpl, typeName, photos.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(photos, key = { it.id }) { photo ->
                                PhotoCard(photo, onClick = { viewModel.viewPhoto(photo) })
                            }
                        }
                    }
                }
            }

            // Comparaison avant/après
            if (state.photos.size >= 2) {
                item { ComparisonSection(state.photos) }
            }

            // Empty state motivant
            if (state.photos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().heightIn(min = 400.dp)) {
                        com.shredcoach.app.presentation.common.EmptyState(
                            icon = Icons.Default.PhotoCamera,
                            title = stringResource(R.string.photos_empty_title),
                            description = stringResource(R.string.photos_empty_desc),
                            ctaLabel = stringResource(R.string.photos_empty_cta),
                            ctaIcon = Icons.Default.CameraAlt,
                            onCtaClick = { launchCamera() }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Photo card ──
@Composable
private fun PhotoCard(photo: ProgressPhotoEntity, onClick: () -> Unit) {
    Card(Modifier.width(140.dp).clickable { onClick() }, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(photo.filePath)).crossfade(true).build(),
                contentDescription = stringResource(photo.photoType.displayNameRes),
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } },
                error = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.BrokenImage, null) } }
            )
            Column(Modifier.padding(8.dp)) {
                Text(photo.date.format(DateTimeFormatter.ofPattern("dd/MM/yy")), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (photo.weightAtTime > 0) Text("${photo.weightAtTime} kg", style = MaterialTheme.typography.labelSmall, color = OrangeVibrant)
            }
        }
    }
}

// ── Comparaison avant/après ──
@Composable
private fun ComparisonSection(photos: List<ProgressPhotoEntity>) {
    val sorted = photos.sortedBy { it.date }
    val oldest = sorted.first()
    val newest = sorted.last()

    val beforeLabel = stringResource(R.string.photos_comparison_before)
    val afterLabel = stringResource(R.string.photos_comparison_after)
    Text(stringResource(R.string.photos_comparison_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.fillMaxWidth().height(220.dp)) {
            // Avant
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(oldest.filePath)).crossfade(true).build(),
                    contentDescription = beforeLabel, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    error = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Text(beforeLabel) } }
                )
                Surface(Modifier.align(Alignment.BottomStart).padding(6.dp), shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Text(oldest.date.format(DateTimeFormatter.ofPattern("dd/MM/yy")), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            HorizontalDivider(Modifier.width(2.dp).fillMaxHeight(), color = OrangeVibrant)
            // Après
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(newest.filePath)).crossfade(true).build(),
                    contentDescription = afterLabel, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    error = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Text(afterLabel) } }
                )
                Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), shape = RoundedCornerShape(4.dp), color = NeonGreen.copy(alpha = 0.8f)) {
                    Text(newest.date.format(DateTimeFormatter.ofPattern("dd/MM/yy")), Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

// ── Photo viewer dialog ──
@Composable
private fun PhotoViewerDialog(photo: ProgressPhotoEntity, onDismiss: () -> Unit, onDelete: (ProgressPhotoEntity) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = {
        Card {
            Column(Modifier.fillMaxWidth()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(File(photo.filePath)).build(),
                    contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    contentScale = ContentScale.Fit,
                    error = { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Default.BrokenImage, null) } }
                )
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(photo.photoType.displayNameRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(photo.date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.getDefault())), style = MaterialTheme.typography.bodySmall)
                        if (photo.weightAtTime > 0) Text("${photo.weightAtTime} kg", style = MaterialTheme.typography.bodySmall, color = OrangeVibrant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { onDelete(photo); onDismiss() }) { Icon(Icons.Default.Delete, stringResource(R.string.photos_action_delete_cd), tint = Color(0xFFEF4444)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.photos_action_close)) }
                    }
                }
            }
        }
    })
}
