package com.shredcoach.app.domain.voice

import com.shredcoach.app.R

/**
 * Catalogue **curaté** des personae disponibles dans l'app.
 *
 * Garde-fous design :
 * - Le set est volontairement réduit (4 par moteur) pour éviter la paralysie
 *   de choix. Chaque persona a une identité claire (nom + tagline + avatar).
 * - Les noms (Marcus, Léa, Hugo, Sophie) sont conservés ENTRE les moteurs :
 *   l'utilisateur retrouve "Marcus" dans Android comme dans Chirp, ce qui
 *   réduit la friction au moment du switch d'engine.
 * - Pour Chirp 3 HD, on choisit 4 voix françaises dont les caractéristiques
 *   sont bien différenciées (chaude grave / vibrante / énergique / posée).
 *
 * **Ne PAS exposer ici toutes les 30+ voix Chirp** : la valeur perçue
 * "voix premium au choix" se construit avec une sélection éditorialisée,
 * pas avec un dump exhaustif. Les voix non-listées peuvent être ajoutées
 * plus tard via une feature "voix bonus" gated.
 */
object VoicePersonaRegistry {

    val androidPersonae: List<Persona> = listOf(
        Persona(
            id = "marcus_android",
            displayName = "Marcus",
            tagline = "Coach posé, voix grave",
            taglineRes = R.string.voice_persona_marcus_android_tagline,
            gender = Gender.MALE,
            engine = VoiceEngineId.ANDROID,
            engineVoiceId = "fr-FR",
            avatarEmoji = "💪",
            speakingRate = 1.02f,
        ),
        Persona(
            id = "lea_android",
            displayName = "Léa",
            tagline = "Pétillante, motivante",
            taglineRes = R.string.voice_persona_lea_android_tagline,
            gender = Gender.FEMALE,
            engine = VoiceEngineId.ANDROID,
            engineVoiceId = "fr-FR",
            avatarEmoji = "✨",
            speakingRate = 1.06f,
        ),
        Persona(
            id = "hugo_android",
            displayName = "Hugo",
            tagline = "Énergique, dynamique",
            taglineRes = R.string.voice_persona_hugo_android_tagline,
            gender = Gender.MALE,
            engine = VoiceEngineId.ANDROID,
            engineVoiceId = "fr-FR",
            avatarEmoji = "🔥",
            speakingRate = 1.10f,
        ),
        Persona(
            id = "sophie_android",
            displayName = "Sophie",
            tagline = "Claire, structurée",
            taglineRes = R.string.voice_persona_sophie_android_tagline,
            gender = Gender.FEMALE,
            engine = VoiceEngineId.ANDROID,
            engineVoiceId = "fr-FR",
            avatarEmoji = "🎯",
            speakingRate = 1.00f,
        ),
    )

    /**
     * Voix Chirp 3 HD pour le français — sélection curée parmi 30+ voix
     * disponibles côté Google :
     *  - **Charon** : voix masculine grave et chaude → Marcus
     *  - **Aoede** : voix féminine vibrante → Léa
     *  - **Puck** : voix masculine énergique → Hugo
     *  - **Kore** : voix féminine posée et claire → Sophie
     */
    val chirp3Personae: List<Persona> = listOf(
        Persona(
            id = "marcus_chirp",
            displayName = "Marcus",
            tagline = "Voix grave, ton posé",
            taglineRes = R.string.voice_persona_marcus_chirp_tagline,
            gender = Gender.MALE,
            engine = VoiceEngineId.GOOGLE_CHIRP3,
            engineVoiceId = "fr-FR-Chirp3-HD-Charon",
            avatarEmoji = "💪",
            speakingRate = 1.02f,
        ),
        Persona(
            id = "lea_chirp",
            displayName = "Léa",
            tagline = "Vibrante, encourageante",
            taglineRes = R.string.voice_persona_lea_chirp_tagline,
            gender = Gender.FEMALE,
            engine = VoiceEngineId.GOOGLE_CHIRP3,
            engineVoiceId = "fr-FR-Chirp3-HD-Aoede",
            avatarEmoji = "✨",
            speakingRate = 1.06f,
        ),
        Persona(
            id = "hugo_chirp",
            displayName = "Hugo",
            tagline = "Énergique, percutant",
            taglineRes = R.string.voice_persona_hugo_chirp_tagline,
            gender = Gender.MALE,
            engine = VoiceEngineId.GOOGLE_CHIRP3,
            engineVoiceId = "fr-FR-Chirp3-HD-Puck",
            avatarEmoji = "🔥",
            speakingRate = 1.08f,
        ),
        Persona(
            id = "sophie_chirp",
            displayName = "Sophie",
            tagline = "Claire, structurée",
            taglineRes = R.string.voice_persona_sophie_chirp_tagline,
            gender = Gender.FEMALE,
            engine = VoiceEngineId.GOOGLE_CHIRP3,
            engineVoiceId = "fr-FR-Chirp3-HD-Kore",
            avatarEmoji = "🎯",
            speakingRate = 1.00f,
        ),
    )

    /** L'union des deux catalogues — utilisé pour la résolution par id. */
    val all: List<Persona> = androidPersonae + chirp3Personae

    fun personaeFor(engine: VoiceEngineId): List<Persona> = when (engine) {
        VoiceEngineId.ANDROID -> androidPersonae
        VoiceEngineId.GOOGLE_CHIRP3 -> chirp3Personae
    }

    fun defaultPersonaFor(engine: VoiceEngineId): Persona = personaeFor(engine).first()

    fun findById(id: String?): Persona? = id?.let { needle -> all.firstOrNull { it.id == needle } }
}
