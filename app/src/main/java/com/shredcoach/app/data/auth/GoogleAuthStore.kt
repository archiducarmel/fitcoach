package com.shredcoach.app.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.googleAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "google_auth")

/**
 * Métadonnées du compte Google linké pour la sauvegarde Drive.
 *
 * **Important — quoi stocke-t-on et quoi PAS** :
 *  - **OK** : email, displayName, accountId (Google `sub`), drapeau scope-granted,
 *    timestamp du dernier auth réussi. Tout ça est public ou au pire de l'identité,
 *    pas un secret.
 *  - **JAMAIS** : access tokens, refresh tokens, ID tokens. On ne les persiste
 *    PAS — on les redemande systématiquement à `AuthorizationClient.authorize()`
 *    qui répond silencieusement si l'app est encore autorisée par l'utilisateur.
 *    Cette politique évite tout risque d'exfiltration de tokens persistés et
 *    s'aligne avec la recommandation Google : "Don't store access tokens".
 *
 * **Pourquoi un store dédié** plutôt qu'étendre `BackupSettingsStore` :
 * isolation des concerns. L'auth Google peut être utilisée plus tard pour
 * d'autres features (ex: Calendar pour scheduler les séances). Garder un store
 * séparé permet un `unlink()` propre qui clear l'auth sans toucher aux prefs
 * de backup.
 */
@Singleton
class GoogleAuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Snapshot(
        /** Email du compte Google linké, null si pas linké. */
        val linkedEmail: String?,
        /** Nom affichable (ex: "Sitou A.") pour l'UI, null si pas dispo. */
        val displayName: String?,
        /** Identifiant Google `sub` — stable cross-renommages d'email. */
        val accountId: String?,
        /** True si l'utilisateur a accordé `drive.appdata` au moins une fois. */
        val driveScopeGranted: Boolean,
        /** Dernière fois qu'on a obtenu un access token avec succès. */
        val lastAuthAt: Instant?,
    ) {
        val isLinked: Boolean get() = linkedEmail != null && driveScopeGranted
    }

    private object Keys {
        val EMAIL = stringPreferencesKey("linked_email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val DRIVE_GRANTED = booleanPreferencesKey("drive_scope_granted")
        val LAST_AUTH_AT = longPreferencesKey("last_auth_at_epoch_ms")
    }

    val snapshot: Flow<Snapshot> = context.googleAuthDataStore.data.map { prefs ->
        Snapshot(
            linkedEmail = prefs[Keys.EMAIL],
            displayName = prefs[Keys.DISPLAY_NAME],
            accountId = prefs[Keys.ACCOUNT_ID],
            driveScopeGranted = prefs[Keys.DRIVE_GRANTED] ?: false,
            lastAuthAt = prefs[Keys.LAST_AUTH_AT]?.let(Instant::ofEpochMilli),
        )
    }

    suspend fun setLinkedAccount(email: String, displayName: String?, accountId: String?) {
        context.googleAuthDataStore.edit { prefs ->
            prefs[Keys.EMAIL] = email
            if (displayName != null) prefs[Keys.DISPLAY_NAME] = displayName
            else prefs.remove(Keys.DISPLAY_NAME)
            if (accountId != null) prefs[Keys.ACCOUNT_ID] = accountId
            else prefs.remove(Keys.ACCOUNT_ID)
            prefs[Keys.DRIVE_GRANTED] = true
            prefs[Keys.LAST_AUTH_AT] = System.currentTimeMillis()
        }
    }

    suspend fun touchLastAuth() {
        context.googleAuthDataStore.edit { prefs ->
            prefs[Keys.LAST_AUTH_AT] = System.currentTimeMillis()
        }
    }

    /** Clear total — pour "Déconnecter le compte Google". */
    suspend fun unlink() {
        context.googleAuthDataStore.edit { it.clear() }
    }
}
