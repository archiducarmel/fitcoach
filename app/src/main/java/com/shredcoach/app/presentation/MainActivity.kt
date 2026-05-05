package com.shredcoach.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.domain.session.ActiveSessionManager
import com.shredcoach.app.notification.AppNotificationDispatcher
import com.shredcoach.app.presentation.navigation.ShredCoachNavigation
import com.shredcoach.app.presentation.theme.ShredCoachTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: ActiveSessionManager
    @Inject lateinit var userRepository: UserRepository

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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { splashKeptForProfile }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(AppNotificationDispatcher.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotificationsState.value = openNotificationsState.value + 1
        }
        intent?.getStringExtra(AppNotificationDispatcher.EXTRA_DEEPLINK_ROUTE)?.let { route ->
            deeplinkRouteState.value = (deeplinkRouteState.value.first + 1) to route
        }

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
                    when {
                        !profileLoaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        else -> ShredCoachNavigation(
                            sessionManager = sessionManager,
                            hasProfile = hasProfile,
                            openNotificationsTrigger = openNotifsTrigger,
                            deeplinkRoute = deeplinkPair,
                        )
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
    }
}
