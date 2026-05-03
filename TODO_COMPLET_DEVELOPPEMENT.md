# 📋 TODO COMPLET - ShredCoach Development Roadmap

## 🎯 VISION GLOBALE

**Objectif final** : Application Android TOP-3 Play Store pour coaching sportif et nutrition durant une sèche/prise de masse.

**État actuel** : ✅ Phase 3.2 complétée (Générateur + Mode Séance en Direct fonctionnel)

---

## 📊 PROGRESSION GLOBALE : ~30% COMPLÉTÉ

### ✅ COMPLÉTÉ (30%)
- Architecture & Base de données
- 68 Exercices intégrés
- Navigation de base
- Écrans Exercices + Détail
- Générateur de séances Full Body
- Mode séance en direct avec chronomètre
- Sauvegarde progression

### 🚧 EN COURS (0%)
- Rien actuellement

### ⏳ À FAIRE (70%)
- Tout le reste ci-dessous !

---

# 🔥 PHASE 3.3 - AMÉLIORATIONS MODE SÉANCE (Priorité: HAUTE)

**Durée estimée** : 2-3 heures

### 1. **Intégration GIFs des Exercices**
- [ ] Trouver/créer GIFs pour les 68 exercices
- [ ] Héberger GIFs (Firebase Storage ou URLs publiques)
- [ ] Ajouter URLs dans `SeedData.kt` (champ `gifUrl`)
- [ ] Remplacer placeholder par `AsyncImage` de Coil
- [ ] Gestion erreur chargement (fallback icon)
- [ ] Loading state pour GIF
- [ ] Optimisation cache Coil

### 2. **Vibration & Son à la Fin du Repos**
- [ ] Ajouter permission VIBRATE dans AndroidManifest
- [ ] Implémenter vibration pattern (3 courtes vibrations)
- [ ] Ajouter son notification (RingtoneManager)
- [ ] Paramètre utilisateur ON/OFF vibration
- [ ] Paramètre utilisateur ON/OFF son
- [ ] Tester sur différents devices

### 3. **Notification en Arrière-Plan**
- [ ] Créer `WorkoutForegroundService`
- [ ] Notification persistante avec chronomètre
- [ ] Contrôles dans notification (Pause/Skip)
- [ ] Update notification chaque seconde
- [ ] Gestion clic notification → retour app
- [ ] Arrêt service à la fin de la séance
- [ ] Permission POST_NOTIFICATIONS (Android 13+)

### 4. **Suggestion Intelligente de Poids**
- [ ] Query dernière séance avec même exercice
- [ ] Afficher "Dernière fois : 50kg × 10 reps"
- [ ] Auto-remplir input poids avec suggestion
- [ ] Badge "PR" si nouveau record
- [ ] Calcul 1RM (One Rep Max) estimé
- [ ] Affichage progression vs dernière fois

### 5. **UX Améliorations**
- [ ] Dialog confirmation sortie séance
- [ ] Sauvegarde progression partielle si abandon
- [ ] Mode paysage optimisé pour chronomètre
- [ ] Haptic feedback sur boutons CTA
- [ ] Animation transition exercices
- [ ] Swipe pour skip exercice (geste)
- [ ] Bouton "Refaire cette série" si échec

---

# 📊 PHASE 4 - DASHBOARD BI SPORTIF (Priorité: TRÈS HAUTE)

**Durée estimée** : 5-7 heures

### 1. **Architecture Dashboard**
- [ ] Créer `StatsViewModel`
- [ ] Créer `StatsRepository`
- [ ] Queries Room pour analytics (agrégations)
- [ ] Intégrer librairie graphiques (MPAndroidChart ou Vico)
- [ ] État filtres temporels (StateFlow)

### 2. **Filtres Temporels**
- [ ] Enum `TimePeriod` (TODAY, WEEK, MONTH, QUARTER, YEAR, CUSTOM)
- [ ] Date range picker pour CUSTOM
- [ ] Boutons chips pour sélection rapide
- [ ] Persistance filtre sélectionné (DataStore)
- [ ] Calcul dates start/end pour chaque période

