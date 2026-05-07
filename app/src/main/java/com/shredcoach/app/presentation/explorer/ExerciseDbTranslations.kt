package com.shredcoach.app.presentation.explorer

import com.shredcoach.app.domain.i18n.PromptLocale

/**
 * Traductions des libellés du dataset free-exercise-db.
 *
 * IMPORTANT : on ne modifie JAMAIS la valeur stockée (qui sert au filtrage avec l'API).
 * On ne traduit que pour l'AFFICHAGE via les fonctions `display*()`.
 *
 * Locale dispatch :
 *  - FR : applique les dictionnaires FR ci-dessous
 *  - EN : retourne la valeur source en Title Case (le dataset est nativement EN)
 *  - Toute autre locale (V2 future) : retombe sur EN par défaut
 */
object ExerciseDbTranslations {

    private fun displayLocaleAware(value: String, frMap: Map<String, String>): String {
        val key = value.lowercase()
        return when {
            !PromptLocale.isEn() -> frMap[key] ?: value.replaceFirstChar { it.uppercase() }
            else -> value.replaceFirstChar { it.uppercase() }
        }
    }

    // ── 17 muscles ──
    private val muscles = mapOf(
        "abdominals" to "Abdominaux",
        "abductors" to "Abducteurs",
        "adductors" to "Adducteurs",
        "biceps" to "Biceps",
        "calves" to "Mollets",
        "chest" to "Pectoraux",
        "forearms" to "Avant-bras",
        "glutes" to "Fessiers",
        "hamstrings" to "Ischios",
        "lats" to "Grand dorsal",
        "lower back" to "Lombaires",
        "middle back" to "Dos (milieu)",
        "neck" to "Cou",
        "quadriceps" to "Quadriceps",
        "shoulders" to "Épaules",
        "traps" to "Trapèzes",
        "triceps" to "Triceps"
    )

    // ── 12 équipements ──
    private val equipments = mapOf(
        "bands" to "Élastiques",
        "barbell" to "Barre",
        "body only" to "Poids du corps",
        "cable" to "Poulie",
        "dumbbell" to "Haltères",
        "e-z curl bar" to "Barre EZ",
        "exercise ball" to "Swiss ball",
        "foam roll" to "Rouleau",
        "kettlebells" to "Kettlebells",
        "machine" to "Machine",
        "medicine ball" to "Medicine ball",
        "other" to "Autre"
    )

    // ── 7 catégories ──
    private val categories = mapOf(
        "strength" to "Musculation",
        "cardio" to "Cardio",
        "olympic weightlifting" to "Haltérophilie",
        "plyometrics" to "Pliométrie",
        "powerlifting" to "Powerlifting",
        "stretching" to "Étirement",
        "strongman" to "Strongman"
    )

    // ── 3 niveaux ──
    private val levels = mapOf(
        "beginner" to "Débutant",
        "intermediate" to "Intermédiaire",
        "expert" to "Expert"
    )

    // ── 3 forces ──
    private val forces = mapOf(
        "pull" to "Tirer",
        "push" to "Pousser",
        "static" to "Statique"
    )

    // ── 2 mécaniques ──
    private val mechanics = mapOf(
        "compound" to "Polyarticulaire",
        "isolation" to "Isolation"
    )

    fun displayMuscle(value: String): String = displayLocaleAware(value, muscles)
    fun displayEquipment(value: String): String = displayLocaleAware(value, equipments)
    fun displayCategory(value: String): String = displayLocaleAware(value, categories)
    fun displayLevel(value: String): String = displayLocaleAware(value, levels)
    fun displayForce(value: String): String = displayLocaleAware(value, forces)
    fun displayMechanic(value: String): String = displayLocaleAware(value, mechanics)

    // ═════════════════════════════════════════════════════════════════
    // Traduction des NOMS d'exercices — dictionnaire à 2 phases
    // Phase 1 : expressions multi-mots (priorité aux plus longues, greedy)
    // Phase 2 : mots uniques pour les tokens restants
    // ═════════════════════════════════════════════════════════════════

