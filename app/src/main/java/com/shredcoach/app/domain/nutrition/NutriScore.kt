package com.shredcoach.app.domain.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

/**
 * Algorithme Nutri-Score 2023 (simplifié, aliments solides).
 *
 * Points négatifs (N) — pour 100 g :
 *   Énergie (kJ), Sucres (g), Acides gras saturés (g), Sodium (mg)
 *
 * Points positifs (P) :
 *   Fibres (g), Protéines (g), % fruits/légumes/noix (estimé via catégories)
 *
 * Score final = N - P
 *   A : ≤ -1  |  B : 0–2  |  C : 3–10  |  D : 11–18  |  E : ≥ 19
 */
object NutriScoreCalculator {

    data class NutriScoreResult(
        val grade: Char,        // 'A'..'E'
        val score: Int,         // points bruts (peut être négatif)
        val negativePoints: Int,
        val positivePoints: Int
    )

    /** Calcule le Nutri-Score à partir des valeurs nutritionnelles pour 100 g. */
    fun calculate(
        energyKcalPer100g: Double,
        sugarsPer100g: Double,
        saturatedFatPer100g: Double,
        sodiumMgPer100g: Double,
        fibersPer100g: Double,
        proteinsPer100g: Double
    ): NutriScoreResult {
        val energyKj = energyKcalPer100g * 4.184

        // ── Points négatifs ──
        val energyPts = when {
            energyKj > 3350 -> 10; energyKj > 3015 -> 9; energyKj > 2680 -> 8
            energyKj > 2345 -> 7; energyKj > 2010 -> 6; energyKj > 1675 -> 5
            energyKj > 1340 -> 4; energyKj > 1005 -> 3; energyKj > 670 -> 2
            energyKj > 335 -> 1; else -> 0
        }
        val sugarPts = when {
            sugarsPer100g > 45 -> 10; sugarsPer100g > 40 -> 9; sugarsPer100g > 36 -> 8
            sugarsPer100g > 31 -> 7; sugarsPer100g > 27 -> 6; sugarsPer100g > 22.5 -> 5
            sugarsPer100g > 18 -> 4; sugarsPer100g > 13.5 -> 3; sugarsPer100g > 9 -> 2
            sugarsPer100g > 4.5 -> 1; else -> 0
        }
        val satFatPts = when {
            saturatedFatPer100g > 10 -> 10; saturatedFatPer100g > 9 -> 9
            saturatedFatPer100g > 8 -> 8; saturatedFatPer100g > 7 -> 7
            saturatedFatPer100g > 6 -> 6; saturatedFatPer100g > 5 -> 5
            saturatedFatPer100g > 4 -> 4; saturatedFatPer100g > 3 -> 3
            saturatedFatPer100g > 2 -> 2; saturatedFatPer100g > 1 -> 1; else -> 0
        }
        val sodiumPts = when {
            sodiumMgPer100g > 900 -> 10; sodiumMgPer100g > 810 -> 9; sodiumMgPer100g > 720 -> 8
            sodiumMgPer100g > 630 -> 7; sodiumMgPer100g > 540 -> 6; sodiumMgPer100g > 450 -> 5
            sodiumMgPer100g > 360 -> 4; sodiumMgPer100g > 270 -> 3; sodiumMgPer100g > 180 -> 2
            sodiumMgPer100g > 90 -> 1; else -> 0
        }
        val negative = energyPts + sugarPts + satFatPts + sodiumPts

        // ── Points positifs ──
        val fiberPts = when {
            fibersPer100g > 4.7 -> 5; fibersPer100g > 3.7 -> 4; fibersPer100g > 2.8 -> 3
            fibersPer100g > 1.9 -> 2; fibersPer100g > 0.9 -> 1; else -> 0
        }
        val proteinPts = when {
            proteinsPer100g > 8.0 -> 5; proteinsPer100g > 6.4 -> 4; proteinsPer100g > 4.8 -> 3
            proteinsPer100g > 3.2 -> 2; proteinsPer100g > 1.6 -> 1; else -> 0
        }
        val positive = fiberPts + proteinPts

        val score = negative - positive
        val grade = when {
            score <= -1 -> 'A'
            score <= 2 -> 'B'
            score <= 10 -> 'C'
            score <= 18 -> 'D'
            else -> 'E'
        }
        return NutriScoreResult(grade, score, negative, positive)
    }

