package com.shredcoach.app.domain.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [VoiceEngine] basée sur le `TextToSpeech` Android natif.
 *
 * **Sélection persona-aware** : Android n'expose pas le genre d'une voix
 * dans son API. On applique une heuristique sur le nom (`f`, `female`,
 * `e-f`, etc.) pour matcher [Persona.gender]. Cache des deux meilleures
 * voix (M/F) calculé une fois à init pour éviter les itérations répétées.
 *
 * **Pourquoi pas de fallback inter-engine ici** : ce moteur est l'ULTIME
 * recours (gratuit, hors-ligne). C'est lui qui sert de fallback aux autres,
 * jamais l'inverse.
 */
@Singleton
class AndroidTtsEngine @Inject constructor() : VoiceEngine {

    override val id: VoiceEngineId = VoiceEngineId.ANDROID

    private var tts: TextToSpeech? = null

    @Volatile private var ready: Boolean = false

    /** Meilleure voix masculine FR sélectionnée à l'init (cachée). */
    private var bestMaleVoice: Voice? = null

    /** Meilleure voix féminine FR sélectionnée à l'init (cachée). */
    private var bestFemaleVoice: Voice? = null

    /** Voix par défaut si aucune n'a pu être catégorisée par genre. */
    private var fallbackVoice: Voice? = null

    override val isReady: Boolean
        get() = ready

    override fun init(context: Context) {
        if (tts != null) return

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Échec init TTS: $status")
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val result = engine.setLanguage(Locale.FRANCE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Français non supporté, fallback anglais")
                engine.setLanguage(Locale.US)
            }
            cacheBestVoicesByGender(engine)
            ready = true
            Log.i(TAG, "TTS prêt — M=${bestMaleVoice?.name}, F=${bestFemaleVoice?.name}")
        }
    }

    private fun cacheBestVoicesByGender(engine: TextToSpeech) {
        try {
            val voices = engine.voices ?: return
            val frenchVoices = voices.filter {
                it.locale.language == "fr" && !it.isNetworkConnectionRequired
            }.sortedByDescending { it.quality }

            fallbackVoice = frenchVoices.firstOrNull { it.quality >= Voice.QUALITY_HIGH }
                ?: frenchVoices.firstOrNull { it.quality >= Voice.QUALITY_NORMAL }
                ?: frenchVoices.firstOrNull()

            // Heuristique nom — Android ne donne pas le genre dans l'API.
            // Patterns observés sur les voix Google/Samsung TTS :
            //   "fr-fr-x-mab-female-...", "fr-fr-x-vlf-male-..."
            //   "fr-FR-language", "fra-FRA-default-network"
            //   "fr-FR-Standard-A" (femme), "fr-FR-Standard-B" (homme), etc.
            bestFemaleVoice = frenchVoices.firstOrNull { isFemale(it.name) } ?: fallbackVoice
            bestMaleVoice = frenchVoices.firstOrNull { isMale(it.name) }
                ?: frenchVoices.firstOrNull { v -> v != bestFemaleVoice }
                ?: fallbackVoice
        } catch (e: Exception) {
            Log.w(TAG, "Sélection voix par genre impossible: ${e.message}")
        }
    }

    private fun isFemale(voiceName: String): Boolean {
        val n = voiceName.lowercase(Locale.ROOT)
        if (n.contains("female")) return true
        // Standard-A/C/E sont féminines, B/D/F masculines (convention Google TTS).
        if (n.endsWith("-a") || n.endsWith("-c") || n.endsWith("-e")) return true
        return false
    }

    private fun isMale(voiceName: String): Boolean {
        val n = voiceName.lowercase(Locale.ROOT)
        if (n.contains("male") && !n.contains("female")) return true
        if (n.endsWith("-b") || n.endsWith("-d") || n.endsWith("-f")) return true
        return false
    }

    override fun speak(text: String, persona: Persona) {
        val engine = tts ?: return
        if (!ready) return

        // Picks la voix mappée au genre de la persona, avec fallback safe.
        val target = when (persona.gender) {
            Gender.MALE -> bestMaleVoice ?: fallbackVoice
            Gender.FEMALE -> bestFemaleVoice ?: fallbackVoice
        }
        if (target != null && target != engine.voice) {
            engine.voice = target
        }
        engine.setPitch(if (persona.gender == Gender.MALE) 0.95f else 1.05f)
        engine.setSpeechRate(persona.speakingRate)

        engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "shreddy_${System.currentTimeMillis()}",
        )
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "AndroidTtsEngine"
    }
}