    /** Expressions composées — matchées EN PREMIER (triées par longueur descendante pour greedy match). */
    private val exercisePhrases = mapOf(
        // Presses
        "close grip bench press" to "Développé Couché Prise Serrée",
        "incline bench press" to "Développé Incliné",
        "decline bench press" to "Développé Décliné",
        "flat bench press" to "Développé Couché",
        "bench press" to "Développé Couché",
        "shoulder press" to "Développé Épaules",
        "military press" to "Développé Militaire",
        "overhead press" to "Développé Overhead",
        "arnold press" to "Développé Arnold",
        "floor press" to "Développé au Sol",
        "push press" to "Push Press",
        // Rows
        "bent over row" to "Rowing Buste Penché",
        "bent-over row" to "Rowing Buste Penché",
        "upright row" to "Rowing Menton",
        "t-bar row" to "Rowing T-Bar",
        "t bar row" to "Rowing T-Bar",
        "one arm row" to "Rowing Un Bras",
        "one-arm row" to "Rowing Un Bras",
        "single arm row" to "Rowing Un Bras",
        "seated row" to "Tirage Horizontal Assis",
        "cable row" to "Tirage Câble",
        "inverted row" to "Rowing Inversé",
        // Pulldowns / Pull-ups
        "lat pulldown" to "Tirage Vertical",
        "wide grip pulldown" to "Tirage Vertical Prise Large",
        "close grip pulldown" to "Tirage Vertical Prise Serrée",
        "pull up" to "Traction",
        "pull-up" to "Traction",
        "pull ups" to "Tractions",
        "pull-ups" to "Tractions",
        "chin up" to "Traction Supination",
        "chin-up" to "Traction Supination",
        "chin ups" to "Tractions Supination",
        "chin-ups" to "Tractions Supination",
        "muscle up" to "Muscle Up",
        "muscle-up" to "Muscle Up",
        // Squats
        "front squat" to "Front Squat",
        "back squat" to "Squat Arrière",
        "hack squat" to "Hack Squat",
        "goblet squat" to "Goblet Squat",
        "split squat" to "Split Squat",
        "bulgarian split squat" to "Fente Bulgare",
        "box squat" to "Squat Box",
        "pistol squat" to "Pistol Squat",
        "jump squat" to "Squat Sauté",
        "overhead squat" to "Squat Overhead",
        "zercher squat" to "Zercher Squat",
        "sumo squat" to "Sumo Squat",
        "wall sit" to "Chaise Statique",
        // Deadlifts
        "sumo deadlift" to "Soulevé de Terre Sumo",
        "romanian deadlift" to "Soulevé de Terre Roumain",
        "stiff leg deadlift" to "Soulevé de Terre Jambes Tendues",
        "stiff-leg deadlift" to "Soulevé de Terre Jambes Tendues",
        "straight leg deadlift" to "Soulevé de Terre Jambes Tendues",
        "conventional deadlift" to "Soulevé de Terre Conventionnel",
        "single leg deadlift" to "Soulevé de Terre Unilatéral",
        "single-leg deadlift" to "Soulevé de Terre Unilatéral",
        "rack pull" to "Rack Pull",
        "deadlift" to "Soulevé de Terre",
        // Jambes
        "leg press" to "Presse à Cuisses",
        "leg extension" to "Leg Extension",
        "leg curl" to "Leg Curl",
        "leg raise" to "Relevé de Jambes",
        "leg raises" to "Relevés de Jambes",
        "hanging leg raise" to "Relevé de Jambes Suspendu",
        "hanging knee raise" to "Relevé de Genoux Suspendu",
        "step up" to "Montée sur Banc",
        "step-up" to "Montée sur Banc",
        "step ups" to "Montées sur Banc",
        "calf raise" to "Mollets Debout",
        "calf raises" to "Mollets Debout",
        "seated calf raise" to "Mollets Assis",
        "standing calf raise" to "Mollets Debout",
        "donkey calf raise" to "Mollets Âne",
        "walking lunge" to "Fentes Marchées",
        "reverse lunge" to "Fente Arrière",
        "forward lunge" to "Fente Avant",
        "side lunge" to "Fente Latérale",
        "jumping lunge" to "Fentes Sautées",
        "curtsy lunge" to "Fente Croisée",
        // Fessiers
        "hip thrust" to "Hip Thrust",
        "glute bridge" to "Pont Fessier",
        "hip abduction" to "Abduction Hanche",
        "hip adduction" to "Adduction Hanche",
        "donkey kick" to "Donkey Kick",
        "donkey kicks" to "Donkey Kicks",
        "fire hydrant" to "Fire Hydrant",
        "good morning" to "Good Morning",
        "good mornings" to "Good Morning",
        // Épaules
        "lateral raise" to "Élévations Latérales",
        "side raise" to "Élévations Latérales",
        "front raise" to "Élévations Frontales",
        "rear delt raise" to "Oiseau",
        "rear delt fly" to "Oiseau",
        "bent over fly" to "Oiseau Buste Penché",
        "bent-over fly" to "Oiseau Buste Penché",
        "face pull" to "Face Pull",
        "face pulls" to "Face Pulls",
        "shrug" to "Shrug",
        "shrugs" to "Shrugs",
        // Triceps
        "tricep extension" to "Extension Triceps",
        "triceps extension" to "Extension Triceps",
        "overhead extension" to "Extension Overhead",
        "overhead triceps extension" to "Extension Overhead",
        "skull crusher" to "Barre au Front",
        "skull crushers" to "Barre au Front",
        "skullcrusher" to "Barre au Front",
        "french press" to "Extension Française",
        "tricep pushdown" to "Poulie Triceps",
        "triceps pushdown" to "Poulie Triceps",
        "tricep kickback" to "Kickback Triceps",
        "triceps kickback" to "Kickback Triceps",
        "bench dip" to "Dips Banc",
        "bench dips" to "Dips Banc",
        "tricep dip" to "Dips Triceps",
        "triceps dip" to "Dips Triceps",
        "diamond push" to "Pompes Diamant",
        "diamond push up" to "Pompes Diamant",
        "diamond push-up" to "Pompes Diamant",
        // Biceps
        "bicep curl" to "Curl Biceps",
        "biceps curl" to "Curl Biceps",
        "hammer curl" to "Curl Marteau",
        "hammer curls" to "Curls Marteau",
        "concentration curl" to "Curl Concentré",
        "preacher curl" to "Curl Pupitre",
        "spider curl" to "Curl Spider",
        "zottman curl" to "Curl Zottman",
        "reverse curl" to "Curl Pronation",
        "drag curl" to "Drag Curl",
        // Abdos
        "sit up" to "Redressement",
        "sit-up" to "Redressement",
        "sit ups" to "Redressements",
        "sit-ups" to "Redressements",
        "crunch" to "Crunch",
        "crunches" to "Crunchs",
        "bicycle crunch" to "Crunch Vélo",
        "reverse crunch" to "Crunch Inversé",
        "russian twist" to "Russian Twist",
        "ab wheel" to "Roue Abdominale",
        "side plank" to "Planche Latérale",
        "plank" to "Planche",
        "planks" to "Planches",
        "flutter kick" to "Battements",
        "flutter kicks" to "Battements",
        "v up" to "V-Up",
        "v-up" to "V-Up",
        "hollow hold" to "Hollow Hold",
        "l sit" to "L-Sit",
        "l-sit" to "L-Sit",
        "dragon flag" to "Drapeau du Dragon",
        // Pompes / Push-ups
        "push up" to "Pompe",
        "push-up" to "Pompe",
        "push ups" to "Pompes",
        "push-ups" to "Pompes",
        "pushup" to "Pompe",
        "pushups" to "Pompes",
        "wide push up" to "Pompes Prise Large",
        "close push up" to "Pompes Prise Serrée",
        "incline push up" to "Pompes Inclinées",
        "decline push up" to "Pompes Déclinées",
        "clap push up" to "Pompes Claquées",
        // Cardio / Pliometric
        "mountain climber" to "Grimpeur",
        "mountain climbers" to "Grimpeurs",
        "jumping jack" to "Jumping Jack",
        "jumping jacks" to "Jumping Jacks",
        "high knees" to "Montées de Genoux",
        "butt kicks" to "Talons-Fesses",
        "burpee" to "Burpee",
        "burpees" to "Burpees",
        "jump rope" to "Corde à Sauter",
        "box jump" to "Box Jump",
        "box jumps" to "Box Jumps",
        "broad jump" to "Saut en Longueur",
        "tuck jump" to "Tuck Jump",
        "star jump" to "Star Jump",
        "bear crawl" to "Marche de l'Ours",
        "crab walk" to "Marche du Crabe",
        // Haltérophilie
        "power clean" to "Épaulé Force",
        "hang clean" to "Épaulé Suspendu",
        "clean and jerk" to "Épaulé-Jeté",
        "clean & jerk" to "Épaulé-Jeté",
        "clean and press" to "Épaulé-Développé",
        "power snatch" to "Arraché Force",
        "hang snatch" to "Arraché Suspendu",
        "snatch" to "Arraché",
        "muscle snatch" to "Arraché Musculaire",
        "jerk" to "Jeté",
        // Strongman
        "farmer's walk" to "Marche du Fermier",
        "farmers walk" to "Marche du Fermier",
        "farmer walk" to "Marche du Fermier",
        "atlas stone" to "Pierre d'Atlas",
        "tire flip" to "Tire Flip",
        "log press" to "Log Press",
        // Écartés
        "dumbbell fly" to "Écartés Haltères",
        "cable fly" to "Écartés Câble",
        "pec fly" to "Écartés Pectoraux",
        "pec deck" to "Butterfly",
        // Divers courts
        "wall ball" to "Wall Ball",
        "medicine ball slam" to "Medicine Ball Slam",
        "ball slam" to "Slam Ball",
        "kettlebell swing" to "Kettlebell Swing",
        "turkish get up" to "Turkish Get-Up",
        "turkish get-up" to "Turkish Get-Up",
        "renegade row" to "Renegade Row",
        "thruster" to "Thruster",
        "thrusters" to "Thrusters",
        "bridge" to "Pont",
        "bridges" to "Ponts",
        // Mots simples mais fréquents (prioritaire sur les listes de mots phase 2)
        "curl" to "Curl",
        "curls" to "Curls",
        "press" to "Développé",
        "row" to "Rowing",
        "rows" to "Rowing",
        "raise" to "Élévation",
        "raises" to "Élévations",
        "extension" to "Extension",
        "extensions" to "Extensions",
        "fly" to "Écarté",
        "flye" to "Écarté",
        "flies" to "Écartés",
        "flyes" to "Écartés",
        "twist" to "Rotation",
        "twists" to "Rotations",
        "kickback" to "Kickback",
        "kickbacks" to "Kickbacks",
        "swing" to "Swing",
        "clean" to "Épaulé",
        "pulldown" to "Tirage Vertical",
        "pushdown" to "Poulie",
        "pushup" to "Pompe",
        "pushups" to "Pompes",
        "dip" to "Dips",
        "dips" to "Dips",
        "squat" to "Squat",
        "squats" to "Squats",
        "lunge" to "Fente",
        "lunges" to "Fentes"
    )