### 3. **Graphique Évolution Poids par Exercice**
- [ ] LineChart avec Vico/MPAndroidChart
- [ ] Dropdown sélection exercice
- [ ] Axe X : Dates des séances
- [ ] Axe Y : Poids utilisé (kg)
- [ ] Points cliquables avec détails (date, poids, reps)
- [ ] Ligne de tendance (régression linéaire)
- [ ] Couleur par exercice
- [ ] Zoom/Pan sur graphique
- [ ] Export image PNG

### 4. **Graphique Volume Hebdomadaire**
- [ ] BarChart volume total par semaine
- [ ] Axe X : Semaines
- [ ] Axe Y : Volume (poids × reps total)
- [ ] Barre colorée par semaine
- [ ] Label valeur au-dessus de chaque barre
- [ ] Comparaison avec semaine précédente (%)
- [ ] Moyenne mobile sur 4 semaines

### 5. **Heat Map Fréquence d'Entraînement**
- [ ] Grille calendrier style GitHub contributions
- [ ] Couleur intensité selon nombre de séances
- [ ] Tooltip au clic (date + séances)
- [ ] Streak le plus long affiché
- [ ] Objectif hebdomadaire (ex: 4 séances/semaine)
- [ ] Indicateur compliance (%)

### 6. **Records Personnels (PR)**
- [ ] Liste top 5 PR par exercice
- [ ] Calcul automatique nouveau PR
- [ ] Badge "🏆 NOUVEAU PR !" en séance
- [ ] Historique progression PR
- [ ] Comparaison 1RM théorique
- [ ] Leaderboard personnel (top exercices)

### 7. **Statistiques Globales**
- [ ] Card "Cette semaine" :
  - Nombre de séances
  - Volume total
  - Temps total
  - Calories brûlées estimées
- [ ] Card "Ce mois" : mêmes stats
- [ ] Card "All time" :
  - Total séances
  - Total volume
  - Exercice le plus fait
  - Muscle le plus travaillé
- [ ] Graphique répartition groupes musculaires (PieChart)

### 8. **Comparaison Multi-Périodes**
- [ ] Sélection 2 périodes à comparer
- [ ] Graphiques côte à côte
- [ ] Tableau comparatif (volume, séances, progression)
- [ ] Pourcentage d'amélioration
- [ ] Insight automatiques ("Tu as progressé de 15% !")

### 9. **Tendances & Prédictions**
- [ ] Calcul tendance progression (linéaire)
- [ ] Prédiction poids dans X semaines
- [ ] Graphique projection future
- [ ] Alerte plateau (pas de progression depuis 4 semaines)
- [ ] Suggestions décharge/variation

### 10. **Export Données**
- [ ] Export CSV (séances, exercices, séries)
- [ ] Export PDF récapitulatif (graphiques inclus)
- [ ] Partage via Intent (email, Drive, etc.)
- [ ] Format compatible Excel

---

# 🥗 PHASE 5 - DASHBOARD BI NUTRITION (Priorité: HAUTE)

**Durée estimée** : 6-8 heures

### 1. **Base de Données Nutrition**
- [ ] Créer `FoodEntity` (nom, calories, protéines, glucides, lipides, portion)
- [ ] Créer `MealLogEntity` (date, type repas, aliments, quantités)
- [ ] Créer `DailyNutritionSummaryEntity` (date, total calories/macros)
- [ ] DAO + Repository
- [ ] Seed aliments courants (base 100-200 aliments)

### 2. **Interface Tracking Nutrition**
- [ ] Écran "Nutrition" dans bottom nav
- [ ] Card résumé journée :
  - Calories : 1850 / 2200 kcal
  - Protéines : 145g / 180g
  - Glucides : 180g / 220g
  - Lipides : 65g / 70g
- [ ] Progress bars colorées par macro
- [ ] Liste repas de la journée
- [ ] Bouton "+ Ajouter un repas"

### 3. **Ajout Manuel Repas**
- [ ] Écran sélection type repas (Petit-déj, Déjeuner, Dîner, Snack)
- [ ] Recherche aliments (SearchBar)
- [ ] Liste aliments trouvés
- [ ] Sélection quantité (grammes ou portions)
- [ ] Calcul automatique macros
- [ ] Sauvegarde `MealLogEntity`
- [ ] Recalcul `DailyNutritionSummaryEntity`

### 4. **Base Aliments Étendue**
- [ ] Intégrer API Open Food Facts (optionnel)
- [ ] Scanner code-barres (ML Kit Barcode)
- [ ] Ajout aliment personnalisé
- [ ] Favoris aliments fréquents
- [ ] Copier repas d'un autre jour

