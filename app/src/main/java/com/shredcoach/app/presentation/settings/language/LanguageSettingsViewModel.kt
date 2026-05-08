package com.shredcoach.app.presentation.settings.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shredcoach.app.domain.locale.AppLocale
import com.shredcoach.app.domain.locale.LocaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM du picker de langue (réutilisé depuis Settings ET Onboarding).
 *
 * **Réactivité** : la locale active est lue depuis [LocaleManager.currentLocaleFlow]
 * (StateFlow stable backed par DataStore/DB) → la sélection cochée se met à jour
 * automatiquement quand l'user revient sur l'écran après un changement.
 *
 * **Effet de bord du `setLocale`** : trigger un recreate de l'Activity courante
 * via AppCompatDelegate → la VM actuelle est détruite, l'écran disparaît avec
 * un fondu, puis réapparaît dans la nouvelle langue. C'est l'UX attendue
 * (pas de modal "redémarrez l'app").
 */
@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val localeManager: LocaleManager,
) : ViewModel() {

    /** Langues affichées dans le picker. V1 only pour l'instant. */
    val availableLocales: List<AppLocale> = AppLocale.entries.toList()

    /** Locale actuellement appliquée (cochée dans le picker). */
    val currentLocale: StateFlow<AppLocale> = localeManager.currentLocaleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, localeManager.currentLocaleSync())

    /** True quand un changement est en cours (évite les double-taps). */
    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    fun selectLocale(locale: AppLocale) {
        android.util.Log.i("LanguageSettingsVM", "selectLocale tapped: ${locale.tag} (isV1=${locale.isV1}, current=${currentLocale.value.tag})")
        if (!locale.isV1) {
            android.util.Log.w("LanguageSettingsVM", "early-return: locale not V1")
            return
        }
        if (currentLocale.value == locale) {
            android.util.Log.w("LanguageSettingsVM", "early-return: locale already current")
            return
        }
        viewModelScope.launch {
            _isApplying.value = true
            try {
                localeManager.setLocale(locale)
                // Pas besoin de stop _isApplying — l'Activity va recreate, la VM
                // est détruite. Si on supporte V2 en cold-restart only, mettre
                // le flag à false ici.
            } catch (t: Throwable) {
                android.util.Log.e("LanguageSettingsVM", "setLocale failed", t)
                _isApplying.value = false
            }
        }
    }
}
