package com.shredcoach.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shredcoach.app.data.local.converter.Converters
import com.shredcoach.app.data.local.dao.*
import com.shredcoach.app.data.local.entity.*
import com.shredcoach.app.data.local.entity.BodyScanLogEntity

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutLogEntity::class,
        WorkoutSetEntity::class,
        UserProfileEntity::class,
        NutritionScheduleEntity::class,
        DailyCheckEntity::class,
        FoodEntity::class,
        MealScanEntity::class,
        MealLogEntity::class,
        NutritionGoalEntity::class,
        WeightLogEntity::class,
        ProgressPhotoEntity::class,
        ChatMessageEntity::class,
        AppNotificationEntity::class,
        ScheduledWorkoutEntity::class,
        BodyScanLogEntity::class,
        GlucoseLogEntity::class
    ],
    version = 44,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ShredCoachDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun chatDao(): ChatDao
    abstract fun mealScanDao(): com.shredcoach.app.data.local.dao.MealScanDao
    abstract fun appNotificationDao(): com.shredcoach.app.data.local.dao.AppNotificationDao
    abstract fun scheduledWorkoutDao(): com.shredcoach.app.data.local.dao.ScheduledWorkoutDao
    abstract fun bodyScanLogDao(): com.shredcoach.app.data.local.dao.BodyScanLogDao
    abstract fun glucoseDao(): com.shredcoach.app.data.local.dao.GlucoseDao

    companion object {
        const val DATABASE_NAME = "shredcoach_db"

        /**
         * Liste exhaustive des tables Room déclarées via [@Database.entities].
         * Source unique de vérité pour les opérations qui doivent boucler sur
         * toutes les tables (purge RGPD, restore backup). Ordonnée enfants → parents
         * pour minimiser les violations FK lors d'un DELETE séquentiel (utile en
         * cas de purge sans `defer_foreign_keys`).
         *
         * **À mettre à jour à chaque fois qu'une nouvelle entité est ajoutée à
         * [@Database.entities] ci-dessus.** Une divergence = données zombies après
         * restore ou purge incomplète.
         */
        val ALL_TABLES = listOf(
            "scheduled_workouts",     // FK → workouts, workout_logs
            "app_notifications",
            "chat_messages",
            "glucose_logs",            // pas de FK, 1 row par date
            "body_scan_logs",          // pas de FK, history-only
            "progress_photos",
            "weight_logs",
            "daily_checks",            // FK → nutrition_schedule
            "meal_logs",               // FK → foods, meal_scans
            "meal_scans",
            "foods",
            "nutrition_schedule",
            "nutrition_goals",
            "user_profile",
            "workout_sets",            // FK → workout_logs, exercises
            "workout_logs",
            "workout_exercises",       // FK → workouts, exercises
            "workouts",
            "exercises",
        )
    }
}
