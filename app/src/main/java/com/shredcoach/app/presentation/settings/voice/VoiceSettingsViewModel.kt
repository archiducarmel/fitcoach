package com.shredcoach.app.presentation.settings.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.domain.voice.Persona
import com.shredcoach.app.domain.voice.ShreddyVoice
import com.shredcoach.app.domain.voice.VoiceEngineId
import com.shredcoach.app.domain.voice.VoicePersonaRegistry
import com.shredcoach.app.domain.voice.VoiceSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel de la section "Voix Shreddy" dans Settings.
 *
 * Expose :
 *  - le moteur sélectionné + la persona sélectionnée (depuis [VoiceSettingsStore]),
 *  - la liste des personae filtrée selon le moteur,
 *  - la clé API Google TTS (depuis [SecureKeyStore]),
 *  - un sample text utilisé pour le bouton "Tester la voix".
 *
 * Côté actions :
 *  - changement d'engine ou de persona persisté immédiatement,
 *  - clé API stockée chiffrée,
 *  - preview délègue à [ShreddyVoice.speak] avec un texte démo.
 */
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    application: Application,
    private val voiceSettings: VoiceSettingsStore,
    private val secureKeyStore: SecureKeyStore,
    private val shreddyVoice: ShreddyVoice,
) : AndroidViewModel(application) {

    /**
     * La clé API n'a pas de Flow natif depuis [SecureKeyStore] (c'est un
     * EncryptedSharedPreferences synchrone). On la matérialise dans un
     * MutableStateFlow et on resync à chaque set/clear.
     */
    private val _googleApiKey = MutableStateFlow(secureKeyStore.getKey(SecureKeyStore.Provider.GOOGLE_TTS))
    val googleApiKey: StateFlow<String> = _googleApiKey.asStateFlow()

    val state: StateFlow<UiState> = combine(
        voiceSettings.snapshot,
        _googleApiKey,
    ) { snap, apiKey ->
        val personae = VoicePersonaRegistry.personaeFor(snap.engineId)
        UiState(
            engineId = snap.engineId,
            personaId = snap.personaId,
            personaeForCurrentEngine = personae,
            googleApiKeyConfigured = apiKey.isNotBlank(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(
            engineId = VoiceEngineId.ANDROID,
            personaId = VoicePersonaRegistry.defaultPersonaFor(VoiceEngineId.ANDROID).id,
            personaeForCurrentEngine = VoicePersonaRegistry.androidPersonae,
            googleApiKeyConfigured = secureKeyStore.hasKey(SecureKeyStore.Provider.GOOGLE_TTS),
        ),
    )

    fun selectEngine(engineId: VoiceEngineId) {
        viewModelScope.launch { voiceSettings.setEngine(engineId) }
    }

    fun selectPersona(persona: Persona) {
        viewModelScope.launch { voiceSettings.setPersona(persona.id) }
    }

    fun updateGoogleApiKey(key: String) {
        val cleaned = key.trim()
        if (cleaned.isEmpty()) {
            secureKeyStore.clear(SecureKeyStore.Provider.GOOGLE_TTS)
        } else {
            secureKeyStore.setKey(SecureKeyStore.Provider.GOOGLE_TTS, cleaned)
        }
        _googleApiKey.value = cleaned
    }

    /**
     * Joue un échantillon vocal avec la persona courante. Utilisé par le
     * bouton "Tester la voix" — donne à l'utilisateur un retour immédiat
     * AVANT de quitter Settings et de tomber sur sa première séance.
     */
    fun playPreview() {
        val pl = com.shredcoach.app.domain.i18n.PromptLocale
        val phrases = when {
            pl.isFr() -> SAMPLE_PHRASES_FR
            pl.isEs() -> SAMPLE_PHRASES_ES
            pl.isIt() -> SAMPLE_PHRASES_IT
            pl.isPt() -> SAMPLE_PHRASES_PT
            pl.isDe() -> SAMPLE_PHRASES_DE
            else -> SAMPLE_PHRASES_EN
        }
        shreddyVoice.speak(phrases.random())
    }

    data class UiState(
        val engineId: VoiceEngineId,
        val personaId: String,
        val personaeForCurrentEngine: List<Persona>,
        val googleApiKeyConfigured: Boolean,
    )

    private companion object {
        /**
         * Phrases démo : doivent illustrer le ton coach + sonner naturel
         * dans la langue cible. On évite les chiffres seuls (countdown) pour
         * mieux juger de la prosodie. Cascade FR ↔ EN selon la locale courante
         * (cohérent avec [WorkoutVoicePhrasebook] qui utilise EN comme fallback
         * pour ES/IT/PT/DE).
         */
        val SAMPLE_PHRASES_FR = listOf(
            "Allez champion, dernière série, on lâche rien !",
            "Tu progresses bien, garde ce rythme.",
            "Repos terminé, on enchaîne avec puissance.",
            "Top exécution, on continue sur cette dynamique.",
        )
        val SAMPLE_PHRASES_EN = listOf(
            "Come on champ, last set, don't give up!",
            "You're making great progress, keep this rhythm.",
            "Rest's over, let's go with power.",
            "Top execution, keep this momentum going.",
        )
        val SAMPLE_PHRASES_ES = listOf(
            "¡Vamos campeón, última serie, no aflojes!",
            "Estás progresando bien, mantén ese ritmo.",
            "Descanso terminado, seguimos con fuerza.",
            "Excelente ejecución, sigamos con esta dinámica.",
        )
        val SAMPLE_PHRASES_IT = listOf(
            "Forza campione, ultima serie, non mollare!",
            "Stai progredendo bene, mantieni questo ritmo.",
            "Recupero finito, andiamo con potenza.",
            "Ottima esecuzione, continuiamo su questa dinamica.",
        )
        val SAMPLE_PHRASES_PT = listOf(
            "Vamos campeão, última série, não desista!",
            "Você está progredindo bem, mantenha esse ritmo.",
            "Descanso acabou, bora com força.",
            "Excelente execução, segue nessa dinâmica.",
        )
        val SAMPLE_PHRASES_DE = listOf(
            "Komm schon Champion, letzter Satz, nicht aufgeben!",
            "Du machst gute Fortschritte, halte diesen Rhythmus.",
            "Pause vorbei, weiter mit Power.",
            "Top-Ausführung, halte dieses Momentum.",
        )
    }
}
