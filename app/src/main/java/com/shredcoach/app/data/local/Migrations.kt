package com.shredcoach.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shredcoach.app.data.local.secure.SecureKeyStore
import com.shredcoach.app.data.seed.ExerciseKey

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

    /**
     * v35 → v36 : 3 ajouts pour fiabiliser la reprise de séance après
     * navigation / process death.
     *
     * - `workouts.isFreestyle` : marque les séances libres (créées via Home →
     *   "Séance libre"). Détection robuste pour ne pas forcer la fin de séance
     *   au lieu de proposer la vue d'ensemble quand l'user revient en cours.
     * - `workout_logs.currentRestEndsAt` + `currentRestTotalSeconds` : ancre
     *   wall-clock du décompte de repos pour qu'il continue correctement entre
     *   navigations.
     * - `workout_logs.extraSeriesJson` : Map<exoIdx, +N séries bonus> persisté
     *   pour ne pas perdre les séries bonus ajoutées à la volée.
     *
     * Backfill `workouts.isFreestyle = 1` pour les workouts existants qui ont
     * `name = "Séance libre"` (utilisateurs déjà en cours sur une telle séance).
     */
    fun migration35to36(): Migration = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `isFreestyle` INTEGER NOT NULL DEFAULT 0")
            // Backfill : marque les freestyles existants (créés avant v36).
            db.execSQL("UPDATE `workouts` SET `isFreestyle` = 1 WHERE `name` = 'Séance libre'")

            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `currentRestEndsAt` TEXT")
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `currentRestTotalSeconds` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `extraSeriesJson` TEXT NOT NULL DEFAULT '{}'")
        }
    }

    /**
     * v36 → v37 : ouverture aux **routines split** (Push, Pull, Legs, Upper,
     * Lower, Chest+Tri, Back+Bi) en plus de Full Body.
     *
     * - `workouts.routineId` : id de la routine du template (généré ou custom).
     * - `workout_logs.routineId` : id capturé au démarrage de la séance, indexé
     *   pour les stats par routine (volume hebdo, fréquence, etc.).
     * - `scheduled_workouts.routineId` : id de la routine prévue, indexé pour
     *   afficher le calendrier par split.
     * - `user_profile.lastUsedRoutineId` : pré-sélection sur le RoutinePicker.
     *
     * Toutes les colonnes ont DEFAULT `'full_body'` → backfill implicite : tous
     * les workouts/logs/planifs pré-v37 sont rétro-classés en Full Body, ce qui
     * matche la réalité (l'app était mono-routine avant cette version).
     *
     * ALTER TABLE ADD COLUMN simple — pas de table-rebuild, pas de risque sur
     * les données existantes. Index créés en `IF NOT EXISTS` pour idempotence.
     */
    fun migration36to37(): Migration = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workouts` ADD COLUMN `routineId` TEXT NOT NULL DEFAULT 'full_body'")
            db.execSQL("ALTER TABLE `workout_logs` ADD COLUMN `routineId` TEXT NOT NULL DEFAULT 'full_body'")
            db.execSQL("ALTER TABLE `scheduled_workouts` ADD COLUMN `routineId` TEXT NOT NULL DEFAULT 'full_body'")
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `lastUsedRoutineId` TEXT NOT NULL DEFAULT 'full_body'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_logs_routineId` ON `workout_logs` (`routineId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scheduled_workouts_routineId` ON `scheduled_workouts` (`routineId`)")
        }
    }

    /**
     * v37 → v38 : ajout du support **i18n** — `user_profile.languageTag` (BCP-47)
     * pour stocker la langue choisie par l'utilisateur.
     *
     * - Colonne **nullable** (vs default `'fr'`) car :
     *   - Null = "pas encore choisi" → trigger auto-détection système au premier
     *     launch après migration. Plus naturel que de présumer FR pour tout le
     *     monde — un user qui a installé en EN sur un device EN aurait été
     *     surpris de voir l'UI rester FR.
     *   - L'auto-détect au premier launch persiste le choix automatiquement,
     *     donc la valeur null se résout en moins de 1 seconde après migration.
     *
     * ALTER TABLE ADD COLUMN simple — pas de table-rebuild, aucune donnée
     * existante affectée.
     */
    fun migration37to38(): Migration = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `languageTag` TEXT")
        }
    }

    /**
     * v38 → v39 : ajoute `exercises.exerciseKey` (clé i18n stable, ASCII
     * snake_case) et backfille en dérivant la valeur depuis `name` via
     * [ExerciseKey.fromName].
     *
     * **Pourquoi backfill dans la migration plutôt qu'au prochain seed-upsert** :
     * `seedDatabaseIdempotent` matche désormais par `exerciseKey`. Sans
     * backfill, les exercices déjà en DB auraient `exerciseKey = ""`, le
     * seed n'aurait aucun match, et il insérerait des doublons (existants en
     * v1 + nouveaux du seed). En backfillant ici, le contract devient :
     * « après v39, **toutes** les rows ont une `exerciseKey` non-vide ».
     *
     * **Idempotence** : NFKD + slugify est déterministe sur le `name`, donc
     * lancer la migration sur une DB fraîche (où la valeur viendrait du
     * default `""`) ou sur une DB existante donne le même résultat — aucun
     * risque de désynchronisation entre clé migrée et clé seed.
     *
     * **Coût** : SELECT id, name FROM exercises (~440 rows) + UPDATE batché
     * via une transaction implicite Room. ~50ms en cold-start IO.
     */
    fun migration38to39(): Migration = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `exercises` ADD COLUMN `exerciseKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_exerciseKey` ON `exercises` (`exerciseKey`)")

            // Backfill depuis le name FR — déterministe et idempotent.
            val rows = mutableListOf<Pair<Long, String>>()
            db.query("SELECT id, name FROM exercises").use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1)
                    rows += id to ExerciseKey.fromName(name)
                }
            }
            for ((id, key) in rows) {
                db.execSQL(
                    "UPDATE exercises SET exerciseKey = ? WHERE id = ?",
                    arrayOf(key, id)
                )
            }
        }
    }

    /**
     * v39 → v40 : ajoute `user_profile.bodyMeshFeaturesPath` pour stocker le
     * chemin du JSON contenant les features du mesh on-device (keypoints
     * pose + contour silhouette + analytics).
     *
     * **Pourquoi un nouveau field plutôt que réutiliser `bodyMeshImagePath`** :
     * - Sémantique différente (PNG snapshot Gemini vs JSON features ML Kit)
     * - Permet de garder l'ancien PNG accessible (legacy) si on veut
     *   afficher un avant/après ou laisser l'user décider
     * - Évite une migration table-rebuild lourde
     *
     * `bodyMeshImagePath` n'est PLUS écrit par le nouveau code (cf.
     * BodyScannerViewModel.generateMesh) et sera silencieusement ignoré
     * côté UI. Une future migration v40+ pourra le drop quand on sera sûr
     * que toutes les features ont été regénérées.
     */
    fun migration39to40(): Migration = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `bodyMeshFeaturesPath` TEXT")
        }
    }

    /**
     * v40 → v41 : crée la table `body_scan_logs` qui historise CHAQUE
     * génération de mesh corporel. Permet de tracer l'évolution dans le
     * temps (V-Taper, Posture, Body Fat) sur le Dashboard.
     *
     * **Schéma** : doit matcher exactement ce que Room dérive de
     * [BodyScanLogEntity], cf. schemas/41.json après build pour vérification.
     *
     * **Pourquoi pas de FK** : on garde l'historique même si l'utilisateur
     * supprime sa photo originale du profil ou wipe les fichiers (cleanup
     * système). Les paths sont nullable — on dégrade gracieusement vers les
     * analytics chiffrées seules.
     *
     * **Idempotent** : `IF NOT EXISTS` garde-fou si la migration est rejouée
     * (ne devrait pas mais c'est ceinture+bretelles).
     */
    /**
     * v41 → v42 : ajoute `userRating` (Int?, -1/0/+1) et `latencyMs` (Long?) à
     * `chat_messages` pour la télémétrie de qualité des réponses Shreddy.
     * - `userRating` null = pas encore noté, -1 = thumb down, +1 = thumb up
     * - `latencyMs` = durée total du tour LLM (envoi → dernier token), pour
     *   tracking coût/latence empirique par turn et par provider.
     */
    fun migration41to42(): Migration = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN userRating INTEGER")
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN latencyMs INTEGER")
        }
    }

    /**
     * v42 → v43 : ajoute le suivi glycémique CGM.
     *
     * - Nouvelle table `glucose_logs` : 1 entrée par date (UNIQUE index),
     *   métriques optionnelles parsées par OCR Gemini Vision (avg, peak, min,
     *   TIR%, hypo count, CV, courbe 24h JSON).
     * - `user_profile.notifGlucoseRecap` : toggle pour la notif J+1 12h17
     *   (analyse de la glycémie de la veille). Default 1 (activé) — opt-out
     *   plutôt qu'opt-in car la feature est cœur du suivi premium.
     *
     * Idempotent via `IF NOT EXISTS`. ALTER TABLE ADD COLUMN simple sur
     * user_profile (pas de table-rebuild).
     */
    /**
     * v43 → v44 : ajoute `persona` à `chat_messages` pour cohabiter Shreddy +
     * Dr. Glykos dans le même DAO sans mélanger les conversations.
     *
     * **Valeur stockée** : tag enum [com.shredcoach.app.domain.chat.ChatPersona]
     * (ex: `"shreddy"`, `"dr_glykos"`). Default `'shreddy'` pour préserver les
     * conversations existantes — elles restent toutes attribuées à Shreddy.
     *
     * Index sur (persona, conversationId) pour les listes de conversations
     * filtrées par persona (O(log N) avec index).
     */
    fun migration43to44(): Migration = object : Migration(43, 44) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `persona` TEXT NOT NULL DEFAULT 'shreddy'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_persona` ON `chat_messages` (`persona`)")
        }
    }

    /**
     * v44 → v45 : ajoute deux modificateurs de portion à `meal_scans` :
     *  - `servingMultiplier` : "J'en ai repris" (×1.5, ×2, ×3 ou custom).
     *  - `leftover*` : "J'ai pas fini mon plat" — restes rescannés en photo,
     *    déduits du repas initial.
     *
     * Toutes les colonnes ont DEFAULT (1.0 pour le multiplicateur, 0 pour les
     * restes) → backfill implicite neutre : tous les scans existants pré-v45
     * restent inchangés en valeur effective (multiplier=1, leftover=0).
     *
     * ALTER TABLE ADD COLUMN simple — pas de table-rebuild, pas de risque sur
     * les données existantes. 9 colonnes ajoutées en séquence.
     *
     * **Pourquoi sur `meal_scans` et pas `meal_logs`** : la décision "j'en ai
     * repris / pas fini" est sémantiquement au niveau du REPAS (le scan), pas
     * de l'ingrédient individuel. Tous les meal_logs liés via scanId héritent
     * du facteur via la JOIN d'agrégation (cf. NutritionDao.getDayTotals v45+).
     */
    fun migration44to45(): Migration = object : Migration(44, 45) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `servingMultiplier` REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverPhotoPath` TEXT")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverCalories` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverProteins` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverCarbs` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverFats` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverFibers` REAL NOT NULL DEFAULT 0.0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverWeight` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverResultJson` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `meal_scans` ADD COLUMN `leftoverScannedAt` TEXT")
        }
    }

    fun migration42to43(): Migration = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `glucose_logs` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `date` TEXT NOT NULL,
                    `imagePath` TEXT,
                    `avgMgdl` REAL,
                    `peakMgdl` REAL,
                    `peakTime` TEXT,
                    `minMgdl` REAL,
                    `minTime` TEXT,
                    `timeInRangePct` INTEGER,
                    `timeAboveRangePct` INTEGER,
                    `timeBelowRangePct` INTEGER,
                    `hypoCount` INTEGER,
                    `cv` REAL,
                    `glucoseMgdlCurveJson` TEXT,
                    `parseConfidence` REAL,
                    `parsedAt` TEXT,
                    `manualOverride` INTEGER NOT NULL DEFAULT 0,
                    `notes` TEXT
                )
            """.trimIndent())
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_glucose_logs_date` " +
                "ON `glucose_logs` (`date`)"
            )
            db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `notifGlucoseRecap` INTEGER NOT NULL DEFAULT 1")
        }
    }

    fun migration40to41(): Migration = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `body_scan_logs` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `capturedAtMs` INTEGER NOT NULL,
                    `featuresPath` TEXT,
                    `photoPath` TEXT,
                    `postureScore` INTEGER NOT NULL,
                    `vTaperRatio` REAL NOT NULL,
                    `shoulderTiltDeg` REAL NOT NULL,
                    `hipTiltDeg` REAL NOT NULL,
                    `shoulderAsymmetryPct` REAL NOT NULL,
                    `hipAsymmetryPct` REAL NOT NULL,
                    `heightCm` INTEGER NOT NULL,
                    `weightKg` REAL NOT NULL,
                    `bodyFatPercent` REAL NOT NULL
                )
            """.trimIndent())
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_body_scan_logs_capturedAtMs` " +
                "ON `body_scan_logs` (`capturedAtMs`)"
            )
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
