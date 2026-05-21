package com.shredcoach.app.domain.notification

import android.content.Context
import android.util.Log
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.domain.glucose.GlucoseAnalysisEngine
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.locale.withCurrentLocale
import com.shredcoach.app.presentation.navigation.Screen
import java.time.LocalDate

/**
 * Notif "récap glycémique J+1" déclenchée à 12h17 le lendemain. Tente d'abord
 * une analyse LLM Dr. Glykos (riche, contextuelle), avec fallback rule-based
 * si le LLM n'est pas dispo.
 *
 * **Stratégie hybride** :
 *  1. `engine.analyzeAndCache(yesterday)` — réutilise le cache si l'user a
 *     déjà ouvert l'écran d'analyse aujourd'hui (zero LLM cost), sinon
 *     déclenche l'inférence. Le builder consomme uniquement `.summary`
 *     (phrase 1-2 lignes optimisée pour la notif).
 *  2. Fallback : si l'engine retourne null (pas de clé API / LLM down / pas
 *     de courbe), on retombe sur les templates pattern-based historiques pour
 *     garantir une notif même en mode dégradé.
 *
 * **Pourquoi 12h17 et pas 8h ou 10h** :
 *  - L'user a typiquement déjeuné → il vient de provoquer un possible pic
 *    postprandial → c'est le moment psychologique le plus pertinent pour
 *    lui rappeler "regarde ce qui s'est passé hier".
 *  - Pas conflit avec les notifs LUNCH (12h30 par défaut) — 13 min d'avance
 *    suffisent.
 *  - L'horaire 12:17 n'est pas rond → moins facile à ignorer mentalement
 *    qu'un 12:00 ou 12:30 "pile" (effet psy : un timestamp précis suggère
 *    qu'il y a une raison spécifique = curiosité).
 *
 * **Skip rules** :
 *  - Pas de log glucose hier → on n'a rien à dire, skip silencieux
 *  - Pattern INSUFFICIENT_DATA (<7j de data sur 30) → skip pour ne pas
 *    générer un récap sans base statistique
 *
 * **Deep-link** : tap sur la notif → [Screen.GlucoseAnalysis] avec la date
 * de J-1 pré-remplie → l'user atterrit directement sur l'analyse experte.
 */
object GlucoseRecapBuilder {

    private const val TAG = "GlucoseRecapBuilder"

    /**
     * Version LLM-aware : tente engine.analyzeAndCache, fallback sur le rule-
     * based si null. Appelée depuis [ShredCoachNotificationWorker].
     */
    suspend fun build(
        ctx: Context,
        s: UserContextSnapshot,
        engine: GlucoseAnalysisEngine,
    ): NotifDecision {
        // Pas de data hier = rien à dire (avant même de tenter l'inférence).
        if (!s.yesterdayGlucoseLogged || s.yesterdayGlucoseAvgMgdl == null) {
            return NotifDecision.Skip("glucose_recap_no_yesterday_data")
        }
        if (s.glucosePattern == GlucosePattern.INSUFFICIENT_DATA) {
            return NotifDecision.Skip("glucose_recap_insufficient_history")
        }

        val yesterday = LocalDate.now().minusDays(1)
        val locale = ctx.withCurrentLocale()
        val title = locale.getString(R.string.notif_glucose_recap_title)
        val deeplink = Screen.GlucoseAnalysis.createRoute(yesterday)

        // 1. Tentative LLM (cache-first via engine, donc rapide si déjà calculé)
        val analysis = runCatching { engine.analyzeAndCache(yesterday) }
            .onFailure { Log.w(TAG, "engine.analyzeAndCache threw", it) }
            .getOrNull()
        if (analysis != null && analysis.summary.isNotBlank()) {
            Log.i(TAG, "Sending LLM-driven recap (verdict=${analysis.verdict})")
            return NotifDecision.Send(
                title = title,
                body = analysis.summary,
                channelId = ShredCoachApplication.CHANNEL_WORKOUT,
                deeplink = deeplink,
            )
        }

        // 2. Fallback rule-based — garantit qu'on push une notif même si LLM down.
        Log.i(TAG, "LLM unavailable, falling back to rule-based template")
        return buildFromPatternFallback(ctx, s, title, deeplink)
    }

