package com.shredcoach.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shredcoach.app.data.local.Migrations
import com.shredcoach.app.data.local.ShredCoachDatabase
import com.shredcoach.app.data.local.dao.*
import com.shredcoach.app.data.seed.SeedData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        exerciseDaoProvider: Provider<ExerciseDao>
    ): ShredCoachDatabase {
        val callback = object : RoomDatabase.Callback() {
            /**
             * Sync UPSERT du catalogue d'exos par `name` :
             *  - Si un exo de [SeedData] existe en DB (match par name), on
             *    UPDATE ses champs en conservant son `id` (préserve les FK
             *    de `workout_sets` qui réfèrent les exos par id).
             *  - Sinon, INSERT avec id=0 (auto-generated par Room).
             *
             * **Pourquoi UPSERT (et pas seulement INSERT-if-missing)** :
             * quand SeedData évolue entre versions (correction de gifUrl,
             * d'executionKey, de tips, etc.), les utilisateurs avec une DB
             * existante doivent voir les corrections. Sans UPDATE, l'exo
             * « Squat barre » resterait à jamais figé sur les valeurs de la
             * première install — par exemple, après la migration GIFs locaux
             * → GitHub Releases (commit cf3855d), tous les v1 gardaient leurs
             * URLs `file:///android_asset/...` pointant vers des assets
             * supprimés → images cassées en prod. UPSERT corrige cela.
             *
             * **Pourquoi pas truncate + re-seed** : les `workout_sets` et
             * `workout_exercises` ont des FK vers exercise.id (auto-generated).
             * Truncate regénère des id différents → FK orphelins → historique
             * de séances perdu. UPSERT par name préserve les id stables.
             *
             * **Coût** : 1 SELECT * FROM exercises + 1 batch UPDATE de N
             * existants + 1 batch INSERT de M nouveaux par launch (~50ms en
             * IO async, n'impacte pas le temps de démarrage UI).
             */
            private fun seedDatabaseIdempotent() {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val exerciseDao = exerciseDaoProvider.get()
                    // Match prioritaire par `exerciseKey` (stable inter-langue,
                    // backfillé par la migration v38→v39). Fallback par `name`
                    // pour défendre contre les rows hypothétiques sans clé.
                    val existing = exerciseDao.getAllExerciseIdsByKey()
                    val byKey = existing.filter { it.exerciseKey.isNotBlank() }
                        .associate { it.exerciseKey to it.id }
                    val byName = existing.associate { it.name to it.id }

                    val toInsert = mutableListOf<com.shredcoach.app.data.local.entity.ExerciseEntity>()
                    val toUpdate = mutableListOf<com.shredcoach.app.data.local.entity.ExerciseEntity>()
                    for (seed in SeedData.getAllExercises()) {
                        val existingId = byKey[seed.exerciseKey] ?: byName[seed.name]
                        if (existingId == null) {
                            toInsert.add(seed)
                        } else {
                            // Conserve l'id existant pour préserver les FK.
                            toUpdate.add(seed.copy(id = existingId))
                        }
                    }

                    if (toInsert.isNotEmpty()) exerciseDao.insertExercises(toInsert)
                    if (toUpdate.isNotEmpty()) exerciseDao.updateExercises(toUpdate)
                }
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDatabaseIdempotent()
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Re-sync à chaque ouverture pour propager l'enrichissement de
                // SeedData aux utilisateurs qui ont déjà créé leur DB sur une
                // version précédente. Idempotent → no-op si tout est à jour.
                seedDatabaseIdempotent()
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                seedDatabaseIdempotent()
            }
        }

        return Room.databaseBuilder(
            context,
            ShredCoachDatabase::class.java,
            ShredCoachDatabase.DATABASE_NAME
        )
            // Migrations explicites — toute évolution de schéma à partir
            // de v33 doit ajouter une Migration ici (cf. Migrations.kt).
            .addMigrations(
                Migrations.migration33to34(context),
                Migrations.migration34to35(),
                Migrations.migration35to36(),
                Migrations.migration36to37(),
                Migrations.migration37to38(),
                Migrations.migration38to39(),
                Migrations.migration39to40(),
                Migrations.migration40to41(),
                Migrations.migration41to42(),
                Migrations.migration42to43(),
                Migrations.migration43to44(),
                Migrations.migration44to45(),
                Migrations.migration45to46(),
                Migrations.migration46to47(),
                Migrations.migration47to48(),
                Migrations.migration48to49(),
                Migrations.migration49to50(),
            )
            // Fallback uniquement en cas de **downgrade** (ex : utilisateur
            // sideload une version plus ancienne). Aucun fallback destructif
            // sur les forward migrations — on ne perd jamais les données
            // utilisateur en mettant à jour l'app.
            .fallbackToDestructiveMigrationOnDowngrade()
            .addCallback(callback)
            .build()
    }

    @Provides
    @Singleton
    fun provideExerciseDao(database: ShredCoachDatabase): ExerciseDao =
        database.exerciseDao()

    @Provides
    @Singleton
    fun provideWorkoutDao(database: ShredCoachDatabase): WorkoutDao =
        database.workoutDao()

    @Provides
    @Singleton
    fun provideWorkoutLogDao(database: ShredCoachDatabase): WorkoutLogDao =
        database.workoutLogDao()

    @Provides
    @Singleton
    fun provideUserProfileDao(database: ShredCoachDatabase): UserProfileDao =
        database.userProfileDao()

    @Provides
    @Singleton
    fun provideNutritionDao(database: ShredCoachDatabase): NutritionDao =
        database.nutritionDao()

    @Provides
    @Singleton
    fun provideChatDao(database: ShredCoachDatabase): ChatDao =
        database.chatDao()

    @Provides
    @Singleton
    fun provideMealScanDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.MealScanDao =
        database.mealScanDao()

    @Provides
    @Singleton
    fun provideAppNotificationDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.AppNotificationDao =
        database.appNotificationDao()

    @Provides
    @Singleton
    fun provideScheduledWorkoutDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.ScheduledWorkoutDao =
        database.scheduledWorkoutDao()

    @Provides
    @Singleton
    fun provideBodyScanLogDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.BodyScanLogDao =
        database.bodyScanLogDao()

    @Provides
    @Singleton
    fun provideGlucoseDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.GlucoseDao =
        database.glucoseDao()

    @Provides
    @Singleton
    fun provideGlucoseAnalysisDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.GlucoseAnalysisDao =
        database.glucoseAnalysisDao()

    @Provides
    @Singleton
    fun provideLlmUsageDao(database: ShredCoachDatabase): com.shredcoach.app.data.local.dao.LlmUsageDao =
        database.llmUsageDao()
}
