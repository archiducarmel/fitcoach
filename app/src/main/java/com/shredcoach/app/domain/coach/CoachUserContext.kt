package com.shredcoach.app.domain.coach

/**
 * Contexte multi-canal de l'utilisateur, agrégé pour enrichir les prompts LLM.
 *
 * Critique pour le coaching FAANG-grade : un message générique "tu peux le faire"
 * vaut zéro vs un message qui référence ce que **cet utilisateur précis** a fait
 * récemment ("après ton développé couché de mardi, ton pec mérite une vraie
 * récup avant les pompes diamant").
 *
 * Sources :
 * - **Profile** : prénom, sexe, âge, niveau, objectif chiffré, blessures
 *   (healthNotes — adapte les suggestions, ex : pas de squat lourd si genou)
 * - **Chat history** : 3 derniers messages utilisateur dans Shreddy (signaux
 *   de ton, sujets, frustrations)
 * - **Body scan** : dernière mesure (waistCm, bodyFatPercent, bodyScanNotes
 *   IA — observations qualitatives genre "asymétrie épaule gauche")
 * - **Top exercises** : 3 exos les plus pratiqués (références concrètes
 *   plutôt que génériques "tes exos préférés")
 * - **Last meal scan** : nom du dernier plat scanné (continuité)
 * - **This week stats** : nb séances faites/prévues, volume cumulé
 *
 * Design : tous les champs sont nullable ou ont un défaut "vide" — un user
 * tout neuf qui n'a rien encore aura un context partiellement vide, et le
 * prompt s'adaptera (cf. [CoachPromptBuilder.buildSystemPrompt]).
 */
data class CoachUserContext(
    // Profile
    val firstName: String,
    val ageYears: Int,
    val sex: String,             // "M" / "F"
    val level: String,           // BEGINNER / INTERMEDIATE / ADVANCED
    val goal: String,            // SHRED / BULK / MAINTAIN
    val currentWeightKg: Double,
    val targetWeightKg: Double,
    val healthNotes: String,     // "tendinite genou", "douleur épaule"...

    // Chat continuity
    val recentChatSnippets: List<String>,  // 3 derniers messages user (max ~80 chars chacun)

    // Body scan
    val lastBodyScanDaysAgo: Int?,         // null = jamais scanné
    val waistCm: Double,                    // 0 = non mesuré
    val bodyFatPercent: Double,             // 0 = non mesuré
    val bodyScanNotes: String,              // observation IA, peut être ""

    // Activity
    val topExerciseNames: List<String>,    // 3 exos les plus pratiqués (par sets)
    val lastMealScanDish: String?,          // null si jamais scanné
    val workoutsThisWeek: Int,
    val targetWorkoutsPerWeek: Int,
    val weeklyVolumeKg: Int,
)
