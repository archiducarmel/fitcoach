package com.shredcoach.app.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.R
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant

/**
 * Share card portrait — ratio 9:16 (Story IG/TikTok/Snap).
 *
 * **Calibration** : design tight pour rendre proprement à ~**290 × 515 dp**
 * (taille typique du preview du bottom sheet, fillMaxWidth 0.92 sur device
 * 360dp wide). Toutes les fontSize / padding sont dimensionnées pour cette
 * zone, avec un mode compact automatique pour les listes longues afin de
 * toujours afficher tous les exos sans clipping.
 *
 * **Pourquoi pas de scale-up architecture** : tentée précédemment via
 * `requiredSize(360,640) + graphicsLayer scale + transformOrigin(0,0)`, elle
 * produisait un décalage visuel à gauche (quirk des SubcomposeLayout +
 * children overflowing parent constraints) et n'évitait pas le clipping
 * vertical. Plus simple et robuste : rendre directement à preview size avec
 * un design qui tient. Bitmap capturé = preview_dp × density (≈870 px wide à
 * 3x sur device standard) = qualité OK pour Stories qui upscale à 1080.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareCard(
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(brush = ShredCoachBrandGradient),
    ) {
        when (data) {
            is ShareCardData.WorkoutPlanned -> WorkoutPlannedContent(data)
            is ShareCardData.WorkoutInProgress -> WorkoutInProgressContent(data)
            is ShareCardData.ExerciseCompleted -> ExerciseCompletedContent(data)
            is ShareCardData.WorkoutFinished -> WorkoutFinishedContent(data)
            is ShareCardData.StatsAggregate -> StatsContent(data)
            is ShareCardData.HistorySummary -> HistoryContent(data)
        }
        Watermark(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        )
    }
}

// ──────────────────────────────────────────────────────────
// Constants design ~290×515 dp (preview size of bottom sheet)
// ──────────────────────────────────────────────────────────
private val CARD_PADDING_H = 18.dp
private val CARD_PADDING_TOP = 20.dp
private val CARD_PADDING_BOTTOM = 36.dp // place pour le watermark anchored bottom

/**
 * Cap dur du nombre d'items affichés dans la liste exos. Avec le mode
 * ultra-compact (cf. [ULTRA_COMPACT_THRESHOLD]), 16 items tiennent
 * confortablement à preview size ~290×515 dp. Au-delà, footer "+N autres".
 */
private const val MAX_LIST_ITEMS = 16

/** ≥ 9 items : compact mode (single-line, name + metric inline). */
private const val COMPACT_LIST_THRESHOLD = 8

/** ≥ 13 items : ultra-compact (font 9sp, badge 12dp, gap 2dp). */
private const val ULTRA_COMPACT_THRESHOLD = 12

private val ShredCoachBrandGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF6B35),   // OrangeVibrant
        Color(0xFFE63946),   // RedPassion
        Color(0xFF1D3557),   // DeepBlue
    ),
)

