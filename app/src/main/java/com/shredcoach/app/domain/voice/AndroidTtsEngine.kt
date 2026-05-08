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

    /** Meilleure voix masculine sélectionnée pour la locale [cachedLocaleTag]. */
    private var bestMaleVoice: Voice? = null

    /** Meilleure voix féminine sélectionnée pour la locale [cachedLocaleTag]. */
    private var bestFemaleVoice: Voice? = null

    /** Voix par défaut si aucune n'a pu être catégorisée par genre. */
    private var fallbackVoice: Voice? = null

    /**
     * Tag BCP-47 de la locale pour laquelle [bestMaleVoice]/[bestFemaleVoice]
     * ont été cachées. Si `Locale.getDefault().language` diffère lors d'un
     * `speak()`, on re-cache. **Sans ce mécanisme** : le singleton survit
     * au recreate de l'Activity post-changement de langue, et continue à
     * utiliser la voix FR pour énoncer du texte ES/IT/PT/DE → user entend
     * "¡Vamos campeón!" prononcé avec phonèmes français.
     */
    @Volatile private var cachedLocaleTag: String = ""

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
            applyLocaleAndRecache(engine, Locale.getDefault())
            ready = true
            Log.i(TAG, "TTS prêt — locale=$cachedLocaleTag M=${bestMaleVoice?.name} F=${bestFemaleVoice?.name}")
        }
    }

    /**
     * Applique la locale au moteur TTS et re-cache les meilleures voix M/F.
     * Idempotent : appelable depuis [init] (boot) ET [speak] (si la locale
     * a changé depuis le dernier cache).
     */
    private fun applyLocaleAndRecache(engine: TextToSpeech, targetLocale: Locale) {
        val result = engine.setLanguage(targetLocale)
        val effective = if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $targetLocale non supporté TTS, fallback en-US")
            engine.setLanguage(Locale.US)
            Locale.US
        } else {
            targetLocale
        }
        cacheBestVoicesByGender(engine, effective)
        cachedLocaleTag = effective.language.lowercase()
    }

    private fun cacheBestVoicesByGender(engine: TextToSpeech, targetLocale: Locale) {
        try {
            val voices = engine.voices ?: return
            val targetLang = targetLocale.language
            // On filtre les voix matchant la langue active. Si aucune voix locale
            // n'existe (ex: device sans pack EN installé), on retombe sur les
            // voix offline disponibles dans toute langue pour ne pas planter.
            val localeVoices = voices.filter {
                it.locale.language == targetLang && !it.isNetworkConnectionRequired
            }.sortedByDescending { it.quality }
            val candidateVoices = localeVoices.ifEmpty {
                voices.filter { !it.isNetworkConnectionRequired }
                    .sortedByDescending { it.quality }
            }

            fallbackVoice = candidateVoices.firstOrNull { it.quality >= Voice.QUALITY_HIGH }
                ?: candidateVoices.firstOrNull { it.quality >= Voice.QUALITY_NORMAL }
                ?: candidateVoices.firstOrNull()

            // Heuristique nom — Android ne donne pas le genre dans l'API.
            // Patterns observés sur les voix Google/Samsung TTS :
            //   "fr-fr-x-mab-female-...", "fr-fr-x-vlf-male-..."
            //   "fr-FR-language", "fra-FRA-default-network"
            //   "fr-FR-Standard-A" (femme), "fr-FR-Standard-B" (homme), etc.
            bestFemaleVoice = candidateVoices.firstOrNull { isFemale(it.name) } ?: fallbackVoice
            bestMaleVoice = candidateVoices.firstOrNull { isMale(it.name) }
                ?: candidateVoices.firstOrNull { v -> v != bestFemaleVoice }
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

        // **Locale-aware reactive** : si la locale a changé depuis le dernier
        // cache (ex: user a switché FR → ES via Settings), on re-applique la
        // langue au moteur TTS et on re-cache les meilleures voix M/F dans
        // cette langue. Sans ça, le singleton survivant au recreate Activity
        // continue à parler avec la voix FR cachée à l'init.
        val currentLang = Locale.getDefault().language.lowercase()
        if (currentLang != cachedLocaleTag) {
            Log.i(TAG, "Locale changée ($cachedLocaleTag → $currentLang), re-cache voices")
            applyLocaleAndRecache(engine, Locale.getDefault())
        }

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
