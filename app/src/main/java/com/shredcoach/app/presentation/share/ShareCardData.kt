package com.shredcoach.app.presentation.share

/**
 * Données affichables sur une share card. Sealed class — chaque variante
 * correspond à un contexte d'usage et embarque exactement les données dont
 * la card a besoin pour son rendu.
 *
 * **Pourquoi sealed class et pas une grosse data class** : chaque écran
 * source partage des choses différentes. Une `WorkoutPlanned` n'a pas de
 * volume soulevé (séance pas encore faite) ; une `WorkoutFinished` n'a pas
 * de liste d'exos détaillés (on a déjà tous les setsEffectués). Un seul type
 * fourre-tout obligerait des `null` partout et de la logique conditionnelle
 * dans la card. Sealed class = chaque rendu est explicite et type-safe.
 *
 * **Format final** : la share card est rendue en 1080×1920 portrait (ratio
 * Instagram Story / TikTok). Toutes les variantes doivent tenir dans ce
 * format. Évitez d'embarquer des listes de >12 items (l'image deviendrait
 * trop dense pour être lisible sur mobile).
 */
sealed class ShareCardData {

    /** Title affiché en grand en haut de la card. */
    abstract val title: String

    /** Sous-titre optionnel sous le title (date, période, etc.). */
    abstract val subtitle: String?

    /** Caption texte joint à l'image dans le partage Intent (Instagram, WhatsApp…). */
    abstract fun caption(): String

