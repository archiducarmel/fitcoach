package com.shredcoach.app.domain.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation [VoiceEngine] qui synthétise via l'API Google Cloud
 * Text-to-Speech v1, voix **Chirp 3 HD** (modèle neuronal studio-grade).
 *
 * **Pipeline** :
 *  1. Lookup cache disque par hash(personaId + speakingRate + text)
 *  2. Si miss → POST `text:synthesize` → Base64 → fichier MP3 en cacheDir
 *  3. MediaPlayer joue le fichier (toujours stop-then-start le précédent)
 *
 * **Fallback Android** : si la clé API est absente, si le réseau échoue,
 * si Google répond 4xx/5xx, ou si MediaPlayer crashe, on délègue à
 * [AndroidTtsEngine.speak] (voix gratuite, hors-ligne) afin que l'utilisateur
 * ne se retrouve JAMAIS avec un coach silencieux. La fallback persona reprend
 * la même `displayName` côté Android registry quand elle existe (ex: Marcus
 * Chirp → Marcus Android), sinon le défaut Android.
 *
 * **Cache** :
 *  - Clé : `google_tts_<sha256(personaId|rate|text)>.mp3`
 *  - Stockage : `cacheDir/voice/` (auto-purgé par Android sous pression mémoire)
 *  - Stratégie : pas de TTL ni d'éviction explicite — les clés évoluant avec
 *    le texte, l'usage typique (countdowns + phrases du phrasebook) plafonne
 *    à ~200 fichiers ~10ko chacun = ~2Mo. Acceptable. Si futur dérapage,
 *    ajouter une LRU sur `cacheDir/voice/`.
 *
 * **Coût économique** : Chirp 3 HD = $0.000016/char (free tier 1M chars/mo
 * sur Studio voices, mais Chirp est tarifé à part — vérifier doc à jour).
 * Le cache amortit > 95% des appels après une session.
 */
@Singleton
class GoogleCloudTtsEngine @Inject constructor(
    private val secureKeyStore: SecureKeyStore,
    @NetworkModule.BaseHttpClient private val baseHttpClient: OkHttpClient,
    private val androidFallback: AndroidTtsEngine,
) : VoiceEngine {

    override val id: VoiceEngineId = VoiceEngineId.GOOGLE_CHIRP3

    @Volatile private var ready: Boolean = false
    @Volatile private var cacheDir: File? = null
    @Volatile private var currentPlayer: MediaPlayer? = null

    private val playerMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        // Read timeout 30s — la synthèse de phrases courtes (1-3s d'audio)
        // répond généralement en <2s ; on garde une marge réseau confortable.
        baseHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override val isReady: Boolean
        get() = ready && secureKeyStore.hasKey(SecureKeyStore.Provider.GOOGLE_TTS)

    override fun init(context: Context) {
        if (cacheDir != null) return
        val dir = File(context.applicationContext.cacheDir, "voice")
        dir.mkdirs()
        cacheDir = dir
        ready = true
    }

    override fun speak(text: String, persona: Persona) {
        if (!ready) {
            androidFallback.speak(text, mapToAndroidPersona(persona))
            return
        }
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return

        scope.launch {
            try {
                val file = getOrSynthesize(cleaned, persona)
                if (file != null) {
                    play(file)
                } else {
                    androidFallback.speak(text, mapToAndroidPersona(persona))
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Synthèse Chirp échouée — fallback Android", e)
                androidFallback.speak(text, mapToAndroidPersona(persona))
            }
        }
    }

    override fun stop() {
        // On force-release sans attendre le lock pour ne pas bloquer le caller.
        currentPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        currentPlayer = null
    }

    override fun shutdown() {
        stop()
        scope.cancel()
        ready = false
    }

    // ─────────────────────────────────────────────────────────────────────
    // Implementation
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun getOrSynthesize(text: String, persona: Persona): File? {
        val dir = cacheDir ?: return null
        val cacheFile = File(dir, cacheKey(persona, text))
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        val apiKey = secureKeyStore.getKey(SecureKeyStore.Provider.GOOGLE_TTS)
        if (apiKey.isBlank()) return null

        val mp3Bytes = synthesize(text, persona, apiKey) ?: return null

        // Écriture atomique : tmp puis rename pour éviter qu'un crash
        // produise un fichier tronqué qui passerait le check `length() > 0`.
        val tmp = File(dir, "${cacheFile.name}.tmp")
        tmp.outputStream().use { it.write(mp3Bytes) }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
        return cacheFile
    }

    private suspend fun synthesize(text: String, persona: Persona, apiKey: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("input", JSONObject().put("text", text))
                put(
                    "voice",
                    JSONObject()
                        .put("languageCode", "fr-FR")
                        .put("name", persona.engineVoiceId),
                )
                // Chirp 3 HD : pitch NON supporté, ne pas l'envoyer (sinon 400).
                // speakingRate (0.25–4.0) supporté.
                put(
                    "audioConfig",
                    JSONObject()
                        .put("audioEncoding", "MP3")
                        .put("speakingRate", persona.speakingRate.toDouble()),
                )
            }.toString()

            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(300)
                    Log.w(TAG, "TTS API ${response.code}: $errorBody")
                    return@withContext null
                }
                val bodyStr = response.body?.string() ?: return@withContext null
                val audioContent = JSONObject(bodyStr).optString("audioContent", "")
                if (audioContent.isBlank()) return@withContext null
                Base64.decode(audioContent, Base64.DEFAULT)
            }
        }

    private suspend fun play(file: File) {
        playerMutex.withLock {
            // Stop + release l'éventuel player précédent — équivalent QUEUE_FLUSH.
            currentPlayer?.let { previous ->
                runCatching { previous.stop() }
                runCatching { previous.release() }
            }
            val player = MediaPlayer()
            currentPlayer = player
            try {
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (currentPlayer === mp) currentPlayer = null
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    runCatching { mp.release() }
                    if (currentPlayer === mp) currentPlayer = null
                    true
                }
                player.prepare() // synchrone, fichier local court → OK
                player.start()
            } catch (e: Throwable) {
                Log.w(TAG, "MediaPlayer setup failed", e)
                runCatching { player.release() }
                currentPlayer = null
                throw e
            }
        }
    }

    private fun cacheKey(persona: Persona, text: String): String {
        val payload = "${persona.id}|${persona.speakingRate}|$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "google_tts_$hex.mp3"
    }

    /**
     * Mappe une persona Chirp vers son équivalent Android en réutilisant
     * le `displayName` (Marcus → Marcus). Garantit une expérience cohérente
     * en mode dégradé.
     */
    private fun mapToAndroidPersona(persona: Persona): Persona {
        if (persona.engine == VoiceEngineId.ANDROID) return persona
        return VoicePersonaRegistry.androidPersonae
            .firstOrNull { it.displayName == persona.displayName && it.gender == persona.gender }
            ?: VoicePersonaRegistry.defaultPersonaFor(VoiceEngineId.ANDROID)
    }

    private companion object {
        const val TAG = "GoogleCloudTtsEngine"
        const val ENDPOINT = "https://texttospeech.googleapis.com/v1/text:synthesize"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
