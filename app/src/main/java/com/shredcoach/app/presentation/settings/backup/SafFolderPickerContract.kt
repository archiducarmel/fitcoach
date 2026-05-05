package com.shredcoach.app.presentation.settings.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Contract pour ouvrir le sélecteur de dossier SAF (Storage Access Framework)
 * Android natif. L'utilisateur peut pointer vers Drive / OneDrive / Dropbox /
 * stockage local — l'app travaillera ensuite avec [androidx.documentfile.provider.DocumentFile]
 * au-dessus de l'URI persistée, peu importe le provider sous-jacent.
 *
 * Différence vs `ActivityResultContracts.OpenDocumentTree` standard : on
 * ajoute le flag [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION] et expose
 * [persistPermissions] / [releasePermissions] pour que l'URI survive au
 * reboot. Sans cette étape, [SecurityException] tombe dès la session suivante.
 *
 * Usage :
 * ```kotlin
 * val launcher = rememberLauncherForActivityResult(SafFolderPickerContract()) { uri ->
 *     uri ?: return@rememberLauncherForActivityResult
 *     SafFolderPickerContract.persistPermissions(context, uri)
 *     viewModel.onFolderPicked(uri)
 * }
 * launcher.launch(currentFolderUri)  // null si aucun dossier déjà sélectionné
 * ```
 *
 * @input URI du dossier précédemment sélectionné (pour pré-positionner le
 *   picker). Passer `null` au premier usage.
 * @output URI du dossier choisi par l'utilisateur, ou `null` s'il a annulé.
 */
class SafFolderPickerContract : ActivityResultContract<Uri?, Uri?>() {

    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            // Pré-sélection du dernier dossier choisi (DocumentsContract API 26+).
            // Le système pose ce hint en best-effort — certains providers
            // (Drive notamment) ne le respectent pas, c'est une limitation
            // côté provider, pas côté app.
            if (input != null) {
                putExtra(EXTRA_INITIAL_URI, input)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }

    companion object {
        // Constante hardcodée car DocumentsContract.EXTRA_INITIAL_URI n'existe
        // qu'en API 26+, mais sa string-value ("android.provider.extra.INITIAL_URI")
        // est stable depuis longtemps. minSdk = 26 → on pourrait utiliser la
        // constante directement, mais hardcoder la string évite un import.
        private const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"

        /**
         * Pin l'URI dans la table système des permissions persistantes.
         * À appeler **après** réception de l'URI dans le callback du contract.
         * Sans ça → [SecurityException] au prochain accès post-reboot.
         *
         * Idempotent : appeler plusieurs fois sur le même URI n'a pas d'effet
         * de bord (le système dédoublonne).
         *
         * Limite système : 128 URIs persistées max par contentResolver
         * (cf. [android.content.ContentResolver.takePersistableUriPermission]).
         * On libère explicitement via [releasePermissions] quand on change
         * de dossier de backup pour ne pas accumuler.
         */
        fun persistPermissions(context: Context, uri: Uri) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }

        /**
         * Libère les permissions persistantes sur [uri]. À appeler quand
         * l'utilisateur change de dossier de backup ou désactive la sauvegarde.
         *
         * Tolère le cas où l'URI n'avait pas (ou plus) de permission —
         * [SecurityException] silencieusement ignorée.
         */
        fun releasePermissions(context: Context, uri: Uri) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // L'URI n'avait pas de permission persistante (état déjà propre).
            }
        }
    }
}
