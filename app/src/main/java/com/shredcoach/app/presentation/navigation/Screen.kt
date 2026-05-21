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
    /**
     * Chat avec persona query-arg. Pattern : `chat?persona={persona}`.
     * - `Screen.Chat.route` = pattern (utilisé par `composable(...)`).
     * - `Screen.Chat.createRoute(persona)` = URL concrète à passer à `navigate(...)`.
     * Sans param explicite, persona = shreddy par défaut.
     */
    object Chat : Screen("chat?persona={persona}") {
        fun createRoute(persona: String = "shreddy") = "chat?persona=$persona"
    }
    /** Helper d'entrée Dr. Glykos (passe persona=dr_glykos à la même Chat composable). */
    object DrGlykosChat {
        val route: String = Chat.createRoute("dr_glykos")
    }
    /**
     * Entrée upload screenshot CGM journalier.
     *
     * Pattern : `glucose_entry?date={date}` — la date cible est passée en
     * query arg (ISO `yyyy-MM-dd`). Si absent, l'écran défaut à `LocalDate.now()`.
     *
     * **Crucial** : sans cet arg, l'user qui navigue depuis NutritionScreen sur
     * une date J+1 atterrirait sur l'upload du jour courant → silently overwrite
     * du log de today à chaque upload. Bug data majeur réglé par ce paramètre.
     */
    object GlucoseEntry : Screen("glucose_entry?date={date}") {
        fun createRoute(date: java.time.LocalDate = java.time.LocalDate.now()) =
            "glucose_entry?date=$date"
    }
    /** Historique CGM (graphes, KPIs, patterns). */
    object GlucoseHistory : Screen("glucose_history")
    /**
     * Analyse experte de la glycémie quotidienne par Dr. Glykos (LLM).
     * Pattern : `glucose_analysis?date={date}`. Si absent → J-1.
     */
    object GlucoseAnalysis : Screen("glucose_analysis?date={date}") {
        fun createRoute(date: java.time.LocalDate = java.time.LocalDate.now().minusDays(1)) =
            "glucose_analysis?date=$date"
    }
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
    object BodyComposition : Screen("body_composition")
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
    object PrivacyPolicy : Screen("privacy_policy")
    object LanguageSettings : Screen("language_settings")
}
