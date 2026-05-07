package com.shredcoach.app.domain.coach

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shredcoach.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.coachDataStore: DataStore<Preferences> by preferencesDataStore(name = "coach_settings")

/**
 * Préférences utilisateur pour le **coach proactif IA**.
 *
 * Architecture : DataStore Preferences dédié — pas dans UserProfileEntity
 * pour éviter une migration Room v34→v35 (priorité du sprint = data-safety).
 *
 * Contient :
 * - [Snapshot.enabled] — opt-in global (default false)
 * - [Snapshot.preferredHourLocal] — heure du push quotidien
 * - [Snapshot.tone] — voix/style préférée (gentle/direct/drill)
 * - [Snapshot.mutedCategories] — catégories de triggers à ignorer
 * - [Snapshot.weeklyCap] — max notifs coach / 7 jours (anti-spam ultime)
 */
@Singleton
class CoachSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Tons disponibles. **Mappés à des prompts différents** dans
     * [CoachPromptBuilder] — change concrètement la formulation des notifs.
     */
    enum class Tone(
        val displayName: String,
        val description: String,
        @StringRes val displayNameRes: Int,
        @StringRes val descriptionRes: Int,
    ) {
        GENTLE("Doux", "Bienveillant, reformule sans pression", R.string.coach_tone_gentle, R.string.coach_tone_gentle_desc),
        DIRECT("Direct", "Constat factuel, action claire, ton neutre", R.string.coach_tone_direct, R.string.coach_tone_direct_desc),
        DRILL("Coach pro max", "Énergique, exigeant, vocabulaire sport", R.string.coach_tone_drill, R.string.coach_tone_drill_desc),
    }

    data class Snapshot(
        val enabled: Boolean,
        val preferredHourLocal: Int,
        val tone: Tone,
        val mutedCategories: Set<String>,
        val weeklyCap: Int,
    )

    private object Keys {
        val ENABLED = booleanPreferencesKey("proactive_coach_enabled")
        val HOUR = intPreferencesKey("preferred_hour_local")
        val TONE = stringPreferencesKey("tone")
        val MUTED = stringSetPreferencesKey("muted_categories")
        val WEEKLY_CAP = intPreferencesKey("weekly_cap")
    }

    val snapshot: Flow<Snapshot> = context.coachDataStore.data.map { prefs ->
        Snapshot(
            enabled = prefs[Keys.ENABLED] ?: false,
            preferredHourLocal = prefs[Keys.HOUR] ?: DEFAULT_HOUR,
            tone = prefs[Keys.TONE]?.let { runCatching { Tone.valueOf(it) }.getOrNull() } ?: Tone.DIRECT,
            mutedCategories = prefs[Keys.MUTED] ?: emptySet(),
            weeklyCap = prefs[Keys.WEEKLY_CAP] ?: DEFAULT_WEEKLY_CAP,
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.coachDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setPreferredHour(hour: Int) {
        context.coachDataStore.edit { it[Keys.HOUR] = hour.coerceIn(6, 22) }
    }

    suspend fun setTone(tone: Tone) {
        context.coachDataStore.edit { it[Keys.TONE] = tone.name }
    }

    suspend fun setMuted(categories: Set<String>) {
        context.coachDataStore.edit { it[Keys.MUTED] = categories }
    }

    suspend fun toggleMute(category: String) {
        context.coachDataStore.edit { prefs ->
            val current = prefs[Keys.MUTED] ?: emptySet()
            prefs[Keys.MUTED] = if (category in current) current - category else current + category
        }
    }

    suspend fun setWeeklyCap(cap: Int) {
        context.coachDataStore.edit { it[Keys.WEEKLY_CAP] = cap.coerceIn(1, 14) }
    }

    suspend fun reset() {
        context.coachDataStore.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_WEEKLY_CAP = 5
    }
}
