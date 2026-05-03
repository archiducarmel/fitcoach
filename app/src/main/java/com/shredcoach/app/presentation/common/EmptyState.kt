package com.shredcoach.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Empty state motivant et reutilisable pour tous les ecrans.
 *
 * @param icon Icone d'illustration (150dp, fond circulaire OrangeVibrant leger)
 * @param title Titre accroche (titleLarge, Bold)
 * @param description Description 2 lignes max (bodyMedium, onSurfaceVariant)
 * @param ctaLabel Label du bouton CTA (null = pas de bouton)
 * @param ctaIcon Icone du bouton CTA (null = pas d'icone)
 * @param onCtaClick Action du bouton CTA
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    ctaLabel: String? = null,
    ctaIcon: ImageVector? = null,
    onCtaClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustration : cercle dégradé avec icône centrée
        Box(
            modifier = Modifier.size(150.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(110.dp).clip(CircleShape).background(OrangeVibrant.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(56.dp), tint = OrangeVibrant)
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

        if (ctaLabel != null && onCtaClick != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (ctaIcon != null) {
                    Icon(ctaIcon, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(ctaLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
