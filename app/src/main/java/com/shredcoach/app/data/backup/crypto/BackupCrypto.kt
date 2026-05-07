package com.shredcoach.app.data.backup.crypto

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Couche crypto AES-256-GCM streaming pour les backups ShredCoach.
 *
 * **Format binaire** d'une archive chiffrée :
 * ```
 * [magic 6 bytes "SCBE\x01\x00"]   ← ShredCoach Backup Encrypted, version 1
 * [iv   12 bytes random]           ← nonce GCM unique par backup
 * [ciphertext + GCM tag (16 bytes appendus auto)]
 * ```
 *
 * **Pourquoi AES-GCM** :
 *  - Authenticated encryption → détecte toute altération du backup (Drive
 *    serveur compromis, MITM, fichier tronqué) → fail au lieu de produire
 *    des données corrompues silencieusement.
 *  - Streaming via [CipherInputStream]/[CipherOutputStream] → mémoire
 *    constante même sur un backup de 600Mo.
 *  - GCM nonce 12 bytes : recommandation NIST SP 800-38D (96 bits, optimal
 *    pour le hardware AES-NI).
 *
 * **Pourquoi pas chunks** : une archive ShredCoach = 1 fichier qu'on lit
 * d'un seul flot. Pas besoin de seek aléatoire → un seul nonce par fichier
 * suffit, on évite la complexité d'AES-GCM-SIV ou de re-keying par chunk.
 *
 * **Important — réutilisation de nonce** : le nonce DOIT être unique pour
 * une clé donnée, sinon la sécurité GCM s'effondre (révélation de la clé).
 * On utilise [SecureRandom] qui garantit ~2^48 backups avant collision
 * statistique → largement suffisant (ShredCoach fait 1 backup/jour).
 */
object BackupCrypto {

    /** "SCBE\x01\x00" — magic + version (1.0). Reconnu sur les 6 premiers bytes. */
    val MAGIC: ByteArray = byteArrayOf(0x53, 0x43, 0x42, 0x45, 0x01, 0x00)
    const val MAGIC_SIZE = 6
    const val IV_SIZE = 12
    const val GCM_TAG_BITS = 128
    const val HEADER_SIZE = MAGIC_SIZE + IV_SIZE
    private const val ALGORITHM = "AES/GCM/NoPadding"

    /**
     * Wrap [output] avec un OutputStream qui chiffre tout ce qu'on écrit dedans.
     * Le caller écrit le ZIP en clair → le wrapper produit du ciphertext sur
     * [output].
     *
     * **Le header est écrit avant le retour** — le caller peut commencer à
     * écrire son ZIP immédiatement.
     *
     * **Critique : le wrapper renvoyé DOIT être close()** pour finaliser le
     * GCM tag. Si on oublie le close, le tag n'est pas écrit → restore
     * échouera avec "AEADBadTagException" alors que les data sont bonnes.
     * Use-with → toujours.
     */
    fun encryptStream(output: OutputStream, key: ByteArray): OutputStream {
        require(key.size == 32) { "AES-256 key must be 32 bytes, got ${key.size}" }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        // Header : magic + iv (les caches/proxies Drive verront ces 18 bytes
        // comme entête fichier — comportement normal).
        output.write(MAGIC)
        output.write(iv)
        output.flush()
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return CipherOutputStream(output, cipher)
    }

    /**
     * Wrap [input] avec un InputStream qui déchiffre à la volée. Le caller
     * lit comme si c'était le ZIP en clair — la décryption + vérif du tag
     * GCM se fait transparente.
     *
     * **Le header est consommé avant le retour** — la position est avancée
     * de [HEADER_SIZE] octets sur [input].
     *
     * **Si le tag GCM ne match pas** (clé erronée OU fichier altéré OU
     * format corrompu), une `IOException` est levée à la lecture finale —
     * pas au démarrage. C'est intrinsèque à AES-GCM streaming : on ne sait
     * que les data sont valides qu'après avoir tout lu.
     */
    fun decryptStream(input: InputStream, key: ByteArray): InputStream {
        require(key.size == 32) { "AES-256 key must be 32 bytes, got ${key.size}" }
        val magic = ByteArray(MAGIC_SIZE)
        if (input.read(magic) != MAGIC_SIZE || !magic.contentEquals(MAGIC)) {
            throw IOException("Format chiffré invalide ou archive en clair (magic mismatch).")
        }
        val iv = ByteArray(IV_SIZE)
        if (input.read(iv) != IV_SIZE) {
            throw IOException("Header chiffré tronqué (IV manquant).")
        }
        val cipher = Cipher.getInstance(ALGORITHM).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return CipherInputStream(input, cipher)
    }

    /**
     * Sniff non-destructif : lit les 6 premiers bytes, les remet dans le buffer
     * via [InputStream.mark]/[InputStream.reset] si supporté. Sinon le caller
     * doit fournir un stream supportant mark (ex: BufferedInputStream).
     *
     * Renvoie true si le stream commence par MAGIC → l'archive est chiffrée.
     */
    fun isEncryptedStream(input: InputStream): Boolean {
        require(input.markSupported()) {
            "isEncryptedStream requires markSupported (wrap in BufferedInputStream)."
        }
        input.mark(MAGIC_SIZE)
        val sniff = ByteArray(MAGIC_SIZE)
        val read = input.read(sniff)
        input.reset()
        return read == MAGIC_SIZE && sniff.contentEquals(MAGIC)
    }
}
