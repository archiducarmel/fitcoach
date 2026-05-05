package com.shredcoach.app.domain.coach

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construit les prompts LLM pour le coach proactif.
 *
 * Architecture :
 * - **System prompt** : règles générales + ton sélectionné par l'utilisateur
 *   (gentle / direct / drill). 3 voix différentes radicales — pas seulement
 *   un adjectif changé, des règles de formulation distinctes.
 * - **Few-shot examples** : 2 exemples bons + 2 mauvais dans le system prompt.
 *   Empiriquement, les LLMs petits (Groq Llama 3.x, Gemini Flash) progressent
 *   beaucoup avec des exemples explicites de "ce qu'on veut" vs "ce qu'on rejette".
 * - **User prompt** : trigger context + multi-channel user context + intention
 *   spécifique au type de trigger.
 *
 * **Pourquoi le contexte multi-channel** : un message qui référence le dernier
 * exo pratiqué, le dernier scan repas, ou un mot du chat précédent est x10 plus
 * percutant qu'un message générique. C'est le différentiel FAANG vs MVP.
 */
@Singleton
class CoachPromptBuilder @Inject constructor() {

    /**
     * System prompt construit dynamiquement selon le ton choisi.
     * **Ton GENTLE** : reformulation douce, focus encouragement, pas de "fais ça".
     * **Ton DIRECT** : constat → action, neutre, factuel.
     * **Ton DRILL** : énergique, vocabulaire sport pro max, exigeant mais bienveillant.
     */
    fun buildSystemPrompt(tone: CoachSettingsStore.Tone): String {
        val toneRules = when (tone) {
            CoachSettingsStore.Tone.GENTLE -> """
TON : DOUX & BIENVEILLANT
- Pas d'impératif sec ("fais", "force-toi"). Préférer suggestion ("et si on...", "tu pourrais").
- Reformule les écarts en opportunités ("c'est ok, on rebondit demain").
- Termine par UNE proposition concrète, jamais une obligation.
            """.trimIndent()
            CoachSettingsStore.Tone.DIRECT -> """
TON : DIRECT & FACTUEL
- Constat → action, deux temps, ton neutre.
- Pas de fioriture ni d'enjolivement. Pas d'émojis.
- Termine par UNE action claire, présent de l'indicatif.
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
TON : COACH PRO MAX, ÉNERGIQUE
- Vocabulaire sport élevé : "explose", "claque", "verrouille la séance", "sec et propre".
- Direct mais JAMAIS méprisant. Exigeant comme un pote sportif passionné.
- Termine par UN call-to-action percutant, optionnel "let's go" ou "on lâche rien" en fin.
            """.trimIndent()
        }

        // Mélanger les exemples de tons différents perturbe le LLM (Llama 3.x,
        // Gemini Flash) : il produit une voix hybride peu lisible. On n'injecte
        // donc que la paire BON/MAUVAIS du ton sélectionné.
        val examples = when (tone) {
            CoachSettingsStore.Tone.DIRECT -> """
✓ BON (DIRECT, après PR sur Squat à 100kg, prev 95kg) :
"Squat 100kg validé hier, +5kg sur ton perso. Prochain palier : 102.5kg en 4 sets de 5."

✗ MAUVAIS (DIRECT — trop d'emoji, hyperbole) :
"INCROYABLE ! 🔥💪🎉 Tu as PULVÉRISÉ ton record !! Continue comme ça champion 🚀"
            """.trimIndent()
            CoachSettingsStore.Tone.GENTLE -> """
✓ BON (GENTLE, après séance ratée hier, comeback) :
"Pas de séance hier, c'est ok. Pour repartir doux : 30 min full body suffisent à recréer le rythme."

✗ MAUVAIS (GENTLE — culpabilisation) :
"Tu n'as pas fait ta séance hier... C'est important de tenir tes engagements. Tu dois t'y remettre."
            """.trimIndent()
            CoachSettingsStore.Tone.DRILL -> """
✓ BON (DRILL, deficit protéines en sèche) :
"105g de prot hier sur 180 visés. Tu laisses du muscle sur la table. Shaker dès maintenant, on lâche rien."

✗ MAUVAIS (DRILL — méprisant) :
"Sérieux ?! 105g de prot ?! T'as jamais lu un livre de sport ou quoi ? Réveille-toi."
            """.trimIndent()
        }

        return """
Tu es Shreddy, coach sportif et nutrition IA de l'app ShredCoach. Tu rédiges UN message
de coaching proactif à destination de l'utilisateur dans une notification push.

RÈGLES ABSOLUES :
- Français uniquement, tutoiement direct, prénom si fourni.
- MAX 2 phrases, 180 caractères maximum (contrainte notification système).
- Pas d'emoji dans le texte (sauf 1 max en fin si pertinent).
- Pas de salutations ("Hey", "Salut") car c'est une notification autonome.
- Commence par UN constat factuel issu du contexte fourni, termine par UNE action concrète.
- N'invente AUCUNE donnée : utilise uniquement les infos fournies. Pas d'hyperbole.
- Référence les détails personnels disponibles (exo récent, dernier repas scanné, blessure)
  pour rendre la notif évidemment "pour cet utilisateur précis".

$toneRules

EXEMPLES (étudie-les, le ton du tien doit être proche du BON exemple) :

$examples
""".trimIndent()
    }

