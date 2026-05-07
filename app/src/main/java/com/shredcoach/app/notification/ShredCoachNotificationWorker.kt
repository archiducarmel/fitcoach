package com.shredcoach.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.data.local.entity.NotifType
import com.shredcoach.app.data.repository.UserRepository
import com.shredcoach.app.data.repository.WorkoutRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class ShredCoachNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val dispatcher: AppNotificationDispatcher
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.failure()
        val profile = userRepository.getUserProfileOnce()

        if (profile?.notificationsEnabled != true) return Result.success()

        when (type) {
            TYPE_BREAKFAST -> if (profile.notifBreakfast) dispatcher.dispatch(
                NotifType.MEAL_REMINDER,
                context.getString(R.string.notif_meal_breakfast_title),
                context.getString(R.string.notif_meal_breakfast_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_LUNCH -> if (profile.notifLunch) dispatcher.dispatch(
                NotifType.MEAL_REMINDER,
                context.getString(R.string.notif_meal_lunch_title),
                context.getString(R.string.notif_meal_lunch_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_SNACK -> if (profile.notifSnack) dispatcher.dispatch(
                NotifType.MEAL_REMINDER,
                context.getString(R.string.notif_meal_snack_title),
                context.getString(R.string.notif_meal_snack_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_DINNER -> if (profile.notifDinner) dispatcher.dispatch(
                NotifType.MEAL_REMINDER,
                context.getString(R.string.notif_meal_dinner_title),
                context.getString(R.string.notif_meal_dinner_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_SHAKER_MORNING -> if (profile.notifShaker) dispatcher.dispatch(
                NotifType.SHAKER_REMINDER,
                context.getString(R.string.notif_shaker_morning_title),
                context.getString(R.string.notif_shaker_morning_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_SHAKER_EVENING -> if (profile.notifShaker) dispatcher.dispatch(
                NotifType.SHAKER_REMINDER,
                context.getString(R.string.notif_shaker_evening_title),
                context.getString(R.string.notif_shaker_evening_body),
                ShredCoachApplication.CHANNEL_MEALS
            )
            TYPE_BEDTIME -> if (profile.notifBedtime) dispatcher.dispatch(
                NotifType.BEDTIME_REMINDER,
                context.getString(R.string.notif_bedtime_title),
                context.getString(R.string.notif_bedtime_body),
                ShredCoachApplication.CHANNEL_BEDTIME
            )
            TYPE_MOTIVATION -> if (profile.notifMotivation) {
                checkMotivation()
            }
        }
        return Result.success()
    }

    private suspend fun checkMotivation() {
        val today = LocalDate.now()
        val threeDaysAgo = today.minusDays(3)
        val count = workoutRepository.getWorkoutCountInPeriod(threeDaysAgo, today)

        if (count == 0) {
            dispatcher.dispatch(
                NotifType.MOTIVATION,
                context.getString(R.string.notif_motivation_title),
                context.getString(R.string.notif_motivation_body),
                ShredCoachApplication.CHANNEL_WORKOUT
            )
        }
    }

    companion object {
        const val TYPE_BREAKFAST = "breakfast"
        const val TYPE_LUNCH = "lunch"
        const val TYPE_SNACK = "snack"
        const val TYPE_DINNER = "dinner"
        const val TYPE_SHAKER_MORNING = "shaker_morning"
        const val TYPE_SHAKER_EVENING = "shaker_evening"
        const val TYPE_BEDTIME = "bedtime"
        const val TYPE_MOTIVATION = "motivation"
    }
}
