package com.shredcoach.app.domain.exercise

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.shredcoach.app.data.local.entity.ExerciseEntity

/**
 * Résolveur i18n pour les exercices du catalogue.
 *
 * Pour chaque champ traduisible (`name`, `equipment`, `executionKey`, `tips`,
 * `startingWeight`), tente de résoudre une ressource Android selon la convention :
 *
 *   `R.string.exo_<exerciseKey>_<field>`
 *
 * Si la ressource existe pour la locale courante, elle est servie. Sinon,
 * fallback transparent sur la valeur FR canonique stockée en DB (qui ne change
 * jamais et reste la source de vérité). Cela permet d'introduire les
 * traductions exercice par exercice sans risque de casser l'app si une clé
 * est manquante.
 *
 * **Pourquoi `Resources.getIdentifier`** : on ne peut pas générer un `R.id`
 * compile-time pour 441 exercices (et il y aurait un risque d'oubli au moment
 * d'ajouter un exo). Cette résolution dynamique a un coût (~quelques µs par
 * lookup, puis cachée par Android), négligeable pour un usage UI.
 *
 * **Why a singleton object** : pas d'état, juste de la résolution stateless.
 *
 * @sample
 *   val name = ExerciseI18n.resolveName(context, exercise)
 *   // EN locale + R.string.exo_squat_barre_name défini → "Barbell squat"
 *   // FR locale OU clé manquante                    → "Squat barre" (DB)
 */
object ExerciseI18n {

    private const val PREFIX = "exo_"

    fun resolveName(context: Context, exercise: ExerciseEntity): String =
        resolveField(context, exercise, "name", exercise.name)

    fun resolveEquipment(context: Context, exercise: ExerciseEntity): String =
        resolveField(context, exercise, "equipment", exercise.equipment)

    fun resolveExecution(context: Context, exercise: ExerciseEntity): String =
        resolveField(context, exercise, "execution", exercise.executionKey)

    fun resolveTips(context: Context, exercise: ExerciseEntity): String =
        resolveField(context, exercise, "tips", exercise.tips)

    fun resolveStartingWeight(context: Context, exercise: ExerciseEntity): String =
        resolveField(context, exercise, "starting_weight", exercise.startingWeight)

    private fun resolveField(
        context: Context,
        exercise: ExerciseEntity,
        field: String,
        fallback: String,
    ): String {
        val key = exercise.exerciseKey
        if (key.isBlank()) return fallback
        val resName = "$PREFIX${key}_$field"
        val resId = context.resources.getIdentifier(resName, "string", context.packageName)
        if (resId == 0) return fallback
        // Defensive : Android peut throw NotFoundException si la ressource est
        // déclarée dans values-en/ uniquement et que la locale courante (ex: ES)
        // n'a ni values-es/ ni la clé dans values/ (default FR). Le fallback DB
        // FR canonique reste cohérent et lisible — préférable à un crash.
        return try {
            context.getString(resId)
        } catch (_: android.content.res.Resources.NotFoundException) {
            fallback
        }
    }
}

/**
 * Composable wrapper qui retourne une snapshot des 5 champs i18n d'un
 * exercice. Re-recompute si la locale change (via [LocalConfiguration]).
 *
 * Préférer ce helper dans les Composables plutôt que d'appeler les
 * `resolveXxx` un par un — évite 5 lookups séparés et 5 invalidations
 * potentielles si la locale change.
 */
data class LocalizedExercise(
    val name: String,
    val equipment: String,
    val execution: String,
    val tips: String,
    val startingWeight: String,
)

@Composable
fun rememberLocalizedExercise(exercise: ExerciseEntity): LocalizedExercise {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    return remember(exercise.id, exercise.exerciseKey, locales) {
        LocalizedExercise(
            name = ExerciseI18n.resolveName(context, exercise),
            equipment = ExerciseI18n.resolveEquipment(context, exercise),
            execution = ExerciseI18n.resolveExecution(context, exercise),
            tips = ExerciseI18n.resolveTips(context, exercise),
            startingWeight = ExerciseI18n.resolveStartingWeight(context, exercise),
        )
    }
}
