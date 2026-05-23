package com.shredcoach.app.presentation.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shredcoach.app.data.remote.EmbeddingService
import kotlin.math.absoluteValue
import kotlin.math.max

/**
 * UI EMBEDDING : text input -> vector display avec heatmap horizontale
 * + statistiques (dimension, norme, sample values, min/max).
 *
 * Layout :
 *  - TextField multiline en haut
 *  - Bouton Calculer
 *  - Resultat : header (dim + tokens + latency) + heatmap mini (1ere dim 128
 *    valeurs) + sample values en monospace + stats numeriques
 */
@Composable
fun EmbeddingInteraction(
    state: LlmDebugState,
    onGenerate: (String, ByteArray?) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var sourceImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    // Fix #2 : detection multimodal embedding → image picker conditionnel
    val isMultimodal = state.selectedModel?.info?.kind ==
        com.shredcoach.app.domain.llm.ModelKind.MULTIMODAL_EMBEDDING

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                sourceImageBytes = bytes
                sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header pedagogique
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                if (isMultimodal)
                    "🔢👁️ Multimodal embedding : convertit texte ET/OU image en vecteur " +
                    "unifié dans le même espace sémantique (CLIP-like, ex: NVCLIP)."
                else
                    "🔢 Embedding : convertit ton texte en vecteur dense (utilisé pour " +
                    "recherche sémantique, clustering, RAG). Le modèle retourne un " +
                    "vecteur de dimension fixe (768, 1024, 1536…).",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }

        // Image picker conditionnel (multimodal only)
        if (isMultimodal) {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Image (optionnelle, embedding texte+image fusionnés)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (sourceBitmap != null) {
                        Box(Modifier.fillMaxWidth()) {
                            Image(
                                bitmap = sourceBitmap!!.asImageBitmap(),
                                contentDescription = "Image source",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { imagePicker.launch("image/*") },
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Sélectionner une image", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (isMultimodal) "Texte (optionnel)" else "Texte à embedder") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
            maxLines = 8,
            placeholder = { Text("Ex: La récursion en informatique est un concept où une fonction s'appelle elle-même…") },
        )

        Button(
            onClick = { onGenerate(input.trim(), sourceImageBytes) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !state.isSending && (
                (isMultimodal && (input.isNotBlank() || sourceImageBytes != null)) ||
                (!isMultimodal && input.isNotBlank())
            ),
        ) {
            if (state.isSending) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Calcul en cours…", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Default.Calculate, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Générer l'embedding", fontWeight = FontWeight.Bold)
            }
        }

        state.embeddingResult?.let { result -> EmbeddingResultView(result) }
    }
}

@Composable
private fun EmbeddingResultView(result: EmbeddingService.EmbeddingResult) {
    val embedding = result.embedding
    val stats = remember(embedding) {
        EmbeddingStats(
            dimension = embedding.size,
            min = embedding.minOrNull() ?: 0.0,
            max = embedding.maxOrNull() ?: 0.0,
            mean = embedding.average(),
            norm = kotlin.math.sqrt(embedding.sumOf { it * it }),
        )
    }
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Stats card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Embedding généré",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatItem("Dim", "${result.dimension}", Modifier.weight(1f))
                    StatItem("Tokens", "${result.tokensInput}", Modifier.weight(1f))
                    StatItem("Latence", "${result.latencyMs}ms", Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatItem("Min", "%.4f".format(stats.min), Modifier.weight(1f))
                    StatItem("Max", "%.4f".format(stats.max), Modifier.weight(1f))
                    StatItem("Norme", "%.4f".format(stats.norm), Modifier.weight(1f))
                }
            }
        }

        // Heatmap visualisation
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Visualisation (premières 128 dimensions)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                EmbeddingHeatmap(embedding.take(128), stats.min, stats.max)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendDot(Color(0xFF1E40AF), "négatif")
                    LegendDot(Color(0xFFE5E7EB), "≈0")
                    LegendDot(Color(0xFFDC2626), "positif")
                }
            }
        }

        // Sample values (premieres 16) avec bouton copy
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Valeurs (16 premières)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(embedding.joinToString(", ") { "%.6f".format(it) }))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copier tout le vecteur", Modifier.size(18.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        embedding.take(16).joinToString(",  ") { "%+.4f".format(it) },
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EmbeddingHeatmap(values: List<Double>, globalMin: Double, globalMax: Double) {
    val rangeAbs = max(globalMax.absoluteValue, globalMin.absoluteValue).coerceAtLeast(1e-6)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val cellW = w / values.size
        val h = size.height
        values.forEachIndexed { i, v ->
            val intensity = (v / rangeAbs).coerceIn(-1.0, 1.0).toFloat()
            val color = if (intensity > 0) {
                lerpColor(Color(0xFFE5E7EB), Color(0xFFDC2626), intensity)
            } else {
                lerpColor(Color(0xFFE5E7EB), Color(0xFF1E40AF), -intensity)
            }
            drawRect(
                color = color,
                topLeft = Offset(i * cellW, 0f),
                size = Size(cellW, h),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = 1f,
    )
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(Modifier.width(6.dp))
    }
}

private data class EmbeddingStats(
    val dimension: Int,
    val min: Double,
    val max: Double,
    val mean: Double,
    val norm: Double,
)
