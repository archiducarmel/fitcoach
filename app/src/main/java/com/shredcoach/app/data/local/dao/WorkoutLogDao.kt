package com.shredcoach.app.data.local.dao


import androidx.compose.runtime.Immutable
import androidx.room.*
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

data class SetWithDate(
    val exerciseId: Long,
    val workoutLogId: Long,
    val weightKg: Double,
    val reps: Int,
    val setNumber: Int,
    val date: LocalDateTime
)

@Immutable
data class DailyVolume(
    val day: String,
    val volume: Double
)

@Immutable
data class DailyCount(
    val day: String,
    val count: Int
)

@Immutable
data class PersonalRecord(
    val exerciseId: Long,
    val maxWeight: Double,
    val reps: Int
)

@Immutable
data class MuscleGroupSets(
    val muscleGroup: String,
    val setCount: Int
)

@Dao
interface WorkoutLogDao {
    @Query("SELECT * FROM workout_logs ORDER BY date DESC")
    fun getAllWorkoutLogs(): Flow<List<WorkoutLogEntity>>

    @Query("SELECT * FROM workout_logs WHERE id = :id")
    suspend fun getWorkoutLogById(id: Long): WorkoutLogEntity?

    @Query("SELECT * FROM workout_logs WHERE date(date) >= :startDate AND date(date) <= :endDate ORDER BY date DESC")
    fun getWorkoutLogsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<WorkoutLogEntity>>

    /**
     * Snapshot des séances effectuées sur une date (utilisé par le calcul
     * adaptatif des calories nutrition : on lit l'activité RÉELLE pour
     * ajuster la cible quotidienne, pas le calendrier prévu).
     * Filtre `completed = 1` pour exclure les séances abandonnées.
     */
    @Query("SELECT * FROM workout_logs WHERE date(date) = date(:date) AND completed = 1 ORDER BY date ASC")
    suspend fun getCompletedLogsOnDateOnce(date: LocalDate): List<WorkoutLogEntity>

    @Query("SELECT * FROM workout_logs ORDER BY date DESC LIMIT :limit")
    fun getRecentWorkoutLogs(limit: Int): Flow<List<WorkoutLogEntity>>

    /**
     * Dernière séance non terminée — utilisée pour proposer "Reprendre" sur la home.
     * Le filtre <24h est appliqué côté ViewModel pour éviter de remonter des sessions
     * abandonnées (cf. règle auto-clean au-delà de 24h).
     */
    @Query("SELECT * FROM workout_logs WHERE completed = 0 ORDER BY date DESC LIMIT 1")
    fun observeLatestUncompletedLog(): Flow<WorkoutLogEntity?>

    /** Nombre d'exercices distincts loggés dans une séance (= progression %). */
    @Query("SELECT COUNT(DISTINCT exerciseId) FROM workout_sets WHERE workoutLogId = :logId AND completed = 1")
    suspend fun getCompletedExerciseCount(logId: Long): Int

    /**
     * Met à jour le wall-clock de l'exo courant. Update partiel (vs `updateWorkoutLog`
     * qui réécrit toute la ligne) : appelé à chaque transition d'exo, donc on
     * minimise le coût I/O et on évite les races avec d'autres updates concurrents
     * de la même ligne (sets, durations).
     */
    @Query("UPDATE workout_logs SET currentExerciseStartedAt = :startedAt WHERE id = :logId")
    suspend fun updateCurrentExerciseStartedAt(logId: Long, startedAt: LocalDateTime?)

    /**
     * Met à jour l'état "série en cours" : startedAt + duration cible (timed sets).
     * Appelé sur `Démarrer la série` (set both) et sur fin/skip/redo de série
     * (clear both → null/0).
     */
    @Query("UPDATE workout_logs SET currentSetStartedAt = :startedAt, currentSetTimedTotalSeconds = :timedTotal WHERE id = :logId")
    suspend fun updateCurrentSetState(logId: Long, startedAt: LocalDateTime?, timedTotal: Int)

    /** Met à jour l'ancre wall-clock du décompte de repos (null = pas de repos). */
    @Query("UPDATE workout_logs SET currentRestEndsAt = :endsAt, currentRestTotalSeconds = :totalSec WHERE id = :logId")
    suspend fun updateCurrentRestState(logId: Long, endsAt: LocalDateTime?, totalSec: Int)

