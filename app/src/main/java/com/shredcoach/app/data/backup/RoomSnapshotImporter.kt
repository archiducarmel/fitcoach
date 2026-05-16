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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restaure un [TableSnapshot] dans la DB Room.
 *
 * **Garantie atomique** : toute l'opération (purge + repopulation) tourne
 * dans une seule [withTransaction]. Si **n'importe quelle** étape échoue,
 * ROLLBACK → la DB conserve son état d'avant le restore. C'est le cœur
 * de la promesse "ne pas perdre les données" : on ne peut pas se retrouver
 * dans un état intermédiaire (ex : tables vidées sans repopulation).
 *
 * **Stratégie FK** : on active `PRAGMA defer_foreign_keys = ON` au début
 * de la transaction. Effet : les FK sont vérifiées **uniquement au commit**,
 * pas à chaque INSERT. Ça nous libère de la contrainte d'ordre d'insertion
 * et permet de DELETE toutes les tables en désordre. À la fin, si le snapshot
 * est globalement cohérent (toutes les FK référencent des PK existantes),
 * le commit passe ; sinon il échoue → ROLLBACK.
 *
 * **Reset autoincrement** : SQLite met à jour `sqlite_sequence` au fur et à
 * mesure des INSERT explicites — un INSERT avec id=42 fait avancer la séquence
 * à 42. Pas de collision possible avec de futurs inserts auto-générés.
 *
 * **Échec de validation amont** : si [TableSnapshot] est null/incohérent,
 * c'est au [BackupRepository] de filtrer en amont (vérification des versions
 * dans le manifest). Cet importer fait confiance au snapshot reçu.
 */
@Singleton
class RoomSnapshotImporter @Inject constructor(
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
    private val glucoseDao: com.shredcoach.app.data.local.dao.GlucoseDao,
) {
    suspend fun import(snapshot: TableSnapshot) {
        db.withTransaction {
            val sqlite = db.openHelper.writableDatabase
            // FKs différées → ordre d'insertion libre, vérification au commit.
            sqlite.execSQL("PRAGMA defer_foreign_keys = ON")

            // 1) Purge — on vide toutes les tables. DELETE FROM (vs DROP)
            //    préserve le schéma et les indexes ; SQLite optimise en
            //    "truncate" si la WHERE est absente.
            ShredCoachDatabase.ALL_TABLES.forEach { table -> sqlite.execSQL("DELETE FROM $table") }

            // 2) Repopulation — ordre parents → enfants. Pas strictement
            //    nécessaire avec defer_foreign_keys, mais c'est plus
            //    diagnostique : si un commit échoue, savoir que les enfants
            //    sont insérés en dernier aide à pinpointer la table coupable.
            snapshot.exercises.forEach { exerciseDao.insertExercise(it) }
            snapshot.workouts.forEach { workoutDao.insertWorkout(it) }
            snapshot.workoutExercises.forEach { workoutDao.insertWorkoutExercise(it) }
            snapshot.workoutLogs.forEach { workoutLogDao.insertWorkoutLog(it) }
            snapshot.workoutSets.forEach { workoutLogDao.insertWorkoutSet(it) }
            snapshot.userProfile?.let { userProfileDao.insertUserProfile(it) }
            snapshot.nutritionSchedules.forEach { nutritionDao.insertSchedule(it) }
            snapshot.foods.forEach { nutritionDao.insertFood(it) }
            snapshot.mealScans.forEach { mealScanDao.insertScan(it) }
            snapshot.mealLogs.forEach { nutritionDao.insertMealLog(it) }
            snapshot.nutritionGoal?.let { nutritionDao.insertNutritionGoal(it) }
            snapshot.dailyChecks.forEach { nutritionDao.insertDailyCheck(it) }
            snapshot.weightLogs.forEach { userProfileDao.insertWeightLog(it) }
            snapshot.progressPhotos.forEach { userProfileDao.insertPhoto(it) }
            snapshot.chatMessages.forEach { chatDao.insertMessage(it) }
            snapshot.appNotifications.forEach { appNotificationDao.insert(it) }
            snapshot.scheduledWorkouts.forEach { scheduledWorkoutDao.insert(it) }
            snapshot.bodyScanLogs.forEach { bodyScanLogDao.insert(it) }
            // CGM (v44+) — pas de FK, restauration directe.
            snapshot.glucoseLogs.forEach { glucoseDao.upsert(it) }

            // 3) Le commit (implicite à la sortie de withTransaction) déclenche
            //    la vérification globale des FK. Si une référence est cassée,
            //    SQLiteConstraintException → rollback automatique.
        }
    }
}
