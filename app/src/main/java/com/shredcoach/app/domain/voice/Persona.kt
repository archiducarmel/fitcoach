package com.shredcoach.app.domain.voice

/**
 * Identifie un moteur de synthèse vocale disponible dans l'app.
 *
 * Chaque moteur a son propre catalogue de personae (cf. [VoicePersonaRegistry])
 * et sa propre implémentation [VoiceEngine].
 */
enum class VoiceEngineId(
    val displayName: String,
    val tagline: String,
    val requiresApiKey: Boolean,
) {
    /** TextToSpeech système — gratuit, hors-ligne, qualité variable selon device. */
    ANDROID(
        displayName = "Android (système)",
        tagline = "Gratuit · hors-ligne",
        requiresApiKey = false,
    ),

    /**
     * Google Cloud Text-to-Speech, voix neuronales **Chirp 3 HD**.
     * Streaming MP3 + cache local. Qualité studio, prosodie naturelle FR.
     */
    GOOGLE_CHIRP3(
        displayName = "Google Cloud · Chirp 3 HD",
        tagline = "Voix neuronale studio-grade",
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
    val tagline: String,
    val gender: Gender,
    val engine: VoiceEngineId,
    val engineVoiceId: String,
    val avatarEmoji: String,
    val speakingRate: Float = 1.05f,
)
