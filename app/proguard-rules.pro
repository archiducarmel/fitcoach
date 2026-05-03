# ═══════════════════════════════════════
# ShredCoach ProGuard Rules
# ═══════════════════════════════════════

# ── Room ──
-keep class com.shredcoach.app.data.local.entity.** { *; }
-keep class com.shredcoach.app.data.local.dao.** { *; }
-keep class com.shredcoach.app.data.local.converter.** { *; }

# ── Hilt / Dagger ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.internal.codegen.**

# ── Gson ──
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.shredcoach.app.data.local.dao.DailyMacros { *; }
-keep class com.shredcoach.app.data.local.dao.DayTotals { *; }
-keep class com.shredcoach.app.data.local.dao.SetWithDate { *; }
-keep class com.shredcoach.app.data.local.dao.DailyVolume { *; }
-keep class com.shredcoach.app.data.local.dao.DailyCount { *; }
-keep class com.shredcoach.app.data.local.dao.PersonalRecord { *; }
-keep class com.shredcoach.app.data.local.dao.MuscleGroupSets { *; }
-keep class com.shredcoach.app.data.local.dao.FoodFrequency { *; }

# ── Enums ──
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# ── Kotlin ──
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ── Coil ──
-dontwarn coil.**

# ── WorkManager ──
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ── Compose ──
-dontwarn androidx.compose.**

# ── Coroutines ──
-dontwarn kotlinx.coroutines.**
