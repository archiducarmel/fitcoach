package com.shredcoach.app.domain.nutrition

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recalibrationBannerStore: DataStore<Preferences> by
    preferencesDataStore(name = "nutrition_recalibration_banner")

/**
 * Mémorise si l'utilisateur a vu et fermé le banner "On a recalibré le calcul
 * des kcal de séance" (suite au passage MET 5.5 → 3.8 + facteur 0.7 sur la
 * durée active).
 *
 * **Pourquoi ce banner** : la cible calorique adaptative du jour baisse de
 * ~300-400 kcal les jours d'entraînement avec le nouveau modèle. Sans
 * explication, l'utilisateur va voir ses chiffres bouger et penser à un bug.
 *
 * **Pourquoi DataStore vs Room** : un simple flag UX local, pas du contenu
 * utilisateur. Pas de backup nécessaire, pas de migration.
 *
 * **Versionning de la clé** : si plus tard on refait une recalibration majeure,
 * on utilisera `BANNER_KEY_V3` pour re-afficher le banner même aux users qui
 * avaient déjà dismiss la V2. Versioner explicitement = pas de surprise.
 */
@Singleton
class RecalibrationBannerStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True = banner doit être affiché. False = déjà dismissé par l'utilisateur. */
    val shouldShow: Flow<Boolean> = context.recalibrationBannerStore.data.map { prefs ->
        prefs[BANNER_KEY_V2_MET_38] != true
    }

    suspend fun dismiss() {
        context.recalibrationBannerStore.edit { it[BANNER_KEY_V2_MET_38] = true }
    }

    /** Reset (debug / test only — pas de surface UI). */
    suspend fun reset() {
        context.recalibrationBannerStore.edit { it.clear() }
    }

    private companion object {
        /** V2 = passage MET 5.5 → 3.8 + ACTIVE_TIME_RATIO 0.7 (2026-05-16). */
        val BANNER_KEY_V2_MET_38 = booleanPreferencesKey("dismissed_v2_met38")
    }
}
