package com.shredcoach.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark — mesure objective du cold/warm/hot start de ShredCoach.
 *
 * Comment ça marche :
 *   - Chaque @Test relance l'app N fois (default = 5 iterations)
 *   - Pour chaque run, mesure timeToInitialDisplay et timeToFullDisplay
 *   - Sortie : P50, P95, P99 + min/max dans le résultat de gradle
 *
 * Trois modes startup :
 *   - **Cold** : process killed avant chaque run = pire cas (JVM init,
 *     class loading, profile installation). C'est le KPI principal.
 *   - **Warm** : process en mémoire mais activity recreated. Représente
 *     "rouvrir l'app après un retour rapide depuis Settings".
 *   - **Hot** : activity en mémoire = simple onResume. Très court par nature.
 *
 * Trois modes compilation :
 *   - **None** : code interprété au max (worst case post-install)
 *   - **Partial** : Compose lib profiles + baseline profile bundlé appliqué
 *   - **Full** : tout AOT (best case, mais réaliste seulement après ~1 jour
 *     d'usage où Android System a fini son optimisation background)
 *
 * Exécution :
 *   ./gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest
 *
 * Avec un device USB-debug connecté (le device DOIT être physique pour des
 * mesures fiables — les émulateurs ont des fluctuations de timing).
 *
 * Lecture du rapport :
 *   baselineprofile/build/outputs/connected_android_test_additional_output/
 *   benchmarkRelease/connected/<device>/com.shredcoach.app.baselineprofile.test-benchmarkData.json
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    /** Cold start sans aucune optimisation AOT — worst case install initial. */
    @Test
    fun startupColdNone() = startup(StartupMode.COLD, CompilationMode.None())

    /**
     * Cold start AVEC baseline profile appliqué — ce qu'expérimente
     * l'utilisateur réel après le premier lancement (post-profile install).
     * **C'est le KPI cible** : différence par rapport à `startupColdNone`
     * = gain effectif du baseline profile.
     */
    @Test
    fun startupColdPartial() = startup(StartupMode.COLD, CompilationMode.Partial())

    /** Warm start — recréation activity sans relaunch process. */
    @Test
    fun startupWarmPartial() = startup(StartupMode.WARM, CompilationMode.Partial())

    /** Hot start — onResume seul. Sanity check (devrait être < 100ms). */
    @Test
    fun startupHotPartial() = startup(StartupMode.HOT, CompilationMode.Partial())

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = "com.shredcoach.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = mode,
        compilationMode = compilation
    ) {
        pressHome()
        startActivityAndWait()
    }
}
