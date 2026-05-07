package com.shredcoach.app.presentation.settings.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shredcoach.app.domain.coach.CoachSettingsStore
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Section "Coach proactif" pour SettingsScreen.
 *
 * Contenu (visible quand activé) :
 * 1. Toggle activation (avec dialog consentement LLM si pas encore accordé)
 * 2. Slider d'horaire quotidien (6h-21h)
 * 3. Sélecteur de **ton** (Doux / Direct / Coach pro max) — 3 prompts radicalement différents
 * 4. Slider **plafond hebdomadaire** (1-14 notifs/semaine)
 * 5. Liste des **catégories de triggers** mutables individuellement
 *    (chaque catégorie = un type de notif que l'utilisateur peut couper)
 *
 * Toutes les options ne sont visibles QUE quand la feature est activée → écran
 * minimal pour les utilisateurs qui n'utilisent pas la feature.
 */
@Composable
fun CoachSettingsSection(viewModel: CoachSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var showConsentDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Toggle principal
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = 16.dp)) {
                Text(
                    "Coach proactif IA",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Notifications intelligentes : célébration de PR, rappel séance, " +
                        "ajustements nutrition, récap dimanche soir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Switch(
                checked = state.enabled,
                onCheckedChange = { newVal ->
                    if (newVal && !state.llmConsentGranted) showConsentDialog = true
                    else viewModel.setEnabled(newVal)
                },
            )
        }

        if (state.enabled) {
            Spacer(Modifier.height(4.dp))

            // ── Horaire quotidien ──
            HourSlider(
                value = state.preferredHour,
                onValueChange = viewModel::setPreferredHour,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── Ton ──
            ToneSelector(
                selected = state.tone,
                onSelected = viewModel::setTone,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── Plafond hebdomadaire ──
            WeeklyCapSlider(
                value = state.weeklyCap,
                onValueChange = viewModel::setWeeklyCap,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // ── Catégories mutables ──
            CategoryToggles(
                muted = state.mutedCategories,
                onToggle = viewModel::toggleMute,
            )
        }
    }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            icon = { Icon(Icons.Default.AutoAwesome, null, tint = OrangeVibrant) },
            title = { Text("Activer le coach IA ?") },
            text = {
                Text(
                    "Pour générer ses messages, Shreddy envoie ton contexte (séances, macros, " +
                        "extraits de chat) au fournisseur IA configuré (Groq, OpenAI ou Claude). " +
                        "Aucune photo ni clé API n'est partagée. Tu peux désactiver à tout moment.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.acceptAndEnable()
                        showConsentDialog = false
                    },
                ) { Text("Accepter et activer") }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) { Text("Plus tard") }
            },
        )
    }
}

@Composable
private fun HourSlider(value: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Notification quotidienne",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${value}h",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = OrangeVibrant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 6f..21f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToneSelector(
    selected: CoachSettingsStore.Tone,
    onSelected: (CoachSettingsStore.Tone) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Ton de Shreddy",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            stringResource(selected.descriptionRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoachSettingsStore.Tone.values().forEach { tone ->
                val isSelected = tone == selected
                Surface(
                    onClick = { onSelected(tone) },
                    modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (isSelected) 0.dp else 1.dp,
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(tone.displayNameRes),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCapSlider(value: Int, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.padding(end = 16.dp)) {
                Text(
                    "Plafond hebdomadaire",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Nb max de notifications coach par semaine",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Text(
                "$value",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = OrangeVibrant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..14f,
            steps = 12,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryToggles(muted: Set<String>, onToggle: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Catégories de notifications",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Désactive celles qui ne te sont pas utiles",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(8.dp))
        CATEGORIES.forEach { (category, label, description) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
                // Switch INVERSÉ : checked = pas mute. UX naturelle "active = je reçois".
                Switch(
                    checked = category !in muted,
                    onCheckedChange = { onToggle(category) },
                )
            }
        }
    }
}

/**
 * Liste des catégories de triggers exposées à l'UI. Synchroniser avec
 * [com.shredcoach.app.domain.coach.CoachTrigger] — chaque entrée = un
 * `category` string, son label visible et sa description.
 */
private val CATEGORIES = listOf(
    Triple("streak_at_risk", "Streak en danger", "Quand ton enchaînement de séances est en jeu"),
    Triple("missed_workout", "Séance ratée", "Quand une séance planifiée n'a pas été faite"),
    Triple("pr_celebration", "Célébration de PR", "Quand tu bats un record perso"),
    Triple("protein_deficit", "Déficit protéine", "En sèche, quand l'apport est sous l'objectif"),
    Triple("plateau_volume", "Plateau", "Quand le volume stagne 3 semaines"),
    Triple("comeback", "Retour après pause", "Encouragement après 7+ jours sans séance"),
    Triple("body_scan_stale", "Scan corporel à jour", "Rappel pour mesurer si > 30 jours"),
    Triple("goal_eta", "Trajectoire d'objectif", "Estimation du temps restant pour atteindre la cible"),
    Triple("weekly_recap", "Récap hebdomadaire", "Bilan dimanche soir"),
    Triple("motivation_general", "Check-in général", "Message neutre quand rien à signaler"),
)
