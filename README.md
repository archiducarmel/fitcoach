# 🔥 ShredCoach - Votre Coach Sportif Personnel

## 📱 Application Android de Coaching Full Body

ShredCoach est votre assistant personnel pour atteindre vos objectifs fitness : sèche, abdos dessinés, et forme olympique ! 💪

---

## 🎯 Fonctionnalités (Phase 1 - MVP)

### ✅ Fonctionnalités implémentées

- **68 exercices Full Body intégrés** issus du guide complet
  - 4 variantes par groupe musculaire (Machine, Haltères, Poids du corps, Isolation)
  - 11 groupes musculaires couverts
  - Détails techniques, poids de départ, séries/reps, temps de repos

- **Architecture professionnelle**
  - Clean Architecture (Data/Domain/Presentation)
  - Room Database pour stockage local
  - Hilt pour injection de dépendances
  - Jetpack Compose pour UI moderne

- **Design System premium**
  - Palette de couleurs énergique (Orange → Rouge)
  - Dark mode par défaut (optimisé salle de sport)
  - Typographie moderne et lisible

- **Écran d'accueil (Dashboard)**
  - Statistiques personnelles
  - Compteur de séries de jours
  - Accès rapide aux exercices

### 🚧 Fonctionnalités à venir (Phases suivantes)

- **Générateur de séances intelligent**
  - Séances Full Body adaptables (1h-3h)
  - Sélection automatique des exercices
  - Rotation des variantes

- **Mode Séance en direct**
  - GIFs animés des exercices
  - Chronomètre de repos
  - Suivi des poids et séries
  - Suggestions de progression

- **Système de notifications**
  - Rappels repas et shakers
  - Heure de coucher
  - Jours d'entraînement

- **Dashboard de progression**
  - Graphiques d'évolution
  - Records personnels
  - Historique complet

---

## 📥 Installation et Déploiement

### Prérequis

1. **Android Studio** (dernière version)
   - Téléchargez depuis : https://developer.android.com/studio
   - Installez avec les paramètres par défaut

2. **Un téléphone Android**
   - Android 8.0 (Oreo) ou supérieur
   - Câble USB pour connexion

---

### 🚀 Méthode 1 : Installation avec Android Studio (Recommandée)

#### Étape 1 : Ouvrir le projet

1. Lancez **Android Studio**
2. Cliquez sur `File` → `Open`
3. Naviguez vers le dossier `C:\Users\Sitou\Desktop\FitCoach`
4. Cliquez sur `OK`
5. Attendez que Gradle synchronise (barre de progression en bas)

#### Étape 2 : Activer le mode développeur sur votre téléphone

**Sur votre téléphone Android :**

1. Ouvrez `Paramètres`
2. Allez dans `À propos du téléphone`
3. Tapez **7 fois** sur `Numéro de build`
4. Un message "Vous êtes développeur !" apparaît

5. Retournez dans `Paramètres`
6. Une nouvelle option `Options pour les développeurs` est apparue
7. Ouvrez-la et activez `Débogage USB`

#### Étape 3 : Connecter votre téléphone

1. Branchez votre téléphone en USB à votre PC
2. Sur le téléphone, acceptez `Autoriser le débogage USB` (popup)
3. Cochez "Toujours autoriser depuis cet ordinateur"

#### Étape 4 : Lancer l'application

1. Dans Android Studio, en haut à droite, vous verrez votre appareil apparaître
2. Cliquez sur le bouton **▶ Run** (triangle vert) ou `Shift + F10`
3. L'application se compile et s'installe automatiquement !
4. ShredCoach s'ouvre sur votre téléphone 🎉

---

### 📦 Méthode 2 : Générer un APK (Installation sans câble)

#### Étape 1 : Générer l'APK

