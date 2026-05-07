package com.shredcoach.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shredcoach.app.data.backup.provider.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

/**
 * Source de vérité pour les préférences de sauvegarde locale (SAF).
 *
 * Architecture : DataStore Preferences (proto3 binary, async, Flow-based) —
 * remplace SharedPreferences qui bloque le main thread sur le premier accès.
 *
 * Pourquoi un seul fichier dédié (`backup_settings.preferences_pb`) plutôt
 * qu'un store global "settings" : isolation des concerns. Le jour où on
 * voudra **réinitialiser** les paramètres backup (ex : "déconnecter le
 * backup" depuis l'écran réglages), un simple [reset] suffit sans toucher
 * aux autres prefs (thème, locale, etc.).
 *
 * Sécurité : ce store contient l'URI SAF du dossier de sauvegarde + un
 * vérificateur de mot de passe (V2). Aucune donnée sensible en clair —
 * le mot de passe lui-même n'est JAMAIS stocké, on stocke uniquement un
 * dérivé permettant de vérifier qu'un mot de passe saisi est le bon.
 */
@Singleton
class BackupSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Snapshot immuable des préférences. Distribué via [Flow] pour permettre
     * aux ViewModels d'observer en réactif sans avoir à appeler manuellement
     * un getter à chaque écran.
     */
    /**
     * **V2.G dormant** : [encryptionEnabled] et [passwordVerifier] sont persistés
     * et exposés mais aucun consommateur ne les utilise pour l'instant. Prévus
     * pour le sprint chiffrement-au-repos AES-GCM des archives ZIP. Conservés
     * dans le snapshot pour éviter une migration DataStore quand on activera
     * la feature. Si la feature est définitivement abandonnée, supprimer ces
     * deux champs ainsi que [setEncryptionEnabled] et [setPasswordVerifier].
     */
    data class Snapshot(
        /**
         * Provider sélectionné par l'utilisateur. Default = LOCAL_SAF pour la
         * compat ascendante (les utilisateurs existants gardent leur folder SAF).
         * On bascule sur GOOGLE_DRIVE quand l'user link son compte ; il peut
         * revenir manuellement à LOCAL_SAF dans les Settings.
         */
        val providerId: ProviderId,
        val folderUri: Uri?,
        val lastBackupAt: Instant?,
        val autoBackupEnabled: Boolean,
        val encryptionEnabled: Boolean,
        val passwordVerifier: String?,
    ) {
        /**
         * "Configuré" dépend du provider :
         *  - SAF : il faut un folderUri
         *  - Drive : il faut être linké (vérifié côté GoogleAuthRepository)
         *
         * Pour SAF on peut le tester ici. Pour Drive, le caller doit cumuler
         * avec l'état GoogleAuthStore (raison : on évite une dépendance
         * circulaire BackupSettingsStore ↔ GoogleAuthStore).
         */
        val isSafConfigured: Boolean get() = providerId == ProviderId.LOCAL_SAF && folderUri != null
    }

    private object Keys {
        val PROVIDER_ID = stringPreferencesKey("provider_id")
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at_epoch_ms")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val PASSWORD_VERIFIER = stringPreferencesKey("password_verifier")
    }

    val snapshot: Flow<Snapshot> = context.backupDataStore.data.map { prefs ->
        Snapshot(
            providerId = ProviderId.fromStorageKey(prefs[Keys.PROVIDER_ID]),
            folderUri = prefs[Keys.FOLDER_URI]?.let(Uri::parse),
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT]?.let(Instant::ofEpochMilli),
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            encryptionEnabled = prefs[Keys.ENCRYPTION_ENABLED] ?: false,
            passwordVerifier = prefs[Keys.PASSWORD_VERIFIER],
        )
    }

    suspend fun setProviderId(provider: ProviderId) {
        context.backupDataStore.edit { prefs -> prefs[Keys.PROVIDER_ID] = provider.storageKey }
    }

    suspend fun setFolderUri(uri: Uri?) {
        context.backupDataStore.edit { prefs ->
            if (uri == null) prefs.remove(Keys.FOLDER_URI)
            else prefs[Keys.FOLDER_URI] = uri.toString()
        }
    }

    suspend fun setLastBackupAt(instant: Instant?) {
        context.backupDataStore.edit { prefs ->
            if (instant == null) prefs.remove(Keys.LAST_BACKUP_AT)
            else prefs[Keys.LAST_BACKUP_AT] = instant.toEpochMilli()
        }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { prefs -> prefs[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.backupDataStore.edit { prefs -> prefs[Keys.ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setPasswordVerifier(verifier: String?) {
        context.backupDataStore.edit { prefs ->
            if (verifier == null) prefs.remove(Keys.PASSWORD_VERIFIER)
            else prefs[Keys.PASSWORD_VERIFIER] = verifier
        }
    }

    /** Réinitialise toutes les préférences backup. Utilisé par "Déconnecter le backup". */
    suspend fun reset() {
        context.backupDataStore.edit { it.clear() }
    }
}
