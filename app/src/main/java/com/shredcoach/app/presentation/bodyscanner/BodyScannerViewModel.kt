package com.shredcoach.app.presentation.bodyscanner


import androidx.compose.runtime.Immutable
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.shredcoach.app.R
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.remote.BodyAnalysisResult
import com.shredcoach.app.data.remote.BodyAnalysisService
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.local.dao.BodyScanLogDao
import com.shredcoach.app.data.local.entity.BodyScanLogEntity
import com.shredcoach.app.domain.bodymesh.BodyInsightGenerator
import com.shredcoach.app.domain.bodymesh.BodyMeshExtractor
import com.shredcoach.app.domain.bodymesh.MeshFeatures
import com.shredcoach.app.domain.llm.AiAssistant
import com.shredcoach.app.domain.llm.AssistantLlmResolver
import com.shredcoach.app.domain.locale.withCurrentLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * State du Body Scanner.
 *
 * Phases visuelles :
 *  1. imageBitmap == null → empty state (zone capture)
 *  2. isAnalyzing == true → scan overlay animé
 *  3. result != null → affichage résultat éditable
 *  4. meshBitmap != null → cta vers BodyMeshScreen
 */
@Immutable
data class BodyScannerState(
    val imageBitmap: Bitmap? = null,
    val isAnalyzing: Boolean = false,
    val result: BodyAnalysisResult? = null,
    val error: String? = null,
    val isConfigured: Boolean = false, // Au moins une clé API dispo
    // Valeurs éditables par l'utilisateur (init depuis result)
    val editHeightCm: String = "",
    val editWeightKg: String = "",
    val editSex: String = "M",
    val editWaistCm: String = "",
    val editChestCm: String = "",
    val editHipCm: String = "",
    val editArmCm: String = "",
    val editThighCm: String = "",
    val editCalfCm: String = "",
    val editBodyFatPercent: String = "",
    val applied: Boolean = false, // true quand les mesures ont été sauvegardées au profil
    // Mesh generation
    val isGeneratingMesh: Boolean = false,
    /**
     * Path JSON du fichier `MeshFeatures` persisté. Null = pas encore généré
     * pour la photo courante. Lecture côté UI via [meshFeatures] (chargé
     * paresseusement par le ViewModel).
     */
    val meshFeaturesPath: String? = null,
    /**
     * Snapshot des features chargées en mémoire pour le rendu Canvas. Null
     * tant que pas chargé / mesh non généré. On évite de re-décoder le JSON
     * à chaque recomposition Compose en gardant l'instance ici.
     */
    val meshFeatures: MeshFeatures? = null,
    val meshError: String? = null,
    /**
     * Quand `true`, la génération courante du mesh DOIT être suivie d'une
     * navigation vers BodyMeshScreen dès que les features sont prêtes. Set
     * par [generateMeshAndNavigate] / handler d'icône grille. Reset par
     * [consumeMeshNavigation] après navigation effective.
     *
     * **Pourquoi un flag plutôt qu'un Channel/SharedFlow** : moins de
     * complexité pour 1 seul event simple. Le LaunchedEffect côté Screen
     * observe le triple (pendingNavigateToMesh, meshFeaturesPath, isGeneratingMesh)
     * et tire la nav quand toutes les conditions sont remplies. Deterministe
     * et debugable.
     */
    val pendingNavigateToMesh: Boolean = false,
    // #15 — LLM insight 1-liner (cache hit ou freshly generated). Null tant
    // que pas calculé / consentement manquant / clé absente. Chargement async,
    // affichage avec un loader pendant `isGeneratingInsight=true`.
    val meshInsight: String? = null,
    val isGeneratingInsight: Boolean = false,
    // Photos
    val originalImagePath: String? = null,
    val bodyScanTimestamp: LocalDateTime? = null
) {
    /** BMI calculé à la volée depuis les valeurs d'édition. */
    val computedBmi: Double
        get() {
            val h = editHeightCm.toDoubleOrNull() ?: 0.0
            val w = editWeightKg.toDoubleOrNull() ?: 0.0
            if (h <= 0 || w <= 0) return 0.0
            val hm = h / 100.0
            return w / (hm * hm)
        }

    val bmiLabel: String
        get() {
            val b = computedBmi
            return when {
                b == 0.0 -> ""
                b < 18.5 -> "Maigreur"
                b < 25.0 -> "Normal"
                b < 30.0 -> "Surpoids"
                else -> "Obésité"
            }
        }
}

