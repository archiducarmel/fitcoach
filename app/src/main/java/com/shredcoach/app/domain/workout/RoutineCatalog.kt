package com.shredcoach.app.domain.workout

import com.shredcoach.app.domain.model.MuscleGroup

/**
 * Catalogue des routines built-in proposées par l'app.
 *
 * 8 routines couvrant les programmations les plus pratiquées :
 *  - **Full Body** : programme historique de l'app, séance équilibrée.
 *  - **Push** / **Pull** / **Legs** : split PPL classique 3-jours/semaine.
 *  - **Upper Body** / **Lower Body** : split haut/bas 4 jours/semaine.
 *  - **Chest + Triceps** / **Back + Biceps** : bro split classique 4-5 jours.
 *
 * **Règles de design** :
 *  - L'`id` est stable. Persisté en DB. Ne JAMAIS le changer après ship.
 *  - Les `primaryGroups` sont ordonnés par priorité de volume (le 1er groupe
 *    sera le plus représenté dans la séance).
 *  - Les `accessoryGroups` sont ajoutés progressivement selon la durée
 *    (60min = 0 accessory, 90min = 1/3, 120min = 1/2, 180min = tous).
 *  - `complementaryRoutineId` permet au coach IA de suggérer la routine
 *    complémentaire (ex: l'utilisateur a fait Push lundi → propose Pull mercredi).
 *  - `volumeDistribution` indique combien d'exos par groupe (default 1).
 *    Sur Push, on veut 2 exos pec (CHEST + CHEST_UPPER), donc CHEST = 2 quand
 *    la durée permet d'avoir un 2e exo pec via accessoryGroups.
 *
 * **Pour ajouter une routine custom** (Phase 4 premium) : créer une instance
 * [WorkoutRoutine] avec `isBuiltIn = false` et la persister dans une table
 * dédiée `custom_routines` (à créer). Le résolveur [byId] interrogera d'abord
 * le catalogue built-in, puis fallback sur les custom.
 */
object RoutineCatalog {

    val FullBody = WorkoutRoutine(
        id = "full_body",
        displayName = "Full Body",
        tagline = "Tout le corps en une séance",
        icon = "🔥",
        primaryGroups = listOf(
            MuscleGroup.QUADS,
            MuscleGroup.CHEST,
            MuscleGroup.BACK_WIDTH,
            MuscleGroup.SHOULDERS,
            MuscleGroup.BICEPS,
            MuscleGroup.ABS_UPPER,
        ),
        // **Ordre = ordre d'ajout** quand la durée monte (90/120/180 min). Aligné
        // sur le comportement historique de Full Body pour préserver les habitudes :
        //  - 90 min : on ajoute HAMSTRINGS + ABS_LOWER (équilibre push/pull bas + abdo)
        //  - 120 min : + TRICEPS + CHEST_UPPER (volume bras + pec haut)
        //  - 180 min : + TRAPS + BACK_THICKNESS (cap target=12 → on s'arrête là)
        // Avant v37 le générateur pouvait pousser jusqu'à 16 exos en 180min ; le cap
        // à 12 reste plus réaliste (~10 min/exo après warmup+cardio).
        accessoryGroups = listOf(
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.ABS_LOWER,
            MuscleGroup.TRICEPS,
            MuscleGroup.CHEST_UPPER,
            MuscleGroup.TRAPS,
            MuscleGroup.BACK_THICKNESS,
            MuscleGroup.CALVES,
            MuscleGroup.ADDUCTORS,
            MuscleGroup.FOREARMS,
            MuscleGroup.LOWER_BACK,
        ),
        warmupFocus = WarmupFocus.GENERAL,
        abdoPolicy = AbdoPolicy.ALWAYS, // déjà dans primaryGroups
        cardioPolicy = CardioPolicy.OPTIONAL,
        recommendedFrequencyPerWeek = 3,
        complementaryRoutineId = null, // FB se suffit
    )

    val Push = WorkoutRoutine(
        id = "push",
        displayName = "Push",
        tagline = "Pec · Épaules · Triceps",
        icon = "💪",
        primaryGroups = listOf(
            MuscleGroup.CHEST,
            MuscleGroup.SHOULDERS,
            MuscleGroup.TRICEPS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.CHEST_UPPER,
            MuscleGroup.ABS_UPPER,
        ),
        warmupFocus = WarmupFocus.UPPER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.SKIP,
        recommendedFrequencyPerWeek = 2,
        complementaryRoutineId = "pull",
        volumeDistribution = mapOf(
            MuscleGroup.CHEST to 2,       // 2 exos pec dès que la durée le permet
        ),
    )

    val Pull = WorkoutRoutine(
        id = "pull",
        displayName = "Pull",
        tagline = "Dos · Biceps",
        icon = "🪝",
        primaryGroups = listOf(
            MuscleGroup.BACK_WIDTH,
            MuscleGroup.BACK_THICKNESS,
            MuscleGroup.BICEPS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.TRAPS,
            MuscleGroup.FOREARMS,
            MuscleGroup.LOWER_BACK,
            MuscleGroup.ABS_UPPER,
        ),
        warmupFocus = WarmupFocus.UPPER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.SKIP,
        recommendedFrequencyPerWeek = 2,
        complementaryRoutineId = "push",
        volumeDistribution = mapOf(
            MuscleGroup.BACK_WIDTH to 2,
        ),
    )

