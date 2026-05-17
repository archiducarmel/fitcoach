package com.shredcoach.app.presentation.glucose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Système de design médical Dr. Glykos / Glucose CGM.
 *
 * Inspiration : Apple Health + Stripe Dashboard + Whoop. Trois principes
 * directeurs :
 *
 *  1. **Hiérarchie typographique** — les chiffres glycémiques sont les héros,
 *     pas les libellés. Numéros 24-44sp `tnum`, labels 10-11sp uppercase à
 *     interlettrage 0.5px, unités 11sp en alpha 0.55.
 *
 *  2. **Contraste WCAG AAA partout** — les gradients hero sont volontairement
 *     foncés (emerald 800→700) pour porter le texte blanc à ≥7:1. La palette
 *     soft (emerald 50/100) n'est utilisée QUE pour les conteneurs avec du
 *     texte emerald 700/800, jamais pour du blanc.
 *
 *  3. **Profondeur sans ombre lourde** — élévation max 4dp, séparation par
 *     contraste de surface + 1px border alpha 0.08, à la Apple. Aucune ombre
 *     "Material par défaut".
 *
 * Toutes les surfaces glucose passent par les helpers exposés ici. Aucun
 * `Color(0xFF...)` ne doit apparaître dans les écrans glucose en dehors de
 * ce fichier.
 */
object GlucoseColors {
    // ─── Brand spectrum (Tailwind Emerald) ──────────────────────────────────
    val Emerald50  = Color(0xFFECFDF5)  // wash bg (empty states, soft chips)
    val Emerald100 = Color(0xFFD1FAE5)  // chip bg avec text emerald 700+
    val Emerald200 = Color(0xFFA7F3D0)  // hover / focus rings
    val Emerald500 = Color(0xFF10B981)  // accent in-range
    val Emerald600 = Color(0xFF059669)  // primary brand
    val Emerald700 = Color(0xFF047857)  // hero gradient end
    val Emerald800 = Color(0xFF065F46)  // text emerald sur soft + Dr. Glykos chat
    val Emerald900 = Color(0xFF064E3B)  // hero gradient start
    val Emerald950 = Color(0xFF022C22)  // ultra deep pour ombre subtile

    // ─── Status colors (clinical) ───────────────────────────────────────────
    val InRange   = Color(0xFF10B981)   // 70-180 mg/dL
    val Warning   = Color(0xFFF59E0B)   // 140-180 ou TIR <70%
    val Critical  = Color(0xFFEF4444)   // >180 (hyperglycémie) ou <70 (hypo)
    val Hypo      = Color(0xFF6366F1)   // indigo, hypoglycémie sévère

    // ─── Accent secondaire (Dr. Glykos signature) ───────────────────────────
    val Teal500   = Color(0xFF14B8A6)
    val Teal700   = Color(0xFF0F766E)

    // ─── Surface helpers ────────────────────────────────────────────────────
    /** Gradient HERO du suivi glycémique : profond, WCAG AAA pour texte blanc. */
    val HeroGradient: List<Color> = listOf(Emerald900, Emerald700)

    /** Variante pour cards moins prééminentes (ex: Dr. Glykos chat avatar). */
    val DeepGradient: List<Color> = listOf(Emerald800, Teal700)

    /** Soft monochrome pour empty states sans gradient (light bg). */
    val SoftGradient: List<Color> = listOf(Emerald50, Color(0xFFF0FDFA))
}

/**
 * Statut clinique d'une métrique glucose. Drive la couleur des status pills,
 * des KPI tiles et des icônes de validation. Centralisé ici pour éviter que
 * chaque écran ré-implémente sa propre logique de seuils.
 */
enum class GlucoseStatus(val color: Color, val icon: ImageVector) {
    InRange(GlucoseColors.InRange, Icons.Default.CheckCircle),
    Warning(GlucoseColors.Warning, Icons.Default.Warning),
    Critical(GlucoseColors.Critical, Icons.Default.Warning),
    Unknown(Color(0xFF94A3B8), Icons.Default.Info);

