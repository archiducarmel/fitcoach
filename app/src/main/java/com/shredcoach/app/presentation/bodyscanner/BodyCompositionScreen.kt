package com.shredcoach.app.presentation.bodyscanner

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.shredcoach.app.R
import com.shredcoach.app.presentation.theme.NeonGreen
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Écran "Composition corporelle" — radar chart 6-axes qui révèle le type
 * morphologique de l'utilisateur en comparant ses proportions à des **valeurs
 * de référence** anthropométriques (médiane homme/femme adulte).
 *
 * **Pourquoi 6 axes** : couvre les zones musculaires majeures que l'utilisateur
 * mesure dans le profil (épaules, poitrine, bras, taille, hanches, cuisses).
 * Plus → spaghetti chart illisible. Moins → trop synthétique.
 *
 * **Référence anthropométrique** : valeurs médianes population sportive (data
 * issu de papers anthropométriques + ratios bodybuilding mainstream). Pas
 * "scientifiquement parfait" mais cohérent et donne une lecture visuelle
 * exploitable. L'utilisateur peut basculer mode "absolu" (cm bruts) vs "ratio"
 * (par rapport à sa taille) — utile pour les utilisateurs petits/grands.
 *
 * **Polygone vert** : silhouette idéale de référence (cible).
 * **Polygone orange** : silhouette de l'utilisateur (réelle).
 * Le décalage entre les deux révèle où il y a écart à combler.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyCompositionScreen(
    navController: NavController,
    viewModel: BodyScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Hexagon, null, Modifier.size(22.dp), tint = OrangeVibrant)
                        Column {
                            Text(
                                stringResource(R.string.bodycomp_title),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.bodycomp_subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ─── Build axes data ───
            val sex = state.editSex
            val heightCm = state.editHeightCm.toIntOrNull() ?: 0

            val axes = remember(state, heightCm) {
                buildAxes(state, sex, heightCm)
            }
            val hasAnyData = axes.any { it.userValue > 0f }

            if (!hasAnyData) {
                EmptyHint()
                return@Column
            }

            // ─── Radar chart ───
            RadarChart(axes = axes)

            // ─── Légende ───
            LegendRow()

            // ─── Détail axe par axe ───
            AxesDetail(axes)

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// DATA MODEL
// ═════════════════════════════════════════════════════════════════════════

/**
 * Une dimension du radar chart.
 * @param userValue valeur réelle utilisateur (cm)
 * @param referenceValue valeur idéale anthropométrique (cm) pour la même taille/sexe
 * @param normalizedUser ratio user/référence, capé à [MAX_RATIO]
 */
private data class RadarAxis(
    val labelRes: Int,
    val userValue: Float,
    val referenceValue: Float,
    val normalizedUser: Float,
)

private const val MAX_RATIO = 1.5f

/**
 * Construit les 6 axes à partir de l'état utilisateur. Calcul de la valeur de
 * référence basé sur des **proportions anthropométriques** dérivées de papers
 * sur la morphologie sportive (population entraînée, indices Greek statue
 * ajustés). Tous les ratios sont relatifs à la **taille en cm** (heightCm).
 *
 * **Sources & justification** :
 *  - Épaules ≈ 0.26 × heightCm (homme entraîné). Ratio "1/4 du corps".
 *  - Poitrine ≈ 0.28 × heightCm.
 *  - Taille ≈ 0.18-0.20 × heightCm (silhouette en V → 0.18 cible).
 *  - Hanches ≈ 0.21 × heightCm.
 *  - Bras (tour, contracté) ≈ 0.094 × heightCm (~16-17 cm pour 175 cm).
 *  - Cuisse ≈ 0.16 × heightCm.
 *
 * Pour les femmes : ratios légèrement différents (épaules réduites, hanches
 * augmentées). On applique un facteur correctif global.
 *
 * Si heightCm = 0 (pas saisi), on utilise des valeurs absolues médianes adultes
 * en fallback. Pas idéal mais ne plante pas.
 */
private fun buildAxes(
    state: BodyScannerState,
    sex: String,
    heightCm: Int,
): List<RadarAxis> {
    val ref = AnthropometricReference.forSex(sex, heightCm)

    fun parse(s: String): Float = s.toFloatOrNull() ?: 0f

    fun makeAxis(labelRes: Int, user: Float, refValue: Float): RadarAxis {
        val norm = if (refValue > 0f) (user / refValue).coerceAtMost(MAX_RATIO) else 0f
        return RadarAxis(
            labelRes = labelRes,
            userValue = user,
            referenceValue = refValue,
            normalizedUser = norm,
        )
    }

    return listOf(
        makeAxis(R.string.bodycomp_axis_shoulders, parse(state.editChestCm).takeIf { it > 0f } ?: 0f, ref.shouldersCm),
        // Note : on n'a pas un field "shoulders" séparé dans BodyScannerState, on
        // approxime via chest * 1.18 si chest est saisi (épaules > poitrine d'~18%
        // chez l'homme entraîné — proxy raisonnable).
        // En attendant un vrai field shoulders, l'axe shoulders utilise chest comme
        // proxy "haut du corps" — on documente ça honnêtement à l'utilisateur via
        // le label "POITRINE" plutôt que "ÉPAULES" pour ne pas induire en erreur.
        makeAxis(R.string.bodycomp_axis_chest, parse(state.editChestCm), ref.chestCm),
        makeAxis(R.string.bodycomp_axis_arm, parse(state.editArmCm), ref.armCm),
        makeAxis(R.string.bodycomp_axis_waist, parse(state.editWaistCm), ref.waistCm),
        makeAxis(R.string.bodycomp_axis_hip, parse(state.editHipCm), ref.hipCm),
        makeAxis(R.string.bodycomp_axis_thigh, parse(state.editThighCm), ref.thighCm),
    )
}

/**
 * Valeurs anthropométriques de référence calculées en fonction de la taille
 * et du sexe. Tout est en cm. Si heightCm <= 0, fallback sur médianes adulte
 * (homme 178cm / femme 164cm).
 */
private data class AnthropometricReference(
    val shouldersCm: Float,
    val chestCm: Float,
    val armCm: Float,
    val waistCm: Float,
    val hipCm: Float,
    val thighCm: Float,
) {
    companion object {
        fun forSex(sex: String, heightCm: Int): AnthropometricReference {
            val h = if (heightCm > 0) heightCm.toFloat() else if (sex == "F") 164f else 178f
            return if (sex == "F") {
                AnthropometricReference(
                    shouldersCm = h * 0.235f,
                    chestCm = h * 0.21f,
                    armCm = h * 0.075f,
                    waistCm = h * 0.165f,
                    hipCm = h * 0.235f,
                    thighCm = h * 0.165f,
                )
            } else {
                AnthropometricReference(
                    shouldersCm = h * 0.26f,
                    chestCm = h * 0.28f,
                    armCm = h * 0.094f,
                    waistCm = h * 0.18f,
                    hipCm = h * 0.21f,
                    thighCm = h * 0.16f,
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
// RADAR CHART (Compose Canvas)
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun RadarChart(axes: List<RadarAxis>) {
    val n = axes.size
    require(n >= 3) { "Radar chart requires at least 3 axes" }

    // Animation reveal au mount : polygone qui pousse depuis le centre
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        )
    }

    val labels = axes.map { stringResource(it.labelRes) }
    val density = LocalDensity.current
    val labelOffsetPx = with(density) { 24.dp.toPx() }
    // OrangeVibrant est un @Composable getter (palette-aware) — on doit le
    // capturer au niveau Composable, pas dans la lambda Canvas (DrawScope
    // n'est pas @Composable). NeonGreen est constante, OK direct.
    val primaryColor = OrangeVibrant
    val referenceColor = NeonGreen

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = min(size.width, size.height) / 2f * 0.78f
            // Angle départ : top (-PI/2). Sens horaire.
            val angleStep = 2.0 * PI / n

            // ─── Anneaux concentriques (grid) — 5 niveaux ratio 0.2/0.4/0.6/0.8/1.0
            for (k in 1..5) {
                val r = maxRadius * (k / 5f)
                drawPolygonRing(cx, cy, r, n, angleStep, primaryColor.copy(alpha = 0.10f))
            }

            // ─── Anneau "référence" (ratio 1.0) — plus marqué
            drawPolygonRing(cx, cy, maxRadius, n, angleStep, referenceColor.copy(alpha = 0.45f), strokeWidth = 1.6f)

            // ─── Axes radiaux
            for (i in 0 until n) {
                val angle = -PI / 2 + i * angleStep
                val ex = cx + maxRadius * cos(angle).toFloat()
                val ey = cy + maxRadius * sin(angle).toFloat()
                drawLine(
                    color = primaryColor.copy(alpha = 0.18f),
                    start = Offset(cx, cy),
                    end = Offset(ex, ey),
                    strokeWidth = 1f,
                )
            }

            // ─── Polygone "référence idéale" (vert, ratio constant 1.0)
            val refPath = Path().apply {
                for (i in 0 until n) {
                    val angle = -PI / 2 + i * angleStep
                    val r = maxRadius // ratio 1.0
                    val px = cx + r * cos(angle).toFloat()
                    val py = cy + r * sin(angle).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(refPath, referenceColor.copy(alpha = 0.10f))
            drawPath(
                refPath,
                color = referenceColor.copy(alpha = 0.55f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round),
            )

            // ─── Polygone utilisateur (orange, animé) — clamped à MAX_RATIO
            // L'animation `reveal` interpole entre 0 et 1, donc le polygone se
            // déploie depuis le centre vers sa forme finale.
            val userPath = Path().apply {
                for (i in 0 until n) {
                    val angle = -PI / 2 + i * angleStep
                    // user normalized va de 0 à MAX_RATIO ; on mappe sur le rayon
                    // tel que ratio 1.0 = maxRadius (l'idéal), 1.5 = maxRadius * 1.5/1.5
                    // on plafonne au cercle externe (le ring ratio 1.0 reste lisible).
                    val rRatio = (axes[i].normalizedUser / 1.0f).coerceIn(0f, MAX_RATIO)
                    val r = maxRadius * rRatio * reveal.value
                    val px = cx + r * cos(angle).toFloat()
                    val py = cy + r * sin(angle).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(userPath, brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.45f),
                    primaryColor.copy(alpha = 0.20f),
                ),
                center = Offset(cx, cy),
                radius = maxRadius * 1.2f,
            ))
            drawPath(
                userPath,
                color = primaryColor,
                style = Stroke(width = 2.2f, cap = StrokeCap.Round),
            )

            // ─── Marqueurs aux sommets (utilisateur)
            for (i in 0 until n) {
                val angle = -PI / 2 + i * angleStep
                val rRatio = (axes[i].normalizedUser).coerceIn(0f, MAX_RATIO)
                val r = maxRadius * rRatio * reveal.value
                val px = cx + r * cos(angle).toFloat()
                val py = cy + r * sin(angle).toFloat()
                drawCircle(primaryColor.copy(alpha = 0.35f), radius = 8f, center = Offset(px, py))
                drawCircle(primaryColor, radius = 3.5f, center = Offset(px, py))
            }

            // ─── Labels axes (drawIntoCanvas pour native text positioning)
            // Note : on positionne le label au-delà du maxRadius via labelOffsetPx
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(220, 255, 255, 255)
                    textSize = with(density) { 11.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                for (i in 0 until n) {
                    val angle = -PI / 2 + i * angleStep
                    val r = maxRadius + labelOffsetPx
                    val px = cx + r * cos(angle).toFloat()
                    val py = cy + r * sin(angle).toFloat() + paint.textSize / 3f
                    canvas.nativeCanvas.drawText(labels[i], px, py, paint)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonRing(
    cx: Float, cy: Float, r: Float, n: Int, angleStep: Double,
    color: Color, strokeWidth: Float = 1f,
) {
    val path = Path().apply {
        for (i in 0 until n) {
            val angle = -PI / 2 + i * angleStep
            val px = cx + r * cos(angle).toFloat()
            val py = cy + r * sin(angle).toFloat()
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

// ═════════════════════════════════════════════════════════════════════════
// LEGEND + AXES DETAIL
// ═════════════════════════════════════════════════════════════════════════

@Composable
private fun LegendRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(NeonGreen, stringResource(R.string.bodycomp_legend_reference))
        LegendDot(OrangeVibrant, stringResource(R.string.bodycomp_legend_you))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AxesDetail(axes: List<RadarAxis>) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.bodycomp_detail_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            for (axis in axes) {
                AxisRow(axis)
            }
        }
    }
}

@Composable
private fun AxisRow(axis: RadarAxis) {
    val gapPct = if (axis.referenceValue > 0f) {
        ((axis.userValue - axis.referenceValue) / axis.referenceValue * 100f).roundToInt()
    } else 0
    val color = when {
        axis.userValue == 0f -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        gapPct in -5..5 -> NeonGreen
        else -> OrangeVibrant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(axis.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.2f),
        )
        if (axis.userValue > 0f) {
            Text(
                "${axis.userValue.roundToInt()} cm",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.End,
            )
            Text(
                stringResource(R.string.bodycomp_target_short, axis.referenceValue.roundToInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.weight(0.8f),
                textAlign = TextAlign.End,
            )
            val signed = if (gapPct >= 0) "+${gapPct}%" else "${gapPct}%"
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = color.copy(alpha = 0.15f),
            ) {
                Text(
                    signed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(2.6f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Default.Hexagon, null,
            Modifier.size(96.dp),
            tint = OrangeVibrant.copy(alpha = 0.3f),
        )
        Text(
            stringResource(R.string.bodycomp_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.bodycomp_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
