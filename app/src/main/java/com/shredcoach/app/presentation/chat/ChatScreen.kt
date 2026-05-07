package com.shredcoach.app.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.data.local.dao.ConversationSummary
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.presentation.common.EmptyState
import com.shredcoach.app.presentation.common.MarkdownText
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll UNE SEULE FOIS quand un nouvel item apparaît (message ou bulle streaming)
    // PAS de scroll pendant le streaming (l'utilisateur peut scroller librement)
    val itemCount = state.messages.size + (if (state.streamingText.isNotBlank() || state.isLoading) 1 else 0)
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem((itemCount - 1).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                com.shredcoach.app.presentation.common.ShredCoachLogo(size = 22.dp)
                            }
                        }
                        Column {
                            Text(stringResource(R.string.chat_assistant_name), fontWeight = FontWeight.Bold)
                            Text(
                                if (state.isLoading) stringResource(R.string.chat_assistant_writing)
                                    else stringResource(R.string.chat_assistant_subtitle, state.providerName),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isLoading) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Bouton nouvelle conversation
                    IconButton(onClick = { viewModel.startNewConversation() }) {
                        Icon(Icons.Default.NoteAdd, stringResource(R.string.chat_new_conversation_cd),
                            tint = OrangeVibrant)
                    }
                    // Bouton historique des conversations
                    IconButton(onClick = { viewModel.toggleConversationList() }) {
                        Icon(
                            if (state.showConversationList) Icons.Default.ChatBubble else Icons.Default.Forum,
                            stringResource(R.string.chat_conversations_cd)
                        )
                    }
                    // Menu
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.chat_options_cd)) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_clear)) },
                            onClick = { viewModel.clearHistory(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_menu_settings)) },
                            onClick = {
                                navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Settings.route)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // ─── Panel latéral conversations (overlay) ───
            AnimatedVisibility(visible = state.showConversationList) {
                ConversationListPanel(
                    conversations = state.conversations,
                    currentId = state.currentConversationId,
                    onOpen = { viewModel.openConversation(it) },
                    onDelete = { viewModel.deleteConversation(it) },
                    onNewConversation = { viewModel.startNewConversation() }
                )
            }

            // ─── Chat principal ───
            AnimatedVisibility(visible = !state.showConversationList, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.messages.isEmpty() && !state.isLoading) {
                        item {
                            WelcomeCard(onSuggestionTap = { suggestion ->
                                viewModel.onInputChanged(suggestion)
                                viewModel.sendMessage()
                            })
                        }
                    }

                    items(state.messages, key = { it.id }) { message ->
                        ChatBubble(message)
                    }

                    if (state.streamingText.isNotBlank()) {
                        item { StreamingBubble(text = state.streamingText) }
                    } else if (state.isLoading) {
                        item { TypingIndicator() }
                    }
                }
            }

            // ─── Barre de saisie (toujours visible) ───
            if (!state.showConversationList) {
                ChatInputBar(
                    value = state.inputText,
                    onValueChange = { viewModel.onInputChanged(it) },
                    onSend = { viewModel.sendMessage() },
                    isLoading = state.isLoading
                )
            }
        }
    }
}

