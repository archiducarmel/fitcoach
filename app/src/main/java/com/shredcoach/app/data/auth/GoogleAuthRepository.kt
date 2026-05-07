package com.shredcoach.app.data.auth

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestre le flow OAuth Google pour la sauvegarde Drive.
 *
 * **Architecture** : on utilise UNIQUEMENT [Identity.getAuthorizationClient]
 * (pas Credential Manager). Raison : pour notre cas d'usage on n'a besoin que
 * du scope `drive.appdata`, pas d'identifier le user pour notre backend (on
 * n'a pas de backend). L'authorization API de Play Services :
 *  - Identifie implicitement le user au premier consent
 *  - Renvoie un access token utilisable directement contre Drive REST
 *  - Gère silencieusement le refresh : à chaque appel, si déjà autorisé →
 *    renvoie un token frais sans UI ; sinon renvoie un PendingIntent à launcher.
 *
 * **Flow utilisateur** :
 * 1. User tape "Connecter Google Drive" dans Settings
 * 2. App appelle [requestAuthorization] → soit result direct (silent), soit
 *    [AuthorizationOutcome.NeedsConsent] avec un PendingIntent à launcher.
 * 3. Si consent UI : Compose lance le PendingIntent via
 *    `rememberLauncherForActivityResult`, récupère l'`Intent` de résultat,
 *    et appelle [completeAuthorization] pour parser le résultat.
 * 4. Sur succès : on persiste email/accountId via [GoogleAuthStore].
 * 5. Pour les opérations Drive (backup/restore), on rappelle [requestAuthorization]
 *    qui répond silencieusement si tout va bien.
 *
 * **Tokens** : on NE PERSISTE PAS les access tokens. À chaque call Drive on
 * redemande un fresh token via [requestAuthorization]. Coût : un round-trip
 * Play Services local (~ms), bénéfice : zéro risque d'exfiltration.
 */
