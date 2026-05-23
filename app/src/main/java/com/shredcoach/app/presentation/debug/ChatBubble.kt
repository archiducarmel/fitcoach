package com.shredcoach.app.presentation.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import com.shredcoach.app.data.remote.LlmProvider

/**
 * Bulle de chat premium FAANG-grade.
 *
 *  - User : gradient orange a droite, ombre subtile, texte blanc
 *  - Assistant : surface neutre a gauche, avatar provider en cercle + nom modele
 *    en header, metadata footer en tnum gris discret
 *  - Streaming : caret pulsant a la fin du texte courant
 *  - Erreur : bordure rouge + icon + message inline + bouton retry (TODO)
 */
@Composable
fun ChatBubble(message: DebugChatMessage, isUser: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message.timestampMs) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            if (!isUser) {
                ProviderAvatar(message.provider, size = 30.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier.widthIn(max = 320.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                // Bubble body
                Surface(
                    shape = if (isUser)
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    else
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
                    color = Color.Transparent,
                    shadowElevation = 1.dp,
                    border = if (message.error != null) androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ) else null,
                ) {
                    Column(
                        Modifier
                            .background(
                                if (isUser) Brush.linearGradient(
                                    listOf(Color(0xFFFF8A65), Color(0xFFFF7043))
                                )
                                else Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    )
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        // Image (si attachee)
                        message.imageBytes?.let { bytes ->
                            val bmp = remember(bytes) {
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            bmp?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Image attachée",
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .size(width = 200.dp, height = 150.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        }
                        // Text content
                        val displayText = when {
                            message.text.isBlank() && message.isStreaming -> "•••"
                            message.text.isBlank() && message.error == null -> ""
                            else -> message.text
                        }
                        if (displayText.isNotEmpty()) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                lineHeight = 21.sp,
                            )
                        }
                        // Streaming caret
                        if (message.isStreaming && message.text.isNotEmpty()) {
                            Box(
                                Modifier
                                    .padding(top = 4.dp)
                                    .size(width = 2.dp, height = 14.dp)
                                    .background(
                                        if (isUser) Color.White else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                        // Error state
                        if (message.error != null) {
                            Row(
                                Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline, null,
                                    Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    message.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                // Metadata footer (assistant uniquement)
                if (!isUser && !message.isStreaming && message.text.isNotBlank()) {
                    MetadataFooter(message)
                }
            }
            if (isUser) {
                Spacer(Modifier.width(8.dp))
                // Placeholder pour symetrie visuelle. On peut mettre l'avatar user si on veut.
                Spacer(Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun MetadataFooter(message: DebugChatMessage) {
    val total = message.tokensInput + message.tokensOutput
    val latencyStr = "${message.latencyMs} ms"
    Row(
        Modifier.padding(top = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${message.provider.displayName} · ${shortenModelId(message.model)}",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        )
        Text(
            "·",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Text(
            "↓${message.tokensInput} ↑${message.tokensOutput}",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            "·",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Text(
            latencyStr,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

private fun shortenModelId(id: String): String {
    // "publisher/long-model-name-instruct-v1" → "long-model"
    val name = id.substringAfter('/', id)
    return if (name.length > 24) name.take(22) + "…" else name
}