    /** Mots uniques — phase 2 (appliquée après les phrases). */
    private val exerciseWords = mapOf(
        // Équipement
        "barbell" to "Barre",
        "dumbbell" to "Haltère",
        "dumbbells" to "Haltères",
        "cable" to "Câble",
        "cables" to "Câbles",
        "machine" to "Machine",
        "smith" to "Smith",
        "kettlebell" to "Kettlebell",
        "kettlebells" to "Kettlebells",
        "band" to "Élastique",
        "bands" to "Élastiques",
        "resistance" to "Résistance",
        "ball" to "Ballon",
        "bar" to "Barre",
        "plate" to "Disque",
        "weighted" to "Lesté",
        "bodyweight" to "Poids du Corps",
        "ez" to "EZ",
        "e-z" to "EZ",
        "trap" to "Trap",
        "hex" to "Hex",
        // Parties du corps
        "chest" to "Pectoraux",
        "bench" to "Banc",
        "back" to "Dos",
        "shoulder" to "Épaule",
        "shoulders" to "Épaules",
        "delt" to "Deltoïde",
        "delts" to "Deltoïdes",
        "leg" to "Jambe",
        "legs" to "Jambes",
        "arm" to "Bras",
        "arms" to "Bras",
        "bicep" to "Biceps",
        "biceps" to "Biceps",
        "tricep" to "Triceps",
        "triceps" to "Triceps",
        "forearm" to "Avant-bras",
        "forearms" to "Avant-bras",
        "neck" to "Cou",
        "wrist" to "Poignet",
        "hip" to "Hanche",
        "hips" to "Hanches",
        "glute" to "Fessier",
        "glutes" to "Fessiers",
        "quad" to "Quadriceps",
        "quads" to "Quadriceps",
        "hamstring" to "Ischio",
        "hamstrings" to "Ischios",
        "calf" to "Mollet",
        "calves" to "Mollets",
        "abs" to "Abdos",
        "ab" to "Abdo",
        "abdominal" to "Abdominal",
        "abdominals" to "Abdominaux",
        "core" to "Gainage",
        "lat" to "Dorsal",
        "lats" to "Dorsaux",
        "pec" to "Pectoral",
        "pecs" to "Pectoraux",
        "pectoral" to "Pectoral",
        "pectorals" to "Pectoraux",
        "traps" to "Trapèzes",
        "trapezius" to "Trapèze",
        "spine" to "Colonne",
        "obliques" to "Obliques",
        "oblique" to "Oblique",
        // Positions / Modificateurs
        "incline" to "Incliné",
        "decline" to "Décliné",
        "flat" to "Plat",
        "seated" to "Assis",
        "standing" to "Debout",
        "lying" to "Allongé",
        "prone" to "Ventre",
        "supine" to "Dos Plat",
        "hanging" to "Suspendu",
        "kneeling" to "Agenouillé",
        "half" to "Demi",
        "full" to "Complet",
        "wide" to "Large",
        "narrow" to "Étroit",
        "close" to "Serré",
        "neutral" to "Neutre",
        "reverse" to "Inversé",
        "front" to "Avant",
        "rear" to "Arrière",
        "side" to "Côté",
        "lateral" to "Latéral",
        "overhead" to "Overhead",
        "underhand" to "Supination",
        "overhand" to "Pronation",
        "single" to "Un",
        "one" to "Un",
        "two" to "Deux",
        "alternating" to "Alterné",
        "alternate" to "Alterné",
        "heavy" to "Lourd",
        "light" to "Léger",
        "slow" to "Lent",
        "fast" to "Rapide",
        "explosive" to "Explosif",
        "dynamic" to "Dynamique",
        "static" to "Statique",
        "isometric" to "Isométrique",
        "assisted" to "Assisté",
        "negative" to "Négatif",
        "eccentric" to "Excentrique",
        "concentric" to "Concentrique",
        "paused" to "Pausé",
        "tempo" to "Tempo",
        "high" to "Haut",
        "low" to "Bas",
        "mid" to "Milieu",
        "upper" to "Supérieur",
        "lower" to "Inférieur",
        "middle" to "Milieu",
        "outer" to "Externe",
        "inner" to "Interne",
        // Petits mots de liaison
        "with" to "avec",
        "and" to "et",
        "to" to "",
        "on" to "sur",
        "in" to "en",
        "the" to "",
        "a" to "",
        "an" to "",
        "of" to "de"
    )

