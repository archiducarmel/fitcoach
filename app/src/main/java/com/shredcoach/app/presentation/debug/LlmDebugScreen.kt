package com.shredcoach.app.presentation.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.domain.llm.LlmModelInfo
import com.shredcoach.app.domain.llm.ModelKind
import kotlinx.coroutines.launch

/**
 * Playground IA : page perenne multi-modale qui permet d'interagir avec
 * n'importe quel modele IA integre (LANGUAGE/VLM/Embedding/ImageGen/TTS/STT).
 *
 * Routing UI selon `selectedModel.kind` -> sous-ecran dedie.
 *
 * **Polish FAANG-grade** :
 *  - Header sticky avec model picker (chip tap → bottom sheet plein ecran)
 *  - Bulles chat gradient orange (user) / surface (assistant)
 *  - Footer metadata par message : provider/model/tokens/latency
 *  - Auto-scroll smart (s'arrete si user scroll up)
 *  - Empty state illustre avec CTA
 *  - Inline error states avec retry
 *  - Animations slide-in/fade pour les messages
 *  - Bouton attach image visible uniquement si model.acceptsImageInput
 *
 * NOTE: les fichiers du package `presentation/debug/` ne sont pas renommes
 * pour preserver l'historique Git. Le nom "debug" est purement interne — UI
 * et nav route exposees a l'user disent "Playground" partout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmDebugScreen(
    navController: NavController,
    viewModel: LlmDebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showApiKeyDialog by remember { mutableStateOf<SecureKeyStore.Provider?>(null) }

    Scaffold(
        topBar = {
            DebugTopBar(
                state = state,
                onBack = { navController.navigateUp() },
                onModelPickerClick = { viewModel.togglePicker() },
                onApiKeyEntry = { provider -> showApiKeyDialog = provider },
                onReset = { viewModel.resetSession() },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // PAS de .imePadding() ici : Scaffold + enableEdgeToEdge gerent l'IME
            // au niveau du systeme. Les InputBars de chaque sous-page (ChatInputBar,
            // EmbeddingInput, etc.) appliquent .imePadding() localement.
            // ── Bandeau d'erreur global (catalogError + lastError) ─────────────
            // Visible immediatement (pas confine au bottom sheet). Sans ca,
            // l'utilisateur ne voyait pas pourquoi rien ne fonctionnait.
            Column(Modifier.fillMaxSize()) {
                val displayedError = state.lastError ?: state.catalogError
                AnimatedVisibility(
                    visible = displayedError != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    displayedError?.let { err ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            tonalElevation = 2.dp,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("⚠️", fontSize = 16.sp)
                                Text(
                                    err,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                                IconButton(
                                    onClick = { viewModel.dismissError() },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close, "Fermer",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                // Zone contenu : weight=1f recoit la hauteur restante (apres banner + IME)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val selected = state.selectedModel
                    when {
                        selected == null -> EmptyState(
                            state = state,
                            onPickModel = { viewModel.togglePicker() },
                            onConfigureKey = { showApiKeyDialog = it },
                        )
                        selected.info.kind == ModelKind.LANGUAGE ||
                            selected.info.kind == ModelKind.VLM ||
                            selected.info.kind == ModelKind.REWARD_MODEL ->
                                ChatInteraction(
                                    state = state,
                                    onSend = { text, image -> viewModel.sendMessage(text, image) },
                                    onCancel = { viewModel.cancelStream() },
                                )
                        selected.info.kind == ModelKind.EMBEDDING ||
                            selected.info.kind == ModelKind.MULTIMODAL_EMBEDDING ->
                                EmbeddingInteraction(
                                    state = state,
                                    onGenerate = { text, imgBytes -> viewModel.generateEmbedding(text, imgBytes) },
                                    onClear = { viewModel.clearKindResults() },
                                )
                        selected.info.kind == ModelKind.IMAGE_GENERATION ->
                            ImageGenerationInteraction(
                                state = state,
                                onGenerate = { prompt, size, sourceBytes ->
                                    viewModel.generateImage(prompt, size, sourceBytes)
                                },
                            )
                        selected.info.kind == ModelKind.TTS ->
                            TtsInteraction(
                                state = state,
                                onSynthesize = { text, voice, format -> viewModel.synthesizeTts(text, voice, format) },
                            )
                        selected.info.kind == ModelKind.STT ->
                            SttInteraction(
                                state = state,
                                onTranscribe = { file, mime, lang -> viewModel.transcribeAudio(file, mime, lang) },
                            )
                        else -> UnsupportedKindPlaceholder(selected.info.kind)
                    }
                }
            }
        }

        if (state.pickerOpen) {
            ModelPickerBottomSheet(
                state = state,
                onDismiss = { viewModel.togglePicker() },
                onSelectModel = { viewModel.selectModel(it) },
                onSearch = { viewModel.setPickerSearch(it) },
                onFilterKind = { viewModel.setPickerKindFilter(it) },
                onFilterProvider = { viewModel.setPickerProviderFilter(it) },
                onToggleHideGated = { viewModel.togglePickerHideGated() },
                onRefreshCatalog = { viewModel.refreshAllCatalogs() },
            )
        }

        showApiKeyDialog?.let { provider ->
            ApiKeyEntryDialog(
                provider = provider,
                isAlreadySet = state.apiKeyAvailable[provider] == true,
                onSave = { key ->
                    viewModel.saveApiKey(provider, key)
                    showApiKeyDialog = null
                },
                onClear = {
                    viewModel.clearApiKey(provider)
                    showApiKeyDialog = null
                },
                onDismiss = { showApiKeyDialog = null },
            )
        }
    }

    // Auto-dismiss lastError apres 6s (catalogError reste : c'est un etat
    // persistant que l'user dismiss explicitement avec la croix).
    state.lastError?.let { err ->
        LaunchedEffect(err) {
            kotlinx.coroutines.delay(6000)
            viewModel.dismissError()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TOP BAR
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugTopBar(
    state: LlmDebugState,
    onBack: () -> Unit,
    onModelPickerClick: () -> Unit,
    onApiKeyEntry: (SecureKeyStore.Provider) -> Unit,
    onReset: () -> Unit,
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    stringResource(com.shredcoach.app.R.string.playground_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                }
            },
            actions = {
                if (state.messages.isNotEmpty()) {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.DeleteSweep, "Nouveau chat",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    }
                }
                IconButton(onClick = { onApiKeyEntry(SecureKeyStore.Provider.GITHUB_MODELS) }) {
                    Box {
                        Icon(Icons.Default.Key, "API keys")
                        val hasGh = state.apiKeyAvailable[SecureKeyStore.Provider.GITHUB_MODELS] == true
                        val hasNv = state.apiKeyAvailable[SecureKeyStore.Provider.NVIDIA_NIM] == true
                        if (hasGh || hasNv) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                        }
                    }
                }
            },
        )
        // Model picker bar : visible toujours pour pouvoir changer rapidement
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onModelPickerClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val sel = state.selectedModel
                if (sel != null) {
                    ProviderAvatar(sel.provider, size = 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            sel.info.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                sel.info.kind.emoji + " " + sel.info.kind.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "·",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                            Text(
                                sel.provider.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "Choisir un modèle",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(
    state: LlmDebugState,
    onPickModel: () -> Unit,
    onConfigureKey: (SecureKeyStore.Provider) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("🧪", fontSize = 48.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Playground IA",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Teste tous les modèles intégrés en un endroit unique : chat, vision, embeddings, image, TTS, STT.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPickModel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Choisir un modèle", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        // Quick-access API key buttons (2 rangees pour ne pas trop charger)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ApiKeyShortcut(
                label = "Token GitHub",
                isSet = state.apiKeyAvailable[SecureKeyStore.Provider.GITHUB_MODELS] == true,
                modifier = Modifier.weight(1f),
                onClick = { onConfigureKey(SecureKeyStore.Provider.GITHUB_MODELS) },
            )
            ApiKeyShortcut(
                label = "Clé NVIDIA",
                isSet = state.apiKeyAvailable[SecureKeyStore.Provider.NVIDIA_NIM] == true,
                modifier = Modifier.weight(1f),
                onClick = { onConfigureKey(SecureKeyStore.Provider.NVIDIA_NIM) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ApiKeyShortcut(
                label = "CF Token",
                isSet = state.apiKeyAvailable[SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN] == true,
                modifier = Modifier.weight(1f),
                onClick = { onConfigureKey(SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN) },
            )
            ApiKeyShortcut(
                label = "CF Account",
                isSet = state.apiKeyAvailable[SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID] == true,
                modifier = Modifier.weight(1f),
                onClick = { onConfigureKey(SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "🌸 Pollinations ne nécessite pas de clé.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ApiKeyShortcut(label: String, isSet: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Default.Key, null, Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (isSet) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// CHAT INTERACTION (CHAT + VLM)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatInteraction(
    state: LlmDebugState,
    onSend: (String, Bitmap?) -> Unit,
    onCancel: () -> Unit,
) {
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    val supportsImage = state.selectedModel?.info?.acceptsImageInput == true

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                attachedBitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    // Auto-scroll vers le bas quand un nouveau message arrive
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            scope.launch { scrollState.animateScrollToItem(state.messages.lastIndex) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Messages list (weight 1f)
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { ChatEmptyState(state.selectedModel?.info) }
            } else {
                items(state.messages, key = { "${it.timestampMs}_${it.role}" }) { msg ->
                    ChatBubble(msg, isUser = msg.role == "user")
                }
            }
        }

        // Input bar sticky en bas
        ChatInputBar(
            input = input,
            onInputChange = { input = it },
            attachedBitmap = attachedBitmap,
            onClearAttachment = { attachedBitmap = null },
            supportsImage = supportsImage,
            onPickImage = { imageLauncher.launch("image/*") },
            isSending = state.isSending,
            onSend = {
                onSend(input.trim(), attachedBitmap)
                input = ""
                attachedBitmap = null
            },
            onCancel = onCancel,
        )
    }
}

@Composable
private fun ChatEmptyState(info: LlmModelInfo?) {
    if (info == null) return
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(info.kind.emoji, fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            info.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                info.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Tape un message ci-dessous pour démarrer",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    attachedBitmap: Bitmap?,
    onClearAttachment: () -> Unit,
    supportsImage: Boolean,
    onPickImage: () -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Image attachment preview
            AnimatedVisibility(
                visible = attachedBitmap != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
            ) {
                attachedBitmap?.let { bmp ->
                    Box(
                        Modifier
                            .padding(bottom = 6.dp, start = 4.dp)
                            .size(width = 72.dp, height = 56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Image attachée",
                            modifier = Modifier.fillMaxSize(),
                        )
                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(Icons.Default.Close, "Retirer image", Modifier.size(12.dp), tint = Color.White)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (supportsImage) {
                    IconButton(
                        onClick = onPickImage,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, "Attacher image",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…", style = MaterialTheme.typography.bodyMedium) },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                val canSend = input.isNotBlank() || attachedBitmap != null
                val sendButtonScale by animateFloatAsState(
                    targetValue = if (canSend) 1f else 0.85f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "send_btn_scale",
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(sendButtonScale)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(
                                listOf(Color(0xFFFF8A65), Color(0xFFFF7043))
                            ) else Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                        )
                        .clickable(enabled = !isSending || canSend) {
                            if (isSending) onCancel() else if (canSend) onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSending) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, "Envoyer",
                            tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════
// UNSUPPORTED KIND PLACEHOLDER (pour Commit F)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun UnsupportedKindPlaceholder(kind: ModelKind) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(kind.emoji, fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "UI ${kind.name} en construction",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Cette modalité (${kind.name}) sera interactive dans le prochain commit. Le service backend est déjà en place.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// API KEY ENTRY DIALOG
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ApiKeyEntryDialog(
    provider: SecureKeyStore.Provider,
    isAlreadySet: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var showValue by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Fix #5 : l'Account ID Cloudflare est public (32 chars hex visible sur
    // dashboard), pas un secret. Ne pas le masquer = meilleure UX (l'user
    // peut verifier sa saisie). Les vrais tokens restent masques.
    val isSecret = provider != SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID
    val titlePrefix = when (provider) {
        SecureKeyStore.Provider.GITHUB_MODELS -> "🐙 Token GitHub"
        SecureKeyStore.Provider.NVIDIA_NIM -> "🟢 Clé NVIDIA"
        SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN -> "🟠 Clé Cloudflare AI"
        SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID -> "🟠 Cloudflare Account ID"
        else -> "Clé ${provider.name}"
    }
    val hint = when (provider) {
        SecureKeyStore.Provider.GITHUB_MODELS -> "PAT GitHub (ghp_xxx). Crée-en un sur github.com/settings/tokens avec le scope models:read."
        SecureKeyStore.Provider.NVIDIA_NIM -> "Clé nvapi-xxx. Obtiens-la sur build.nvidia.com après login."
        SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN -> "Token cfat-xxx. Profil → API Tokens → Create avec template \"Workers AI\". Pense aussi à enregistrer ton Account ID séparément."
        SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID -> "32 chars hex visible en haut à droite du dashboard Cloudflare (dash.cloudflare.com)."
        else -> "Clé API du provider"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titlePrefix, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim() },
                    label = { Text(if (isSecret) "Coller la clé ici" else "Coller l'Account ID ici") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    // Account ID = public, pas de password mask. Tokens = mask par defaut.
                    visualTransformation = if (!isSecret || showValue) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { input = it.trim() }
                            }) {
                                Icon(Icons.Default.ContentPaste, "Coller")
                            }
                            if (isSecret) {
                                IconButton(onClick = { showValue = !showValue }) {
                                    Icon(
                                        if (showValue) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        "Afficher/masquer",
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrect = false,
                    ),
                )
                if (isAlreadySet) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF22C55E).copy(alpha = 0.10f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "✓ Une clé est déjà enregistrée. Laisser vide pour conserver, ou en saisir une nouvelle pour remplacer.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF15803D),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(input) },
                enabled = input.isNotBlank() || !isAlreadySet,
            ) { Text("Enregistrer", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isAlreadySet) {
                    TextButton(onClick = onClear) {
                        Text("Supprimer", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

// ════════════════════════════════════════════════════════════════════════════
// PROVIDER AVATAR (utilitaire commun)
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ProviderAvatar(provider: LlmProvider, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val (bg, fg) = when (provider) {
        LlmProvider.GROQ -> Color(0xFFFF8A65) to Color.White
        LlmProvider.OPENAI -> Color(0xFF10A37F) to Color.White
        LlmProvider.CLAUDE -> Color(0xFFCC785C) to Color.White
        LlmProvider.GEMINI -> Color(0xFF34A853) to Color.White
        LlmProvider.MISTRAL -> Color(0xFFFF7000) to Color.White
        LlmProvider.GITHUB_MODELS -> Color(0xFF1F2328) to Color.White
        LlmProvider.NVIDIA_NIM -> Color(0xFF76B900) to Color.Black
        LlmProvider.POLLINATIONS -> Color(0xFFEC4899) to Color.White
        LlmProvider.CLOUDFLARE_AI -> Color(0xFFF38020) to Color.White
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            provider.iconLabel,
            color = fg,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
