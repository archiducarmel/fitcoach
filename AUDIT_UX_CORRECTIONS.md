# AUDIT UX/UI — GUIDE DE CORRECTIONS PREMIUM++

## ShredCoach Android — Liste exhaustive des problemes et guidelines de correction

> Chaque probleme identifie est documente avec :
> - **Page** : ecran concerne
> - **Emplacement** : localisation precise sur l'ecran
> - **Probleme** : description detaillee
> - **Guideline** : correction precise pour atteindre le niveau Premium++

---

# SECTION A — PROBLEMES CRITIQUES (bloquants)

---

## A.01 — Absence de Bottom Navigation

- **Page** : Globale (toute l'application)
- **Emplacement** : Bas de l'ecran — absent
- **Probleme** : L'application utilise une navigation "hub & spoke" ou l'utilisateur doit revenir au HomeScreen pour acceder a chaque section. C'est un anti-pattern majeur pour une app a 6+ destinations. Cela genere +2 taps par changement de section et empeche toute fluidite de navigation. Aucune app premium du marche ne fonctionne sans barre de navigation persistante.
- **Guideline** :
  - Implementer une `NavigationBar` Material Design 3 persistante en bas de tous les ecrans principaux
  - 5 destinations : **Accueil** (Home icon) | **Seance** (FitnessCenter icon) | **Exercices** (ListAlt icon) | **Stats** (BarChart icon) | **Profil** (Person icon)
  - Icones : outlined pour l'etat inactif, filled pour l'etat actif
  - Couleur active : `OrangeVibrant` pour l'icone et le label
  - Couleur inactive : `onSurfaceVariant` (gris M3)
  - Background : `DarkSurface` avec une elevation de 3dp et un blur subtil de 8dp
  - Hauteur : 80dp (standard M3)
  - Ajouter un indicateur de selection : pilule horizontale arrondie (64x32dp, `OrangeVibrant.copy(alpha = 0.12f)`) derriere l'icone active
  - Animer le switch entre destinations avec `slideInHorizontally` + `fadeIn` (300ms, EaseOutCubic)
  - Masquer la nav bar uniquement pendant `WorkoutSessionScreen` (ecran immersif)
  - La barre de session active doit s'afficher AU-DESSUS de la nav bar, pas a la place

---

## A.02 — Chip "Intermediaire" tronque (texte coupe sur 2 lignes)

- **Page** : OnboardingScreen (page 5), SettingsScreen (section "Mon profil"), WorkoutGeneratorScreen (section "Votre niveau")
- **Emplacement** : FilterChip central dans la rangee de 3 chips (Debutant / Intermediaire / Avance)
- **Probleme** : Le mot "Intermediaire" deborde du FilterChip et passe a la ligne, affichant "Intermediair" + "e" en dessous. Le chip n'a pas assez de largeur pour contenir le texte. C'est un bug visuel qui apparait sur 3 ecrans differents et donne une impression d'application non testee.
- **Guideline** :
  - **Option 1 (recommandee)** : Remplacer les 3 FilterChips par un `SegmentedButton` Material 3 qui gere automatiquement la repartition de largeur (chaque segment fait 1/3 de la largeur disponible)
  - **Option 2** : Ajouter `Modifier.weight(1f)` a chaque chip dans la Row et `overflow = TextOverflow.Ellipsis` + `maxLines = 1` sur le texte
  - **Option 3** : Raccourcir le texte en "Inter." uniquement si la largeur est insuffisante — utiliser `BoxWithConstraints` pour detecter
  - Appliquer la meme correction aux 3 ecrans concernes
  - Tester sur un ecran de 360dp de large (taille minimale courante) pour valider
  - Padding interne des chips : 12dp horizontal, 8dp vertical minimum
  - Font : `labelLarge` (14sp, SemiBold) — ne pas descendre en dessous

---

## A.03 — Badge de difficulte affiche verticalement (lettres empilees)

- **Page** : ExerciseDetailScreen
- **Emplacement** : Sous le nom de l'exercice, a droite des badges "Groupe musculaire" et "Variante" — le 3eme badge (difficulte) s'affiche en colonne verticale
- **Probleme** : Le badge "Intermediaire" (ou tout autre niveau de difficulte) est rendu avec chaque lettre sur une ligne separee, formant une colonne "I/n/t/e/r/m/e/d/i/a/i/r/e" le long du bord droit de l'ecran. C'est un bug de layout critique — probablement cause par un conteneur `Row` avec `weight(1f)` qui ne laisse que quelques pixels au dernier element, forcant un word-wrap extreme.
- **Guideline** :
  - Localiser dans `ExerciseDetailScreen.kt` la `Row` contenant les 3 badges (muscle group, variant, difficulty)
  - Remplacer la `Row` unique par un `FlowRow` (Material 3) qui wraps automatiquement les badges a la ligne si l'espace est insuffisant
  - Chaque badge doit avoir `Modifier.wrapContentWidth()` (jamais `weight(1f)`)
  - Padding interne de chaque badge : 10dp horizontal, 6dp vertical
  - Espacement entre badges : 8dp horizontal, 6dp vertical
  - Corner radius : 8dp (RoundedCornerShape)
  - Assurer que le texte du badge est toujours sur une seule ligne avec `maxLines = 1` et `overflow = TextOverflow.Ellipsis`
  - Tester avec tous les noms de difficulte : "Debutant", "Intermediaire", "Avance"

---

## A.04 — 4 couleurs de selection differentes selon les ecrans

- **Page** : Globale — Onboarding (pages 3, 4, 5), WorkoutGeneratorScreen, SettingsScreen, CustomWorkoutScreen, WorkoutSessionScreen
- **Emplacement** : Tous les composants interactifs de selection (chips, cards selectionnables, toggles, boutons +/-)
- **Probleme** : L'application utilise 4 couleurs differentes pour indiquer l'etat "selectionne" selon les ecrans :
  - **Violet** (`VioletDark #5B21B6`) : boutons Homme/Femme, chips de niveau/equipement dans Settings, boutons +/- dans Session et Custom
  - **Orange** (`OrangeVibrant #FF6B35`) : border de selection des objectifs dans Onboarding
  - **Vert** (`NeonGreen #10B981`) : border de selection equipement dans Onboarding
  - **Jaune** (`BrightYellow #FBBF24`) : fond de selection duree et equipement dans Generator
  
  Cette inconsistance empeche l'utilisateur de reconnaitre le pattern "selectionne" d'un ecran a l'autre. C'est la violation la plus grave du design system.
- **Guideline** :
  - Definir UN token unique `SelectedColor = OrangeVibrant` applique partout
  - **Pour les FilterChips / SegmentedButtons selectionnes** :
    - Fond : `OrangeVibrant.copy(alpha = 0.15f)`
    - Texte : `OrangeVibrant`
    - Border : `OrangeVibrant` (1.5dp)
    - Etat non-selectionne : fond `surfaceVariant`, texte `onSurfaceVariant`, border `outlineVariant`
  - **Pour les Cards selectionnables** (objectif, equipement) :
    - Fond selectionne : `OrangeVibrant.copy(alpha = 0.10f)`
    - Border selectionne : `OrangeVibrant` (2dp)
    - Elevation selectionnee : 4dp (vs 1dp au repos)
    - Fond non-selectionne : `surfaceVariant`, border `outlineVariant` (1dp)
  - **Pour les boutons +/-** :
    - Couleur : `OrangeVibrant` (fond) avec texte blanc
    - Etat pressed : `OrangeVibrant` assombri de 15%
  - **Eliminer completement** le violet, le jaune et le vert des composants de selection
  - Creer un composable reutilisable `SelectableCard` et un `SelectableChip` centralises dans le design system
  - Ne conserver `NeonGreen` que pour : succes, completion, validation
  - Ne conserver le violet pour RIEN — le supprimer du design system interactif

---

## A.05 — Boutons +/- de la session trop petits et en violet sombre

- **Page** : WorkoutSessionScreen, CustomWorkoutScreen
- **Emplacement** : Dans la carte de serie active — les boutons circulaires "-" et "+" entourant les champs de saisie "Poids (kg)" et "Repetitions"
- **Probleme** : Ces boutons sont les elements les plus tapes de toute l'application (des dizaines de taps par seance). Ils sont en `VioletDark` (#5B21B6) sur fond `DarkSurface` (#1E293B), ce qui offre un contraste de seulement 3.2:1 (en dessous du ratio WCAG AA de 4.5:1). Leur taille de 48dp est le minimum Material Design, insuffisant pour un usage en condition d'effort (mains moites, fatigue, attention reduite). De plus, il n'y a pas de haptic feedback sur le tap.
- **Guideline** :
  - **Taille** : Augmenter a 56dp (idealement 64dp pour un usage fitness)
  - **Couleur** : `OrangeVibrant` (#FF6B35) en fond, texte blanc. Contraste 5.8:1 — conforme WCAG AA
  - **Forme** : CircleShape, elevation 2dp
  - **Etat pressed** : Scale 0.92 + `OrangeVibrant` assombri 20% + elevation 0dp — animation 100ms
  - **Haptic feedback** : Ajouter `hapticClick()` (vibration 20ms) a CHAQUE tap
  - **Long press** : Implementer un auto-increment : +1 par 200ms tant que le bouton est presse. Ajouter un haptic leger tous les 5 increments
  - **Texte des boutons** : "+" et "-" en 24sp Bold (au lieu de 20sp)
  - **Espacement** : 12dp entre les boutons et le TextField central
  - Le champ de saisie central (poids/reps) doit etre de 64dp de hauteur avec texte 28sp Bold centre
  - Sur `CustomWorkoutScreen`, les boutons sont encore plus petits (28dp) — les augmenter a 40dp minimum

---

# SECTION B — PROBLEMES MAJEURS

---

## B.01 — Absence totale d'animations et transitions entre ecrans

- **Page** : Globale — toutes les navigations entre ecrans
- **Emplacement** : Chaque appel `navController.navigate()`
- **Probleme** : Les ecrans apparaissent et disparaissent sans aucune transition. Pas de slide, pas de fade, pas de shared element. Cela donne une impression de prototype, pas de produit fini. Les apps premium utilisent systematiquement des animations de navigation (300-400ms).
- **Guideline** :
  - Definir des transitions par defaut dans le `NavHost` :
    - **Enter** : `slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300))`
    - **Exit** : `slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(tween(200))`
    - **PopEnter** : `slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(tween(300))`
    - **PopExit** : `slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200))`
  - Pour l'onboarding : utiliser `HorizontalPager` avec spring animation au lieu de navigation composable
  - Pour le detail exercice : utiliser `SharedTransitionLayout` (Compose 1.7+) pour animer le GIF depuis la liste vers le hero
  - Pour les dialogs (AddMeal, WeightLog) : remplacer par des `BottomSheet` avec animation slide-up native
  - Pour la transition exercice "Excellent !" : ajouter un `fadeIn` (500ms) + scale up de 0.8 a 1.0 sur le check vert
  - Pour le timer de repos : animate le cercle avec `drawArc` progressif (smooth 60fps)

---

## B.02 — Ecrans trop longs sans sections collapsibles

- **Page** : ProfileScreen, SettingsScreen, DashboardScreen
- **Emplacement** : Tout le contenu scrollable — 3 a 5 scrolls necessaires pour atteindre le bas
- **Probleme** : Ces ecrans empilent des dizaines de sections dans un scroll vertical infini. L'utilisateur perd le contexte, ne sait pas ou il en est, et oublie des sections entieres. Le Dashboard est particulierement dense avec 8+ sections analytiques.
- **Guideline** :
  - **ProfileScreen** : Reorganiser en onglets ou sections collapsibles
    - Tab 1 : "Informations" (nom, age, taille, sexe)
    - Tab 2 : "Suivi poids" (pesees, graphique, objectif)
    - Tab 3 : "Mensurations" (tour de taille, bras, etc.)
    - Implementer avec `ScrollableTabRow` + `HorizontalPager` (swipe entre tabs)
    - Deplacer "Photos de progression" et "Zone dangereuse" dans un menu overflow (3 dots)
  - **SettingsScreen** : Utiliser des `ExpandableCard` pour chaque section
    - Par defaut : toutes les sections collapsed, montrant seulement l'icone + titre
    - Tap : expand avec animation `animateContentSize()` (300ms, EaseOutCubic)
    - Chevron de rotation (90deg) pour indiquer l'etat expand/collapse
  - **DashboardScreen** : 
    - Utiliser un `LazyColumn` avec `stickyHeader` pour les titres de section
    - Ajouter un scroll-to-section rapide via des chips en haut (Resume | Records | Graphiques | Tendances)
    - Charger les graphiques en lazy (ne render que ce qui est visible)

---

## B.03 — Pas de thumbnails GIF dans la liste d'exercices

- **Page** : ExercisesScreen
- **Emplacement** : Chaque card d'exercice dans la `LazyColumn`
- **Probleme** : Les cards d'exercice ne montrent que du texte (nom, groupe musculaire, description, stats). L'utilisateur ne peut pas visuellement identifier un exercice sans cliquer dessus. Toutes les apps fitness concurrentes (Hevy, JEFIT, Nike Training) affichent une miniature visuelle dans les listes.
- **Guideline** :
  - Ajouter un thumbnail GIF de 64x64dp a gauche de chaque card exercice
  - Utiliser `SubcomposeAsyncImage` avec Coil et `ImageDecoderDecoder` pour l'animation
  - Le GIF doit etre dans un `Box` avec `RoundedCornerShape(12.dp)` et `Modifier.clip()`
  - Pendant le chargement : afficher un placeholder shimmer (rectangle gris anime)
  - En cas d'erreur : afficher l'icone `FitnessCenter` sur fond `surfaceVariant`
  - Le GIF doit boucler infiniment a vitesse normale
  - Pour la performance : utiliser `size(64)` dans la requete Coil pour ne pas charger le GIF en pleine resolution
  - Layout de la card : `Row` avec thumbnail (64dp) + spacer(12dp) + `Column` (texte)
  - Ajouter `ContentScale.Crop` sur le thumbnail pour un cadrage serre

---

## B.04 — Couleurs semantiques incorrectes (rouge pour actions positives)

- **Page** : ProfileScreen, SettingsScreen
- **Emplacement** :
  - ProfileScreen : bouton "Photos de progression" (full-width, rouge vif)
  - SettingsScreen : bouton "Modifier mon profil" (full-width, rouge vif)
- **Probleme** : Le rouge est universellement associe a "danger", "erreur", "suppression" en UX. Utiliser du rouge pour "Photos de progression" (action positive, motivante) et "Modifier mon profil" (action neutre, courante) est une erreur semantique grave qui cree de l'anxiete inconsciente chez l'utilisateur.
- **Guideline** :
  - **"Photos de progression"** : Fond `OrangeVibrant`, texte blanc, icone Camera. C'est une action motivante — elle merite la couleur primaire.
  - **"Modifier mon profil"** : Style `OutlinedButton` avec border `outlineVariant` et texte `onSurface`. C'est une action secondaire courante — style neutre.
  - Reserver le rouge (`ErrorRed`) EXCLUSIVEMENT pour :
    - Le bouton "Supprimer toutes les donnees" (Zone dangereuse)
    - Les messages d'erreur de validation
    - Les etats d'erreur des champs
  - Creer un `enum class ButtonStyle { PRIMARY, SECONDARY, DANGER }` pour standardiser

---

## B.05 — Redondance entre Settings et Profile

- **Page** : SettingsScreen (section "Mon profil") + ProfileScreen (section "Objectif")
- **Emplacement** : Les chips Niveau (Debutant/Intermediaire/Avance), Equipement (Salle/Home/Bodyweight), Objectif (Seche/Bulk/Maintien) sont editables aux DEUX endroits
- **Probleme** : L'utilisateur ne sait pas quelle modification prime. S'il change son niveau dans Settings puis va dans Profile, il voit l'ancienne valeur (ou vice versa selon l'implementation). Double source de verite = bugs inevitables.
- **Guideline** :
  - **Supprimer** la section "Mon profil" du SettingsScreen. Les parametres de profil appartiennent au ProfileScreen.
  - SettingsScreen ne doit contenir QUE des preferences de comportement de l'app :
    - "Pendant la seance" (auto-start, vibration, son, tips, repos default)
    - "Notifications" (toggles et heures)
    - "Affichage" (theme, unites)
    - "A propos" (version, liens)
  - ProfileScreen gere tout ce qui concerne l'utilisateur :
    - Identite (nom, age, taille, sexe)
    - Objectifs (seche/bulk/maintien, niveau, equipement)
    - Suivi corporel (poids, mesures)
    - Nutrition cibles (calories, proteines)
  - Ajouter un lien "Modifier mes objectifs" dans Settings qui navigue vers la section correspondante du Profile

---

## B.06 — Bouton "Appliquer les notifications" a action manuelle

- **Page** : SettingsScreen
- **Emplacement** : Sous la section "Notifications" — bouton full-width orange "Appliquer les notifications"
- **Probleme** : L'utilisateur doit explicitement taper un bouton pour que les changements de notification prennent effet. C'est un pattern obsolete. Les apps modernes appliquent les changements en temps reel des que l'utilisateur toggle un switch ou modifie une heure.
- **Guideline** :
  - Supprimer le bouton "Appliquer les notifications"
  - Chaque toggle de notification doit appeler immediatement `NotificationScheduler.schedule()` ou `.cancel()` dans le `onCheckedChange`
  - Chaque modification d'heure doit immediatement reprogrammer l'alarme correspondante
  - Ajouter un `Snackbar` temporaire (3s) en bas de l'ecran : "Notification mise a jour" avec icone check
  - Si la permission POST_NOTIFICATIONS n'est pas accordee, afficher un `Snackbar` avec action "Autoriser" qui ouvre les parametres systeme

---

## B.07 — Pas de drag & drop pour reordonner les exercices custom

- **Page** : CustomWorkoutScreen
- **Emplacement** : La liste des exercices (cards numerotees 1 a 12)
- **Probleme** : L'utilisateur ne peut pas reordonner les exercices de sa seance custom. L'ordre est fixe selon l'ordre d'ajout. Pour une seance personnalisee, pouvoir reordonner est essentiel (ex: placer les exercices composes avant les isolations).
- **Guideline** :
  - Ajouter une icone "drag handle" (6 dots verticaux) a gauche de chaque card exercice
  - Implementer le reordonnement avec `LazyColumn` + `rememberReorderableLazyColumnState` (librairie `org.burnoutcrew.reorderable` ou implementation custom avec `detectDragGesturesAfterLongPress`)
  - Lors du drag : la card selectionnee doit avoir une elevation de 8dp + scale 1.02 + fond legerement plus clair
  - Les autres cards doivent s'ecarter avec une animation `animateItemPlacement()` (300ms)
  - Haptic feedback : vibration legere au debut du drag et au drop
  - Les numeros (1, 2, 3...) doivent se remettre a jour automatiquement apres le drop
  - Alternative plus simple : boutons fleche haut/bas sur chaque card (moins elegant mais plus simple a implementer)

---

## B.08 — Pas de Snackbar/Toast de confirmation apres les actions

- **Page** : Globale — toutes les actions de sauvegarde/modification
- **Emplacement** : Apres chaque action utilisateur significative (save profil, ajout pesee, ajout repas, save seance favorite, export CSV, etc.)
- **Probleme** : L'utilisateur n'a aucun feedback visuel apres avoir effectue une action. Quand il tape "Enregistrer" dans le profil, rien ne se passe visuellement. Il ne sait pas si l'action a reussi ou echoue. C'est anxiogene.
- **Guideline** :
  - Implementer un systeme de `Snackbar` centralise dans le `Scaffold` principal
  - Utiliser un `SnackbarHostState` partage via le NavHost
  - Style du Snackbar :
    - Fond : `surfaceVariant` avec elevation 6dp
    - Texte : `bodyMedium` en `onSurface`
    - Icone : Check vert pour succes, Warning orange pour attention
    - Duree : 3 secondes, dismissible par swipe
    - Position : au-dessus de la Bottom Navigation
  - Actions necessitant un Snackbar :
    - "Profil mis a jour" (apres save profil)
    - "Pesee ajoutee : 80.0 kg" (apres ajout poids)
    - "Repas ajoute" (apres ajout nutrition)
    - "Seance ajoutee aux favoris" (avec action "Annuler")
    - "Mesures sauvegardees"
    - "Donnees exportees" (avec action "Partager")
    - "Notifications mises a jour"
    - "Photo sauvegardee"

---

## B.09 — Card hero de la preview trop haute

- **Page** : WorkoutPreviewScreen
- **Emplacement** : La carte orange en haut de l'ecran avec "Seance Full Body", duree, repartition echauffement/exos/cardio
- **Probleme** : La card hero occupe environ 40% du viewport initial. L'utilisateur doit scroller pour voir la liste des exercices qui est le contenu principal de cet ecran. La repartition echauffement/exos/cardio pourrait etre plus compacte.
- **Guideline** :
  - Reduire la hauteur de la card hero :
    - Titre "Seance Full Body" + duree sur UNE ligne (Row au lieu de Column)
    - Repartition echauffement/exos/cardio en chips horizontaux (pas en colonnes avec icones)
    - Padding interne : 16dp au lieu de 20dp
    - Supprimer l'icone decorative (croix fitness) qui prend de la place
  - Implementer un `CollapsingTopAppBar` : la card hero se reduit a une barre compacte (titre + duree) quand l'utilisateur scrolle vers le bas
  - Animation de collapse : `animateContentSize()` avec `LinearOutSlowIn`
  - Hauteur expanded : ~160dp. Hauteur collapsed : ~56dp
  - Garder les infos essentielles visibles en mode collapsed (nom + duree)

---

## B.10 — Empty states generiques sans illustrations

- **Page** : DashboardScreen (graphiques), ProgressPhotosScreen, NutritionScreen (jour vide), FavoriteWorkoutsScreen (si vide)
- **Emplacement** : Zone centrale de l'ecran quand aucune donnee n'est disponible
- **Probleme** : Les empty states actuels affichent juste un texte gris centre ("Pas assez de donnees", "Aucune photo"). C'est demotivant et froid. Les apps premium transforment les empty states en opportunites de motivation et d'engagement.
- **Guideline** :
  - Chaque empty state doit contenir :
    1. **Illustration** : SVG ou animation Lottie thematique (150x150dp)
    2. **Titre motivant** : `titleLarge`, Bold, `onSurface`
    3. **Description** : `bodyMedium`, `onSurfaceVariant`, 2 lignes max
    4. **CTA** : bouton primaire qui guide vers l'action
  - Exemples :
    - **Stats vides** : Illustration d'un graphique montant → "Ta premiere seance sera ta reference !" → bouton "Commencer une seance"
    - **Photos vides** : Illustration d'un avant/apres stylise → "Capture ta transformation" → bouton "Prendre ma premiere photo"
    - **Nutrition jour vide** : Illustration d'une assiette → "Qu'est-ce qu'on mange aujourd'hui ?" → bouton "Ajouter un repas"
    - **Favoris vides** : Illustration d'un coeur + dumbbell → "Sauvegarde tes meilleures seances" → bouton "Generer une seance"
    - **Graphique insuffisant** : Afficher un graphique fictif grise avec overlay → "Encore 2 seances pour debloquer les tendances"
  - Utiliser des illustrations vectorielles dans un style coherent (ligne fine, couleur `OrangeVibrant` en accent)
  - Les animations Lottie doivent etre legeres (<50KB) et boucler une seule fois

---

## B.11 — Pas de salutation personnalisee sur le HomeScreen

- **Page** : HomeScreen
- **Emplacement** : Top bar — affiche "ShredCoach" comme titre avec icones Profile/Settings
- **Probleme** : L'utilisateur est accueilli par le nom de l'app au lieu d'une salutation personnalisee. C'est une occasion manquee de creer un lien emotionnel et de donner le sentiment d'un coaching personnalise. Toutes les apps premium de fitness (Nike, Peloton, Fitbod) saluent l'utilisateur par son prenom.
- **Guideline** :
  - Remplacer "ShredCoach" par une salutation contextuelle :
    - Avant 12h : "Bonjour, {prenom}"
    - 12h-18h : "Bon apres-midi, {prenom}"
    - Apres 18h : "Bonsoir, {prenom}"
  - Sous la salutation, ajouter un sous-titre motivationnel contextuel :
    - Jour de seance planifiee : "C'est jour de seance !"
    - Lendemain de seance : "Super seance hier, {volume}kg souleves"
    - Streak actif : "3 jours de suite, continue !"
    - Defaut : "On s'y met ?"
  - Style : titre en `headlineMedium` Bold, sous-titre en `bodyMedium` `onSurfaceVariant`
  - Le logo "ShredCoach" peut rester en petit (labelSmall) au-dessus de la salutation ou dans l'icone de l'app en top-left

---

## B.12 — Stats "0" affichees sans contexte pour nouvel utilisateur

- **Page** : HomeScreen
- **Emplacement** : Les 3 mini-cards de statistiques sous le CTA orange ("0 Seances", "0 kg Volume", "56 Exercices")
- **Probleme** : Afficher des zeros est demotivant pour un nouvel utilisateur. De plus, "56 Exercices" est une donnee systeme (nombre total dans la base), pas une stat utilisateur — c'est trompeur. Ces stats ne deviennent utiles qu'apres la premiere seance.
- **Guideline** :
  - **Pour les nouveaux utilisateurs** (0 seances) : Masquer la rangee de stats et la remplacer par un card motivationnel :
    - Fond : `NeonGreen.copy(alpha = 0.08f)`
    - Icone : EmojiEvents (trophee)
    - Texte : "Ta premiere seance t'attend ! Debloque tes stats."
    - Tap : navigue vers WorkoutGeneratorScreen
  - **Apres la premiere seance** : Afficher les stats avec des labels clairs
    - "X Seances" : nombre total de seances completees
    - "X kg" : volume total souleve
    - "X min" : temps total d'entrainement (plus pertinent que le nombre d'exercices en base)
  - Ajouter une animation `AnimatedContent` pour le changement de valeur (counter animation)
  - Chaque mini-card doit avoir un tap action : naviguer vers la section stats correspondante

