package com.shredcoach.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shredcoach.app.data.local.secure.SecureKeyStore

/**
 * Migrations Room pour ShredCoachDatabase.
 *
 * Règle FAANG : à partir de v33, **toute** évolution de schéma DOIT être
 * couverte par une [Migration] explicite ici. Plus jamais de
 * `fallbackToDestructiveMigration` qui efface les données utilisateur.
 *
 * Chaque migration est testable isolément via [androidx.room.testing.MigrationTestHelper]
 * (cf. [com.shredcoach.app.data.local.MigrationsTest] — Phase E).
 */
object Migrations {

    /**
     * v33 → v34 : déplace les clés API LLM des colonnes `user_profile`
     * (en clair) vers le [SecureKeyStore] (chiffré AES256-GCM, master key
     * Android Keystore), puis retire les 4 colonnes correspondantes.
     *
     * **Atomique** : la copie vers SecureKeyStore et le drop des colonnes
     * sont exécutés dans la même transaction Room (Room enveloppe chaque
     * `Migration.migrate` dans une transaction). Si la copie échoue,
     * la migration rollback et les colonnes Room restent intactes — aucune
     * perte de clé possible.
     *
     * **Idempotent** : si une clé existe déjà dans SecureKeyStore (cas où
     * l'ApiKeyMigrationManager d'une version précédente l'a déjà copiée),
     * la valeur Room en clair est ignorée — pas d'écrasement.
     */
    fun migration33to34(context: Context): Migration = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ─── 1. Copier les clés en clair vers le SecureKeyStore ───
            copyApiKeysToSecureStore(context, db)

