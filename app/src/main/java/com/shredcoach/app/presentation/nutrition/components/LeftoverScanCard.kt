package com.shredcoach.app.presentation.nutrition.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.unit.dp
import com.shredcoach.app.R
import com.shredcoach.app.presentation.util.hapticClick
import java.io.File

/**
 * Card "J'ai pas fini mon plat" — scan des restes pour déduction calorique.
 *
 * 3 états :
 *  1. **Empty** : CTA "Scanner les restes" (galerie ou caméra) + sous-titre.
 *  2. **Analyzing** : photo preview + spinner + libellé "Analyse en cours…".
 *  3. **Resolved** : photo thumbnail à gauche, déductions à droite,
 *     bouton "Rescanner" + "Annuler la déduction".
 *
 * **Sécurité** : si la photo locale ne peut plus être lue (purgée par
 * l'utilisateur, RGPD, fichier corrompu), on dégrade gracieusement en
 * "icône" placeholder. Pas de crash.
 */
@Composable
fun LeftoverScanCard(
    leftoverPhotoPath: String?,
    leftoverCalories: Int,
    leftoverProteins: Double,
    leftoverCarbs: Double,
    leftoverFats: Double,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onScanLeftover: (Bitmap) -> Unit,
    onClearLeftover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasLeftover = leftoverCalories > 0 || leftoverPhotoPath != null

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeUriToBitmap(context, uri)
            if (bmp != null) onScanLeftover(bmp)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bmp: Bitmap? -> if (bmp != null) onScanLeftover(bmp) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Header ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.LunchDining,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (hasLeftover) R.string.meal_modifier_leftover_title_active
                            else R.string.meal_modifier_leftover_title
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            if (hasLeftover) R.string.meal_modifier_leftover_subtitle_active
                            else R.string.meal_modifier_leftover_subtitle
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                if (hasLeftover) {
                    IconButton(
                        onClick = {
                            hapticClick(context)
                            onClearLeftover()
                        },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.meal_modifier_leftover_clear_cd),
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            // ── Erreur OCR ──
            if (errorMessage != null && !isAnalyzing) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // ── Body selon état ──
            when {
                isAnalyzing -> LeftoverAnalyzingRow(leftoverPhotoPath)
                hasLeftover -> LeftoverResolvedRow(
                    leftoverPhotoPath = leftoverPhotoPath,
                    leftoverCalories = leftoverCalories,
                    leftoverProteins = leftoverProteins,
                    leftoverCarbs = leftoverCarbs,
                    leftoverFats = leftoverFats,
                    onRescanGallery = { galleryLauncher.launch("image/*") },
                    onRescanCamera = { cameraLauncher.launch(null) },
                )
                else -> LeftoverEmptyRow(
                    onPickGallery = {
                        hapticClick(context)
                        galleryLauncher.launch("image/*")
                    },
                    onPickCamera = {
                        hapticClick(context)
                        cameraLauncher.launch(null)
                    },
                )
            }
        }
    }
}

@Composable
private fun LeftoverEmptyRow(
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPickGallery,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
        ) {
            Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.meal_modifier_leftover_pick_gallery), fontWeight = FontWeight.Bold)
        }
        FilledTonalButton(
            onClick = onPickCamera,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.meal_modifier_leftover_pick_camera), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LeftoverAnalyzingRow(leftoverPhotoPath: String?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThumbnailBox(leftoverPhotoPath = leftoverPhotoPath, size = 64.dp)
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.meal_modifier_leftover_analyzing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun LeftoverResolvedRow(
    leftoverPhotoPath: String?,
    leftoverCalories: Int,
    leftoverProteins: Double,
    leftoverCarbs: Double,
    leftoverFats: Double,
    onRescanGallery: () -> Unit,
    onRescanCamera: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThumbnailBox(leftoverPhotoPath = leftoverPhotoPath, size = 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.meal_modifier_leftover_deducted_kcal, leftoverCalories),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(
                    R.string.meal_modifier_leftover_deducted_macros,
                    leftoverProteins.toInt(),
                    leftoverCarbs.toInt(),
                    leftoverFats.toInt(),
                ),
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                hapticClick(context)
                onRescanGallery()
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.meal_modifier_leftover_rescan), style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = {
                hapticClick(context)
                onRescanCamera()
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.meal_modifier_leftover_rescan_camera), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Thumbnail carrée. Fallback gracieux si le fichier n'existe pas (purge RGPD,
 * disque plein, etc.) → icône placeholder.
 */
@Composable
private fun ThumbnailBox(leftoverPhotoPath: String?, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val file = leftoverPhotoPath?.let { File(it) }
    val hasFile = file != null && file.exists()
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (hasFile) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(context).data(file).crossfade(true).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Default.Restaurant,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

private fun decodeUriToBitmap(context: android.content.Context, uri: Uri): Bitmap? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    }
} catch (_: Throwable) {
    null
}