    /**
     * Construit le user prompt à partir d'un trigger + contexte utilisateur.
     * Le contexte est PARTIELLEMENT injecté : on choisit les éléments les plus
     * pertinents pour ce trigger (économie de tokens + focus du LLM).
     */
    fun buildUserPrompt(trigger: CoachTrigger, ctx: CoachUserContext): String {
        val nameLine = "Prénom : ${ctx.firstName.ifBlank { "(pas connu)" }}"
        val baseProfile = """
$nameLine
Sexe : ${ctx.sex} | Âge : ${ctx.ageYears} | Niveau : ${ctx.level} | Objectif : ${ctx.goal}
Poids : ${"%.1f".format(ctx.currentWeightKg)}kg → cible ${"%.1f".format(ctx.targetWeightKg)}kg
${if (ctx.healthNotes.isNotBlank()) "Blessures/limitations : ${ctx.healthNotes}" else ""}
""".trim()

        val activitySnippet = buildString {
            append("Activité semaine : ${ctx.workoutsThisWeek}/${ctx.targetWorkoutsPerWeek} séances, volume ${ctx.weeklyVolumeKg}kg")
            if (ctx.topExerciseNames.isNotEmpty()) {
                append("\nExos pratiqués récemment : ${ctx.topExerciseNames.joinToString(", ")}")
            }
        }

        // Snippets sélectifs selon le trigger
        val extraContext = buildString {
            when (trigger) {
                is CoachTrigger.PersonalRecordCelebration ->
                    if (ctx.topExerciseNames.isNotEmpty())
                        append("Top exos historiques : ${ctx.topExerciseNames.joinToString(", ")}\n")
                is CoachTrigger.ProteinDeficit -> {
                    ctx.lastMealScanDish?.let { append("Dernier repas scanné : $it\n") }
                }
                is CoachTrigger.BodyScanStale -> {
                    if (ctx.bodyFatPercent > 0) append("BF% mesuré : ${"%.1f".format(ctx.bodyFatPercent)}%\n")
                }
                is CoachTrigger.GoalProximityETA -> {
                    // Le contexte est déjà chiffré dans trigger.context — pas besoin d'ajouter
                }
                is CoachTrigger.Comeback, is CoachTrigger.StreakAtRisk, is CoachTrigger.MissedScheduledWorkout,
                is CoachTrigger.PlateauVolume -> {
                    if (ctx.recentChatSnippets.isNotEmpty()) {
                        append("Derniers messages utilisateur dans le chat (continuité) :\n")
                        ctx.recentChatSnippets.take(2).forEach { append("- \"$it\"\n") }
                    }
                }
                is CoachTrigger.WeeklyRecap, is CoachTrigger.GeneralMotivation -> {
                    // Pas d'enrichissement spécifique
                }
            }
        }.trim()

        val intent = when (trigger) {
            is CoachTrigger.StreakAtRisk ->
                "Le streak est en danger. Encourage à reprendre une séance dès aujourd'hui sans alourdir la pression. Mentionne précisément la longueur du streak."
            is CoachTrigger.MissedScheduledWorkout ->
                "Séance ratée. Propose de la reporter aujourd'hui ou de la recaler dans la semaine. ZÉRO culpabilisation."
            is CoachTrigger.PersonalRecordCelebration ->
                "Célèbre ce nouveau record. Court, percutant, sans hyperbole. Mentionne le delta chiffré et suggère le prochain palier réaliste."
            is CoachTrigger.ProteinDeficit ->
                "Rappel sec sur l'enjeu protéine en sèche pour préserver le muscle. Suggère UNE source simple (shaker, oeufs, poulet, fromage blanc) en cohérence avec ce que l'utilisateur consomme déjà s'il y a une donnée dispo."
            is CoachTrigger.PlateauVolume ->
                "Diagnostic plateau. Suggère UNE action de sortie : intensité (charges +), nouveau pattern d'exo, ou deload léger. Pas de jargon scientifique."
            is CoachTrigger.Comeback ->
                "Pas de séance depuis longtemps. ZÉRO culpabilisation. Propose une session COURTE (30 min) ou LIGHT pour recréer l'élan. Pas de promesse ambitieuse."
            is CoachTrigger.BodyScanStale ->
                "La dernière mesure date. Invite à scanner le corps pour recalibrer le suivi. 30 secondes suffisent, pas une corvée."
            is CoachTrigger.GoalProximityETA ->
                "Donne une lecture chiffrée et motivante de la trajectoire vers l'objectif. Aucune projection irréaliste. Mentionne l'ETA en semaines."
            is CoachTrigger.WeeklyRecap ->
                "Récap dimanche soir. Bilan factuel de la semaine + cap sur la semaine suivante. Ton réflexif, pas dans l'urgence."
            is CoachTrigger.GeneralMotivation ->
                "Check-in amical, neutre. Pas de constat négatif. Question simple ou objectif léger pour la journée."
        }

        return """
Profil :
$baseProfile

$activitySnippet
${if (extraContext.isNotBlank()) "\nContexte additionnel :\n$extraContext\n" else ""}

Déclencheur (catégorie : ${trigger.category}) :
${trigger.context}

Tâche :
$intent

Rédige UNE notification courte (max 2 phrases, max 180 caractères).
""".trimIndent()
    }
}
