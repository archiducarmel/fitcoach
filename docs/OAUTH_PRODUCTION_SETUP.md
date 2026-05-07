# Passer l'OAuth Google de Testing → Production

Ce guide est pour Sitou (non-tech). Tu n'as à le faire **qu'une fois**, juste avant de soumettre l'app sur le Play Store.

> **C'est quoi le problème ?** Aujourd'hui, quand un utilisateur tape "Continuer avec Google", il voit un écran rouge "Google n'a pas validé cette app". C'est normal en mode "Testing". Une fois passé en "Production" et validé par Google, cet écran disparaît.

---

## Avant de commencer — checklist matériel

Tu vas avoir besoin de :

- [ ] Ton compte Google Cloud (`console.cloud.google.com`) — celui où tu as déjà créé le projet ShredCoach
- [ ] Une **page web publique** avec ta politique de confidentialité (on en crée une plus bas)
- [ ] Un **logo** ShredCoach en 120×120 PNG (le launcher icon haute résolution suffit)
- [ ] Un **email de support** que tu surveilles (`archiducarmel@gmail.com` est OK)
- [ ] **2-3 captures d'écran** de l'app montrant l'écran de connexion Google Drive et la sauvegarde fonctionner
- [ ] Une **vidéo de démo** de 30-60 secondes montrant le flow OAuth (peut être filmée à l'arrache avec ton téléphone)

Compte **2-6 semaines** entre la soumission et la validation finale par Google. Plante le drapeau dès que possible.

---

## Étape 1 — Préparer ta page Politique de confidentialité

Google EXIGE une URL publique. Pas le choix.

**Options du plus simple au plus pro :**

### Option A — GitHub Pages (gratuit, 10 minutes)
1. Crée un repo public `shredcoach-privacy` sur GitHub
2. Mets dedans un fichier `index.md` avec le template ci-dessous
3. Active GitHub Pages : Settings → Pages → Source → "main" branch → Save
4. Ton URL devient `https://<ton-username>.github.io/shredcoach-privacy/`

### Option B — Notion / Bear / Google Sites (gratuit)
- Notion : crée une page → bouton "Share" → "Share to web" → tu as une URL
- Google Sites : `sites.google.com` → nouveau site → publie

### Option C — Domaine pro (`shredcoach.app`)
- Achète sur Namecheap (~12€/an), fait pointer sur Vercel/Netlify
- Plus pro mais overkill pour la validation initiale

**Template politique de confidentialité** (à coller dans ton `index.md` ou ta page Notion) :

```markdown
# Politique de confidentialité ShredCoach

Dernière mise à jour : [DATE]

ShredCoach est une application Android de coach sportif et nutritionnel.
Elle est développée et opérée par [TON NOM/PRÉNOM].

## Données collectées

ShredCoach collecte et stocke localement sur ton téléphone :
- Profil (nom, âge, sexe, poids, taille, objectifs)
- Séances d'entraînement et progression (poids, séries, répétitions)
- Données nutritionnelles (repas, calories, macros)
- Photos de progression (stockées localement uniquement)
- Conversations avec l'assistant IA Shreddy

Aucune de ces données n'est envoyée à un serveur ShredCoach — il n'y en a pas.

## Sauvegarde Google Drive (optionnel)

Si tu actives la sauvegarde Google Drive, ShredCoach :
- Demande l'accès au scope `drive.appdata` UNIQUEMENT (dossier privé invisible)
- Ne demande JAMAIS accès à tes autres fichiers Drive
- Ne stocke AUCUN token d'accès — chaque sauvegarde redemande un jeton frais
- Stocke ton email Google localement uniquement pour afficher l'état de connexion

Tes sauvegardes restent dans TON Drive, dans un dossier app-spécifique
invisible que seule ShredCoach peut lire/écrire. Si tu désinstalles l'app,
le dossier reste mais devient inaccessible.

## Services tiers

ShredCoach utilise les services suivants :
- **Google Drive API** : sauvegarde optionnelle (cf. ci-dessus)
- **Google Gemini / OpenAI / Anthropic (au choix)** : pour l'assistant IA
  Shreddy et l'analyse de repas. Les requêtes sont envoyées AVEC tes données
  pertinentes (texte du repas, photo). Aucune persistance côté ces services
  au-delà de leurs propres politiques.
- **GitHub Releases** : streaming des GIFs d'exercices. Aucune donnée envoyée.

## Permissions Android

- Caméra : prendre des photos de repas et de progression
- Stockage : sauvegarder les photos
- Notifications : rappels nutrition et coach proactif

## Tes droits

Tes données sont sur ton téléphone. Tu peux à tout moment :
- Désinstaller l'app (toutes les données locales sont effacées)
- Déconnecter Google Drive depuis Réglages → Sauvegarde
- Supprimer une sauvegarde manuellement depuis ton Drive

## Contact

Pour toute question : archiducarmel@gmail.com
```

Une fois publié, **note l'URL** — tu vas en avoir besoin partout.

---

## Étape 2 — Compléter l'OAuth consent screen (Cloud Console)

1. Va sur `https://console.cloud.google.com`
2. Sélectionne ton projet **ShredCoach** (en haut à gauche)
3. Menu de gauche → **APIs & Services** → **OAuth consent screen**

Tu vas voir des onglets : "OAuth consent screen", "Branding", "Audience", "Data access", "Verification center".

### Branding

Remplis TOUS les champs :

| Champ | Valeur |
|---|---|
| App name | `ShredCoach` |
| User support email | `archiducarmel@gmail.com` |
| App logo | Upload le PNG 120×120 |
| Application home page | URL de ta privacy policy (oui, même page que la privacy) ou un Notion "ShredCoach – Présentation" |
| Application privacy policy link | URL privacy policy de l'étape 1 |
| Application terms of service link | Optionnel mais recommandé. Tu peux pointer sur la même page privacy ou créer une `/terms` |
| Authorized domains | Le domaine de ta privacy policy (ex: `github.io` ou `notion.site` ou ton domaine) |
| Developer contact information | `archiducarmel@gmail.com` |

> **Astuce** : si ton URL est `https://archiducarmel.github.io/shredcoach-privacy/`, alors `authorized domains` = `github.io`.

### Data access (les scopes)

Tu vois la liste des scopes que ton app demande. Ça doit dire **uniquement** :

- `.../auth/drive.appdata`

Si tu vois autre chose (ex: `email`, `profile`, `openid`), tu peux les enlever — on n'en a pas besoin (cf. memory `project_drive_backup.md`, on récupère l'email via Drive `about` endpoint).

