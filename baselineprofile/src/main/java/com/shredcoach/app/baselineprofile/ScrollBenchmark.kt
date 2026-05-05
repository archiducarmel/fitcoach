package com.shredcoach.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Macrobenchmark — mesure de la fluidité du scroll dans l'écran Exercices.
 *
 * Pourquoi cet écran : c'est l'écran le plus dense de l'app — LazyColumn
 * de cards avec GIFs animés (Coil decoder) + filtres dynamiques. Si on
 * passe les 16ms par frame ici, on dropframe → l'utilisateur perçoit
 * un scroll saccadé.
 *
 * FrameTimingMetric capture pour chaque frame :
 *   - **frameDurationCpuMs** : temps CPU pour produire la frame
 *   - **frameOverrunMs**     : combien on dépasse les 16.67ms cible
 *
 * Stats reportées : P50 (médiane), P95 (95% des frames), P99 (pire 1%).
 *
 * Cible FAANG : P50 < 8ms, P95 < 16ms, P99 < 24ms. Au-delà, jank visible.
 *
 * Exécution :
 *   ./gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.shredcoach.app.baselineprofile.ScrollBenchmark
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule val rule = MacrobenchmarkRule()

    /** Scroll AVEC baseline profile — ce que l'utilisateur réel expérimente. */
    @Test
    fun scrollExercisesPartial() = rule.measureRepeated(
        packageName = "com.shredcoach.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial()
    ) {
        startActivityAndWait()

        // Naviguer vers Exercices (bottom tab)
        val exercicesTab = device.findObject(By.text("Exercices"))
            ?: device.findObject(By.desc("Exercices"))
        exercicesTab?.click()
        device.wait(Until.findObject(By.scrollable(true)), 5_000)

        // Scroll continu pour capturer les frames pendant que les GIFs
        // sont décodés et affichés.
        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(2) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
