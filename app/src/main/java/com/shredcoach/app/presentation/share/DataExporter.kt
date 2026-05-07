package com.shredcoach.app.presentation.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Génère et expose des fichiers d'export pour les données stats et
 * historiques. Format-agnostique côté caller : tu fournis une [ExportPayload]
 * (structure tabulaire ou groupée), tu choisis un [ExportFormat], tu reçois
 * un Uri prêt pour `Intent.ACTION_SEND`.
 *
 * **Architecture payload** : structure générique qui décrit les données
 * comme une table (rows + columns) + métadonnées (titre, période). Toutes
 * les sources de données (workout history, nutrition stats, etc.) se mappent
 * dessus avant export — pas besoin de nouvel exporter par source.
 *
 * **Sécurité** : on n'embarque jamais de données sensibles non-utilisateur
 * (PII tiers, API keys, tokens). Tout ce qui sort dans l'export est par
 * définition les données que l'user a déjà saisies/générées.
 */
object DataExporter {

    /** Description tabulaire d'un dataset à exporter. */
    data class ExportPayload(
        /** Titre du dataset, ex. "Historique séances Mai 2026". */
        val title: String,
        /** Description optionnelle (période, filtres appliqués). */
        val description: String? = null,
        /** Headers des colonnes (ordre = ordre des cellules dans chaque row). */
        val columns: List<String>,
        /** Lignes de données. Chaque List<String> doit avoir size == columns.size. */
        val rows: List<List<String>>,
        /** Sommaire optionnel (totaux, moyennes) affiché en haut/bas selon format. */
        val summary: List<Pair<String, String>> = emptyList(),
    )

    /**
     * Génère le contenu textuel selon le format choisi.
     *
     * **Encoding** : tout en UTF-8 (BOM pour CSV pour qu'Excel sur Windows
     * détecte les accents français correctement, sinon "été" devient "Ã©tÃ©").
     */
    fun render(payload: ExportPayload, format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> renderCsv(payload)
        ExportFormat.JSON -> renderJson(payload)
        ExportFormat.TXT -> renderTxt(payload)
    }

    private fun renderCsv(p: ExportPayload): String = buildString {
        // BOM UTF-8 → Excel Windows détecte l'encoding correctement
        append('﻿')
        // Métadonnées en commentaires CSV (lignes qui commencent par #) —
        // pas standard mais Excel les ignore comme texte. Pour rester robuste
        // côté parsers stricts, on les met en lignes "Titre,Description".
        append("\"# ").append(p.title).append("\"\n")
        if (p.description != null) {
            append("\"# ").append(p.description).append("\"\n")
        }
        append("\"# Exporté le ").append(timestamp()).append("\"\n")
        if (p.summary.isNotEmpty()) {
            p.summary.forEach { (k, v) ->
                append("\"# ").append(k).append(" : ").append(v).append("\"\n")
            }
        }
        append("\n")
        // Headers
        append(p.columns.joinToString(",") { csvEscape(it) }).append("\n")
        // Rows
        p.rows.forEach { row ->
            append(row.joinToString(",") { csvEscape(it) }).append("\n")
        }
    }

    private fun renderJson(p: ExportPayload): String {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val obj = mapOf(
            "title" to p.title,
            "description" to p.description,
            "exportedAt" to timestamp(),
            "summary" to p.summary.toMap(),
            "columns" to p.columns,
            "rows" to p.rows.map { row ->
                p.columns.zip(row).toMap()
            },
        )
        return gson.toJson(obj)
    }

    private fun renderTxt(p: ExportPayload): String = buildString {
        append("═══════════════════════════════════════════\n")
        append(" ").append(p.title).append("\n")
        append("═══════════════════════════════════════════\n")
        if (p.description != null) {
            append(p.description).append("\n")
        }
        append("Exporté le ").append(timestamp()).append("\n\n")

        if (p.summary.isNotEmpty()) {
            append("──── Sommaire ────\n")
            p.summary.forEach { (k, v) ->
                append("  ").append(k).append(" : ").append(v).append("\n")
            }
            append("\n")
        }

        append("──── Données (").append(p.rows.size).append(" lignes) ────\n\n")
        // Format compact : chaque row sur plusieurs lignes (label : value)
        // Plus lisible qu'une vraie table ASCII pour les small/mid datasets,
        // évite d'avoir à calculer la largeur de chaque colonne.
        p.rows.forEachIndexed { idx, row ->
            append("[").append(idx + 1).append("]\n")
            p.columns.zip(row).forEach { (col, value) ->
                append("  ").append(col).append(" : ").append(value).append("\n")
            }
            append("\n")
        }
    }

    /**
     * Sauve le contenu en cacheDir et retourne un FileProvider Uri prêt pour
     * `Intent.ACTION_SEND`. Le filename inclut un timestamp pour éviter les
     * collisions inter-exports.
     */
    suspend fun saveToCache(
        context: Context,
        content: String,
        format: ExportFormat,
        baseFilename: String,
    ): Uri = withContext(Dispatchers.IO) {
        val safe = baseFilename.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(context.cacheDir, "${safe}_${ts}.${format.extension}")
        file.writeText(content, Charsets.UTF_8)
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /** Lance le picker système pour partager le fichier exporté. */
    fun launchShareIntent(context: Context, uri: Uri, format: ExportFormat, subject: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Exporter vers…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun csvEscape(s: String): String {
        // RFC 4180 : si la valeur contient `,`, `"`, ou un saut de ligne →
        // on la wrap dans `"..."` et on double les `"` internes.
        if (s.isEmpty()) return ""
        val needsQuoting = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return s
        val escaped = s.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}