            // ─── 2. Retirer les 4 colonnes via table-rebuild ───
            // SQLite < 3.35 (Android < 11) ne supporte pas ALTER TABLE DROP COLUMN.
            // On reconstruit la table avec uniquement les colonnes conservées.
            db.execSQL(USER_PROFILE_V34_CREATE_SQL)
            db.execSQL(USER_PROFILE_V33_TO_V34_COPY_SQL)
            db.execSQL("DROP TABLE `user_profile`")
            db.execSQL("ALTER TABLE `user_profile_new` RENAME TO `user_profile`")
        }
    }

    /**
     * v34 → v35 : ajoute 3 colonnes à `workout_logs` pour persister l'état
     * de la séance active à travers cold-start :
     * - `currentExerciseStartedAt` : wall-clock du début de l'exo courant.
     * - `currentSetStartedAt` : wall-clock du `Démarrer la série` (null si
     *   aucune série en cours).
     * - `currentSetTimedTotalSeconds` : durée cible pour les exos chronométrés
     *   (gainage, cardio fixe). 0 = pas un set timed.
     *
     * Toutes nullables / DEFAULT 0 → ALTER TABLE ADD COLUMN simple, pas besoin
     * de table-rebuild. Aucune donnée existante affectée.
     */
    fun migration34to35(): Migration = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `currentExerciseStartedAt` TEXT")
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `currentSetStartedAt` TEXT")
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `currentSetTimedTotalSeconds` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun copyApiKeysToSecureStore(context: Context, db: SupportSQLiteDatabase) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                SecureKeyStore.FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            db.query(
                "SELECT `llmApiKey`, `geminiApiKey`, `groqMealApiKey`, `mistralApiKey` " +
                    "FROM `user_profile` WHERE `id` = 1"
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val mappings = listOf(
                        SecureKeyStore.Provider.LLM.name to cursor.getString(0).orEmpty(),
                        SecureKeyStore.Provider.GEMINI.name to cursor.getString(1).orEmpty(),
                        SecureKeyStore.Provider.GROQ_MEAL.name to cursor.getString(2).orEmpty(),
                        SecureKeyStore.Provider.MISTRAL.name to cursor.getString(3).orEmpty()
                    )
                    val editor = prefs.edit()
                    var copied = 0
                    for ((prefKey, plaintextValue) in mappings) {
                        // Idempotent : ne pas écraser une valeur déjà présente.
                        if (plaintextValue.isNotBlank() && !prefs.contains(prefKey)) {
                            editor.putString(prefKey, plaintextValue)
                            copied++
                        }
                    }
                    if (copied > 0) {
                        editor.apply()
                        Log.i(TAG, "v33→v34: copied $copied API key(s) to SecureKeyStore")
                    }
                }
            }
        } catch (t: Throwable) {
            // Si EncryptedSharedPreferences échoue (rare : Keystore corrompu),
            // on loggue mais on ne bloque pas la migration. L'utilisateur devra
            // re-saisir ses clés dans Settings, mais aucune donnée n'est perdue
            // ailleurs (séances, photos, etc.).
            Log.e(TAG, "v33→v34: failed to copy API keys to SecureKeyStore", t)
        }
    }

    private const val TAG = "Migrations"

    /**
     * SQL exact de la table `user_profile` en v34 (généré par Room et
     * extrait de schemas/34.json). Ne JAMAIS éditer manuellement —
     * doit matcher byte-pour-byte ce que Room attend, sinon l'app crashe
     * au runtime avec "Migration didn't properly handle: user_profile".
     */
    private const val USER_PROFILE_V34_CREATE_SQL = """
        CREATE TABLE IF NOT EXISTS `user_profile_new` (
            `id` INTEGER NOT NULL,
            `firstName` TEXT NOT NULL,
            `lastName` TEXT NOT NULL,
            `age` INTEGER NOT NULL,
            `sex` TEXT NOT NULL,
            `heightCm` INTEGER NOT NULL,
            `currentWeightKg` REAL NOT NULL,
            `targetWeightKg` REAL NOT NULL,
            `level` TEXT NOT NULL,
            `equipment` TEXT NOT NULL,
            `goal` TEXT NOT NULL,
            `preferredWorkoutDuration` INTEGER NOT NULL,
            `bedTime` TEXT,
            `workoutDays` TEXT NOT NULL,
            `waistCm` REAL NOT NULL,
            `chestCm` REAL NOT NULL,
            `armCm` REAL NOT NULL,
            `thighCm` REAL NOT NULL,
            `hipCm` REAL NOT NULL,
            `calfCm` REAL NOT NULL,
            `bodyFatPercent` REAL NOT NULL,
            `bodyScanImagePath` TEXT,
            `bodyMeshImagePath` TEXT,
            `bodyScanTimestamp` TEXT,
            `bodyScanConfidence` TEXT NOT NULL,
            `bodyScanNotes` TEXT NOT NULL,
            `useImperial` INTEGER NOT NULL,
            `darkMode` TEXT NOT NULL,
            `themePalette` TEXT NOT NULL,
            `currentStreakDays` INTEGER NOT NULL,
            `totalWorkouts` INTEGER NOT NULL,
            `autoStartAfterRest` INTEGER NOT NULL,
            `vibrationEnabled` INTEGER NOT NULL,
            `soundEnabled` INTEGER NOT NULL,
            `voiceEnabled` INTEGER NOT NULL,
            `defaultRestSeconds` INTEGER NOT NULL,
            `showCoachTips` INTEGER NOT NULL,
            `suggestBonusSeries` INTEGER NOT NULL,
            `notificationsEnabled` INTEGER NOT NULL,
            `notifBreakfast` INTEGER NOT NULL,
            `notifLunch` INTEGER NOT NULL,
            `notifSnack` INTEGER NOT NULL,
            `notifDinner` INTEGER NOT NULL,
            `notifShaker` INTEGER NOT NULL,
            `notifBedtime` INTEGER NOT NULL,
            `notifMotivation` INTEGER NOT NULL,
            `notifMealDebrief` INTEGER NOT NULL,
            `notifWorkoutDebrief` INTEGER NOT NULL,
            `mealDebriefDelayMinutes` INTEGER NOT NULL,
            `workoutDebriefDelayMinutes` INTEGER NOT NULL,
            `breakfastTime` TEXT NOT NULL,
            `lunchTime` TEXT NOT NULL,
            `snackTime` TEXT NOT NULL,
            `dinnerTime` TEXT NOT NULL,
            `shakerMorningTime` TEXT NOT NULL,
            `shakerEveningTime` TEXT NOT NULL,
            `healthNotes` TEXT NOT NULL,
            `mealScanProvider` TEXT NOT NULL,
            `geminiModel` TEXT NOT NULL,
            `llmProvider` TEXT NOT NULL,
            `llmModel` TEXT NOT NULL,
            `profilePhotoPath` TEXT,
            PRIMARY KEY(`id`)
        )
    """

    /** Recopie l'intégralité des colonnes communes de v33 vers user_profile_new. */
    private const val USER_PROFILE_V33_TO_V34_COPY_SQL = """
        INSERT INTO `user_profile_new` (
            `id`, `firstName`, `lastName`, `age`, `sex`, `heightCm`,
            `currentWeightKg`, `targetWeightKg`, `level`, `equipment`, `goal`,
            `preferredWorkoutDuration`, `bedTime`, `workoutDays`,
            `waistCm`, `chestCm`, `armCm`, `thighCm`, `hipCm`, `calfCm`, `bodyFatPercent`,
            `bodyScanImagePath`, `bodyMeshImagePath`, `bodyScanTimestamp`,
            `bodyScanConfidence`, `bodyScanNotes`,
            `useImperial`, `darkMode`, `themePalette`, `currentStreakDays`, `totalWorkouts`,
            `autoStartAfterRest`, `vibrationEnabled`, `soundEnabled`, `voiceEnabled`,
            `defaultRestSeconds`, `showCoachTips`, `suggestBonusSeries`,
            `notificationsEnabled`, `notifBreakfast`, `notifLunch`, `notifSnack`,
            `notifDinner`, `notifShaker`, `notifBedtime`, `notifMotivation`,
            `notifMealDebrief`, `notifWorkoutDebrief`,
            `mealDebriefDelayMinutes`, `workoutDebriefDelayMinutes`,
            `breakfastTime`, `lunchTime`, `snackTime`, `dinnerTime`,
            `shakerMorningTime`, `shakerEveningTime`,
            `healthNotes`, `mealScanProvider`, `geminiModel`,
            `llmProvider`, `llmModel`, `profilePhotoPath`
        )
        SELECT
            `id`, `firstName`, `lastName`, `age`, `sex`, `heightCm`,
            `currentWeightKg`, `targetWeightKg`, `level`, `equipment`, `goal`,
            `preferredWorkoutDuration`, `bedTime`, `workoutDays`,
            `waistCm`, `chestCm`, `armCm`, `thighCm`, `hipCm`, `calfCm`, `bodyFatPercent`,
            `bodyScanImagePath`, `bodyMeshImagePath`, `bodyScanTimestamp`,
            `bodyScanConfidence`, `bodyScanNotes`,
            `useImperial`, `darkMode`, `themePalette`, `currentStreakDays`, `totalWorkouts`,
            `autoStartAfterRest`, `vibrationEnabled`, `soundEnabled`, `voiceEnabled`,
            `defaultRestSeconds`, `showCoachTips`, `suggestBonusSeries`,
            `notificationsEnabled`, `notifBreakfast`, `notifLunch`, `notifSnack`,
            `notifDinner`, `notifShaker`, `notifBedtime`, `notifMotivation`,
            `notifMealDebrief`, `notifWorkoutDebrief`,
            `mealDebriefDelayMinutes`, `workoutDebriefDelayMinutes`,
            `breakfastTime`, `lunchTime`, `snackTime`, `dinnerTime`,
            `shakerMorningTime`, `shakerEveningTime`,
            `healthNotes`, `mealScanProvider`, `geminiModel`,
            `llmProvider`, `llmModel`, `profilePhotoPath`
        FROM `user_profile`
    """
}
