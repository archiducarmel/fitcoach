package com.shredcoach.app.presentation.common

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus singleton de transfert d'un `ACTION_SEND` (image partagée depuis une
 * autre app) vers le destinataire fonctionnel dans ShredCoach.
 *
 * **Flow** :
 *  1. User screenshot la courbe CGM dans LibreLink / Dexcom / xDrip.
 *  2. Tap "Partager" dans le toolbar screenshot Android.
 *  3. System sheet propose plusieurs cibles ShredCoach (Glycémie, Repas, …),
 *     grâce aux `<activity-alias>` du manifest.
 *  4. User pique la cible → MainActivity démarre (ou reprend) avec l'intent.
 *  5. `MainActivity.handleIncomingShare()` lit `intent.component?.className`
 *     pour identifier la cible et `EXTRA_STREAM` pour récupérer l'Uri.
 *  6. [set] dépose le couple (cible, Uri) ici.
 *  7. `ShredCoachNavigation` observe ce StateFlow et navigue vers le bon écran.
 *  8. Le ViewModel de l'écran observe à son tour, consomme l'Uri et déclenche
 *     l'analyse — puis appelle [consume] pour vider le bus (sinon replay à
 *     la rotation / config change).
 *
 * **Pourquoi un singleton + StateFlow plutôt qu'un nav arg** :
 *  - Uri sérialisée dans une route nav cassait sur URIs longues / encodage
 *    spécial. Le singleton garde l'Uri en mémoire propre.
 *  - L'Activity peut recevoir l'intent AVANT que la nav graph soit prête
 *    (cold-start avec splash). Le StateFlow attend tranquillement que
 *    quelqu'un l'observe.
 *  - Pas de fuite : on consomme dès traitement → null après.
 *
 * **Permission Uri** : ShredCoach reçoit l'Uri avec `FLAG_GRANT_READ_URI_PERMISSION`
 * implicite via Intent.ACTION_SEND. La permission est valide tant que
 * l'Activity qui a reçu l'intent vit. Comme on consomme dans la foulée
 * (LaunchedEffect dès navigation), pas besoin de
 * `takePersistableUriPermission`.
 */
object IncomingShareIntent {

    /** Destination fonctionnelle de l'image partagée. */
    enum class Target { GLUCOSE, MEAL }

    data class Pending(val target: Target, val uri: Uri)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** Appelé par MainActivity sur ACTION_SEND. */
    fun set(target: Target, uri: Uri) {
        _pending.value = Pending(target, uri)
    }

    /** Appelé par le ViewModel quand l'image a été consommée. */
    fun consume() {
        _pending.value = null
    }
}
