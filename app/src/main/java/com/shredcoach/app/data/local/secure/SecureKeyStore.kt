package com.shredcoach.app.data.local.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage chiffré des secrets utilisateur (clés API LLM).
 *
 * Implémentation : [EncryptedSharedPreferences] avec
 * - AES256-SIV pour les noms de clés (déterministe → on peut requêter par nom)
 * - AES256-GCM pour les valeurs (authentification + chiffrement)
 * - Clé maître protégée par Android Keystore (hardware-backed sur la plupart
 *   des devices récents — le device root ne peut pas extraire la clé maître).
 *
 * Le fichier sous-jacent (`shredcoach_secure_keys.xml`) est exclu du backup
 * cloud (cf. `backup_rules.xml` / `data_extraction_rules.xml`).
 *
 * @Singleton car la première instanciation peut prendre ~50-200 ms
 * (génération clé maître) — on l'instancie une fois pour toute la durée
 * de vie de l'app.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    /**
     * Identifiant logique du fournisseur LLM dont on stocke la clé API.
     * Le `name` de l'enum sert de clé dans EncryptedSharedPreferences.
     */
    enum class Provider {
        /** Chat IA principal (Groq / OpenAI / Claude — choisi via llmProvider). */
        LLM,
        /** Gemini : MealScanner, BodyMesh, BodyAnalysis. */
        GEMINI,
        /** Groq image-capable : alternative au scan repas. */
        GROQ_MEAL,
        /** Mistral vision : alternative au scan repas. */
        MISTRAL,
        /** Google Cloud Text-to-Speech (Chirp 3 HD) — voix Shreddy premium. */
        GOOGLE_TTS,
        /** GitHub Models — PAT GitHub (ghp_xxx) pour acces au catalogue. */
        GITHUB_MODELS,
        /** NVIDIA NIM — API key nvapi-xxx pour 150+ modeles NIM. */
        NVIDIA_NIM,
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKey(provider: Provider): String =
        prefs.getString(provider.name, "") ?: ""

    fun setKey(provider: Provider, value: String) {
        prefs.edit().putString(provider.name, value).apply()
    }

    fun hasKey(provider: Provider): Boolean =
        getKey(provider).isNotBlank()

    fun clear(provider: Provider) {
        prefs.edit().remove(provider.name).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val FILE_NAME = "shredcoach_secure_keys"
    }
}
