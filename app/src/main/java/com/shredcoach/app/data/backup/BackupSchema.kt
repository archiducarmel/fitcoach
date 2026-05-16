package com.shredcoach.app.data.backup

import com.shredcoach.app.data.local.entity.AppNotificationEntity
import com.shredcoach.app.data.local.entity.BodyScanLogEntity
import com.shredcoach.app.data.local.entity.ChatMessageEntity
import com.shredcoach.app.data.local.entity.DailyCheckEntity
import com.shredcoach.app.data.local.entity.ExerciseEntity
import com.shredcoach.app.data.local.entity.FoodEntity
import com.shredcoach.app.data.local.entity.MealLogEntity
import com.shredcoach.app.data.local.entity.MealScanEntity
import com.shredcoach.app.data.local.entity.NutritionGoalEntity
import com.shredcoach.app.data.local.entity.NutritionScheduleEntity
import com.shredcoach.app.data.local.entity.ProgressPhotoEntity
import com.shredcoach.app.data.local.entity.ScheduledWorkoutEntity
import com.shredcoach.app.data.local.entity.UserProfileEntity
import com.shredcoach.app.data.local.entity.WeightLogEntity
import com.shredcoach.app.data.local.entity.WorkoutEntity
import com.shredcoach.app.data.local.entity.WorkoutExerciseEntity
import com.shredcoach.app.data.local.entity.WorkoutLogEntity
import com.shredcoach.app.data.local.entity.WorkoutSetEntity

/**
 * Schéma JSON d'un backup ShredCoach.
 *
 * Versionnage à deux étages :
 * - [BackupManifest.backupSchemaVersion] : version du **format** de ce fichier.
 *   Augmente quand on change la structure (ajout d'un top-level field, renommage
 *   d'une table, etc.). Permet à un futur ShredCoach de refuser un backup trop
 *   récent qu'il ne saurait pas lire.
 * - [BackupManifest.roomDbVersion] : version Room du schéma DB **au moment de
 *   l'export**. Permet de détecter les backups pris avec une version d'app
 *   différente et d'appliquer (ou non) des migrations.
 *
 * Politique d'import V2 (conservatrice) :
 * - `backupSchemaVersion > BACKUP_SCHEMA_VERSION` → REFUS (format inconnu).
 * - `roomDbVersion > current` → REFUS (entités peut-être absentes).
 * - `roomDbVersion <= current` → ACCEPT (lenient parsing : champs manquants
 *   prennent leur valeur par défaut Kotlin, champs en trop sont ignorés par Gson).
 *
 * Sécurité : ce JSON contient TOUTES les données utilisateur (poids, repas,
 * conversations IA, photos via [photos]). Ne jamais loguer son contenu —
 * uniquement les meta (versions, exportedAt, sizes). Si l'utilisateur active
 * le chiffrement, le manifest est chiffré au niveau de l'archive ZIP, pas du JSON.
 */
data class BackupManifest(
    val backupSchemaVersion: Int,
    val roomDbVersion: Int,
    val appVersionCode: Int,
    val appVersionName: String,
    val exportedAt: String,        // ISO-8601 UTC, ex : 2026-05-05T03:00:00Z
    val tables: TableSnapshot,
    val photos: List<PhotoEntry>,
) {
    companion object {
        /** Version du **format** d'archive. À incrémenter lors de changements structurels. */
        const val BACKUP_SCHEMA_VERSION = 1
    }
}

/**
 * Snapshot de toutes les tables Room. Une entrée par entité.
 *
 * - `userProfile` et `nutritionGoal` sont des tables single-row (PK=1) → champ
 *   nullable au lieu d'une List, pour refléter la sémantique.
 * - Les autres sont des `List<EntityX>` (potentiellement vides).
 *
 * **Ordre des champs = ordre d'insertion** lors du restore (voir
 * [RoomSnapshotImporter]) : on place les parents avant les enfants pour limiter
 * la pression sur les FK différées (ceinture + bretelles).
 */
data class TableSnapshot(
    val exercises: List<ExerciseEntity>,
    val workouts: List<WorkoutEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val workoutLogs: List<WorkoutLogEntity>,
    val workoutSets: List<WorkoutSetEntity>,
    val userProfile: UserProfileEntity?,
    val nutritionSchedules: List<NutritionScheduleEntity>,
    val foods: List<FoodEntity>,
    val mealScans: List<MealScanEntity>,
    val mealLogs: List<MealLogEntity>,
    val nutritionGoal: NutritionGoalEntity?,
    val dailyChecks: List<DailyCheckEntity>,
    val weightLogs: List<WeightLogEntity>,
    val progressPhotos: List<ProgressPhotoEntity>,
    val chatMessages: List<ChatMessageEntity>,
    val appNotifications: List<AppNotificationEntity>,
    val scheduledWorkouts: List<ScheduledWorkoutEntity>,
    /**
     * Historique des scans corporels (#16). Liste vide si l'utilisateur n'a
     * jamais scanné. Lenient parsing : si un backup ancien (pré-v41) est
     * restauré, ce champ sera absent du JSON et Gson l'initialisera à
     * emptyList par defaultValue.
     */
    val bodyScanLogs: List<BodyScanLogEntity> = emptyList(),
    /**
     * Historique CGM (v44+). Liste vide si l'utilisateur n'a jamais uploadé.
     * Backups pré-v44 ne contiennent pas ce champ → Gson l'initialise à
     * emptyList. La restoration ne créera pas de logs glucose si absents.
     */
    val glucoseLogs: List<com.shredcoach.app.data.local.entity.GlucoseLogEntity> = emptyList(),
)

/**
 * Référence vers une photo packée dans l'archive ZIP.
 *
 * - [originalPath] : chemin disque tel qu'il était stocké dans la DB au backup
 *   (ex: `/data/user/0/com.shredcoach.app/files/photos/123.jpg`). Inutilisable
 *   tel quel après restore (path-dépendant) → on le garde uniquement pour
 *   diagnostic.
 * - [archivePath] : chemin à l'intérieur du ZIP (ex: `photos/<uuid>.jpg`).
 *   C'est ce qu'on extrait, vers un nouveau filesDir au moment du restore.
 * - [sha256] : empreinte du fichier original. Vérifiée à l'import → si une
 *   photo est corrompue dans l'archive (transfert cloud foiré), on saute
 *   uniquement cette photo au lieu de planter tout le restore.
 */
data class PhotoEntry(
    val originalPath: String,
    val archivePath: String,
    val sha256: String,
    val sizeBytes: Long,
)
