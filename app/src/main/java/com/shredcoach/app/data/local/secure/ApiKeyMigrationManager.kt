package com.shredcoach.app.data.local.secure

import android.util.Log
import com.shredcoach.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Migration douce des clés API stockées en clair dans `UserProfileEntity`
 * (colonnes Room) vers le [SecureKeyStore] chiffré.
 *
 * **Idempotent** : si les colonnes Room sont déjà vides (cas nominal après
 * la première migration ou pour un nouvel utilisateur), c'est un no-op.
 *
 * **Resilience** : si l'app crashe entre la copie (vers SecureKeyStore)
 * et le nettoyage (UPDATE Room), la clé existe temporairement aux deux
 * endroits — aucune perte. Le prochain boot reprend le nettoyage car
 * `setKey` n'écrase pas (vérification `hasKey` préalable).
 *
 * **Phase D** retirera définitivement les colonnes Room via une migration
 * v33 → v34 (ALTER TABLE DROP COLUMN). À ce moment-là, ce manager pourra
 * être supprimé.
 */
@Singleton
class ApiKeyMigrationManager @Inject constructor(
    private val userRepository: UserRepository,
    private val secureKeyStore: SecureKeyStore
) {

    /**
     * Lance la migration en arrière-plan (Dispatchers.IO).
     * Ne bloque pas le thread appelant — sûr d'appeler depuis `Application.onCreate`.
     */
    fun migrateInBackground() {
        scope.launch {
            try {
                migrate()
            } catch (t: Throwable) {
                // On loggue mais on ne crashe pas l'app : si la migration
                // échoue, les VMs liront simplement une clé vide et l'utilisateur
                // pourra la re-saisir dans Settings.
                Log.e(TAG, "API key migration failed", t)
            }
        }
    }

    private suspend fun migrate() {
        val profile = userRepository.getUserProfileOnce() ?: return

        var copied = false
        val mappings = listOf(
            SecureKeyStore.Provider.LLM to profile.llmApiKey,
            SecureKeyStore.Provider.GEMINI to profile.geminiApiKey,
            SecureKeyStore.Provider.GROQ_MEAL to profile.groqMealApiKey,
            SecureKeyStore.Provider.MISTRAL to profile.mistralApiKey
        )
        for ((provider, plaintextKey) in mappings) {
            if (plaintextKey.isNotBlank() && !secureKeyStore.hasKey(provider)) {
                secureKeyStore.setKey(provider, plaintextKey)
                copied = true
            }
        }

        if (copied || profile.hasAnyClearTextKey()) {
            // Vide les colonnes Room — les valeurs sont désormais
            // exclusivement dans SecureKeyStore (chiffré).
            userRepository.updateUserProfile(
                profile.copy(
                    llmApiKey = "",
                    geminiApiKey = "",
                    groqMealApiKey = "",
                    mistralApiKey = ""
                )
            )
            Log.i(TAG, "Migrated API keys from Room → SecureKeyStore (cleared cleartext columns)")
        }
    }

    private fun com.shredcoach.app.data.local.entity.UserProfileEntity.hasAnyClearTextKey(): Boolean =
        llmApiKey.isNotBlank() ||
            geminiApiKey.isNotBlank() ||
            groqMealApiKey.isNotBlank() ||
            mistralApiKey.isNotBlank()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val TAG = "ApiKeyMigration"
    }
}