    /**
     * Version legacy (sync, rule-based). Conservée pour rétro-compat avec les
     * tests / appels directs. La logique métier est extraite de [build] avant
     * la conversion LLM-aware.
     */
    fun build(ctx: Context, s: UserContextSnapshot): NotifDecision {
        // Pas de data hier = rien à dire.
        if (!s.yesterdayGlucoseLogged || s.yesterdayGlucoseAvgMgdl == null) {
            return NotifDecision.Skip("glucose_recap_no_yesterday_data")
        }
        // <7j de history = analyse pas fiable.
        if (s.glucosePattern == GlucosePattern.INSUFFICIENT_DATA) {
            return NotifDecision.Skip("glucose_recap_insufficient_history")
        }

        val locale = ctx.withCurrentLocale()
        val title = locale.getString(R.string.notif_glucose_recap_title)
        val yesterdayAvg = s.yesterdayGlucoseAvgMgdl.toInt()
        val yesterdayTir = s.yesterdayTirPct ?: 0
        val yesterdayPeak = s.yesterdayPeakMgdl?.toInt() ?: 0
        val yesterdayHypo = s.yesterdayHypoCount ?: 0

        // Body adapté au pattern dominant — pas au snapshot d'hier seul (un
        // pattern dominant sur 30j est plus stable et plus actionnable qu'une
        // unique journée).
        val body = when (s.glucosePattern) {
            GlucosePattern.HYPO_RISK ->
                locale.getString(R.string.notif_glucose_recap_hypo, yesterdayHypo)
            GlucosePattern.POSTPRANDIAL_SPIKES ->
                locale.getString(R.string.notif_glucose_recap_spike, yesterdayPeak)
            GlucosePattern.HIGH_VARIABILITY ->
                locale.getString(
                    R.string.notif_glucose_recap_variability,
                    s.glucose30dCv?.toInt() ?: 36,
                )
            GlucosePattern.DAWN_PHENOMENON ->
                locale.getString(R.string.notif_glucose_recap_dawn)
            GlucosePattern.STABLE_OPTIMAL ->
                locale.getString(R.string.notif_glucose_recap_stable, yesterdayTir)
            GlucosePattern.RISING_TREND ->
                locale.getString(R.string.notif_glucose_recap_rising)
            GlucosePattern.FALLING_TREND ->
                locale.getString(R.string.notif_glucose_recap_falling)
            else ->
                locale.getString(
                    R.string.notif_glucose_recap_default,
                    yesterdayAvg, yesterdayTir,
                )
        }

        return NotifDecision.Send(
            title = title,
            body = body,
            channelId = ShredCoachApplication.CHANNEL_WORKOUT, // pas de CHANNEL_HEALTH dédié V1, on réutilise
            deeplink = Screen.GlucoseAnalysis.createRoute(LocalDate.now().minusDays(1)),
        )
    }

    /**
     * Helper interne factorisant la construction depuis le pattern, pour
     * éviter de dupliquer la logique entre [build] suspend et legacy sync.
     * Le `title`/`deeplink` sont passés en param car déjà calculés en amont.
     */
    private fun buildFromPatternFallback(
        ctx: Context,
        s: UserContextSnapshot,
        title: String,
        deeplink: String,
    ): NotifDecision {
        val locale = ctx.withCurrentLocale()
        val yesterdayAvg = s.yesterdayGlucoseAvgMgdl?.toInt() ?: 0
        val yesterdayTir = s.yesterdayTirPct ?: 0
        val yesterdayPeak = s.yesterdayPeakMgdl?.toInt() ?: 0
        val yesterdayHypo = s.yesterdayHypoCount ?: 0

        val body = when (s.glucosePattern) {
            GlucosePattern.HYPO_RISK ->
                locale.getString(R.string.notif_glucose_recap_hypo, yesterdayHypo)
            GlucosePattern.POSTPRANDIAL_SPIKES ->
                locale.getString(R.string.notif_glucose_recap_spike, yesterdayPeak)
            GlucosePattern.HIGH_VARIABILITY ->
                locale.getString(R.string.notif_glucose_recap_variability, s.glucose30dCv?.toInt() ?: 36)
            GlucosePattern.DAWN_PHENOMENON ->
                locale.getString(R.string.notif_glucose_recap_dawn)
            GlucosePattern.STABLE_OPTIMAL ->
                locale.getString(R.string.notif_glucose_recap_stable, yesterdayTir)
            GlucosePattern.RISING_TREND ->
                locale.getString(R.string.notif_glucose_recap_rising)
            GlucosePattern.FALLING_TREND ->
                locale.getString(R.string.notif_glucose_recap_falling)
            else ->
                locale.getString(R.string.notif_glucose_recap_default, yesterdayAvg, yesterdayTir)
        }

        return NotifDecision.Send(
            title = title,
            body = body,
            channelId = ShredCoachApplication.CHANNEL_WORKOUT,
            deeplink = deeplink,
        )
    }
}
