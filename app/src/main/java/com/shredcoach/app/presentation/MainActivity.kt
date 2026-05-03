package com.shredcoach.app.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(AppNotificationDispatcher.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            openNotificationsState.value = openNotificationsState.value + 1
        }

        setContent {
            // Observer le profil en Flow → thème + palette réactifs aux changements settings
            val profile by userRepository.getUserProfile()
                .collectAsState(initial = null)
            val hasProfile = profile != null // null = loading au premier affichage

            // Premier chargement strict (blocking) pour savoir si on route vers onboarding
            var profileLoaded by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                userRepository.getUserProfileOnce()
                profileLoaded = true
            }

            val openNotifsTrigger by openNotificationsState

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
                        else -> ShredCoachNavigation(sessionManager, hasProfile, openNotifsTrigger)
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
    }
}
