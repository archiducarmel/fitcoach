package com.shredcoach.app.data.local.entity


import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shredcoach.app.R
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

enum class NotifType(
    val displayName: String,
    @StringRes val displayNameRes: Int,
    val icon: String,
) {
    MEAL_DEBRIEF("Débrief repas", R.string.notif_type_meal_debrief, "🍽"),
    WORKOUT_DEBRIEF("Débrief séance", R.string.notif_type_workout_debrief, "💪"),
    MEAL_REMINDER("Rappel repas", R.string.notif_type_meal_reminder, "⏰"),
    SHAKER_REMINDER("Rappel shaker", R.string.notif_type_shaker_reminder, "🥤"),
    BEDTIME_REMINDER("Rappel coucher", R.string.notif_type_bedtime_reminder, "😴"),
    WORKOUT_REMINDER("Rappel séance", R.string.notif_type_workout_reminder, "🏋"),
    MOTIVATION("Motivation", R.string.notif_type_motivation, "🔥"),
    /** Notification proactive du coach IA (catégorie dédiée pour analytics + canal). */
    COACH_PROACTIVE("Coach Shreddy", R.string.notif_type_coach_proactive, "🧠"),
    /** Récap hebdomadaire (dimanche soir). */
    WEEKLY_RECAP("Récap de la semaine", R.string.notif_type_weekly_recap, "📊"),
    OTHER("Info", R.string.notif_type_other, "ℹ")
}
