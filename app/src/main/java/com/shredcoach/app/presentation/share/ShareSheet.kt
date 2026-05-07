package com.shredcoach.app.presentation.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bottom sheet de partage : prévisualise la share card + bouton "Partager"
 * qui capture le composable affiché en bitmap, le sauve via FileProvider, et
 * lance un `Intent.ACTION_SEND` pour l'app picker système.
 *
 * **Architecture capture** : on utilise `rememberGraphicsLayer` +
 * `Modifier.drawWithContent { record + drawLayer }` pour recorder le rendu de
 * la card on-screen. Au tap "Partager", on convertit le layer en `ImageBitmap`
 * via `toImageBitmap()`, puis en `android.graphics.Bitmap` via `asAndroidBitmap()`.
 *
 * **Trade-off résolution** : la card est affichée à ~70% de la largeur du
 * bottom sheet (≈ 750-900 px sur écran moderne). Le bitmap capturé fait donc
 * cette résolution, pas 1080×1920 nominal. Pour Instagram/Twitter, 750×1330
 * est largement acceptable (le compositeur upscale propre). Pour un futur
 * "premium pixel-perfect", on passerait sur un offscreen `ComposeView`
 * attaché à un fake LifecycleOwner — overkill pour v1.
 *
 * **Pourquoi pas `ComposeView` offscreen** : `ComposeView.setContent` requiert
 * un `LifecycleOwner` et un `SavedStateRegistryOwner` attachés via les
 * `setViewTree*Owner` extensions. Sans ces 2 binds, la composition ne se
 * déclenche pas au measure() → bitmap noir. La complexité d'orchestrer ça
 * proprement (gestion lifecycle + cleanup) ne vaut pas la peine vu qu'on a
 * déjà la card affichée à l'écran à capture-time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    data: ShareCardData,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSharing by remember { mutableStateOf(false) }

    val graphicsLayer = rememberGraphicsLayer()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Partager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Aperçu de l'image qui sera partagée",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(16.dp))

            // Preview de la card — rendue directement à la taille du preview
            // (pas de requiredSize+scale magic, qui produisait un décalage
            // visuel à gauche sur certains devices et un clipping vertical
            // imprévisible). Le design de [ShareCard] est calibré pour rendre
            // proprement à ~290 × 515 dp (taille typique de cet emplacement).
            //
            // Bitmap capturé via `graphicsLayer.record` au moment du tap
            // "Partager" : la résolution = preview_dp × densité. Sur device
            // xxhdpi (3x) ça donne ~870 × 1545 px — largement suffisant pour
            // Stories IG/TikTok/Snap qui upscalent à 1080 × 1920.
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .aspectRatio(9f / 16f)
                    .align(Alignment.CenterHorizontally),
            ) {
                ShareCard(
                    data = data,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isSharing) return@Button
                    isSharing = true
                    scope.launch {
                        try {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val uri = withContext(Dispatchers.IO) {
                                saveBitmapToCache(context, bitmap)
                            }
                            launchShareIntent(context, uri, data.caption())
                            onDismiss()
                        } finally {
                            isSharing = false
                        }
                    }
                },
                enabled = !isSharing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Préparation…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Partager", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    val file = File(context.cacheDir, "shredcoach_share_${System.currentTimeMillis()}.png")
    file.outputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file,
    )
}

private fun launchShareIntent(context: Context, imageUri: Uri, caption: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Partager via…").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
