package com.shredcoach.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Test runner custom qui remplace `ShredCoachApplication` par `HiltTestApplication`
 * pour les tests instrumentés. Permet à Hilt d'instancier les modules de test
 * (`@UninstallModules`, `@BindValue`, etc.) plutôt que les modules de production.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
