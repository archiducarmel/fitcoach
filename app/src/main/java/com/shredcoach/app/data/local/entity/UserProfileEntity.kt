package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "user_profile")
@Immutable
data class UserProfileEntity(
    @PrimaryKey
    val id: Long = 1,
    val firstName: String,
    val lastName: String = "",
    val age: Int = 30,
    val sex: String = "M", // M ou F
    val heightCm: Int = 178,
    val currentWeightKg: Double = 80.0,
    val targetWeightKg: Double = 75.0,
    val level: FitnessLevel = FitnessLevel.INTERMEDIATE,
    val equipment: EquipmentType = EquipmentType.FULL_GYM,
    val goal: FitnessGoal = FitnessGoal.SHRED,
    val preferredWorkoutDuration: Int = 90,
    val bedTime: LocalTime? = null,
    val workoutDays: Set<Int> = setOf(1, 3, 5),
    // Mesures corporelles (cm)
    val waistCm: Double = 0.0,
    val chestCm: Double = 0.0,
    val armCm: Double = 0.0,
    val thighCm: Double = 0.0,
    val hipCm: Double = 0.0,
    val calfCm: Double = 0.0,
    val bodyFatPercent: Double = 0.0, // Taux de gras corporel estimé (0 = non mesuré)
    // ── Body Scanner ──
    val bodyScanImagePath: String? = null,   // Chemin de la photo originale uploadée
    /**
     * @deprecated Legacy path d'un PNG mesh généré via Gemini Image Gen
     * (avant migration v40). Plus jamais écrit par le nouveau code. Reste
     * lisible pour ne pas casser un user qui aurait encore un mesh Gemini
     * stocké, mais l'UI de mesh est désormais 100% rendue depuis
     * [bodyMeshFeaturesPath]. À supprimer dans une future migration.
     */
    val bodyMeshImagePath: String? = null,
    /**
     * Chemin du fichier JSON contenant les `MeshFeatures` extraites
     * on-device (ML Kit Pose + Selfie Segmentation). Le mesh wireframe
     * néon est rendu en Compose Canvas depuis ce JSON, animé en temps
     * réel (scan-line, glow, pulse keypoints).
     */
    val bodyMeshFeaturesPath: String? = null,
    val bodyScanTimestamp: java.time.LocalDateTime? = null,
    val bodyScanConfidence: String = "",     // "low", "medium", "high"
    val bodyScanNotes: String = "",          // Note IA sur la qualité de l'estimation
    // Unités & display
    val useImperial: Boolean = false,
    val darkMode: String = "auto",
    val themePalette: String = "sunset", // sunset | ocean | forest | royal | graphite
    val currentStreakDays: Int = 0,
    val totalWorkouts: Int = 0,
    // ── Paramètres séance ──
    val autoStartAfterRest: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val voiceEnabled: Boolean = true, // Voix Shreddy (TTS) à la fin du repos
    val defaultRestSeconds: Int = 90,
    val showCoachTips: Boolean = true,
    val suggestBonusSeries: Boolean = false, // Proposer une série bonus à la fin de chaque exercice muscu
    // ── Notifications ──
    val notificationsEnabled: Boolean = true,
    val notifBreakfast: Boolean = true,
    val notifLunch: Boolean = true,
    val notifSnack: Boolean = true,
    val notifDinner: Boolean = true,
    val notifShaker: Boolean = true,
    val notifBedtime: Boolean = true,
    val notifMotivation: Boolean = true,
    // ── Débriefs IA ──
    val notifMealDebrief: Boolean = true,       // Activation débrief après repas
    val notifWorkoutDebrief: Boolean = true,    // Activation débrief après séance
    val mealDebriefDelayMinutes: Int = 45,      // Délai après scan repas
    val workoutDebriefDelayMinutes: Int = 30,   // Délai après fin de séance
    val breakfastTime: LocalTime = LocalTime.of(8, 0),
    val lunchTime: LocalTime = LocalTime.of(12, 30),
    val snackTime: LocalTime = LocalTime.of(16, 0),
    val dinnerTime: LocalTime = LocalTime.of(19, 0),
    val shakerMorningTime: LocalTime = LocalTime.of(7, 30),
    val shakerEveningTime: LocalTime = LocalTime.of(22, 0),
    // ── Santé / limitations ──
    val healthNotes: String = "", // "douleur épaule gauche", "tendinite genou"...
    // ── Meal Scanner ──
    val mealScanProvider: String = "GEMINI", // GEMINI, GROQ, MISTRAL
    val geminiModel: String = "gemini-2.5-flash",
    // ── Assistant IA ──
    val llmProvider: String = "GROQ", // GROQ, OPENAI, CLAUDE
    val llmModel: String = "", // vide = défaut du provider
    // Profile photo
    val profilePhotoPath: String? = null,
    // ── Routines / Splits (v37) ──
    /**
     * Dernier [com.shredcoach.app.domain.workout.WorkoutRoutine] utilisé par
     * l'utilisateur — sert de pré-sélection sur le RoutinePicker au prochain
     * lancement. Default `"full_body"` (rétro-compat).
     */
    val lastUsedRoutineId: String = "full_body",
    // ── i18n (v38) ──
    /**
     * Tag BCP-47 de la langue choisie (ex: `"fr"`, `"en"`, `"es"`). `null` ou
     * blank = pas encore choisi → l'auto-détection système s'applique au
     * premier launch et persiste silencieusement le choix détecté.
     *
     * **Pourquoi pas un enum** : un enum couplerait la migration DB à l'enum
     * Kotlin (suppression d'une langue = migration nécessaire). En `String`
     * on reste flexible — un id inconnu retombe sur [com.shredcoach.app.domain.locale.AppLocale.Default]
     * via [com.shredcoach.app.domain.locale.AppLocale.fromTag], jamais d'exception.
     */
    val languageTag: String? = null,
    // NOTE : les clés API (llmApiKey, geminiApiKey, groqMealApiKey, mistralApiKey)
    // ont été retirées en v34 et déplacées vers SecureKeyStore (Phase C).
    // Voir ShredCoachDatabase.DropLegacyApiKeyColumns pour la migration.
)

enum class FitnessLevel(@androidx.annotation.StringRes val displayNameRes: Int) {
    BEGINNER(com.shredcoach.app.R.string.fitness_level_beginner),
    INTERMEDIATE(com.shredcoach.app.R.string.fitness_level_intermediate),
    ADVANCED(com.shredcoach.app.R.string.fitness_level_advanced)
}

enum class EquipmentType(@androidx.annotation.StringRes val displayNameRes: Int) {
    FULL_GYM(com.shredcoach.app.R.string.equipment_full_gym),      // Machines + poids libres
    HOME_GYM(com.shredcoach.app.R.string.equipment_home_gym),      // Poids libres uniquement
    BODYWEIGHT(com.shredcoach.app.R.string.equipment_bodyweight)   // Poids du corps uniquement
}

enum class FitnessGoal(@androidx.annotation.StringRes val displayNameRes: Int) {
    SHRED(com.shredcoach.app.R.string.fitness_goal_shred),         // Sèche / perte de gras
    BULK(com.shredcoach.app.R.string.fitness_goal_bulk),           // Prise de masse
    MAINTAIN(com.shredcoach.app.R.string.fitness_goal_maintain)    // Maintien
}
