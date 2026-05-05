package com.shredcoach.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Génère le Baseline Profile de ShredCoach.
 *
 * Stratégie : on couvre les chemins critiques que l'utilisateur emprunte
 * dans les 30 premières secondes après l'ouverture de l'app — Home → Exercices
 * → Stats. AGP capture chaque classe/méthode JIT-compilée pendant ce parcours
 * et écrit la liste dans `app/src/main/baseline-prof.txt`. Au prochain build
 * release, ces méthodes seront AOT-compilées à l'install — d'où le gain de
 * cold start mesurable.
 *
 * Pourquoi ces écrans précisément :
 *  - **Home** : 100% des sessions y commencent. C'est l'écran le plus critique.
 *  - **Exercices (liste GIFs)** : LazyColumn avec décodage Coil, premier
 *    écran "lourd" en composition. La fluidité du premier scroll dépend
 *    directement du profil.
 *  - **Stats / Dashboard** : les charts animés (NutriMacroRing, WeeklyChart)
 *    instancient beaucoup de Path/Canvas — bénéficie énormément du AOT.
 *
 * Régénération : `./gradlew.bat :app:generateBaselineProfile`
 * Le test n'est PAS lancé en CI/build normal, uniquement à la demande.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.shredcoach.app",
        includeInStartupProfile = true
    ) {
        // Cold start — kill + reopen pour capturer le path d'init complet
        startActivityAndWait()

        // L'utilisateur attend 1-2 secondes que la home soit stabilisée
        // (StaggeredAppear cascade ~600ms, puis interaction probable).
        device.waitForIdle()

        // Scroll Home : capture les éléments en lazy load (sections wrappées
        // dans StaggeredAppear, widgets prochaine séance, FAB Shreddy).
        device.findObject(By.scrollable(true))?.scroll(
            androidx.test.uiautomator.Direction.DOWN, 0.7f
        )
        device.waitForIdle()

        // Naviguer vers Exercices via la bottom bar (label "Exercices" ou
        // équivalent — on cherche d'abord par desc, fallback par texte).
        // On capture les recompositions sur ce path : c'est l'écran le plus
        // riche en GIFs + filtres.
        navigateBottomTab("Exercices")
        device.wait(Until.findObject(By.scrollable(true)), 5_000)
        device.findObject(By.scrollable(true))?.scroll(
            androidx.test.uiautomator.Direction.DOWN, 0.8f
        )
        device.waitForIdle()

        // Naviguer vers Stats / Dashboard (charts + animations).
        navigateBottomTab("Stats")
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.navigateBottomTab(label: String) {
        // BottomNavigation items ont à la fois un text et une content desc.
        // On essaie les deux pour robustesse cross-locale.
        val target = device.findObject(By.text(label))
            ?: device.findObject(By.desc(label))
        target?.click()
    }
}