    /** Met à jour le JSON des séries bonus à la volée. */
    @Query("UPDATE workout_logs SET extraSeriesJson = :json WHERE id = :logId")
    suspend fun updateExtraSeriesJson(logId: Long, json: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutLog(log: WorkoutLogEntity): Long

    @Update
    suspend fun updateWorkoutLog(log: WorkoutLogEntity)

    @Delete
    suspend fun deleteWorkoutLog(log: WorkoutLogEntity)

    /** Snapshot global, utilisé par le moteur de backup. */
    @Query("SELECT * FROM workout_logs ORDER BY id ASC")
    suspend fun getAllWorkoutLogsOnce(): List<WorkoutLogEntity>

    // WorkoutSet operations
    @Query("SELECT * FROM workout_sets WHERE workoutLogId = :workoutLogId ORDER BY exerciseId, setNumber ASC")
    suspend fun getWorkoutSets(workoutLogId: Long): List<WorkoutSetEntity>

    /** Snapshot global de toutes les séries, utilisé par le backup. */
    @Query("SELECT * FROM workout_sets ORDER BY workoutLogId ASC, exerciseId ASC, setNumber ASC")
    suspend fun getAllWorkoutSetsOnce(): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE workoutLogId = :workoutLogId AND exerciseId = :exerciseId ORDER BY setNumber ASC")
    suspend fun getWorkoutSetsByExercise(workoutLogId: Long, exerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId ORDER BY workoutLogId DESC, setNumber ASC LIMIT 10")
    suspend fun getRecentSetsForExercise(exerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT MAX(weightKg) FROM workout_sets WHERE exerciseId = :exerciseId AND completed = 1 AND weightKg > 0")
    suspend fun getMaxWeightForExercise(exerciseId: Long): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSet(set: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateWorkoutSet(set: WorkoutSetEntity)

    @Delete
    suspend fun deleteWorkoutSet(set: WorkoutSetEntity)

    // Statistics queries
    @Query("""
        SELECT SUM(totalVolume)
        FROM workout_logs
        WHERE date(date) >= :startDate AND date(date) <= :endDate AND completed = 1
    """)
    suspend fun getTotalVolumeInPeriod(startDate: LocalDate, endDate: LocalDate): Double?

    @Query("""
        SELECT COUNT(*)
        FROM workout_logs
        WHERE date(date) >= :startDate AND date(date) <= :endDate AND completed = 1
    """)
    suspend fun getWorkoutCountInPeriod(startDate: LocalDate, endDate: LocalDate): Int

    // ── Stats Dashboard queries ──

    @Query("""
        SELECT ws.exerciseId, ws.workoutLogId, ws.weightKg, ws.reps, ws.setNumber, wl.date
        FROM workout_sets ws
        INNER JOIN workout_logs wl ON ws.workoutLogId = wl.id
        WHERE ws.exerciseId = :exerciseId AND ws.completed = 1 AND wl.completed = 1
        ORDER BY wl.date ASC, ws.setNumber ASC
    """)
    suspend fun getWeightProgressionForExercise(exerciseId: Long): List<SetWithDate>

    @Query("""
        SELECT date(wl.date) as day, COALESCE(SUM(wl.totalVolume), 0.0) as volume
        FROM workout_logs wl
        WHERE wl.completed = 1 AND date(wl.date) >= :startDate
        GROUP BY date(wl.date)
        ORDER BY day ASC
    """)
    suspend fun getDailyVolume(startDate: LocalDate): List<DailyVolume>

    @Query("""
        SELECT date(wl.date) as day, COUNT(*) as count
        FROM workout_logs wl
        WHERE wl.completed = 1 AND date(wl.date) >= :startDate
        GROUP BY date(wl.date)
        ORDER BY day ASC
    """)
    suspend fun getTrainingFrequency(startDate: LocalDate): List<DailyCount>

    @Query("""
        SELECT ws.exerciseId, ws.weightKg as maxWeight, ws.reps
        FROM workout_sets ws
        WHERE ws.completed = 1 AND ws.weightKg > 0
        AND ws.weightKg = (SELECT MAX(ws2.weightKg) FROM workout_sets ws2 WHERE ws2.exerciseId = ws.exerciseId AND ws2.completed = 1)
        GROUP BY ws.exerciseId
        ORDER BY maxWeight DESC
    """)
    suspend fun getPersonalRecords(): List<PersonalRecord>

    @Query("""
        SELECT e.muscleGroup, COUNT(ws.id) as setCount
        FROM workout_sets ws
        INNER JOIN exercises e ON ws.exerciseId = e.id
        INNER JOIN workout_logs wl ON ws.workoutLogId = wl.id
        WHERE wl.completed = 1 AND date(wl.date) >= :startDate
        GROUP BY e.muscleGroup
        ORDER BY setCount DESC
    """)
    suspend fun getMuscleGroupDistribution(startDate: LocalDate): List<MuscleGroupSets>

    @Query("SELECT COUNT(*) FROM workout_logs WHERE completed = 1")
    suspend fun getTotalWorkoutCount(): Int

    @Query("SELECT SUM(totalVolume) FROM workout_logs WHERE completed = 1")
    suspend fun getTotalVolumeAllTime(): Double?

    @Query("SELECT SUM(actualDurationSeconds) FROM workout_logs WHERE completed = 1")
    suspend fun getTotalDurationAllTime(): Long?

    @Query("SELECT SUM(totalReps) FROM workout_logs WHERE completed = 1")
    suspend fun getTotalRepsAllTime(): Int?

    /** Somme des durees (en secondes) par groupe musculaire. Utilise exerciseDurationSeconds du dernier set de chaque exercice. */
    @Query("""
        SELECT e.muscleGroup as muscleGroup, COALESCE(SUM(ws.exerciseDurationSeconds), 0) as totalSeconds
        FROM workout_sets ws
        INNER JOIN exercises e ON ws.exerciseId = e.id
        INNER JOIN workout_logs wl ON ws.workoutLogId = wl.id
        WHERE wl.completed = 1 AND ws.exerciseDurationSeconds IS NOT NULL
        GROUP BY e.muscleGroup
    """)
    suspend fun getDurationByMuscleGroup(): List<MuscleGroupDuration>
}

@Immutable
data class MuscleGroupDuration(
    val muscleGroup: String,
    val totalSeconds: Long
)
