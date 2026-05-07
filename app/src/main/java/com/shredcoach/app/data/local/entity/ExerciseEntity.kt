package com.shredcoach.app.data.local.entity


import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shredcoach.app.domain.model.ExerciseVariant
import com.shredcoach.app.domain.model.MuscleGroup

/**
 * @property exerciseKey clé stable ASCII (snake_case) dérivée du nom FR via
 *   `ExerciseKey.fromName`. Utilisée pour :
 *   - Identifier un exercice indépendamment de la langue (matching seed-upsert
 *     remplace l'ancien match par `name`).
 *   - Résoudre les ressources `R.string.exo_<key>_<field>` au moment de
 *     l'affichage (cf. `ExerciseI18n`). Quand la traduction n'existe pas,
 *     l'affichage retombe sur le texte FR canonique stocké en DB.
 *   - Vide ("") pour les rows créées avant la migration v38→v39 ; backfillées
 *     par cette migration en utilisant le name existant.
 */
@Entity(tableName = "exercises", indices = [Index("exerciseKey")])
@Immutable
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val exerciseKey: String = "",
    val muscleGroup: MuscleGroup,
    val variant: ExerciseVariant,
    val equipment: String,
    val executionKey: String, // Conseils d'exécution
    val startingWeight: String, // Ex: "60-80 kg", "Poids du corps", "Barre vide 20 kg"
    val series: Int, // Nombre de séries
    val repsMin: Int, // Minimum de répétitions
    val repsMax: Int, // Maximum de répétitions
    val restSeconds: Int, // Temps de repos en secondes
    val tips: String, // Astuces et notes supplémentaires
    val tempo: String = "3-0-1-0", // Format: excentrique-pause basse-concentrique-pause haute (en secondes)
    val gifUrl: String? = null, // URL ou nom du fichier GIF dans assets
    val difficulty: Int = 1, // 1=Débutant, 2=Intermédiaire, 3=Avancé
    val isTimeBased: Boolean = false // true = durée au lieu de reps (gainage, mountain climber...)
)
