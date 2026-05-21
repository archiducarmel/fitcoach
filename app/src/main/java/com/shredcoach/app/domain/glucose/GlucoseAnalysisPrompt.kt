package com.shredcoach.app.domain.glucose

import com.shredcoach.app.domain.i18n.PromptLocale
import java.time.format.DateTimeFormatter

/**
 * Builder du prompt LLM pour l'analyse experte glycémique quotidienne.
 *
 * Architecture du prompt :
 *  1. **Rôle** : Dr. Glykos, endocrinologue-diététicien 20+ ans expérience CGM
 *  2. **Données structurées** injectées (pas de raw points, que des events
 *     déjà labellés algorithmiquement par [GlucoseCurvePreprocessor])
 *  3. **Method** : 9 catégories d'insights à chercher, avec exemples
 *  4. **Ton** : factuel, jamais alarmiste, physiologiquement ancré
 *  5. **Output** : JSON strict avec verdict global + insights + global advice
 *
 * **Pourquoi des examples in-prompt** : le LLM reproduit le style/profondeur
 * des exemples. Sans eux, on obtient des analyses banales ("ta glycémie était
 * un peu élevée à midi"). Avec, on obtient le niveau attendu ("le pic à 165
 * mg/dL à 13h25 suit ton plat de pâtes — magnitude attendue pour ~60g de
 * glucides à IG élevé, retour à 95 mg/dL en 110min indique une bonne
 * sensibilité à l'insuline").
 *
 * **Locale-aware** : le LLM lit le prompt en français (template) mais produit
 * les `title` / `explanation` / `globalAdvice` dans la langue de l'user via
 * `PromptLocale.outputLanguageDirective()`.
 */
object GlucoseAnalysisPrompt {

