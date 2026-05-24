package com.shredcoach.app.presentation.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.remote.LlmProvider
import com.shredcoach.app.domain.llm.ModelKind

/**
 * Bottom sheet plein-ecran pour piocher un modele parmi les 150+ disponibles.
 *
 * Features premium :
 *  - Search bar sticky top avec icone 🔍
 *  - Chips de filtre : kinds (LANGUAGE/VLM/EMBED/etc.) + provider + hide-gated toggle
 *  - Liste virtualisee (LazyColumn) groupee par publisher
 *  - Pour chaque modele : avatar provider + nom + kind emoji + metadata
 *    (architecture, weights, origine, params) + tier badge + gating indicator
 *  - Bouton "🔄 Refresh GitHub catalog" qui invalide le cache 24h
 *  - Indicateur d'erreur / loading inline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerBottomSheet(
    state: LlmDebugState,
    onDismiss: () -> Unit,
    onSelectModel: (ResolvedModel) -> Unit,
    onSearch: (String) -> Unit,
    onFilterKind: (ModelKind?) -> Unit,
    onFilterProvider: (LlmProvider?) -> Unit,
    onToggleHideGated: () -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxHeight(0.92f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header sticky : title + refresh button
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Choisir un modèle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "${state.allModels.size} modèles disponibles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                IconButton(
                    onClick = onRefreshCatalog,
                    enabled = !state.isFetchingCatalog,
                ) {
                    if (state.isFetchingCatalog) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Rafraîchir catalogue GitHub")
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = state.pickerSearch,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Rechercher (nom, publisher, kind)…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            // Filtres chips
            FilterRow(
                state = state,
                onFilterKind = onFilterKind,
                onFilterProvider = onFilterProvider,
                onToggleHideGated = onToggleHideGated,
            )

            // Erreur catalogue
            AnimatedVisibility(visible = state.catalogError != null) {
                state.catalogError?.let { err ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    ) {
                        Text(
                            "⚠️ $err",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Liste filtrée + groupée
            val filtered = remember(
                state.allModels,
                state.pickerSearch,
                state.pickerKindFilter,
                state.pickerProviderFilter,
                state.pickerHideGated,
            ) {
                applyFilters(state)
            }

            if (filtered.isEmpty()) {
                EmptyResultPlaceholder(state)
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val grouped = filtered.groupBy { it.info.publisher ?: it.provider.displayName.lowercase() }
                    grouped.forEach { (publisher, models) ->
                        item(key = "header_$publisher") {
                            PublisherHeader(publisher, models.size, models.first().provider)
                        }
                        items(models, key = { "${it.provider}_${it.info.id}" }) { resolved ->
                            ModelRow(
                                resolved = resolved,
                                isSelected = state.selectedModel?.info?.id == resolved.info.id &&
                                        state.selectedModel?.provider == resolved.provider,
                                isKeyMissing = !hasKeyForProvider(state, resolved.provider),
                                onClick = { onSelectModel(resolved) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp).navigationBarsPadding()) }
                }
            }
        }
    }
}

// ─── Filter row ─────────────────────────────────────────────────────────────

@Composable
private fun FilterRow(
    state: LlmDebugState,
    onFilterKind: (ModelKind?) -> Unit,
    onFilterProvider: (LlmProvider?) -> Unit,
    onToggleHideGated: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Kinds row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChipAll(active = state.pickerKindFilter == null, label = "Tous", onClick = { onFilterKind(null) }) }
            items(ModelKind.values()) { k ->
                FilterChipPill(
                    active = state.pickerKindFilter == k,
                    // Fix #7 : i18n via stringResource(labelKey) au lieu de
                    // l'enum name en anglais hardcode
                    label = "${k.emoji} ${kindLabel(k)}",
                    onClick = { onFilterKind(if (state.pickerKindFilter == k) null else k) },
                )
            }
        }
        // Providers + hide-gated
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FilterChipAll(active = state.pickerProviderFilter == null, label = "Tous fournisseurs", onClick = { onFilterProvider(null) }) }
            items(LlmProvider.values()) { p ->
                FilterChipPill(
                    active = state.pickerProviderFilter == p,
                    label = p.displayName,
                    onClick = { onFilterProvider(if (state.pickerProviderFilter == p) null else p) },
                )
            }
            item {
                FilterChipPill(
                    active = state.pickerHideGated,
                    label = if (state.pickerHideGated) "🔒 Masqués" else "🔒 Affichés",
                    onClick = onToggleHideGated,
                )
            }
        }
    }
}

@Composable
private fun FilterChipAll(active: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun FilterChipPill(active: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (active) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary
        ) else null,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

// ─── Liste ──────────────────────────────────────────────────────────────────

@Composable
private fun PublisherHeader(publisher: String, count: Int, provider: LlmProvider) {
    Row(
        Modifier.padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProviderAvatar(provider, size = 16.dp)
        Text(
            publisher.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
        )
        Text(
            "($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/**
 * Verifie si une cle API est configuree pour le provider du modele.
 * Pollinations = no auth, toujours OK.
 */
