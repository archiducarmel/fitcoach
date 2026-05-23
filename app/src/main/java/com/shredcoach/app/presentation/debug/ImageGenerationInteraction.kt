package com.shredcoach.app.presentation.debug

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.remote.ImageGenerationService

/**
 * UI IMAGE_GENERATION : prompt + size picker + generate -> image display
 * + bouton save to gallery.
 */
@Composable
fun ImageGenerationInteraction(
    state: LlmDebugState,
    onGenerate: (String, String, ByteArray?) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf("1024x1024") }
    val sizes = listOf("512x512", "768x768", "1024x1024", "1024x1792", "1792x1024")
    val context = LocalContext.current

    // Img2img : si le model accepte une image en input, on affiche un picker
    val acceptsImageInput = state.selectedModel?.info?.acceptsImageInput == true
    var sourceImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                val bytes = input.readBytes()
                sourceImageBytes = bytes
                sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                if (acceptsImageInput)
                    "🎨 Img2Img : transforme une image existante avec un prompt (FLUX.2 Klein, SD img2img)."
                else
                    "🎨 Génère une image à partir d'un prompt texte (FLUX, Stable Diffusion, Pollinations, etc.).",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        // ── Image source picker (visible uniquement si model.acceptsImageInput) ──
        if (acceptsImageInput) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Image source",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (sourceBitmap != null) {
                        Box(Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.Image(
                                bitmap = sourceBitmap!!.asImageBitmap(),
                                contentDescription = "Image source",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { imagePicker.launch("image/*") },
                                contentScale = ContentScale.Fit,
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    "${sourceBitmap!!.width}×${sourceBitmap!!.height} · tap pour changer",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color.White,
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sélectionner une image source", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        "Note : redimensionnée à ≤512×512 automatiquement avant envoi (contrainte modèle).",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(if (acceptsImageInput) "Prompt de transformation" else "Prompt") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            maxLines = 6,
            placeholder = {
                Text(
                    if (acceptsImageInput) "Ex: image transformée en peinture à l'huile, style Van Gogh"
                    else "Ex: A cyberpunk skyline at sunset, neon lights, ultra-detailed, 8K"
                )
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Taille",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sizes) { size ->
                    val sel = size == selectedSize
                    Surface(
                        onClick = { selectedSize = size },
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            size,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onGenerate(prompt.trim(), selectedSize, sourceImageBytes) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = prompt.isNotBlank() && !state.isSending &&
                (!acceptsImageInput || sourceImageBytes != null),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Génération… (peut prendre 10-30s)", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Générer l'image", fontWeight = FontWeight.Bold)
            }
        }

        state.imageResult?.let { result ->
            val bitmap = remember(result.imageBytes) {
                BitmapFactory.decodeByteArray(result.imageBytes, 0, result.imageBytes.size)
            }
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Image générée",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${result.sizeBytes / 1024} KB · ${result.latencyMs / 1000.0}s" +
                                    (result.seed?.let { " · seed=$it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        OutlinedButton(
                            onClick = { saveImageToGallery(context, result.imageBytes) },
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sauver", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun saveImageToGallery(context: android.content.Context, bytes: ByteArray) {
    val filename = "fitcoach_debug_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FitCoach")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return
    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
}
