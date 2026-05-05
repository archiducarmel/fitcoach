package com.shredcoach.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Error state réutilisable — miroir d'[EmptyState] mais focus sur les erreurs.
 *
 * À utiliser quand un écran/section ne peut pas afficher son contenu pour
 * cause de panne (réseau, parse JSON, exception serveur). Le message reste
 * humain ; le call-to-action est explicite (« Réessayer » par défaut).
 *
 * @param title Titre court (ex: « Connexion perdue »)
 * @param description Détail (1-3 lignes max, doit être actionnable)
 * @param icon Icône d'illustration. Default = ErrorOutline.
 * @param retryLabel Label du CTA. Default = « Réessayer ».
 * @param onRetry Action de retry. null = pas de bouton.
 */
@Composable
fun ErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ErrorOutline,
    retryLabel: String = "Réessayer",
    onRetry: (() -> Unit)? = null
) {
    val errorTint = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration : double cercle dégradé (cohérent avec EmptyState)
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(errorTint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(errorTint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = errorTint
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            ShredButton(
                text = retryLabel,
                onClick = onRetry,
                variant = ShredButtonVariant.Primary,
                leadingIcon = Icons.Default.Refresh
            )
        }
    }
}
