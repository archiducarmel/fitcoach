package com.shredcoach.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.domain.llm.LlmFallbackBus
import com.shredcoach.app.domain.llm.LlmFallbackEvent
import com.shredcoach.app.domain.llm.LlmFallbackMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Banner top-anchored qui s'affiche brievement (~4s) quand le LLM bascule sur
 * son fallback. Ecoute le [LlmFallbackBus] singleton.
 *
 * **Design FAANG** :
 *  - Glassmorphic + degrade subtil (orange chaud = "attention sans panique")
 *  - Slide in/out vertical pour ne pas surprendre
 *  - Auto-dismiss apres 4s
 *  - Icon SwapHoriz signale le swap de service
 *  - Message localise + humoristique (cf. LlmFallbackMessages)
 *
 * **Placement** : a integrer dans le Scaffold de chaque MainScreen.kt /
 * activite hote (au top, sous la TopAppBar). Ne capture pas les inputs.
 */
@Composable
fun LlmFallbackBanner(
    bus: LlmFallbackBus,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<LlmFallbackEvent?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bus) {
        bus.events.collectLatest { event ->
            current = event
            delay(4_500)
            current = null
        }
    }

    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val event = current ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFB74D).copy(alpha = 0.95f),
                                Color(0xFFFF8A65).copy(alpha = 0.95f),
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Icone rond avec swap
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White,
                    )
                }
                Text(
                    text = LlmFallbackMessages.shortMessage(event),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
