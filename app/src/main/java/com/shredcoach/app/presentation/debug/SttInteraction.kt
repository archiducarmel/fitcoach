package com.shredcoach.app.presentation.debug

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.remote.SttService
import java.io.File
import java.io.FileOutputStream

/**
 * UI STT : audio file picker -> upload multipart -> transcript + segments.
 * V1 simplifie : file picker only (recording = V2, necessite permissions
 * RECORD_AUDIO + MediaRecorder).
 */
@Composable
fun SttInteraction(
    state: LlmDebugState,
    onTranscribe: (File, String, String?) -> Unit,
) {
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedMime by remember { mutableStateOf("audio/wav") }
    var language by remember { mutableStateOf("") }
    val context = LocalContext.current

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { audioUri ->
            // Copie le contenu dans un fichier cache (le multipart upload a besoin d'un File)
            val mime = context.contentResolver.getType(audioUri) ?: "audio/wav"
            val ext = when {
                mime.contains("wav") -> "wav"
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("m4a") || mime.contains("mp4") -> "m4a"
                mime.contains("ogg") || mime.contains("opus") -> "ogg"
                mime.contains("flac") -> "flac"
                else -> "audio"
            }
            val tempFile = File(context.cacheDir, "stt_input_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            pickedFile = tempFile
            pickedMime = mime
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
                "🎙️ Transcription audio : envoie un fichier audio (wav/mp3/m4a/ogg/flac), " +
                "obtient le texte et les timestamps (Whisper, Parakeet, Canary).",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        // File picker button
        OutlinedButton(
            onClick = { audioLauncher.launch("audio/*") },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.AudioFile, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (pickedFile != null) "Fichier sélectionné" else "Choisir un fichier audio",
                    fontWeight = FontWeight.Bold,
                )
                pickedFile?.let { f ->
                    Text(
                        "${f.name} · ${f.length() / 1024} KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        OutlinedTextField(
            value = language,
            onValueChange = { language = it.trim().lowercase() },
            label = { Text("Langue (ISO-639-1, optionnel — auto-détect si vide)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("fr, en, es, it, de, …") },
        )

        Button(
            onClick = {
                pickedFile?.let { onTranscribe(it, pickedMime, language.ifBlank { null }) }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = pickedFile != null && !state.isSending,
        ) {
            if (state.isSending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Transcription en cours…", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Transcrire", fontWeight = FontWeight.Bold)
            }
        }

        state.sttResult?.let { result -> TranscriptView(result) }
    }
}

@Composable
private fun TranscriptView(result: SttService.TranscriptionResult) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Stats
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Transcript", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    result.language?.let { lang ->
                        Text("🌍 $lang", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    result.durationSec?.let { d ->
                        Text("⏱ ${"%.1f".format(d)}s", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text("⚡ ${result.latencyMs}ms", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Full transcript
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Texte complet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    result.text,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                )
            }
        }

        // Segments si disponibles
        if (!result.segments.isNullOrEmpty()) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Segments (${result.segments.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    result.segments.forEach { seg ->
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "%.1fs".format(seg.startSec),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 2.dp).widthIn(min = 36.dp),
                            )
                            Text(
                                seg.text.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
