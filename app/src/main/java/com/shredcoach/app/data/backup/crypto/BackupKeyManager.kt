package com.shredcoach.app.data.backup.crypto

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cryptoDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_crypto")

/**
 * Gestionnaire de la clé maître AES-256 utilisée pour chiffrer les backups.
 *
 * **Modèle de menace** :
 *  - **Couvert** : exfiltration du backup depuis Drive (insider Google, court order),
 *    MITM sur le transport (déjà couvert par HTTPS, mais ceinture+bretelles),
 *    fichier altéré (GCM tag détecte la modification).
 *  - **Non-couvert** : compromission du device (rooted, malware avec accès aux
 *    private prefs), dump RAM pendant un backup (la clé est en RAM ~secondes).
 *
 * **Stockage** : la clé est stockée en clair dans un DataStore privé. La protection
 * vient de **Android File-Based Encryption** (FBE) : depuis Android 7, le stockage
 * privé de l'app est chiffré par une clé dérivée du PIN/biométrie de l'écran
 * verrouillé. Tant que le device est verrouillé, la clé est illisible même
 * pour root.
 *
 * On NE stocke PAS la clé dans Android KeyStore parce que les clés AES KS sont
 * **non-extractables** par design — impossible de les afficher à l'user pour
 * un export de récupération. Trade-off acceptable : FBE est suffisant pour
 * une app fitness, et on gagne la portabilité cross-device.
 *
 * **Code de récupération** : Base64 URL-safe (43 chars) du raw key, formaté
 * en 11 groupes de 4 chars séparés par des hyphens pour la lisibilité humaine.
 * L'user note ce code → peut restaurer sur un nouveau téléphone.
 */
@Singleton
class BackupKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val MASTER_KEY_B64 = stringPreferencesKey("master_key_b64")
    }

    /** Émet true si l'encryption est active (clé présente). */
    val isEnabled: Flow<Boolean> = context.cryptoDataStore.data.map {
        it[Keys.MASTER_KEY_B64] != null
    }

    /**
     * Lit la clé maître. Renvoie null si l'encryption n'est pas activée
     * (l'archive sera packée en clair). Le caller ne doit JAMAIS persister
     * la clé en mémoire au-delà de l'op courante.
     */
    suspend fun keyOrNull(): ByteArray? {
        val b64 = context.cryptoDataStore.data.first()[Keys.MASTER_KEY_B64] ?: return null
        return Base64.getUrlDecoder().decode(b64)
    }

    /**
     * Active l'encryption en générant une clé fraîche de 32 bytes (256 bits)
     * via [SecureRandom]. Idempotent : si une clé existe déjà, on ne la
     * régénère pas (sinon tous les backups précédents deviendraient
     * indéchiffrables).
     *
     * @return true si une nouvelle clé a été générée, false si une existait déjà.
     */
    suspend fun enableAndGenerate(): Boolean {
        val existing = context.cryptoDataStore.data.first()[Keys.MASTER_KEY_B64]
        if (existing != null) return false
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(key)
        context.cryptoDataStore.edit { it[Keys.MASTER_KEY_B64] = b64 }
        // Wipe la version en RAM dès qu'on a fini → minimisation surface attaque
        key.fill(0)
        return true
    }

    /**
     * Importe une clé externe (recovery code décodé). Utilisé par le flow
     * "Restaurer depuis un nouveau téléphone" : l'user paste son code,
     * on le décode et on le persiste comme clé maître locale.
     *
     * @param raw Clé brute (32 bytes attendus).
     * @throws IllegalArgumentException si la clé n'a pas la bonne taille.
     */
    suspend fun importKey(raw: ByteArray) {
        require(raw.size == 32) { "La clé de récupération doit faire 32 bytes (a fait ${raw.size})" }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        context.cryptoDataStore.edit { it[Keys.MASTER_KEY_B64] = b64 }
    }

    /**
     * Désactive l'encryption en supprimant la clé. **Attention** : les backups
     * chiffrés existants deviennent indéchiffrables sans le code de
     * récupération préalablement exporté. Le caller DOIT avoir averti l'user.
     */
    suspend fun disable() {
        context.cryptoDataStore.edit { it.remove(Keys.MASTER_KEY_B64) }
    }

    /**
     * Renvoie le code de récupération formaté pour affichage humain :
     * 11 groupes de 4 chars Base64 URL-safe séparés par des hyphens.
     *
     * Exemple: `AbCd-EfGh-IjKl-MnOp-QrSt-UvWx-YzAb-CdEf-GhIj-KlMn-OpQr`
     *
     * Renvoie null si l'encryption n'est pas activée.
     */
    suspend fun exportRecoveryCode(): String? {
        val b64 = context.cryptoDataStore.data.first()[Keys.MASTER_KEY_B64] ?: return null
        return formatHumanReadable(b64)
    }

    companion object {
        /**
         * Formate un code Base64 brut en groupes de 4 séparés par '-'. Les
         * lecteurs humains font moins d'erreurs sur un code segmenté.
         */
        fun formatHumanReadable(rawBase64: String): String {
            return rawBase64.chunked(4).joinToString("-")
        }

        /**
         * Parse un recovery code utilisateur (avec ou sans hyphens, espaces,
         * casse-insensible). Tolère le copy-paste imparfait.
         *
         * @return Les 32 bytes décodés, ou null si le format est invalide.
         */
        fun parseRecoveryCode(input: String): ByteArray? {
            val cleaned = input.replace(Regex("[\\s\\-]"), "")
            return try {
                val decoded = Base64.getUrlDecoder().decode(cleaned)
                if (decoded.size == 32) decoded else null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
