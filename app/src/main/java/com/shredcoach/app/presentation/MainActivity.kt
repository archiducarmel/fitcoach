package com.shredcoach.app.presentation

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.session.ActiveSessionManager
import com.shredcoach.app.notification.AppNotificationDispatcher
import com.shredcoach.app.presentation.common.GeminiRetryBanner
import com.shredcoach.app.presentation.common.IncomingShareIntent
import com.shredcoach.app.presentation.navigation.ShredCoachNavigation
import com.shredcoach.app.presentation.theme.ShredCoachTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: ActiveSessionManager
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var llmFallbackBus: com.shredcoach.app.domain.llm.LlmFallbackBus

    // State observé par Compose : bump à chaque nouveau tap notif (onCreate + onNewIntent)
    private val openNotificationsState = mutableStateOf(0)

    // Deeplink route demandé par une notif (ou bouton d'action). Format :
    // (counter, route) — counter pour forcer la recomposition même si la
    // route est la même que la précédente. Route null = pas de deeplink.
    private val deeplinkRouteState = mutableStateOf<Pair<Int, String?>>(0 to null)

    // Le splash reste affiché tant que ce flag est false. Évite le flash
    // splash → spinner → Home en gardant le splash pendant que le profile
    // initial est chargé. @Volatile : le splashscreen lit ce flag depuis
    // sa boucle de frame, on garantit la visibilité cross-thread.
    @Volatile private var splashKeptForProfile: Boolean = true

    /**
     * Wrappe le Context de base avec la locale courante (`Locale.getDefault()`).
     *
     * **Pourquoi pas AppCompatDelegate** : sur Android 16 (API 36), validé via
     * logcat user, `AppCompatDelegate.setApplicationLocales()` ne persiste pas
     * la locale → `getApplicationLocales()` retourne vide juste après l'appel,
     * et la nouvelle Activity post-recreate hérite du locale système (souvent
     * différent de celui choisi par l'user).
     *
     * **Stratégie** : `LocaleManager.applyToFramework` appelle
     * `Locale.setDefault(...)` qui MARCHE (validé : `default='en'` dans le log
     * onCreate). On utilise donc `Locale.getDefault()` comme source de vérité
     * runtime, et on force la Configuration du Context de base à refléter cette
     * locale. Compose lit `stringResource()` depuis ce Configuration → strings
     * dans la bonne langue.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapContextWithDefaultLocale(newBase))
    }

    private fun wrapContextWithDefaultLocale(base: Context): Context {
        val defaultLocale = java.util.Locale.getDefault()
        val baseLocale = base.resources.configuration.locales.takeIf { !it.isEmpty }?.get(0)
        if (baseLocale != null && baseLocale.toLanguageTag() == defaultLocale.toLanguageTag()) {
            android.util.Log.i("MainActivity", "attachBaseContext: locale already matches (${defaultLocale.toLanguageTag()}), base unchanged")
            return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(defaultLocale)
        android.util.Log.i("MainActivity", "attachBaseContext: wrapping with locale=${defaultLocale.toLanguageTag()} (was=${baseLocale?.toLanguageTag()})")
        return base.createConfigurationContext(config)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { splashKeptForProfile }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Diagnostic locale au démarrage de l'Activity (utile pour debug
        // changement de langue : on voit si la nouvelle Activity post-recreate
        // a effectivement la bonne locale).
        runCatching {
            val appLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val resLocale = resources.configuration.locales.takeIf { !it.isEmpty }?.get(0)
            android.util.Log.i(
                "MainActivity",
                "onCreate: SDK=${Build.VERSION.SDK_INT} appLocales='$appLocales' " +
                "resLocale='$resLocale' default='${java.util.Locale.getDefault().toLanguageTag()}'"
            )
        }
        if (intent?.getBooleanExtra(AppNotificationDispatcher.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotificationsState.value = openNotificationsState.value + 1
        }
        intent?.getStringExtra(AppNotificationDispatcher.EXTRA_DEEPLINK_ROUTE)?.let { route ->
            deeplinkRouteState.value = (deeplinkRouteState.value.first + 1) to route
        }
        // ACTION_SEND : l'user a partagé une image vers une cible ShredCoach
        // (via activity-alias dans le manifest). Route vers la bonne destination.
        intent?.let { handleIncomingShare(it) }

        // Restaure une séance non-complétée (<24h) après un cold-start. Idempotent
        // — la tentative est garde-fou-ée dans le manager, donc onCreate multiple
        // (config change, retour Activity) ne cause pas de double-restore.
        lifecycleScope.launch {
            sessionManager.tryRestoreFromDb()
        }

        setContent {
            // Observer le profil en Flow → thème + palette réactifs aux changements settings
            val profile by userRepository.getUserProfile()
                .collectAsState(initial = null)
            val hasProfile = profile != null // null = loading au premier affichage

            // Premier chargement strict (blocking) pour savoir si on route vers onboarding.
            // try/finally OBLIGATOIRE : si la lecture profil throw (DB corrompue,
            // migration foirée, etc.), il faut TOUT DE MÊME libérer la splash et
            // marquer le profil "chargé" — sinon l'app reste gelée à vie sur la
            // splash screen, sans aucun moyen de récupération côté utilisateur.
            var profileLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                try {
                    userRepository.getUserProfileOnce()
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "Initial profile load failed", t)
                } finally {
                    profileLoaded = true
                    splashKeptForProfile = false
                }
            }

            val openNotifsTrigger by openNotificationsState
            val deeplinkPair by deeplinkRouteState

            val darkMode = profile?.darkMode ?: "auto"
            val paletteKey = profile?.themePalette ?: "sunset"

            val forceDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            ShredCoachTheme(darkTheme = forceDark, paletteKey = paletteKey) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Box global : NavHost en fond + GeminiRetryBanner aligné
                    // en bas pour rassurer l'user pendant un retry transparent.
                    // Le banner est SOUS la nav bar (navigationBarsPadding) pour
                    // ne pas être masqué par le swipe gesture handle.
                    Box(Modifier.fillMaxSize()) {
                        when {
                            !profileLoaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                            else -> ShredCoachNavigation(
                                sessionManager = sessionManager,
                                hasProfile = hasProfile,
                                openNotificationsTrigger = openNotifsTrigger,
                                deeplinkRoute = deeplinkPair,
                            )
                        }
                        // Banner global Gemini retry — overlay non-bloquant.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                        ) {
                            GeminiRetryBanner()
                        }
                        // Banner LLM fallback — overlay top, ~4s, humoristique.
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        ) {
                            com.shredcoach.app.presentation.components.LlmFallbackBanner(bus = llmFallbackBus)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Remplace l'intent courant
        if (intent.getBooleanExtra(AppNotificationDispatcher.EXTRA_OPEN_NOTIFICATIONS, false)) {
            // Bump le state observé par Compose → LaunchedEffect dans ShredCoachNavigation réagit
            openNotificationsState.value = openNotificationsState.value + 1
        }
        intent.getStringExtra(AppNotificationDispatcher.EXTRA_DEEPLINK_ROUTE)?.let { route ->
            deeplinkRouteState.value = (deeplinkRouteState.value.first + 1) to route
        }
        handleIncomingShare(intent)
    }

    /**
     * Route un ACTION_SEND (mime image) vers la destination fonctionnelle
     * choisie par l'user dans la system share sheet.
     *
     * Identification de la cible via intent.component.className :
     *  - ShareGlucoseAlias - analyse glycemique
     *  - ShareMealAlias    - analyse repas
     *
     * Si l'intent vient d'autre part (alias inconnu, intent direct sans
     * component, action differente), on ignore silencieusement.
     */
    private fun handleIncomingShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        // IntentCompat gère le split d'API entre legacy getParcelableExtra
        // (deprecated en 33+) et la nouvelle signature typée Class<T>.
        val uri = androidx.core.content.IntentCompat
            .getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
            ?: return

        val className = intent.component?.className.orEmpty()
        val target = when {
            className.endsWith("ShareGlucoseAlias") -> IncomingShareIntent.Target.GLUCOSE
            className.endsWith("ShareMealAlias") -> IncomingShareIntent.Target.MEAL
            else -> return
        }
        android.util.Log.i("MainActivity", "handleIncomingShare: target=$target uri=$uri")
        IncomingShareIntent.set(target, uri)
    }
}
