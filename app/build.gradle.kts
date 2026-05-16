plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.shredcoach.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shredcoach.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Custom runner injecte HiltTestApplication pour les instrumented tests.
        testInstrumentationRunner = "com.shredcoach.app.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // i18n : locales supportées V2 = FR + EN + ES + IT + PT(BR) + DE.
        // Exclut toutes les autres locales des libraries tierces (AndroidX,
        // Material) de l'APK final → APK plus petit + cohérence des strings.
        resourceConfigurations += listOf("fr", "en", "es", "it", "pt", "de")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // shrinkResources = R8 + AAPT2 enlève les drawables/strings non-référencés
            // après le pruning du code par minify. Indissociable de minify : doit
            // être activé conjointement, sinon l'optimisation est partielle.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signe le release avec la keystore debug par défaut afin que
            // `./gradlew :app:installRelease` fonctionne sans setup keystore
            // upload Play Store. C'est UNIQUEMENT pour le dev workflow — la
            // version finale Play Store doit utiliser une vraie release keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"

        // Stability configuration : marque les types externes (java.time,
        // Bitmap, NavController, etc.) comme stables, ce qui évite des
        // recompositions inutiles. Toujours actif (gain de perf permanent).
        val stabilityConfig = "${rootDir.absolutePath}/compose_stability_config.txt"
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=$stabilityConfig",
        )

        // Compose compiler metrics + reports — activés à la demande via
        // -Pcomposemetrics=true. Génère :
        //   - app_release-classes.txt    : stabilité par classe
        //   - app_release-composables.txt: skippable/restartable status
        //   - app_release-module.json    : aggregate metrics
        //
        // ./gradlew :app:compileReleaseKotlin -Pcomposemetrics=true --rerun-tasks
        // grep "^unstable class" app/build/compose-reports/app_release-classes.txt
        if (project.findProperty("composemetrics") == "true") {
            val metricsDir = "${project.layout.buildDirectory.get().asFile.absolutePath}/compose-metrics"
            val reportsDir = "${project.layout.buildDirectory.get().asFile.absolutePath}/compose-reports"
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$metricsDir",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$reportsDir",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Conflits META-INF entre les jars Google API client (Drive, auth-oauth2,
            // auth-credentials, http-client). Chaque jar embarque son propre INDEX.LIST,
            // DEPENDENCIES, NOTICE, LICENSE — mêmes chemins, contenus différents → AGP
            // refuse au moment du packaging. On exclut systématiquement ces fichiers
            // de l'APK final (ils ne servent pas au runtime, juste au build/distribution).
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }

    // MigrationTestHelper a besoin d'accéder aux schémas Room exportés
    // au runtime des tests instrumentés — on les déclare comme asset
    // du source set androidTest (ils sont packagés dans le test APK).
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // Phase 7 — Quality gate i18n
    // ─────────────────────────────────────────────────────────────────
    // MissingTranslation : escalé en error → CI casse si une string FR
    //   n'a pas de traduction EN (ou inverse). Empêche les régressions.
    //   Baseline lint-baseline.xml liste les violations connues à corriger
    //   progressivement (notamment les exo_* EN-only par design — la FR
    //   reste dans la DB en source de vérité).
    // HardcodedText : escalé en warning → visible dans Android Studio +
    //   échec CI si on l'escale plus tard. Détecte les littéraux Compose
    //   non extraits.
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = false
        // i18n : strings sans traduction = erreur (sauf baseline)
        error += "MissingTranslation"
        // ExtraTranslation downgrad en warning : les `exo_<key>_*` du catalogue
        // exos sont **EN-only by design** (la source FR vit dans la DB
        // ExerciseEntity, EN dans values-en/, resolver dynamique dans
        // ExerciseI18n). Les laisser en error casserait la CI à chaque nouvelle
        // wave de traductions exos. Trade-off accepté : on ne détecte plus
        // les accidents EN-only non-exo, mais on a 0 false positive sur les
        // ~250 exos déjà traduits.
        warning += "ExtraTranslation"
        // Garde-fou pour les nouveaux écrans (ne pas remonter sur l'existant
        // déjà nettoyé via Vagues 1A-1E).
        warning += "HardcodedText"
        // Fichier baseline = "violations connues, à corriger plus tard".
        // Sans baseline, l'app casserait sur les exo_* EN-only.
        baseline = file("lint-baseline.xml")
        // Génère les rapports en HTML/XML pour CI / inspection locale.
        htmlReport = true
        xmlReport = true
    }
}

