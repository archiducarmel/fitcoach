package com.shredcoach.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable qui rend du texte Markdown simple.
 * Supporte : **bold**, *italic*, `inline code`, ```code blocks```,
 * # headers, - listes, > citations.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    lineHeight: androidx.compose.ui.unit.TextUnit = 22.sp
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.CodeBlock -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp
                            ),
                            color = color.copy(alpha = 0.9f)
                        )
                    }
                }
                is MdBlock.Heading -> {
                    Text(
                        text = buildAnnotatedInline(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                is MdBlock.Quote -> {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(
                            Modifier.width(3.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedInline(block.text),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic, lineHeight = lineHeight
                            ),
                            color = color.copy(alpha = 0.8f)
                        )
                    }
                }
                is MdBlock.ListItem -> {
                    Row(Modifier.fillMaxWidth()) {
                        Text("•", modifier = Modifier.width(16.dp),
                            color = color.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = buildAnnotatedInline(block.text),
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = lineHeight),
                            color = color
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedInline(block.text),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = lineHeight),
                        color = color
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════
// PARSER — blocks
// ═══════════════════════════════════════

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data class ListItem(val text: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        when {
            // Code block ```
            line.trimStart().startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i]); i++
                }
                blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n")))
                i++ // skip closing ```
            }
            // Heading
            line.startsWith("### ") -> { blocks.add(MdBlock.Heading(3, line.removePrefix("### ").trim())); i++ }
            line.startsWith("## ") -> { blocks.add(MdBlock.Heading(2, line.removePrefix("## ").trim())); i++ }
            line.startsWith("# ") -> { blocks.add(MdBlock.Heading(1, line.removePrefix("# ").trim())); i++ }
            // List item
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                blocks.add(MdBlock.ListItem(line.trimStart().removePrefix("- ").removePrefix("* ").trim())); i++
            }
            // Numbered list
            line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                val text = line.trimStart().replaceFirst(Regex("^\\d+\\.\\s"), "")
                blocks.add(MdBlock.ListItem(text)); i++
            }
            // Quote
            line.trimStart().startsWith("> ") -> {
                blocks.add(MdBlock.Quote(line.trimStart().removePrefix("> ").trim())); i++
            }
            // Empty line → skip
            line.isBlank() -> i++
            // Paragraph (accumulate contiguous lines)
            else -> {
                val para = StringBuilder(line)
                i++
                while (i < lines.size && lines[i].isNotBlank()
                    && !lines[i].startsWith("#") && !lines[i].startsWith("```")
                    && !lines[i].trimStart().startsWith("- ") && !lines[i].trimStart().startsWith("* ")
                    && !lines[i].trimStart().startsWith("> ")
                    && !lines[i].trimStart().matches(Regex("^\\d+\\.\\s.*"))) {
                    para.append(" ").append(lines[i])
                    i++
                }
                blocks.add(MdBlock.Paragraph(para.toString()))
            }
        }
    }
    return blocks
}

// ═══════════════════════════════════════
// INLINE FORMATTING — **bold** *italic* `code`
// ═══════════════════════════════════════

private fun buildAnnotatedInline(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append("**"); i += 2 }
                }
                // `inline code`
                text[i] == '`' && !text.startsWith("```", i) -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x20808080)
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append('`'); i++ }
                }
                // *italic* (single asterisk, not double)
                text[i] == '*' && (i + 1 >= text.length || text[i + 1] != '*') -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append('*'); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