// ═══════════════════════════════════════
// CONVERSATION LIST PANEL
// ═══════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListPanel(
    conversations: List<ConversationSummary>,
    currentId: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewConversation: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.chat_conversations_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.chat_new_conversation_short), fontWeight = FontWeight.Bold)
            }
        }

        if (conversations.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Forum,
                title = stringResource(R.string.chat_conversations_empty_title),
                description = stringResource(R.string.chat_conversations_empty_desc),
                ctaLabel = stringResource(R.string.chat_conversations_empty_cta),
                ctaIcon = Icons.Default.Add,
                onCtaClick = onNewConversation
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversations, key = { it.conversationId }) { convo ->
                    val isActive = convo.conversationId == currentId
                    ConversationCard(convo, isActive, onOpen = { onOpen(convo.conversationId) },
                        onDelete = { onDelete(convo.conversationId) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationCard(
    convo: ConversationSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val title = convo.firstUserMessage?.take(60) ?: stringResource(R.string.chat_conversation_default_title)
    val dateStr = try {
        LocalDateTime.parse(convo.lastTimestamp).format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"))
    } catch (_: Exception) { "" }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) OrangeVibrant.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, OrangeVibrant.copy(alpha = 0.4f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icône
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) OrangeVibrant.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(20.dp),
                    tint = if (isActive) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dateStr, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                    Text(stringResource(R.string.chat_conversation_msg_count, convo.messageCount), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }
            // Delete — IconButton garde sa taille default 48dp pour respecter
            // le min touch target WCAG AA. L'icône reste petite (16dp) au centre.
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, stringResource(R.string.chat_conversation_delete_cd), Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}

// ═══════════════════════════════════════
// WELCOME CARD
// ═══════════════════════════════════════

@Composable
private fun WelcomeCard(onSuggestionTap: (String) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = OrangeVibrant.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            com.shredcoach.app.presentation.common.ShredCoachLogo(size = 56.dp)
            Text(stringResource(R.string.chat_welcome_title),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.chat_welcome_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SuggestionChip(stringResource(R.string.chat_welcome_suggestion_1), onSuggestionTap)
                SuggestionChip(stringResource(R.string.chat_welcome_suggestion_2), onSuggestionTap)
                SuggestionChip(stringResource(R.string.chat_welcome_suggestion_3), onSuggestionTap)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionChip(text: String, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(text) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Icon(Icons.Default.Send, null, Modifier.size(14.dp),
                tint = OrangeVibrant.copy(alpha = 0.5f))
        }
    }
}

// ═══════════════════════════════════════
// MESSAGE BUBBLE (avec Markdown)
// ═══════════════════════════════════════

@Composable
private fun ChatBubble(message: ChatMessageEntity) {
    val isUser = message.role == "user"
    val bubbleColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> OrangeVibrant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            if (!isUser) {
                Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Surface(shape = shape, color = bubbleColor, tonalElevation = if (isUser) 0.dp else 1.dp,
                modifier = if (isUser) Modifier else Modifier.weight(1f, fill = false)) {
                if (isUser) {
                    // User → texte simple
                    Text(message.content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium, color = textColor, lineHeight = 20.sp)
                } else {
                    // Assistant → rendu Markdown
                    MarkdownText(
                        text = message.content,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = textColor
                    )
                }
            }
        }

        Text(
            message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 2.dp)
        )
    }
}

// ═══════════════════════════════════════
// STREAMING BUBBLE (token-by-token + Markdown live)
// ═══════════════════════════════════════

@Composable
private fun StreamingBubble(text: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth(0.88f)) {
            Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                    MarkdownText(
                        text = text,
                        modifier = Modifier.weight(1f, fill = false),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Curseur clignotant
                    val inf = rememberInfiniteTransition(label = "cursor")
                    val alpha by inf.animateFloat(1f, 0f,
                        infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "ca")
                    Box(Modifier.padding(start = 2.dp).width(2.dp).height(16.dp)
                        .background(OrangeVibrant.copy(alpha = alpha), RoundedCornerShape(1.dp)))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// TYPING INDICATOR (3 dots bounce)
// ═══════════════════════════════════════

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
        Surface(shape = CircleShape, color = OrangeVibrant.copy(alpha = 0.12f), modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                com.shredcoach.app.presentation.common.ShredCoachLogo(size = 16.dp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { index ->
                    val inf = rememberInfiniteTransition(label = "dot$index")
                    val offset by inf.animateFloat(0f, -6f,
                        infiniteRepeatable(tween(400, delayMillis = index * 120, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse), label = "do$index")
                    Box(Modifier.size(8.dp).offset(y = offset.dp)
                        .clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.6f)))
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// INPUT BAR
// ═══════════════════════════════════════

@Composable
private fun ChatInputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, isLoading: Boolean) {
    Surface(tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 120.dp),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder), style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() }),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrangeVibrant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = OrangeVibrant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.Send, stringResource(R.string.chat_send_cd), Modifier.size(20.dp))
            }
        }
    }
}
