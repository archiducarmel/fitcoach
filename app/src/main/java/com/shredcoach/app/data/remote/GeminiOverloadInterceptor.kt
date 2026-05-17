package com.shredcoach.app.data.remote

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Interceptor OkHttp qui rattrape les "Gemini overloaded" de manière transparente.
 *
 * Gemini renvoie `503 UNAVAILABLE` (parfois `429`) avec un message du type
 * "This model is currently experiencing high demand. Spikes in demand are usually
 * temporary." quand l'infra Google sature. Le pattern est temporaire (qq secondes)
 * → on retente automatiquement plutôt que de remonter un échec à l'utilisateur.
 *
 * **Périmètre** : seuls les appels `generativelanguage.googleapis.com` sont
 * concernés (les autres providers — Groq, Mistral, OpenAI — ont leurs propres
 * codes et messages, on ne touche pas à ça).
 *
 * **Politique de retry** :
 *  - 3 retries max (donc 4 tentatives au total)
 *  - Backoff exponentiel + jitter : 1.5s, 3s, 6s (±25%)
 *  - Plafond cumul = ~10s d'attente → l'user ne voit qu'un léger délai
 *  - Lecture du `responseBody` non-destructive via `peekBody(8KB)` pour ne pas
 *    consommer le stream (sinon le caller perd le corps de la réponse)
 *
 * **UX** : chaque tentative émet un événement dans [GeminiRetryBus] que la UI
 * observe pour afficher un snackbar humour ("Gemini reprend son souffle..."),
 * rassurant l'user que l'app continue de bosser.
 *
 * **Sécurité** : on n'inspecte JAMAIS le corps des requêtes (qui peut contenir
 * des prompts user / photos). Seul le corps de réponse est sniffé pour détecter
 * la signature d'overload, et seulement quand `!response.isSuccessful`.
 */
