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
            private fun seedDatabase() {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val exerciseDao = exerciseDaoProvider.get()
                    exerciseDao.deleteAllExercises()
                    exerciseDao.insertExercises(SeedData.getAllExercises())
                }
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDatabase()
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                seedDatabase()
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
