package com.shredcoach.app.domain.nutrition

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Lit la date/heure de prise de vue d'une image depuis ses métadonnées EXIF.
 *
 * **Cas d'usage** : le user upload une photo de son déj prise à 12h45 mais
 * ne pense à l'analyser qu'à 21h. Sans EXIF, l'app loggerait le repas à 21h
 * (heure de l'analyse) → faux pour les stats, le débrief, les calories du
 * soir, etc. Avec EXIF, on récupère le 12h45 d'origine → repas correctement
 * daté.
 *
 * **Tags EXIF lus, par ordre de priorité** :
 *  1. `DateTimeOriginal` (Tag 0x9003) : moment de prise de vue par le capteur.
 *     C'est LA donnée fiable. Présent sur 99% des photos smartphone.
 *  2. `DateTimeDigitized` (Tag 0x9004) : moment de numérisation. Pour les
 *     photos numériques natives, identique au précédent.
 *  3. `DateTime` (Tag 0x0132) : "moment de modification" — peut avoir été
 *     mis à jour par un soft de retouche. Fallback acceptable.
 *
 * **Format EXIF** : "yyyy:MM:dd HH:mm:ss" (notez les `:` au lieu de `-` pour
 * la date — c'est la spec EXIF 2.31 §4.6.4). Pas de timezone EXIF dans v2.x
 * → on suppose timezone système (cas standard pour un smartphone qui prend
 * une photo en local).
 *
 * **Robustesse** :
 *  - Si fichier non lisible (permission denied, URI invalide) → null
 *  - Si pas de tag date EXIF (image générée par soft, screenshot) → null
 *  - Si parse fail (format custom) → null + log warning
 *  - JAMAIS de throw : le caller utilise un `?: LocalDateTime.now()` fallback.
 */
object PhotoExifReader {

    private const val TAG = "PhotoExifReader"
    private val EXIF_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * Retourne la date/heure de prise de vue extraite des EXIF de [uri], ou
     * `null` si aucune donnée fiable. Ne throw jamais.
     */
    fun readCaptureDateTime(context: Context, uri: Uri): LocalDateTime? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                // Priorité au DateTimeOriginal (moment capteur) ; fallback sur
                // les autres tags moins fiables.
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return null
                parseExifDateTime(raw)
            }
        } catch (t: Throwable) {
            // Lecture échouée (URI invalidée, permission révoquée, fichier
            // corrompu). Pas critique — on retombe sur LocalDateTime.now()
            // côté caller.
            Log.w(TAG, "EXIF read failed for $uri", t)
            null
        }
    }

    /**
     * Parse le format EXIF "yyyy:MM:dd HH:mm:ss" en [LocalDateTime].
     * Retourne null si le format ne match pas (cas rare où un soft a réécrit
     * la balise en non-standard).
     */
    private fun parseExifDateTime(raw: String): LocalDateTime? {
        // Certains appareils mettent une string vide ou "0000:00:00 00:00:00"
        // quand pas de date dispo — filtre explicitement ces cas.
        if (raw.isBlank() || raw.startsWith("0000")) return null
        return try {
            LocalDateTime.parse(raw, EXIF_DATETIME_FORMAT)
        } catch (t: Throwable) {
            Log.w(TAG, "EXIF datetime parse failed: '$raw'", t)
            null
        }
    }
}