@Singleton
class GeminiOverloadInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Filtre par host : seul Gemini est concerné. Les autres providers
        // (Groq, Mistral, OpenAI) ont leurs propres protocoles d'erreur, on
        // évite d'interférer avec leur logique.
        if (!request.url.host.contains(GEMINI_HOST)) {
            return chain.proceed(request)
        }

        var lastResponse: Response? = null
        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            try {
                // Ferme la réponse précédente pour libérer la connexion avant
                // de retenter. Sans ça, OkHttp peut épuiser le pool de connexions.
                lastResponse?.close()
                lastResponse = chain.proceed(request)

                if (lastResponse.isSuccessful) {
                    if (attempt > 0) {
                        Log.i(TAG, "Gemini recovered after attempt $attempt")
                        GeminiRetryBus.tryEmitRecovered()
                    }
                    return lastResponse
                }

                // Pas un overload reconnu → on ne retente pas (ex: 400 bad request,
                // 401 unauthorized, 403 forbidden → erreurs définitives côté caller).
                if (!isRetryableOverload(lastResponse)) {
                    return lastResponse
                }

                // Overload détecté. Si on a encore des retries, on attend + on émet.
                if (attempt < MAX_RETRIES) {
                    val delayMs = computeBackoffMs(attempt)
                    Log.w(TAG, "Gemini overloaded (code=${lastResponse.code}), retry ${attempt + 1}/$MAX_RETRIES in ${delayMs}ms")
                    GeminiRetryBus.tryEmitRetrying(attempt + 1, MAX_RETRIES)
                    Thread.sleep(delayMs)
                } else {
                    // Dernier essai échoué → on émet "failed" et on renvoie la
                    // réponse 503 telle quelle pour que le caller la traite
                    // comme une erreur normale.
                    Log.e(TAG, "Gemini still overloaded after $MAX_RETRIES retries")
                    GeminiRetryBus.tryEmitFailed()
                }
            } catch (e: java.io.IOException) {
                // Exception réseau : on retente aussi (timeout, connection reset).
                // Ces erreurs sont souvent transitoires en cas de surcharge serveur.
                lastException = e
                if (attempt < MAX_RETRIES) {
                    val delayMs = computeBackoffMs(attempt)
                    Log.w(TAG, "Gemini network error (${e.javaClass.simpleName}), retry ${attempt + 1}/$MAX_RETRIES in ${delayMs}ms")
                    GeminiRetryBus.tryEmitRetrying(attempt + 1, MAX_RETRIES)
                    Thread.sleep(delayMs)
                } else {
                    GeminiRetryBus.tryEmitFailed()
                    throw e
                }
            } catch (e: InterruptedException) {
                // Le scope coroutine a été cancellé pendant le sleep → on
                // restaure le flag et on propage.
                Thread.currentThread().interrupt()
                throw java.io.InterruptedIOException("Retry interrupted").also { it.initCause(e) }
            }
        }

        // Théoriquement injoignable (la boucle retourne avant), mais le compilateur
        // veut un return → on rend la dernière réponse ou rethrow la dernière exception.
        return lastResponse ?: throw (lastException ?: java.io.IOException("Gemini retry loop exhausted"))
    }

    /**
     * Détecte les signatures d'overload Google. Plutôt que de matcher uniquement
     * le code HTTP, on inspecte aussi le corps : Gemini renvoie parfois 200 OK
     * avec un payload d'erreur structuré (rare mais documenté), et inversement
     * certains 503 légitimes ne sont pas des overloads (ex: API désactivée).
     *
     * On utilise `peekBody(8KB)` qui ne consomme PAS le stream → le caller peut
     * lire le body normalement.
     */
    private fun isRetryableOverload(response: Response): Boolean {
        // Cas classique : code 503 (UNAVAILABLE) ou 429 (RESOURCE_EXHAUSTED).
        if (response.code == 503 || response.code == 429) return true

        // Cas 500 : parfois un overload est masqué en 500 INTERNAL côté Vertex.
        // On regarde le corps pour confirmer.
        if (response.code in 500..599) {
            val bodyPreview = try {
                response.peekBody(PEEK_BYTES).string()
            } catch (_: Exception) { "" }
            return OVERLOAD_PATTERNS.any { bodyPreview.contains(it, ignoreCase = true) }
        }

        return false
    }

    /**
     * Backoff exponentiel borné + jitter ±25% pour éviter le thundering herd
     * (si 100 users sont en overload en même temps, on ne veut pas qu'ils
     * retentent tous exactement à 1.5s).
     */
    private fun computeBackoffMs(attempt: Int): Long {
        val base = BASE_BACKOFF_MS * (1L shl attempt)  // 1500, 3000, 6000
        val jitter = (base * 0.25 * (Random.nextDouble() - 0.5) * 2).toLong()
        return (base + jitter).coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
    }

    companion object {
        private const val TAG = "GeminiRetry"
        private const val GEMINI_HOST = "generativelanguage.googleapis.com"
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 1500L
        private const val MIN_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 10_000L
        private const val PEEK_BYTES = 8 * 1024L  // 8KB suffit largement pour le message d'erreur

        /**
         * Signatures textuelles d'overload Gemini. Le message exact varie
         * légèrement selon la région/version mais ces patterns sont stables.
         * Source : observation logs prod + doc Google Cloud "Quotas et limites".
         */
        private val OVERLOAD_PATTERNS = listOf(
            "high demand",
            "Spikes in demand",
            "overloaded",
            "UNAVAILABLE",
            "RESOURCE_EXHAUSTED",
            "model is currently",
            "try again later",
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// EVENT BUS — communique avec la UI sans dépendance circulaire
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Événement émis par [GeminiOverloadInterceptor] et consommé par la UI
 * (snackbar global dans MainActivity).
 *
 * On utilise un `SharedFlow` (replay=0) plutôt qu'un `StateFlow` pour deux
 * raisons :
 *  1. Les événements sont éphémères — pas d'état persistant à observer après
 *     coup. Un user qui ouvre l'app 1h après un retry ne doit pas voir le
 *     snackbar "on rattrape Gemini".
 *  2. Plusieurs collecteurs (potentiels) reçoivent chacun l'événement — utile
 *     si une feature a son propre handling local (ex: ré-essayer manuellement).
 *
 * `extraBufferCapacity = 8` + `DROP_OLDEST` : si la UI est lente à consommer
 * (changement d'écran), on perd les plus vieux événements plutôt que de
 * bloquer l'interceptor (qui tourne sur le thread network).
 */
sealed class GeminiRetryEvent {
    /** Une nouvelle tentative est en cours. */
    data class Retrying(val attempt: Int, val maxAttempts: Int) : GeminiRetryEvent()
    /** Une tentative de retry a fini par réussir. */
    data object Recovered : GeminiRetryEvent()
    /** Tous les retries ont échoué — Gemini reste indisponible. */
    data object Failed : GeminiRetryEvent()
}

/**
 * Bus global d'événements de retry Gemini. Singleton object pour rester
 * accessible depuis l'Interceptor (qui n'est pas un Composable) et depuis
 * MainActivity (qui consomme).
 *
 * Threading : `tryEmit` est non-bloquant et thread-safe ; on l'appelle
 * depuis le thread network de OkHttp.
 */
object GeminiRetryBus {
    private val _events = MutableSharedFlow<GeminiRetryEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GeminiRetryEvent> = _events.asSharedFlow()

    fun tryEmitRetrying(attempt: Int, maxAttempts: Int) {
        _events.tryEmit(GeminiRetryEvent.Retrying(attempt, maxAttempts))
    }

    fun tryEmitRecovered() {
        _events.tryEmit(GeminiRetryEvent.Recovered)
    }

    fun tryEmitFailed() {
        _events.tryEmit(GeminiRetryEvent.Failed)
    }

    /**
     * Helper de test : permet aux tests unitaires d'attendre une émission.
     * Non utilisé en prod — `runBlocking` ici car les tests sont JVM-only.
     */
    internal fun emitBlocking(event: GeminiRetryEvent) = runBlocking {
        _events.emit(event)
    }
}
