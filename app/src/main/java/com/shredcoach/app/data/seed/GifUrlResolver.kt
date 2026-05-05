package com.shredcoach.app.data.seed

import java.text.Normalizer

/**
 * Résolution d'URL pour les GIFs d'exercices stockés sur GitHub Releases.
 *
 * **Pourquoi GitHub Releases plutôt que des assets bundlés** :
 *  - Les ~500 GIFs représentent ~250MB. Bundlés dans l'APK, ça ferait sauter
 *    la limite Play Store (150MB par AAB) et augmenterait drastiquement
 *    le coût d'install pour l'utilisateur (data+stockage).
 *  - GitHub Releases sert les fichiers via CDN gratuitement, sans rate-limit
 *    pour usage normal d'une app mobile. Coil cache localement (disk cache)
 *    chaque GIF après le premier affichage → coût réseau payé une seule fois
 *    par exercice consulté.
 *  - Versionnement : nouveau set de GIFs = nouvelle release tag, pas de update
 *    APK requis pour rajouter/corriger un GIF.
 *
 * **Slugify GitHub** : à l'upload, GitHub Releases normalise agressivement les
 * noms d'assets — accents strippés, caractères non-`[A-Za-z0-9._-]` remplacés
 * par `.`, séquences de `.` collapsées. Empiriquement (vérifié sur les 506
 * assets de la release `gifs`) :
 *   - `Curl_à_la_barre.gif`         → `Curl_a_la_barre.gif`
 *   - `Squat_(exercice).gif`        → `Squat_.exercice.gif`
 *   - `Rotation_à_90°_à_la_poulie.gif` → `Rotation_a_90._a_la_poulie.gif`
 *   - `Marche_de_l'ours.gif`        → `Marche_de_l.ours.gif`
 * Le helper [slugify] reproduit cette règle pour que tout filename source
 * (avec ou sans accents) résolve vers l'asset GitHub correct.
 *
 * **Cache-busting** : [VERSION] est appendée en query param `?v=N` pour que
 * Coil DiskCache distingue les versions d'un GIF si on republie une release
 * avec le même nom mais un contenu corrigé. Bumper [VERSION] invalide le
 * cache disque pour tous les utilisateurs au prochain fetch.
 *
 * @sample
 *   GifUrlResolver.urlFor("Cercles_de_hanches.gif")
 *   // → "https://github.com/.../gifs/Cercles_de_hanches.gif?v=1"
 *
 *   GifUrlResolver.urlFor("Curl_à_la_barre.gif")
 *   // → "https://github.com/.../gifs/Curl_a_la_barre.gif?v=1"  (accents strippés)
 *
 *   GifUrlResolver.urlFor("Squat_statique_contre_un_mur_(exercice_de_la_chaise).gif")
 *   // → "https://github.com/.../gifs/Squat_statique_contre_un_mur_.exercice_de_la_chaise.gif?v=1"
 */
object GifUrlResolver {

    /** Base URL des GIFs (release tag "gifs" du repo public). */
    const val BASE_URL = "https://github.com/archiducarmel/fitcoach/releases/download/gifs/"

    /**
     * Version du catalogue GIFs. Incrémenter pour invalider le DiskCache de
     * Coil (cf. [com.shredcoach.app.ShredCoachApplication.newImageLoader]) si
     * une release publiée est corrigée avec le même nom de fichier — sinon
     * Coil servira la version stale en cache.
     */
    const val VERSION = 1

    /**
     * Combining mark Unicode category — utilisé pour stripper les diacritiques
     * (à → a, é → e, ç → c, etc.) après normalisation NFKD.
     */
    private val DIACRITIC_REGEX = "\\p{Mn}+".toRegex()

    /** Caractères non-allowés par le slugify GitHub (négation de [A-Za-z0-9._-]). */
    private val UNSAFE_REGEX = "[^A-Za-z0-9._-]".toRegex()

    /** Séquences de 2+ points consécutifs, à collapse en un seul `.`. */
    private val COLLAPSE_DOTS_REGEX = "\\.{2,}".toRegex()

    /**
     * Reproduit le slugify appliqué par GitHub Releases lors de l'upload :
     *  1. NFKD normalize (décompose les caractères composés : `é` → `e` + ◌́)
     *  2. Strip combining marks (les diacritiques `◌́`, `◌̀`, etc. partent)
     *  3. Replace tout caractère non-`[A-Za-z0-9._-]` par `.`
     *  4. Collapse les `..` consécutifs en un seul `.`
     *
     * Idempotent : `slugify(slugify(x)) == slugify(x)`.
     */
    internal fun slugify(filename: String): String {
        val nfkd = Normalizer.normalize(filename, Normalizer.Form.NFKD)
        val noDiacritic = DIACRITIC_REGEX.replace(nfkd, "")
        val cleaned = UNSAFE_REGEX.replace(noDiacritic, ".")
        return COLLAPSE_DOTS_REGEX.replace(cleaned, ".")
    }

    /**
     * Construit l'URL complète d'un GIF à partir de son nom de fichier source.
     * Le nom doit inclure l'extension `.gif` (case-insensitive).
     *
     * Le filename peut contenir accents, parenthèses, `°`, etc. — ils seront
     * normalisés via [slugify] pour matcher l'asset GitHub.
     *
     * @throws IllegalArgumentException si le nom ne se termine pas par `.gif`.
     */
    fun urlFor(filename: String): String {
        require(filename.endsWith(".gif", ignoreCase = true)) {
            "GIF filename must end with .gif, got: $filename"
        }
        val slugged = slugify(filename)
        return "$BASE_URL$slugged?v=$VERSION"
    }
}

/**
 * Helper top-level pour utilisation concise dans [SeedData] :
 *
 * ```kotlin
 * gifUrl = gif("Squat.gif")
 * ```
 *
 * Internal pour scoper l'API à seed/ — les Composables UI utilisent directement
 * la valeur stockée (déjà une URL complète après [GifUrlResolver.urlFor]).
 */
internal fun gif(filename: String): String = GifUrlResolver.urlFor(filename)
