package com.shredcoach.app.domain.notification

import android.content.Context
import com.shredcoach.app.R
import com.shredcoach.app.ShredCoachApplication
import com.shredcoach.app.domain.glucose.GlucosePattern
import com.shredcoach.app.domain.locale.withCurrentLocale

/**
 * Notif "récap glycémique J+1" déclenchée à 12h17 le lendemain. Analyse la
 * glycémie de la veille via Dr. Glykos et propose une CTA contextuelle.
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
 * **Body templates par pattern** : on adapte le ton au pattern dominant 30j.
 * Critique pour ne pas être générique ("Hier : avg 118") mais actionnable
 * ("Hier pic 195 à 13h, après tes pâtes — Dr. Glykos a une analyse pour toi").
 */
object GlucoseRecapBuilder {

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
            deeplink = null, // tap = open app, le user clique sur la card Home pour aller à Dr. Glykos
        )
    }
}
