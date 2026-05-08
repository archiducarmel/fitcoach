package com.shredcoach.app.data.backup

import androidx.room.withTransaction
import com.shredcoach.app.data.local.ShredCoachDatabase
import com.shredcoach.app.data.local.dao.AppNotificationDao
import com.shredcoach.app.data.local.dao.ChatDao
import com.shredcoach.app.data.local.dao.ExerciseDao
import com.shredcoach.app.data.local.dao.MealScanDao
import com.shredcoach.app.data.local.dao.NutritionDao
import com.shredcoach.app.data.local.dao.ScheduledWorkoutDao
import com.shredcoach.app.data.local.dao.UserProfileDao
import com.shredcoach.app.data.local.dao.WorkoutDao
import com.shredcoach.app.data.local.dao.WorkoutLogDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lit toutes les tables Room et produit un [TableSnapshot] in-memory.
 *
 * **Cohérence transactionnelle** : tous les reads sont enveloppés dans
 * [withTransaction]. Room/SQLite garantit alors un read-snapshot cohérent —
 * les writes concurrents ne peuvent pas s'intercaler entre deux DAOs lus.
 * C'est important pour éviter le scénario "on lit foods à T0, on lit
 * meal_logs à T1, entre les deux un meal_log a été inséré référençant un
 * food créé après notre read foods → restore plus tard, FK orpheline".
 *
 * **Ne lit PAS les fichiers photo** — c'est le rôle de `PhotoArchiver`.
 * Cette séparation permet de tester l'export DB sans avoir à mocker le
 * filesystem.
 */
@Singleton
class RoomSnapshotExporter @Inject constructor(
    private val db: ShredCoachDatabase,
    private val exerciseDao: ExerciseDao,
    private val workoutDao: WorkoutDao,
    private val workoutLogDao: WorkoutLogDao,
    private val userProfileDao: UserProfileDao,
    private val nutritionDao: NutritionDao,
    private val chatDao: ChatDao,
    private val mealScanDao: MealScanDao,
    private val appNotificationDao: AppNotificationDao,
    private val scheduledWorkoutDao: ScheduledWorkoutDao,
    private val bodyScanLogDao: com.shredcoach.app.data.local.dao.BodyScanLogDao,
) {
    /**
     * Lit toutes les tables et retourne un snapshot immuable. Les Flow-based
     * DAOs sont consommés via `.first()` — on prend la première émission et
     * on se débranche, sans laisser de subscription pendre.
     */
    suspend fun export(): TableSnapshot = db.withTransaction {
        TableSnapshot(
            exercises = exerciseDao.getAllExercises().first(),
            workouts = workoutDao.getAllWorkoutsOnce(),
            workoutExercises = workoutDao.getAllWorkoutExercisesOnce(),
            workoutLogs = workoutLogDao.getAllWorkoutLogsOnce(),
            workoutSets = workoutLogDao.getAllWorkoutSetsOnce(),
            userProfile = userProfileDao.getUserProfileOnce(),
            nutritionSchedules = nutritionDao.getAllSchedules().first(),
            foods = nutritionDao.getAllFoods().first(),
            mealScans = mealScanDao.getAllScans().first(),
            mealLogs = nutritionDao.getAllMealLogsOnce(),
            nutritionGoal = nutritionDao.getNutritionGoalOnce(),
            dailyChecks = nutritionDao.getAllDailyChecksOnce(),
            weightLogs = userProfileDao.getAllWeightLogs().first(),
            progressPhotos = userProfileDao.getAllPhotos().first(),
            chatMessages = chatDao.getAllMessagesOnce(),
            appNotifications = appNotificationDao.getAll().first(),
            scheduledWorkouts = scheduledWorkoutDao.getAll().first(),
            bodyScanLogs = bodyScanLogDao.getAllOnce(),
        )
    }
}
