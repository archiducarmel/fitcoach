package com.shredcoach.app.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Politique de confidentialité — texte template à adapter avec un avocat
 * avant publication. Le wording ci-dessous donne les bonnes briques RGPD
 * (responsable de traitement, finalités, transferts tiers, droits utilisateur,
 * durées de conservation) mais NE remplace PAS un audit juridique.
 *
 * Les sections marquées `[À COMPLÉTER]` doivent être remplies avant de
 * publier l'app sur le Play Store. Google exige une URL de privacy policy
 * publique en plus de cette version in-app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Politique de confidentialité", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(
                title = "En résumé",
                body = "ShredCoach stocke tes données (séances, repas, photos, conversations) " +
                    "uniquement sur ton téléphone. Aucun serveur ShredCoach ne reçoit tes informations. " +
                    "Les seules exceptions : Shreddy IA (chat) et le scan repas/corps, qui envoient ton " +
                    "texte ou tes photos à des fournisseurs IA tiers (Groq, OpenAI, Claude, Gemini, " +
                    "Mistral) que tu choisis explicitement dans les paramètres.",
            )

            Section(
                title = "1. Responsable de traitement",
                body = "[À COMPLÉTER : nom, raison sociale, adresse, email RGPD du responsable de " +
                    "traitement. Ex : Sitou — contact@example.com]",
            )

            Section(
                title = "2. Données collectées et finalités",
                body = """Données stockées localement sur ton appareil :
• Profil : prénom, nom, âge, poids, objectifs, niveau, blessures
• Séances : exercices, séries, poids, durée, historique
• Nutrition : repas, macros, scans, programme
• Conversations Shreddy IA : tes messages et les réponses
• Photos : photos de progression, scans repas, scans gym

Finalités : t'accompagner dans ta sèche, calculer tes statistiques, " +
                    "personnaliser tes recommandations.""",
            )

            Section(
                title = "3. Transferts vers des fournisseurs IA tiers",
                body = "Lorsque tu utilises Shreddy (chat) ou un scan IA, ton message ou ta photo est " +
                    "envoyé directement (sans intermédiaire ShredCoach) au fournisseur que tu as choisi. " +
                    "Tu fournis ta propre clé API. Les fournisseurs ont leurs propres politiques :\n\n" +
                    "• Groq (États-Unis) — https://groq.com/privacy-policy\n" +
                    "• OpenAI (États-Unis) — https://openai.com/policies/privacy-policy\n" +
                    "• Anthropic Claude (États-Unis) — https://www.anthropic.com/privacy\n" +
                    "• Google Gemini — https://policies.google.com/privacy\n" +
                    "• Mistral (France) — https://mistral.ai/terms/\n\n" +
                    "Ces transferts hors UE sont fondés sur l'art. 49.1.a RGPD (consentement explicite). " +
                    "Tu peux refuser : l'app fonctionnera sans ces fonctions.",
            )

            Section(
                title = "4. Sauvegarde locale",
                body = "Si tu actives la sauvegarde, ShredCoach écrit une archive ZIP dans le dossier " +
                    "que TU choisis (Drive, OneDrive, Dropbox, local). Le contenu et la sécurité de ce " +
                    "dossier dépendent du fournisseur que tu as sélectionné — leur politique s'applique " +
                    "à l'archive. ShredCoach ne lit pas l'archive après écriture.",
            )

            Section(
                title = "5. Tes droits RGPD",
                body = "• **Accès** : tu peux exporter toutes tes données via Paramètres > Sauvegarde " +
                    "> Sauvegarder maintenant (archive JSON + photos lisibles).\n\n" +
                    "• **Rectification** : tu modifies directement tes données dans l'app.\n\n" +
                    "• **Effacement** : Paramètres > Supprimer toutes mes données. Tout est effacé du " +
                    "téléphone en une opération atomique.\n\n" +
                    "• **Portabilité** : l'archive de sauvegarde est en JSON ouvert + photos JPEG.\n\n" +
                    "• **Opposition / retrait du consentement** : désactive Shreddy ou les scans IA dans " +
                    "les paramètres pour stopper les transferts vers les fournisseurs tiers.",
            )

            Section(
                title = "6. Durée de conservation",
                body = "Les données restent sur ton téléphone tant que tu utilises l'app. Tu peux les " +
                    "effacer à tout moment via Paramètres. Lors de la désinstallation de l'app, Android " +
                    "supprime automatiquement toutes les données locales (sauf les sauvegardes que tu as " +
                    "exportées dans ton cloud).",
            )

            Section(
                title = "7. Sécurité",
                body = "Tes clés API IA sont chiffrées via Android Keystore (AES-256-GCM, hardware-backed " +
                    "sur la majorité des appareils). Le trafic réseau utilise TLS 1.2+. La base de données " +
                    "Room est protégée par le chiffrement disque Android (FBE) si activé sur ton appareil.",
            )

            Section(
                title = "8. Cookies, traceurs, analytics",
                body = "ShredCoach n'utilise AUCUN cookie, AUCUN tracker, AUCUN outil d'analytics. " +
                    "Aucune publicité.",
            )

            Section(
                title = "9. Réclamation",
                body = "Si tu estimes que ShredCoach traite tes données de manière non conforme, tu " +
                    "peux contacter le responsable (cf. section 1) ou déposer une réclamation auprès " +
                    "de la CNIL (cnil.fr).",
            )

            Section(
                title = "10. Modifications",
                body = "Cette politique peut évoluer. Toute modification substantielle déclenchera un " +
                    "renouvellement de consentement à la prochaine ouverture de l'app.",
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Version 1 — 2026-05-05",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
