package com.shredcoach.app.domain.streak

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.streakMilestoneStore: DataStore<Preferences> by preferencesDataStore(name = "streak_milestones")

/**
 * Mémorise les milestones de streak DÉJÀ célébrés par une dialog/animation.
 *
 * **Anti double-pop** : sans cette mémoire, l'utilisateur qui clôt sa dialog
 * "30 jours !" et rouvre l'app verrait la même dialog re-popper (puisque
 * son streak vaut toujours 30). Stockage par milestone (key : `milestone_$days`)
 * → granularité fine, simple à reset si on veut tester.
 *
 * **Pourquoi DataStore vs Room** : ces données sont des états locaux d'UX,
 * pas du contenu utilisateur (ils ne valent rien à backuper, peuvent être
 * reset sans impact métier). Migrations Room évitées, lecture asynchrone
 * native via Flow.
 */
@Singleton
class StreakMilestoneStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val celebratedMilestones: Flow<Set<Int>> = context.streakMilestoneStore.data.map { prefs ->
        prefs.asMap().keys
            .mapNotNull { k ->
                if (!k.name.startsWith(PREFIX)) return@mapNotNull null
                k.name.removePrefix(PREFIX).toIntOrNull()
            }
            .toSet()
    }

    suspend fun snapshot(): Set<Int> = celebratedMilestones.first()

    /**
     * Marque [milestone] comme célébré, **et tous les paliers inférieurs définis
     * dans [StreakService.MILESTONES]**.
     *
     * Pourquoi marquer les inférieurs : si l'utilisateur récupère un backup et
     * débarque avec un streak de 100j, on lui pop la dialog "100 jours" en
     * priorité. Sans cette propagation, plus tard quand son streak descend
     * (deload, semaine off) puis remonte à 60j, on lui pop "60 jours" alors
     * qu'il a déjà passé ce palier. Marquer les inférieurs au moment du highest
     * = "tu as déjà passé tout ça, on n'y revient pas".
     */
    suspend fun markCelebrated(milestone: Int) {
        val toMark = StreakService.MILESTONES.filter { it <= milestone }
        if (toMark.isEmpty()) return
        context.streakMilestoneStore.edit { prefs ->
            toMark.forEach { m -> prefs[intPreferencesKey("$PREFIX$m")] = 1 }
        }
    }

    /** Reset complet — utilisé par DataPurger lors du right-to-be-forgotten. */
    suspend fun reset() {
        context.streakMilestoneStore.edit { it.clear() }
    }

    private companion object {
        const val PREFIX = "milestone_"
    }
}
