# ✅ Corrections Cardio & Échauffement

## 🎯 Changements Implémentés

### 1. **Cardio en UN SEUL exercice** ✅

**Avant** : 4 exercices cardio affichés (rameur, vélo, tapis, elliptique)
**Après** : 1 SEUL exercice cardio sélectionné aléatoirement

**Fichier modifié** : `GenerateWorkoutUseCase.kt`
```kotlin
// Sélectionner UN SEUL exercice cardio (pas fractionné)
val cardioExercises = filterByEquipment(
    allExercises.filter { it.muscleGroup == MuscleGroup.CARDIO },
    config.equipmentType
).randomOrNull()?.let { listOf(it) } ?: emptyList()
```

**Résultat** :
- La séance affiche maintenant : "🏃 Cardio (30 min)" avec UN SEUL exercice
- Ex: "Rameur - 30 minutes" ou "Vélo elliptique - 25 minutes"

---

### 2. **Échauffement & Cardio intégrés dans le déroulé** ✅

**Avant** :
- Démarrer la séance → Commence directement par la musculation
- Échauffement et cardio affichés uniquement dans la preview

**Après** :
- Démarrer la séance → Ordre complet : **Échauffement → Musculation → Cardio**

**Fichier modifié** : `WorkoutGeneratorViewModel.kt`
```kotlin
// Save ALL workout exercises in order: Warmup → Muscu → Cardio
val allExercisesInOrder = workout.warmupExercises + workout.exercises + workout.cardioExercises
```

**Résultat** :
- L'utilisateur fait d'abord les 4 exercices d'échauffement (cardio léger, mobilisations, étirements, activation)
- Puis les 8 exercices de musculation
- Puis 1 exercice cardio de 30 minutes

---

### 3. **Card Cardio spécialisée** ✅

**Nouveau composant** : `CardioExerciseCard`

**Affichage** :
- 🏃 Icône running vert néon
- Nom de l'exercice en gros
- **"30 minutes"** en évidence (au lieu de séries × reps)
- Tips de l'exercice

**Fichier modifié** : `WorkoutPreviewScreen.kt`

---

## 📊 Ordre Complet d'une Séance 90 min

### Preview de la séance :
```
🔥 Échauffement (8 min)
  - Cardio léger (5-10 min)
  - Mobilisations articulaires (10-15 reps)
  - Étirements dynamiques (10-15 reps)
  - Séries d'activation (2×10-15 reps)

💪 Exercices de Musculation (8 exercices)
  1. Squat à la machine
  2. Développé couché machine
  3. Tirage poitrine prise large
  4. Élévations latérales haltères
  5. Curl biceps barre EZ
  6. Crunch classique
  7. Leg curl allongé
  8. Planche abdominale

🏃 Cardio (25 min)
  - Rameur - 25 minutes
```

### Déroulé dans WorkoutSessionScreen :
```
Exercice 1/13 : Cardio léger
Exercice 2/13 : Mobilisations articulaires
Exercice 3/13 : Étirements dynamiques
Exercice 4/13 : Séries d'activation
Exercice 5/13 : Squat à la machine
Exercice 6/13 : Développé couché machine
...
Exercice 12/13 : Planche abdominale
Exercice 13/13 : Rameur - 25 minutes
```

---

## 🎨 Améliorations UX à venir (optionnel)

### Pour WorkoutSessionScreen :

**Détection du type d'exercice** :
```kotlin
when (exercise.muscleGroup) {
    MuscleGroup.WARMUP -> {
        // Afficher seulement les instructions
        // Bouton "Terminé" au lieu de "Série terminée"
        // Pas d'input poids
    }
    MuscleGroup.CARDIO -> {
        // Afficher chronomètre de X minutes
        // Pas d'input poids/reps
        // Bouton "Terminer le cardio"
    }
    else -> {
        // Interface actuelle (poids + reps)
    }
}
```

**Pour l'instant** : L'interface actuelle affiche poids/reps même pour échauffement/cardio
- L'utilisateur peut saisir "0" pour le poids
- Ou simplement valider avec les valeurs par défaut
- Fonctionnel mais pas optimal

---

## 🚀 Comment Tester

1. **Rebuild** le projet
2. **Génère une séance** 90 min
3. **Dans la preview**, vérifie :
   - ✅ Échauffement : 4 exercices
   - ✅ Musculation : 8 exercices
   - ✅ Cardio : **1 SEUL exercice** avec durée en minutes
4. **Clique "COMMENCER LA SÉANCE"**
5. **Premier exercice** devrait être "Cardio léger" (échauffement)
6. **Dernier exercice** devrait être le cardio (ex: Rameur 25 min)

---

## 📝 Notes Techniques

### Ordre des exercices
- `allExercisesInOrder = warmup + muscu + cardio`
- Stocké dans `WorkoutExerciseEntity` avec `orderIndex` séquentiel

### Sélection cardio
- `.randomOrNull()` : Choisit aléatoirement 1 exercice parmi ceux disponibles
- Filtré selon équipement (ex: si "Poids du corps", pas de rameur)

### Exercices d'échauffement
- Toujours les 4 mêmes (pas de filtre)
- Préparent corps et articulations

---

**Développé par Claude (Anthropic)**
**Date : Avril 2026**
**Version : 1.3.2 (Cardio unique + Warmup intégré)**
