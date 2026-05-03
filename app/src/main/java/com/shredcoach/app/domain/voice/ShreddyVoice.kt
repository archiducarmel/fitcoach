package com.shredcoach.app.domain.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voix de Shreddy — wrapper autour de Android TextToSpeech.
 * Sélectionne automatiquement la meilleure voix française disponible.
 * Singleton injectable via Hilt.
 */
@Singleton
class ShreddyVoice @Inject constructor() {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val restEndPhrases = listOf(
        "C'est reparti !",
        "On enchaîne !",
        "Série suivante !",
        "Allez, on y retourne !",
        "Go, c'est à toi !",
        "Repos terminé, on repart !",
        "C'est le moment, donne tout !"
    )

    private val countdownPhrases = mapOf(
        10 to "10 secondes",
        5 to "5",
        4 to "4",
        3 to "3",
        2 to "2",
        1 to "1"
    )

    private var phraseIndex = 0

    fun init(context: Context) {
        if (tts != null) return

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val result = engine.setLanguage(Locale.FRANCE)

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("ShreddyVoice", "Français non supporté, fallback anglais")
                    engine.setLanguage(Locale.US)
                }

                // Sélectionner la meilleure voix disponible
                selectBestVoice(engine)

                // Ton coach : légèrement plus grave, rythme dynamique
                engine.setPitch(0.95f)
                engine.setSpeechRate(1.05f)

                isReady = true
                Log.i("ShreddyVoice", "TTS prêt — voix: ${engine.voice?.name ?: "défaut"}")
            } else {
                Log.e("ShreddyVoice", "Échec init TTS: $status")
            }
        }
    }

    private fun selectBestVoice(engine: TextToSpeech) {
        try {
            val voices = engine.voices ?: return
            // Chercher une voix française de haute qualité (neuronale)
            val frenchVoices = voices.filter {
                it.locale.language == "fr" && !it.isNetworkConnectionRequired
            }.sortedByDescending { it.quality }

            val bestVoice = frenchVoices.firstOrNull { it.quality >= Voice.QUALITY_HIGH }
                ?: frenchVoices.firstOrNull { it.quality >= Voice.QUALITY_NORMAL }
                ?: frenchVoices.firstOrNull()

            if (bestVoice != null) {
                engine.voice = bestVoice
                Log.i("ShreddyVoice", "Voix sélectionnée: ${bestVoice.name} (qualité: ${bestVoice.quality})")
            }
        } catch (e: Exception) {
            Log.w("ShreddyVoice", "Impossible de sélectionner la voix: ${e.message}")
        }
    }

    /** Prononce un texte. */
    fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shreddy_${System.currentTimeMillis()}")
    }

    /** Annonce de fin de repos (phrase variée). */
    fun speakRestEnd() {
        val phrase = restEndPhrases[phraseIndex % restEndPhrases.size]
        phraseIndex++
        speak(phrase)
    }

    /** Countdown vocal (5, 3, 2, 1). */
    fun speakCountdown(secondsRemaining: Int) {
        countdownPhrases[secondsRemaining]?.let { speak(it) }
    }

    /** Libérer les ressources. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
