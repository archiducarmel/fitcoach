package com.shredcoach.app.domain.coach

import java.util.Locale
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
 * **Multi-locale (Phase 4 i18n)** : prompts disponibles en FR et EN. La locale
 * courante est lue via [Locale.getDefault] (overlay AppCompatDelegate) à chaque
 * appel — le LLM répond donc dans la langue de l'app, avec les few-shots
 * traduits pour éviter le biais (sinon Llama-3 produit un mix FR/EN).
 *
 * **Pourquoi le contexte multi-channel** : un message qui référence le dernier
 * exo pratiqué, le dernier scan repas, ou un mot du chat précédent est x10 plus
 * percutant qu'un message générique. C'est le différentiel FAANG vs MVP.
 */
@Singleton
class CoachPromptBuilder @Inject constructor() {

    /**
     * System prompt construit dynamiquement selon le ton choisi + locale courante.
     */
    fun buildSystemPrompt(tone: CoachSettingsStore.Tone): String {
        val l = currentLocaleLang()
        val toneRules = toneRulesFor(l, tone)
        val examples = examplesFor(l, tone)
        return systemPromptShellFor(l, toneRules, examples)
    }

    /**
     * Construit le user prompt à partir d'un trigger + contexte utilisateur.
     * Le contexte est PARTIELLEMENT injecté : on choisit les éléments les plus
     * pertinents pour ce trigger (économie de tokens + focus du LLM).
     */
    fun buildUserPrompt(trigger: CoachTrigger, ctx: CoachUserContext): String {
        val l = currentLocaleLang()
        val nameLabel = label(l, "Prénom", "First name")
        val sexLabel = label(l, "Sexe", "Sex")
        val ageLabel = label(l, "Âge", "Age")
        val levelLabel = label(l, "Niveau", "Level")
        val goalLabel = label(l, "Objectif", "Goal")
        val weightLabel = label(l, "Poids", "Weight")
        val targetLabel = label(l, "cible", "target")
        val healthLabel = label(l, "Blessures/limitations", "Injuries/limitations")
        val unknown = label(l, "(pas connu)", "(unknown)")

        val nameLine = "$nameLabel : ${ctx.firstName.ifBlank { unknown }}"
        val baseProfile = """
$nameLine
$sexLabel : ${ctx.sex} | $ageLabel : ${ctx.ageYears} | $levelLabel : ${ctx.level} | $goalLabel : ${ctx.goal}
$weightLabel : ${"%.1f".format(ctx.currentWeightKg)}kg → $targetLabel ${"%.1f".format(ctx.targetWeightKg)}kg
${if (ctx.healthNotes.isNotBlank()) "$healthLabel : ${ctx.healthNotes}" else ""}
""".trim()

        val activityLabel = label(l, "Activité semaine", "Week activity")
        val sessionsLabel = label(l, "séances", "sessions")
        val volumeLabel = label(l, "volume", "volume")
        val recentExosLabel = label(l, "Exos pratiqués récemment", "Recent exercises")
        val activitySnippet = buildString {
            append("$activityLabel : ${ctx.workoutsThisWeek}/${ctx.targetWorkoutsPerWeek} $sessionsLabel, $volumeLabel ${ctx.weeklyVolumeKg}kg")
            if (ctx.topExerciseNames.isNotEmpty()) {
                append("\n$recentExosLabel : ${ctx.topExerciseNames.joinToString(", ")}")
            }
        }

        // Snippets sélectifs selon le trigger
        val topExosHistLabel = label(l, "Top exos historiques", "Top historical exercises")
        val lastMealLabel = label(l, "Dernier repas scanné", "Last scanned meal")
        val bfLabel = label(l, "BF% mesuré", "Measured BF%")
        val recentChatLabel = label(l, "Derniers messages utilisateur dans le chat (continuité)",
            "Recent user chat messages (continuity)")
        val extraContext = buildString {
            when (trigger) {
                is CoachTrigger.PersonalRecordCelebration ->
                    if (ctx.topExerciseNames.isNotEmpty())
                        append("$topExosHistLabel : ${ctx.topExerciseNames.joinToString(", ")}\n")
                is CoachTrigger.ProteinDeficit -> {
                    ctx.lastMealScanDish?.let { append("$lastMealLabel : $it\n") }
                }
                is CoachTrigger.BodyScanStale -> {
                    if (ctx.bodyFatPercent > 0) append("$bfLabel : ${"%.1f".format(ctx.bodyFatPercent)}%\n")
                }
                is CoachTrigger.GoalProximityETA -> {
                    // Le contexte est déjà chiffré dans trigger.context — pas besoin d'ajouter
                }
                is CoachTrigger.Comeback, is CoachTrigger.StreakAtRisk, is CoachTrigger.MissedScheduledWorkout,
                is CoachTrigger.PlateauVolume -> {
                    if (ctx.recentChatSnippets.isNotEmpty()) {
                        append("$recentChatLabel :\n")
                        ctx.recentChatSnippets.take(2).forEach { append("- \"$it\"\n") }
                    }
                }
                is CoachTrigger.WeeklyRecap, is CoachTrigger.GeneralMotivation -> {
                    // Pas d'enrichissement spécifique
                }
            }
        }.trim()

        val intent = intentFor(l, trigger)

        val profileLabel = label(l, "Profil", "Profile")
        val extraLabel = label(l, "Contexte additionnel", "Additional context")
        val triggerLabel = label(l, "Déclencheur (catégorie", "Trigger (category")
        val taskLabel = label(l, "Tâche", "Task")
        val finalTask = label(
            l,
            "Rédige UNE notification courte (max 2 phrases, max 180 caractères).",
            "Write ONE short notification (max 2 sentences, max 180 characters)."
        )

        return """
$profileLabel :
$baseProfile

$activitySnippet
${if (extraContext.isNotBlank()) "\n$extraLabel :\n$extraContext\n" else ""}

$triggerLabel : ${trigger.category}) :
${trigger.context}

$taskLabel :
$intent

$finalTask
""".trimIndent()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Locale dispatch helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun currentLocaleLang(): String = Locale.getDefault().language.lowercase()

    private fun label(lang: String, fr: String, en: String): String =
        if (lang == "en") en else fr

    private fun toneRulesFor(lang: String, tone: CoachSettingsStore.Tone): String {
        return if (lang == "en") {
            when (tone) {
                CoachSettingsStore.Tone.GENTLE -> """
TONE: SOFT & SUPPORTIVE
- No blunt imperative ("do this", "force yourself"). Prefer suggestion ("what if we...", "you could").
- Reframe gaps as opportunities ("it's ok, we bounce back tomorrow").
- End with ONE concrete suggestion, never an obligation.
                """.trimIndent()
                CoachSettingsStore.Tone.DIRECT -> """
TONE: DIRECT & FACTUAL
- Observation → action, two beats, neutral tone.
- No fluff or embellishment. No emojis.
- End with ONE clear action, present tense.
                """.trimIndent()
                CoachSettingsStore.Tone.DRILL -> """
TONE: PRO COACH MAX, ENERGETIC
- High-level sport vocabulary: "explode", "smash", "lock the session", "clean and tight".
- Direct but NEVER demeaning. Demanding like a passionate sport friend.
- End with ONE punchy call-to-action, optional "let's go" or "don't quit".
                """.trimIndent()
            }
        } else {
            when (tone) {
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
        }
    }

    private fun examplesFor(lang: String, tone: CoachSettingsStore.Tone): String {
        return if (lang == "en") {
            when (tone) {
                CoachSettingsStore.Tone.DIRECT -> """
✓ GOOD (DIRECT, after PR on Squat at 100kg, prev 95kg):
"Squat 100kg locked yesterday, +5kg on your PR. Next milestone: 102.5kg in 4 sets of 5."

✗ BAD (DIRECT — too many emojis, hyperbole):
"INCREDIBLE! 🔥💪🎉 You SHATTERED your record!! Keep it up champion 🚀"
                """.trimIndent()
                CoachSettingsStore.Tone.GENTLE -> """
✓ GOOD (GENTLE, missed session yesterday, comeback):
"No session yesterday, that's ok. To restart soft: 30 min full body is enough to rebuild rhythm."

✗ BAD (GENTLE — guilt-tripping):
"You didn't do your session yesterday... It's important to keep your commitments. You must get back to it."
                """.trimIndent()
                CoachSettingsStore.Tone.DRILL -> """
✓ GOOD (DRILL, protein deficit while shredding):
"105g of protein yesterday vs 180 target. You're leaving muscle on the table. Shaker now, don't quit."

✗ BAD (DRILL — demeaning):
"Seriously?! 105g of protein?! Have you ever read a fitness book? Wake up."
                """.trimIndent()
            }
        } else {
            when (tone) {
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
        }
    }

    private fun systemPromptShellFor(lang: String, toneRules: String, examples: String): String {
        return if (lang == "en") """
You are Shreddy, the AI sport and nutrition coach of the ShredCoach app. You write ONE
proactive coaching message for the user as a push notification.

ABSOLUTE RULES:
- English only, direct address (you), use the first name if provided.
- MAX 2 sentences, 180 characters max (system notification constraint).
- No emoji in the text (max 1 at the very end if relevant).
- No greetings ("Hey", "Hi") since this is a standalone notification.
- Start with ONE factual observation from the provided context, end with ONE concrete action.
- INVENT no data: only use the provided info. No hyperbole.
- Reference personal details available (recent exercise, last scanned meal, injury) to make
  the notification obviously "for this user specifically".

$toneRules

EXAMPLES (study them, your tone must be close to the GOOD example):

$examples
""".trimIndent() else """
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

    private fun intentFor(lang: String, trigger: CoachTrigger): String {
        return if (lang == "en") when (trigger) {
            is CoachTrigger.StreakAtRisk ->
                "The streak is at risk. Encourage to start a session today without piling on pressure. Mention the streak length precisely."
            is CoachTrigger.MissedScheduledWorkout ->
                "Missed session. Suggest moving it to today or rescheduling within the week. ZERO guilt-tripping."
            is CoachTrigger.PersonalRecordCelebration ->
                "Celebrate this new record. Short, punchy, no hyperbole. Mention the numerical delta and suggest the next realistic milestone."
            is CoachTrigger.ProteinDeficit ->
                "Sharp reminder about protein for shredding (preserves muscle). Suggest ONE simple source (shaker, eggs, chicken, cottage cheese) consistent with what the user already eats if data available."
            is CoachTrigger.PlateauVolume ->
                "Plateau diagnosis. Suggest ONE escape action: intensity (heavier loads), new exercise pattern, or light deload. No scientific jargon."
            is CoachTrigger.Comeback ->
                "No session for a long time. ZERO guilt-tripping. Suggest a SHORT (30 min) or LIGHT session to rebuild momentum. No ambitious promise."
            is CoachTrigger.BodyScanStale ->
                "Last measurement is old. Invite to scan the body to recalibrate tracking. 30 seconds is enough, not a chore."
            is CoachTrigger.GoalProximityETA ->
                "Give a numerical, motivating reading of the goal trajectory. No unrealistic projection. Mention the ETA in weeks."
            is CoachTrigger.WeeklyRecap ->
                "Sunday evening recap. Factual week summary + cap on next week. Reflective tone, not urgent."
            is CoachTrigger.GeneralMotivation ->
                "Friendly, neutral check-in. No negative observation. Simple question or light goal for the day."
        } else when (trigger) {
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
    }
}
