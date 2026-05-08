package com.shredcoach.app.domain.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Façade i18n centrale — applique le locale choisi par l'utilisateur partout.
 *
 * **Architecture** :
 *  - Source de vérité : [com.shredcoach.app.data.local.entity.UserProfileEntity.languageTag]
 *    (DB v38). Null = pas encore choisi → on utilise l'auto-détection système.
 *  - Application runtime : [AppCompatDelegate.setApplicationLocales] (API 33+)
 *    triggers automatiquement un `recreate()` de l'Activity courante. Pour API <33,
 *    AppCompatDelegate fait un overlay du Configuration et gère le recreate aussi.
 *  - Persistance : `autoStoreLocales=true` dans le Manifest service permet à
 *    AppCompatDelegate de persister le locale entre les cold-starts sans qu'on
 *    ait à le re-appliquer manuellement. **Mais** on garde [UserProfileEntity]
 *    comme source de vérité pour ne pas dépendre du framework qui pourrait
 *    perdre le locale (clear data, restore from backup, etc.).
 *
 * **Cycle de vie** :
 *  1. Cold-start : [Application.onCreate] (avant toute Activity) → on lit
 *     `userProfile.languageTag` ; si null on auto-détecte ; on applique.
 *  2. User change la langue : [setLocale] → DB write + AppCompatDelegate →
 *     Activity recreate automatique → toute l'UI se ré-affiche en nouvelle langue.
 *
 * **Singleton + Hilt** : injecté partout où on a besoin du locale courant
 * (formatters, voice phrasebook resolver, LLM prompt builder).
 */
@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
) {

    /**
     * Applique le locale stocké en DB au démarrage de l'app. À appeler depuis
     * [com.shredcoach.app.ShredCoachApplication.onCreate] AVANT que toute
     * Activity ne se construise. Si null en DB (premier launch), on persiste
     * silencieusement l'auto-détection pour que les launches suivants soient
     * cohérents.
     */
    suspend fun applyPersistedOrDetect() {
        val profile = userRepository.getUserProfileOnce()
        val tag = profile?.languageTag
        val resolved = if (tag.isNullOrBlank()) {
            // Premier launch (ou profil absent) → auto-détect système
            val detected = AppLocale.autoDetect(systemDefaultLocale())
            // Si le profil existe (cas migration v37→v38), on persiste le choix auto
            // pour que les launches suivants ne ré-auto-détectent pas (l'user a pu
            // changer la langue système entre temps mais notre choix reste stable).
            if (profile != null) {
                userRepository.updateLanguageTag(detected.tag)
            }
            detected
        } else {
            AppLocale.fromTag(tag)
        }
        applyToFramework(resolved)
    }

    /**
     * Change la langue courante : persiste en DB + applique runtime via
     * AppCompatDelegate (qui trigger un recreate de l'Activity). À appeler
     * depuis le picker (Settings ou Onboarding).
     *
     * **Idempotent** : si la locale est déjà active, no-op (évite un recreate
     * inutile qui ferait flasher l'UI).
     */
    suspend fun setLocale(appLocale: AppLocale) {
        android.util.Log.i(TAG, "setLocale called with tag=${appLocale.tag}")
        val profile = userRepository.getUserProfileOnce()
        android.util.Log.i(TAG, "current DB tag=${profile?.languageTag}")
        if (profile?.languageTag == appLocale.tag) {
            android.util.Log.i(TAG, "early-return: locale already active")
            return
        }
        userRepository.updateLanguageTag(appLocale.tag)
        android.util.Log.i(TAG, "DB updated → calling applyToFramework")
        applyToFramework(appLocale)
        android.util.Log.i(TAG, "applyToFramework done → scheduling recreate")
        scheduleRecreateCurrentActivity()
    }

    private fun scheduleRecreateCurrentActivity() {
        val app = context.applicationContext as? ShredCoachApplication
        if (app == null) {
            android.util.Log.w(TAG, "applicationContext is NOT ShredCoachApplication — recreate skipped")
            return
        }
        val activity = app.currentActivity()
        if (activity == null) {
            android.util.Log.w(TAG, "currentActivity() returned null — recreate skipped")
            return
        }
        android.util.Log.i(TAG, "posting recreate() on Main looper for $activity")
        Handler(Looper.getMainLooper()).post {
            android.util.Log.i(TAG, "Main looper tick → calling activity.recreate()")
            activity.recreate()
        }
    }

    companion object {
        private const val TAG = "LocaleManager"
    }

    /**
     * Locale courante observée comme [Flow] — utile pour les composables /
     * ViewModels qui doivent recomposer/recharger sur changement (ex:
     * formatters dans un widget, prompts LLM en cours de session).
     *
     * **Note** : la source de vérité reste DB. Le Flow émet à chaque update DB
     * de `languageTag`. Compose recompose grâce à `collectAsState`.
     */
    val currentLocaleFlow: Flow<AppLocale> = userRepository.getUserProfile()
        .map { AppLocale.fromTag(it?.languageTag) }

    /**
     * Lecture synchrone du locale courant. Utilisé par les composants non-
     * suspendables (lint formatters appelés en pleine recomposition). Préfère
     * [currentLocaleFlow] si possible pour la réactivité.
     */
    fun currentLocaleSync(): AppLocale {
        val configLocale = Locale.getDefault()
        return AppLocale.fromTag(configLocale.language)
    }

    /**
     * Applique la locale au framework Android via AppCompatDelegate.
     * - API 33+ : per-app locale natif Android, persiste automatiquement.
     * - API <33 : AppCompatDelegate gère un overlay Configuration avec
     *   recreate() de l'Activity automatique.
     *
     * **Threading** : doit tourner sur le main thread. Hilt + AppCompatDelegate
     * gèrent la dispatch correctement, mais si on appelle depuis un Worker/IO
     * il faut wrap avec `withContext(Dispatchers.Main)`.
     */
    private fun applyToFramework(appLocale: AppLocale) {
        val list = LocaleListCompat.forLanguageTags(appLocale.tag)
        AppCompatDelegate.setApplicationLocales(list)
        // Force la propagation synchrone à `Locale.getDefault()` — certaines
        // APIs internes (formatters, ICU) lisent via getDefault() sans passer
        // par Resources. Sans ce setDefault, on peut avoir un décalage entre
        // setApplicationLocales (async côté framework) et la première lecture
        // de getDefault par les Composables après recreate.
        java.util.Locale.setDefault(appLocale.toJavaLocale())
        android.util.Log.i(TAG, "applyToFramework: AppCompat+Locale.setDefault → ${appLocale.tag}")
    }

    private fun systemDefaultLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales: LocaleList = Resources_getSystem_configuration().locales
            if (locales.isEmpty) Locale.getDefault() else locales[0]
        } else {
            @Suppress("DEPRECATION")
            Resources_getSystem_configuration().locale
        }
    }

    /**
     * Lit la `Configuration` SYSTÈME (pas de l'app), pour récupérer la vraie
     * locale système même si AppCompatDelegate a déjà overlayé celle de l'app.
     * Usage : auto-détection initiale au premier launch.
     */
    private fun Resources_getSystem_configuration(): Configuration =
        android.content.res.Resources.getSystem().configuration
}
