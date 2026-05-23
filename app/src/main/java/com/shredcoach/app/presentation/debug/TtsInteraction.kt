package com.shredcoach.app.presentation.debug

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.remote.TtsService
import java.io.File
import java.io.FileOutputStream

/**
 * UI TTS : text input + voice picker + format chips -> audio player MediaPlayer.
 */
@Composable
fun TtsInteraction(
    state: LlmDebugState,
    onSynthesize: (String, String, String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf("default") }
    var format by remember { mutableStateOf("mp3") }
    val formats = listOf("mp3", "wav", "opus", "flac", "aac")

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                "🔊 Synthèse vocale : convertit ton texte en audio (Magpie multilingual/flow/zeroshot).",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Texte à vocaliser") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            maxLines = 8,
            placeholder = { Text("Ex: Bonjour, je teste la synthèse vocale.") },
        )

        OutlinedTextField(
            value = voice,
            onValueChange = { voice = it },
            label = { Text("Voice ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("default, fr_FR_001, …") },
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Format audio", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(formats) { fmt ->
                    val sel = fmt == format
                    Surface(
                        onClick = { format = fmt },
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            fmt.uppercase(),
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
            onClick = { onSynthesize(text.trim(), voice, format) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = text.isNotBlank() && !state.isSending,
        ) {
            if (state.isSending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Génération…", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Headphones, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Générer l'audio", fontWeight = FontWeight.Bold)
            }
        }

        state.ttsResult?.let { result -> AudioPlayer(result) }
    }
}

@Composable
private fun AudioPlayer(result: TtsService.TtsResult) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Sauve l'audio en fichier temp pour le donner au MediaPlayer
    val audioFile = remember(result) {
        val ext = when {
            result.mimeType.contains("mpeg") -> "mp3"
            result.mimeType.contains("wav") -> "wav"
            result.mimeType.contains("opus") -> "opus"
            result.mimeType.contains("flac") -> "flac"
            result.mimeType.contains("aac") -> "aac"
            else -> "mp3"
        }
        File(context.cacheDir, "tts_${System.currentTimeMillis()}.$ext").apply {
            FileOutputStream(this).use { it.write(result.audioBytes) }
        }
    }
    var player by remember(audioFile) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableIntStateOf(0) }
    var positionMs by remember { mutableIntStateOf(0) }

    DisposableEffect(audioFile) {
        val mp = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            setOnPreparedListener { durationMs = it.duration }
            setOnCompletionListener { isPlaying = false; positionMs = duration }
            prepareAsync()
        }
        player = mp
        onDispose { mp.release(); audioFile.delete() }
    }

    // Polling progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let { positionMs = it.currentPosition }
            kotlinx.coroutines.delay(100)
        }
    }

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Audio généré",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Bouton play/pause
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            player?.let { mp ->
                                if (isPlaying) { mp.pause(); isPlaying = false }
                                else { mp.start(); isPlaying = true }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Lire",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = { if (durationMs > 0) positionMs / durationMs.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            formatMs(positionMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            formatMs(durationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Text(
                "${result.sizeBytes / 1024} KB · ${result.mimeType} · ${result.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun formatMs(ms: Int): String {
    val sec = ms / 1000
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
