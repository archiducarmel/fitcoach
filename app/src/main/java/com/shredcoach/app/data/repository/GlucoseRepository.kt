package com.shredcoach.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.shredcoach.app.data.local.dao.GlucoseDao
import com.shredcoach.app.data.local.entity.GlucoseLogEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.GlucoseOcrService
import com.shredcoach.app.domain.llm.AiAssistant
import com.shredcoach.app.domain.llm.AssistantLlmResolver
import com.shredcoach.app.data.remote.GlucoseParseResult
import com.shredcoach.app.domain.glucose.GlucoseAnalyzer
import com.shredcoach.app.domain.glucose.GlucoseDaySummary
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.glucose.GlucoseWindowSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth pour les entrées glycémiques. Wraps :
 *  - [GlucoseDao] (Room)
 *  - [GlucoseOcrService] (Gemini Vision)
 *  - [UserRepository] (clé API + provider OCR)
 *
 * **Pattern d'upload** : save image dans `filesDir/glucose/`, call OCR, insert
 * GlucoseLogEntity. Si l'OCR échoue partiellement, on insère quand même avec
 * les champs nullables — l'user pourra corriger plus tard.
 *
 * **Idempotent** : un même `date` écrase la row précédente (REPLACE strategy
 * via UNIQUE index). C'est intentionnel : si l'user upload 2x dans la journée,
 * on garde la dernière (sans doublon parasite).
 */