    /** Liaisons françaises qui doivent rester en minuscules après la phase 2 (sortie de la phase 1). */
    private val frenchLowercaseKeep = setOf(
        "sur", "de", "du", "à", "et", "en", "la", "le", "les",
        "un", "une", "au", "aux", "pour", "avec", "par", "ou",
        "d", "l", "s", "à"
    )

    /**
     * Traduit un nom d'exercice EN → FR via l'algorithme 2 phases.
     * Si aucune traduction n'est trouvée, retourne le nom original en Title Case.
     *
     * En locale EN, retourne le nom source en Title Case (pas de traduction).
     */
    fun translateExerciseName(name: String): String {
        if (name.isBlank()) return name
        if (PromptLocale.isEn()) {
            return name.split(Regex("\\s+"))
                .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
        }
        var result = name

        // Phase 1 : remplacer les expressions (triées par longueur descendante pour greedy match)
        val sortedPhrases = exercisePhrases.entries.sortedByDescending { it.key.length }
        for ((en, fr) in sortedPhrases) {
            // Word-boundary-aware replace pour éviter "curl" → "Curl" dans "curly"
            val pattern = Regex("(?<![a-zA-Z])${Regex.escape(en)}(?![a-zA-Z])", RegexOption.IGNORE_CASE)
            result = pattern.replace(result, fr)
        }

        // Phase 2 : tokenisation par WHITESPACE SEULEMENT (préserve "/", "-", etc.)
        val tokens = result.split(Regex("\\s+"))
        val translated = tokens.map { token ->
            val cleanLower = token.lowercase().trim('(', ')', ',', '.')
            exerciseWords[cleanLower]?.let { fr ->
                // Reconstruit avec caractères ponctuation autour si présents
                val prefix = token.takeWhile { !it.isLetter() }
                val suffix = token.takeLastWhile { !it.isLetter() }
                "$prefix$fr$suffix"
            } ?: run {
                // Règles de préservation de case :
                //  - Déjà en majuscule initiale → sortie de Phase 1 (ex: "Développé") → préserver
                //  - Contient des accents français → sortie de Phase 1 → préserver
                //  - Liaison FR minuscule connue (sur, de, à…) → préserver lowercase
                //  - Sinon : c'est un mot EN non reconnu → TitleCase
                val firstChar = token.firstOrNull()
                val hasFrenchAccent = token.any { it in "éèêëàâäçîïôöùûüÉÈÊÀÂÄÇÎÏÔÖÙÛÜ" }
                when {
                    firstChar?.isUpperCase() == true -> token
                    hasFrenchAccent -> token
                    cleanLower in frenchLowercaseKeep -> token
                    firstChar?.isLowerCase() == true -> token.replaceFirstChar { it.titlecase() }
                    else -> token // tokens non-alphabétiques (chiffres, etc.)
                }
            }
        }
        return translated.filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
