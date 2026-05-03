package com.shredcoach.app.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WorkoutGenerator : Screen("workout_generator")
    object Workout : Screen("workout")
    object WorkoutSession : Screen("workout_session/{workoutId}") {
        fun createRoute(workoutId: Long) = "workout_session/$workoutId"
    }
    object WorkoutSummary : Screen("workout_summary")
    object Exercises : Screen("exercises")
    object ExerciseDetail : Screen("exercise_detail/{exerciseId}") {
        fun createRoute(exerciseId: Long) = "exercise_detail/$exerciseId"
    }
    object Stats : Screen("stats")
    object Nutrition : Screen("nutrition")
    object Settings : Screen("settings")
    object Profile : Screen("profile")
    object ProgressPhotos : Screen("progress_photos")
    object CustomWorkout : Screen("custom_workout")
    object FavoriteWorkouts : Screen("favorite_workouts")
    object Onboarding : Screen("onboarding")
    object Chat : Screen("chat")
    object MealScanner : Screen("meal_scanner")
    object MealScanDetail : Screen("meal_scan_detail/{scanId}") {
        fun createRoute(scanId: Long) = "meal_scan_detail/$scanId"
    }
    object FavoritePreview : Screen("favorite_preview/{workoutId}") {
        fun createRoute(workoutId: Long) = "favorite_preview/$workoutId"
    }
    object Notifications : Screen("notifications")
    object BodyScanner : Screen("body_scanner")
    object BodyMesh : Screen("body_mesh")
    object Calendar : Screen("calendar")
    object WorkoutHistory : Screen("workout_history")
    object WorkoutHistoryDetail : Screen("workout_history_detail/{logId}") {
        fun createRoute(logId: Long) = "workout_history_detail/$logId"
    }
    object ExerciseDbExplorer : Screen("exercise_db_explorer")
    object GymScan : Screen("gym_scan")
    object ExerciseDbDetail : Screen("exercise_db_detail/{exerciseId}") {
        fun createRoute(exerciseId: String): String {
            val encoded = java.net.URLEncoder.encode(exerciseId, "UTF-8")
            return "exercise_db_detail/$encoded"
        }
    }
}
