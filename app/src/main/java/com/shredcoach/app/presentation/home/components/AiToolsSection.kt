package com.shredcoach.app.presentation.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.R
import com.shredcoach.app.presentation.navigation.Screen
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Section "Tes assistants" — point d'entrée premium vers les 4 features
 * AI-powered de l'app : Shreddy chat, Meal Analyser, Body Scanner, Gym Scan.
 *
 * Pourquoi le terme "assistants" plutôt que "Outils IA" : le mot "IA"
 * évoque pour beaucoup d'utilisateurs des connotations défensives
 * (vie privée, remplacement humain, gadget). "Tes assistants" personnifie
 * et humanise les features — chacune est un coach spécialisé dédié à
 * l'user, pas un outil froid. Cf. Apple "Intelligence", Google "Magic
 * Eraser", Spotify "AI DJ" : les apps premium positionnent l'IA comme
 * un service personnel, pas une étiquette technique.
 *
 * Layout :
 *  - Header avec icône ✨ + titre + badge "Premium"
 *  - Tagline coachée 1 ligne
 *  - Grille 2×2 de [AiToolCard], chaque assistant avec sa palette dédiée
 *
 * Les couleurs sont distinctes pour différencier les 4 assistants
 * visuellement (mémorisable : "Shreddy violet", "scan repas orange",
 * "scan corps cyan", "scan salle vert").
 */
@Composable
fun AiToolsSection(
    onShreddyClick: () -> Unit,
    onMealScanClick: () -> Unit,
    onBodyScanClick: () -> Unit,
    onGymScanClick: () -> Unit,
    onDrGlykosClick: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ─── Header ───
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Icône AutoAwesome avec micro-pulse subtle pour signaler "vivant"
            // (vs idle). 0.95→1.05 sur 2.4s, easeInOut → respiration discrète.
            val infiniteTransition = rememberInfiniteTransition(label = "ai-pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ai-pulse-scale"
            )
            Icon(
                Icons.Default.AutoAwesome, null,
                modifier = Modifier.size(20.dp).graphicsLayer {
                    scaleX = scale; scaleY = scale
                },
                tint = OrangeVibrant
            )
            Text(stringResource(R.string.home_assistants_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Badge "Premium" — accent valorisant qui dédramatise vs "AI"
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = OrangeVibrant.copy(alpha = 0.15f)
            ) {
                Text(
                    stringResource(R.string.home_assistants_badge_premium),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = OrangeVibrant,
                    fontSize = 9.sp
                )
            }
        }

        Text(
            stringResource(R.string.home_assistants_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 2,
            lineHeight = 16.sp
        )

        // ─── Grille 2×2 ───
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiToolCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Shreddy",
                subtitle = stringResource(R.string.home_assistant_shreddy_subtitle),
                icon = Icons.AutoMirrored.Filled.Chat,
                gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)), // violet → rose
                onClick = onShreddyClick
            )
            AiToolCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Meal Scan",
                subtitle = stringResource(R.string.home_assistant_mealscan_subtitle),
                icon = Icons.Default.PhotoCamera,
                gradient = listOf(OrangeVibrant, Color(0xFFEF4444)), // orange → rouge
                onClick = onMealScanClick
            )
        }
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AiToolCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Body Scan",
                subtitle = stringResource(R.string.home_assistant_bodyscan_subtitle),
                icon = Icons.Default.Accessibility,
                gradient = listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), // cyan → blue
                onClick = onBodyScanClick
            )
            AiToolCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Gym Scan",
                subtitle = stringResource(R.string.home_assistant_gymscan_subtitle),
                icon = Icons.Default.QrCodeScanner,
                gradient = listOf(NeonGreen, Color(0xFF14B8A6)), // green → teal
                onClick = onGymScanClick
            )
        }
        // 3e rang : Dr. Glykos (full-width, mis en avant — c'est la persona
        // premium endocrino, on lui donne une card pleine largeur).
        AiToolCard(
            modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
            title = stringResource(R.string.ai_tools_dr_glykos),
            subtitle = stringResource(R.string.chat_dr_glykos_subtitle, "CGM"),
            icon = Icons.Default.MedicalServices,
            gradient = listOf(Color(0xFF0F4C75), Color(0xFF3B82F6)), // bleu médical → blue
            onClick = onDrGlykosClick,
        )
    }
}

/**
 * Card individuelle d'un outil IA.
 *
 * Design pattern :
 *  - Background gradient vif sur toute la card (effet "spot of color").
 *  - Icône blanche dans un cercle alpha pour le contraste sur tout gradient.
 *  - Titre en blanc bold, sous-titre en blanc 0.85 alpha.
 *  - ChevronRight pour signaler l'action.
 *  - Hauteur min ~118dp pour cohérence visuelle quel que soit le sous-titre.
 *  - shape arrondi 20dp (premium feel vs 12dp utilitaire).
 *
 * Pourquoi gradient en `linearGradient` plutôt que `radialGradient` :
 *  - linear donne un effet "mouvement" diagonal qui guide l'œil.
 *  - radial centré donne un look "spotlight" plus statique, moins dynamique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiToolCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 118.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = gradient.map { it.copy(alpha = 0.95f) }
                )
            ).padding(14.dp)
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header : icône + chevron
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, Modifier.size(20.dp), tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Default.ChevronRight, null,
                        Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
                // Footer : titre + subtitle.
                // Sous-titres délibérément < 23 chars pour tenir sur 1 ligne
                // sur petit écran, max 2 lignes sur fontScale élevé.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
