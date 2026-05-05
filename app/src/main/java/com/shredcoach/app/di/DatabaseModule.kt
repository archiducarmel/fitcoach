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
             * Sync idempotente du catalogue d'exos : insère uniquement les
             * entrées de [SeedData] dont le `name` n'est pas déjà en DB.
             *
             * **Pourquoi pas un truncate-then-insert** : les `workout_sets` et
             * `workout_exercises` réfèrent les exos par `id` (auto-generated).
             * Truncate + re-seed regénère des id différents → FK orphelins,
             * historique de séances cassé. La diff par `name` préserve les id
             * existants.
             *
             * **Cas d'usage** :
             *  - `onCreate` (premier launch après install) : DB vide, tout
             *    SeedData s'insère.
             *  - `onOpen` (chaque launch ensuite) : si SeedData a grossi entre
             *    versions de l'app (ex: 170 → 440 exos), les nouveaux noms
             *    s'insèrent sans toucher aux 170 anciens. Coût d'un launch
             *    quand le catalogue est à jour : 1 SELECT name FROM exercises
             *    + comparaison Set, ~5ms.
             */
            private fun seedDatabaseIdempotent() {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val exerciseDao = exerciseDaoProvider.get()
                    val existingNames = exerciseDao.getAllExerciseNames().toHashSet()
                    val missing = SeedData.getAllExercises()
                        .filter { it.name !in existingNames }
                    if (missing.isNotEmpty()) {
                        exerciseDao.insertExercises(missing)
                    }
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
            .addMigrations(Migrations.migration33to34(context))
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
}
