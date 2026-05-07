package com.shredcoach.app.domain.voice

import android.content.Context

/**
 * Abstraction d'un moteur de synthèse vocale.
 *
 * Toutes les méthodes sont **fire-and-forget** : `speak()` ne bloque pas
 * et n'expose aucune complétion. Pourquoi : les callers actuels appellent
 * `speak()` depuis des `LaunchedEffect` Compose et n'attendent pas la fin
 * de la diction. Garder la même API que [ShreddyVoice] pré-refactor permet
 * un swap sans toucher aux écrans.
 *
 * **Lifecycle** : un engine est singleton (Hilt). [init] est appelé une
 * fois au démarrage de l'app par [ShreddyVoice]. [shutdown] libère les
 * ressources natives (TextToSpeech, MediaPlayer).
 *
 * **Concurrency** : un nouvel appel à [speak] doit interrompre le précédent
 * (équivalent QUEUE_FLUSH). Garantit que le countdown 5/4/3/2/1 ne se
 * superpose pas si un play met du temps à démarrer.
 */
interface VoiceEngine {

    val id: VoiceEngineId

    /** Vrai après initialisation réussie ; sinon [speak] no-op. */
    val isReady: Boolean

    fun init(context: Context)

    /**
     * Synthétise et joue [text] avec la voix [persona].
     *
     * Si la persona ne correspond pas à [id], le moteur applique son défaut
     * (sécurité : on ne crashe pas, on ne refuse pas).
     */
    fun speak(text: String, persona: Persona)

    /** Arrête la diction en cours, sans libérer les ressources. */
    fun stop()

    /** Libère définitivement les ressources natives. */
    fun shutdown()
}
