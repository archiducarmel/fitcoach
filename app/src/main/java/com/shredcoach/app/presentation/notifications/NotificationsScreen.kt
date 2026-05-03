package com.shredcoach.app.presentation.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.entity.AppNotificationEntity
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Tout supprimer ?", fontWeight = FontWeight.Bold) },
            text = { Text("${state.notifications.size} notifications seront définitivement supprimées.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAll(); showClearAllConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Tout supprimer") }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Annuler") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Notifications", fontWeight = FontWeight.Bold)
                        if (state.unreadCount > 0) {
                            Badge(containerColor = OrangeVibrant) {
                                Text("${state.unreadCount}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                actions = {
                    if (state.unreadCount > 0) {
                        TextButton(onClick = { viewModel.markAllAsRead() }) {
                            Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tout lu")
                        }
                    }
                    if (state.notifications.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, "Tout supprimer",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                CircularProgressIndicator(color = OrangeVibrant)
            }
        } else if (state.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.NotificationsNone, null,
                        Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    Text("Aucune notification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Shreddy t'enverra des débriefs ici après tes repas et séances.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp))
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.notifications, key = { it.id }) { notif ->
                    NotificationCard(
                        notif = notif,
                        onClick = { viewModel.markAsRead(notif.id) },
                        onDelete = { viewModel.delete(notif.id) }
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(
    notif: AppNotificationEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val type = runCatching { NotifType.valueOf(notif.type) }.getOrDefault(NotifType.OTHER)
    val accent = when (type) {
        NotifType.MEAL_DEBRIEF, NotifType.MEAL_REMINDER, NotifType.SHAKER_REMINDER -> OrangeVibrant
        NotifType.WORKOUT_DEBRIEF, NotifType.WORKOUT_REMINDER, NotifType.MOTIVATION -> NeonGreen
        NotifType.BEDTIME_REMINDER -> Color(0xFF8B5CF6) // purple
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface
                else accent.copy(alpha = 0.08f)
        ),
        border = if (notif.isRead) null else BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notif.isRead) 0.dp else 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icône type
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(accent.copy(alpha = if (notif.isRead) 0.08f else 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(type.icon, style = MaterialTheme.typography.titleMedium)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notif.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        lineHeight = 18.sp
                    )
                    if (!notif.isRead) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    }
                }
                Text(
                    notif.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (notif.isRead) 0.55f else 0.85f),
                    lineHeight = 16.sp
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            formatRelativeTime(notif.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        if (notif.source == "llm") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accent.copy(alpha = 0.15f)
                            ) {
                                Text("IA", Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                    IconButton(onClick = onDelete, Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Supprimer", Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: LocalDateTime): String {
    val now = LocalDateTime.now()
    val minutes = Duration.between(timestamp, now).toMinutes()
    return when {
        minutes < 1 -> "À l'instant"
        minutes < 60 -> "Il y a ${minutes}min"
        minutes < 1440 -> "Il y a ${minutes / 60}h"
        minutes < 2880 -> "Hier"
        else -> timestamp.format(DateTimeFormatter.ofPattern("dd MMM", Locale.FRANCE))
    }
}