### 5. **Graphiques Nutrition**
- [ ] LineChart calories quotidiennes (7/30 jours)
- [ ] StackedBarChart macros par jour
- [ ] PieChart répartition macros
- [ ] Graphique compliance objectif (%)
- [ ] Corrélation calories vs poids corporel

### 6. **Objectifs Nutrition**
- [ ] Profil nutrition :
  - Objectif (sèche, prise de masse, maintenance)
  - TDEE calculé (âge, poids, taille, activité)
  - Macros cibles (protéines, glucides, lipides)
- [ ] Ajustement automatique si poids change
- [ ] Cycles (ex: 5 jours surplus, 2 jours déficit)

### 7. **Top Aliments Consommés**
- [ ] Liste aliments les plus mangés (30 jours)
- [ ] Fréquence + quantité totale
- [ ] Analyse qualité (aliments "propres" vs "junk")
- [ ] Suggestions alternatives saines

### 8. **Corrélation Nutrition/Performance**
- [ ] Graphique calories vs volume séance
- [ ] Graphique protéines vs progression force
- [ ] Insight : "Meilleure performance à 2500 kcal"
- [ ] Alerte si calories trop basses 3 jours d'affilée

### 9. **Planning Nutrition Hebdomadaire**
- [ ] Template plan alimentaire
- [ ] Génération plan basé sur objectifs
- [ ] Liste courses automatique
- [ ] Meal prep suggestions

---

# 📸 PHASE 6 - SCANNER IA CALORIES (Priorité: MOYENNE)

**Durée estimée** : 10-15 heures (complexe)

### 1. **Intégration Caméra**
- [ ] Permission CAMERA
- [ ] CameraX API pour preview
- [ ] Capture photo haute résolution
- [ ] Sélection image depuis galerie
- [ ] Crop/rotation image

### 2. **Modèle IA Reconnaissance Aliments**
- [ ] Intégrer TensorFlow Lite
- [ ] Télécharger modèle pré-entraîné (Food-101, Nutrition5k)
- [ ] Ou entraîner modèle custom
- [ ] Inférence sur device (offline)
- [ ] Détection multi-aliments dans une assiette

### 3. **Estimation Calories**
- [ ] Détection taille portion (ML)
- [ ] Comparaison avec objet référence (fourchette, main)
- [ ] Mapping aliment → base nutritionnelle
- [ ] Calcul calories + macros estimés
- [ ] Marge erreur affichée

### 4. **UI Scanner**
- [ ] Écran scan avec preview caméra
- [ ] Overlay guide (centrer assiette)
- [ ] Loading pendant inférence
- [ ] Résultats détection (liste aliments)
- [ ] Correction manuelle quantités
- [ ] Ajout direct au journal nutrition

### 5. **Amélioration Continue**
- [ ] Feedback utilisateur sur précision
- [ ] Historique photos repas
- [ ] Comparaison visuelle progression (photos body)

---

# 🔔 PHASE 7 - SYSTÈME NOTIFICATIONS (Priorité: HAUTE)

**Durée estimée** : 3-4 heures

### 1. **WorkManager Setup**
- [ ] Créer `NotificationWorker`
- [ ] Schedule notifications récurrentes
- [ ] Gestion rappels multiples par jour

### 2. **Notifications Repas**
- [ ] Notification petit-déjeuner (8h)
- [ ] Notification déjeuner (12h30)
- [ ] Notification snack pré-training (16h)
- [ ] Notification dîner (19h)
- [ ] Notification snack post-training
- [ ] Personnalisation horaires (Settings)
- [ ] Toggle ON/OFF par type repas

### 3. **Notifications Shakers Protéines**
- [ ] Notification shaker matin
- [ ] Notification shaker post-workout
- [ ] Notification shaker avant coucher
- [ ] Tracking compliance shakers

### 4. **Notification Heure Coucher**
- [ ] Notification 30min avant heure cible
- [ ] Rappel importance sommeil pour gains
- [ ] Tracking heures de sommeil (manual input)
- [ ] Corrélation sommeil vs performance

### 5. **Notifications Motivationnelles**
- [ ] Notification si pas de séance depuis 3 jours
- [ ] Notification nouveaux PR atteints
- [ ] Notification streak 7 jours
- [ ] Notification objectifs hebdomadaires atteints

