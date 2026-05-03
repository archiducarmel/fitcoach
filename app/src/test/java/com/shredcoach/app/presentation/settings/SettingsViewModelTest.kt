package com.shredcoach.app.presentation.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [SettingsViewModel].
 *
 * Focus : valider le **flux des clés API** post-Phase C.
 * On veut être SÛR que :
 *  - À l'init, le state expose les clés lues depuis le SecureKeyStore (pas Room).
 *  - `updateLlmApiKey` (et les 3 autres) délèguent à `userRepository.setApiKey`.
 *  - Après update, le state est rafraîchi avec la nouvelle valeur.
 *
 * On mocke `UserRepository` pour ne pas dépendre du Keystore Android — ce qui
 * rend ces tests des **unit tests JVM purs**, rapides (<1 s) et exécutables
 * sans device ni emulator.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userRepository = mockk(relaxed = true)
        // Profile Flow par défaut — un profil minimal vide
        every { userRepository.getUserProfile() } returns flowOf(
            UserProfileEntity(firstName = "Sitou")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init lit les 4 cles API depuis le SecureKeyStore`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(SecureKeyStore.Provider.LLM) } returns "sk-llm-123"
        every { userRepository.getApiKey(SecureKeyStore.Provider.GEMINI) } returns "AIza-gemini"
        every { userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL) } returns "gsk-groq"
        every { userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL) } returns ""

        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.llmApiKey).isEqualTo("sk-llm-123")
            assertThat(state.geminiApiKey).isEqualTo("AIza-gemini")
            assertThat(state.groqMealApiKey).isEqualTo("gsk-groq")
            assertThat(state.mistralApiKey).isEqualTo("")
        }

        verify { userRepository.getApiKey(SecureKeyStore.Provider.LLM) }
        verify { userRepository.getApiKey(SecureKeyStore.Provider.GEMINI) }
        verify { userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL) }
        verify { userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL) }
    }

    @Test
    fun `updateLlmApiKey delegue au SecureKeyStore via UserRepository`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(any()) } returns ""
        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.updateLlmApiKey("sk-new-key")
        advanceUntilIdle()

        verify { userRepository.setApiKey(SecureKeyStore.Provider.LLM, "sk-new-key") }
    }

    @Test
    fun `updateGeminiApiKey delegue au bon provider`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(any()) } returns ""
        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.updateGeminiApiKey("AIza-new")
        advanceUntilIdle()

        verify { userRepository.setApiKey(SecureKeyStore.Provider.GEMINI, "AIza-new") }
    }

    @Test
    fun `updateGroqMealApiKey delegue au bon provider`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(any()) } returns ""
        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.updateGroqMealApiKey("gsk-new")
        advanceUntilIdle()

        verify { userRepository.setApiKey(SecureKeyStore.Provider.GROQ_MEAL, "gsk-new") }
    }

    @Test
    fun `updateMistralApiKey delegue au bon provider`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(any()) } returns ""
        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.updateMistralApiKey("mk-new")
        advanceUntilIdle()

        verify { userRepository.setApiKey(SecureKeyStore.Provider.MISTRAL, "mk-new") }
    }

    @Test
    fun `apres update, le state reflete la nouvelle cle`() = runTest(testDispatcher) {
        // Avant update : clé vide. Après update : la clé "sk-new-key".
        every { userRepository.getApiKey(SecureKeyStore.Provider.LLM) } returnsMany listOf(
            "",          // appel init
            "sk-new-key" // appel après update (refreshApiKeys)
        )
        every { userRepository.getApiKey(SecureKeyStore.Provider.GEMINI) } returns ""
        every { userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL) } returns ""
        every { userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL) } returns ""

        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()
        assertThat(viewModel.state.value.llmApiKey).isEqualTo("")

        viewModel.updateLlmApiKey("sk-new-key")
        advanceUntilIdle()

        assertThat(viewModel.state.value.llmApiKey).isEqualTo("sk-new-key")
        assertThat(viewModel.state.value.saved).isTrue()
    }

    @Test
    fun `update champ profil utilise UserRepository updateUserProfile`() = runTest(testDispatcher) {
        every { userRepository.getApiKey(any()) } returns ""
        coEvery { userRepository.updateUserProfile(any()) } returns Unit

        val viewModel = SettingsViewModel(userRepository)
        advanceUntilIdle()

        viewModel.updateVibration(false)
        advanceUntilIdle()

        coVerify {
            userRepository.updateUserProfile(match { profile -> !profile.vibrationEnabled })
        }
    }
}
