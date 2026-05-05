package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Une notification envoyée par l'application, conservée pour l'inbox.
 * L'utilisateur peut la relire, la marquer comme lue, ou la supprimer.
 */
@Entity(
    tableName = "app_notifications",
    indices = [Index("timestamp"), Index("isRead")]
)
@Immutable
data class AppNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // NotifType.name
    val title: String,
    val body: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isRead: Boolean = false,
    val source: String = "local", // "llm" | "local"
    /** Deeplink optionnel : route vers l'écran à ouvrir au tap (ex: "meal_scan_detail/42"). */
    val deeplink: String? = null
)

enum class NotifType(val displayName: String, val icon: String) {
    MEAL_DEBRIEF("Débrief repas", "🍽"),
    WORKOUT_DEBRIEF("Débrief séance", "💪"),
    MEAL_REMINDER("Rappel repas", "⏰"),
    SHAKER_REMINDER("Rappel shaker", "🥤"),
    BEDTIME_REMINDER("Rappel coucher", "😴"),
    WORKOUT_REMINDER("Rappel séance", "🏋"),
    MOTIVATION("Motivation", "🔥"),
    /** Notification proactive du coach IA (catégorie dédiée pour analytics + canal). */
    COACH_PROACTIVE("Coach Shreddy", "🧠"),
    /** Récap hebdomadaire (dimanche soir). */
    WEEKLY_RECAP("Récap de la semaine", "📊"),
    OTHER("Info", "ℹ")
}