// Configure KSP to work correctly with Hilt + export Room schemas
ksp {
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
    // Schémas Room exportés vers app/schemas/ — versionnés dans Git pour
    // permettre l'écriture de migrations testables (compile-time + runtime).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    // Per-app locales API (AppCompatDelegate.setApplicationLocales) + auto-store
    // via AppLocalesMetadataHolderService. Requis pour l'i18n runtime sans
    // restart d'app explicite.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // EXIF reader (date/heure de prise de vue) — utilisé par PhotoExifReader
    // pour auto-remplir la date d'un repas uploadé depuis la galerie. La lib
    // AndroidX est ~70KB, supporte JPEG/HEIF/PNG/WebP, lecture seule = sûr.
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    // ProcessLifecycleOwner — détecte foreground/background du process pour
    // que le WorkoutSessionService ne double-fire pas la voix/vibration
    // quand l'app est au premier plan (UI gère déjà).
    implementation("androidx.lifecycle:lifecycle-process:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Material 1 (uniquement pour l'API pullrefresh absente de M3 1.1.x)
    implementation("androidx.compose.material:material")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("javax.inject:javax.inject:1")

    // Coil for Image Loading (GIFs)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")

    // Lottie for reward animations (PR celebration, streak milestones, etc.)
    // Les .json animations sont attendus dans app/src/main/assets/lottie/
    // Si un asset manque, [LottieReward] retombe sur une animation Compose-natif.
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // WorkManager for Notifications
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // DataStore for Preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // SAF traversal — DocumentFile abstraction au-dessus des content URIs.
    // Permet d'écrire/lire dans Drive/OneDrive/Dropbox/local sans connaître
    // le provider sous-jacent. Utilisé par le moteur de backup local.
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Encrypted storage for secrets (API keys)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Pour `Task<T>.await()` — pont coroutines vers Play Services Tasks (utilisé par
    // l'AuthorizationClient Drive : `client.authorize(req).await()`).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp for LLM API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ───────────────────────────────────────────────
    // Google Drive backup (cloud sync)
    // ───────────────────────────────────────────────
    // play-services-auth fournit `AuthorizationClient` qui demande à l'utilisateur
    // l'accès au scope `drive.appdata` (dossier caché app-specific). On évite
    // Credential Manager (overkill pour ce use case — l'authent est implicite via
    // AuthorizationClient.authorize()).
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    // SDK Google Drive REST v3. Volumineux mais gère pour nous : auth headers,
    // resumable uploads pour fichiers > 5Mo, retry, parsing JSON.
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        // Évite conflit avec httpclient déjà transitive d'OkHttp/Conscrypt.
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }

    // Baseline Profile installer — au premier lancement, applique le profil
    // AOT-compilé bundlé dans l'APK (généré par le module :baselineprofile).
    // Sans cette dep, le profil bundlé est ignoré par le runtime.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    "baselineProfile"(project(":baselineprofile"))

    // ───────────────────────────────────────────────
    // ML Kit — Body mesh on-device (pose + segmentation)
    // ───────────────────────────────────────────────
    // Pose Detection (accurate variant) — extrait 33 keypoints anatomiques
    // depuis une photo. ~70ms sur mid-range, déterministe, 100% on-device,
    // pas de clé API. Utilisé par BodyMeshExtractor pour construire le
    // wireframe néon. Variant "accurate" (vs base) : keypoints plus fiables
    // sur les angles extrêmes, meilleure inFrameLikelihood.
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")
    // Selfie Segmentation — masque binaire de la silhouette du corps. ~100ms
    // on-device, retourne ConfidenceMask FloatBuffer (256x256 par défaut,
    // upscalé à la résolution input). Sert à dessiner le contour silhouette
    // et délimiter le masque mesh polygonal.
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // LeakCanary — detection automatique de fuites mémoire en debug.
    // S'auto-installe via ContentProvider (rien à appeler dans Application).
    // Quand un leak est détecté (Activity/ViewModel non-GC après pop nav),
    // une notification système apparaît avec le heap path. Aucun overhead
    // en release (debugImplementation = pas dans l'APK release).
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.13")

    // ───────────────────────────────────────────────
    // Tests unitaires (src/test) — JVM, rapides, isolés
    // ───────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.4.0")

    // ───────────────────────────────────────────────
    // Tests instrumentés (src/androidTest) — emulator/device
    // ───────────────────────────────────────────────
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room migration testing (MigrationTestHelper)
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // Hilt testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.50")

    // Truth pour les assertions lisibles
    androidTestImplementation("com.google.truth:truth:1.4.0")
}
