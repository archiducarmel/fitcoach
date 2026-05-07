package com.shredcoach.app.domain.workout

import com.shredcoach.app.domain.model.MuscleGroup

/**
 * Définit un **type de séance** (Full Body, Push, Pull, Legs, Upper, Lower, …).
 *
 * **Pourquoi `data class` + registry static ([RoutineCatalog]) plutôt qu'un
 * `sealed class enum`** :
 *  - Extensible : ajouter une routine = 1 entrée dans le catalogue, pas
 *    de modif du code de génération.
 *  - Persistance par `id: String` → migrations DB triviales (TEXT NOT NULL).
 *  - Custom routines (Phase 4 premium) gérées via la même structure.
 *  - A/B testing : on peut gate des routines via feature flag sans recompiler.
 *
 * **Champs** :
 * - [id] : identifiant stable (ne change JAMAIS, persisté). Convention snake_case.
 * - [primaryGroups] : groupes musculaires "core" du routine, **ordonnés par
 *   priorité de volume** (le 1er reçoit potentiellement plus de séries via
 *   [volumeDistribution]).
 * - [accessoryGroups] : groupes ajoutés selon la durée (paliers 90/120/180min).
 * - [warmupFocus] : oriente la sélection des exos d'échauffement.
 * - [abdoPolicy] : controle l'ajout systématique d'abdos.
 * - [cardioPolicy] : controle l'ajout d'un cardio en fin de séance.
 * - [recommendedFrequencyPerWeek] : repère pour le coach IA et la home screen.
 * - [complementaryRoutineId] : routine complémentaire suggérée pour équilibrer
 *   la semaine (ex: Push → "pull").
 * - [volumeDistribution] : si non vide, multiplier de séries par groupe.
 *   Ex: Push → CHEST = 2 (2 exos pec) si la durée le permet, TRICEPS = 1.
 */
data class WorkoutRoutine(
    val id: String,
    val displayName: String,
    val tagline: String,
    val icon: String,
    val primaryGroups: List<MuscleGroup>,
    val accessoryGroups: List<MuscleGroup> = emptyList(),
    val warmupFocus: WarmupFocus = WarmupFocus.GENERAL,
    val abdoPolicy: AbdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
    val cardioPolicy: CardioPolicy = CardioPolicy.OPTIONAL,
    val recommendedFrequencyPerWeek: Int = 2,
    val complementaryRoutineId: String? = null,
    val volumeDistribution: Map<MuscleGroup, Int> = emptyMap(),
    val isBuiltIn: Boolean = true,
) {
    /** Tous les groupes ciblés (primary + accessory), sans duplicats. */
    val allTargetGroups: List<MuscleGroup>
        get() = (primaryGroups + accessoryGroups).distinct()

    /**
     * Indique si ce routine cible le groupe musculaire donné. Utilisé par le
     * freestyle exo picker pour filtrer les exos pertinents.
     */
    fun targets(group: MuscleGroup): Boolean = group in allTargetGroups
}

/** Stratégie d'échauffement à appliquer à une routine. */
enum class WarmupFocus {
    /** Mobilité full-body classique : hanches, épaules, colonne. */
    GENERAL,

    /** Échauffement haut du corps : épaules, thoracique, pectoraux légers. */
    UPPER,

    /** Échauffement bas du corps : hanches, ischios, quadriceps légers. */
    LOWER,

    /**
     * Échauffement strictement ciblé sur les `primaryGroups` du routine —
     * priorité maximum aux mots-clés correspondants dans le sélecteur de warmup.
     */
    TARGETED,
}

/** Politique d'inclusion d'exos abdos dans le routine. */
enum class AbdoPolicy {
    /** Toujours ajouter un exo abdos (peu importe la durée). */
    ALWAYS,

    /** Ajouter un exo abdos uniquement si la durée >= 90 min. */
    LONG_SESSIONS_ONLY,

    /** Ne jamais ajouter d'abdos automatiquement (le user les ajoute en freestyle s'il veut). */
    NEVER,
}

/** Politique d'inclusion d'un exercice cardio dans le routine. */
enum class CardioPolicy {
    /** Toujours inclure un exo cardio en fin de séance. */
    ALWAYS,

    /** Inclure un cardio si la durée >= 60 min (logique Full Body actuelle). */
    OPTIONAL,

    /** Ne jamais inclure de cardio (Push/Pull pure muscu, etc.). */
    SKIP,
}
