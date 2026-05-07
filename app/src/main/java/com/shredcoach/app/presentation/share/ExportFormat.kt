package com.shredcoach.app.presentation.share

import androidx.annotation.StringRes
import com.shredcoach.app.R

/**
 * Formats d'export supportés.
 *
 * **Pourquoi pas XLSX** : Apache POI bloate l'APK de 8-10 Mo, fastexcel-writer
 * de 3 Mo. Pour la plupart des users (ouvrir dans Excel/Sheets/Numbers), CSV
 * fait l'affaire — toutes ces apps importent CSV nativement avec parfois un
 * dialog d'import (séparateur, encoding) à l'ouverture. Le ratio coût/bénéfice
 * d'un vrai .xlsx ne justifie pas le bloat APK pour une app fitness.
 *
 * @property displayName libellé affiché dans le bottom sheet
 * @property mimeType MIME pour Intent SEND
 * @property extension extension fichier (sans le point)
 */
enum class ExportFormat(
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val description: String,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
) {
    CSV(
        displayName = "CSV",
        mimeType = "text/csv",
        extension = "csv",
        description = "Tableur (Excel, Numbers, Sheets)",
        displayNameRes = R.string.export_format_csv,
        descriptionRes = R.string.export_format_csv_desc,
    ),
    JSON(
        displayName = "JSON",
        mimeType = "application/json",
        extension = "json",
        description = "Pour developers, APIs, scripts",
        displayNameRes = R.string.export_format_json,
        descriptionRes = R.string.export_format_json_desc,
    ),
    TXT(
        displayName = "Texte",
        mimeType = "text/plain",
        extension = "txt",
        description = "Lisible humain, copier-coller",
        displayNameRes = R.string.export_format_txt,
        descriptionRes = R.string.export_format_txt_desc,
    ),
}