### 6. **Paramètres Notifications**
- [ ] Écran Settings complet
- [ ] Toggle master ON/OFF
- [ ] Personnalisation par type
- [ ] Choix horaires
- [ ] Son/Vibration
- [ ] DND (Do Not Disturb) mode

---

# 👤 PHASE 8 - PROFIL & SETTINGS (Priorité: MOYENNE)

**Durée estimée** : 3-4 heures

### 1. **Écran Profil Utilisateur**
- [ ] Photo de profil (upload/caméra)
- [ ] Informations de base :
  - Nom, Prénom
  - Âge, Date de naissance
  - Poids actuel
  - Taille
  - Sexe
- [ ] Modification infos
- [ ] Sauvegarde dans `UserProfileEntity`

### 2. **Objectifs & Niveau**
- [ ] Sélection objectif (Sèche, Prise de masse, Maintenance, Recomposition)
- [ ] Niveau (Débutant, Intermédiaire, Avancé)
- [ ] Équipement disponible
- [ ] Jours d'entraînement par semaine

### 3. **Historique Poids Corporel**
- [ ] Input poids manuel (avec date)
- [ ] Graphique évolution poids (LineChart)
- [ ] Objectif poids cible
- [ ] Perte/gain par semaine
- [ ] Photos progression (avant/après)
- [ ] Mesures corporelles (tour de taille, bras, cuisses, etc.)

### 4. **Photos Progression**
- [ ] Upload photos front/side/back
- [ ] Comparaison photos par période
- [ ] Slider avant/après
- [ ] Galerie privée
- [ ] Rappel hebdomadaire "Prends ta photo"

### 5. **Settings Avancés**
- [ ] Unités (kg/lbs, cm/inches)
- [ ] Format date
- [ ] Langue (FR/EN)
- [ ] Thème (Clair/Sombre/Auto)
- [ ] Sauvegarde & Restore
- [ ] Suppression compte

---

# 🎬 PHASE 9 - ONBOARDING (Priorité: MOYENNE)

**Durée estimée** : 2-3 heures

### 1. **Welcome Flow**
- [ ] Splash screen avec logo
- [ ] Écran 1 : Welcome "Bienvenue sur ShredCoach"
- [ ] Écran 2 : Features principales (3 cards)
- [ ] Écran 3 : Permissions (notifications, caméra)

### 2. **Setup Initial**
- [ ] Écran saisie nom/prénom
- [ ] Écran saisie âge/poids/taille
- [ ] Écran sélection objectif (cards visuelles)
- [ ] Écran sélection niveau (Débutant/Inter/Avancé)
- [ ] Écran sélection équipement (checkboxes)
- [ ] Écran objectifs nutrition (TDEE auto-calculé)

### 3. **Tutorial Interactif**
- [ ] Guided tour : "Démarre ta première séance"
- [ ] Tooltips sur features principales
- [ ] Skip tutorial option
- [ ] Revoir tutorial dans Settings

### 4. **Logique First Launch**
- [ ] Flag `isFirstLaunch` (DataStore)
- [ ] Redirection automatique Onboarding si nouveau
- [ ] Sinon → Home directement

---

# 🚀 PHASE 10 - FEATURES AVANCÉES (Priorité: BASSE)

**Durée estimée** : 10-15 heures

### 1. **Planning Hebdomadaire**
- [ ] Vue calendrier semaine
- [ ] Planifier séances à l'avance
- [ ] Types : Full Body, Push/Pull/Legs, Upper/Lower
- [ ] Glisser-déposer séances
- [ ] Notifications rappel séance du jour

### 2. **Templates Séances Personnalisés**
- [ ] Créer template custom
- [ ] Sélection exercices manuellement
- [ ] Ordre exercices drag & drop
- [ ] Sauvegarder template
- [ ] Bibliothèque templates
- [ ] Dupliquer/Modifier templates

### 3. **Historique Complet**
- [ ] Liste toutes les séances passées
- [ ] Filtres (date, type, durée)
- [ ] Voir détails séance passée
- [ ] Rejouer une séance
- [ ] Supprimer séance

### 4. **Mode Déconnecté Robuste**
- [ ] Fonctionnement 100% offline
- [ ] Sync automatique au retour connexion
- [ ] Queue d'actions offline
- [ ] Indicateur status connexion