@HiltViewModel
class BodyScannerViewModel @Inject constructor(
    private val bodyAnalysisService: BodyAnalysisService,
    private val meshExtractor: BodyMeshExtractor,
    private val insightGenerator: BodyInsightGenerator,
    private val bodyScanLogDao: BodyScanLogDao,
    private val userRepository: UserRepository,
    private val llmResolver: AssistantLlmResolver,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val gson = Gson()

    private val _state = MutableStateFlow(BodyScannerState())
    val state: StateFlow<BodyScannerState> = _state.asStateFlow()

    init {
        // Charger les valeurs existantes du profil pour pré-remplir les champs
        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            val hasKey = userRepository.hasApiKey(SecureKeyStore.Provider.GEMINI)
                || userRepository.hasApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                || userRepository.hasApiKey(SecureKeyStore.Provider.MISTRAL)

            // Charger les features mesh persistées si le fichier existe encore
            // sur disque. Anti-zombie : un path en DB sans fichier (cleanup
            // système, restore depuis un backup où la photo n'a pas été
            // incluse) → on remet null pour ne pas afficher un mesh fantôme.
            val featuresPath = profile?.bodyMeshFeaturesPath
            val features = featuresPath?.let { loadFeaturesFromDisk(it) }
            val effectivePath = if (features != null) featuresPath else null

            _state.update {
                it.copy(
                    isConfigured = hasKey,
                    editHeightCm = profile?.heightCm?.takeIf { h -> h > 0 }?.toString() ?: "",
                    editWeightKg = profile?.currentWeightKg?.takeIf { w -> w > 0 }?.toString() ?: "",
                    editSex = profile?.sex ?: "M",
                    editWaistCm = profile?.waistCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editChestCm = profile?.chestCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editHipCm = profile?.hipCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editArmCm = profile?.armCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editThighCm = profile?.thighCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editCalfCm = profile?.calfCm?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    editBodyFatPercent = profile?.bodyFatPercent?.takeIf { it > 0 }?.toInt()?.toString() ?: "",
                    meshFeaturesPath = effectivePath,
                    meshFeatures = features,
                    originalImagePath = profile?.bodyScanImagePath,
                    bodyScanTimestamp = profile?.bodyScanTimestamp
                )
            }
            // Si on a déjà un mesh + un profil, tenter de récupérer l'insight
            // (cache hit DataStore, sinon fallback null silencieux). Pas de
            // re-generate au boot pour ne pas spammer le LLM.
            if (features != null && profile != null) {
                tryLoadInsight(features, profile)
            }
        }
    }

    /**
     * Lance la résolution insight pour le scan courant. Idempotent : si déjà
     * cached pour ce capturedAtMs, retour immédiat sans appel LLM.
     */
    private fun tryLoadInsight(
        features: MeshFeatures,
        profile: com.shredcoach.app.data.local.entity.UserProfileEntity,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingInsight = true) }
            val insight = insightGenerator.getOrGenerate(features, profile)
            _state.update { it.copy(isGeneratingInsight = false, meshInsight = insight) }
        }
    }

    fun setImage(bitmap: Bitmap) {
        // Une nouvelle photo invalide le mesh précédent (qui correspondait à
        // l'ancienne photo). On reset le path en mémoire mais on ne touche
        // pas au DB ni au fichier disque ici — `applyMeshToProfile` (appelé
        // au succès de `generateMesh`) écrasera proprement.
        _state.update {
            it.copy(
                imageBitmap = bitmap,
                result = null,
                error = null,
                applied = false,
                meshFeatures = null,
                meshFeaturesPath = null,
                meshError = null,
                meshInsight = null,
                isGeneratingInsight = false,
            )
        }
    }

    fun clear() {
        _state.update {
            it.copy(
                imageBitmap = null,
                result = null,
                error = null,
                applied = false,
                meshFeatures = null,
                meshFeaturesPath = null,
                meshError = null,
                meshInsight = null,
                isGeneratingInsight = false,
            )
        }
    }

    fun analyze() {
        val bitmap = _state.value.imageBitmap ?: return
        _state.update { it.copy(isAnalyzing = true, error = null, result = null) }

        viewModelScope.launch {
            val profile = userRepository.getUserProfileOnce()
            // Resolver per-assistant : BODY_SCAN configurable via Settings → Assistants IA.
            val llmConfig = llmResolver.resolveWithProfile(AiAssistant.BODY_SCAN, profile)
            val provider = llmConfig.provider.name
            val apiKey = when (provider) {
                "GROQ" -> userRepository.getApiKey(SecureKeyStore.Provider.GROQ_MEAL)
                "MISTRAL" -> userRepository.getApiKey(SecureKeyStore.Provider.MISTRAL)
                else -> userRepository.getApiKey(SecureKeyStore.Provider.GEMINI)
            }
            val model = llmConfig.modelId

            if (apiKey.isBlank()) {
                val providerName = when (provider) { "GROQ" -> "Groq"; "MISTRAL" -> "Mistral"; else -> "Gemini" }
                _state.update { it.copy(isAnalyzing = false, error = "Configure ta clé API $providerName dans Réglages → Meal Scanner") }
                return@launch
            }

            // Sauvegarder la photo originale
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val originalPath = saveImageToFile(stream.toByteArray(), "original")

            val result = bodyAnalysisService.analyzeBody(
                stream.toByteArray(), "image/jpeg", apiKey, model, provider
            )

            result.fold(
                onSuccess = { analysis ->
                    val now = LocalDateTime.now()
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            result = analysis,
                            originalImagePath = originalPath,
                            bodyScanTimestamp = now,
                            // Auto-remplir les champs éditables avec les valeurs IA
                            editSex = analysis.sex.takeIf { s -> s.isNotBlank() } ?: it.editSex,
                            editHeightCm = analysis.heightCm.takeIf { h -> h > 0 }?.toString() ?: it.editHeightCm,
                            editWeightKg = analysis.weightEstimateKg.takeIf { w -> w > 0 }?.toString() ?: it.editWeightKg,
                            editWaistCm = analysis.waistCm.takeIf { v -> v > 0 }?.toString() ?: it.editWaistCm,
                            editChestCm = analysis.chestCm.takeIf { v -> v > 0 }?.toString() ?: it.editChestCm,
                            editHipCm = analysis.hipCm.takeIf { v -> v > 0 }?.toString() ?: it.editHipCm,
                            editArmCm = analysis.armCm.takeIf { v -> v > 0 }?.toString() ?: it.editArmCm,
                            editThighCm = analysis.thighCm.takeIf { v -> v > 0 }?.toString() ?: it.editThighCm,
                            editCalfCm = analysis.calfCm.takeIf { v -> v > 0 }?.toString() ?: it.editCalfCm,
                            editBodyFatPercent = analysis.bodyFatPercent.takeIf { v -> v > 0 }?.toString() ?: it.editBodyFatPercent
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isAnalyzing = false, error = error.message ?: "Erreur d'analyse") }
                }
            )
        }
    }

    // ── Setters pour l'édition manuelle ──
    fun setSex(v: String) = _state.update { it.copy(editSex = v) }
    fun setHeight(v: String) = _state.update { it.copy(editHeightCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setWeight(v: String) = _state.update { it.copy(editWeightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) }
    fun setWaist(v: String) = _state.update { it.copy(editWaistCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setChest(v: String) = _state.update { it.copy(editChestCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setHip(v: String) = _state.update { it.copy(editHipCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setArm(v: String) = _state.update { it.copy(editArmCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setThigh(v: String) = _state.update { it.copy(editThighCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setCalf(v: String) = _state.update { it.copy(editCalfCm = v.filter { c -> c.isDigit() }.take(3)) }
    fun setBodyFat(v: String) = _state.update { it.copy(editBodyFatPercent = v.filter { c -> c.isDigit() }.take(3)) }

    /** Sauvegarde les mesures au profil utilisateur. */
    fun applyToProfile() {
        viewModelScope.launch {
            val s = _state.value
            val current = userRepository.getUserProfileOnce() ?: return@launch
            // Normaliser les entrées (accepter virgule comme séparateur décimal)
            fun String.toDouble2(): Double? = replace(',', '.').toDoubleOrNull()
            fun String.toIntClean(): Int? = replace(',', '.').toDoubleOrNull()?.toInt()
            val updated = current.copy(
                sex = s.editSex,
                heightCm = s.editHeightCm.toIntClean() ?: current.heightCm,
                currentWeightKg = s.editWeightKg.toDouble2() ?: current.currentWeightKg,
                waistCm = s.editWaistCm.toDouble2() ?: current.waistCm,
                chestCm = s.editChestCm.toDouble2() ?: current.chestCm,
                hipCm = s.editHipCm.toDouble2() ?: current.hipCm,
                armCm = s.editArmCm.toDouble2() ?: current.armCm,
                thighCm = s.editThighCm.toDouble2() ?: current.thighCm,
                calfCm = s.editCalfCm.toDouble2() ?: current.calfCm,
                bodyFatPercent = s.editBodyFatPercent.toDouble2() ?: current.bodyFatPercent,
                bodyScanImagePath = s.originalImagePath ?: current.bodyScanImagePath,
                bodyScanTimestamp = s.bodyScanTimestamp ?: current.bodyScanTimestamp,
                bodyScanConfidence = s.result?.confidence ?: current.bodyScanConfidence,
                bodyScanNotes = s.result?.notes ?: current.bodyScanNotes
            )
            userRepository.updateUserProfile(updated)
            _state.update { it.copy(applied = true) }
        }
    }

    /**
     * Extrait les features mesh on-device (ML Kit Pose + Selfie Segmentation),
     * persiste le JSON sur disque, met à jour le profil. Pas d'appel réseau,
     * pas de clé API requise.
     *
     * **Idempotent** : appelable plusieurs fois → écrase proprement la version
     * précédente (delete old file).
     *
     * **Erreurs métiers possibles** :
     *  - `NO_POSE_DETECTED` : la photo ne contient pas de pose détectable
     *    (corps incomplet, occlusion forte, photo non-humaine)
     *  - JSON write failed : disque plein → on remonte un message générique.
     */
    fun generateMesh() {
        val bitmap = _state.value.imageBitmap
        if (bitmap == null) {
            _state.update {
                it.copy(meshError = appContext.withCurrentLocale().getString(R.string.bodymesh_error_no_photo))
            }
            return
        }
        _state.update { it.copy(isGeneratingMesh = true, meshError = null) }

        viewModelScope.launch {
            val extracted = meshExtractor.extract(bitmap)
            extracted.fold(
                onSuccess = { features ->
                    val path = saveFeaturesToDisk(features)
                    if (path == null) {
                        _state.update {
                            it.copy(
                                isGeneratingMesh = false,
                                meshError = appContext.withCurrentLocale().getString(R.string.bodymesh_error_save),
                            )
                        }
                        return@fold
                    }

                    // Cleanup ancien mesh file (orphelin) — best-effort.
                    val oldPath = _state.value.meshFeaturesPath
                    if (!oldPath.isNullOrBlank() && oldPath != path) {
                        runCatching { File(oldPath).delete() }
                    }

                    // Persiste sur le profil. Wipe le legacy `bodyMeshImagePath`
                    // pour ne pas garder un PNG Gemini orphelin qui ne reflète
                    // plus la photo courante.
                    val current = userRepository.getUserProfileOnce()
                    if (current != null) {
                        userRepository.updateUserProfile(
                            current.copy(
                                bodyMeshFeaturesPath = path,
                                bodyMeshImagePath = null,
                            )
                        )
                    }

                    // #16 — Historise le scan dans body_scan_logs.
                    // Snapshot des analytics + mesures profil pour pouvoir
                    // tracer la timeline même si les fichiers JSON disparaissent.
                    runCatching {
                        bodyScanLogDao.insert(
                            BodyScanLogEntity(
                                capturedAtMs = features.capturedAtMs,
                                featuresPath = path,
                                photoPath = _state.value.originalImagePath,
                                postureScore = features.analytics.postureScore,
                                vTaperRatio = features.analytics.vTaperRatio,
                                shoulderTiltDeg = features.analytics.shoulderTiltDeg,
                                hipTiltDeg = features.analytics.hipTiltDeg,
                                shoulderAsymmetryPct = features.analytics.shoulderAsymmetryPct,
                                hipAsymmetryPct = features.analytics.hipAsymmetryPct,
                                heightCm = current?.heightCm ?: 0,
                                weightKg = current?.currentWeightKg ?: 0.0,
                                bodyFatPercent = current?.bodyFatPercent ?: 0.0,
                            )
                        )
                    }

                    _state.update {
                        it.copy(
                            isGeneratingMesh = false,
                            meshFeaturesPath = path,
                            meshFeatures = features,
                            meshError = null,
                            // Reset l'insight précédent (correspondait à un autre
                            // scan), on relance la génération pour le nouveau.
                            meshInsight = null,
                        )
                    }
                    // Trigger insight pour ce nouveau scan (async, non-bloquant).
                    if (current != null) {
                        tryLoadInsight(features, current)
                    }
                },
                onFailure = { error ->
                    val msg = when {
                        error.message?.contains("NO_POSE_DETECTED") == true ->
                            appContext.withCurrentLocale().getString(R.string.bodymesh_error_no_pose)
                        else ->
                            appContext.withCurrentLocale().getString(R.string.bodymesh_error_generic)
                    }
                    // Reset le flag de nav pending : pas de raison de naviguer
                    // vers un mesh qui n'existe pas. Sinon un click ultérieur
                    // qui produirait un mesh nav-iguerait sans intention.
                    _state.update {
                        it.copy(
                            isGeneratingMesh = false,
                            meshError = msg,
                            pendingNavigateToMesh = false,
                        )
                    }
                }
            )
        }
    }

    /**
     * Variante de [generateMesh] qui flag l'intent de naviguer vers
     * BodyMeshScreen dès que les features sont prêtes. Utilisée par les CTAs
     * "Générer le mesh" et l'icône grille du top app bar — l'utilisateur ne
     * veut pas avoir à cliquer une 2e fois sur "Voir" après la génération.
     *
     * Si un mesh existe déjà (`meshFeaturesPath != null`), set juste le flag
     * pour que le LaunchedEffect navigue immédiatement sans regénérer.
     */
    fun generateMeshAndNavigate() {
        if (_state.value.meshFeaturesPath != null && _state.value.meshFeatures != null) {
            // Déjà disponible : pas de regénération, juste flag pour nav.
            _state.update { it.copy(pendingNavigateToMesh = true) }
            return
        }
        _state.update { it.copy(pendingNavigateToMesh = true) }
        generateMesh()
    }

    /**
     * À appeler depuis le Composable APRÈS une navigation déclenchée par
     * [pendingNavigateToMesh]. Évite que le flag reste "true" et re-trigger
     * une nouvelle nav au retour sur l'écran.
     */
    fun consumeMeshNavigation() {
        _state.update { it.copy(pendingNavigateToMesh = false) }
    }

    /**
     * Sérialise [features] en JSON et l'écrit dans `filesDir/body_scans/`.
     * Chemin retourné absolu, ou null en cas d'échec disque.
     */
    private fun saveFeaturesToDisk(features: MeshFeatures): String? {
        return try {
            val dir = File(appContext.filesDir, "body_scans")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "mesh_${features.capturedAtMs}.json")
            file.writeText(gson.toJson(features))
            file.absolutePath
        } catch (_: Exception) { null }
    }

    /**
     * Lecture des features depuis le JSON. Robuste aux fichiers manquants/
     * corrompus → null silencieux (l'UI tombe sur l'empty state).
     */
    private fun loadFeaturesFromDisk(path: String): MeshFeatures? {
        return try {
            val file = File(path)
            if (!file.exists() || !file.canRead()) return null
            val features = gson.fromJson(file.readText(), MeshFeatures::class.java)
            // Garde-fou versionning : on ignore les features anciennes
            // qui ne matchent pas le schéma courant.
            if (features.version != MeshFeatures.CURRENT_VERSION) null
            else features
        } catch (_: Exception) { null }
    }

    /** Sauvegarde l'image dans `filesDir/body_scans/`. */
    private fun saveImageToFile(bytes: ByteArray, prefix: String, ext: String = "jpg"): String? {
        return try {
            val dir = java.io.File(appContext.filesDir, "body_scans")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "${prefix}_${System.currentTimeMillis()}.$ext")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (_: Exception) { null }
    }
}
