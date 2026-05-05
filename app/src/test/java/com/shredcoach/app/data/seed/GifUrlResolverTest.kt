package com.shredcoach.app.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour [GifUrlResolver.slugify] et [GifUrlResolver.urlFor].
 *
 * **Pourquoi** : la règle de slugify reproduit empiriquement le traitement
 * appliqué par GitHub Releases lors de l'upload. Si GitHub change ce comportement
 * ou si on republie sur une autre plateforme, ces tests détectent immédiatement
 * la régression — sinon, on aurait des 404 silencieux côté UI (placeholder
 * affiché au lieu du GIF, sans erreur évidente côté monitoring).
 *
 * Les expected values ont été vérifiés en listant les 506 assets de la release
 * via l'API GitHub (cf. commit message du fix).
 */
class GifUrlResolverTest {

    // ─── slugify : règles de base ────────────────────────────────────────

    @Test
    fun `slugify preserves pure ASCII filename`() {
        assertEquals("Squat.gif", GifUrlResolver.slugify("Squat.gif"))
        assertEquals("Cercles_de_hanches.gif", GifUrlResolver.slugify("Cercles_de_hanches.gif"))
    }

    @Test
    fun `slugify strips accents (a-grave to a)`() {
        assertEquals("Curl_a_la_barre.gif", GifUrlResolver.slugify("Curl_à_la_barre.gif"))
    }

    @Test
    fun `slugify strips multiple accents`() {
        assertEquals(
            "Developpe_couche_avec_halteres.gif",
            GifUrlResolver.slugify("Développé_couché_avec_haltères.gif")
        )
    }

    @Test
    fun `slugify handles uppercase accents`() {
        assertEquals(
            "Elevations_laterales.gif",
            GifUrlResolver.slugify("Élévations_latérales.gif")
        )
    }

    @Test
    fun `slugify replaces parentheses with dot`() {
        assertEquals(
            "Dead_Hang_.suspension_passive.gif",
            GifUrlResolver.slugify("Dead_Hang_(suspension_passive).gif")
        )
    }

    @Test
    fun `slugify drops degree symbol with adjacent dot collapse`() {
        // 45°.gif → 45..gif → (collapse) → 45.gif
        assertEquals(
            "Extension_lombaire_au_banc_a_45.gif",
            GifUrlResolver.slugify("Extension_lombaire_au_banc_à_45°.gif")
        )
    }

    @Test
    fun `slugify replaces degree symbol with dot when not adjacent to dot`() {
        // 90°_à → 90._a (degree → dot, underscore preserved)
        assertEquals(
            "Rotation_interne_a_90._a_la_poulie.gif",
            GifUrlResolver.slugify("Rotation_interne_à_90°_à_la_poulie.gif")
        )
    }

    @Test
    fun `slugify replaces smart quote with dot`() {
        // ’ (U+2019 right single quotation mark) → .
        assertEquals(
            "Marche_de_l.ours.gif",
            GifUrlResolver.slugify("Marche_de_l’ours.gif")
        )
    }

    @Test
    fun `slugify replaces smart double quotes with dots`() {
        // “ ” (U+201C / U+201D) → .
        assertEquals(
            "Rowing_.T-bar.gif",
            GifUrlResolver.slugify("Rowing_“T-bar”.gif")
        )
    }

    @Test
    fun `slugify replaces straight apostrophe with dot`() {
        assertEquals(
            "L.exercice.gif",
            GifUrlResolver.slugify("L'exercice.gif")
        )
    }

    @Test
    fun `slugify is idempotent (slugify x equals slugify slugify x)`() {
        val source = "Curl_à_la_poulie_(unilatéral).gif"
        val once = GifUrlResolver.slugify(source)
        val twice = GifUrlResolver.slugify(once)
        assertEquals(once, twice)
    }

    @Test
    fun `slugify preserves underscores hyphens and dots`() {
        assertEquals("Pec-deck_ou_butterfly.gif", GifUrlResolver.slugify("Pec-deck_ou_butterfly.gif"))
    }

    @Test
    fun `slugify collapses consecutive dots`() {
        assertEquals(
            "A.B.C.gif",
            GifUrlResolver.slugify("A((B))..C.gif")
        )
    }

    // ─── urlFor : assemblage URL complète ────────────────────────────────

    @Test
    fun `urlFor builds expected URL for simple filename`() {
        assertEquals(
            "https://github.com/archiducarmel/fitcoach/releases/download/gifs/Cercles_de_hanches.gif?v=1",
            GifUrlResolver.urlFor("Cercles_de_hanches.gif")
        )
    }

    @Test
    fun `urlFor strips accents in result`() {
        val url = GifUrlResolver.urlFor("Développé_couché.gif")
        assertEquals(
            "https://github.com/archiducarmel/fitcoach/releases/download/gifs/Developpe_couche.gif?v=1",
            url
        )
    }

    @Test
    fun `urlFor includes version query param`() {
        val url = GifUrlResolver.urlFor("Squat.gif")
        assertTrue("URL doit contenir ?v=", url.contains("?v="))
    }

    @Test
    fun `urlFor accepts uppercase GIF extension`() {
        val url = GifUrlResolver.urlFor("Squat.GIF")
        assertTrue("URL doit être construite même si .GIF en majuscules", url.startsWith(GifUrlResolver.BASE_URL))
    }

    @Test
    fun `urlFor throws when extension is missing`() {
        assertThrows(IllegalArgumentException::class.java) {
            GifUrlResolver.urlFor("Squat")
        }
    }

    @Test
    fun `urlFor throws when extension is wrong`() {
        assertThrows(IllegalArgumentException::class.java) {
            GifUrlResolver.urlFor("Squat.png")
        }
    }

    // ─── Cas réels du catalogue (non-régression) ─────────────────────────

    @Test
    fun `urlFor handles all known special-char filenames from catalogue`() {
        // Mapping vérifié empiriquement contre les 506 assets de la release GitHub.
        val cases = listOf(
            "Curl_à_la_barre.gif" to "Curl_a_la_barre.gif",
            "Curl_inversé_à_la_barre.gif" to "Curl_inverse_a_la_barre.gif",
            "Élévations_latérales.gif" to "Elevations_laterales.gif",
            "Pec_deck_inversé.gif" to "Pec_deck_inverse.gif",
            "Soulevé_de_terre.gif" to "Souleve_de_terre.gif",
            "Soulevé_de_terre_roumain.gif" to "Souleve_de_terre_roumain.gif",
            "Développé_Arnold.gif" to "Developpe_Arnold.gif",
            "Vélo_spinning.gif" to "Velo_spinning.gif",
            "Étirement_du_psoas.gif" to "Etirement_du_psoas.gif",
            "Squat_statique_contre_un_mur_(exercice_de_la_chaise).gif"
                to "Squat_statique_contre_un_mur_.exercice_de_la_chaise.gif",
            "Rotation_externe_de_l’épaule_à_la_poulie.gif"
                to "Rotation_externe_de_l.epaule_a_la_poulie.gif"
        )
        for ((source, expectedSlug) in cases) {
            val expectedUrl = "${GifUrlResolver.BASE_URL}$expectedSlug?v=${GifUrlResolver.VERSION}"
            assertEquals("Mismatch pour: $source", expectedUrl, GifUrlResolver.urlFor(source))
        }
    }

    @Test
    fun `gif top-level helper produces same URL as urlFor`() {
        assertEquals(GifUrlResolver.urlFor("Squat.gif"), gif("Squat.gif"))
    }
}