### 5. **Synchronisation Cloud**
- [ ] Firebase Authentication
- [ ] Firestore pour backup données
- [ ] Sync multi-devices
- [ ] Résolution conflits
- [ ] Export/Import manuel

### 6. **Social & Partage**
- [ ] Partager séance sur réseaux
- [ ] Partager PR/records
- [ ] Mode "Ami" (voir progression amis)
- [ ] Challenges (ex: "100 séances en 2026")

### 7. **Coaching Intelligent**
- [ ] Analyse performances (plateau détection)
- [ ] Suggestions décharge
- [ ] Conseils progression
- [ ] Alertes surentraînement
- [ ] Programme adaptatif (auto-progression poids)

### 8. **Accessibilité**
- [ ] TalkBack support
- [ ] Tailles texte
- [ ] Contraste élevé
- [ ] Navigation clavier
- [ ] Voice commands

---

# 🎨 PHASE 11 - POLISSAGE & QUALITÉ (Priorité: HAUTE avant launch)

**Durée estimée** : 5-8 heures

### 1. **Design & Animations**
- [ ] Splash screen animé
- [ ] Transitions écrans fluides (SharedElement)
- [ ] Animations boutons (ripple, scale)
- [ ] Lottie animations (success, loading)
- [ ] Micro-interactions (haptic feedback)
- [ ] Skeleton loading states
- [ ] Empty states avec illustrations

### 2. **Icône & Branding**
- [ ] Icône app professionnelle (adaptive icon)
- [ ] Logo vectoriel
- [ ] Color scheme cohérent
- [ ] Typography harmonieuse
- [ ] Illustrations custom (si budget)

### 3. **Optimisation Performances**
- [ ] Profiling avec Android Studio Profiler
- [ ] Optimisation requêtes Room (indexes)
- [ ] LazyColumn optimisations
- [ ] Image loading optimisé (Coil cache)
- [ ] Réduction taille APK
- [ ] Obfuscation R8/ProGuard
- [ ] Baseline profiles (Jetpack Compose)

### 4. **Tests**
- [ ] Tests unitaires ViewModels (80% coverage)
- [ ] Tests repositories
- [ ] Tests use cases
- [ ] Tests UI (Compose Testing)
- [ ] Tests intégration database
- [ ] Tests screenshot (Paparazzi)
- [ ] CI/CD setup (GitHub Actions)

### 5. **Gestion Erreurs**
- [ ] Crash reporting (Firebase Crashlytics)
- [ ] Error boundaries
- [ ] Messages d'erreur user-friendly
- [ ] Retry mechanisms
- [ ] Offline fallbacks

### 6. **Sécurité**
- [ ] Obfuscation code
- [ ] ProGuard rules
- [ ] Validation inputs
- [ ] SQL injection prevention (Room auto)
- [ ] Secure data storage (EncryptedSharedPreferences pour données sensibles)

---

# 📱 PHASE 12 - DÉPLOIEMENT PLAY STORE (Priorité: HAUTE avant launch)

**Durée estimée** : 3-5 heures

### 1. **Préparation App**
- [ ] Version finale signée (release build)
- [ ] App Bundle (.aab)
- [ ] Version codes & names
- [ ] Permissions minimales
- [ ] Tester sur 5+ devices différents

### 2. **Assets Play Store**
- [ ] Screenshots (6-8 images) :
  - Téléphone (16:9)
  - Tablette 7" et 10"
- [ ] Feature Graphic (1024x500)
- [ ] Icône haute résolution (512x512)
- [ ] Vidéo promo (optionnel mais recommandé)

### 3. **Textes Marketing**
- [ ] Titre app (30 caractères)
- [ ] Description courte (80 caractères)
- [ ] Description longue (4000 caractères) :
  - Features clés
  - Avantages
  - Comment ça marche
  - Keywords pour ASO
- [ ] Release notes

### 4. **Compliance**
- [ ] Politique de confidentialité (hébergée sur site web)
- [ ] Conditions d'utilisation
- [ ] Questionnaire contenu Google
- [ ] Catégorie app (Santé & Fitness)
- [ ] Classification de contenu (PEGI)
- [ ] Déclarations données utilisateurs