    private val DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.FRENCH)
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Construit le prompt complet à envoyer au LLM. Renvoie un String prêt à
     * être passé à `GeminiMealService.callTextLLM`.
     */
    fun build(context: PreprocessedContext, userFirstName: String?, athleteGoal: String? = null): String {
        val sb = StringBuilder()

        // ─── Locale directive (output language override) ─────────────────────
        sb.append(PromptLocale.outputLanguageDirective())
        sb.append("\n\n")

        // ─── Rôle + Méthodologie ─────────────────────────────────────────────
        sb.append(ROLE_BLOCK)

        // ─── Contexte utilisateur (anonymisé si pas de prénom) ───────────────
        sb.append("\n## CONTEXTE UTILISATEUR\n")
        userFirstName?.let { sb.append("- Prénom : $it\n") }
        athleteGoal?.let { sb.append("- Objectif : $it\n") }
        sb.append("- Cible glycémique athlète : 70-140 mg/dL (vs 70-180 standard)\n")
        sb.append("- Date analysée : ").append(context.date.format(DATE_FMT)).append("\n")

        // ─── Métriques globales ──────────────────────────────────────────────
        val m = context.metrics
        sb.append("\n## MÉTRIQUES GLOBALES JOURNALIÈRES\n")
        sb.append("- Moyenne : ").append(m.avg.toInt()).append(" mg/dL\n")
        sb.append("- Pic max : ").append(m.peak.toInt()).append(" mg/dL\n")
        sb.append("- Minimum : ").append(m.min.toInt()).append(" mg/dL\n")
        sb.append("- Écart-type : ").append("%.1f".format(m.stdDev)).append(" mg/dL\n")
        sb.append("- CV (variabilité) : ").append("%.1f".format(m.cv)).append("%")
            .append(if (m.cv < 36) " (stable)" else " (variable)").append("\n")
        sb.append("- TIR 70-180 : ").append(m.tir).append("%")
            .append(if (m.tir >= 70) " ✓" else " (sous-cible)").append("\n")
        sb.append("- TAR (>180) : ").append(m.tar).append("%\n")
        sb.append("- TBR (<70) : ").append(m.tbr).append("%\n")

        // ─── Repas logués ────────────────────────────────────────────────────
        if (context.mealsContext.isNotEmpty()) {
            sb.append("\n## REPAS LOGUÉS (").append(context.mealsContext.size).append(")\n")
            context.mealsContext.forEach { meal ->
                sb.append("- ").append(meal.time.format(TIME_FMT)).append(" — ")
                    .append(meal.name).append(" (")
                    .append(meal.calories.toInt()).append(" kcal, ")
                    .append("%.0f".format(meal.carbsGrams)).append("g glucides, ")
                    .append("%.0f".format(meal.proteinsGrams)).append("g prot, ")
                    .append("%.0f".format(meal.fatsGrams)).append("g lipides)\n")
            }
        } else {
            sb.append("\n## REPAS LOGUÉS\nAucun repas logué (jeûne ou tracking incomplet)\n")
        }

        // ─── Réponses postprandiales (le cœur de l'analyse) ──────────────────
        if (context.postprandialResponses.isNotEmpty()) {
            sb.append("\n## RÉPONSES POSTPRANDIALES DÉTECTÉES\n")
            context.postprandialResponses.forEach { resp ->
                sb.append("- Repas : ").append(resp.mealName).append(" à ").append(resp.mealTime.format(TIME_FMT)).append("\n")
                sb.append("  - Baseline pré-repas : ").append(resp.baselineMgdl.toInt()).append(" mg/dL\n")
                sb.append("  - Pic post-repas : ").append(resp.peakMgdl.toInt()).append(" mg/dL ")
                    .append("(+").append(resp.peakDeltaMgdl.toInt()).append(") atteint ")
                    .append(resp.peakDelayMin).append(" min après le repas\n")
                resp.recoveryMin?.let {
                    sb.append("  - Retour baseline en ").append(it).append(" min\n")
                } ?: sb.append("  - Pas de retour baseline complet dans la fenêtre 3h\n")
                sb.append("  - Magnitude : ").append(resp.magnitude.name).append("\n")
            }
        }

        // ─── Phénomène de l'aube ─────────────────────────────────────────────
        context.dawnPhenomenon?.let { dawn ->
            sb.append("\n## PHÉNOMÈNE DE L'AUBE DÉTECTÉ\n")
            sb.append("- Montée monotone de ").append(dawn.startMgdl.toInt()).append(" → ")
                .append(dawn.endMgdl.toInt()).append(" mg/dL ")
                .append("(+").append(dawn.riseMgdl.toInt()).append(") entre ")
                .append(dawn.startTime.format(TIME_FMT)).append(" et ")
                .append(dawn.endTime.format(TIME_FMT))
                .append(" sans repas dans la fenêtre.\n")
        }

        // ─── Jeûne nocturne ──────────────────────────────────────────────────
        context.nightFastingStats?.let { night ->
            sb.append("\n## JEÛNE NOCTURNE (23h-6h)\n")
            sb.append("- Moyenne : ").append(night.avgMgdl.toInt()).append(" mg/dL\n")
            sb.append("- Min/Max : ").append(night.minMgdl.toInt()).append("/").append(night.maxMgdl.toInt()).append(" mg/dL\n")
            sb.append("- Stabilité (CV nuit) : ").append("%.1f".format(night.stabilityCv)).append("%\n")
        }

        // ─── Hypos ───────────────────────────────────────────────────────────
        if (context.hypoEvents.isNotEmpty()) {
            sb.append("\n## ÉPISODES HYPOGLYCÉMIQUES (<70 mg/dL)\n")
            context.hypoEvents.forEach { hypo ->
                sb.append("- ").append(hypo.startTime.format(TIME_FMT)).append(" → ")
                    .append(hypo.endTime.format(TIME_FMT)).append(" — nadir ")
                    .append(hypo.nadirMgdl.toInt()).append(" mg/dL (")
                    .append(hypo.severity.name).append(")\n")
            }
        }

        // ─── Montées sans repas ─────────────────────────────────────────────
        if (context.unexplainedRises.isNotEmpty()) {
            sb.append("\n## MONTÉES SANS REPAS LOGUÉ\n")
            context.unexplainedRises.forEach { rise ->
                sb.append("- Pic ").append(rise.peakMgdl.toInt()).append(" mg/dL à ")
                    .append(rise.time.format(TIME_FMT))
                    .append(" (rise +").append(rise.rise.toInt()).append(")\n")
            }
        }

        // ─── Pics + vallées résiduels ─────────────────────────────────────────
        if (context.peaks.isNotEmpty()) {
            sb.append("\n## PICS LOCAUX (top 5)\n")
            context.peaks.forEach { p ->
                sb.append("- ").append(p.time.format(TIME_FMT))
                    .append(" : ").append(p.mgdl.toInt()).append(" mg/dL\n")
            }
        }

        // ─── Méthode + Output schema ─────────────────────────────────────────
        sb.append(METHOD_BLOCK)
        sb.append(OUTPUT_SCHEMA_BLOCK)
        sb.append(FEW_SHOT_EXAMPLES)

        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BLOCS STATIQUES DU PROMPT
    // ═══════════════════════════════════════════════════════════════════════

    private const val ROLE_BLOCK = """# RÔLE
Tu es Dr. Glykos, endocrinologue-diététicien avec plus de 20 ans d'expérience
dans l'analyse de capteurs de glycémie en continu (CGM) pour des athlètes en
optimisation métabolique et des patients en pré-diabète. Tu produis chaque
jour pour ton patient une analyse experte, factuelle et bienveillante de sa
courbe glycémique 24h.

# OBJECTIF
Identifier TOUS les évènements remarquables de la journée, les expliquer
physiologiquement (insuline, cortisol, glucagon, GLP-1, adrénaline) et les
relier aux repas / activités quand pertinent. Ton ton est celui d'un
professionnel de santé chevronné qui explique sans jargon technique inutile,
qui félicite les bonnes réponses, qui rassure quand c'est bénin, qui alerte
SEULEMENT quand c'est cliniquement pertinent.

"""

    private const val METHOD_BLOCK = """

# MÉTHODE — CATÉGORIES D'INSIGHTS À RECHERCHER

Pour chaque insight, tu choisis UNE catégorie parmi :

1. **POSTPRANDIAL_PEAK** : pic glycémique 30-90min après un repas. Évalue la
   magnitude vs la composition du repas (glucides + IG). Félicite si le pic
   est contenu (<40 mg/dL de rise), neutre si modéré, signal si excessif.

2. **RECOVERY** : qualité du retour à baseline post-repas. Excellent <90min,
   bon <120min, lent >150min. Explique en termes de sensibilité insulinique.

3. **DAWN** : phénomène de l'aube (montée 4h-8h sans repas, cortisol matinal).
   À mentionner UNIQUEMENT si présent dans les données — c'est éducatif.

4. **CORTISOL_RISE** : pic isolé sans repas attribuable, généralement matinal
   ou en cas de stress. Explique le mécanisme cortisol/adrénaline.

5. **STABLE_FASTING** : période de stabilité remarquable (plateau). Félicite
   si elle confirme une bonne flexibilité métabolique.

6. **NIGHT_FASTING** : qualité de la glycémie nocturne (23h-6h). Une nuit
   stable autour de 80-100 mg/dL est un excellent signal.

7. **HYPO** : épisode <70 mg/dL. Toujours mentionner. Si <54 = alerte
   importante avec recommandation de vérifier la sensation hypoglycémique.

8. **SPIKE** : pic excessif (>180 mg/dL) attribuable ou non. Explique
   physiologiquement.

9. **EXERCISE_RESPONSE** : creux ou pic lié à une probable activité
   physique (chute rapide -30 mg/dL en 30min, ou rebond hépatique).

# RÈGLES DE PRIORISATION

- Maximum 6 insights par analyse — sois sélectif, garde les plus pédagogiques.
- Ordonne par importance clinique (hypos > pics > patterns > stabilité).
- Chaque postprandial avec magnitude >= MODERATE doit avoir un insight.
- Le phénomène de l'aube : 1 insight si présent dans les données.

# TON ET PHRASÉ

- Factuel, jamais alarmiste. "On observe..." plutôt que "Attention !"
- Physiologiquement ancré. Mentionne le mécanisme (insuline, glucagon, etc.).
- Pédagogique. Si l'user a un super pattern, explique POURQUOI c'est bien.
- Pas de prescription médicale. Pas de "tu devrais prendre..."
- Conseil pratique nutritionnel OK : "pairer les glucides avec des fibres",
  "rapprocher les repas du soir", etc.

"""

    private const val OUTPUT_SCHEMA_BLOCK = """

# OUTPUT — JSON strict, rien d'autre

```json
{
  "verdict": "EXCELLENT | GOOD | FAIR | CONCERN",
  "summary": "Phrase 1-2 lignes résumant la journée pour notif push.",
  "globalAdvice": "1-2 phrases actionnables pour demain. Vide si rien à dire.",
  "insights": [
    {
      "time": "HH:mm",
      "category": "POSTPRANDIAL_PEAK | RECOVERY | DAWN | CORTISOL_RISE | STABLE_FASTING | NIGHT_FASTING | HYPO | SPIKE | EXERCISE_RESPONSE",
      "title": "Titre court 5-8 mots",
      "explanation": "Explication factuelle 2-4 phrases avec mécanisme physiologique.",
      "verdict": "POSITIVE | NEUTRAL | CONCERN",
      "relatedMealName": "Nom du repas si applicable, sinon null"
    }
  ]
}
```

Détermine le `verdict` global selon ces critères (calculables) :
- EXCELLENT : TIR >= 85, peak < 160, 0 hypo, CV < 30
- GOOD : TIR >= 70, peak < 180, 0-1 hypo, CV < 36
- FAIR : TIR >= 50 ou peak < 220, 1-2 hypos
- CONCERN : TIR < 50, peak >= 220, ou >= 3 hypos, ou pattern HYPO_RISK

Tu peux sur-classer ou sous-classer d'1 niveau si les patterns détectés le
justifient cliniquement.

# EXEMPLES DE BONS INSIGHTS (ne pas copier-coller, inspirer)

"""

    private const val FEW_SHOT_EXAMPLES = """
Exemple 1 (POSTPRANDIAL_PEAK avec contexte) :
{
  "time": "13:25",
  "category": "POSTPRANDIAL_PEAK",
  "title": "Pic post-déjeuner attendu mais contenu",
  "explanation": "Ton glucose monte à 165 mg/dL 55 min après ton plat de nouilles sautées (62g de glucides à index glycémique élevé). C'est une réponse physiologique normale — le pic à +70 mg/dL au-dessus de la baseline reste dans la fourchette d'un sujet sain non diabétique. La présence de protéines dans ton plat a probablement ralenti l'absorption.",
  "verdict": "NEUTRAL",
  "relatedMealName": "Nouilles sautées"
}

Exemple 2 (RECOVERY excellent — à féliciter) :
{
  "time": "14:35",
  "category": "RECOVERY",
  "title": "Réponse métabolique excellente",
  "explanation": "Ta glycémie redescend à 82 mg/dL exactement 1h35 après ton déjeuner. Une récupération sous 2h après un repas riche en glucides est le marqueur d'une excellente sensibilité à l'insuline — typique d'un athlète bien entraîné dont les muscles captent efficacement le glucose. Continue dans cette voie.",
  "verdict": "POSITIVE",
  "relatedMealName": "Bowl quinoa-poulet"
}

Exemple 3 (DAWN) :
{
  "time": "06:45",
  "category": "DAWN",
  "title": "Phénomène de l'aube classique",
  "explanation": "On observe une montée progressive de 88 à 112 mg/dL entre 4h30 et 7h, sans aucun repas dans cette fenêtre. C'est le phénomène de l'aube : la sécrétion matinale de cortisol et de glucagon prépare ton corps au réveil en libérant du glucose hépatique. Phénomène totalement normal, présent chez 50% des adultes.",
  "verdict": "NEUTRAL",
  "relatedMealName": null
}

Exemple 4 (CORTISOL_RISE sans repas) :
{
  "time": "08:15",
  "category": "CORTISOL_RISE",
  "title": "Petit pic au réveil, sans repas",
  "explanation": "Un pic de 28 mg/dL apparaît à 8h15 sans repas logué. Le plus probable : décharge cortisol-adrénaline du réveil, qui mobilise les réserves hépatiques (néoglucogénèse). Bénin si transitoire. Garde l'œil si ça se répète tous les matins — la routine du réveil compte (un café noir à jeun peut amplifier).",
  "verdict": "NEUTRAL",
  "relatedMealName": null
}

Réponds UNIQUEMENT en JSON valide selon le schéma. Pas de texte avant/après.
"""
}
