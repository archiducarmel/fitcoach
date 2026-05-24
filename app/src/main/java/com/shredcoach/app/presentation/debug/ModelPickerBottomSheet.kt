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
import androidx.compose.material.icons.filled.Close
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
    onFilterModelMaker: (String?) -> Unit,
    onApplyPreset: (PickerPreset) -> Unit,
    onSetSortMode: (PickerSortMode) -> Unit,
    onClearAll: () -> Unit,
    onToggleHideGated: () -> Unit,
    onRefreshCatalog: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Liste filtrée + groupée — calculee une fois par changement de critere
    val filtered = remember(
        state.allModels,
        state.pickerSearch,
        state.pickerKindFilter,
        state.pickerProviderFilter,
        state.pickerModelMakerFilter,
        state.pickerSortMode,
        state.pickerHideGated,
    ) { applyFilters(state) }

    val topMakers = remember(state.allModels) { computeTopModelMakers(state) }

    val hasActiveFilters = state.pickerSearch.isNotBlank() ||
        state.pickerKindFilter != null ||
        state.pickerProviderFilter != null ||
        state.pickerModelMakerFilter != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ─── Header : title + counts + refresh ─────────────────────────
            PickerHeader(
                totalCount = state.allModels.size,
                displayedCount = filtered.size,
                isFetching = state.isFetchingCatalog,
                onRefresh = onRefreshCatalog,
            )

            // ─── Search bar avec clear button ──────────────────────────────
            OutlinedTextField(
                value = state.pickerSearch,
                onValueChange = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Rechercher (nom, code, vision, rapide…)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.pickerSearch.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Default.Close, "Effacer recherche", Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )

            // ─── Quick presets (un tap = applique kind filter) ─────────────
            QuickPresetsRow(
                currentKind = state.pickerKindFilter,
                onApplyPreset = onApplyPreset,
            )

            // ─── Filtres sectionnes ────────────────────────────────────────
            SectionedFilters(
                state = state,
                topMakers = topMakers,
                onFilterKind = onFilterKind,
                onFilterProvider = onFilterProvider,
                onFilterModelMaker = onFilterModelMaker,
                onSetSortMode = onSetSortMode,
                onToggleHideGated = onToggleHideGated,
            )

            // ─── Active filters bar (pills avec X) ─────────────────────────
            AnimatedVisibility(visible = hasActiveFilters) {
                ActiveFiltersBar(
                    state = state,
                    onClearSearch = { onSearch("") },
                    onClearKind = { onFilterKind(null) },
                    onClearProvider = { onFilterProvider(null) },
                    onClearMaker = { onFilterModelMaker(null) },
                    onClearAll = onClearAll,
                )
            }

            // ─── Catalogue error inline ────────────────────────────────────
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

            if (filtered.isEmpty()) {
                EmptyResultPlaceholder(state, hasActiveFilters, onClearAll)
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val grouped = filtered.groupBy { it.info.publisher ?: it.provider.displayName.lowercase() }
                    grouped.forEach { (publisher, models) ->
                        item(key = "header_$publisher") {
                            // Header montre tous les service providers qui servent ce maker
                            val providersForMaker = models.map { it.provider }.distinct()
                            PublisherHeader(publisher, models.size, providersForMaker)
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

// ─── Header (title + counts + refresh) ──────────────────────────────────────

@Composable
private fun PickerHeader(
    totalCount: Int,
    displayedCount: Int,
    isFetching: Boolean,
    onRefresh: () -> Unit,
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$displayedCount affichés",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    " sur $totalCount modèles disponibles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        IconButton(onClick = onRefresh, enabled = !isFetching) {
            if (isFetching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, "Rafraîchir catalogue")
        }
    }
}

// ─── Quick presets (rangee horizontale de gros boutons) ─────────────────────

@Composable
private fun QuickPresetsRow(
    currentKind: ModelKind?,
    onApplyPreset: (PickerPreset) -> Unit,
) {
    LazyRow(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(PickerPreset.values()) { preset ->
            val active = preset.kind == currentKind || (preset == PickerPreset.ALL && currentKind == null)
            Surface(
                onClick = { onApplyPreset(preset) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(preset.emoji, fontSize = 14.sp)
                    Text(
                        preset.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ─── Sections filtres (Type / Servi par / Cree par / Tri) ───────────────────

@Composable
private fun SectionedFilters(
    state: LlmDebugState,
    topMakers: List<Pair<String, Int>>,
    onFilterKind: (ModelKind?) -> Unit,
    onFilterProvider: (LlmProvider?) -> Unit,
    onFilterModelMaker: (String?) -> Unit,
    onSetSortMode: (PickerSortMode) -> Unit,
    onToggleHideGated: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Toggle "Filtres avances" + sort selector + hide gated
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(10.dp),
                color = if (expanded) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(if (expanded) "▲" else "▼", fontSize = 10.sp)
                    Text(
                        "Filtres avancés",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            SortDropdown(state.pickerSortMode, onSetSortMode)
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                // ─── Type (kind) ───────────────────────────────────────────
                FilterSection(
                    title = "Type de modèle",
                    subtitle = "Ce que le modèle sait faire",
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChipAll(active = state.pickerKindFilter == null, label = "Tous", onClick = { onFilterKind(null) }) }
                        items(ModelKind.values()) { k ->
                            FilterChipPill(
                                active = state.pickerKindFilter == k,
                                label = "${k.emoji} ${kindLabel(k)}",
                                onClick = { onFilterKind(if (state.pickerKindFilter == k) null else k) },
                            )
                        }
                    }
                }

                // ─── Service provider ──────────────────────────────────────
                FilterSection(
                    title = "📡 Servi par",
                    subtitle = "L'entreprise qui héberge l'API",
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChipAll(active = state.pickerProviderFilter == null, label = "Tous", onClick = { onFilterProvider(null) }) }
                        items(LlmProvider.values()) { p ->
                            FilterChipPill(
                                active = state.pickerProviderFilter == p,
                                label = p.displayName,
                                onClick = { onFilterProvider(if (state.pickerProviderFilter == p) null else p) },
                            )
                        }
                    }
                }

                // ─── Model maker ───────────────────────────────────────────
                FilterSection(
                    title = "🧪 Créé par",
                    subtitle = "L'équipe qui a entraîné les poids du modèle",
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChipAll(
                                active = state.pickerModelMakerFilter == null,
                                label = "Tous",
                                onClick = { onFilterModelMaker(null) },
                            )
                        }
                        items(topMakers) { (maker, count) ->
                            FilterChipPill(
                                active = state.pickerModelMakerFilter?.lowercase() == maker,
                                label = "${prettifyMaker(maker)} ($count)",
                                onClick = {
                                    val toggled = if (state.pickerModelMakerFilter?.lowercase() == maker) null else maker
                                    onFilterModelMaker(toggled)
                                },
                            )
                        }
                    }
                }

                // ─── Hide gated toggle ─────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChipPill(
                        active = state.pickerHideGated,
                        label = if (state.pickerHideGated) "🔒 Masquer payants" else "🔒 Tout afficher",
                        onClick = onToggleHideGated,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun SortDropdown(current: PickerSortMode, onSelect: (PickerSortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(current.emoji, fontSize = 12.sp)
                Text(
                    "Tri: ${current.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text("⌄", fontSize = 10.sp)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PickerSortMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text("${mode.emoji} ${mode.label}") },
                    onClick = { onSelect(mode); open = false },
                )
            }
        }
    }
}

// ─── Active filters bar (chips removable) ───────────────────────────────────

@Composable
private fun ActiveFiltersBar(
    state: LlmDebugState,
    onClearSearch: () -> Unit,
    onClearKind: () -> Unit,
    onClearProvider: () -> Unit,
    onClearMaker: () -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "📌",
                fontSize = 11.sp,
                modifier = Modifier.padding(end = 2.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (state.pickerSearch.isNotBlank()) {
                    item {
                        RemovableChip("🔍 ${state.pickerSearch}", onClearSearch)
                    }
                }
                state.pickerKindFilter?.let { k ->
                    item { RemovableChip("${k.emoji} ${k.name.lowercase()}", onClearKind) }
                }
                state.pickerProviderFilter?.let { p ->
                    item { RemovableChip("📡 ${p.displayName}", onClearProvider) }
                }
                state.pickerModelMakerFilter?.let { m ->
                    item { RemovableChip("🧪 ${prettifyMaker(m)}", onClearMaker) }
                }
            }
            TextButton(
                onClick = onClearAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Tout effacer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            Text("✕", fontSize = 9.sp, color = Color.White)
        }
    }
}

/** "mistralai" -> "Mistral AI", "openai" -> "OpenAI", etc. */
internal fun prettifyMaker(maker: String): String {
    val lower = maker.lowercase()
    return when (lower) {
        "openai" -> "OpenAI"
        "mistralai", "mistral-ai" -> "Mistral AI"
        "meta", "meta-llama" -> "Meta"
        "google" -> "Google"
        "microsoft" -> "Microsoft"
        "anthropic" -> "Anthropic"
        "deepseek-ai", "deepseek" -> "DeepSeek"
        "qwen", "alibaba", "alibaba-cloud" -> "Alibaba"
        "nvidia", "nv-mistralai" -> "NVIDIA"
        "cohere" -> "Cohere"
        "ibm" -> "IBM"
        "xai" -> "xAI"
        "moonshotai" -> "Moonshot"
        "minimaxai" -> "MiniMax"
        "z-ai" -> "Z.ai"
        "stepfun-ai" -> "StepFun"
        "ai21labs", "ai21-labs" -> "AI21 Labs"
        "01-ai" -> "01.AI"
        "writer" -> "Writer"
        "sarvamai" -> "Sarvam AI"
        "stockmark" -> "Stockmark"
        "upstage" -> "Upstage"
        "snowflake" -> "Snowflake"
        "databricks" -> "Databricks"
        "bytedance" -> "ByteDance"
        "aisingapore" -> "AI Singapore"
        "abacusai" -> "Abacus.AI"
        "adept" -> "Adept"
        "bigcode" -> "BigCode"
        "zyphra" -> "Zyphra"
        "baai" -> "BAAI"
        "black-forest-labs" -> "Black Forest Labs"
        "stabilityai" -> "Stability AI"
        else -> maker.replaceFirstChar { it.uppercase() }
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
private fun PublisherHeader(publisher: String, count: Int, providers: List<LlmProvider>) {
    Column(
        Modifier.padding(top = 12.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Avatar du premier service provider (representatif visuellement)
            providers.firstOrNull()?.let { ProviderAvatar(it, size = 16.dp) }
            Text(
                "🧪 ${prettifyMaker(publisher).uppercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                letterSpacing = 0.8.sp,
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        // Sub-line : "Servi par : NVIDIA NIM, GitHub Models, Groq"
        Text(
            "Servi par : " + providers.joinToString(", ") { it.displayName },
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(start = 22.dp),
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
    // BUGFIX v2026.05.24 : check les slots DEDIES + fallback LLM legacy.
    // Avant on lisait UNIQUEMENT LLM (partage) -> faux positifs/negatifs.
    LlmProvider.GROQ -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.GROQ] == true ||
        state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.LLM] == true
    LlmProvider.OPENAI -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.OPENAI] == true ||
        state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.LLM] == true
    LlmProvider.CLAUDE -> state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.CLAUDE] == true ||
        state.apiKeyAvailable[com.shredcoach.app.data.local.secure.SecureKeyStore.Provider.LLM] == true
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
                Spacer(Modifier.height(3.dp))
                // ── "Servi par" line : indique l'API endpoint (≠ creator) ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("📡", fontSize = 8.sp)
                            Text(
                                resolved.provider.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
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
                // Description editorialisee ≤30 mots :
                //  1. ModelDescriptions.describe (catalogue editorial avec match exact)
                //  2. info.notes (cas non couvert : fallback notes statiques LlmCatalog)
                val description = com.shredcoach.app.domain.llm.ModelDescriptions
                    .describe(info.id, info.publisher) ?: info.notes
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 13.sp,
                        maxLines = 4,
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
private fun EmptyResultPlaceholder(
    state: LlmDebugState,
    hasActiveFilters: Boolean,
    onClearAll: () -> Unit,
) {
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
            when {
                state.dynamicGitHubModels.isEmpty() && state.dynamicNvidiaModels.isEmpty() ->
                    "Aucun catalogue distant chargé. Configure ton token puis rafraîchis."
                hasActiveFilters ->
                    "Tes filtres sont trop restrictifs. Élargis ou efface-les."
                else -> "Essaie d'autres mots-clés."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (hasActiveFilters) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onClearAll,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("✨ Effacer tous les filtres", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
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

/**
 * Normalise une chaine pour matching tolerant aux accents et a la casse.
 * Permet de taper "francais" pour trouver "francais", "Francais", etc.
 */
private fun String.normalizeForSearch(): String {
    val noAccents = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return noAccents.lowercase()
}

/** Alias humains -> tokens techniques utilises dans les ids/descriptions. */
private val SEARCH_ALIASES: Map<String, List<String>> = mapOf(
    "chat" to listOf("language", "instruct", "chat"),
    "discussion" to listOf("language", "instruct", "chat"),
    "vision" to listOf("vlm", "vision", "image", "multimodal", "-vl", "kosmos", "fuyu", "llava"),
    "image" to listOf("image", "flux", "diffusion", "sdxl", "dall-e", "vision"),
    "video" to listOf("video", "stable-video", "cosmos-predict", "trellis"),
    "voix" to listOf("tts", "stt", "whisper", "parakeet", "canary", "magpie", "voice", "speech"),
    "voice" to listOf("tts", "stt", "voice", "speech"),
    "audio" to listOf("tts", "stt", "audio", "voice", "speech"),
    "transcription" to listOf("stt", "whisper", "parakeet", "canary"),
    "code" to listOf("code", "coder", "starcoder", "codestral", "codegemma", "codellama"),
    "embedding" to listOf("embed", "rerank", "retriever", "bge", "arctic-embed"),
    "embed" to listOf("embed", "rerank", "retriever", "bge"),
    "recherche" to listOf("embed", "rerank", "retriever", "rag"),
    "rag" to listOf("embed", "retriever", "rerank"),
    "moderation" to listOf("guard", "safety", "classification", "pii"),
    "securite" to listOf("guard", "safety", "classification", "pii"),
    "raisonnement" to listOf("reasoning", "o1", "o3", "o4", "r1", "deepseek-r"),
    "reasoning" to listOf("reasoning", "o1", "o3", "o4", "r1", "deepseek-r"),
    "rapide" to listOf("flash", "fast", "groq", "nano", "mini", "lightning", "turbo", "schnell"),
    "francais" to listOf("francais", "french", "fr ", "mistral", "europe", "europeen"),
    "europeen" to listOf("mistral", "europe", "francais", "european"),
    "chinois" to listOf("chinese", "deepseek", "qwen", "kimi", "minimax", "glm", "yi", "01-ai"),
    "japonais" to listOf("japanese", "stockmark"),
    "indien" to listOf("sarvam", "hindi", "indian"),
)

private fun resolveSearchTerms(rawQuery: String): List<String> {
    val q = rawQuery.normalizeForSearch().trim()
    if (q.isEmpty()) return emptyList()
    val aliases = SEARCH_ALIASES[q].orEmpty()
    return (listOf(q) + aliases).distinct()
}

internal fun applyFilters(state: LlmDebugState): List<ResolvedModel> {
    val terms = resolveSearchTerms(state.pickerSearch)
    val makerFilter = state.pickerModelMakerFilter?.lowercase()
    val filtered = state.allModels.filter { res ->
        val info = res.info
        val matchesSearch = terms.isEmpty() || run {
            // On agrege TOUS les champs textuels en un seul haystack normalise.
            // Permet a "rapide" de matcher la description meme si le mot n'est
            // ni dans l'id ni dans le nom (alias -> "flash"/"fast"/...).
            val haystack = buildString {
                append(info.displayName.normalizeForSearch()).append(' ')
                append(info.id.normalizeForSearch()).append(' ')
                append(info.publisher.orEmpty().normalizeForSearch()).append(' ')
                append(info.kind.name.normalizeForSearch()).append(' ')
                append(info.notes.normalizeForSearch()).append(' ')
                append(res.provider.name.normalizeForSearch()).append(' ')
                append(res.provider.displayName.normalizeForSearch())
            }
            terms.any { haystack.contains(it) }
        }
        val matchesKind = state.pickerKindFilter == null || info.kind == state.pickerKindFilter
        val matchesProvider = state.pickerProviderFilter == null || res.provider == state.pickerProviderFilter
        val matchesMaker = makerFilter == null ||
            (info.publisher?.lowercase() == makerFilter)
        val matchesGated = !state.pickerHideGated || !info.isGated
        matchesSearch && matchesKind && matchesProvider && matchesMaker && matchesGated
    }
    return when (state.pickerSortMode) {
        com.shredcoach.app.presentation.debug.PickerSortMode.RECOMMENDED -> filtered
        com.shredcoach.app.presentation.debug.PickerSortMode.NAME_ASC ->
            filtered.sortedBy { it.info.displayName.lowercase() }
        com.shredcoach.app.presentation.debug.PickerSortMode.NEWEST ->
            filtered.sortedByDescending { it.info.releaseYear }
        com.shredcoach.app.presentation.debug.PickerSortMode.LARGEST ->
            filtered.sortedByDescending { it.info.parameterCountBillions }
        com.shredcoach.app.presentation.debug.PickerSortMode.SMALLEST ->
            filtered.sortedBy { if (it.info.parameterCountBillions <= 0) Double.MAX_VALUE
                                else it.info.parameterCountBillions }
    }
}

/** Top model makers tries par frequence (pour les chips de filtre). */
internal fun computeTopModelMakers(state: LlmDebugState, limit: Int = 12): List<Pair<String, Int>> =
    state.allModels
        .mapNotNull { it.info.publisher?.takeIf { p -> p.isNotBlank() } }
        .groupingBy { it.lowercase() }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key to it.value }
