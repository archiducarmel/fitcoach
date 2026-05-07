package com.shredcoach.app.domain.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_settings")

/**
 * Préférences utilisateur pour la voix de Shreddy : moteur + persona.
 *
 * Architecture : DataStore Preferences dédié — pas dans UserProfileEntity
 * pour éviter une migration Room et pour permettre à [ShreddyVoice] de
 * s'abonner réactivement sans dépendre du repo profile (qui ferait une
 * dépendance circulaire UI → repo → voice).
 *
 * **Réactivité** : [snapshot] est consommé en `collect` par [ShreddyVoice].
 * Tout `setEngine` ou `setPersona` propage immédiatement le changement à
 * la prochaine synthèse vocale (countdown ou phrase). Aucun restart de
 * service nécessaire.
 *
 * **Cohérence engine ↔ persona** : si l'utilisateur change de moteur,
 * [setEngine] reset la persona vers le défaut du nouveau moteur. Évite
 * d'avoir un `engineId = ANDROID` avec `personaId = marcus_chirp` qui
 * forcerait [ShreddyVoice] à un fallback silencieux.
 */
@Singleton
class VoiceSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Snapshot(
        val engineId: VoiceEngineId,
        val personaId: String,
    )

    private object Keys {
        val ENGINE = stringPreferencesKey("voice_engine_id")
        val PERSONA = stringPreferencesKey("voice_persona_id")
    }

    val snapshot: Flow<Snapshot> = context.voiceDataStore.data.map { prefs ->
        val engine = VoiceEngineId.fromKey(prefs[Keys.ENGINE])
        val storedPersonaId = prefs[Keys.PERSONA]
        val resolved = VoicePersonaRegistry.findById(storedPersonaId)
            ?.takeIf { it.engine == engine }
            ?: VoicePersonaRegistry.defaultPersonaFor(engine)
        Snapshot(
            engineId = engine,
            personaId = resolved.id,
        )
    }

    suspend fun setEngine(engine: VoiceEngineId) {
        context.voiceDataStore.edit { prefs ->
            prefs[Keys.ENGINE] = engine.name
            // Reset persona vers le défaut du nouveau moteur si l'actuelle
            // n'appartient pas au moteur sélectionné.
            val currentPersona = VoicePersonaRegistry.findById(prefs[Keys.PERSONA])
            if (currentPersona == null || currentPersona.engine != engine) {
                prefs[Keys.PERSONA] = VoicePersonaRegistry.defaultPersonaFor(engine).id
            }
        }
    }

    suspend fun setPersona(personaId: String) {
        val target = VoicePersonaRegistry.findById(personaId) ?: return
        context.voiceDataStore.edit { prefs ->
            prefs[Keys.ENGINE] = target.engine.name
            prefs[Keys.PERSONA] = target.id
        }
    }
}
