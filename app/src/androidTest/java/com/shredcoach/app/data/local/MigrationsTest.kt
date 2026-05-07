package com.shredcoach.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.shredcoach.app.data.local.secure.SecureKeyStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests de migration Room — **le filet de sécurité critique**.
 *
 * MigrationTestHelper crée une DB à la version source, exécute la migration
 * sous test, puis valide que le schéma final matche `schemas/<version>.json`.
 * Si Room détecte la moindre divergence (colonne, type, contrainte), le test
 * échoue avant même qu'on assert quoi que ce soit.
 *
 * Pour v33 → v34 on teste en plus :
 *  - les valeurs des colonnes conservées sont bien préservées
 *  - les 4 colonnes API key ont bien disparu
 *  - les clés en clair ont bien atterri dans EncryptedSharedPreferences
 */
@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    private val testDbName = "migration-test-db"
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShredCoachDatabase::class.java,
        emptyList(), // Pas d'AutoMigrationSpec
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    @After
    @Throws(IOException::class)
    fun cleanSecureKeyStore() {
        // Le file partagé entre la migration et SecureKeyStore — chaque test
        // démarre avec un store propre.
        context.deleteSharedPreferences(SecureKeyStore.FILE_NAME)
    }

    @Test
    fun migrate33to34_drop_les_4_colonnes_api_key() {
        // ─── 1. Créer une DB v33 avec un user_profile contenant des clés en clair ───
        helper.createDatabase(testDbName, 33).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profile (
                    id, firstName, lastName, age, sex, heightCm,
                    currentWeightKg, targetWeightKg, level, equipment, goal,
                    preferredWorkoutDuration, bedTime, workoutDays,
                    waistCm, chestCm, armCm, thighCm, hipCm, calfCm, bodyFatPercent,
                    bodyScanImagePath, bodyMeshImagePath, bodyScanTimestamp,
                    bodyScanConfidence, bodyScanNotes,
                    useImperial, darkMode, themePalette, currentStreakDays, totalWorkouts,
                    autoStartAfterRest, vibrationEnabled, soundEnabled, voiceEnabled,
                    defaultRestSeconds, showCoachTips, suggestBonusSeries,
                    notificationsEnabled, notifBreakfast, notifLunch, notifSnack,
                    notifDinner, notifShaker, notifBedtime, notifMotivation,
                    notifMealDebrief, notifWorkoutDebrief,
                    mealDebriefDelayMinutes, workoutDebriefDelayMinutes,
                    breakfastTime, lunchTime, snackTime, dinnerTime,
                    shakerMorningTime, shakerEveningTime,
                    healthNotes,
                    mealScanProvider, geminiApiKey, geminiModel,
                    groqMealApiKey, mistralApiKey,
                    llmProvider, llmApiKey, llmModel,
                    profilePhotoPath
                ) VALUES (
                    1, 'Sitou', '', 30, 'M', 178,
                    80.0, 75.0, 'INTERMEDIATE', 'FULL_GYM', 'SHRED',
                    90, NULL, '1,3,5',
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    NULL, NULL, NULL,
                    '', '',
                    0, 'auto', 'sunset', 0, 0,
                    1, 1, 1, 1,
                    90, 1, 0,
                    1, 1, 1, 1,
                    1, 1, 1, 1,
                    1, 1,
                    45, 30,
                    '08:00', '12:30', '16:00', '19:00',
                    '07:30', '22:00',
                    '',
                    'GEMINI', 'AIza-secret-gemini', 'gemini-2.5-flash',
                    'gsk-secret-groq', 'mistral-secret',
                    'GROQ', 'sk-secret-llm', '',
                    NULL
                )
                """.trimIndent()
            )
        }

        // ─── 2. Exécuter la migration v33 → v34 ───
        helper.runMigrationsAndValidate(
            testDbName,
            34,
            true,
            Migrations.migration33to34(context)
        ).use { db ->
            // ─── 3. Vérifier que les 4 colonnes API key n'existent plus ───
            db.query("PRAGMA table_info(user_profile)").use { cursor ->
                val columnNames = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columnNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertThat(columnNames).doesNotContain("llmApiKey")
                assertThat(columnNames).doesNotContain("geminiApiKey")
                assertThat(columnNames).doesNotContain("groqMealApiKey")
                assertThat(columnNames).doesNotContain("mistralApiKey")
            }

            // ─── 4. Vérifier que la ligne et ses valeurs métier sont préservées ───
            db.query("SELECT firstName, age, currentWeightKg, llmProvider, mealScanProvider FROM user_profile WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Sitou")
                assertThat(c.getInt(1)).isEqualTo(30)
                assertThat(c.getDouble(2)).isEqualTo(80.0)
                assertThat(c.getString(3)).isEqualTo("GROQ")
                assertThat(c.getString(4)).isEqualTo("GEMINI")
            }
        }

        // ─── 5. Vérifier que les clés en clair ont été migrées chiffrées ───
        val store = SecureKeyStore(context)
        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("sk-secret-llm")
        assertThat(store.getKey(SecureKeyStore.Provider.GEMINI)).isEqualTo("AIza-secret-gemini")
        assertThat(store.getKey(SecureKeyStore.Provider.GROQ_MEAL)).isEqualTo("gsk-secret-groq")
        assertThat(store.getKey(SecureKeyStore.Provider.MISTRAL)).isEqualTo("mistral-secret")
    }

    @Test
    fun migrate33to34_idempotent_ne_ecrase_pas_une_cle_deja_presente_dans_secure_store() {
        // Une clé est DÉJÀ dans SecureKeyStore (cas où l'utilisateur l'a re-saisie
        // après Phase C avant que la migration v33→v34 ne s'exécute).
        SecureKeyStore(context).setKey(SecureKeyStore.Provider.LLM, "user-resaisi-key")

        helper.createDatabase(testDbName, 33).use { db ->
            db.execSQL(
                """
                INSERT INTO user_profile (
                    id, firstName, lastName, age, sex, heightCm,
                    currentWeightKg, targetWeightKg, level, equipment, goal,
                    preferredWorkoutDuration, bedTime, workoutDays,
                    waistCm, chestCm, armCm, thighCm, hipCm, calfCm, bodyFatPercent,
                    bodyScanImagePath, bodyMeshImagePath, bodyScanTimestamp,
                    bodyScanConfidence, bodyScanNotes,
                    useImperial, darkMode, themePalette, currentStreakDays, totalWorkouts,
                    autoStartAfterRest, vibrationEnabled, soundEnabled, voiceEnabled,
                    defaultRestSeconds, showCoachTips, suggestBonusSeries,
                    notificationsEnabled, notifBreakfast, notifLunch, notifSnack,
                    notifDinner, notifShaker, notifBedtime, notifMotivation,
                    notifMealDebrief, notifWorkoutDebrief,
                    mealDebriefDelayMinutes, workoutDebriefDelayMinutes,
                    breakfastTime, lunchTime, snackTime, dinnerTime,
                    shakerMorningTime, shakerEveningTime,
                    healthNotes,
                    mealScanProvider, geminiApiKey, geminiModel,
                    groqMealApiKey, mistralApiKey,
                    llmProvider, llmApiKey, llmModel,
                    profilePhotoPath
                ) VALUES (
                    1, 'Sitou', '', 30, 'M', 178,
                    80.0, 75.0, 'INTERMEDIATE', 'FULL_GYM', 'SHRED',
                    90, NULL, '1,3,5',
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    NULL, NULL, NULL, '', '',
                    0, 'auto', 'sunset', 0, 0,
                    1, 1, 1, 1, 90, 1, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    45, 30,
                    '08:00', '12:30', '16:00', '19:00', '07:30', '22:00',
                    '',
                    'GEMINI', '', 'gemini-2.5-flash', '', '',
                    'GROQ', 'sk-different-old-key', '',
                    NULL
                )
                """.trimIndent()
            )
        }

        helper.runMigrationsAndValidate(testDbName, 34, true, Migrations.migration33to34(context)).close()

        // La clé déjà présente dans SecureKeyStore n'a pas été écrasée par
        // la valeur qui était en clair dans Room (idempotence).
        val store = SecureKeyStore(context)
        assertThat(store.getKey(SecureKeyStore.Provider.LLM)).isEqualTo("user-resaisi-key")
    }

    @Test
    fun migrate33to34_pas_de_user_profile_ne_crashe_pas() {
        // Cas limite : DB v33 sans aucune ligne user_profile (utilisateur fraîchement
        // installé qui n'a jamais ouvert l'app). La migration doit passer sans crash.
        helper.createDatabase(testDbName, 33).close()

        helper.runMigrationsAndValidate(testDbName, 34, true, Migrations.migration33to34(context)).use { db ->
            db.query("SELECT COUNT(*) FROM user_profile").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
        }
    }

    /**
     * v36 → v37 : ajout de `routineId` sur 3 tables + `lastUsedRoutineId` sur
     * `user_profile` + 2 indexes. Le test valide :
     *  - Le schéma final matche `schemas/37.json` (validation Room interne)
     *  - Les colonnes existent sur les 4 tables
     *  - Les valeurs pré-existantes héritent du DEFAULT 'full_body' (backfill)
     *  - Les indexes routineId sont créés (sinon les queries stats par routine
     *    feraient un table-scan)
     *
     * Pourquoi tester via SQL brut (pas DAOs) : à v36 les DAOs Kotlin attendent
     * déjà la structure v37, ils ne peuvent pas opérer sur la DB pré-migration.
     */
    @Test
    fun migrate36to37_ajoute_routineId_avec_default_full_body() {
        // ─── 1. Créer DB v36 avec une séance terminée + un workout + une planif ───
        helper.createDatabase(testDbName, 36).use { db ->
            // Workout existant (template)
            db.execSQL(
                """
                INSERT INTO workouts (
                    id, name, durationMinutes, exerciseCount, createdAt,
                    isTemplate, isFavorite, isCustom, isFreestyle
                ) VALUES (
                    42, 'Mon FB perso', 90, 8, '2026-01-15T10:00:00',
                    1, 1, 0, 0
                )
                """.trimIndent()
            )
            // WorkoutLog associé (séance terminée pré-v37)
            db.execSQL(
                """
                INSERT INTO workout_logs (
                    id, workoutId, date, durationMinutes, actualDurationSeconds,
                    startTime, endTime, totalVolume, totalSets, totalReps,
                    totalRestSeconds, exercisesCompleted, exercisesSkipped, completed,
                    currentSetTimedTotalSeconds, currentRestTotalSeconds, extraSeriesJson
                ) VALUES (
                    100, 42, '2026-01-15T10:00:00', 90, 5400,
                    '2026-01-15T10:00:00', '2026-01-15T11:30:00', 5000.0, 30, 240,
                    1500, 8, 0, 1,
                    0, 0, '{}'
                )
                """.trimIndent()
            )
            // Séance planifiée future
            db.execSQL(
                """
                INSERT INTO scheduled_workouts (
                    id, date, time, workoutId, status, title, note,
                    reminderShakerSent, reminderStartSent, source, createdAt
                ) VALUES (
                    7, '2026-02-01', '18:00', 42, 'PLANNED', 'Séance FB jeudi', '',
                    0, 0, 'manual', '2026-01-30T20:00:00'
                )
                """.trimIndent()
            )
            // user_profile minimal (les autres champs ont des défauts via Room)
            db.execSQL(
                """
                INSERT INTO user_profile (
                    id, firstName, lastName, age, sex, heightCm,
                    currentWeightKg, targetWeightKg, level, equipment, goal,
                    preferredWorkoutDuration, bedTime, workoutDays,
                    waistCm, chestCm, armCm, thighCm, hipCm, calfCm, bodyFatPercent,
                    bodyScanImagePath, bodyMeshImagePath, bodyScanTimestamp,
                    bodyScanConfidence, bodyScanNotes,
                    useImperial, darkMode, themePalette, currentStreakDays, totalWorkouts,
                    autoStartAfterRest, vibrationEnabled, soundEnabled, voiceEnabled,
                    defaultRestSeconds, showCoachTips, suggestBonusSeries,
                    notificationsEnabled, notifBreakfast, notifLunch, notifSnack,
                    notifDinner, notifShaker, notifBedtime, notifMotivation,
                    notifMealDebrief, notifWorkoutDebrief,
                    mealDebriefDelayMinutes, workoutDebriefDelayMinutes,
                    breakfastTime, lunchTime, snackTime, dinnerTime,
                    shakerMorningTime, shakerEveningTime,
                    healthNotes, mealScanProvider, geminiModel,
                    llmProvider, llmModel, profilePhotoPath
                ) VALUES (
                    1, 'Sitou', '', 30, 'M', 178,
                    80.0, 75.0, 'INTERMEDIATE', 'FULL_GYM', 'SHRED',
                    90, NULL, '1,3,5',
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    NULL, NULL, NULL, '', '',
                    0, 'auto', 'sunset', 0, 0,
                    1, 1, 1, 1, 90, 1, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                    45, 30,
                    '08:00', '12:30', '16:00', '19:00', '07:30', '22:00',
                    '', 'GEMINI', 'gemini-2.5-flash', 'GROQ', '', NULL
                )
                """.trimIndent()
            )
        }

        // ─── 2. Exécuter la migration v36 → v37 ───
        helper.runMigrationsAndValidate(
            testDbName,
            37,
            true,
            Migrations.migration36to37()
        ).use { db ->
            // ─── 3. Vérifier que routineId existe sur les 3 tables et vaut 'full_body' ───
            db.query("SELECT routineId FROM workouts WHERE id = 42").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("full_body")
            }
            db.query("SELECT routineId FROM workout_logs WHERE id = 100").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("full_body")
            }
            db.query("SELECT routineId FROM scheduled_workouts WHERE id = 7").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("full_body")
            }
            db.query("SELECT lastUsedRoutineId FROM user_profile WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("full_body")
            }

            // ─── 4. Vérifier que les indexes routineId sont en place ───
            // (sinon les queries stats par routine sur 100k+ logs feraient table-scan)
            val indexNames = mutableListOf<String>()
            db.query("PRAGMA index_list('workout_logs')").use { c ->
                while (c.moveToNext()) indexNames.add(c.getString(c.getColumnIndexOrThrow("name")))
            }
            assertThat(indexNames).contains("index_workout_logs_routineId")

            val schedIndexes = mutableListOf<String>()
            db.query("PRAGMA index_list('scheduled_workouts')").use { c ->
                while (c.moveToNext()) schedIndexes.add(c.getString(c.getColumnIndexOrThrow("name")))
            }
            assertThat(schedIndexes).contains("index_scheduled_workouts_routineId")

            // ─── 5. Vérifier que les valeurs pré-existantes (firstName etc.) sont intactes ───
            db.query("SELECT firstName, currentWeightKg FROM user_profile WHERE id = 1").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Sitou")
                assertThat(c.getDouble(1)).isEqualTo(80.0)
            }
        }
    }

    /**
     * Cas limite : DB v36 vide (utilisateur fraîchement installé). La migration
     * doit passer sans crash et créer les colonnes/indexes.
     */
    @Test
    fun migrate36to37_db_vide_ne_crashe_pas() {
        helper.createDatabase(testDbName, 36).close()
        helper.runMigrationsAndValidate(testDbName, 37, true, Migrations.migration36to37()).use { db ->
            // Tables et indexes créés malgré 0 ligne
            db.query("SELECT COUNT(*) FROM workouts").use { c ->
                c.moveToFirst()
                assertThat(c.getInt(0)).isEqualTo(0)
            }
            db.query("PRAGMA table_info(workouts)").use { c ->
                val columns = mutableListOf<String>()
                while (c.moveToNext()) columns.add(c.getString(c.getColumnIndexOrThrow("name")))
                assertThat(columns).contains("routineId")
            }
        }
    }
}