### Audience

Tu dois être sur **External**, **In production** (ou cliquer "PUBLISH APP" si encore en Testing).

> ⚠️ Tant que tu cliques "PUBLISH APP", l'app passe en mode "Production NON vérifié" — le warning rouge reste mais n'importe qui peut maintenant tester l'app, plus juste tes test users. C'est l'étape avant la verification.

---

## Étape 3 — Préparer la vidéo de démo

Google va te demander une vidéo YouTube (publique ou unlisted) qui montre :

1. **Le flow OAuth** : utilisateur tape "Continuer avec Google" → consent screen → linked
2. **L'usage du scope** : "voilà, maintenant l'app sauvegarde dans Drive"
3. **Aucune utilisation parallèle non déclarée**

Script (60 secondes) :

```
[0-5s] "Bonjour, voici ShredCoach, une app Android de coach fitness."

[5-15s] [Montrer Réglages → Sauvegarde]
"L'utilisateur peut sauvegarder ses données sportives dans son Google Drive privé."

[15-30s] [Tap "Continuer avec Google" → consent screen]
"On demande UNIQUEMENT le scope drive.appdata, qui crée un dossier app-spécifique
invisible dans Drive."

[30-45s] [Tap "Sauvegarder maintenant" → snackbar succès]
"Les données — séances, repas, photos — sont packagées en ZIP et uploadées
dans ce dossier privé."

[45-60s] [Optionnel : montrer la restauration]
"À tout moment, l'utilisateur peut restaurer depuis Drive sur un nouveau téléphone.
Aucun token n'est stocké, aucune donnée transitée. Merci."
```

**Filme avec ton téléphone**, screen-mirror sur ton PC (via scrcpy gratuit, ou QuickTime sur Mac), et upload sur YouTube en **non répertorié** (unlisted).

---

## Étape 4 — Soumettre la verification

1. OAuth consent screen → onglet **Verification center**
2. Clique **"Submit for verification"**
3. Remplis le formulaire :
   - **Why do you need each scope** : *"drive.appdata is the ONLY scope requested. We use it to store user backup ZIP archives in a hidden app-specific folder in their personal Drive. We never access user files outside this folder. The implementation follows the same pattern as WhatsApp's chat backup feature."*
   - **Vidéo URL** : ton lien YouTube
   - **Privacy policy URL** : confirme l'URL
   - **Domaine** : confirme le domaine

4. Clique Submit.

Google va te répondre par mail dans **2-6 semaines**. Ils peuvent demander des clarifications — réponds vite, ça accélère.

---

## Étape 5 — Pendant l'attente

Tu peux **continuer à dev et publier sur le Play Store** pendant que la verification est en cours. L'app fonctionne, juste avec le warning rouge "Avancé → Continuer".

> ⚠️ Le Play Store a sa **propre review** (séparée de Google Cloud OAuth). Quand tu listes ton app, mets le warning OAuth dans la description : "À la première connexion Google Drive, tu peux voir un écran 'Google n'a pas validé cette app' tant que notre verification est en cours — c'est normal, tape Avancé → Continuer."

---

## Étape 6 — Quand la verification est validée

Tu reçois un mail "Your OAuth verification is complete". À partir de là :
- Le warning rouge disparaît au prochain consent screen
- Tu peux retirer la mention dans la description Play Store
- 🎉

---

## Si quelque chose se passe mal

- **Google rejette ta soumission** : ils donnent une raison ("ajoutez X au privacy", "scope mal justifié"). Corrige et resoumets — pas de pénalité.
- **Tu changes de scope** : tu dois resoumettre la verification entière. Donc évite, à moins d'absolu nécessité.
- **Tu changes de package name ou de signing key** : nouvelle verification requise. Donc fixe ton package `com.shredcoach.app` une fois pour toutes.

---

## Réfs officielles

- OAuth verification FAQ : https://support.google.com/cloud/answer/9110914
- Drive API restricted/sensitive scopes : https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification
- App verification timeline : https://support.google.com/cloud/answer/13463073

---

**Quand tu seras prêt à soumettre, ping-moi** : je peux te relire le formulaire de soumission et la justification du scope avant que tu cliques Submit.
