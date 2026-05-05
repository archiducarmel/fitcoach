package com.shredcoach.app.presentation.settings


import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsState(
    val profile: UserProfileEntity? = null,
    val isLoading: Boolean = true,
    val saved: Boolean = false,
    // Clés API : lues depuis SecureKeyStore (chiffré), pas Room.
    val llmApiKey: String = "",
    val geminiApiKey: String = "",
    val groqMealApiKey: String = "",
    val mistralApiKey: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.getUserProfile().collect { profile ->
                _state.update { it.copy(profile = profile, isLoading = false) }
            }
        }
        refreshApiKeys()
    }

    /** Recharge les 4 clés depuis le SecureKeyStore. */
    private fun refreshApiKeys() {
        _state.update {
            it.copy(
                llmApiKey = userRepository.getApiKey(SecureKeyStore.Provider.LLM),
                geminiApiKey = userRepository.getApiKey(SecureKeyStore.Provider.GEMINI),
                groqMealApiKey = userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL),
                mistralApiKey = userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
            )
        }
    }

    // Séance
    fun updateAutoStartAfterRest(value: Boolean) = updateProfile { it.copy(autoStartAfterRest = value) }
    fun updateVibration(value: Boolean) = updateProfile { it.copy(vibrationEnabled = value) }
    fun updateSound(value: Boolean) = updateProfile { it.copy(soundEnabled = value) }
    fun updateVoiceEnabled(value: Boolean) = updateProfile { it.copy(voiceEnabled = value) }
    fun updateShowCoachTips(value: Boolean) = updateProfile { it.copy(showCoachTips = value) }
    fun updateSuggestBonusSeries(value: Boolean) = updateProfile { it.copy(suggestBonusSeries = value) }
    fun updateHealthNotes(value: String) = updateProfile { it.copy(healthNotes = value) }
    fun updateDefaultRest(value: Int) = updateProfile { it.copy(defaultRestSeconds = value) }
    fun updateDuration(value: Int) = updateProfile { it.copy(preferredWorkoutDuration = value) }
    fun updateLevel(value: FitnessLevel) = updateProfile { it.copy(level = value) }
    fun updateEquipment(value: EquipmentType) = updateProfile { it.copy(equipment = value) }
    fun updateGoal(value: FitnessGoal) = updateProfile { it.copy(goal = value) }
    fun updateFirstName(value: String) = updateProfile { it.copy(firstName = value) }
    // Notifications
    fun updateNotificationsEnabled(v: Boolean) = updateProfile { it.copy(notificationsEnabled = v) }
    fun updateNotifBreakfast(v: Boolean) = updateProfile { it.copy(notifBreakfast = v) }
    fun updateNotifLunch(v: Boolean) = updateProfile { it.copy(notifLunch = v) }
    fun updateNotifSnack(v: Boolean) = updateProfile { it.copy(notifSnack = v) }
    fun updateNotifDinner(v: Boolean) = updateProfile { it.copy(notifDinner = v) }
    fun updateNotifShaker(v: Boolean) = updateProfile { it.copy(notifShaker = v) }
    fun updateNotifBedtime(v: Boolean) = updateProfile { it.copy(notifBedtime = v) }
    fun updateNotifMotivation(v: Boolean) = updateProfile { it.copy(notifMotivation = v) }
    fun updateNotifMealDebrief(v: Boolean) = updateProfile { it.copy(notifMealDebrief = v) }
    fun updateNotifWorkoutDebrief(v: Boolean) = updateProfile { it.copy(notifWorkoutDebrief = v) }
    fun updateMealDebriefDelay(minutes: Int) = updateProfile { it.copy(mealDebriefDelayMinutes = minutes.coerceIn(5, 240)) }
    fun updateWorkoutDebriefDelay(minutes: Int) = updateProfile { it.copy(workoutDebriefDelayMinutes = minutes.coerceIn(5, 240)) }
    // Display
    fun updateDarkMode(v: String) = updateProfile { it.copy(darkMode = v) }
    fun updateThemePalette(key: String) = updateProfile { it.copy(themePalette = key) }
    fun updateUseImperial(v: Boolean) = updateProfile { it.copy(useImperial = v) }
    // Meal Scanner — provider/model restent dans Room, mais clés API → SecureKeyStore
    fun updateMealScanProvider(v: String) = updateProfile { it.copy(mealScanProvider = v) }
    fun updateGeminiModel(v: String) = updateProfile { it.copy(geminiModel = v) }
    fun updateGeminiApiKey(v: String) = setApiKey(SecureKeyStore.Provider.GEMINI, v)
    fun updateGroqMealApiKey(v: String) = setApiKey(SecureKeyStore.Provider.GROQ_MEAL, v)
    fun updateMistralApiKey(v: String) = setApiKey(SecureKeyStore.Provider.MISTRAL, v)
    // Assistant IA — provider/model restent dans Room, clé API → SecureKeyStore
    fun updateLlmProvider(v: String) = updateProfile { it.copy(llmProvider = v) }
    fun updateLlmModel(v: String) = updateProfile { it.copy(llmModel = v) }
    fun updateLlmApiKey(v: String) = setApiKey(SecureKeyStore.Provider.LLM, v)

    private fun setApiKey(provider: SecureKeyStore.Provider, value: String) {
        userRepository.setApiKey(provider, value)
        refreshApiKeys()
        _state.update { it.copy(saved = true) }
    }

    private fun updateProfile(transform: (UserProfileEntity) -> UserProfileEntity) {
        viewModelScope.launch {
            val current = _state.value.profile ?: return@launch
            val updated = transform(current)
            userRepository.updateUserProfile(updated)
            _state.update { it.copy(saved = true) }
        }
    }

    fun ensureProfileExists() {
        viewModelScope.launch {
            val existing = userRepository.getUserProfileOnce()
            if (existing == null) {
                userRepository.insertUserProfile(
                    UserProfileEntity(firstName = "Athlète")
                )
            }
        }
    }
}