    /**
     * Calcule le Nutri-Score à partir des totaux d'un repas/plat.
     * Convertit automatiquement en valeurs pour 100g.
     */
    fun fromTotals(
        calories: Int,
        sugars: Double,
        saturatedFat: Double,
        saltG: Double,
        fibers: Double,
        proteins: Double,
        weightG: Int
    ): NutriScoreResult {
        if (weightG <= 0) return NutriScoreResult('C', 5, 5, 0) // fallback
        val factor = 100.0 / weightG
        return calculate(
            energyKcalPer100g = calories * factor,
            sugarsPer100g = sugars * factor,
            saturatedFatPer100g = saturatedFat * factor,
            sodiumMgPer100g = saltG * 1000.0 * factor / 2.5, // sel → sodium (÷2.5)
            fibersPer100g = fibers * factor,
            proteinsPer100g = proteins * factor
        )
    }
}

// ═══════════════════════════════════════
// COULEURS OFFICIELLES NUTRI-SCORE
// ═══════════════════════════════════════

private val NutriA = Color(0xFF038141)
private val NutriB = Color(0xFF85BB2F)
private val NutriC = Color(0xFFFECB02)
private val NutriD = Color(0xFFEE8100)
private val NutriE = Color(0xFFE63E11)

private fun gradeColor(grade: Char): Color = when (grade) {
    'A' -> NutriA; 'B' -> NutriB; 'C' -> NutriC; 'D' -> NutriD; 'E' -> NutriE; else -> Color.Gray
}

// ═══════════════════════════════════════
// CENTRAGE OPTIQUE DES LETTRES DANS UN BADGE
// ═══════════════════════════════════════
//
// Pourquoi ce style dédié : par défaut, un `Text` Compose hérite de la
// compatibilité Android legacy (`includeFontPadding = true`) qui ajoute
// du padding **asymétrique** autour du glyphe (top padding ≠ bottom
// padding selon les métriques de la font Roboto). Couplé à un line-height
// par défaut ~1.4 × fontSize, le glyphe d'une lettre majuscule (A–E n'ont
// pas de descendeur) se retrouve **décalé vers le bas** dans son line-box
// quand on tente de le centrer dans un Box étroit (badge Nutri-Score
// inactive de hauteur ≈ 0.72× la hauteur active).
//
// Symptôme côté user : "les lettres A/B/C/D/E sont coupées en bas".
//
// Fix premium :
//  1. `includeFontPadding = false` → enlève le padding legacy asymétrique.
//  2. `LineHeightStyle(Alignment.Center, Trim.Both)` → centre le glyphe
//     sur sa cap-height (la zone visuelle réelle), et coupe l'espace
//     ascender/descender inutile au-dessus/dessous.
//  3. `lineHeight = fontSize` → line-box serré, pas d'espace en trop.
private fun nutriBadgeLetterStyle(fontSize: TextUnit, weight: FontWeight): TextStyle =
    TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = weight,
        color = Color.White,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )

// ═══════════════════════════════════════
// PICTOGRAMME NUTRI-SCORE (barre A–E)
// ═══════════════════════════════════════

/**
 * Pictogramme officiel Nutri-Score : barre horizontale A-B-C-D-E
 * avec la lettre active agrandie et mise en avant.
 */
@Composable
fun NutriScorePictogram(
    grade: Char,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp
) {
    val grades = listOf('A', 'B', 'C', 'D', 'E')
    val activeIdx = grades.indexOf(grade).coerceIn(0, 4)
    val activeHeight = height
    val inactiveHeight = height * 0.72f

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2D2D2D))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        grades.forEachIndexed { idx, g ->
            val isActive = idx == activeIdx
            val h = if (isActive) activeHeight else inactiveHeight
            val fontSize = if (isActive) (height.value * 0.55f).sp else (height.value * 0.38f).sp
            val color = gradeColor(g)
            val alpha = if (isActive) 1f else 0.45f

            Box(
                modifier = Modifier
                    .height(h)
                    .width(if (isActive) height * 0.92f else height * 0.58f)
                    .clip(RoundedCornerShape(if (isActive) 4.dp else 2.dp))
                    .background(color.copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                // Style dédié (cf. nutriBadgeLetterStyle) : garantit un
                // centrage optique exact du glyphe, sans coupure en bas du
                // badge — point bloquant pour un rendu premium.
                Text(
                    "$g",
                    style = nutriBadgeLetterStyle(
                        fontSize = fontSize,
                        weight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Version compacte du Nutri-Score (juste la lettre dans un badge coloré).
 * Utilisée dans les listes serrées.
 */
@Composable
fun NutriScoreBadgeCompact(
    grade: Char,
    modifier: Modifier = Modifier
) {
    val color = gradeColor(grade)
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        // Même style dédié que le pictogramme, pour cohérence du centrage
        // optique des lettres dans tous les badges Nutri-Score de l'app.
        Text(
            "$grade",
            style = nutriBadgeLetterStyle(fontSize = 12.sp, weight = FontWeight.ExtraBold)
        )
    }
}
