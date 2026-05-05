package com.shredcoach.app.domain.coach

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.coachHistoryStore: DataStore<Preferences> by preferencesDataStore(name = "coach_history")

/**
 * Historique des notifications coach proactif émises par catégorie.
 *
 * **Pourquoi un store dédié plutôt que requêter `app_notifications` :** la table
 * Room est une chronologie générale (toutes les notifs : repas, séance, coach...).
 * Filter par catégorie (sub-classification du coach) implique un parsing du
 * `body` ou un nouveau champ. DataStore par catégorie = zéro migration, lecture
 * triviale, et survit à un wipe inbox notifications.
 *
 * **Anti-fatigue par cooldown** : chaque catégorie a son propre cooldown
 * (cf. [CoachTrigger.cooldownDays]). On vérifie la dernière émission via
 * [isOnCooldown] avant d'émettre. Si oui → on skip cette catégorie pour
 * cette fenêtre, on tente la suivante par score décroissant.
 *
 * **Ne contient PAS de PII** : juste catégorie + timestamp. RGPD-safe par design.
 */
@Singleton
class CoachHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Snapshot complet : toutes les catégories vues vs leurs derniers timestamps.
     * Permet à l'engine de filtrer en O(1) sans round-trip per-trigger.
     */
    val snapshot: Flow<Map<String, Instant>> = context.coachHistoryStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { (k, _) -> k.name.startsWith(PREFIX) }
            .mapNotNull { (k, v) ->
                val category = k.name.removePrefix(PREFIX)
                (v as? Long)?.let { category to Instant.ofEpochMilli(it) }
            }
            .toMap()
    }

    /** Date de dernière émission pour [category], ou null si jamais émise. */
    suspend fun lastEmittedAt(category: String): Instant? =
        snapshot.first()[category]

    /** Vrai si [category] a été émise il y a moins de [cooldown]. */
    suspend fun isOnCooldown(category: String, cooldown: Duration): Boolean {
        val last = lastEmittedAt(category) ?: return false
        return Duration.between(last, Instant.now()) < cooldown
    }

    /** Enregistre une émission de [category] (timestamp = maintenant). */
    suspend fun recordEmission(category: String) {
        context.coachHistoryStore.edit { prefs ->
            prefs[longPreferencesKey("$PREFIX$category")] = Instant.now().toEpochMilli()
        }
    }

    /** Reset complet (utilisé par DataPurger lors du right-to-be-forgotten). */
    suspend fun reset() {
        context.coachHistoryStore.edit { it.clear() }
    }

    /**
     * Compte les émissions sur les 7 derniers jours, toutes catégories confondues.
     * Utilisé pour appliquer le **weekly cap** utilisateur.
     */
    suspend fun emissionsLast7Days(): Int {
        val cutoff = Instant.now().minus(Duration.ofDays(7))
        return snapshot.first().count { (_, instant) -> instant.isAfter(cutoff) }
    }

    companion object {
        private const val PREFIX = "last_emitted_"

        /**
         * Catégories appartenant au moteur coach proactif. Exclut les autres
         * canaux qui partagent le même DataStore (ex: `meal_debrief`) — utile
         * pour calculer le weekly-cap du coach sans inclure les debrief repas.
         */
        val COACH_CATEGORIES: Set<String> = setOf(
            "streak_at_risk",
            "missed_workout",
            "pr_celebration",
            "protein_deficit",
            "plateau_volume",
            "comeback",
            "body_scan_stale",
            "weekly_recap",
            "goal_eta",
            "motivation_general",
        )

        /** Catégorie historique partagée pour le débrief post-repas. */
        const val MEAL_DEBRIEF_CATEGORY = "meal_debrief"
    }
}
