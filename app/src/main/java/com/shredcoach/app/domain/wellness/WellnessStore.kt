package com.shredcoach.app.domain.wellness

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.wellnessStore by preferencesDataStore("wellness_check")

/**
 * Persiste le check-in journalier "comment tu te sens ?" en 1 tap (index emoji 0..4).
 *
 * **Pourquoi DataStore et pas Room** :
 *  - Donnée triviale (un int par jour), pas de relations, pas de queries complexes.
 *  - Évite une migration Room (qui demande une bump de version + Migration object).
 *  - Lecture/écriture instantanée, no-blocking.
 *
 * **Convention de clé** : `mood_YYYY-MM-DD` → entier 0..4. La date est fixée par
 * le caller (utiliser [LocalDate.now] côté ViewModel pour rester time-zone-aware).
 *
 * **GC** : pas implémenté. Pour 365 jours × 4 octets ≈ 1.5 Ko/an, le stockage est
 * négligeable. Si pertinent un jour, ajouter [purgeOlderThan].
 */
@Singleton
class WellnessStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Mood enregistré pour [date] ou null si pas encore tapé. */
    fun observeMood(date: LocalDate): Flow<Int?> {
        val key = intPreferencesKey(prefix(date))
        return context.wellnessStore.data.map { prefs -> prefs[key] }
    }

    /**
     * Enregistre le mood pour la date [date]. Index attendu dans [0..4]
     * correspondant aux 5 emojis (😴 😐 🙂 💪 🔥).
     */
    suspend fun saveMood(date: LocalDate, moodIndex: Int) {
        require(moodIndex in 0..4) { "moodIndex doit être dans [0..4], got $moodIndex" }
        val key = intPreferencesKey(prefix(date))
        context.wellnessStore.edit { prefs -> prefs[key] = moodIndex }
    }

    /** Lecture one-shot — utile pour les workers / contexts non-réactifs. */
    suspend fun moodOnce(date: LocalDate): Int? = observeMood(date).first()

    /** Reset complet — utilisé par DataPurger sur effacement utilisateur (RGPD). */
    suspend fun reset() {
        context.wellnessStore.edit(MutablePreferences::clear)
    }

    private fun prefix(date: LocalDate): String = "mood_${date.toString()}"

    companion object {
        /** Index → emoji + label affichage. Source de vérité unique. */
        val MOOD_OPTIONS = listOf(
            MoodOption(0, "😴", "Fatigué"),
            MoodOption(1, "😐", "Bof"),
            MoodOption(2, "🙂", "OK"),
            MoodOption(3, "💪", "Motivé"),
            MoodOption(4, "🔥", "En feu"),
        )
    }
}

private typealias MutablePreferences = androidx.datastore.preferences.core.MutablePreferences

data class MoodOption(val index: Int, val emoji: String, val label: String)
