package com.shredcoach.app.domain.locale

import java.util.Locale

/**
 * Langues supportées par ShredCoach (V1 = FR/EN ; V2 ajoute ES/IT/PT/DE).
 *
 * **Pourquoi un enum plutôt qu'une List<Locale>** :
 *  - Source de vérité unique pour les locales supportées (impossible d'ajouter
 *    une langue côté UI sans d'abord l'ajouter ici).
 *  - `tag` stable persisté en DB (`UserProfileEntity.languageTag`) — ne JAMAIS
 *    changer après ship sinon les profils existants pointent vers une locale
 *    inconnue et retombent sur le default.
 *  - `displayNameNative` = "Français" / "English" / "Español"… affiché dans le
 *    picker UI (l'utilisateur cherche sa langue dans SA langue, jamais dans une
 *    langue qu'il ne lit pas).
 *  - `flag` emoji pour repère visuel rapide dans le picker.
 *  - `isV1` distingue ce qu'on ship en première vague (FR + EN) du reste qui
 *    arrive en V2. Permet de masquer les langues V2 dans le picker tant qu'elles
 *    ne sont pas encore traduites.
 *
 * **Tag BCP-47** : on utilise les tags simples (`fr`, `en`, …) plutôt que régionaux
 * (`fr-FR`, `en-US`) pour éviter de fragmenter les ressources et faire correspondre
 * un user `en-GB` à `en-US`/`en-CA` ou `fr-CA`/`fr-BE` à `fr-FR` automatiquement
 * via le fallback Android `values-en/`. Si on a besoin de variantes régionales
 * (date format US vs UK), on les ajoutera sur le `Locale` final, pas sur `tag`.
 */
enum class AppLocale(
    val tag: String,
    val displayNameNative: String,
    val flag: String,
    val isV1: Boolean,
) {
    FRENCH(tag = "fr", displayNameNative = "Français", flag = "🇫🇷", isV1 = true),
    ENGLISH(tag = "en", displayNameNative = "English", flag = "🇬🇧", isV1 = true),
    SPANISH(tag = "es", displayNameNative = "Español", flag = "🇪🇸", isV1 = false),
    ITALIAN(tag = "it", displayNameNative = "Italiano", flag = "🇮🇹", isV1 = false),
    PORTUGUESE(tag = "pt", displayNameNative = "Português", flag = "🇵🇹", isV1 = false),
    GERMAN(tag = "de", displayNameNative = "Deutsch", flag = "🇩🇪", isV1 = false);

    /** [Locale] objet correspondant — utilisé pour les formatters (date, nombre). */
    fun toJavaLocale(): Locale = Locale(tag)

    companion object {
        /** Default historique de l'app. À changer si jamais on rebrande FR-first. */
        val Default: AppLocale = FRENCH

        /**
         * Résout une locale par son tag BCP-47. Robuste à :
         *  - `null` / blank → `Default`
         *  - tag inconnu → `Default` (ex: futur `ja` non encore supporté)
         *  - tag régional `en-US` / `fr-CA` → mapping vers la base (`en` / `fr`)
         *  - cas insensitive (`FR`, `Fr`, `fr` → `FRENCH`)
         */
        fun fromTag(tag: String?): AppLocale {
            if (tag.isNullOrBlank()) return Default
            val base = tag.substringBefore('-').lowercase()
            return entries.firstOrNull { it.tag == base } ?: Default
        }

        /**
         * Auto-détection initiale au premier launch :
         * priorise la langue système si elle fait partie des langues supportées,
         * sinon retombe sur [Default]. Évite à l'utilisateur de re-confirmer
         * sa langue système (UX standard Android).
         */
        fun autoDetect(systemLocale: Locale): AppLocale =
            fromTag(systemLocale.language)

        /** Liste des locales actuellement shippées. */
        val v1Locales: List<AppLocale> = entries.filter { it.isV1 }
    }
}
