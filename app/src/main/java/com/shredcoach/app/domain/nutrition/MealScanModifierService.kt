package com.shredcoach.app.domain.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.GeminiMealService
import com.shredcoach.app.data.remote.MealAnalysisResult
import com.shredcoach.app.data.repository.UserRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service orchestrant les modificateurs de portion d'un scan repas :
 *  - **× reprises** : applique un multiplicateur (servingMultiplier).
 *  - **Restes** : déclenche un OCR Gemini sur la photo des restes, persiste
 *    les macros à déduire, copie le fichier image dans `meal_scans/`.
 *  - **Reset restes** : supprime la photo de restes + remet les champs à zéro.
 *
 * Toute la persistance passe par [MealScanDao]. Les agrégations quotidiennes
 * (NutritionDao.getDayTotals) appliquent automatiquement les modificateurs
 * via JOIN — **aucune** mise à jour de meal_logs n'est nécessaire ici.
 *
 * **Erreurs** : retourne [Result] pour permettre au caller (ViewModel) de
 * snackbar/dialog proprement. La couche service n'avale jamais silencieusement
 * un échec OCR — l'UX doit savoir si la déduction n'a pas pu être calculée.
 */
@Singleton
class MealScanModifierService @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mealScanDao: MealScanDao,
    private val geminiService: GeminiMealService,
    private val userRepository: UserRepository,
) {
    private val gson = Gson()

    /**
     * Applique un multiplicateur de portion. Clampé dans [0.25, 10.0] côté
     * [MealScanModifierMath] avant write — la DAO fait confiance.
     *
     * Idempotent : appeler `setMultiplier(scanId, 2.0f)` deux fois donne le
     * même état final. Pas d'effet cumulatif involontaire.
     */
    suspend fun setMultiplier(scanId: Long, multiplier: Float) {
        val clamped = MealScanModifierMath.clampMultiplier(multiplier)
        mealScanDao.updateServingMultiplier(scanId, clamped)
    }

    /**
     * Lance l'OCR Gemini sur la photo des restes et persiste les macros à
     * déduire. La photo est copiée dans `meal_scans/` (même répertoire que
     * les scans de repas — backup/purge déjà câblés sur ce dossier).
     *
     * **Important** : on n'appelle pas analyzeMeal avec un hintBlock dédié
     * "restes" pour v1 — le prompt standard détecte aussi bien les portions
     * partielles. Si la précision est insuffisante, un hint "ces aliments
     * sont les RESTES d'un plat précédent, estime ce qui reste" sera ajouté
     * en v2.
     *
     * **Échec OCR** : si le LLM renvoie une erreur (image non alimentaire,
     * parsing JSON KO, etc.), on retourne [Result.failure] sans toucher au
     * scan. L'utilisateur garde la possibilité de retenter ou de saisir
     * manuellement (V2).
     */
    suspend fun scanAndApplyLeftover(
        scanId: Long,
        bitmap: Bitmap,
    ): Result<MealAnalysisResult> = withContext(Dispatchers.IO) {
        val scan = mealScanDao.getScanById(scanId)
            ?: return@withContext Result.failure(IllegalArgumentException("Scan introuvable"))

        // 1. Récupérer provider + clé API (même logique que MealScannerViewModel)
        val profile = userRepository.getUserProfileOnce()
        val provider = profile?.mealScanProvider ?: "GEMINI"
        val apiKey = when (provider) {
            "GROQ" -> userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL)
            "MISTRAL" -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
            else -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
        }
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Clé API $provider absente"))
        }
        val model = profile?.geminiModel ?: "gemini-2.5-flash"

        // 2. Compresser + appeler le LLM
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val analysis = geminiService.analyzeMeal(
            imageBytes = bytes,
            mimeType = "image/jpeg",
            apiKey = apiKey,
            model = model,
            provider = provider,
            hintBlock = LEFTOVER_HINT,
        ).getOrElse { return@withContext Result.failure(it) }

        // 3. Sauver la photo des restes dans le même répertoire que les scans
        //    de repas (déjà câblé sur backup + RGPD purge — pas de nouveau
        //    dossier à enregistrer ailleurs).
        val leftoverPath = runCatching {
            val dir = File(appContext.filesDir, "meal_scans")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "leftover_${scanId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
            file.absolutePath
        }.onFailure { Log.w(TAG, "Échec persist photo restes — déduction quand même sauvée", it) }
            .getOrNull()

        // 4. Si une photo de restes précédente existe, la supprimer
        scan.leftoverPhotoPath?.takeIf { it != leftoverPath }?.let { oldPath ->
            runCatching { File(oldPath).delete() }
                .onFailure { Log.w(TAG, "Suppression ancienne photo restes échouée (non bloquant)", it) }
        }

        // 5. Persister les macros à déduire
        mealScanDao.updateLeftover(
            id = scanId,
            photoPath = leftoverPath,
            calories = analysis.totalCalories.coerceAtLeast(0),
            proteins = analysis.totalProteins.coerceAtLeast(0.0),
            carbs = analysis.totalCarbs.coerceAtLeast(0.0),
            fats = analysis.totalFats.coerceAtLeast(0.0),
            fibers = analysis.totalFibers.coerceAtLeast(0.0),
            weight = analysis.totalWeight.coerceAtLeast(0),
            resultJson = gson.toJson(analysis),
            scannedAt = LocalDateTime.now(),
        )
        Result.success(analysis)
    }

    /**
     * Reset le scan de restes (l'utilisateur change d'avis). Supprime la
     * photo sur disque (best-effort) et remet les champs leftover* à zéro.
     */
    suspend fun clearLeftover(scanId: Long) {
        val scan = mealScanDao.getScanById(scanId) ?: return
        scan.leftoverPhotoPath?.let { path ->
            runCatching { File(path).delete() }
                .onFailure { Log.w(TAG, "Suppression photo restes échouée (non bloquant)", it) }
        }
        mealScanDao.clearLeftover(scanId)
    }

    private companion object {
        const val TAG = "MealScanModifierService"

        /**
         * Indice injecté dans le prompt OCR pour orienter le LLM : on lui dit
         * explicitement qu'il analyse des RESTES, pas un plat complet. Évite
         * que Gemini hallucine "tu vois 200g de riz" alors qu'il en voit
         * 60g (parce qu'il a une "image mentale" du plat complet typique).
         */
        const val LEFTOVER_HINT = """

═══ CONTEXTE : RESTES D'UN PLAT DÉJÀ ENTAMÉ ═══
- Cette photo montre les RESTES d'un repas que l'utilisateur n'a pas terminé.
- N'estime PAS la portion complète originale : estime UNIQUEMENT ce qui est
  visible sur la photo (ce qui reste, donc ce qui sera JETÉ ou STOCKÉ).
- Si l'assiette est presque vide, c'est normal — renvoie des macros très
  faibles voire 0 sur certains aliments absents.
- Les valeurs renvoyées seront DÉDUITES du repas initial — sur-estimer fait
  sous-évaluer l'apport calorique réel de l'utilisateur (et inversement).
"""
    }
}