---

# SECTION C — PROBLEMES MINEURS

---

## C.01 — Prenom non capitalise dans le profil

- **Page** : ProfileScreen
- **Emplacement** : Champ "Prenom" et affichage du nom sous l'avatar
- **Probleme** : Le prenom "si" est affiche en minuscules sous l'avatar et dans le champ. Pas de capitalisation automatique.
- **Guideline** :
  - Ajouter `keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)` sur les champs Prenom et Nom
  - Lors de la sauvegarde, appliquer `name.replaceFirstChar { it.uppercase() }` pour forcer la majuscule
  - L'affichage sous l'avatar doit toujours capitaliser la premiere lettre

---

## C.02 — Clavier numerique couvre le contenu en session

- **Page** : WorkoutSessionScreen
- **Emplacement** : Lorsque l'utilisateur tape dans les champs de poids ou repetitions
- **Probleme** : Le clavier numerique qui s'ouvre cache la moitie inferieure de l'ecran, y compris le bouton "SERIE TERMINEE". L'utilisateur doit fermer le clavier pour valider.
- **Guideline** :
  - **Option 1 (recommandee)** : Remplacer les `OutlinedTextField` par un `NumberPicker` custom (roue de defilement vertical) qui ne necessite pas de clavier. Range : 0-300 pour le poids, 0-50 pour les reps. Step : 2.5kg pour le poids, 1 pour les reps.
  - **Option 2** : Ajouter `Modifier.imePadding()` au `Scaffold` pour que le contenu remonte automatiquement avec le clavier. S'assurer que le bouton "SERIE TERMINEE" reste visible au-dessus du clavier.
  - Ajouter `imeAction = ImeAction.Done` + `keyboardActions = KeyboardActions(onDone = { validerSerie() })` pour permettre la validation directe depuis le clavier
  - Ajouter `Modifier.focusRequester()` pour naviguer automatiquement du champ poids au champ reps au tap sur "Next" du clavier