@Singleton
class GlucoseRepository @Inject constructor(
    private val glucoseDao: GlucoseDao,
    private val glucoseOcrService: GlucoseOcrService,
    private val userRepository: UserRepository,
    private val llmResolver: AssistantLlmResolver,
    @ApplicationContext private val appContext: Context,
) {

    fun observeForDate(date: LocalDate): Flow<GlucoseLogEntity?> =
        glucoseDao.observeForDate(date)

    suspend fun getForDate(date: LocalDate): GlucoseLogEntity? =
        glucoseDao.getForDateOnce(date)

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<GlucoseLogEntity>> =
        glucoseDao.observeRange(from, to)

    suspend fun getRange(from: LocalDate, to: LocalDate): List<GlucoseLogEntity> =
        glucoseDao.getRangeOnce(from, to)

    suspend fun getRecent(limit: Int = 30): List<GlucoseLogEntity> =
        glucoseDao.getRecentOnce(limit)

    suspend fun getMostRecentBefore(before: LocalDate): GlucoseLogEntity? =
        glucoseDao.getMostRecentBefore(before)

    /**
     * Upload un screenshot CGM pour [date] : sauvegarde le bitmap sur disque,
     * lance l'OCR Gemini, insère/remplace l'entrée DB.
     *
     * **Retour** : l'entrée DB finale (avec champs OCR ou null si erreur),
     * ou Result.failure si l'OCR a complètement échoué (clé absente, réseau).
     *
     * **Robustesse** : si l'OCR partiellement échoue (errorReason non null
     * mais avec quelques champs parsés), on insère quand même la row avec
     * imagePath + champs présents. L'user verra "Corriger manuellement" en UI.
     */
    suspend fun uploadScreenshot(
        bitmap: Bitmap,
        date: LocalDate = LocalDate.now(),
    ): Result<GlucoseLogEntity> = withContext(Dispatchers.IO) {
        // 1. Persist le bitmap dans filesDir/glucose/
        val imagePath = saveBitmap(bitmap, date)

        // 2. Récupère config LLM via le resolver (back-compat : fallback sur
        //    profile.mealScanProvider + profile.geminiModel si aucun override).
        val profile = userRepository.getUserProfileOnce()
            ?: return@withContext Result.failure(IllegalStateException("Profil utilisateur absent"))
        val llmConfig = llmResolver.resolveWithProfile(AiAssistant.GLUCOSE_OCR, profile)
        val provider = llmConfig.provider.name
        val apiKey = when (llmConfig.provider) {
            com.shredcoach.app.data.remote.LlmProvider.GROQ -> userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL)
            com.shredcoach.app.data.remote.LlmProvider.MISTRAL -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
            else -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
        }
        if (apiKey.isBlank()) {
            // On insère malgré tout pour ne pas perdre le screenshot : l'user
            // ajoutera sa clé ou corrigera à la main.
            val placeholder = GlucoseLogEntity(
                date = date, imagePath = imagePath, parseConfidence = 0f,
                parsedAt = LocalDateTime.now(),
                notes = "OCR non effectué (clé API absente)",
            )
            glucoseDao.upsert(placeholder)
            return@withContext Result.failure(IllegalStateException("Clé API ${provider} absente — screenshot conservé, à compléter manuellement"))
        }

        // 3. OCR Vision LLM (provider configurable via Settings)
        val bytes = bitmapToJpegBytes(bitmap)
        val parseResult = glucoseOcrService.parseScreenshot(
            imageBytes = bytes, mimeType = "image/jpeg",
            apiKey = apiKey, model = llmConfig.modelId, provider = provider,
        )

        val parse: GlucoseParseResult = parseResult.getOrElse {
            // Réseau ou erreur fatale : on persiste avec imagePath seul.
            val placeholder = GlucoseLogEntity(
                date = date, imagePath = imagePath, parseConfidence = 0f,
                parsedAt = LocalDateTime.now(),
                notes = "OCR échoué : ${it.message?.take(80)}",
            )
            glucoseDao.upsert(placeholder)
            return@withContext Result.failure(it)
        }

        // 4. Compose l'entité finale
        val entity = parse.toEntity(date = date, imagePath = imagePath)
        glucoseDao.upsert(entity)
        // 5. Re-read pour récupérer l'id assigné
        val saved = glucoseDao.getForDateOnce(date) ?: entity
        Result.success(saved)
    }

    /**
     * Override manuel des champs OCR — `manualOverride = true` pour tracer
     * que la valeur ne vient plus de l'OCR. Conserve les autres champs
     * existants si on ne les passe pas.
     */
    suspend fun manualOverride(
        date: LocalDate,
        avgMgdl: Double? = null,
        peakMgdl: Double? = null,
        peakTime: LocalTime? = null,
        minMgdl: Double? = null,
        minTime: LocalTime? = null,
        timeInRangePct: Int? = null,
        hypoCount: Int? = null,
        cv: Double? = null,
        notes: String? = null,
    ): Result<GlucoseLogEntity> = withContext(Dispatchers.IO) {
        val existing = glucoseDao.getForDateOnce(date)
            ?: GlucoseLogEntity(date = date)
        val updated = existing.copy(
            avgMgdl = avgMgdl ?: existing.avgMgdl,
            peakMgdl = peakMgdl ?: existing.peakMgdl,
            peakTime = peakTime ?: existing.peakTime,
            minMgdl = minMgdl ?: existing.minMgdl,
            minTime = minTime ?: existing.minTime,
            timeInRangePct = timeInRangePct ?: existing.timeInRangePct,
            hypoCount = hypoCount ?: existing.hypoCount,
            cv = cv ?: existing.cv,
            notes = notes ?: existing.notes,
            manualOverride = true,
        )
        glucoseDao.upsert(updated)
        val saved = glucoseDao.getForDateOnce(date) ?: updated
        Result.success(saved)
    }

    suspend fun deleteForDate(date: LocalDate) {
        // Supprime le fichier image si présent
        getForDate(date)?.imagePath?.let {
            runCatching { File(it).delete() }
        }
        glucoseDao.deleteForDate(date)
    }

    // ─── Summaries consolidées (consommées par UI et IA contexts) ───

    /**
     * Snapshot du jour pour [date]. Retourne un summary "vide" (null partout)
     * si pas de log — utile pour les UI qui veulent afficher "Pas encore de
     * data, upload ton screenshot".
     */
    suspend fun getDaySummary(date: LocalDate): GlucoseDaySummary? {
        val log = glucoseDao.getForDateOnce(date) ?: return null
        return GlucoseDaySummary(
            date = log.date,
            avgMgdl = log.avgMgdl,
            peakMgdl = log.peakMgdl,
            peakTime = log.peakTime,
            minMgdl = log.minMgdl,
            minTime = log.minTime,
            timeInRangePct = log.timeInRangePct,
            hypoCount = log.hypoCount,
            cv = log.cv,
            parseConfidence = log.parseConfidence,
            manualOverride = log.manualOverride,
            imagePath = log.imagePath,
            notes = log.notes,
        )
    }

    /**
     * Agrégat sur les [days] derniers jours (today inclus). Toujours non-null
     * mais ses champs peuvent l'être si insuffisamment de data.
     */
    suspend fun getWindowSummary(today: LocalDate, days: Int): GlucoseWindowSummary {
        val from = today.minusDays((days - 1).toLong())
        val logs = glucoseDao.getRangeOnce(from, today)
        return GlucoseWindowSummary(
            daysCovered = GlucoseAnalyzer.countWithData(logs),
            avgMgdl = GlucoseAnalyzer.avgMgdl(logs),
            avgTirPct = GlucoseAnalyzer.avgTir(logs),
            avgCv = GlucoseAnalyzer.avgCv(logs),
            totalHypo = GlucoseAnalyzer.totalHypo(logs),
            trendMgdlPerWeek = GlucoseAnalyzer.trendMgdlPerWeek(logs),
            pattern = GlucoseAnalyzer.detectPattern(logs),
        )
    }

    /**
     * Pattern dominant sur 30j. Source unique de vérité pour notif builders,
     * UI badges, et IA contexts. Si <7j de data, retourne INSUFFICIENT_DATA.
     */
    suspend fun getDominantPattern(today: LocalDate = LocalDate.now()): GlucosePattern {
        val from = today.minusDays(29)
        val logs = glucoseDao.getRangeOnce(from, today)
        return GlucoseAnalyzer.detectPattern(logs)
    }

    // ─── Agrégats fenêtres ───────────────────────────────────────

    suspend fun getAvgMgdl(from: LocalDate, to: LocalDate): Double? =
        glucoseDao.getAvgMgdlOnRange(from, to)

    suspend fun getAvgTir(from: LocalDate, to: LocalDate): Double? =
        glucoseDao.getAvgTirOnRange(from, to)

    suspend fun getAvgCv(from: LocalDate, to: LocalDate): Double? =
        glucoseDao.getAvgCvOnRange(from, to)

    suspend fun getCountOnRange(from: LocalDate, to: LocalDate): Int =
        glucoseDao.getCountOnRange(from, to)

    suspend fun getTotalHypoCount(from: LocalDate, to: LocalDate): Int =
        glucoseDao.getTotalHypoCountOnRange(from, to) ?: 0

    // ─── Helpers I/O ─────────────────────────────────────────────

    private fun saveBitmap(bitmap: Bitmap, date: LocalDate): String {
        val dir = File(appContext.filesDir, "glucose")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "scan_${date}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        return baos.toByteArray()
    }

    private fun GlucoseParseResult.toEntity(date: LocalDate, imagePath: String): GlucoseLogEntity {
        val peakLt = peakTime?.let { tryParseHm(it) }
        val minLt = minTime?.let { tryParseHm(it) }
        val noteParts = mutableListOf<String>()
        errorReason?.let { noteParts += "OCR: $it" }
        if (confidence < 0.7f && errorReason == null) noteParts += "Confiance OCR faible (${(confidence * 100).toInt()}%)"
        return GlucoseLogEntity(
            date = date,
            imagePath = imagePath,
            avgMgdl = avgMgdl,
            peakMgdl = peakMgdl,
            peakTime = peakLt,
            minMgdl = minMgdl,
            minTime = minLt,
            timeInRangePct = timeInRangePct,
            timeAboveRangePct = timeAboveRangePct,
            timeBelowRangePct = timeBelowRangePct,
            hypoCount = hypoCount,
            cv = cv,
            glucoseMgdlCurveJson = curve24hJson,
            parseConfidence = confidence,
            parsedAt = LocalDateTime.now(),
            manualOverride = false,
            notes = noteParts.joinToString(" · ").ifBlank { null },
        )
    }

    private fun tryParseHm(s: String): LocalTime? = try {
        // Accepte "HH:MM", "H:MM", "HH:MM:SS"
        LocalTime.parse(s.take(5))
    } catch (_: Exception) {
        Log.w("GlucoseRepository", "Invalid time format from OCR: $s")
        null
    }
}
