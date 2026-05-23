package com.shredcoach.app.presentation.debug

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    onGenerate: (String, String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf("1024x1024") }
    val sizes = listOf("512x512", "768x768", "1024x1024", "1024x1792", "1792x1024")
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                "🎨 Génère une image à partir d'un prompt texte (FLUX, Stable Diffusion).",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            maxLines = 6,
            placeholder = { Text("Ex: A cyberpunk skyline at sunset, neon lights, ultra-detailed, 8K") },
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
            onClick = { onGenerate(prompt.trim(), selectedSize) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = prompt.isNotBlank() && !state.isSending,
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
