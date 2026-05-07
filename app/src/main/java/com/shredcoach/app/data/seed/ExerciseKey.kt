package com.shredcoach.app.data.seed

import java.text.Normalizer

/**
 * Dérive une clé stable, ASCII snake_case, à partir du nom français d'un
 * exercice. Cette clé sert de *passerelle i18n* :
 *
 *  - Persistée dans `exercises.exerciseKey` (DB) — référence stable
 *    indépendante de la langue.
 *  - Utilisée pour résoudre `R.string.exo_<key>_<field>` au moment de
 *    l'affichage (cf. `ExerciseI18n`). Si la traduction existe pour la
 *    locale courante, elle est servie ; sinon, on retombe sur le texte FR
 *    canonique du DB.
 *
 * Règles de normalisation (volontairement strictes pour rester compatibles
 * avec les conventions Android resource IDs `[a-z0-9_]`) :
 *
 *  1. NFKD : décompose les caractères composés (`é` → `e` + ◌́).
 *  2. Strip diacritiques (combining marks).
 *  3. Lowercase ASCII.
 *  4. Remplace tout caractère non-`[a-z0-9]` par `_`.
 *  5. Collapse séquences de `_` consécutifs.
 *  6. Trim `_` en début/fin.
 *
 * Idempotent : `fromName(fromName(x)) == fromName(x)`.
 *
 * @sample
 *   ExerciseKey.fromName("Presse à cuisses")        // → "presse_a_cuisses"
 *   ExerciseKey.fromName("Squat (exercice)")        // → "squat_exercice"
 *   ExerciseKey.fromName("Rotation à 90°")          // → "rotation_a_90"
 *   ExerciseKey.fromName("Soulevé de terre roumain") // → "souleve_de_terre_roumain"
 */
object ExerciseKey {

    private val DIACRITIC_REGEX = "\\p{Mn}+".toRegex()
    private val NON_ALNUM_REGEX = "[^a-z0-9]+".toRegex()
    private val EDGE_UNDERSCORE_REGEX = "(^_+)|(_+$)".toRegex()

    fun fromName(name: String): String {
        val nfkd = Normalizer.normalize(name, Normalizer.Form.NFKD)
        val noDiacritic = DIACRITIC_REGEX.replace(nfkd, "")
        val lowered = noDiacritic.lowercase()
        val underscored = NON_ALNUM_REGEX.replace(lowered, "_")
        return EDGE_UNDERSCORE_REGEX.replace(underscored, "")
    }
}
