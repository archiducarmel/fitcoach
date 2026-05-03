package com.shredcoach.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shredcoach.app.data.local.converter.Converters
import com.shredcoach.app.data.local.dao.*
import com.shredcoach.app.data.local.entity.*

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
        ScheduledWorkoutEntity::class
    ],
    version = 34,
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

    companion object {
        const val DATABASE_NAME = "shredcoach_db"
    }
}
