package com.shredcoach.app.domain.notification

/**
 * Résultat de la décision d'un [NotificationBuilder] : envoyer (avec contenu)
 * ou skip silencieusement.
 *
 * **Pourquoi un sealed type plutôt que body: String?** : un null pourrait être
 * interprété comme "skip" OU "erreur" OU "pas de contexte". Un type fermé
 * rend l'intention explicite et le caller doit traiter les deux cas.
 *
 * **Channel & deeplink** sont portés ici (pas par le dispatcher) parce que
 * certaines notifs doivent pouvoir override leur channel par défaut selon
 * le contexte (ex: shaker post-workout = channel WORKOUT pas MEALS).
 */
sealed interface NotifDecision {
    data class Send(
        val title: String,
        val body: String,
        val channelId: String,
        val deeplink: String? = null,
    ) : NotifDecision

    /**
     * Skip silencieux — aucune notif envoyée à l'utilisateur. La raison est
     * tracée en debug uniquement (pas affichée).
     */
    data class Skip(val reason: String) : NotifDecision
}
