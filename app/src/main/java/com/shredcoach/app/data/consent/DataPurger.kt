package com.shredcoach.app.data.consent

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.room.withTransaction
import com.shredcoach.app.data.backup.BackupSettingsStore
import com.shredcoach.app.data.local.ShredCoachDatabase
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.domain.coach.CoachHistoryStore
import com.shredcoach.app.domain.coach.CoachSettingsStore
import com.shredcoach.app.domain.streak.StreakMilestoneStore
import com.shredcoach.app.domain.wellness.WellnessStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Right to be forgotten" RGPD : efface l'intégralité des données utilisateur
 * locales en une seule opération.
 *
 * Ce qui est purgé :
 * 1. **Base Room** (toutes les 17 tables) — séances, repas, photos, chat, etc.
 * 2. **Clés API chiffrées** (LLM, Gemini, Groq, Mistral)
 * 3. **DataStores** (consent, backup settings, théme/locale via UserProfile au step 1)
 * 4. **Fichiers photos sur disque** : `body_scans/`, `meal_scans/`, `photos/`,
 *    `restored_photos/`
 * 5. **Cache app** (best-effort)
 *
 * Ce qui n'est PAS purgé (volontairement) :
 * - Les **archives de backup** (sur Drive/local) — elles appartiennent à
 *   l'utilisateur. La désactivation du backup via [BackupSettingsStore.reset]
 *   nous fait juste oublier le dossier ; les ZIP existants sont intacts.
 * - Les **logs système Android** (logcat) — sortent de notre périmètre.
 *
 * Ordre d'exécution **critique** : on lit les paths photos depuis Room AVANT
 * de wiper la DB, sinon on perd le mapping et on devrait wiper tout filesDir
 * au pifomètre (risquant de casser DataStore).
 *
 * **Best-effort** : si une étape échoue (fichier verrouillé, permission, etc.),
 * on log et on continue. La promesse RGPD "tout effacer" est mieux servie par
 * "presque tout effacer" que par un crash qui laisse 100% des données en place.
 */
@Singleton
class DataPurger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ShredCoachDatabase,
    private val secureKeyStore: SecureKeyStore,
    private val consentStore: ConsentStore,
    private val backupSettings: BackupSettingsStore,
    private val coachSettings: CoachSettingsStore,
    private val coachHistory: CoachHistoryStore,
    private val streakMilestoneStore: StreakMilestoneStore,
    private val wellnessStore: WellnessStore,
) {
    suspend fun purgeAll() {
        Log.i(TAG, "Début purge totale (RGPD right-to-be-forgotten)")

        // 1. Capturer les paths photos AVANT de wiper la DB.
        val photoPaths = runCatching { collectPhotoPaths() }
            .onFailure { Log.w(TAG, "Capture des paths photo échouée — fallback wipe directories", it) }
            .getOrDefault(emptyList())

        // 2. Wipe Room (transaction atomique). On ne peut pas appeler
        //    clearAllTables() ici car Room doc dit qu'il ne réinitialise pas
        //    sqlite_sequence, et on veut un reset COMPLET pour éviter qu'un
        //    nouvel enregistrement utilisateur reçoive un id pré-purge.
        runCatching {
            db.withTransaction {
                val sqlite = db.openHelper.writableDatabase
                ShredCoachDatabase.ALL_TABLES.forEach { table -> sqlite.execSQL("DELETE FROM $table") }
                // Reset autoincrement counters → IDs repartent de 1.
                runCatching { sqlite.execSQL("DELETE FROM sqlite_sequence") }
            }
        }.onFailure { Log.w(TAG, "Wipe Room échoué", it) }

        // 3. Wipe SecureKeyStore (API keys).
        runCatching { secureKeyStore.clearAll() }
            .onFailure { Log.w(TAG, "Wipe SecureKeyStore échoué", it) }

        // 4. Wipe DataStores (consent + backup settings + coach settings + coach history).
        //    Pour backup_settings on libère D'ABORD la permission SAF persistante :
        //    sinon Android continue de tracker un URI orphelin (quota 128/contentResolver),
        //    ce qui viole l'esprit RGPD "tout effacer".
        runCatching {
            backupSettings.snapshot.first().folderUri?.let { uri ->
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
            }
        }.onFailure { Log.w(TAG, "Release SAF URI permission échoué (non bloquant)", it) }

        runCatching { consentStore.reset() }.onFailure { Log.w(TAG, "Reset consent échoué", it) }
        runCatching { backupSettings.reset() }.onFailure { Log.w(TAG, "Reset backup settings échoué", it) }
        runCatching { coachSettings.reset() }.onFailure { Log.w(TAG, "Reset coach settings échoué", it) }
        runCatching { coachHistory.reset() }.onFailure { Log.w(TAG, "Reset coach history échoué", it) }
        runCatching { streakMilestoneStore.reset() }.onFailure { Log.w(TAG, "Reset streak milestones échoué", it) }
        runCatching { wellnessStore.reset() }.onFailure { Log.w(TAG, "Reset wellness store échoué", it) }

        // 5. Wipe fichiers photos. On commence par les paths spécifiques
        //    (capturés en étape 1), puis on supprime les dirs en bloc pour
        //    catcher tout fichier orphelin (DB row supprimée mais fichier resté).
        photoPaths.forEach { path -> runCatching { File(path).delete() } }
        PHOTO_DIRS.forEach { dirName ->
            val dir = File(context.filesDir, dirName)
            if (dir.exists()) runCatching { dir.deleteRecursively() }
                .onFailure { Log.w(TAG, "Suppression $dirName échouée", it) }
        }

        // 6. Cache app (downloaded GIFs, thumbnails Coil, etc.).
        runCatching { context.cacheDir.deleteRecursively() }
            .onFailure { Log.w(TAG, "Wipe cacheDir échoué (non bloquant)", it) }

        Log.i(TAG, "Purge totale terminée")
    }

    /**
     * Lit les chemins de photos depuis la DB avant que celle-ci soit wipée.
     * Permet de supprimer EXACTEMENT ce que l'utilisateur a stocké, sans
     * risquer de toucher des fichiers d'autres composants qui partageraient
     * le même répertoire.
     */
    private suspend fun collectPhotoPaths(): List<String> {
        val paths = mutableListOf<String>()
        val userProfileDao = db.userProfileDao()
        val mealScanDao = db.mealScanDao()
        userProfileDao.getAllPhotos().first().forEach { paths += it.filePath }
        mealScanDao.getAllScans().first().mapNotNull { it.photoPath }.forEach { paths += it }
        userProfileDao.getUserProfileOnce()?.profilePhotoPath?.let { paths += it }
        return paths
    }

    private companion object {
        const val TAG = "DataPurger"
        val PHOTO_DIRS = listOf("body_scans", "meal_scans", "photos", "restored_photos")
    }
}
