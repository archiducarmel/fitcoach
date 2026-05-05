// Module :baselineprofile — exerce l'app via Macrobenchmark pour générer un
// fichier de Baseline Profile (liste de méthodes hot-path à pré-compiler AOT
// avant le premier lancement). Résultat : cold start −30 à −40%, scrolls plus
// fluides à la première ouverture d'écran.
//
// Pour régénérer le profil quand on modifie le code applicatif :
//   1. Connecter un téléphone physique en USB debugging (Pixel/Samsung/etc.)
//      OU démarrer un AVD Android 14+ avec Google APIs activées
//   2. Lancer : ./gradlew.bat :app:generateBaselineProfile
//   3. AGP roule le test BaselineProfileGenerator sur l'appareil, capture les
//      classes/méthodes hit, et écrit le profil dans
//      app/src/main/baseline-prof.txt (auto-bundlé dans les release builds).
//
// Le test BaselineProfileGenerator simule le parcours typique premier-launch :
// cold start → Home → ouverture Exercices → ouverture Stats. C'est cette
// trajectoire qui sera ensuite optimisée AOT pour tous les utilisateurs.

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.shredcoach.app.baselineprofile"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    defaultConfig {
        minSdk = 28          // Macrobenchmark requires API 28+
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Variants de l'app à benchmarker. AGP 8 + baselineprofile 1.3 attend
    // une "release" de :app comme cible — on a donc juste besoin de la
    // benchmarkVariant standard, pas de config exotique.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}

baselineProfile {
    // Profil utilisable AOT-compiled-on-install via le ProfileInstaller bundlé
    // dans :app. Pas besoin de Play Store baseline profile API pour le moment.
    useConnectedDevices = true
}