// ──────────────────────────────────────────────────────────
// Variantes de contenu
// ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WorkoutPlannedContent(data: ShareCardData.WorkoutPlanned) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Subtitle = muscle groups summary (au lieu d'une row de chips en bas
        // qui mangeait l'espace vertical au détriment de la liste exos).
        val subtitle = if (data.muscleGroups.isNotEmpty())
            data.muscleGroups.take(4).joinToString(" · ")
        else data.subtitle ?: stringResource(R.string.share_card_planned_subtitle)
        CardHeader(title = data.title, subtitle = subtitle)

        HeroMetric(value = data.durationMinutes.toString(), unit = "min", label = stringResource(R.string.share_card_label_duration))

        // InlineStatsRow (hauteur prévisible 50dp) — remplace FlowRow StatChips
        // qui pouvait wrapper sur 2 lignes selon le nombre de chips et créait
        // de l'incertitude sur la hauteur.
        val tiles = mutableListOf(
            InlineStat(Icons.Default.FitnessCenter, "${data.exerciseCount}", "exos"),
        )
        if (data.warmupCount > 0)
            tiles += InlineStat(Icons.Default.LocalFireDepartment, "${data.warmupCount}", "warmups")
        if (data.cardioCount > 0)
            tiles += InlineStat(Icons.Default.Timer, "${data.cardioCount}", "cardio")
        // Pad jusqu'à 4 tiles pour garder la grille équilibrée. Ajoute un
        // tile "min" récapitulatif si pas warmup/cardio.
        if (tiles.size < 4) {
            tiles += InlineStat(Icons.Default.Schedule, "${data.durationMinutes}", "min")
        }
        InlineStatsRow(tiles = tiles.take(4))

        if (data.firstFewExercises.isNotEmpty()) {
            val overflow = (data.firstFewExercises.size - MAX_LIST_ITEMS).coerceAtLeast(0)
            val visible = if (overflow > 0)
                data.firstFewExercises.take(MAX_LIST_ITEMS - 1)
            else data.firstFewExercises
            val realOverflow = data.firstFewExercises.size - visible.size
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                ExerciseList(exercises = visible, overflowCount = realOverflow)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WorkoutInProgressContent(data: ShareCardData.WorkoutInProgress) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CardHeader(title = data.title, subtitle = data.subtitle ?: stringResource(R.string.share_card_in_live))

        // Hero : volume kg si > 0, sinon temps écoulé. Label court ("VOLUME"
        // pas "VOLUME EN COURS" qui dépassait la largeur centrée).
        if (data.totalVolumeKg > 0) {
            HeroMetric(
                value = data.totalVolumeKg.toInt().toString(),
                unit = "kg",
                label = stringResource(R.string.share_card_label_volume),
            )
        } else {
            HeroMetric(
                value = data.elapsedMinutes.toString(),
                unit = "min",
                label = stringResource(R.string.share_card_label_active),
            )
        }

        // Bandeau de progression compact (4 chiffres) — remplace l'ancienne
        // grille 2×2 trop verticale et libère la place pour la liste d'exos.
        InlineStatsRow(
            tiles = listOf(
                InlineStat(Icons.Default.Schedule, "${data.elapsedMinutes}", "min"),
                InlineStat(Icons.Default.FitnessCenter, "${data.exercisesDone}/${data.totalExercises}", "exos"),
                InlineStat(Icons.Default.CheckCircle, "${data.totalSetsCompleted}", stringResource(R.string.share_card_unit_sets)),
                InlineStat(Icons.Default.LocalFireDepartment, "${data.totalReps}", "reps"),
            ),
        )

        // Liste des exercices avec statut visuel : DONE / CURRENT / UPCOMING.
        // weight(1f, fill = true) → la liste reçoit TOUT l'espace résiduel
        // disponible et s'auto-borne. Combiné avec le mode compact si > 8
        // items (police plus petite, métrique inline), 12+ exos tiennent
        // proprement sans clipping.
        if (data.plannedExercises.isNotEmpty()) {
            // Si > MAX_LIST_ITEMS exos, on en cache 1 de plus pour laisser
            // la place au footer "+N autres" sans empiéter sur le reste.
            val overflow = (data.plannedExercises.size - MAX_LIST_ITEMS).coerceAtLeast(0)
            val visible = if (overflow > 0)
                data.plannedExercises.take(MAX_LIST_ITEMS - 1)
            else data.plannedExercises
            val realOverflow = data.plannedExercises.size - visible.size
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                ExerciseProgressList(
                    items = visible,
                    sectionLabel = stringResource(R.string.share_card_progression_section),
                    overflowCount = realOverflow,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.ExerciseCompletedContent(data: ShareCardData.ExerciseCompleted) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CardHeader(
            title = stringResource(if (data.isPersonalRecord) R.string.share_card_pr_title else R.string.share_card_done_title),
            subtitle = data.subtitle,
        )

        Text(
            text = data.title,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            textAlign = TextAlign.Start,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        StatsGrid {
            StatTile(icon = Icons.Default.FitnessCenter, value = "${data.setsCompleted}", unit = "", label = stringResource(R.string.share_card_stat_sets))
            StatTile(icon = Icons.Default.LocalFireDepartment, value = "${data.totalReps}", unit = "reps", label = stringResource(R.string.share_card_stat_volume))
            StatTile(
                icon = Icons.Default.EmojiEvents,
                value = data.volumeKg.toInt().toString(),
                unit = "kg",
                label = stringResource(R.string.share_card_stat_charge),
            )
            StatTile(
                icon = Icons.Default.Schedule,
                value = (data.durationSeconds / 60).toString(),
                unit = "min",
                label = stringResource(R.string.share_card_stat_duration),
            )
        }
        // Note : le coach message volontairement non rendu — la share card
        // doit prioriser les stats brutes (les "key insights" partageables).
        // Le message coach reste dans le caption Intent pour les apps qui
        // l'exploitent, mais pas affiché sur la card visuelle.
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.WorkoutFinishedContent(data: ShareCardData.WorkoutFinished) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header avec trophy badge inline.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                CardHeader(title = data.title, subtitle = data.subtitle ?: stringResource(R.string.share_card_session_complete))
            }
        }

        // Hero : volume si > 0 (chiffre impactant), sinon durée. Évite la
        // valeur à zéro qui produit un "0 kg" anti-bling-bling.
        if (data.totalVolumeKg > 0) {
            HeroMetric(
                value = data.totalVolumeKg.toInt().toString(),
                unit = "kg",
                label = stringResource(R.string.share_card_label_volume),
            )
        } else {
            HeroMetric(
                value = (data.durationSeconds / 60).toString(),
                unit = "min",
                label = stringResource(R.string.share_card_label_duration),
            )
        }

        InlineStatsRow(
            tiles = listOf(
                InlineStat(Icons.Default.Schedule, "${data.durationSeconds / 60}", "min"),
                InlineStat(Icons.Default.FitnessCenter, "${data.exerciseCount}", "exos"),
                InlineStat(Icons.Default.CheckCircle, "${data.totalSets}", stringResource(R.string.share_card_unit_sets)),
                InlineStat(Icons.Default.LocalFireDepartment, "${data.totalReps}", "reps"),
            ),
        )

        if (data.completedExercises.isNotEmpty()) {
            val overflow = (data.completedExercises.size - MAX_LIST_ITEMS).coerceAtLeast(0)
            val visible = if (overflow > 0)
                data.completedExercises.take(MAX_LIST_ITEMS - 1)
            else data.completedExercises
            val realOverflow = data.completedExercises.size - visible.size
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                ExerciseProgressList(
                    items = visible,
                    sectionLabel = stringResource(R.string.share_card_exercises_done_section),
                    overflowCount = realOverflow,
                )
            }
        }
        // Note : coach message volontairement non affiché ici. Le résumé
        // post-séance est déjà visible sur l'écran Summary, et la share card
        // doit prioriser la liste des exos + métriques (les "key insights"
        // que les viewers veulent voir).
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.StatsContent(data: ShareCardData.StatsAggregate) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardHeader(title = "${data.accentEmoji} ${data.title}", subtitle = data.subtitle)

        StatsGrid {
            data.keyMetrics.take(6).forEach { m ->
                StatTile(
                    icon = pickIconForMetric(m.label),
                    value = m.value,
                    unit = m.unit ?: "",
                    label = m.label,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.HistoryContent(data: ShareCardData.HistorySummary) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CARD_PADDING_H, vertical = CARD_PADDING_TOP)
            .padding(bottom = CARD_PADDING_BOTTOM - CARD_PADDING_TOP),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardHeader(title = "${data.accentEmoji} ${data.title}", subtitle = data.subtitle)

        HeroMetric(
            value = data.totalCount.toString(),
            unit = "",
            label = data.countLabel.uppercase(),
        )

        StatsGrid {
            data.keyMetrics.take(4).forEach { m ->
                StatTile(
                    icon = pickIconForMetric(m.label),
                    value = m.value,
                    unit = m.unit ?: "",
                    label = m.label,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// Atoms
// ──────────────────────────────────────────────────────────

@Composable
private fun CardHeader(title: String, subtitle: String?) {
    Column(horizontalAlignment = Alignment.Start) {
        if (subtitle != null) {
            Text(
                text = subtitle.uppercase(),
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 17.sp,
            lineHeight = 19.sp,
            maxLines = 2,
        )
    }
}

/**
 * Affichage hero — calibré tight pour laisser de la place à la liste des
 * 16 exos. Hauteur totale ~62-72dp (label + valeur + spacing).
 *  - 1-3 chars : 56sp ("85", "120")
 *  - 4 chars : 44sp ("1234")
 *  - 5+ chars : 36sp ("12345")
 */
@Composable
private fun HeroMetric(value: String, unit: String, label: String) {
    val fontSizeSp = when {
        value.length <= 3 -> 56
        value.length == 4 -> 44
        else -> 36
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = fontSizeSp.sp,
                lineHeight = fontSizeSp.sp,
                maxLines = 1,
                softWrap = false,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = unit,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = (fontSizeSp / 14).dp),
                )
            }
        }
    }
}

/**
 * Bandeau horizontal de mini-stats (4 valeurs chacune avec icône). Plus
 * compact que [StatsGrid] (2 lignes), libère la place verticale pour la
 * liste d'exercices. Utilisé sur [WorkoutInProgressContent] et
 * [WorkoutFinishedContent] qui veulent montrer 4-5 chiffres + une liste.
 */
private data class InlineStat(val icon: ImageVector, val value: String, val label: String)

@Composable
private fun InlineStatsRow(tiles: List<InlineStat>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(vertical = 7.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        tiles.forEach { tile ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    tile.icon,
                    null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = tile.value,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = tile.label,
                    color = Color.White.copy(alpha = 0.65f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 7.sp,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Liste d'exercices avec status badge (DONE/CURRENT/UPCOMING/SKIPPED).
 * Variante riche de [ExerciseList] qui affiche aussi un statut visuel par
 * item — utile pour montrer la progression d'une séance partagée live ou
 * la composition d'une séance terminée (avec exos skippés barrés).
 */
/**
 * Liste d'exercices avec status badge — 3 tiers de densité auto-sélectionnés
 * selon le nombre d'items :
 *  - **Standard** (≤ [COMPACT_LIST_THRESHOLD]) : 2-lines par item (nom + métrique
 *    en sous-titre), fonts 13/10sp, badge 16dp. Aéré, lisible, premium.
 *  - **Compact** (> 8 et ≤ 12) : single-line "nom · 4×10 80kg", fonts 11/9sp,
 *    badge 14dp.
 *  - **Ultra-compact** (> 12) : single-line, fonts 9/8sp, badge 12dp, gap 2dp.
 *    Permet de faire tenir 16+ items sans clipping vertical.
 *
 * **Overflow indicator** : si la liste source dépasse [MAX_LIST_ITEMS],
 * l'appelant doit avoir déjà passé le sous-set affichable et fournir
 * [overflowCount] > 0 pour qu'on rende un footer "+ N autres exos".
 */
@Composable
private fun ExerciseProgressList(
    items: List<ShareCardData.ExerciseProgressItem>,
    sectionLabel: String,
    overflowCount: Int = 0,
) {
    val effectiveSize = items.size + (if (overflowCount > 0) 1 else 0)
    val ultraCompact = effectiveSize > ULTRA_COMPACT_THRESHOLD
    val compact = ultraCompact || effectiveSize > COMPACT_LIST_THRESHOLD
    val rowGap = when {
        ultraCompact -> 1.dp
        compact -> 3.dp
        else -> 5.dp
    }
    val nameSize = when {
        ultraCompact -> 9
        compact -> 11
        else -> 13
    }
    // lineHeight de 1dp au-dessus de fontSize : compresse la hauteur de ligne
    // pour tenir 16+ items en ultra-compact (sinon Compose ajoute du padding
    // intrinsèque qui inflate la hauteur de chaque row).
    val nameLineHeight = nameSize + 1
    val metricSize = when {
        ultraCompact -> 8
        compact -> 9
        else -> 10
    }
    val metricLineHeight = metricSize + 1
    val badgeSize = when {
        ultraCompact -> 11
        compact -> 14
        else -> 16
    }
    val badgeIconSize = when {
        ultraCompact -> 7
        compact -> 9
        else -> 10
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = sectionLabel,
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(item.status, badgeSize.dp, badgeIconSize.dp)
                Spacer(Modifier.width(7.dp))
                val nameAlpha = when (item.status) {
                    ShareCardData.ExerciseStatus.UPCOMING -> 0.65f
                    ShareCardData.ExerciseStatus.SKIPPED -> 0.45f
                    else -> 1f
                }
                if (compact) {
                    // Layout single-line : "Nom · 4×10 80kg"
                    val combined = if (!item.metric.isNullOrBlank())
                        "${item.name}  ·  ${item.metric}" else item.name
                    Text(
                        text = combined,
                        color = Color.White.copy(alpha = nameAlpha),
                        fontWeight = if (item.status == ShareCardData.ExerciseStatus.CURRENT)
                            FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = nameSize.sp,
                        lineHeight = nameLineHeight.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White.copy(alpha = nameAlpha),
                            fontWeight = if (item.status == ShareCardData.ExerciseStatus.CURRENT)
                                FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = nameSize.sp,
                            lineHeight = nameLineHeight.sp,
                            maxLines = 1,
                        )
                        if (!item.metric.isNullOrBlank()) {
                            Text(
                                text = item.metric,
                                color = Color.White.copy(alpha = 0.55f),
                                fontWeight = FontWeight.Medium,
                                fontSize = metricSize.sp,
                                lineHeight = metricLineHeight.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (overflowCount > 0) {
            OverflowFooter(count = overflowCount, badgeSize = badgeSize.dp, fontSize = nameSize)
        }
    }
}

/**
 * Footer "+N autres" affiché en fin de liste quand la source contenait plus
 * d'items que [MAX_LIST_ITEMS]. Cohérent avec la taille des badges de statut
 * pour s'aligner visuellement avec la liste dans tous les modes.
 */
@Composable
private fun OverflowFooter(count: Int, badgeSize: androidx.compose.ui.unit.Dp, fontSize: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Black,
                fontSize = (fontSize - 2).sp,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = "+$count autres exos",
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusBadge(
    status: ShareCardData.ExerciseStatus,
    badgeSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    val (icon, bgColor, iconTint) = when (status) {
        ShareCardData.ExerciseStatus.DONE ->
            Triple(Icons.Default.CheckCircle, NeonGreen, Color.White)
        ShareCardData.ExerciseStatus.CURRENT ->
            Triple(Icons.Default.PlayArrow, OrangeVibrant, Color.White)
        ShareCardData.ExerciseStatus.UPCOMING ->
            Triple(Icons.Default.Bolt, Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.7f))
        ShareCardData.ExerciseStatus.SKIPPED ->
            Triple(Icons.Default.Close, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.55f))
    }
    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsGrid(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.StatTile(
    icon: ImageVector,
    value: String,
    unit: String,
    label: String,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Column {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    maxLines = 1,
                    softWrap = false,
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit,
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ExerciseList(exercises: List<String>, overflowCount: Int = 0) {
    val effectiveSize = exercises.size + (if (overflowCount > 0) 1 else 0)
    val ultraCompact = effectiveSize > ULTRA_COMPACT_THRESHOLD
    val compact = ultraCompact || effectiveSize > COMPACT_LIST_THRESHOLD
    val rowGap = when {
        ultraCompact -> 1.dp
        compact -> 3.dp
        else -> 4.dp
    }
    val nameSize = when {
        ultraCompact -> 9
        compact -> 11
        else -> 13
    }
    val nameLineHeight = nameSize + 1
    val bulletSize = when {
        ultraCompact -> 3
        compact -> 4
        else -> 5
    }

    Column(verticalArrangement = Arrangement.spacedBy(rowGap), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.share_card_program_section),
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
        exercises.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(bulletSize.dp).background(NeonGreen, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = nameSize.sp,
                    lineHeight = nameLineHeight.sp,
                    maxLines = 1,
                )
            }
        }
        if (overflowCount > 0) {
            // Approxime un badge en réutilisant la taille du bullet (point) + 4dp.
            OverflowFooter(
                count = overflowCount,
                badgeSize = (bulletSize + 8).dp,
                fontSize = nameSize,
            )
        }
    }
}

@Composable
private fun Watermark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(16.dp).background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                null,
                tint = OrangeVibrant,
                modifier = Modifier.size(10.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "shredcoach",
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

private fun pickIconForMetric(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        l.contains("temps") || l.contains("durée") || l.contains("min") -> Icons.Default.Schedule
        l.contains("volume") || l.contains("kg") || l.contains("charge") -> Icons.Default.EmojiEvents
        l.contains("séance") || l.contains("séries") || l.contains("set") -> Icons.Default.FitnessCenter
        l.contains("kcal") || l.contains("cal") || l.contains("réc") -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.CheckCircle
    }
}