### 5. **Stratégie Launch**
- [ ] Beta testing (Google Play Console - Track fermé)
- [ ] Recrutement beta testers (50-100 personnes)
- [ ] Collecte feedback
- [ ] Corrections bugs
- [ ] Soft launch (1 pays test)
- [ ] Full launch mondial

### 6. **Post-Launch**
- [ ] Monitoring analytics (Firebase Analytics)
- [ ] Réponses avis utilisateurs
- [ ] Roadmap mises à jour
- [ ] Marketing (réseaux sociaux)

---

# 📊 RÉCAPITULATIF ESTIMATIONS

| Phase | Priorité | Durée | Statut |
|-------|----------|-------|--------|
| 3.3 - Améliorations Séance | 🔴 HAUTE | 2-3h | ⏳ TODO |
| 4 - Dashboard BI Sportif | 🔴 TRÈS HAUTE | 5-7h | ⏳ TODO |
| 5 - Dashboard BI Nutrition | 🔴 HAUTE | 6-8h | ⏳ TODO |
| 6 - Scanner IA Calories | 🟡 MOYENNE | 10-15h | ⏳ TODO |
| 7 - Notifications | 🔴 HAUTE | 3-4h | ⏳ TODO |
| 8 - Profil & Settings | 🟡 MOYENNE | 3-4h | ⏳ TODO |
| 9 - Onboarding | 🟡 MOYENNE | 2-3h | ⏳ TODO |
| 10 - Features Avancées | 🟢 BASSE | 10-15h | ⏳ TODO |
| 11 - Polissage & Qualité | 🔴 HAUTE | 5-8h | ⏳ TODO |
| 12 - Déploiement Play Store | 🔴 HAUTE | 3-5h | ⏳ TODO |

**TOTAL ESTIMÉ : 50-75 heures de développement**

---

# 🎯 RECOMMANDATION ORDRE D'IMPLÉMENTATION

## 🥇 **Sprint 1 - MVP Complet** (15-20h)
1. Phase 4 - Dashboard BI Sportif ⭐ **PRIORITÉ #1**
2. Phase 5 - Dashboard BI Nutrition (partie basique)
3. Phase 7 - Notifications repas/shakers
4. Phase 3.3 - GIFs + Suggestions poids

→ **App VRAIMENT utilisable au quotidien**

## 🥈 **Sprint 2 - Profil & Onboarding** (8-10h)
1. Phase 8 - Profil complet
2. Phase 9 - Onboarding
3. Phase 7 - Compléter notifications (coucher, motivation)

→ **Experience utilisateur complète**

## 🥉 **Sprint 3 - Features Avancées** (15-20h)
1. Phase 10 - Planning, Templates, Historique
2. Phase 5 - Dashboard Nutrition avancé
3. Phase 3.3 - Notification arrière-plan séance

→ **App riche en fonctionnalités**

## 🏅 **Sprint 4 - IA & Polissage** (15-20h)
1. Phase 6 - Scanner IA Calories
2. Phase 11 - Polissage, animations, tests
3. Phase 10 - Social, Cloud sync

→ **App premium & moderne**

## 🚀 **Sprint 5 - Launch** (5-8h)
1. Phase 12 - Préparation Play Store
2. Beta testing
3. Corrections finales
4. Launch ! 🎉

---

# 💡 NOTES IMPORTANTES

### **Scope Minimum pour Play Store** :
- ✅ Phase 3.2 (actuel)
- ✅ Phase 4 (Dashboard BI Sportif)
- ✅ Phase 5 (Nutrition basique)
- ✅ Phase 7 (Notifications)
- ✅ Phase 8 (Profil)
- ✅ Phase 9 (Onboarding)
- ✅ Phase 11 (Polissage essentiel)
- ✅ Phase 12 (Déploiement)

**= ~35-45 heures de dev**

### **Features "Nice to Have"** :
- Scanner IA (Phase 6)
- Social/Cloud (Phase 10)
- Coaching intelligent (Phase 10)

**= +20-30 heures**

### **Recommandations** :
1. **Focuser sur Sprint 1** pour avoir MVP solide
2. **Beta tester** avant d'ajouter features complexes
3. **Itérer** selon feedback utilisateurs
4. **Ne pas sur-développer** avant validation marché

---

**Développé par Claude (Anthropic)**
**Date : Avril 2026**
**Version du document : 1.0**
