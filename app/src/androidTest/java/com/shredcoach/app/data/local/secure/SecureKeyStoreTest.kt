package com.shredcoach.app.data.local.secure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test instrumenté du [SecureKeyStore].
 *
 * Doit s'exécuter sur device/emulator car [androidx.security.crypto.EncryptedSharedPreferences]
 * dépend de l'**Android Keystore** (impossible à mocker proprement).
 *
 * Chaque test isole son fichier de prefs en supprimant le file avant ET après —
 * évite la pollution entre runs (le Keystore peut conserver la master key entre
 * désinstalls, donc on doit nettoyer activement).
 */
@RunWith(AndroidJUnit4::class)
class SecureKeyStoreTest {

    private lateinit var context: Context
    private lateinit var store: SecureKeyStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Nettoyage avant test : supprime le fichier prefs s'il existe
        // (ne supprime PAS la master key dans le Keystore — ce serait coûteux
        //  et inutile pour notre logique).
        context.deleteSharedPreferences(SecureKeyStore.FILE_NAME)
        store = SecureKeyStore(context)
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(SecureKeyStore.FILE_NAME)
    }

    @Test
    fun getKey_renvoie_chaine_vide_quand_absent() {
        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("")
        assertThat(store.hasKey(SecureKeyStore.Provider.LLM)).isFalse()
    }

    @Test
    fun setKey_puis_getKey_retourne_la_meme_valeur() {
        store.setKey(SecureKeyStore.Provider.LLM, "sk-test-123")
        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("sk-test-123")
        assertThat(store.hasKey(SecureKeyStore.Provider.LLM)).isTrue()
    }

    @Test
    fun les_4_providers_sont_independants() {
        store.setKey(SecureKeyStore.Provider.LLM, "llm-value")
        store.setKey(SecureKeyStore.Provider.GEMINI, "gemini-value")
        store.setKey(SecureKeyStore.Provider.GROQ_MEAL, "groq-value")
        store.setKey(SecureKeyStore.Provider.MISTRAL, "mistral-value")

        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("llm-value")
        assertThat(store.getKey(SecureKeyStore.Provider.GEMINI)).isEqualTo("gemini-value")
        assertThat(store.getKey(SecureKeyStore.Provider.GROQ_MEAL)).isEqualTo("groq-value")
        assertThat(store.getKey(SecureKeyStore.Provider.MISTRAL)).isEqualTo("mistral-value")
    }

    @Test
    fun setKey_ecrase_la_valeur_precedente() {
        store.setKey(SecureKeyStore.Provider.LLM, "old-value")
        store.setKey(SecureKeyStore.Provider.LLM, "new-value")
        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("new-value")
    }

    @Test
    fun clear_retire_la_cle_specifiee_seulement() {
        store.setKey(SecureKeyStore.Provider.LLM, "llm-value")
        store.setKey(SecureKeyStore.Provider.GEMINI, "gemini-value")

        store.clear(SecureKeyStore.Provider.LLM)

        assertThat(store.hasKey(SecureKeyStore.Provider.LLM)).isFalse()
        assertThat(store.hasKey(SecureKeyStore.Provider.GEMINI)).isTrue()
        assertThat(store.getKey(SecureKeyStore.Provider.GEMINI)).isEqualTo("gemini-value")
    }

    @Test
    fun clearAll_retire_toutes_les_cles() {
        store.setKey(SecureKeyStore.Provider.LLM, "llm")
        store.setKey(SecureKeyStore.Provider.GEMINI, "gemini")
        store.setKey(SecureKeyStore.Provider.GROQ_MEAL, "groq")
        store.setKey(SecureKeyStore.Provider.MISTRAL, "mistral")

        store.clearAll()

        SecureKeyStore.Provider.values().forEach { provider ->
            assertThat(store.hasKey(provider)).isFalse()
        }
    }

    @Test
    fun valeur_persiste_entre_instances_du_store() {
        // Simule un redémarrage d'app : on écrit avec un store, on lit avec un autre.
        store.setKey(SecureKeyStore.Provider.LLM, "persistent-key")

        val store2 = SecureKeyStore(context)
        assertThat(store2.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("persistent-key")
    }

    @Test
    fun hasKey_false_pour_chaine_blanche() {
        store.setKey(SecureKeyStore.Provider.LLM, "")
        assertThat(store.hasKey(SecureKeyStore.Provider.LLM)).isFalse()

        store.setKey(SecureKeyStore.Provider.LLM, "   ")
        assertThat(store.hasKey(SecureKeyStore.Provider.LLM)).isFalse()
    }
}
