# ═══════════════════════════════════════════════════════════════
# ShredCoach — ProGuard / R8 rules
# ═══════════════════════════════════════════════════════════════
#
# R8 fullMode est activé par défaut depuis AGP 8.x — pas besoin de flag.
# minify + shrinkResources sont activés sur le buildType `release` dans
# `app/build.gradle.kts`.
#
# Catégories :
#   1. Room (entités, DAOs, query result types)
#   2. Hilt / Dagger (DI runtime)
#   3. Gson + DTOs API LLM (toutes les classes sérialisées)
#   4. Coil (image loader)
#   5. OkHttp + interceptors
#   6. WorkManager (workers via réflexion)
#   7. Compose (déjà géré par AGP, on garde -dontwarn pour sécurité)
#   8. Coroutines / Kotlin metadata
#   9. Gestes globaux (enums, annotations, signatures génériques)

# ───────────────────────────────────────────────────────────────
# 1. Room — entités + DAOs + query result POJOs
# ───────────────────────────────────────────────────────────────
-keep class com.shredcoach.app.data.local.entity.** { *; }
-keep class com.shredcoach.app.data.local.dao.** { *; }
-keep class com.shredcoach.app.data.local.converter.** { *; }
# Migrations et schéma exporté (pour debugging migrations en prod)
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static <methods>;
}
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *

# ───────────────────────────────────────────────────────────────
# 2. Hilt / Dagger
# ───────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface javax.inject.Inject
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.internal.codegen.**

# ───────────────────────────────────────────────────────────────
# 3. Gson — DTOs sérialisés / désérialisés par les services LLM
# ───────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# Réflexion sur les noms de champs annotés @SerializedName
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── DTOs LLM API (response/request bodies) ──
-keep class com.shredcoach.app.data.remote.BodyAnalysisResult { *; }
-keep class com.shredcoach.app.data.remote.MealAnalysisResult { *; }
-keep class com.shredcoach.app.data.remote.AnalyzedDish { *; }
-keep class com.shredcoach.app.data.remote.Ingredient { *; }
-keep class com.shredcoach.app.data.remote.Micronutrient { *; }
-keep class com.shredcoach.app.data.remote.ExerciseDbExercise { *; }
-keep class com.shredcoach.app.data.remote.ExerciseDbMeta { *; }
-keep class com.shredcoach.app.data.remote.GymScanResult { *; }
-keep class com.shredcoach.app.data.remote.ChatMessage { *; }
# Note : OpenAiRequest / ClaudeRequest sont `private` mais sérialisés via
# gson.toJson() — file-scoped suffit en JVM, mais on les keep par sécurité
# (R8 peut inliner les private data class en release, perdant les noms de champs).
-keep class com.shredcoach.app.data.remote.LlmApiService$* { *; }

# ── DAO query result types (déjà gérés par règle § 1, explicit ici) ──
-keep class com.shredcoach.app.data.local.dao.DailyMacros { *; }
-keep class com.shredcoach.app.data.local.dao.DayTotals { *; }
-keep class com.shredcoach.app.data.local.dao.FoodFrequency { *; }
-keep class com.shredcoach.app.data.local.dao.SetWithDate { *; }
-keep class com.shredcoach.app.data.local.dao.DailyVolume { *; }
-keep class com.shredcoach.app.data.local.dao.DailyCount { *; }
-keep class com.shredcoach.app.data.local.dao.PersonalRecord { *; }
-keep class com.shredcoach.app.data.local.dao.MuscleGroupSets { *; }
-keep class com.shredcoach.app.data.local.dao.MuscleGroupDuration { *; }
-keep class com.shredcoach.app.data.local.dao.ConversationSummary { *; }

# ───────────────────────────────────────────────────────────────
# 4. Coil (image loader)
# ───────────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }
-keep interface coil.** { *; }

# ───────────────────────────────────────────────────────────────
# 5. OkHttp + Logging Interceptor
# ───────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ───────────────────────────────────────────────────────────────
# 6. WorkManager
# ───────────────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
# Hilt-Worker — l'AssistedFactory est référencée par réflexion
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
-keepclassmembers class * {
    @dagger.assisted.AssistedInject <init>(...);
}

# ───────────────────────────────────────────────────────────────
# 7. Compose — l'AGP gère déjà l'essentiel, ces règles sont défensives
# ───────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
# Material You / dynamic colors API (réflexion sur Build.VERSION)
-dontwarn androidx.compose.material3.**

# ───────────────────────────────────────────────────────────────
# 8. Kotlin / Coroutines
# ───────────────────────────────────────────────────────────────
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
# Kotlin reflection — utilisé par Hilt (KSP-generated) et Gson
-keepattributes RuntimeVisible*Annotations
-keepattributes AnnotationDefault
# kotlinx.coroutines — service file pour Channel.shutdownHook etc.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {*;}

# ───────────────────────────────────────────────────────────────
# 9. Gestes globaux
# ───────────────────────────────────────────────────────────────
# Enums : Gson + Room TypeConverters utilisent valueOf()/values() par réflexion
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Tous les enums de l'app : la sérialisation Gson / les TypeConverters Room
# stringifient les enums via name() et les reconstruisent via valueOf().
# Si R8 obfusque, valueOf("ORIGINAL_NAME") plante.
-keep enum com.shredcoach.app.data.local.entity.** { *; }
-keep enum com.shredcoach.app.data.remote.** { *; }
-keep enum com.shredcoach.app.domain.** { *; }

# Parcelable creators (entities + repository transfer objects qui passent
# entre Activities via Bundle / SavedStateHandle)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Native methods — au cas où une lib JNI s'ajoute (ex: ML Kit, TensorFlow)
-keepclasseswithmembernames class * {
    native <methods>;
}

# ───────────────────────────────────────────────────────────────
# 10. Annotations compile-time non-packagées (Tink, etc.)
# ───────────────────────────────────────────────────────────────
# androidx.security.crypto utilise Google Tink, qui référence des annotations
# `errorprone` uniquement présentes au build-time. Sans -dontwarn, R8 fail.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.errorprone.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.checkerframework.**
-dontwarn org.jspecify.annotations.**
# Tink lui-même
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