    val Legs = WorkoutRoutine(
        id = "legs",
        displayName = "Legs",
        tagline = "Quadri · Ischios · Fessiers",
        icon = "🦵",
        primaryGroups = listOf(
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.CALVES,
            MuscleGroup.ADDUCTORS,
            MuscleGroup.LOWER_BACK,
            MuscleGroup.ABS_LOWER,
        ),
        warmupFocus = WarmupFocus.LOWER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.OPTIONAL,
        recommendedFrequencyPerWeek = 2,
        complementaryRoutineId = null, // Legs se suffit
        volumeDistribution = mapOf(
            MuscleGroup.QUADS to 2,
            MuscleGroup.HAMSTRINGS to 2,
        ),
    )

    val UpperBody = WorkoutRoutine(
        id = "upper",
        displayName = "Upper Body",
        tagline = "Tout le haut du corps",
        icon = "👕",
        primaryGroups = listOf(
            MuscleGroup.CHEST,
            MuscleGroup.BACK_WIDTH,
            MuscleGroup.SHOULDERS,
            MuscleGroup.BICEPS,
            MuscleGroup.TRICEPS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.CHEST_UPPER,
            MuscleGroup.BACK_THICKNESS,
            MuscleGroup.TRAPS,
            MuscleGroup.ABS_UPPER,
            MuscleGroup.FOREARMS,
        ),
        warmupFocus = WarmupFocus.UPPER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.SKIP,
        recommendedFrequencyPerWeek = 2,
        complementaryRoutineId = "lower",
    )

    val LowerBody = WorkoutRoutine(
        id = "lower",
        displayName = "Lower Body",
        tagline = "Tout le bas du corps",
        icon = "🦿",
        primaryGroups = listOf(
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.CALVES,
        ),
        accessoryGroups = listOf(
            MuscleGroup.ADDUCTORS,
            MuscleGroup.LOWER_BACK,
            MuscleGroup.ABS_LOWER,
        ),
        warmupFocus = WarmupFocus.LOWER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.OPTIONAL,
        recommendedFrequencyPerWeek = 2,
        complementaryRoutineId = "upper",
        volumeDistribution = mapOf(
            MuscleGroup.QUADS to 2,
        ),
    )

    val ChestTriceps = WorkoutRoutine(
        id = "chest_tri",
        displayName = "Chest + Tri",
        tagline = "Pectoraux · Triceps",
        icon = "🎯",
        primaryGroups = listOf(
            MuscleGroup.CHEST,
            MuscleGroup.CHEST_UPPER,
            MuscleGroup.TRICEPS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.SHOULDERS,
            MuscleGroup.ABS_UPPER,
        ),
        warmupFocus = WarmupFocus.UPPER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.SKIP,
        recommendedFrequencyPerWeek = 1,
        complementaryRoutineId = "back_bi",
        volumeDistribution = mapOf(
            MuscleGroup.CHEST to 2,
        ),
    )

    val BackBiceps = WorkoutRoutine(
        id = "back_bi",
        displayName = "Back + Bi",
        tagline = "Dos · Biceps",
        icon = "🏹",
        primaryGroups = listOf(
            MuscleGroup.BACK_WIDTH,
            MuscleGroup.BACK_THICKNESS,
            MuscleGroup.BICEPS,
        ),
        accessoryGroups = listOf(
            MuscleGroup.TRAPS,
            MuscleGroup.FOREARMS,
            MuscleGroup.LOWER_BACK,
        ),
        warmupFocus = WarmupFocus.UPPER,
        abdoPolicy = AbdoPolicy.LONG_SESSIONS_ONLY,
        cardioPolicy = CardioPolicy.SKIP,
        recommendedFrequencyPerWeek = 1,
        complementaryRoutineId = "chest_tri",
        volumeDistribution = mapOf(
            MuscleGroup.BACK_WIDTH to 2,
        ),
    )

    /** Liste ordonnée de tous les routines built-in (ordre = ordre d'affichage UI). */
    val builtIn: List<WorkoutRoutine> = listOf(
        FullBody,
        Push,
        Pull,
        Legs,
        UpperBody,
        LowerBody,
        ChestTriceps,
        BackBiceps,
    )

    /** Routine par défaut quand rien n'est spécifié — préserve le comportement historique. */
    val Default: WorkoutRoutine = FullBody

    /**
     * Résout un routine par son `id`. Retourne [Default] si l'id est inconnu —
     * jamais d'exception, pour ne pas casser l'app sur une donnée corrompue
     * (ex: backup d'une version future avec un id custom non reconnu).
     */
    fun byId(id: String?): WorkoutRoutine {
        if (id.isNullOrBlank()) return Default
        return builtIn.firstOrNull { it.id == id } ?: Default
    }
}