1. Dans Android Studio, cliquez sur `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. Attendez la compilation (1-2 minutes)
3. Une notification apparaît en bas : `APK(s) generated successfully`
4. Cliquez sur `locate` pour ouvrir le dossier

#### Étape 2 : Transférer l'APK

L'APK se trouve dans :
```
FitCoach\app\build\outputs\apk\debug\app-debug.apk
```

**Option A : Par email**
- Envoyez-vous l'APK par email
- Ouvrez l'email sur votre téléphone
- Téléchargez et installez

**Option B : Par câble USB**
- Copiez `app-debug.apk` sur votre téléphone
- Utilisez un gestionnaire de fichiers pour l'ouvrir
- Installez (autorisez "Sources inconnues" si demandé)

**Option C : Par Google Drive / OneDrive**
- Uploadez l'APK sur le cloud
- Téléchargez-le depuis votre téléphone
- Installez

---

## 🛠️ Structure du Projet

```
ShredCoach/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/shredcoach/app/
│   │   │   │   ├── data/                  # Couche données
│   │   │   │   │   ├── local/            # Base de données Room
│   │   │   │   │   │   ├── entity/       # Tables (Exercise, Workout, etc.)
│   │   │   │   │   │   ├── dao/          # Data Access Objects
│   │   │   │   │   │   └── converter/    # Type converters
│   │   │   │   │   ├── repository/       # Repositories
│   │   │   │   │   └── seed/             # Seed data (68 exercices)
│   │   │   │   ├── domain/               # Logique métier
│   │   │   │   │   └── model/            # Modèles (MuscleGroup, Variant)
│   │   │   │   ├── presentation/         # Interface utilisateur
│   │   │   │   │   ├── home/            # Écran d'accueil
│   │   │   │   │   ├── navigation/      # Navigation
│   │   │   │   │   └── theme/           # Design System
│   │   │   │   └── di/                   # Injection de dépendances (Hilt)
│   │   │   └── res/                       # Ressources (strings, themes)
│   ├── build.gradle.kts                   # Configuration Gradle
│   └── proguard-rules.pro                 # Règles ProGuard
├── build.gradle.kts                       # Configuration projet
├── settings.gradle.kts                    # Settings Gradle
└── README.md                              # Ce fichier
```

---

## 🎨 Technologies Utilisées

- **Kotlin** - Langage moderne et performant
- **Jetpack Compose** - UI déclarative et fluide
- **Room Database** - Stockage local robuste
- **Hilt** - Injection de dépendances
- **Coroutines & Flow** - Programmation asynchrone
- **Material Design 3** - Design system Google
- **Navigation Compose** - Navigation entre écrans

---

## 📊 Base de Données

### Tables principales

1. **exercises** - 68 exercices avec détails complets
2. **workouts** - Templates de séances
3. **workout_logs** - Historique des entraînements
4. **workout_sets** - Détails série par série
5. **user_profile** - Profil utilisateur
6. **nutrition_schedule** - Planning nutrition
7. **daily_checks** - Suivi quotidien

---

## 🔜 Prochaines Étapes de Développement

### Phase 2 : Générateur de Séances (Semaine 3-5)
- Algorithme de génération de séances
- Interface de sélection exercices
- Mode séance en direct avec GIFs
- Chronomètre de repos intégré
- Saisie des poids et répétitions

### Phase 3 : Suivi & Progression (Semaine 6-7)
- Dashboard statistiques
- Graphiques de progression
- Historique complet
- Records personnels

### Phase 4 : Assistant Quotidien (Semaine 8-9)
- Système de notifications
- Planning nutrition
- Rappels personnalisés
- Suivi routine quotidienne

### Phase 5 : Polish & Optimisation (Semaine 10-12)
- Animations fluides
- Optimisation performances
- Tests utilisateurs
- Guide complet d'utilisation

---

## 💡 Aide et Support

### Problèmes courants

**"Gradle sync failed"**
- Solution : `File` → `Invalidate Caches` → `Invalidate and Restart`

**"Device not found"**
- Vérifiez que le débogage USB est activé
- Essayez un autre câble USB
- Redémarrez Android Studio

**"Installation failed"**
- Désinstallez l'ancienne version
- Vérifiez l'espace de stockage

**"App crashes at startup"**
- Vérifiez Android version (minimum 8.0)
- Consultez les logs dans Logcat (Android Studio)

---

## 📝 Notes pour le Développeur

### Ajout de nouveaux exercices

Modifiez `app/src/main/java/com/shredcoach/app/data/seed/SeedData.kt` :

```kotlin
ExerciseEntity(
    name = "Nom de l'exercice",
    muscleGroup = MuscleGroup.CHEST,
    variant = ExerciseVariant.MACHINE,
    equipment = "Équipement nécessaire",
    executionKey = "Instructions d'exécution",
    startingWeight = "20-30 kg",
    series = 4,
    repsMin = 8,
    repsMax = 12,
    restSeconds = 90,
    tips = "Conseils d'expert",
    difficulty = 1
)
```

Puis : `Build` → `Clean Project` → `Rebuild Project`

### Modification des couleurs

Éditez `app/src/main/java/com/shredcoach/app/presentation/theme/Color.kt`

### Ajout d'un nouvel écran

1. Créer le package : `presentation/monecran/`
2. Créer `MonEcranScreen.kt` et `MonEcranViewModel.kt`
3. Ajouter la route dans `navigation/Screen.kt`
4. Ajouter le composable dans `ShredCoachNavigation.kt`

---

## 📄 Licence

© 2024 ShredCoach - Tous droits réservés

---

## 🏆 Remerciements

Application développée avec passion par Claude (Anthropic) en collaboration avec Sitou.

**Version actuelle :** 1.0.0 (MVP - Phase 1)
**Dernière mise à jour :** Avril 2026

---

## 🔗 Ressources Utiles

- [Documentation Android](https://developer.android.com/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)

---

**🔥 Prêt à devenir shredded ? Let's go ! 💪**