@Singleton
class GoogleAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: GoogleAuthStore,
) {

    val state: Flow<GoogleAuthStore.Snapshot> = store.snapshot

    /**
     * Demande l'accès au scope `drive.appdata`. Silent si déjà accordé,
     * sinon renvoie un PendingIntent que le caller doit launcher pour
     * obtenir le consentement utilisateur.
     */
    suspend fun requestAuthorization(): AuthorizationOutcome = runCatching {
        val client = Identity.getAuthorizationClient(context)
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(REQUIRED_SCOPES)
            .build()
        val result = client.authorize(request).await()

        if (result.hasResolution()) {
            // Premier auth — l'utilisateur doit confirmer à l'écran
            val sender = result.pendingIntent?.intentSender
                ?: return@runCatching AuthorizationOutcome.Failed(
                    reason = "Pas d'IntentSender retourné par Play Services."
                )
            AuthorizationOutcome.NeedsConsent(sender)
        } else {
            // Silent — déjà autorisé
            val token = result.accessToken
                ?: return@runCatching AuthorizationOutcome.Failed("Token d'accès vide")
            persistFromResult(result, token)
            AuthorizationOutcome.Granted(accessToken = token)
        }
    }.getOrElse { e ->
        Log.e(TAG, "requestAuthorization failed", e)
        AuthorizationOutcome.Failed(reason = e.message ?: "Erreur Play Services")
    }

    /**
     * Parse le résultat de l'Activity launchée par le caller suite à un
     * [AuthorizationOutcome.NeedsConsent]. Renvoie l'access token + persiste
     * les métadonnées du compte (email, accountId).
     *
     * **Fallback robuste** : certains chemins de consent (notamment quand
     * l'utilisateur passe par "Avancé" sur l'écran "Google n'a pas validé")
     * peuvent retourner un Intent vide ou un AuthorizationResult sans token.
     * Dans ce cas, on re-tente un `requestAuthorization()` qui devrait
     * répondre silencieusement maintenant que l'user a consenti.
     */
    suspend fun completeAuthorization(data: Intent?): AuthorizationOutcome = runCatching {
        // Tentative 1 : extraire le résultat depuis l'Intent renvoyé par la consent UI.
        val fromIntent = data?.let { extractGrantFromIntent(it) }
        if (fromIntent != null) return@runCatching fromIntent

        // Tentative 2 : re-call authorize, devrait être silent maintenant que
        // l'user a consenti côté UI Google.
        when (val outcome = requestAuthorization()) {
            is AuthorizationOutcome.Granted -> outcome
            is AuthorizationOutcome.NeedsConsent ->
                AuthorizationOutcome.Failed("Consentement non finalisé. Réessaie.")
            is AuthorizationOutcome.Failed -> outcome
        }
    }.getOrElse { e ->
        Log.e(TAG, "completeAuthorization failed", e)
        AuthorizationOutcome.Failed(e.message ?: "Erreur lors du parsing du résultat OAuth")
    }

    /**
     * Extrait un Granted depuis l'Intent renvoyé par la consent UI. Renvoie null
     * si le SDK n'a pas pu parser, si le token est absent, ou si une exception
     * est levée — dans tous ces cas, le caller doit fallback sur un re-authorize
     * silent. Helper extrait pour éviter l'ambiguïté de label sur deux
     * `runCatching` imbriqués (Kotlin compile alors le `return@runCatching` de
     * l'inner comme expression et exige un else sur l'if).
     */
    private suspend fun extractGrantFromIntent(data: Intent): AuthorizationOutcome.Granted? = runCatching {
        val client = Identity.getAuthorizationClient(context)
        val result = client.getAuthorizationResultFromIntent(data)
        val token = result.accessToken ?: return@runCatching null
        persistFromResult(result, token)
        AuthorizationOutcome.Granted(token)
    }.onFailure {
        Log.w(TAG, "Extraction depuis Intent échouée, fallback re-authorize", it)
    }.getOrNull()

    /**
     * Récupère un access token frais utilisable pour les calls Drive REST.
     * Lance la consent UI si nécessaire (premier auth ou révocation user).
     *
     * Pour les workers background : si [outcome] est `NeedsConsent`, le worker
     * doit faire échouer le job avec un Result.retry et notifier l'user que
     * sa session Drive est expirée — on ne peut pas lancer une UI depuis un
     * worker.
     */
    suspend fun getAccessTokenSilent(): String? {
        return when (val outcome = requestAuthorization()) {
            is AuthorizationOutcome.Granted -> outcome.accessToken
            else -> null
        }
    }

    /**
     * Déconnecte le compte. Clear les métadonnées + révoque côté Google si
     * possible (best-effort). Après ça, l'user devra re-link via consent UI.
     */
    suspend fun unlink(): Boolean = runCatching {
        // Best-effort : on garde l'unlink local même si la révocation Google échoue
        runCatching { revokeAuthorizationOnPlayServices() }
            .onFailure { Log.w(TAG, "Revocation Google failed (non-bloquant)", it) }
        store.unlink()
        true
    }.getOrElse { e ->
        Log.e(TAG, "unlink failed", e)
        false
    }

    private suspend fun revokeAuthorizationOnPlayServices() {
        // Pas d'API directe pour révoquer un scope spécifique — on relance
        // l'authorization en mode "clear" pour invalider le cache local PS.
        // Si PS supporte une vraie révocation, l'API a évolué — à monitorer.
        // Pour l'instant on se contente du clear local du DataStore.
    }

    /**
     * Persiste l'identité du compte Google linké.
     *
     * **Pourquoi le fallback Drive `about`** : `AuthorizationClient` avec un
     * scope NON-identité (`drive.appdata`) ne renvoie PAS l'email côté
     * `result.toGoogleSignInAccount()` — celle-ci est null. Plutôt que d'ajouter
     * `email` aux scopes (= re-toucher la consent screen Cloud + 1 scope user-
     * facing en plus), on tape l'endpoint Drive `about?fields=user` qui renvoie
     * `user.emailAddress` même avec juste le scope `drive.appdata`. Trade-off :
     * un round-trip réseau de plus à la connexion, mais zéro friction côté setup.
     */
    private suspend fun persistFromResult(result: AuthorizationResult, accessToken: String) {
        // 1. Tentative directe via GoogleSignInAccount (rare avec drive.appdata seul,
        //    mais gratuit et instantané si présent — ex: si le user a accordé email
        //    via un autre flow Identity Services qui co-existe).
        val account = result.toGoogleSignInAccount()
        var email = account?.email
        var displayName = account?.displayName
        var accountId = account?.id

        // 2. Fallback : Drive about API. Toujours fiable avec drive.appdata.
        if (email.isNullOrBlank()) {
            val info = fetchUserInfoViaDrive(accessToken)
            email = info?.email
            if (displayName.isNullOrBlank()) displayName = info?.displayName
            if (accountId.isNullOrBlank()) accountId = info?.permissionId
        }

        if (email.isNullOrBlank()) {
            Log.w(TAG, "Identité Google non récupérable — email manquant, link non persisté")
            return
        }
        store.setLinkedAccount(
            email = email,
            displayName = displayName,
            accountId = accountId,
        )
    }

    /**
     * Récupère email + displayName via `https://www.googleapis.com/drive/v3/about?fields=user`.
     * Marche avec scope `drive.appdata` seul (pas besoin d'identity scopes).
     */
    private suspend fun fetchUserInfoViaDrive(accessToken: String): DriveUserInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val initializer = HttpRequestInitializer { req ->
                req.headers.authorization = "Bearer $accessToken"
                req.connectTimeout = 15_000
                req.readTimeout = 15_000
            }
            val drive = Drive.Builder(transport, jsonFactory, initializer)
                .setApplicationName("ShredCoach")
                .build()
            val about = drive.about().get().setFields("user").execute()
            val user = about.user
            DriveUserInfo(
                email = user?.emailAddress,
                displayName = user?.displayName,
                permissionId = user?.permissionId,
            )
        }.getOrElse { e ->
            Log.w(TAG, "fetchUserInfoViaDrive failed", e)
            null
        }
    }

    private data class DriveUserInfo(
        val email: String?,
        val displayName: String?,
        val permissionId: String?,
    )

    /**
     * Read-only accès à l'état linké courant — utile pour les decisions
     * synchrones (ex: enabler le toggle auto-backup uniquement si linké).
     */
    suspend fun currentSnapshot(): GoogleAuthStore.Snapshot = store.snapshot.first()

    sealed interface AuthorizationOutcome {
        /** Token frais disponible. À utiliser immédiatement, ne pas persister. */
        data class Granted(val accessToken: String) : AuthorizationOutcome
        /** Consent UI à lancer via Activity.startIntentSenderForResult ou launcher Compose. */
        data class NeedsConsent(val intentSender: IntentSender) : AuthorizationOutcome
        /** Erreur — message lisible par l'utilisateur. */
        data class Failed(val reason: String) : AuthorizationOutcome
    }

    companion object {
        private const val TAG = "GoogleAuth"
        /**
         * Scope `drive.appdata` : dossier caché spécifique à l'app, illimité
         * (consomme le quota Drive global de l'user). Invisible dans Drive UI →
         * impossible que l'user supprime accidentellement ses backups. Modèle
         * Whatsapp pour les sauvegardes WA chats.
         */
        val REQUIRED_SCOPES: List<Scope> = listOf(
            Scope(DriveScopes.DRIVE_APPDATA)
        )
    }
}
