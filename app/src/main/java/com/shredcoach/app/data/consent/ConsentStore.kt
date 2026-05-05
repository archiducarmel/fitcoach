package com.shredcoach.app.data.consent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.consentDataStore: DataStore<Preferences> by preferencesDataStore(name = "consent_settings")

/**
 * Tracking des consentements RGPD utilisateur.
 *
 * Pour chaque type de traitement, on stocke :
 * - La **version** de la politique au moment de l'acceptation (0 = jamais accepté)
 * - L'**instant** d'acceptation (epoch ms)
 *
 * Si la politique évolue (incrément de [POLICY_VERSION]), tous les consentements
 * deviennent "stale" et l'app re-prompt l'utilisateur. Mécanisme RGPD recommandé
 * pour le "renouvellement de consentement éclairé" lors de changements
 * substantiels (nouveau provider, nouvelle finalité, etc.).
 *
 * Types de traitement tracés :
 * - [ConsentType.LLM_CHAT] : envois Shreddy chat → Groq/OpenAI/Claude (texte uniquement).
 * - [ConsentType.VISION_API] : envois photos repas/corps → Gemini/Groq/Mistral.
 *
 * **Pas de consentement "global"** : RGPD exige du granulaire — l'utilisateur
 * peut refuser le scan repas tout en acceptant le chat textuel.
 */
@Singleton
class ConsentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class ConsentType(val key: String) {
        LLM_CHAT("llm_chat"),
        VISION_API("vision_api"),
    }

    /** Snapshot immuable des consentements actuels. Observable via [snapshot]. */
    data class State(
        val acceptedVersions: Map<ConsentType, Int>,
        val acceptedAt: Map<ConsentType, Instant>,
    ) {
        /**
         * `true` si [type] est consenti pour la version courante de la politique.
         * Faux si jamais accepté OU si accepté pour une version antérieure
         * (renouvellement requis).
         */
        fun isCurrent(type: ConsentType): Boolean =
            (acceptedVersions[type] ?: 0) >= POLICY_VERSION
    }

    val snapshot: Flow<State> = context.consentDataStore.data.map { prefs ->
        State(
            acceptedVersions = ConsentType.values().associateWith { type ->
                prefs[versionKey(type)] ?: 0
            },
            acceptedAt = ConsentType.values().mapNotNull { type ->
                prefs[timestampKey(type)]?.let { type to Instant.ofEpochMilli(it) }
            }.toMap(),
        )
    }

    /** Enregistre l'acceptation d'un type de traitement à la version courante. */
    suspend fun grant(type: ConsentType) {
        context.consentDataStore.edit { prefs ->
            prefs[versionKey(type)] = POLICY_VERSION
            prefs[timestampKey(type)] = Instant.now().toEpochMilli()
        }
    }

    /** Révoque un consentement. L'utilisateur ne pourra plus utiliser la feature. */
    suspend fun revoke(type: ConsentType) {
        context.consentDataStore.edit { prefs ->
            prefs.remove(versionKey(type))
            prefs.remove(timestampKey(type))
        }
    }

    /** Reset complet. Appelé par DataPurger lors du "supprimer toutes mes données". */
    suspend fun reset() {
        context.consentDataStore.edit { it.clear() }
    }

    private fun versionKey(type: ConsentType) = intPreferencesKey("${type.key}_version")
    private fun timestampKey(type: ConsentType) = longPreferencesKey("${type.key}_at_ms")

    companion object {
        /**
         * Version de la politique de confidentialité **courante**.
         *
         * À incrémenter manuellement quand :
         * - On ajoute un nouveau provider tiers (ex: nouvelle API LLM)
         * - On change une finalité de traitement
         * - Le texte de la privacy policy change substantiellement
         *
         * Tout incrément invalide les consentements antérieurs → l'utilisateur
         * doit réaccepter. Ne PAS incrémenter pour des changements cosmétiques
         * (faute de frappe corrigée, reformulation) sinon on harcèle.
         */
        const val POLICY_VERSION = 1
    }
}
