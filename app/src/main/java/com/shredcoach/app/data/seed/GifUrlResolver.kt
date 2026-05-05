package com.shredcoach.app.data.seed

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
 * **Convention de nommage** : les GIFs suivent le nom français exact des exos
 * (ex: "Squat.gif", "Curl_à_la_barre.gif"), avec accents et underscores. URL
 * encoding indispensable pour les caractères non-ASCII.
 *
 * **Encoding** : `URLEncoder` Java utilise `+` pour les espaces (form-encoding),
 * mais une URL de path doit utiliser `%20`. On normalise ici. Les caractères
 * accentués (é, à, ç, etc.) sont encodés en UTF-8 puis percent-encoded — c'est
 * exactement ce que GitHub attend (vérifié sur l'URL exemple fournie).
 *
 * @sample
 *   GifUrlResolver.urlFor("Squat.gif")
 *   // → "https://github.com/archiducarmel/fitcoach/releases/download/gifs/Squat.gif"
 *
 *   GifUrlResolver.urlFor("Curl_à_la_barre.gif")
 *   // → "https://github.com/.../Curl_%C3%A0_la_barre.gif"
 */
object GifUrlResolver {

    /** Base URL des GIFs (release tag "gifs" du repo public). */
    const val BASE_URL = "https://github.com/archiducarmel/fitcoach/releases/download/gifs/"

    /**
     * Construit l'URL complète d'un GIF à partir de son nom de fichier.
     * Le nom doit inclure l'extension `.gif`.
     */
    fun urlFor(filename: String): String {
        require(filename.endsWith(".gif", ignoreCase = true)) {
            "GIF filename must end with .gif, got: $filename"
        }
        // URLEncoder.encode encode tout, y compris l'underscore (qu'on veut préserver
        // tel quel pour matcher GitHub). On post-process pour décoder le `+` en `%20`
        // et restaurer les caractères safe (lettres, chiffres, _, -, .).
        val encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "$BASE_URL$encoded"
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
 * la valeur stockée (déjà une URL complète après `urlFor`).
 */
internal fun gif(filename: String): String = GifUrlResolver.urlFor(filename)