    // ── 1. Vue d'ensemble séance (avant démarrage) ──
    data class WorkoutPlanned(
        override val title: String = "Ma séance du jour",
        override val subtitle: String? = null,
        val durationMinutes: Int,
        val exerciseCount: Int,
        val warmupCount: Int,
        val cardioCount: Int,
        val muscleGroups: List<String>,
        val firstFewExercises: List<String>,
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            append("💪 ")
            append(title)
            append(" · ")
            append(durationMinutes)
            append(" min")
            if (muscleGroups.isNotEmpty()) {
                append(" · ")
                append(muscleGroups.take(3).joinToString(", "))
            }
            append("\n")
            append(exerciseCount)
            append(" exercices")
            if (warmupCount > 0) append(" · $warmupCount warmups")
            if (cardioCount > 0) append(" · $cardioCount cardio")
            append("\n#ShredCoach")
        }
    }

    /**
     * Statut d'un exercice dans la liste affichée par la share card.
     * Permet de distinguer visuellement :
     *  - DONE : ✓ vert, exo complété
     *  - CURRENT : ▶ orange, exo en cours
     *  - UPCOMING : · gris, à venir (uniquement WorkoutInProgress)
     *  - SKIPPED : ✗ atténué, exo passé
     */
    enum class ExerciseStatus { DONE, CURRENT, UPCOMING, SKIPPED }

    data class ExerciseProgressItem(
        val name: String,
        val status: ExerciseStatus,
        /** Sous-titre optionnel ("4×10 · 80 kg" pour exos done, null sinon). */
        val metric: String? = null,
    )

    // ── 2. Vue exos en cours de séance ──
    data class WorkoutInProgress(
        override val title: String = "Séance en cours",
        override val subtitle: String? = null,
        val elapsedMinutes: Int,
        val exercisesDone: Int,
        val totalExercises: Int,
        val totalSetsCompleted: Int,
        val totalReps: Int,
        val totalVolumeKg: Double,
        /**
         * Liste complète des exercices planifiés avec leur statut courant.
         * Affichée sur la card pour donner du contexte au lecteur (lui montre
         * la trajectoire de la séance, pas juste un chiffre brut). Limitée à
         * ~8 visibles côté rendu pour rester lisible.
         */
        val plannedExercises: List<ExerciseProgressItem> = emptyList(),
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            append("🔥 Je suis en pleine séance !\n")
            append(elapsedMinutes).append(" min · ")
            append(exercisesDone).append("/").append(totalExercises).append(" exos · ")
            append(totalSetsCompleted).append(" séries\n")
            if (totalVolumeKg > 0) {
                append("Volume : ").append(totalVolumeKg.toInt()).append(" kg")
            }
            append("\n#ShredCoach #fit")
        }
    }

    // ── 3. Fin d'exercice ──
    data class ExerciseCompleted(
        override val title: String,         // = nom de l'exercice
        override val subtitle: String? = null,
        val setsCompleted: Int,
        val totalReps: Int,
        val volumeKg: Double,
        val durationSeconds: Long,
        val isPersonalRecord: Boolean,
        val coachMessage: String? = null,
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            if (isPersonalRecord) append("🏆 NOUVEAU RECORD ! ") else append("✅ ")
            append(title).append("\n")
            append(setsCompleted).append(" séries · ")
            append(totalReps).append(" reps")
            if (volumeKg > 0) append(" · ").append(volumeKg.toInt()).append(" kg")
            append("\n#ShredCoach")
        }
    }

    // ── 4. Fin de séance ──
    data class WorkoutFinished(
        override val title: String = "Séance terminée",
        override val subtitle: String? = null,
        val durationSeconds: Long,
        val totalVolumeKg: Double,
        val totalSets: Int,
        val totalReps: Int,
        val exerciseCount: Int,
        val coachMessage: String? = null,
        /**
         * Liste des exos joués pendant la séance terminée (DONE/SKIPPED).
         * Donne au lecteur la composition exacte du workout, pas juste un
         * compteur — partage plus contextuel et impactant.
         */
        val completedExercises: List<ExerciseProgressItem> = emptyList(),
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            append("🎯 Séance terminée !\n")
            val durMin = durationSeconds / 60
            append(durMin).append(" min · ")
            append(exerciseCount).append(" exos · ")
            append(totalSets).append(" séries · ")
            append(totalReps).append(" reps\n")
            if (totalVolumeKg > 0) {
                append("Volume total : ").append(totalVolumeKg.toInt()).append(" kg")
            }
            append("\n#ShredCoach #workout")
        }
    }

    // ── 5. Stats agrégées (workout / nutrition) ──
    data class StatsAggregate(
        override val title: String,         // ex. "Mes stats Nutrition"
        override val subtitle: String?,     // ex. "30 derniers jours"
        val keyMetrics: List<KeyMetric>,    // 4-6 métriques max pour rester lisible
        val accentEmoji: String = "📊",
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            append(accentEmoji).append(" ").append(title)
            if (subtitle != null) append(" — ").append(subtitle)
            append("\n")
            keyMetrics.take(4).forEach { m ->
                append("• ").append(m.label).append(" : ").append(m.value)
                if (m.unit != null) append(" ").append(m.unit)
                append("\n")
            }
            append("#ShredCoach")
        }

        data class KeyMetric(
            val label: String,
            val value: String,
            val unit: String? = null,
        )
    }

    // ── 6. Historique (séance ou repas) condensé ──
    data class HistorySummary(
        override val title: String,         // ex. "Mon historique séances"
        override val subtitle: String?,     // ex. "Mai 2026"
        val totalCount: Int,                // nb de séances ou repas
        val countLabel: String,             // "séances" / "repas scannés"
        val keyMetrics: List<StatsAggregate.KeyMetric>,
        val accentEmoji: String = "📅",
    ) : ShareCardData() {
        override fun caption(): String = buildString {
            append(accentEmoji).append(" ").append(title)
            if (subtitle != null) append(" — ").append(subtitle)
            append("\n").append(totalCount).append(" ").append(countLabel).append("\n")
            keyMetrics.take(3).forEach { m ->
                append("• ").append(m.label).append(" : ").append(m.value)
                if (m.unit != null) append(" ").append(m.unit)
                append("\n")
            }
            append("#ShredCoach")
        }
    }
}
