package com.shredcoach.app.presentation.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import android.os.Build
import com.shredcoach.app.R
import com.shredcoach.app.presentation.theme.OrangeVibrant
import com.shredcoach.app.presentation.theme.SYSTEM_PALETTE_KEY
import com.shredcoach.app.presentation.theme.ShredPalette
import com.shredcoach.app.presentation.theme.ShredPalettes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = com.shredcoach.app.presentation.navigation.LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val context = navController.context

    // Reprogramme les notifications en temps reel apres chaque changement
    val notifsUpdatedMsg = stringResource(R.string.settings_notifications_updated)
    fun applyNotifications() {
        val p = state.profile ?: return
        com.shredcoach.app.notification.NotificationScheduler.scheduleAll(context, p)
        scope.launch { snackbarHostState.showSnackbar(notifsUpdatedMsg, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(Unit) { viewModel.ensureProfileExists() }

    // Settings est un onglet racine (nav bar). On ne montre PAS la flèche retour.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        val profile = state.profile

        if (state.isLoading || profile == null) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ═══ PROFIL HEADER (premium card) ═══
                ProfileHeaderCard(
                    profile = profile,
                    onClick = { navController.navigate(com.shredcoach.app.presentation.navigation.Screen.Profile.route) }
                )

                // ═══ LANGUE (i18n) ═══
                // Affiche le drapeau + la langue native courante. Tap → écran
                // picker dédié (Screen.LanguageSettings). Volontairement en
                // tête car c'est un setting global qui affecte TOUTE l'UX
                // (texte, notifs, coach vocal).
                LanguageSettingEntry(
                    profile = profile,
                    onClick = {
                        navController.navigate(com.shredcoach.app.presentation.navigation.Screen.LanguageSettings.route)
                    },
                )

                // ═══ SÉANCE ═══
                SettingsSection(stringResource(R.string.settings_section_during_workout), Icons.Default.FitnessCenter) {
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_autostart_title),
                        subtitle = stringResource(R.string.settings_workout_autostart_subtitle),
                        checked = profile.autoStartAfterRest,
                        onCheckedChange = { viewModel.updateAutoStartAfterRest(it) }
                    )
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_vibration_title),
                        subtitle = stringResource(R.string.settings_workout_vibration_subtitle),
                        checked = profile.vibrationEnabled,
                        onCheckedChange = { viewModel.updateVibration(it) }
                    )
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_sound_title),
                        subtitle = stringResource(R.string.settings_workout_sound_subtitle),
                        checked = profile.soundEnabled,
                        onCheckedChange = { viewModel.updateSound(it) }
                    )
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_voice_title),
                        subtitle = stringResource(R.string.settings_workout_voice_subtitle),
                        checked = profile.voiceEnabled,
                        onCheckedChange = { viewModel.updateVoiceEnabled(it) }
                    )
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_coach_tips_title),
                        subtitle = stringResource(R.string.settings_workout_coach_tips_subtitle),
                        checked = profile.showCoachTips,
                        onCheckedChange = { viewModel.updateShowCoachTips(it) }
                    )
                    SwitchSetting(
                        title = stringResource(R.string.settings_workout_bonus_title),
                        subtitle = stringResource(R.string.settings_workout_bonus_subtitle),
                        checked = profile.suggestBonusSeries,
                        onCheckedChange = { viewModel.updateSuggestBonusSeries(it) }
                    )

                    // Repos par défaut
                    SliderSetting(
                        title = stringResource(R.string.settings_workout_default_rest_title),
                        value = profile.defaultRestSeconds,
                        range = 30..180,
                        step = 15,
                        unit = stringResource(R.string.settings_unit_seconds),
                        onValueChange = { viewModel.updateDefaultRest(it) }
                    )
                }

                // ═══ VOIX SHREDDY (moteur + persona) ═══
                SettingsSection(stringResource(R.string.settings_section_voice), Icons.Default.RecordVoiceOver) {
                    com.shredcoach.app.presentation.settings.voice.VoiceSettingsSection()
                }

                // ═══ SANTÉ / LIMITATIONS ═══
                SettingsSection(stringResource(R.string.settings_section_health), Icons.Default.HealthAndSafety) {
                    OutlinedTextField(
                        value = profile.healthNotes,
                        onValueChange = { viewModel.updateHealthNotes(it) },
                        label = { Text(stringResource(R.string.settings_health_notes_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2, maxLines = 4,
                        placeholder = { Text(stringResource(R.string.settings_health_notes_placeholder), style = MaterialTheme.typography.bodySmall) },
                        supportingText = { Text(stringResource(R.string.settings_health_notes_support), style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // ═══ SÉANCE (durée) ═══
                SettingsSection(stringResource(R.string.settings_section_duration), Icons.Default.Timer) {
                    SliderSetting(
                        title = stringResource(R.string.settings_duration_preferred),
                        value = profile.preferredWorkoutDuration,
                        range = 30..180,
                        step = 15,
                        unit = stringResource(R.string.settings_unit_minutes),
                        onValueChange = { viewModel.updateDuration(it) }
                    )
                }

                // ═══ NOTIFICATIONS (application en temps reel) ═══
                SettingsSection(stringResource(R.string.settings_section_notifications), Icons.Default.Notifications) {
                    SwitchSetting(
                        stringResource(R.string.settings_notifs_enabled_title),
                        stringResource(R.string.settings_notifs_enabled_subtitle),
                        profile.notificationsEnabled,
                    ) { viewModel.updateNotificationsEnabled(it); applyNotifications() }

                    if (profile.notificationsEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text(stringResource(R.string.settings_notifs_meals_label),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp), color = OrangeVibrant)
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_breakfast),
                            stringResource(R.string.settings_notifs_reminder_at, profile.breakfastTime),
                            profile.notifBreakfast,
                        ) { viewModel.updateNotifBreakfast(it); applyNotifications() }
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_lunch),
                            stringResource(R.string.settings_notifs_reminder_at, profile.lunchTime),
                            profile.notifLunch,
                        ) { viewModel.updateNotifLunch(it); applyNotifications() }
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_snack),
                            stringResource(R.string.settings_notifs_reminder_at, profile.snackTime),
                            profile.notifSnack,
                        ) { viewModel.updateNotifSnack(it); applyNotifications() }
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_dinner),
                            stringResource(R.string.settings_notifs_reminder_at, profile.dinnerTime),
                            profile.notifDinner,
                        ) { viewModel.updateNotifDinner(it); applyNotifications() }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text(stringResource(R.string.settings_notifs_shakers_label),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp), color = OrangeVibrant)
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_shakers_title),
                            stringResource(R.string.settings_notifs_shakers_subtitle, profile.shakerMorningTime, profile.shakerEveningTime),
                            profile.notifShaker,
                        ) { viewModel.updateNotifShaker(it); applyNotifications() }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text(stringResource(R.string.settings_notifs_others_label),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp), color = OrangeVibrant)
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_bedtime_title),
                            stringResource(R.string.settings_notifs_bedtime_subtitle),
                            profile.notifBedtime,
                        ) { viewModel.updateNotifBedtime(it); applyNotifications() }
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_motivation_title),
                            stringResource(R.string.settings_notifs_motivation_subtitle),
                            profile.notifMotivation,
                        ) { viewModel.updateNotifMotivation(it); applyNotifications() }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Text(stringResource(R.string.settings_notifs_debrief_label),
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp), color = OrangeVibrant)
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_meal_debrief_title),
                            stringResource(R.string.settings_notifs_meal_debrief_subtitle),
                            profile.notifMealDebrief,
                        ) { viewModel.updateNotifMealDebrief(it) }
                        if (profile.notifMealDebrief) {
                            SliderSetting(
                                title = stringResource(R.string.settings_notifs_meal_debrief_delay),
                                value = profile.mealDebriefDelayMinutes,
                                range = 5..180,
                                step = 5,
                                unit = stringResource(R.string.settings_unit_minutes),
                            ) { viewModel.updateMealDebriefDelay(it) }
                        }
                        SwitchSetting(
                            stringResource(R.string.settings_notifs_workout_debrief_title),
                            stringResource(R.string.settings_notifs_workout_debrief_subtitle),
                            profile.notifWorkoutDebrief,
                        ) { viewModel.updateNotifWorkoutDebrief(it) }
                        if (profile.notifWorkoutDebrief) {
                            SliderSetting(
                                title = stringResource(R.string.settings_notifs_workout_debrief_delay),
                                value = profile.workoutDebriefDelayMinutes,
                                range = 5..180,
                                step = 5,
                                unit = stringResource(R.string.settings_unit_minutes),
                            ) { viewModel.updateWorkoutDebriefDelay(it) }
                        }
                    }
                }

                // ═══ AFFICHAGE ═══
                SettingsSection(stringResource(R.string.settings_section_appearance), Icons.Default.Palette) {
                    // Mode clair / sombre / auto
                    ChipGroupSetting(
                        title = stringResource(R.string.settings_appearance_mode_title),
                        options = listOf(
                            stringResource(R.string.settings_appearance_mode_light),
                            stringResource(R.string.settings_appearance_mode_dark),
                            stringResource(R.string.settings_appearance_mode_auto),
                        ),
                        selectedIndex = when (profile.darkMode) { "light" -> 0; "dark" -> 1; else -> 2 },
                        onSelected = { viewModel.updateDarkMode(when (it) { 0 -> "light"; 1 -> "dark"; else -> "auto" }) }
                    )

                    // Sélecteur de palette (thème couleurs)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_appearance_palette_title),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_appearance_palette_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    PalettePicker(
                        selectedKey = profile.themePalette,
                        isDark = when (profile.darkMode) {
                            "dark" -> true
                            "light" -> false
                            else -> androidx.compose.foundation.isSystemInDarkTheme()
                        },
                        onSelected = { viewModel.updateThemePalette(it) }
                    )

                    Spacer(Modifier.height(4.dp))
                    SwitchSetting(
                        stringResource(R.string.settings_appearance_imperial_title),
                        stringResource(R.string.settings_appearance_imperial_subtitle),
                        profile.useImperial,
                    ) { viewModel.updateUseImperial(it) }
                }

                // ═══ SAUVEGARDE LOCALE (Drive / OneDrive / Dropbox / local) ═══
                SettingsSection(stringResource(R.string.settings_section_backup), Icons.Default.CloudSync) {
                    com.shredcoach.app.presentation.settings.backup.BackupSettingsSection(
                        snackbar = snackbarHostState
                    )
                }

                // ═══ COACH PROACTIF IA (gated on LLM consent) ═══
                SettingsSection(stringResource(R.string.settings_section_coach), Icons.Default.AutoAwesome) {
                    com.shredcoach.app.presentation.settings.coach.CoachSettingsSection()
                }

                // ═══ MEAL SCANNER ═══
                SettingsSection(stringResource(R.string.settings_section_meal_scanner), Icons.Default.CameraAlt) {
                    // Provider selector
                    Text(stringResource(R.string.settings_meal_scanner_provider_label),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "GEMINI" to "Gemini",
                            "GROQ" to "Groq",
                            "MISTRAL" to "Mistral"
                        ).forEach { (code, label) ->
                            val selected = profile.mealScanProvider == code
                            Surface(
                                onClick = { viewModel.updateMealScanProvider(code) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    val clipboardMgr = androidx.compose.ui.platform.LocalClipboardManager.current

                    // ─── Config Gemini ───
                    if (profile.mealScanProvider == "GEMINI") {
                        ChipGroupSetting(
                            title = stringResource(R.string.settings_meal_scanner_gemini_model_label),
                            options = listOf("2.5 Flash", "2.0 Flash", "3 Preview"),
                            selectedIndex = when (profile.geminiModel) { "gemini-2.0-flash" -> 1; "gemini-3-flash-preview" -> 2; else -> 0 },
                            onSelected = { viewModel.updateGeminiModel(when (it) { 1 -> "gemini-2.0-flash"; 2 -> "gemini-3-flash-preview"; else -> "gemini-2.5-flash" }) }
                        )
                        var showGeminiKey by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.geminiApiKey,
                            onValueChange = { viewModel.updateGeminiApiKey(it.trim()) },
                            label = { Text(stringResource(R.string.settings_meal_scanner_api_key_gemini)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        clipboardMgr.getText()?.text?.let { viewModel.updateGeminiApiKey(it.trim()) }
                                    }) { Icon(Icons.Default.ContentPaste, stringResource(R.string.cd_paste), tint = OrangeVibrant) }
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.cd_show_password))
                                    }
                                }
                            },
                            placeholder = { Text("AIzaSy...", style = MaterialTheme.typography.bodySmall) },
                            supportingText = { Text(stringResource(R.string.settings_meal_scanner_gemini_support)) }
                        )
                    }

                    // ─── Config Groq ───
                    if (profile.mealScanProvider == "GROQ") {
                        Text(stringResource(R.string.settings_meal_scanner_groq_model),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        var showGroqKey by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.groqMealApiKey,
                            onValueChange = { viewModel.updateGroqMealApiKey(it.trim()) },
                            label = { Text(stringResource(R.string.settings_meal_scanner_api_key_groq)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showGroqKey) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        clipboardMgr.getText()?.text?.let { viewModel.updateGroqMealApiKey(it.trim()) }
                                    }) { Icon(Icons.Default.ContentPaste, stringResource(R.string.cd_paste), tint = OrangeVibrant) }
                                    IconButton(onClick = { showGroqKey = !showGroqKey }) {
                                        Icon(if (showGroqKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.cd_show_password))
                                    }
                                }
                            },
                            placeholder = { Text("gsk_...", style = MaterialTheme.typography.bodySmall) },
                            supportingText = { Text(stringResource(R.string.settings_meal_scanner_groq_support)) }
                        )
                    }

                    // ─── Config Mistral ───
                    if (profile.mealScanProvider == "MISTRAL") {
                        Text(stringResource(R.string.settings_meal_scanner_mistral_model),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        var showMistralKey by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.mistralApiKey,
                            onValueChange = { viewModel.updateMistralApiKey(it.trim()) },
                            label = { Text(stringResource(R.string.settings_meal_scanner_api_key_mistral)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showMistralKey) androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = {
                                        clipboardMgr.getText()?.text?.let { viewModel.updateMistralApiKey(it.trim()) }
                                    }) { Icon(Icons.Default.ContentPaste, stringResource(R.string.cd_paste), tint = OrangeVibrant) }
                                    IconButton(onClick = { showMistralKey = !showMistralKey }) {
                                        Icon(if (showMistralKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.cd_show_password))
                                    }
                                }
                            },
                            placeholder = { Text("eRTo...", style = MaterialTheme.typography.bodySmall) },
                            supportingText = { Text(stringResource(R.string.settings_meal_scanner_mistral_support)) }
                        )
                    }
                }

                // ═══ ASSISTANT IA (Shreddy) ═══
                SettingsSection(stringResource(R.string.settings_section_assistant), Icons.Default.AutoAwesome) {
                    // Provider selector
                    Text(stringResource(R.string.settings_assistant_provider_label),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GROQ" to "Groq", "OPENAI" to "OpenAI", "CLAUDE" to "Claude").forEach { (code, label) ->
                            val selected = profile.llmProvider == code
                            Surface(
                                onClick = { viewModel.updateLlmProvider(code) },
                                modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = if (selected) 0.dp else 1.dp
                            ) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // API Key
                    var showKey by remember { mutableStateOf(false) }
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    OutlinedTextField(
                        value = state.llmApiKey,
                        onValueChange = { viewModel.updateLlmApiKey(it.trim()) },
                        label = { Text(stringResource(R.string.settings_assistant_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            Row {
                                // Bouton coller depuis le presse-papier
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.let { pasted ->
                                        viewModel.updateLlmApiKey(pasted.trim())
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, stringResource(R.string.cd_paste), tint = OrangeVibrant)
                                }
                                // Toggle visibilité
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.cd_toggle_password))
                                }
                            }
                        },
                        placeholder = { Text(stringResource(R.string.settings_assistant_api_key_placeholder), style = MaterialTheme.typography.bodySmall) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                            autoCorrect = false
                        )
                    )
                    // Model override (optionnel)
                    OutlinedTextField(
                        value = profile.llmModel,
                        onValueChange = { viewModel.updateLlmModel(it) },
                        label = { Text(stringResource(R.string.settings_assistant_model_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            val defaultModel = when (profile.llmProvider) {
                                "OPENAI" -> "gpt-4o-mini"; "CLAUDE" -> "claude-sonnet-4-20250514"; else -> "openai/gpt-oss-120b"
                            }
                            Text(stringResource(R.string.settings_assistant_model_default, defaultModel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    )
                }

                // ═══ CONFIDENTIALITÉ & DONNÉES (RGPD) ═══
                SettingsSection(stringResource(R.string.settings_section_privacy), Icons.Default.PrivacyTip) {
                    com.shredcoach.app.presentation.legal.LegalSettingsSection(
                        navController = navController,
                        snackbar = snackbarHostState
                    )
                }

                // ═══ APP ═══
                SettingsSection(stringResource(R.string.settings_section_about), Icons.Default.Info) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.settings_about_version_label), style = MaterialTheme.typography.bodyMedium)
                        Text("2.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ═══════════════════════════════════════
// PROFILE HEADER — Premium card
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeaderCard(
    profile: com.shredcoach.app.data.local.entity.UserProfileEntity,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayName = "${profile.firstName.replaceFirstChar { it.uppercase() }} ${profile.lastName.replaceFirstChar { it.uppercase() }}".trim()
    val goalLabel = stringResource(profile.goal.displayNameRes)
    val levelLabel = stringResource(profile.level.displayNameRes)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        OrangeVibrant.copy(alpha = 0.95f),
                        OrangeVibrant.copy(alpha = 0.72f)
                    )
                )
            ).padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ─── Avatar + nom + chevron ───
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Avatar
                    Box(
                        Modifier.size(64.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val photoPath = profile.profilePhotoPath
                        if (photoPath != null) {
                            coil.compose.AsyncImage(
                                model = coil.request.ImageRequest.Builder(context)
                                    .data(java.io.File(photoPath)).crossfade(true).build(),
                                contentDescription = stringResource(R.string.cd_profile_photo),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                        } else {
                            Text(
                                profile.firstName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_profile_header_greeting),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
                        Text(
                            displayName.ifBlank { stringResource(R.string.settings_profile_header_default_name) },
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            fontWeight = FontWeight.ExtraBold, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        // Tags goal + level
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            MiniTag(goalLabel)
                            MiniTag(levelLabel)
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight, null, tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // ─── Stats row ───
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.18f)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    HeaderStat("${profile.currentWeightKg.toInt()}", stringResource(R.string.settings_profile_header_stat_kg))
                    HeaderDivider()
                    HeaderStat("${profile.targetWeightKg.toInt()}", stringResource(R.string.settings_profile_header_stat_target))
                    HeaderDivider()
                    HeaderStat("${profile.totalWorkouts}", stringResource(R.string.settings_profile_header_stat_workouts))
                    HeaderDivider()
                    HeaderStatWithIcon("${profile.currentStreakDays}", stringResource(R.string.settings_profile_header_stat_streak), Icons.Default.LocalFireDepartment)
                }
            }
        }
    }
}

