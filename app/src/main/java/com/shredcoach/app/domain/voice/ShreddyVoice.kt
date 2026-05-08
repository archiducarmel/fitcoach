package com.shredcoach.app.domain.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Façade publique de la voix de Shreddy.
 *
 * Avant le refactor multi-moteurs, [ShreddyVoice] embarquait directement le
 * `TextToSpeech` Android. Désormais elle délègue à un [VoiceEngine] choisi
 * dynamiquement via [VoiceSettingsStore]. Les callers existants
 * (WorkoutSessionScreen, WorkoutSessionService, ShredCoachApplication)
 * conservent l'API `init(context)` / `speak(text)` / `speakRestEnd()` /
 * `speakCountdown(int)` / `shutdown()` — **aucun changement requis côté
 * appelants**.
 *
 * **Swap réactif** : la persona ou le moteur changé dans Settings prend
 * effet à la **prochaine synthèse vocale** (countdown ou phrase). Pas de
 * restart d'app, pas de re-init manuel. Implémenté en collectant le
 * snapshot DataStore dans un scope IO long-vie et en mettant à jour
 * `currentEngine` / `currentPersona` (volatiles).
 *
 * **Pourquoi ne pas exposer `VoiceEngine` directement** : les callers
 * appellent `speakCountdown(3)` qui maps un int → "3" via le phrasebook
 * commun à TOUS les moteurs. Garder la logique de phrase ici évite de
 * dupliquer le mapping dans chaque engine.
 */
@Singleton
class ShreddyVoice @Inject constructor(
    private val voiceSettings: VoiceSettingsStore,
    private val androidEngine: AndroidTtsEngine,
    private val googleEngine: GoogleCloudTtsEngine,
) {
    private val restEndPhrasesFr = listOf(
        "C'est reparti !",
        "On enchaîne !",
        "Série suivante !",
        "Allez, on y retourne !",
        "Go, c'est à toi !",
        "Repos terminé, on repart !",
        "C'est le moment, donne tout !",
    )
    private val restEndPhrasesEn = listOf(
        "Let's go!",
        "Next set!",
        "Time to roll!",
        "Come on, back at it!",
        "Go, you got this!",
        "Rest's over, let's roll!",
        "This is your moment, give it all!",
    )
    private val restEndPhrasesEs = listOf(
        "¡Vamos!",
        "¡Siguiente serie!",
        "¡A darle!",
        "¡Vamos, otra vez!",
        "¡Tú puedes!",
        "¡Descanso terminado, seguimos!",
        "¡Es el momento, dale todo!",
    )
    private val restEndPhrasesIt = listOf(
        "Andiamo!",
        "Prossima serie!",
        "Si riparte!",
        "Forza, di nuovo!",
        "Vai, ce la fai!",
        "Recupero finito, riprendiamo!",
        "È il momento, dai tutto!",
    )
    private val restEndPhrasesPt = listOf(
        "Vamos!",
        "Próxima série!",
        "Bora lá!",
        "Vamos, mais uma!",
        "Você consegue!",
        "Descanso acabou, vamos!",
        "É a hora, dá tudo!",
    )
    private val restEndPhrasesDe = listOf(
        "Los geht's!",
        "Nächster Satz!",
        "Weiter so!",
        "Komm schon, weiter!",
        "Du schaffst das!",
        "Pause vorbei, weiter!",
        "Jetzt alles geben!",
    )

    private val countdownPhrasesFr = mapOf(
        10 to "10 secondes",
        5 to "5",
        4 to "4",
        3 to "3",
        2 to "2",
        1 to "1",
    )
    private val countdownPhrasesEn = mapOf(
        10 to "10 seconds",
        5 to "5",
        4 to "4",
        3 to "3",
        2 to "2",
        1 to "1",
    )
    private val countdownPhrasesEs = mapOf(
        10 to "10 segundos",
        5 to "5", 4 to "4", 3 to "3", 2 to "2", 1 to "1",
    )
    private val countdownPhrasesIt = mapOf(
        10 to "10 secondi",
        5 to "5", 4 to "4", 3 to "3", 2 to "2", 1 to "1",
    )
    private val countdownPhrasesPt = mapOf(
        10 to "10 segundos",
        5 to "5", 4 to "4", 3 to "3", 2 to "2", 1 to "1",
    )
    private val countdownPhrasesDe = mapOf(
        10 to "10 Sekunden",
        5 to "5", 4 to "4", 3 to "3", 2 to "2", 1 to "1",
    )

    /** Cascade par locale. Tous V2 ont leur propre liste. */
    private val restEndPhrases: List<String>
        get() {
            val pl = com.shredcoach.app.domain.i18n.PromptLocale
            return when {
                pl.isFr() -> restEndPhrasesFr
                pl.isEs() -> restEndPhrasesEs
                pl.isIt() -> restEndPhrasesIt
                pl.isPt() -> restEndPhrasesPt
                pl.isDe() -> restEndPhrasesDe
                else -> restEndPhrasesEn
            }
        }

    private val countdownPhrases: Map<Int, String>
        get() {
            val pl = com.shredcoach.app.domain.i18n.PromptLocale
            return when {
                pl.isFr() -> countdownPhrasesFr
                pl.isEs() -> countdownPhrasesEs
                pl.isIt() -> countdownPhrasesIt
                pl.isPt() -> countdownPhrasesPt
                pl.isDe() -> countdownPhrasesDe
                else -> countdownPhrasesEn
            }
        }

    private var phraseIndex = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    @Volatile
    private var currentEngineId: VoiceEngineId = VoiceEngineId.ANDROID

    @Volatile
    private var currentPersona: Persona =
        VoicePersonaRegistry.defaultPersonaFor(VoiceEngineId.ANDROID)

    /**
     * Initialise les deux moteurs et démarre l'observation des préférences.
     * Idempotent : appelable depuis [Application.onCreate] ET un Foreground
     * Service sans risque de double-init.
     */
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        androidEngine.init(context)
        googleEngine.init(context)

        scope.launch {
            voiceSettings.snapshot.distinctUntilChanged().collect { snap ->
                currentEngineId = snap.engineId
                currentPersona = VoicePersonaRegistry.findById(snap.personaId)
                    ?: VoicePersonaRegistry.defaultPersonaFor(snap.engineId)
            }
        }
    }

    /** Prononce un texte avec la persona courante via le moteur courant. */
    fun speak(text: String) {
        currentEngine().speak(text, currentPersona)
    }

    /** Annonce de fin de repos (phrase variée). */
    fun speakRestEnd() {
        val phrase = restEndPhrases[phraseIndex % restEndPhrases.size]
        phraseIndex++
        speak(phrase)
    }

    /** Countdown vocal (10, 5, 4, 3, 2, 1). */
    fun speakCountdown(secondsRemaining: Int) {
        countdownPhrases[secondsRemaining]?.let { speak(it) }
    }

    /** Libérer les ressources. */
    fun shutdown() {
        scope.cancel()
        androidEngine.shutdown()
        googleEngine.shutdown()
        initialized = false
    }

    private fun currentEngine(): VoiceEngine = when (currentEngineId) {
        VoiceEngineId.ANDROID -> androidEngine
        VoiceEngineId.GOOGLE_CHIRP3 -> googleEngine
    }
}