    companion object {
        /** Status d'une moyenne glycémique journalière. Seuils ADA/EASD 2024. */
        fun forAvg(mgdl: Double?): GlucoseStatus = when {
            mgdl == null -> Unknown
            mgdl in 80.0..130.0 -> InRange
            mgdl in 70.0..150.0 -> Warning
            else -> Critical
        }

        /** Status d'un pic (max journalier). */
        fun forPeak(mgdl: Double?): GlucoseStatus = when {
            mgdl == null -> Unknown
            mgdl < 140.0 -> InRange
            mgdl < 180.0 -> Warning
            else -> Critical
        }

        /** Status d'un minimum (détection d'hypo). */
        fun forMin(mgdl: Double?): GlucoseStatus = when {
            mgdl == null -> Unknown
            mgdl >= 80.0 -> InRange
            mgdl >= 70.0 -> Warning
            else -> Critical
        }

        /** Status d'un Time-In-Range (objectif ADA: ≥70%). */
        fun forTir(pct: Int?): GlucoseStatus = when {
            pct == null -> Unknown
            pct >= 70 -> InRange
            pct >= 50 -> Warning
            else -> Critical
        }

        /** Status d'un coefficient de variation (stabilité, objectif <36%). */
        fun forCv(pct: Double?): GlucoseStatus = when {
            pct == null -> Unknown
            pct < 36.0 -> InRange
            pct < 50.0 -> Warning
            else -> Critical
        }

        /** Status d'un compteur d'hypos (0 = top, >0 = warning). */
        fun forHypoCount(n: Int?): GlucoseStatus = when {
            n == null -> Unknown
            n == 0 -> InRange
            n <= 2 -> Warning
            else -> Critical
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// COMPOSABLES RÉUTILISABLES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Tile KPI premium pour usage sur fond foncé (hero gradient). Glassmorphique :
 * surface blanche alpha 0.14 + 1px border alpha 0.18. Typo en hiérarchie
 * stricte : label uppercase 10sp / value 24sp Black tnum / unit 11sp alpha 0.7.
 *
 * Pourquoi pas `Surface(Color.White.copy(alpha=0.16))` plain : on veut un léger
 * stroke pour matérialiser la tile sur le gradient sans dépendre du gradient
 * sous-jacent. Le 1px border permet de garder le shape lisible sur n'importe
 * quel point du gradient.
 */
@Composable
fun GlucoseHeroKpiTile(
    label: String,
    value: String,
    unit: String,
    status: GlucoseStatus,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = modifier.border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(14.dp),
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                // Point de statut : 6dp circle, hyper-discret, juste un signal.
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(status.color)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Tile KPI pour fond clair (SurfaceVariant ou white). Variante "light" du hero
 * tile : utilisé dans GlucoseDashboard, GlucoseHistoryScreen.
 */
@Composable
fun GlucoseKpiTile(
    label: String,
    value: String,
    unit: String,
    status: GlucoseStatus,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(
            width = 1.dp,
            color = status.color.copy(alpha = 0.18f),
            shape = RoundedCornerShape(14.dp),
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(status.color)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Pill de statut clinique : background coloré + icône + label. Utilisé pour
 * communiquer en un coup d'œil l'état glycémique (in-range, warning, critical).
 */
@Composable
fun GlucoseStatusPill(
    status: GlucoseStatus,
    label: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val bg = if (onDark) Color.White.copy(alpha = 0.18f) else status.color.copy(alpha = 0.12f)
    val fg = if (onDark) Color.White else status.color
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(status.icon, null, Modifier.size(12.dp), tint = fg)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/**
 * Header de card glucose, premium : icône caissetée dans une surface alpha,
 * titre fort, sous-titre subtil. Utilisé en haut de chaque card section.
 */
@Composable
fun GlucoseSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onDark: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (onDark) Color.White else GlucoseColors.Emerald800
    val subtitleColor = if (onDark) Color.White.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val iconBg = if (onDark) Color.White.copy(alpha = 0.18f) else GlucoseColors.Emerald100
    val iconTint = if (onDark) Color.White else GlucoseColors.Emerald600

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = CircleShape, color = iconBg, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = titleColor,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Container hero foncé. Wrap un Column dans une Box avec gradient + padding
 * standard. Toute card "premium glucose" (TodayGlucoseCard, hero du Dashboard,
 * etc.) passe par ici pour rester cohérente.
 */
@Composable
fun GlucoseHeroSurface(
    modifier: Modifier = Modifier,
    gradient: List<Color> = GlucoseColors.HeroGradient,
    cornerRadius: androidx.compose.ui.unit.Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        // Élévation faible volontaire : sur fond clair Material, 2dp suffit
        // pour la séparation. Au-delà, on tombe dans le "Material par défaut"
        // qui casse le côté premium.
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradient))
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}