private fun hasKeyForProvider(state: LlmDebugState, provider: LlmProvider): Boolean = when (provider) {
    LlmProvider.POLLINATIONS -> true
    LlmProvider.GITHUB_MODELS -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.GITHUB_MODELS] == true
    LlmProvider.NVIDIA_NIM -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.NVIDIA_NIM] == true
    LlmProvider.CLOUDFLARE_AI -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.CLOUDFLARE_AI_TOKEN] == true &&
        state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.CLOUDFLARE_ACCOUNT_ID] == true
    LlmProvider.GEMINI -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.GEMINI] == true
    LlmProvider.MISTRAL -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.MISTRAL] == true
    LlmProvider.GROQ, LlmProvider.OPENAI, LlmProvider.CLAUDE -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.LLM] == true
}

@Composable
private fun ModelRow(resolved: ResolvedModel, isSelected: Boolean, isKeyMissing: Boolean = false, onClick: () -> Unit) {
    val info = resolved.info
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                RoundedCornerShape(12.dp),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${info.kind.emoji} ${info.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        color = if (isKeyMissing) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.onSurface,
                    )
                    if (info.isGated) {
                        Icon(
                            Icons.Default.Lock, null,
                            Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        )
                    }
                    if (isKeyMissing) {
                        // Badge subtil "🔒 Clé requise" — model selectable mais
                        // l'user sait qu'il devra configurer la cle avant d'envoyer.
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    Icons.Default.Lock, null,
                                    Modifier.size(9.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    "clé requise",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                // Metadata row : architecture + weights + origin + params
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        info.kind.name.lowercase().replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    )
                    if (info.weightsSource != com.shredcoach.app.domain.llm.WeightsSource.UNKNOWN) {
                        Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            info.weightsSource.emoji,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                        )
                    }
                    if (info.originRegion != com.shredcoach.app.domain.llm.ModelOriginRegion.UNKNOWN) {
                        Text(
                            info.originRegion.flag,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                        )
                    }
                    if (info.parameterCountBillions > 0) {
                        Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            "${info.parameterCountBillions.toInt()}B",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (info.maxContextTokens > 0) {
                        Text("·", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            "${info.maxContextTokens / 1000}K ctx",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                // Capability badges
                if (info.supportsThinking || info.supportsToolCalling || info.supportsAgentic ||
                    info.supportsCodeGen || info.supportsTranslation) {
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (info.supportsThinking) CapabilityBadge("🧠", "Thinking")
                        if (info.supportsToolCalling) CapabilityBadge("🛠", "Tools")
                        if (info.supportsAgentic) CapabilityBadge("⚡", "Agentic")
                        if (info.supportsCodeGen) CapabilityBadge("💻", "Code")
                        if (info.supportsTranslation) CapabilityBadge("🌍", "i18n")
                    }
                }
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 13.sp,
                        maxLines = 4,  // ≤30 mots = ~3-4 lignes selon largeur
                    )
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(emoji: String, label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            "$emoji $label",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun EmptyResultPlaceholder(state: LlmDebugState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔍", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Aucun modèle correspond",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.dynamicGitHubModels.isEmpty())
                "Le catalogue GitHub n'est pas chargé. Configure ton token puis rafraîchis."
            else "Essaie d'autres filtres ou recherche.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun kindLabel(kind: ModelKind): String = when (kind) {
    ModelKind.LANGUAGE -> stringResource(com.shredcoach.app.R.string.model_kind_chat)
    ModelKind.VLM -> stringResource(com.shredcoach.app.R.string.model_kind_vlm)
    ModelKind.EMBEDDING -> stringResource(com.shredcoach.app.R.string.model_kind_embedding)
    ModelKind.MULTIMODAL_EMBEDDING -> stringResource(com.shredcoach.app.R.string.model_kind_embedding) + "+img"
    ModelKind.RERANKER -> "Reranker"
    ModelKind.IMAGE_GENERATION -> stringResource(com.shredcoach.app.R.string.model_kind_image_gen)
    ModelKind.VIDEO_GENERATION -> "Vidéo"
    ModelKind.TTS -> stringResource(com.shredcoach.app.R.string.model_kind_tts)
    ModelKind.STT -> stringResource(com.shredcoach.app.R.string.model_kind_stt)
    ModelKind.OBJECT_DETECTION -> stringResource(com.shredcoach.app.R.string.model_kind_object_detection)
    ModelKind.OCR -> "OCR"
    ModelKind.CLASSIFICATION -> "Safety"
    ModelKind.REWARD_MODEL -> "Reward"
    ModelKind.SCIENTIFIC -> "Scientifique"
    ModelKind.OPTIMIZATION -> "Optim."
}

// ─── Filter logic ───────────────────────────────────────────────────────────

private fun applyFilters(state: LlmDebugState): List<ResolvedModel> {
    val q = state.pickerSearch.trim().lowercase()
    return state.allModels.filter { res ->
        val info = res.info
        val matchesSearch = q.isEmpty() ||
                info.displayName.lowercase().contains(q) ||
                info.id.lowercase().contains(q) ||
                (info.publisher?.lowercase()?.contains(q) == true) ||
                info.kind.name.lowercase().contains(q)
        val matchesKind = state.pickerKindFilter == null || info.kind == state.pickerKindFilter
        val matchesProvider = state.pickerProviderFilter == null || res.provider == state.pickerProviderFilter
        val matchesGated = !state.pickerHideGated || !info.isGated
        matchesSearch && matchesKind && matchesProvider && matchesGated
    }
}
