package com.shredcoach.app.domain.voice

import androidx.annotation.StringRes
import com.shredcoach.app.R

/**
 * Identifie un moteur de synthèse vocale disponible dans l'app.
 *
 * Chaque moteur a son propre catalogue de personae (cf. [VoicePersonaRegistry])
 * et sa propre implémentation [VoiceEngine].
 */
enum class VoiceEngineId(
    val displayName: String,
    val tagline: String,
    @StringRes val displayNameRes: Int,
    @StringRes val taglineRes: Int,
    val requiresApiKey: Boolean,
) {
    /** TextToSpeech système — gratuit, hors-ligne, qualité variable selon device. */
    ANDROID(
        displayName = "Android (système)",
        tagline = "Gratuit · hors-ligne",
        displayNameRes = R.string.voice_engine_android,
        taglineRes = R.string.voice_engine_android_tagline,
        requiresApiKey = false,
    ),

    /**
     * Google Cloud Text-to-Speech, voix neuronales **Chirp 3 HD**.
     * Streaming MP3 + cache local. Qualité studio, prosodie naturelle FR.
     */
    GOOGLE_CHIRP3(
        displayName = "Google Cloud · Chirp 3 HD",
        tagline = "Voix neuronale studio-grade",
        displayNameRes = R.string.voice_engine_chirp3,
        taglineRes = R.string.voice_engine_chirp3_tagline,
        requiresApiKey = true,
    );

    companion object {
        fun fromKey(key: String?): VoiceEngineId =
            values().firstOrNull { it.name == key } ?: ANDROID
    }
}

enum class Gender { MALE, FEMALE }

/**
 * Une "voix de personnage" sélectionnable par l'utilisateur.
 *
 * Chaque persona est lié à un [VoiceEngineId] précis ; changer de moteur
 * implique de re-sélectionner une persona du nouveau moteur (le défaut est
 * appliqué automatiquement par [VoicePersonaRegistry.defaultPersonaFor]).
 *
 * @property id stable, persisté dans [VoiceSettingsStore]. Format
 *   `<personnage>_<engine_short>` ex: `marcus_chirp`, `lea_android`.
 * @property engineVoiceId identifiant côté provider :
 *   - Android: locale tag (`fr-FR`) — le moteur sélectionne la meilleure
 *     voix de ce locale matchant le [gender] (heuristique sur le nom).
 *   - Google Chirp 3: nom complet `fr-FR-Chirp3-HD-Charon`.
 * @property speakingRate vitesse côté Google API (Chirp 3 supporte le rate
 *   mais PAS le pitch). Côté Android on l'applique aussi via setSpeechRate.
 */
data class Persona(
    val id: String,
    val displayName: String,
    /**
     * Tagline FR par défaut (DB-stable, fallback si la locale n'a pas de
     * traduction de [taglineRes]). L'UI doit toujours préférer
     * `stringResource(persona.taglineRes)` pour un rendu locale-aware.
     */
    val tagline: String,
    @androidx.annotation.StringRes val taglineRes: Int,
    val gender: Gender,
    val engine: VoiceEngineId,
    val engineVoiceId: String,
    val avatarEmoji: String,
    val speakingRate: Float = 1.05f,
) {
    /**
     * Résout le voice ID pour la locale courante (fr-FR, en-US, …).
     *
     * **Règle de mapping** :
     *  - ANDROID : retourne un BCP-47 region tag (`fr-FR`, `en-US`, …) que
     *    [TextToSpeech.setLanguage] sait consommer. Le moteur Android pickera
     *    ensuite la meilleure voix locale matchant le [gender].
     *  - GOOGLE_CHIRP3 : remplace le préfixe `xx-XX-` du voice ID figé en
     *    base par le préfixe de la locale courante. Les "characters" Chirp 3 HD
     *    (Charon, Aoede, Puck, Kore) sont disponibles cross-langue côté Google
     *    Cloud TTS — donc swap du préfixe = même persona dans la nouvelle voix.
     *
     * Locales V1 supportées : fr, en. V2 (es/it/pt/de) : fallback FR pour
     * l'instant, à activer quand les phrasebooks correspondants seront prêts.
     */
    fun engineVoiceIdForLocale(localeTag: String): String {
        val regionTag = bcp47RegionTag(localeTag)
        return when (engine) {
            VoiceEngineId.ANDROID -> regionTag
            VoiceEngineId.GOOGLE_CHIRP3 ->
                engineVoiceId.replaceFirst(Regex("^[a-z]{2}-[A-Z]{2}"), regionTag)
        }
    }

    private fun bcp47RegionTag(localeTag: String): String = when (localeTag.lowercase()) {
        "en" -> "en-US"
        "es" -> "es-ES"
        "it" -> "it-IT"
        "pt" -> "pt-PT"
        "de" -> "de-DE"
        else -> "fr-FR"
    }
}