@Composable
private fun MiniTag(label: String) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.White.copy(alpha = 0.22f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HeaderStatWithIcon(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = Color.White)
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HeaderDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.18f)))
}

// ═══════════════════════════════════════
// COMPOSANTS SETTINGS
// ═══════════════════════════════════════

/**
 * Entry de langue style "row clickable" (≠ section expandable). UX standard
 * Android Settings : tap → écran dédié au lieu d'inline. Cohérent avec le
 * pattern "Profile" / "Privacy Policy" déjà utilisé dans cet écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSettingEntry(
    profile: com.shredcoach.app.data.local.entity.UserProfileEntity,
    onClick: () -> Unit,
) {
    val current = remember(profile.languageTag) {
        com.shredcoach.app.domain.locale.AppLocale.fromTag(profile.languageTag)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = OrangeVibrant,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.res.stringResource(com.shredcoach.app.R.string.settings_section_language_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${current.flag} ${current.displayNameNative}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron"
    )

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(300)),
        ) {
            // Header cliquable
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, null, Modifier.size(24.dp), tint = OrangeVibrant)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, stringResource(R.string.cd_toggle), Modifier.size(24.dp).rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
            // Contenu collapsible
            if (expanded) {
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, range: IntRange, step: Int, unit: String, onValueChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("$value $unit", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = OrangeVibrant)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(((it / step).toInt() * step).coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChipGroupSetting(title: String, options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f).fillMaxHeight().defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = if (selected) 0.dp else 1.dp
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// PALETTE PICKER — sélecteur visuel de thème couleurs (Apple-like)
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PalettePicker(
    selectedKey: String,
    isDark: Boolean,
    onSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Sur Android 12+, on prepend une carte "Système" qui reflète live les
    // couleurs Material You extraites du wallpaper. Preview immédiat de l'effet
    // dynamic. Sur API < 31, l'option n'est pas affichée (Theme.kt fallback
    // déjà sunset si la clé "system" arrive sur un OS trop vieux).
    val systemPaletteName = stringResource(R.string.settings_appearance_palette_system)
    val systemPalette: ShredPalette? = if (supportsDynamic) {
        val dyn = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        ShredPalette(
            key = SYSTEM_PALETTE_KEY,
            displayName = systemPaletteName,
            icon = "✨",
            primary = dyn.primary,
            primaryContainer = dyn.primaryContainer,
            secondary = dyn.secondary,
            secondaryContainer = dyn.secondaryContainer,
            success = ShredPalettes.SemanticSuccess,
            warning = ShredPalettes.SemanticWarning,
            info = ShredPalettes.SemanticInfo,
            error = if (isDark) ShredPalettes.SemanticErrorDark
                else ShredPalettes.SemanticErrorLight,
            background = dyn.background,
            surface = dyn.surface,
            surfaceVariant = dyn.surfaceVariant,
            onBackground = dyn.onBackground,
            onSurface = dyn.onSurface,
            onSurfaceVariant = dyn.onSurfaceVariant,
            isDark = isDark
        )
    } else null

    val palettes = buildList {
        systemPalette?.let { add(it) }
        addAll(ShredPalettes.all.map { (light, dark) -> if (isDark) dark else light })
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(palettes, key = { it.key }) { palette ->
            PaletteCard(
                palette = palette,
                selected = palette.key == selectedKey,
                onClick = { onSelected(palette.key) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteCard(
    palette: com.shredcoach.app.presentation.theme.ShredPalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.95f,
        animationSpec = tween(250),
        label = "paletteScale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(108.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.5.dp, palette.primary)
            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        tonalElevation = if (selected) 3.dp else 0.dp,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Aperçu visuel : gradient primary → secondary
            Box(
                Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(palette.primary, palette.secondary)
                            )
                        )
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check, null,
                        Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(palette.icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    palette.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) palette.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
