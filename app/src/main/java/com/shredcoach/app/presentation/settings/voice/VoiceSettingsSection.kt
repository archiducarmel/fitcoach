package com.shredcoach.app.presentation.settings.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.domain.voice.Gender
import com.shredcoach.app.domain.voice.Persona
import com.shredcoach.app.domain.voice.VoiceEngineId
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Section "Voix Shreddy" — composant de [SettingsScreen].
 *
 * Layout :
 *  1. **Engine picker** (2 cards horizontales) : Android | Google Chirp 3 HD
 *  2. **Persona grid** (2×2) : 4 personae filtrées selon le moteur choisi.
 *     Chaque card = avatar emoji + nom + tagline + chip genre.
 *  3. **Bouton Tester** : joue un échantillon vocal avec la voix sélectionnée.
 *  4. **Clé API Google** (visible uniquement si engine = GOOGLE_CHIRP3) :
 *     champ password + paste + visibility toggle + lien vers la console GCP.
 */
@Composable
fun VoiceSettingsSection(viewModel: VoiceSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val apiKey by viewModel.googleApiKey.collectAsState()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Choisis le moteur de synthèse + la voix qui te motive le plus. " +
                "Le changement s'applique à la prochaine annonce vocale.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        // ═══ Engine picker ═══
        EnginePicker(
            selected = state.engineId,
            googleApiKeyConfigured = state.googleApiKeyConfigured,
            onSelect = viewModel::selectEngine,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // ═══ Persona grid ═══
        Text(
            "Personnage",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        PersonaGrid(
            personae = state.personaeForCurrentEngine,
            selectedPersonaId = state.personaId,
            onSelect = viewModel::selectPersona,
        )

        // ═══ Test button ═══
        Button(
            onClick = { viewModel.playPreview() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
            enabled = state.engineId == VoiceEngineId.ANDROID || state.googleApiKeyConfigured,
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tester la voix", fontWeight = FontWeight.SemiBold)
        }

        // ═══ Clé API Google (conditionnel) ═══
        if (state.engineId == VoiceEngineId.GOOGLE_CHIRP3) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            GoogleApiKeyField(
                apiKey = apiKey,
                onChange = viewModel::updateGoogleApiKey,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Engine picker
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun EnginePicker(
    selected: VoiceEngineId,
    googleApiKeyConfigured: Boolean,
    onSelect: (VoiceEngineId) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngineCard(
            engine = VoiceEngineId.ANDROID,
            selected = selected == VoiceEngineId.ANDROID,
            warning = null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = { onSelect(VoiceEngineId.ANDROID) },
        )
        EngineCard(
            engine = VoiceEngineId.GOOGLE_CHIRP3,
            selected = selected == VoiceEngineId.GOOGLE_CHIRP3,
            warning = if (selected == VoiceEngineId.GOOGLE_CHIRP3 && !googleApiKeyConfigured) "Clé API requise"
                else null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            onClick = { onSelect(VoiceEngineId.GOOGLE_CHIRP3) },
        )
    }
}

@Composable
private fun EngineCard(
    engine: VoiceEngineId,
    selected: Boolean,
    warning: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (selected) OrangeVibrant else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        tween(220),
        label = "engineBorder",
    )
    val containerColor by animateColorAsState(
        if (selected) OrangeVibrant.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        tween(220),
        label = "engineBg",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 110.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (engine == VoiceEngineId.ANDROID) Icons.Default.PhoneAndroid else Icons.Default.Cloud,
                    null,
                    Modifier.size(20.dp),
                    tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    stringResource(engine.displayNameRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(engine.taglineRes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning != null) {
                Text(
                    warning,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Persona grid
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonaGrid(
    personae: List<Persona>,
    selectedPersonaId: String,
    onSelect: (Persona) -> Unit,
) {
    // Layout : 2 colonnes × N lignes. Pour 4 personae → 2×2.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        personae.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { persona ->
                    PersonaCard(
                        persona = persona,
                        selected = persona.id == selectedPersonaId,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSelect(persona) },
                    )
                }
                // Padding si row impair (sécurité visuelle, pas attendu pour 4)
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: Persona,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        if (selected) 1f else 0.97f,
        tween(220),
        label = "personaScale",
    )
    val borderColor by animateColorAsState(
        if (selected) OrangeVibrant else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        tween(220),
        label = "personaBorder",
    )
    val genderColor = if (persona.gender == Gender.MALE) {
        Color(0xFF4FC3F7) // bleu clair
    } else {
        Color(0xFFFF80AB) // rose
    }

    Surface(
        onClick = onClick,
        modifier = modifier.scale(scale).defaultMinSize(minHeight = 132.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) OrangeVibrant.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Avatar : disque dégradé persona-color → emoji
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                genderColor.copy(alpha = 0.85f),
                                genderColor.copy(alpha = 0.45f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(persona.avatarEmoji, fontSize = 26.sp)
            }
            Text(
                persona.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                persona.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            GenderChip(gender = persona.gender, accent = genderColor)
        }
    }
}

@Composable
private fun GenderChip(gender: Gender, accent: Color) {
    val label = if (gender == Gender.MALE) "Masc." else "Fém."
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// API Key field
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun GoogleApiKeyField(apiKey: String, onChange: (String) -> Unit) {
    var showKey by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = OrangeVibrant)
            Text(
                "Clé API Google Cloud TTS",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            placeholder = {
                Text("AIza…", style = MaterialTheme.typography.bodySmall)
            },
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { onChange(it.trim()) }
                    }) { Icon(Icons.Default.ContentPaste, "Coller", tint = OrangeVibrant) }
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Afficher / masquer",
                        )
                    }
                }
            },
        )
        Text(
            "Active l'API « Cloud Text-to-Speech » sur console.cloud.google.com, " +
                "puis crée une clé API dans \"APIs & Services › Credentials\". " +
                "La clé est stockée chiffrée sur l'appareil.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}
