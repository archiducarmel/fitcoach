package com.shredcoach.app.domain.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Renvoie un Context dont les Resources utilisent `Locale.getDefault()` (la
 * locale runtime de l'app, alimentée par [LocaleManager.applyToFramework]).
 *
 * **Pourquoi pas juste `appContext.getString(...)`** : sur Android, `Resources`
 * sont créées au démarrage avec la Configuration du moment et ne sont jamais
 * re-rafraichies si on appelle uniquement `Locale.setDefault(...)`. Un
 * `appContext` injecté via Hilt (`@ApplicationContext`) reflète donc la
 * locale système au lancement, PAS la locale choisie ensuite par l'user
 * dans Settings.
 *
 * **Usage** : depuis n'importe quel ViewModel/use-case qui résout des
 * `R.string` côté backend (insights texte, verdicts, notifs construites
 * server-side) :
 *
 *   appContext.withCurrentLocale().getString(R.string.stats_insight_X, …)
 *
 * Ne PAS l'utiliser depuis les Composables — `LocalContext.current` y est
 * déjà wrappé par MainActivity.attachBaseContext sur la bonne locale.
 *
 * **Coût** : `createConfigurationContext` est ~10µs et ses Resources
 * partagent l'AssetManager du parent, pas de fuite mémoire à craindre.
 */
fun Context.withCurrentLocale(): Context {
    val cfg = Configuration(resources.configuration)
    cfg.setLocale(Locale.getDefault())
    return createConfigurationContext(cfg)
}