---

## C.03 — Bouton "Refaire" trop petit dans les series completees

- **Page** : WorkoutSessionScreen
- **Emplacement** : Dans les cards de series completees (fond verdatre), a droite — texte "Refaire" avec icone Replay
- **Probleme** : Le lien "Refaire" est en `bodySmall` (12sp) avec une icone de 14dp. C'est quasiment invisible et impossible a taper avec precision pendant l'effort. Or, corriger une serie mal enregistree est une action importante.
- **Guideline** :
  - Augmenter la taille de la zone tactile a 44dp minimum (meme si l'element visuel est plus petit)
  - Icone Replay : 18dp, couleur `OrangeVibrant`
  - Texte "Refaire" : `labelMedium` (12sp) — OK pour la taille texte, mais augmenter le `Modifier.clickable()` padding a 12dp autour
  - Alternative : remplacer par un `IconButton` de 36dp avec tooltip "Refaire cette serie"

---

## C.04 — Pas de suggestions de poids basees sur l'historique

- **Page** : WorkoutSessionScreen
- **Emplacement** : Champ de saisie du poids dans la carte de serie active
- **Probleme** : Le poids pre-rempli est toujours le poids par defaut de l'exercice ou le dernier utilise. Il n'y a pas de suggestion intelligente (ex: "+2.5kg par rapport a la derniere seance" ou "poids recommande pour cette serie").
- **Guideline** :
  - Afficher sous le champ poids un hint contextuel :
    - "Derniere fois : 57.5 kg x 10" en `labelSmall`, `onSurfaceVariant`
    - Si PR potentiel : "Record actuel : 60 kg" avec icone etoile en `BrightYellow`
  - Pre-remplir avec la valeur de la derniere seance pour cet exercice
  - Ajouter un bouton "Suggerer" qui propose le poids base sur la progression lineaire (dernier poids + step standard de l'exercice)
  - Lors d'un PR (poids > max historique) : afficher une animation subtile (shimmer gold sur le champ)

---

## C.05 — "Muscle #1 : Echauffement" dans les stats

- **Page** : DashboardScreen
- **Emplacement** : Sous les 6 cards de stats, la ligne "Echauffement — Muscle #1 | 960 kg — Volume total"
- **Probleme** : "Echauffement" est affiche comme le "Muscle #1" le plus travaille. L'echauffement n'est pas un groupe musculaire. C'est un bug de categorisation des donnees — les exercices d'echauffement devraient etre exclus du calcul de distribution musculaire.
- **Guideline** :
  - Filtrer les exercices avec `category == "Echauffement"` du calcul de distribution musculaire
  - Si aucun exercice de musculation n'a ete fait (seulement echauffement), afficher "Pas encore de donnees musculaires" au lieu de "Echauffement"
  - Renommer "Muscle #1" en "Muscle le plus travaille" — plus clair
  - Afficher le nom du muscle avec une icone coloree correspondante (pastille de couleur du muscle group)

---

## C.06 — Scroll long dans le Dashboard sans navigation interne

- **Page** : DashboardScreen
- **Emplacement** : Tout l'ecran — 8+ sections empilees (Resume, Comparaison, Records, Evolution, Volume, Distribution, Frequence, Tendances)
- **Probleme** : L'utilisateur doit scroller longuement pour acceder aux sections en bas (Distribution, Tendances). Il n'y a pas de moyen de jumper directement a une section.
- **Guideline** :
  - Ajouter une rangee de chips de navigation rapide en haut (sous le filtre de periode), sticky au scroll :
    - "Resume" | "Records" | "Graphiques" | "Tendances"
  - Chaque chip scrolle automatiquement a la section correspondante avec `lazyListState.animateScrollToItem()`
  - Le chip actif (section visible) se met a jour automatiquement au scroll via `derivedStateOf { lazyListState.firstVisibleItemIndex }`
  - Style des chips : `FilterChip` avec selected = section visible, couleur `OrangeVibrant`
  - Alternative : utiliser des onglets en haut (`ScrollableTabRow`) qui filtrent le contenu affiche

---

## C.07 — Boutons Pause/Stop en zone haute difficilement accessible

- **Page** : WorkoutSessionScreen
- **Emplacement** : Top bar — boutons Pause (||) et Stop (carre) en haut a droite de l'ecran
- **Probleme** : Pendant l'effort, l'utilisateur tient souvent son telephone d'une seule main. Les boutons Pause et Stop sont dans le coin superieur droit — la zone la MOINS accessible du pouce (surtout sur des ecrans 6"+). Mettre en pause ou arreter une seance sont des actions frequentes qui doivent etre facilement accessibles.
- **Guideline** :
  - Deplacer les controles de session dans une barre inferieure fixe :
    - A gauche : Chrono global (affichage)
    - Au centre : Bouton d'action principal (DEMARRER SERIE / SERIE TERMINEE)
    - A droite : Bouton Pause (40dp) + long press pour Stop
  - Le top bar ne garde que : compteur exercice ("5/13"), nom du muscle, bouton X (fermer/suspendre)
  - Le Stop ne doit pas etre un simple tap — ajouter une confirmation (`AlertDialog` : "Terminer la seance ? Tu pourras reprendre plus tard.")
  - La Pause doit etre facilement reversible — toggle visuellement clair (icone Play/Pause animee)

---

## C.08 — Pas de haptic feedback sur les boutons +/-

- **Page** : WorkoutSessionScreen, CustomWorkoutScreen
- **Emplacement** : Boutons +/- pour poids et repetitions
- **Probleme** : L'utilisateur tape les boutons +/- des dizaines de fois par seance sans aucun retour tactile. La confirmation que le tap a ete enregistre repose uniquement sur le changement visuel du chiffre, ce qui est insuffisant pendant l'effort (regard ailleurs, fatigue).
- **Guideline** :
  - Ajouter `HapticFeedbackType.TextHandleMove` (vibration ultra-legere de 10ms) a chaque tap +/-
  - Pour le long press (increment continu) : vibration de 5ms tous les 200ms
  - Pour les multiples de 5 (sur les reps) ou 10 (sur le poids) : vibration legerement plus forte (20ms)
  - Implementer via `LocalHapticFeedback.current.performHapticFeedback()` dans le `onClick` handler
  - Respecter le setting "Vibration" de la section Settings — si desactive, pas de haptic sur les boutons

---

## C.09 — Pas de pull-to-refresh sur les ecrans de donnees

- **Page** : DashboardScreen, NutritionScreen, ExercisesScreen, FavoriteWorkoutsScreen
- **Emplacement** : Tout l'ecran (action de swipe vers le bas)
- **Probleme** : L'utilisateur ne peut pas rafraichir les donnees par un geste naturel de pull-to-refresh. S'il ajoute une pesee puis revient au Dashboard, il n'est pas certain que les stats soient a jour.
- **Guideline** :
  - Implementer `PullToRefreshContainer` (Material 3) sur les ecrans de donnees
  - Animation de pull : indicateur circulaire `OrangeVibrant` qui apparait en haut
  - Au refresh : recharger les donnees depuis le ViewModel (`viewModel.refresh()`)
  - Duree indicateur : 1 seconde minimum (meme si les donnees locales sont instantanees) pour donner le feedback que "quelque chose s'est passe"
  - Utiliser `rememberPullToRefreshState()` + `Modifier.pullToRefresh()`

---

# SECTION D — DETAILS ESTHETIQUES (polish manquant)

---

## D.01 — Emoji haltere comme logo/branding

- **Page** : OnboardingScreen (page 0), splash screen
- **Emplacement** : Centre de l'ecran de bienvenue, au-dessus du nom "ShredCoach"
- **Probleme** : L'emoji haltere est generique, depend du systeme (varie selon Samsung/Google/Xiaomi), et positionne l'app dans la categorie "projet amateur". Aucune app du Top 100 fitness n'utilise un emoji comme identite visuelle.
- **Guideline** :
  - Remplacer par un logo vectoriel custom (SVG/vector drawable) :
    - Design : flamme stylisee + haltere minimaliste, ligne fine, monochrome `OrangeVibrant` sur fond sombre
    - Taille : 96x96dp centre
    - OU : animation Lottie du logo (2-3 secondes, joue une fois)
  - Utiliser le meme logo dans :
    - La top bar du HomeScreen (petit, 24dp a gauche du titre)
    - L'icone launcher (deja bien fait en SVG)
    - Le splash screen
  - Remplacer TOUS les emojis utilises comme icones (haltere, feu, biceps, balance) par des icones Material ou custom SVG dans un style unifie

---

## D.02 — Font systeme SansSerif sans identite typographique

- **Page** : Globale — toute l'application
- **Emplacement** : Tous les textes
- **Probleme** : `FontFamily.SansSerif` est Roboto par defaut sur Android. C'est fonctionnel mais generique. L'application n'a aucune personnalite typographique, ce qui contribue fortement a la perception "non premium".
- **Guideline** :
  - Installer la police **Inter** (ou **Plus Jakarta Sans**) via Google Fonts et la definir comme fontFamily par defaut dans `Type.kt`
  - Telecharger les fichiers .ttf (Regular, Medium, SemiBold, Bold) dans `res/font/`
  - Creer la FontFamily Compose :
    ```
    val InterFont = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    ```
  - Appliquer a TOUS les styles dans `Type.kt`
  - Inter offre : lisibilite excellente sur mobile, personnalite moderne, support complet des caracteres francais (accents)
  - Si une seconde fonte est desiree pour les titres : utiliser **Space Grotesk** (Bold) pour les `headlineLarge` et `displayLarge` uniquement

---

## D.03 — Padding inconsistant sur le HomeScreen

- **Page** : HomeScreen
- **Emplacement** : Les differentes cards et sections
- **Probleme** : 4 valeurs de padding differentes sur un seul ecran : CTA orange (20dp), mini stats (14dp), cartes moyennes (16dp), petites cartes (12dp). Cette inconsistance cree un rythme visuel irregulier.
- **Guideline** :
  - Standardiser le padding interne des cards a **16dp** uniformement
  - Definir des tokens de spacing dans le theme :
    - `SpacingXS = 4.dp`
    - `SpacingSM = 8.dp`
    - `SpacingMD = 16.dp`
    - `SpacingLG = 24.dp`
    - `SpacingXL = 32.dp`
  - Le padding interne de TOUTES les cards de l'app doit utiliser `SpacingMD` (16dp) par defaut
  - Exception : les cards compactes (mini stats) peuvent utiliser `SpacingSM + SpacingXS` (12dp) si espace contraint
  - L'espacement ENTRE les cards doit etre uniformement `SpacingSM` (8dp) pour les elements lies, `SpacingMD` (16dp) pour les sections

---

## D.04 — Elevation uniforme sans profondeur hierarchique

- **Page** : Globale — toutes les cards
- **Emplacement** : Toutes les `Card` et `ElevatedCard` de l'application
- **Probleme** : Presque toutes les cards utilisent 1dp d'elevation. Il n'y a pas de differentiation de profondeur entre les elements importants (CTA) et les elements secondaires (info cards). L'interface est visuellement "plate".
- **Guideline** :
  - Definir 4 niveaux d'elevation :
    - `Level0 = 0.dp` : surfaces de fond, cartes embeddees
    - `Level1 = 1.dp` : cards informatives au repos
    - `Level2 = 3.dp` : cards interactives/selectionnables, CTA secondaires
    - `Level3 = 6.dp` : FAB, active session banner, bottom sheets
    - `Level4 = 8.dp` : dialogs, overlays
  - Exemples d'application :
    - Mini stats HomeScreen : Level0 (integrees dans le fond)
    - Cartes Exercices/Stats/Nutrition : Level1
    - CTA "Demarrer seance" : Level2
    - Active session banner : Level3
    - Transition "Excellent !" overlay : Level4
  - En dark mode, l'elevation se traduit par un fond legerement plus clair (Material tonalElevation) — s'assurer que le theme utilise correctement `surfaceTint`

---

## D.05 — Espacement entre cards trop serre

- **Page** : HomeScreen, ExercisesScreen, WorkoutPreviewScreen, DashboardScreen
- **Emplacement** : L'espace vertical entre chaque card dans les `LazyColumn` et `Column`
- **Probleme** : Les cards sont espacees de 8-12dp, ce qui cree un "mur de cartes" visuellement etouffant. Les apps premium utilisent 16-24dp entre les sections majeures pour creer de la "respiration visuelle".
- **Guideline** :
  - **Entre elements du meme groupe** : 8dp (ex: entre deux exercices dans une liste)
  - **Entre sections differentes** : 16dp (ex: entre la section "Resume" et "Records" dans le Dashboard)
  - **Entre groupes majeurs** : 24dp (ex: entre les cartes du Home et le footer)
  - Ajouter un `Spacer(Modifier.height(24.dp))` entre chaque section majeure
  - Utiliser `verticalArrangement = Arrangement.spacedBy(12.dp)` dans les LazyColumn comme minimum
  - Les titres de section doivent avoir un `Modifier.padding(top = 24.dp, bottom = 8.dp)` pour marquer clairement la rupture

---

## D.06 — Pas de gradients ou effets de profondeur

- **Page** : Globale
- **Emplacement** : Tous les backgrounds et cards
- **Probleme** : Toutes les surfaces sont des aplats de couleur unis (DarkBackground, DarkSurface, OrangeVibrant). C'est propre mais froid et "digital". Les apps premium utilisent des gradients subtils pour creer de la chaleur et de la profondeur.
- **Guideline** :
  - **CTA HomeScreen** : Remplacer l'aplat `OrangeVibrant` par un gradient :
    ```
    Brush.linearGradient(
        colors = listOf(OrangeVibrant, RedPassion),
        start = Offset.Zero,
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )
    ```
  - **Card hero preview** : Meme gradient orange→rouge
  - **Background global** : Ajouter un gradient radial tres subtil au centre de l'ecran :
    ```
    Brush.radialGradient(
        colors = listOf(
            OrangeVibrant.copy(alpha = 0.03f),
            Color.Transparent
        ),
        center = Offset(screenWidth * 0.3f, screenHeight * 0.2f),
        radius = screenWidth * 0.8f
    )
    ```
  - **Boutons "LANCER" / "TERMINER"** : Gradient NeonGreen → NeonGreen.copy(green = 0.9f) pour de la profondeur
  - **Scroll fade** : Ajouter un fade gradient en haut et en bas des listes longues (20dp de hauteur, `DarkBackground` a transparent)

---

## D.07 — Icones sans logique semantique sur HomeScreen

- **Page** : HomeScreen
- **Emplacement** : Les icones des 5 cartes d'action (Exercices, Mes Stats, Nutrition, Profil, Photos)
- **Probleme** : Les icones utilisent des couleurs aleatoires (orange pour Exercices, vert pour Stats, blanc pour les 3 petites cartes). Il n'y a pas de logique de couleur associee aux fonctionnalites.
- **Guideline** :
  - Definir un code couleur semantique persistant dans toute l'app :
    - **Seances/Workout** : `OrangeVibrant`
    - **Exercices** : `Color(0xFF3B82F6)` (bleu)
    - **Nutrition** : `NeonGreen`
    - **Stats/Dashboard** : `Color(0xFF8B5CF6)` (violet — ici le violet a un role defini)
    - **Profil** : `onSurfaceVariant` (gris neutre)
    - **Photos** : `OrangeVibrant`
  - Appliquer ces couleurs aux icones, aux titres de section, et aux headers correspondants dans toute l'app
  - Les mini-stats du Home doivent utiliser les memes couleurs que les sections auxquelles elles correspondent

---

## D.08 — Cards identiques pour des contenus de hierarchie differente

- **Page** : HomeScreen
- **Emplacement** : Les cartes "Exercices"/"Mes Stats" (100dp, 2 colonnes) et "Nutrition"/"Profil"/"Photos" (plus petites, 3 colonnes)
- **Probleme** : Les 5 cartes d'action utilisent le meme style visuel (fond surfaceVariant, meme coin radius, meme elevation) malgre des niveaux d'importance differents. L'utilisateur ne percoit pas quelle carte est la plus importante.
- **Guideline** :
  - Differencier visuellement par importance :
    - **Tier 1** (Seance) : Card hero avec gradient, elevation 3dp, 120dp hauteur → deja fait (CTA orange)
    - **Tier 2** (Exercices, Stats, Nutrition) : Cards moyennes avec icone de section coloree, elevation 1dp, 90dp hauteur, fond `surfaceVariant`
    - **Tier 3** (Profil, Photos) : Liens textuels ou petites cards sans elevation (Level0), fond transparent, juste icone + label
  - Ajouter un `subtitle` contextuel aux cartes Tier 2 :
    - Exercices : "68 mouvements"
    - Stats : "1 seance ce mois"
    - Nutrition : "1,200 / 2,458 kcal"

---

## D.09 — Progress bar onboarding sans animation de remplissage

- **Page** : OnboardingScreen (toutes les pages)
- **Emplacement** : Barre de progression horizontale en haut de l'ecran
- **Probleme** : La barre de progression passe d'une etape a l'autre sans animation. Le segment rempli (orange) et le segment vide (gris) changent instantanement, sans transition.
- **Guideline** :
  - Animer la progression avec `animateFloatAsState` :
    ```
    val progress by animateFloatAsState(
        targetValue = (currentPage + 1f) / totalPages,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )
    ```
  - Utiliser `LinearProgressIndicator(progress = progress)` avec les couleurs custom
  - Augmenter la hauteur a 6dp (au lieu de 4dp) avec `RoundedCornerShape(3.dp)`
  - Couleur remplie : gradient `OrangeVibrant` → `RedPassion`
  - Couleur vide : `outlineVariant.copy(alpha = 0.3f)`
  - Ajouter un effet de brillance (shimmer) sur le bord de progression pour un effet premium

---

## D.10 — Bouton "Suivant" aligne a droite sans symetrie avec "Retour"

- **Page** : OnboardingScreen (pages 1 a 6)
- **Emplacement** : Barre de navigation en bas de l'ecran — "Retour" a gauche, "Suivant" a droite
- **Probleme** : Le bouton "Retour" est un `TextButton` (pas de fond) tandis que "Suivant" est un `Button` (fond orange plein). L'asymetrie est correcte en hierarchie d'action, mais le placement cree un desequilibre visuel — le poids visuel est entierement a droite.
- **Guideline** :
  - Conserver la hierarchie actuelle (TextButton vs Button) — c'est correct
  - Rendre le bouton "Suivant" full-width quand il n'y a pas de "Retour" (page 0)
  - Sur les pages avec "Retour" : placer les deux boutons dans une Row avec `Arrangement.SpaceBetween` et `Modifier.fillMaxWidth()`
  - Le bouton "Suivant" doit avoir un minimum width de 140dp pour l'equilibre
  - Ajouter une icone `ArrowForward` (18dp) a droite du texte "Suivant" pour renforcer la direction
  - Le bouton final "C'est parti !" doit etre full-width, plus grand (56dp height) et avec une animation de pulse legere pour attirer l'attention

---

## D.11 — Pas de skeleton loading pour les GIFs

- **Page** : ExerciseDetailScreen, WorkoutSessionScreen, ExercisesScreen (si thumbnails ajoutees)
- **Emplacement** : Zones d'affichage des GIFs d'exercice
- **Probleme** : Pendant le chargement d'un GIF, un simple `CircularProgressIndicator` de 2dp d'epaisseur est affiche sur fond `surfaceVariant`. C'est minimal et generique. Les apps premium utilisent des skeleton screens avec effet shimmer.
- **Guideline** :
  - Remplacer le `CircularProgressIndicator` par un skeleton shimmer :
    - Zone GIF : rectangle arrondi (memes dimensions que le GIF final) en `surfaceVariant`
    - Appliquer un effet shimmer anime :
      ```
      val shimmerColors = listOf(
          surfaceVariant,
          surfaceVariant.copy(alpha = 0.5f),
          surfaceVariant
      )
      // Gradient anime de gauche a droite, loop infini
      ```
    - Duration : 1200ms par cycle
    - Easing : `LinearEasing`
  - En cas d'erreur de chargement : afficher l'illustration statique du mouvement (premiere frame du GIF convertie en PNG) au lieu de l'icone generique FitnessCenter
  - Ajouter `crossfade(true)` dans le builder `ImageRequest` pour une transition douce GIF charge → anime

---

## D.12 — Corner radius non standardise (10+ valeurs)

- **Page** : Globale
- **Emplacement** : Tous les composants avec RoundedCornerShape
- **Probleme** : L'application utilise au moins 10 valeurs differentes de corner radius : 2dp, 3dp, 4dp, 6dp, 8dp, 12dp, 16dp, 20dp, et CircleShape. Cette fragmentation cree un manque de coherence visuelle — certaines cards ont des coins presque carres (4dp) tandis que d'autres sont tres arrondies (20dp).
- **Guideline** :
  - Definir 4 tokens de corner radius dans le theme :
    ```
    object ShredCorners {
        val Small = RoundedCornerShape(8.dp)    // chips, badges, small buttons
        val Medium = RoundedCornerShape(12.dp)   // cards, text fields, standard elements
        val Large = RoundedCornerShape(16.dp)    // containers, hero cards, action cards
        val Full = CircleShape                    // avatars, FAB, round buttons
    }
    ```
  - Appliquer systematiquement :
    - Badges, chips, filtres : `Small` (8dp)
    - Cards exercice, stats, nutrition, inputs : `Medium` (12dp)
    - CTA hero, bottom sheets, containers de section : `Large` (16dp)
    - Avatar, boutons +/-, FAB : `Full` (CircleShape)
  - Supprimer TOUTES les valeurs intermediaires (2dp, 3dp, 4dp, 6dp, 20dp) du codebase

---

## D.13 — 6 couleurs de fond differentes pour les cards du Dashboard

- **Page** : DashboardScreen
- **Emplacement** : Les 6 stat cards en grille 2x3 (Seances, Volume, Ce mois, Calories, Total seances, Temps total)
- **Probleme** : Chaque card utilise une couleur de fond differente (violet, vert, rouge, teal, orange, bleu). Cet arc-en-ciel est visuellement agressif et empeche l'oeil de prioriser l'information. Les apps analytics premium (Strava, Apple Health) utilisent 1-2 couleurs max dans les stat cards.
- **Guideline** :
  - Utiliser un fond uniforme `surfaceVariant` pour TOUTES les stat cards
  - Differencier les stats par l'icone coloree (24dp, coin superieur gauche) :
    - Icone seances : `OrangeVibrant`
    - Icone volume : `NeonGreen`
    - Icone calendrier : `Color(0xFF3B82F6)` (bleu)
    - Icone calories : `Color(0xFFEF4444)` (rouge)
    - Icone total : `Color(0xFF8B5CF6)` (violet)
    - Icone temps : `Color(0xFF14B8A6)` (teal)
  - Le chiffre principal doit etre en `onSurface` (blanc en dark mode), pas en couleur
  - Le label doit etre en `onSurfaceVariant`
  - Seule l'icone porte la couleur = propre et lisible

---

## D.14 — Fond blanc des GIFs qui detonne en dark mode

- **Page** : ExerciseDetailScreen, WorkoutSessionScreen
- **Emplacement** : Zone hero du GIF et thumbnail GIF dans la session
- **Probleme** : Les GIFs d'exercice ont un fond blanc/gris clair qui contraste brutalement avec le dark mode de l'app (fond #0F172A). Ce "rectangle blanc" au milieu de l'interface sombre casse l'immersion.
- **Guideline** :
  - **Option 1** : Appliquer un `ColorFilter.tint(DarkSurface, BlendMode.Multiply)` pour assombrir le fond du GIF sans affecter le personnage
  - **Option 2** : Placer le GIF dans un conteneur avec fond `surfaceVariant` (gris moyen) et `RoundedCornerShape(16.dp)` pour creer une transition douce entre le fond clair du GIF et le fond sombre de l'app
  - **Option 3 (ideale)** : Regenerer les GIFs avec fond transparent (PNG anime ou WebP anime) — necessite un travail de post-production mais c'est la solution premium
  - En attendant, ajouter un `Modifier.clip(RoundedCornerShape(16.dp))` + un border de 1dp `outlineVariant.copy(alpha = 0.1f)` autour du GIF pour l'integrer visuellement

---

## D.15 — Avatar profil sans possibilite de photo

- **Page** : ProfileScreen
- **Emplacement** : Cercle avatar en haut de l'ecran — affiche l'initiale du prenom
- **Probleme** : L'avatar est un simple cercle avec une lettre. L'utilisateur ne peut pas ajouter sa propre photo. C'est une personnalisation basique attendue de toute app moderne.
- **Guideline** :
  - Ajouter une icone Camera (18dp) en superposition sur le coin inferieur droit de l'avatar
  - Au tap : proposer un `BottomSheet` avec 3 options :
    - "Prendre une photo" (camera)
    - "Choisir dans la galerie" (picker)
    - "Supprimer la photo" (si photo existante)
  - Stocker l'image dans `context.filesDir/avatar/` comme pour les photos de progression
  - Afficher l'image avec `SubcomposeAsyncImage`, `ContentScale.Crop`, `CircleShape`
  - Fallback : garder l'initiale si pas de photo
  - Taille de l'avatar : augmenter a 96dp (actuellement 80dp) pour un effet plus premium

---

## D.16 — RadioButton natif Android dans le Generator

- **Page** : WorkoutGeneratorScreen
- **Emplacement** : Section "Equipement disponible" — RadioButton cercle natif a gauche des cartes (Salle complete, Home Gym, Poids du corps)
- **Probleme** : Le RadioButton Android natif (cercle gris avec point) detonne avec le design system custom de l'app. C'est le seul endroit ou un composant natif non-stylise apparait.
- **Guideline** :
  - Supprimer le RadioButton et transformer les options en `SelectableCard` custom (meme pattern que les objectifs dans l'onboarding) :
    - Card non selectionnee : fond `surfaceVariant`, border `outlineVariant` (1dp)
    - Card selectionnee : fond `OrangeVibrant.copy(alpha = 0.10f)`, border `OrangeVibrant` (2dp), elevation 3dp
    - Check icon (20dp, `OrangeVibrant`) en remplacement du RadioButton
  - Ou utiliser un `Icon` personnalise : cercle vide pour non-selectionne, cercle plein `OrangeVibrant` pour selectionne

---

## D.17 — Section "Ou bien..." peu visible dans le Generator

- **Page** : WorkoutGeneratorScreen
- **Emplacement** : Sous le bouton "GENERER MA SEANCE", le texte "Ou bien..." et les 2 boutons outlined "Creer ma seance" / "Mes favoris"
- **Probleme** : Le texte "Ou bien..." est en gris tres clair (`onSurfaceVariant` faible opacite), a peine lisible. Les 2 boutons alternatifs sont des `OutlinedButton` discrets que l'utilisateur pourrait ne jamais voir. Ce sont pourtant des features importantes (creation custom et favoris).
- **Guideline** :
  - Remplacer "Ou bien..." par un `Divider` horizontal avec texte centre : "ou" en `labelMedium`, `onSurfaceVariant`, avec padding 16dp de chaque cote du texte
  - Augmenter la visibilite des boutons :
    - "Creer ma seance" : icone Build (18dp, `OrangeVibrant`) + texte `OrangeVibrant`
    - "Mes favoris" : icone Favorite (18dp, `ErrorRed`) + texte `onSurface`
  - Border : `outlineVariant` (1dp), pas `OrangeVibrant` (trop proche du CTA principal)
  - Hauteur : 48dp (au lieu du default)
  - Espacement entre les 2 boutons : 12dp horizontal

---

## D.18 — Noms d'exercices tronques dans Custom Workout

- **Page** : CustomWorkoutScreen
- **Emplacement** : Chaque card d'exercice — le texte du nom est coupe avec "..."
- **Probleme** : "Cardio leger (rameur,...", "Mobilisations articul..." — les noms sont tronques a cause d'un espace horizontal insuffisant. L'utilisateur doit deviner l'exercice.
- **Guideline** :
  - Augmenter l'espace horizontal disponible pour le nom :
    - Mettre le nom sur 2 lignes (`maxLines = 2`) au lieu de 1
    - Reduire la taille de l'icone swap (de 20dp a 16dp) ou la placer en dessous du nom
    - Reduire le padding horizontal de la card de 16dp a 12dp pour gagner de l'espace
  - Si le nom depasse encore 2 lignes, utiliser `TextOverflow.Ellipsis` sur la 2eme ligne
  - Alternative : afficher le nom complet, quitte a augmenter la hauteur de la card

---

## D.19 — Boutons Camera/Galerie trop petits dans Photos Progression

- **Page** : ProgressPhotosScreen
- **Emplacement** : Section "Nouvelle photo" — les 6 boutons icones (Camera/Galerie x 3 vues)
- **Probleme** : Les `FilledTonalIconButton` font 36dp avec des icones de 18dp. C'est trop petit, trop rapproche entre les 2 boutons d'une meme vue, et la couleur violette est inattendue.
- **Guideline** :
  - Augmenter la taille des boutons a 48dp avec icones de 24dp
  - Espacement entre Camera et Galerie de la meme vue : 8dp
  - Espacement entre les 3 groupes (Face/Profil/Dos) : 16dp
  - Couleur : `OrangeVibrant` pour le bouton Camera (action primaire), `surfaceVariant` pour Galerie (action secondaire)
  - Ajouter un label sous chaque paire de boutons : "Face", "Profil", "Dos" en `labelMedium`
  - Alternative premium : transformer en 3 grandes cards (une par vue) avec illustration silhouette + boutons Camera/Galerie en dessous

---

## D.20 — Ecran Favoris trop minimaliste

- **Page** : FavoriteWorkoutsScreen
- **Emplacement** : Toute la page — une seule card avec nom + nombre d'exercices
- **Probleme** : La card de favori ne montre que "Seance Full Body - 90min / 13 exercices - 90 min" + coeur. Pas de preview des exercices, pas de derniere utilisation, pas de stats associees, pas de bouton "Lancer" visible.
- **Guideline** :
  - Enrichir la card de favori :
    - **Header** : Nom de la seance + badge duree + badge nombre d'exercices
    - **Preview** : 3-4 mini thumbnails des premiers exercices en Row (32x32dp GIFs ou icones)
    - **Meta** : "Derniere utilisation : il y a 3 jours" en `bodySmall`, `onSurfaceVariant`
    - **Stats** : "Meilleur volume : 4,500 kg" en `labelMedium`
    - **Actions** : Bouton "Lancer" (vert NeonGreen, Icon PlayArrow) + bouton overflow (3 dots) pour "Supprimer" / "Dupliquer" / "Modifier"
    - **Coeur** : Deplacer dans le menu overflow au lieu de l'afficher en permanence (il est toujours favori, inutile)
  - Ajouter un FAB pour "Generer + Sauvegarder" une nouvelle seance directement depuis cet ecran

---

## D.21 — Transition "Excellent !" sans animation

- **Page** : WorkoutSessionScreen
- **Emplacement** : Overlay qui apparait entre deux exercices — check vert + "Excellent !" + stats
- **Probleme** : L'overlay apparait et disparait sans animation (cut direct). C'est le moment de celebration de l'app — il devrait etre le plus anime et le plus gratifiant.
- **Guideline** :
  - **Entree** (500ms total) :
    1. Fond : fade in noir semi-transparent (0 → 0.7f alpha, 200ms)
    2. Check vert : scale 0 → 1.2 → 1.0 (spring, dampingRatio = 0.6f) + rotation 0 → 360deg
    3. "Excellent !" : fade in + slide up 20dp (200ms, delay 200ms)
    4. Stats card : fade in + slide up 30dp (300ms, delay 350ms)
    5. Confetti burst : particules colorees depuis le centre (Lottie ou custom Canvas, 1000ms)
  - **Sortie** (300ms) :
    1. Tout : fade out simultane (200ms)
    2. Slide down 30dp sur le contenu
  - Haptic feedback : double vibration (tap-tap) au moment du check
  - Son : ding subtil de completion (custom audio, pas le son systeme)

---

## D.22 — Timer repos sans animation circulaire

- **Page** : WorkoutSessionScreen
- **Emplacement** : Card de repos entre les series — affiche "01:43" avec LinearProgressIndicator
- **Probleme** : Le timer utilise une barre lineaire de 6dp pour montrer la progression. C'est fonctionnel mais peu intuitif et peu visible de loin. Les apps premium (Strong, Apple Watch Timer) utilisent un cercle animé.
- **Guideline** :
  - Remplacer le `LinearProgressIndicator` par un **cercle de progression** en `Canvas` :
    - Taille : 120dp de diametre
    - Arc de fond : `outlineVariant.copy(alpha = 0.2f)`, stroke 8dp
    - Arc de progression : `OrangeVibrant` → `NeonGreen` (gradient quand < 10s), stroke 8dp, `StrokeCap.Round`
    - Le temps restant (grande typographie `displaySmall` Bold) au centre du cercle
    - Animation : `animateFloatAsState` smooth sur la progression (pas de saccade)
  - Dans les 5 dernieres secondes :
    - L'arc passe en `NeonGreen` avec un pulse d'opacite
    - Le chiffre grossit legerement (scale 1.0 → 1.1 par seconde)
    - Haptic tick chaque seconde
  - A 0 : pulse final + vibration pattern + son de notification

---

## D.23 — Pas de scroll fade gradient sur les listes longues

- **Page** : ExercisesScreen, WorkoutPreviewScreen, DashboardScreen, NutritionScreen
- **Emplacement** : Haut et bas des listes scrollables (LazyColumn)
- **Probleme** : Quand l'utilisateur scrolle, le contenu est brutalement coupe par les bords de l'ecran. Il n'y a pas d'indication visuelle qu'il y a du contenu au-dela.
- **Guideline** :
  - Ajouter un gradient fade en haut et en bas des LazyColumn :
    ```
    Box {
        LazyColumn(...)
        // Fade top
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, Color.Transparent)
                    )
                )
        )
        // Fade bottom
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, DarkBackground)
                    )
                )
        )
    }
    ```
  - Ne montrer le fade que quand la liste est effectivement scrollable (plus d'items que le viewport)
  - Ajuster la couleur du gradient selon le fond de l'ecran

---

## D.24 — Pas de support Material You / Dynamic Color

- **Page** : Globale — Theme.kt
- **Emplacement** : Configuration du theme de l'application
- **Probleme** : L'app n'utilise pas les Dynamic Colors de Material You (Android 12+) qui adaptent la palette de couleurs au fond d'ecran de l'utilisateur. C'est un signal premium fort sur les appareils Pixel et Samsung recents.
- **Guideline** :
  - Ajouter le support conditionnel dans `Theme.kt` :
    ```
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    ```
  - Conserver les couleurs custom (OrangeVibrant) pour les elements de marque (logo, CTA hero, accents specifiques) meme avec Dynamic Color actif
  - Ajouter un toggle dans Settings : "Couleurs dynamiques (Material You)" — actif par defaut sur Android 12+, cache sur les versions anterieures

---

## D.25 — Pas d'animation sur les cards au tap (press state)

- **Page** : Globale — toutes les cards cliquables
- **Emplacement** : HomeScreen cards, ExercisesScreen cards, DashboardScreen stat cards, etc.
- **Probleme** : Quand l'utilisateur tape sur une card, le seul feedback est le ripple Material par defaut. Les apps premium ajoutent une micro-animation de press (scale down + elevation change) qui rend l'interface plus tactile et responsive.
- **Guideline** :
  - Creer un `Modifier.pressAnimation()` custom :
    ```
    fun Modifier.pressAnimation(): Modifier = composed {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f)
        )
        this
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null) { }
    }
    ```
  - Appliquer a TOUTES les cards cliquables de l'app
  - Combiner avec un changement d'elevation : 1dp → 3dp au press
  - Duree de l'animation : 150ms press, 200ms release (spring)
  - Conserver le ripple Material en complement

---

## D.26 — Bouton "Suivant" de l'onboarding desactive sans explication

- **Page** : OnboardingScreen (page 2 — Nom)
- **Emplacement** : Bouton "Suivant" en bas a droite — grise/desactive quand le prenom est vide
- **Probleme** : Le bouton est visuellement grise mais il n'y a aucune indication expliquant POURQUOI il est desactive. L'utilisateur nouveau ne comprend pas immediatement que le prenom est obligatoire.
- **Guideline** :
  - Ajouter un asterisque rouge sur le label "Prenom *" (deja present mais peu visible)
  - Quand l'utilisateur tape sur le bouton desactive : animer un shake horizontal (3 oscillations, 300ms) + afficher un message sous le champ prenom : "Le prenom est requis" en `bodySmall`, `ErrorRed`
  - Ajouter une bordure `ErrorRed` (1dp) sur le champ prenom si l'utilisateur essaie de continuer sans le remplir
  - Le bouton desactive doit avoir une opacite de 0.38f (standard M3) — pas juste un changement de couleur

---

## D.27 — Section Coach collapsible dans la session sans transition

- **Page** : WorkoutSessionScreen
- **Emplacement** : La card "Coach" avec chevron (V) entre le header de l'exercice et la timeline des series
- **Probleme** : La section Coach s'ouvre/ferme sans animation. Le contenu apparait/disparait instantanement, ce qui est visuellement saccade et desoriente.
- **Guideline** :
  - Utiliser `animateContentSize(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f))` sur le contenu de la section Coach
  - Animer la rotation du chevron : 0deg (ferme) → 180deg (ouvert) avec `animateFloatAsState` (200ms)
  - Au premier affichage d'un exercice : la section Coach doit etre ouverte par defaut (l'utilisateur a besoin des instructions)
  - Apres la premiere serie : fermer automatiquement la section Coach (l'utilisateur connait l'exercice)
  - Conserver l'etat ouvert/ferme dans le ViewModel pour ne pas le perdre au scroll

---

## D.28 — Pas de distinction visuelle entre echauffement et musculation dans la preview

- **Page** : WorkoutPreviewScreen
- **Emplacement** : Les cards d'exercice dans la LazyColumn
- **Probleme** : Les exercices d'echauffement et les exercices de musculation ont le meme style visuel de card. Seul le numero colore (rouge pour echauffement, bleu pour muscu) les differencie, mais cette logique n'est pas expliquee.
- **Guideline** :
  - Ajouter des headers de section visibles :
    - "Echauffement (8 min)" avec icone flamme, fond `OrangeVibrant.copy(alpha = 0.08f)`
    - "Exercices de Musculation (57 min)" avec icone dumbbell, fond `surfaceVariant`
    - "Cardio (25 min)" avec icone running, fond `NeonGreen.copy(alpha = 0.08f)`
  - Ces headers doivent etre des `stickyHeader` dans le LazyColumn pour rester visibles au scroll
  - Les cards d'echauffement peuvent avoir un style legerement different : border `OrangeVibrant.copy(alpha = 0.15f)` au lieu de pas de border

---

## D.29 — Pas de confirmation avant de quitter la session

- **Page** : WorkoutSessionScreen
- **Emplacement** : Bouton X (fermer) en haut a gauche, bouton Stop en haut a droite, back gesture systeme
- **Probleme** : Quitter la session en cours (bouton X) met la session en pause silencieusement. L'utilisateur n'est pas averti des consequences. S'il tape X par accident, il ne comprend pas que sa session continue en arriere-plan.
- **Guideline** :
  - Au tap sur X : afficher un `AlertDialog` de confirmation :
    - Titre : "Mettre en pause ?"
    - Message : "Ta seance sera suspendue. Tu pourras la reprendre depuis l'accueil."
    - Bouton 1 : "Continuer la seance" (primary, `OrangeVibrant`)
    - Bouton 2 : "Suspendre" (secondary, outlined)
    - Bouton 3 : "Terminer definitivement" (text button, `ErrorRed`)
  - Au tap sur Stop : meme dialog mais avec focus sur "Terminer"
  - Intercepter le back gesture systeme avec `BackHandler` pour afficher le meme dialog
  - Le banner de session active sur les autres ecrans doit pulser plus visiblement (animation scale + glow)

---

## D.30 — Pas de feedback lors de la generation de seance

- **Page** : WorkoutGeneratorScreen
- **Emplacement** : Apres le tap sur "GENERER MA SEANCE"
- **Probleme** : Un simple `CircularProgressIndicator` (24dp) remplace le texte du bouton pendant la generation. C'est minimal et ne communique pas ce qui se passe. La generation est probablement instantanee (algorithme local), ce qui rend meme le spinner invisible.
- **Guideline** :
  - Meme si la generation est rapide, ajouter un delai minimum de 800ms pour donner l'impression qu'un algorithme travaille (perceived performance)
  - Pendant le delai, afficher une animation dans une modale/overlay :
    - 3 icones qui s'animent : dumbbell qui tourne → check → fleche vers exercice suivant
    - Texte progressif : "Analyse de ton profil..." → "Selection des exercices..." → "Seance prete !"
    - Background : fond semi-transparent avec blur
  - A la fin : transition smooth vers WorkoutPreviewScreen avec un slide-in

---

## D.31 — Notifications sans time picker visuel

- **Page** : SettingsScreen
- **Emplacement** : Section "Notifications" — les heures affichees (08:00, 12:30, 16:00, etc.)
- **Probleme** : Les heures sont affichees en texte mais il n'est pas clair comment les modifier (probablement un tap ouvre un TimePicker, mais l'affordance est faible). Le texte "Rappel a 08:00" ne ressemble pas a un element interactif.
- **Guideline** :
  - Ajouter une icone `Schedule` (18dp, `OrangeVibrant`) a gauche de l'heure
  - Rendre l'heure visuellement cliquable : fond `surfaceVariant`, corner 8dp, padding 8dp horizontal
  - Au tap : ouvrir `TimePickerDialog` Material 3 (pas un AlertDialog custom)
  - Apres modification : animer le changement d'heure avec `AnimatedContent` (fade)
  - Confirmer avec un Snackbar : "Rappel petit-dejeuner mis a jour : 07:30"

---

## D.32 — Absence de swipe gesture entre exercices en session

- **Page** : WorkoutSessionScreen
- **Emplacement** : Navigation entre exercices (actuellement via bouton "Exercice suivant" et overlay de transition)
- **Probleme** : L'utilisateur ne peut naviguer entre les exercices que via des boutons. Un swipe horizontal (pattern naturel sur mobile) pour previsualiser l'exercice suivant/precedent serait intuitif.
- **Guideline** :
  - Implementer un `HorizontalPager` pour la navigation entre exercices
  - Swipe gauche : previsualiser l'exercice suivant (pas valider — juste voir)
  - La validation reste via le bouton "Exercice Suivant" pour eviter les swipes accidentels
  - Ajouter un indicateur de dots en bas montrant la progression (exercice actuel parmi tous)
  - Le swipe doit etre desactivable dans Settings (certains utilisateurs prefereront les boutons)

---

## D.33 — Export CSV sans preview ni partage direct

- **Page** : DashboardScreen
- **Emplacement** : Icone de partage en haut a droite du top bar
- **Probleme** : L'export CSV est une fonctionnalite avancee mais le flow n'est pas clair — l'utilisateur ne sait pas ce qui va etre exporte ni ou le fichier sera sauvegarde.
- **Guideline** :
  - Au tap sur l'icone : ouvrir un `BottomSheet` avec options :
    - "Exporter les seances (CSV)" + description courte
    - "Exporter la nutrition (CSV)"
    - "Sauvegarder tout (backup)"
  - Apres l'export : ouvrir le `ShareSheet` Android natif avec le fichier en piece jointe (Intent.ACTION_SEND)
  - Afficher un Snackbar : "Export genere — 12 seances, 156 series" avec action "Partager"
  - Icone : remplacer l'icone generique par `FileDownload` (24dp)

---

## D.34 — Pas de mode "focus" pendant la seance

- **Page** : WorkoutSessionScreen
- **Emplacement** : Global — la status bar et les notifications restent visibles
- **Probleme** : Pendant une seance de 60-180 minutes, les notifications et la status bar distraient l'utilisateur. Les apps fitness premium proposent un mode immersif.
- **Guideline** :
  - Activer le mode immersif pendant la session :
    ```
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.insetsController?.let {
        it.hide(WindowInsetsCompat.Type.statusBars())
        it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    ```
  - Ajouter un toggle dans Settings : "Mode focus pendant la seance"
  - Quand actif : masquer la status bar, garder uniquement l'horloge dans le top bar de l'app
  - Desactiver les notifications non-urgentes via DND mode (NotificationManager.INTERRUPTION_FILTER_PRIORITY)

---

## D.35 — Pas de celebration de Records Personnels (PR)

- **Page** : WorkoutSessionScreen
- **Emplacement** : Lors de la validation d'une serie ou le poids depasse le maximum historique
- **Probleme** : Quand l'utilisateur bat son record personnel, il n'y a aucun feedback special. C'est le moment le plus gratifiant d'une seance — il devrait etre celebre visuellement.
- **Guideline** :
  - Detecter le PR en temps reel : comparer le poids saisi avec le max historique de l'exercice
  - Si PR :
    - Afficher un badge "NOUVEAU RECORD !" en gold (`Color(0xFFFFD700)`) au-dessus du champ poids
    - Animation : shimmer gold sur toute la card de serie (1 seconde)
    - Haptic pattern special : triple vibration crescendo (10ms, 20ms, 30ms avec 50ms entre)
    - Icone etoile animee (scale up 0 → 1.2 → 1.0, rotation 360deg, 500ms)
    - Enregistrer le PR dans la base de donnees immediatement
  - Dans le recap de transition "Excellent !" : mentionner le PR avec une section speciale gold
  - Dans le WorkoutSummary : section dediee "Records battus" avec liste des PR de la seance

---

## D.36 — Auto-save absent sur ProfileScreen

- **Page** : ProfileScreen
- **Emplacement** : Formulaire d'informations personnelles (Prenom, Nom, Age, Taille, Sexe) avec bouton "Enregistrer"
- **Probleme** : L'utilisateur doit taper un bouton "Enregistrer" pour sauvegarder ses modifications. S'il navigue away sans sauvegarder, les changements sont perdus silencieusement. Le pattern "formulaire + bouton save" est obsolete — les apps modernes sauvegardent automatiquement.
- **Guideline** :
  - Implementer l'auto-save avec debounce :
    - Chaque modification de champ declenche un `viewModelScope.launch` avec un `delay(1000)` (debounce 1 seconde)
    - Apres le delay : sauvegarder automatiquement en base
    - Afficher un indicateur discret : "Sauvegarde..." → icone check verte "Sauvegarde" (2 secondes, puis disparait)
    - L'indicateur doit etre en `labelSmall` sous le dernier champ modifie
  - Supprimer le bouton "Enregistrer" — il n'est plus necessaire
  - Si l'utilisateur navigue away avec des modifications non sauvegardees (pendant le debounce) : sauvegarder immediatement

---

## D.37 — Pas de bottom sheet pour les dialogs

- **Page** : NutritionScreen (AddMealDialog), ProfileScreen (WeightLogDialog), ExercisesScreen (filtres)
- **Emplacement** : Les `AlertDialog` actuels
- **Probleme** : Les `AlertDialog` Material sont centres a l'ecran et ont une surface fixe. Sur mobile, les `ModalBottomSheet` sont plus ergonomiques (accessibles par le pouce, surface plus large, dismiss par swipe down).
- **Guideline** :
  - Remplacer les `AlertDialog` suivants par des `ModalBottomSheet` :
    - **AddMealDialog** → `ModalBottomSheet` avec search bar, liste d'aliments, selecteur quantite
    - **WeightLogDialog** → `ModalBottomSheet` avec number picker et bouton confirmer
    - **SwapExerciseDialog** → `ModalBottomSheet` avec liste d'alternatives
    - **ExercisePickerDialog** (Custom workout) → `ModalBottomSheet` pleine hauteur avec tabs muscle group
  - Style du BottomSheet :
    - Handle bar : 32x4dp, `outlineVariant`, centre en haut, padding 8dp
    - Corner radius top : 24dp
    - Fond : `DarkSurface`
    - Max height : 90% de l'ecran
    - Scrim : `Color.Black.copy(alpha = 0.5f)`
    - Animation : slide up + fade in (300ms)
  - Conserver les `AlertDialog` uniquement pour les confirmations destructives (ex: "Supprimer toutes les donnees")

---

## D.38 — Pas de streaks ni gamification visible

- **Page** : HomeScreen, DashboardScreen
- **Emplacement** : Absent — devrait etre sur le Home et le Dashboard
- **Probleme** : L'application ne gamifie pas la regularite de l'utilisateur. Les streaks (jours consecutifs d'activite) sont le mecanisme de retention le plus efficace des apps fitness (Duolingo, Apple Watch, Strava).
- **Guideline** :
  - Ajouter un compteur de streak sur le HomeScreen :
    - Position : sous la salutation, dans un chip horizontal
    - Design : icone flamme + "3 jours de suite" en `labelMedium` Bold
    - Couleur : gradient orange si streak > 0, gris si streak = 0
    - Animation : flamme qui ondule (Lottie, loop)
  - Definition du streak : une seance completee OU un repas tracke dans la journee
  - Sur le Dashboard : ajouter une section "Regularite" avec un calendrier heatmap (deja present dans le code mais enrichir visuellement)
  - Ajouter des milestones : 7 jours, 30 jours, 100 jours → notification de congratulation + badge
  - Stocker le streak dans `UserProfileEntity` (currentStreak, bestStreak, lastActivityDate)

---

## D.39 — Pas de widget Android

- **Page** : Externe — ecran d'accueil du telephone
- **Emplacement** : Absent
- **Probleme** : Un widget Android permettrait a l'utilisateur de voir ses stats et de lancer une seance sans ouvrir l'app. C'est un element premium fort et un vecteur de retention.
- **Guideline** :
  - Creer un widget Glance (Jetpack Glance) de taille 4x2 :
    - Contenu : "Bonjour {prenom}" + streak + bouton "Seance rapide"
    - Fond : semi-transparent avec blur (Android 12+)
    - Tap sur le widget : ouvre l'app sur WorkoutGeneratorScreen
  - Widget optionnel 2x1 : juste le streak + dernier workout
  - Mettre a jour le widget apres chaque seance et chaque repas tracke

---

## D.40 — Onboarding sans illustration

- **Page** : OnboardingScreen (pages 0 a 6)
- **Emplacement** : Les zones visuelles principales de chaque page
- **Probleme** : Les pages d'onboarding n'ont aucune illustration custom. Juste des emojis et du texte. C'est le premier contact de l'utilisateur avec l'app — il doit etre visuellement impressionnant pour installer la confiance et l'envie.
- **Guideline** :
  - Page 0 (Welcome) : Animation Lottie du logo (3 secondes) — flamme/dumbbell qui se forme
  - Page 1 (Features) : Remplacer les emojis par des icones SVG custom dans un style unifie (line art, orange et blanc, 48dp) dans des cercles colores
  - Page 3 (Body Metrics) : Illustration d'une silhouette avec lignes de mesure
  - Page 4 (Goal) : Illustrations distinctes pour chaque objectif :
    - Seche : silhouette affutee avec abdos visibles
    - Bulk : silhouette musclee en croissance
    - Maintien : silhouette equilibree avec pouce en l'air
  - Page 5 (Equipment) : Illustrations des equipements :
    - Salle : illustration de salle de sport
    - Home : illustration de home gym
    - Bodyweight : illustration de personne en bodyweight
  - Page 6 (Nutrition) : Illustration d'assiette equilibree avec macros
  - Style unifie : trait fin (2dp), couleurs limitees (blanc + OrangeVibrant + gris), fond transparent, taille 200x200dp
  - Les illustrations peuvent etre des fichiers SVG places dans `res/drawable/` ou des composables `Canvas` custom

---

# RESUME QUANTITATIF

| Severite | Nombre | Priorite implementation |
|----------|--------|------------------------|
| Critique (A) | 5 | Sprint 1 (immediat) |
| Majeur (B) | 12 | Sprint 2-3 |
| Mineur (C) | 9 | Sprint 4-5 |
| Esthetique (D) | 40 | Sprint 5-8 (continu) |
| **TOTAL** | **66 problemes** | |

---

> Ce document est le reference unique pour transformer ShredCoach en application Premium++.
> Chaque correction doit etre testee sur un ecran de 360dp minimum et validee en dark mode ET light mode.
