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
    val bodyMeshImagePath: String? = null,   // Chemin de l'image mesh IA générée
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
    val profilePhotoPath: String? = null
    // NOTE : les clés API (llmApiKey, geminiApiKey, groqMealApiKey, mistralApiKey)
    // ont été retirées en v34 et déplacées vers SecureKeyStore (Phase C).
    // Voir ShredCoachDatabase.DropLegacyApiKeyColumns pour la migration.
)

enum class FitnessLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

enum class EquipmentType {
    FULL_GYM,      // Machines + poids libres
    HOME_GYM,      // Poids libres uniquement
    BODYWEIGHT     // Poids du corps uniquement
}

enum class FitnessGoal {
    SHRED,         // Sèche / perte de gras
    BULK,          // Prise de masse
    MAINTAIN       // Maintien
}
