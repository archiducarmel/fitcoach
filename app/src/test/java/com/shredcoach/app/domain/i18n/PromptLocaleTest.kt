package com.shredcoach.app.domain.i18n

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.util.Locale

/**
 * Tests pour [PromptLocale] (Phase 4 helper).
 *
 * On vérifie 3 garanties critiques :
 *  1. `isEn()` ne fire QUE quand la locale courante a "en" comme tag langue.
 *     Tout autre code (fr, es, …) retombe sur FR (langue par défaut V1).
 *  2. `outputLanguageDirective()` retourne "" en FR (no-op) et un texte
 *     non-vide commençant par "═══ OUTPUT LANGUAGE OVERRIDE" en EN.
 *  3. Le helper lit `Locale.getDefault()` à CHAQUE appel — pas de mémoïsation
 *     qui survivrait à un changement de langue depuis Settings.
 */
class PromptLocaleTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `isEn returns true when locale is english`() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(PromptLocale.isEn()).isTrue()
        Locale.setDefault(Locale("en", "US"))
        assertThat(PromptLocale.isEn()).isTrue()
        Locale.setDefault(Locale("en", "GB"))
        assertThat(PromptLocale.isEn()).isTrue()
    }

    @Test
    fun `isEn returns false for french`() {
        Locale.setDefault(Locale.FRENCH)
        assertThat(PromptLocale.isEn()).isFalse()
        Locale.setDefault(Locale.FRANCE)
        assertThat(PromptLocale.isEn()).isFalse()
        Locale.setDefault(Locale("fr", "CA"))
        assertThat(PromptLocale.isEn()).isFalse()
    }

    @Test
    fun `isEn returns false for unsupported locale - falls back to FR convention`() {
        // V1 : seules FR + EN sont supportées. ES/IT/PT/DE retombent sur FR.
        Locale.setDefault(Locale("es", "ES"))
        assertThat(PromptLocale.isEn()).isFalse()
        Locale.setDefault(Locale("de", "DE"))
        assertThat(PromptLocale.isEn()).isFalse()
        Locale.setDefault(Locale("it", "IT"))
        assertThat(PromptLocale.isEn()).isFalse()
    }

    @Test
    fun `pick returns FR variant when locale is french`() {
        Locale.setDefault(Locale.FRANCE)
        assertThat(PromptLocale.pick(fr = "bonjour", en = "hello")).isEqualTo("bonjour")
    }

    @Test
    fun `pick returns EN variant when locale is english`() {
        Locale.setDefault(Locale.ENGLISH)
        assertThat(PromptLocale.pick(fr = "bonjour", en = "hello")).isEqualTo("hello")
    }

    @Test
    fun `pick supports any generic type`() {
        Locale.setDefault(Locale.ENGLISH)
        val frList = listOf("a", "b")
        val enList = listOf("x", "y")
        assertThat(PromptLocale.pick(fr = frList, en = enList)).isSameInstanceAs(enList)
        val intResult: Int = PromptLocale.pick(fr = 1, en = 2)
        assertThat(intResult).isEqualTo(2)
    }

    @Test
    fun `languageName returns localized language label`() {
        Locale.setDefault(Locale.FRENCH)
        assertThat(PromptLocale.languageName()).isEqualTo("français")
        Locale.setDefault(Locale.ENGLISH)
        assertThat(PromptLocale.languageName()).isEqualTo("English")
    }

    @Test
    fun `outputLanguageDirective returns empty string in FR`() {
        Locale.setDefault(Locale.FRENCH)
        assertThat(PromptLocale.outputLanguageDirective()).isEmpty()
    }

    @Test
    fun `outputLanguageDirective returns override block in EN`() {
        Locale.setDefault(Locale.ENGLISH)
        val directive = PromptLocale.outputLanguageDirective()
        assertThat(directive).isNotEmpty()
        assertThat(directive).contains("OUTPUT LANGUAGE OVERRIDE")
        // Contrainte : le bloc doit se terminer par 2 newlines pour être
        // visuellement séparé du prompt FR qui suit.
        assertThat(directive).endsWith("\n\n")
    }

    @Test
    fun `lang returns lowercase BCP-47 language tag`() {
        Locale.setDefault(Locale("FR", "FR"))
        assertThat(PromptLocale.lang()).isEqualTo("fr")
        Locale.setDefault(Locale("EN", "US"))
        assertThat(PromptLocale.lang()).isEqualTo("en")
    }

    @Test
    fun `pick re-evaluates locale on each call - no memoization`() {
        // Contrat critique : si l'utilisateur change la langue depuis Settings
        // (AppCompatDelegate.setApplicationLocales), le PROCHAIN call à pick
        // doit refléter le nouveau choix immédiatement, sans redémarrer l'app.
        Locale.setDefault(Locale.FRENCH)
        assertThat(PromptLocale.pick(fr = "fr1", en = "en1")).isEqualTo("fr1")
        Locale.setDefault(Locale.ENGLISH)
        assertThat(PromptLocale.pick(fr = "fr2", en = "en2")).isEqualTo("en2")
        Locale.setDefault(Locale.FRENCH)
        assertThat(PromptLocale.pick(fr = "fr3", en = "en3")).isEqualTo("fr3")
    }
}
