package com.shredcoach.app.di

import com.shredcoach.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Module Hilt centralisant la configuration réseau de l'app.
 *
 * Fournit un [OkHttpClient] de base que les services dérivent via
 * [OkHttpClient.newBuilder] pour ajuster les timeouts à leurs besoins
 * (LLM streaming = 120s, image generation = 180s, metadata = 45s).
 *
 * Bénéfices :
 * - Logging réseau **activé en debug uniquement** (BuildConfig.DEBUG),
 *   évite les fuites de clés API en Logcat sur les builds release.
 * - **Redaction des en-têtes sensibles** : Authorization, x-api-key,
 *   x-goog-api-key sont masqués dans les logs (affichés "█REDACTED█").
 * - User-Agent unifié pour identifier l'app côté providers.
 * - Pool de connexions partagé entre services = moins de TLS handshakes.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Qualifier pour injecter explicitement le client de base.
     * Permet à un service de demander `@BaseHttpClient OkHttpClient`
     * et de le dériver pour ses propres timeouts.
     */
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class BaseHttpClient

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val interceptor = HttpLoggingInterceptor()
        // En debug : log complet (corps + headers) pour faciliter l'inspection.
        // En release : aucun log réseau (NONE) — les corps de requêtes contiennent
        // potentiellement des données utilisateur (photos base64, prompts, etc.).
        interceptor.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        // Masquage des en-têtes d'authentification (même en debug : on ne veut
        // jamais voir la clé API en Logcat, c'est trop facile à screenshot/share).
        interceptor.redactHeader("Authorization")
        interceptor.redactHeader("x-api-key")
        interceptor.redactHeader("x-goog-api-key")
        return interceptor
    }

    /**
     * Client de base partagé. Connect timeout court (30s) ; les services
     * dérivent leurs propres `read`/`write` timeouts via `.newBuilder()`.
     */
    @Provides
    @Singleton
    @BaseHttpClient
    fun provideBaseOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val buildType = if (BuildConfig.DEBUG) "debug" else "release"
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ShredCoach/${BuildConfig.VERSION_NAME} ($buildType)")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
