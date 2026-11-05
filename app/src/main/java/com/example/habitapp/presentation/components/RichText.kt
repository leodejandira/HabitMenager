package com.example.habitapp.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Lightweight rich-text format used by the journal. Formatting is persisted as
 * plain-text markers so entries stay readable, searchable and exportable:
 *
 * - `**bold**`, `*italic*`, `__underline__` (inline, may be nested)
 * - lines starting with `# ` are headings
 * - lines starting with `- ` are bullet items, `1. ` numbered items
 */
object RichText {

    enum class Inline(val marker: String) { BOLD("**"), ITALIC("*"), UNDERLINE("__") }

    enum class LinePrefix(val prefix: String) { HEADING("# "), BULLET("- "), NUMBERED("1. ") }

    private val numberedRegex = Regex("^(\\d+)\\. ")

    /** Wraps the selection in [style] markers, or inserts an empty pair at the cursor. */
    fun toggleInline(value: TextFieldValue, style: Inline): TextFieldValue {
        val marker = style.marker
        val start = value.selection.min
        val end = value.selection.max
        val text = value.text
        val before = text.substring(0, start)
        val selected = text.substring(start, end)
        val after = text.substring(end)
        // Already wrapped: unwrap.
        if (before.endsWith(marker) && after.startsWith(marker) && selected.isNotEmpty()) {
            val newText = before.dropLast(marker.length) + selected + after.drop(marker.length)
            return TextFieldValue(newText, TextRange(start - marker.length, end - marker.length))
        }
        val newText = before + marker + selected + marker + after
        return TextFieldValue(newText, TextRange(start + marker.length, end + marker.length))
    }

    /** Adds or removes a line prefix on every line touched by the selection. */
    fun toggleLinePrefix(value: TextFieldValue, prefix: LinePrefix): TextFieldValue {
        val text = value.text
        val lineStart = text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(0)).let {
            if (value.selection.min == 0) 0 else if (it < 0) 0 else it + 1
        }
        val lineEndIndex = text.indexOf('\n', value.selection.max).let { if (it < 0) text.length else it }
        val lines = text.substring(lineStart, lineEndIndex).split('\n')
        val allHave = lines.all { hasPrefix(it, prefix) }
        var counter = 1
        val replaced = lines.joinToString("\n") { line ->
            val stripped = stripPrefixes(line)
            if (allHave) {
                stripped
            } else {
                when (prefix) {
                    LinePrefix.NUMBERED -> "${counter++}. $stripped"
                    else -> prefix.prefix + stripped
                }
            }
        }
        val newText = text.substring(0, lineStart) + replaced + text.substring(lineEndIndex)
        val cursor = lineStart + replaced.length
        return TextFieldValue(newText, TextRange(cursor))
    }

    private fun hasPrefix(line: String, prefix: LinePrefix): Boolean = when (prefix) {
        LinePrefix.NUMBERED -> numberedRegex.containsMatchIn(line)
        else -> line.startsWith(prefix.prefix)
    }

    private fun stripPrefixes(line: String): String = when {
        line.startsWith(LinePrefix.HEADING.prefix) -> line.removePrefix(LinePrefix.HEADING.prefix)
        line.startsWith(LinePrefix.BULLET.prefix) -> line.removePrefix(LinePrefix.BULLET.prefix)
        numberedRegex.containsMatchIn(line) -> line.replaceFirst(numberedRegex, "")
        else -> line
    }

    /** Renders markup into a styled string. */
    fun render(markup: String, headingSize: TextUnit = 20.sp): AnnotatedString = buildAnnotatedString {
        markup.split('\n').forEachIndexed { index, rawLine ->
            if (index > 0) append('\n')
            when {
                rawLine.startsWith(LinePrefix.HEADING.prefix) ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingSize)) {
                        appendInline(rawLine.removePrefix(LinePrefix.HEADING.prefix))
                    }
                rawLine.startsWith(LinePrefix.BULLET.prefix) -> {
                    append("  •  ")
                    appendInline(rawLine.removePrefix(LinePrefix.BULLET.prefix))
                }
                numberedRegex.containsMatchIn(rawLine) -> {
                    val number = numberedRegex.find(rawLine)?.groupValues?.get(1).orEmpty()
                    append("  $number.  ")
                    appendInline(rawLine.replaceFirst(numberedRegex, ""))
                }
                else -> appendInline(rawLine)
            }
        }
    }

    /** Plain text without markers (for previews and search snippets). */
    fun plain(markup: String): String = render(markup).text

    private fun AnnotatedString.Builder.appendInline(text: String) {
        var i = 0
        var bold = false
        var italic = false
        var underline = false
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isEmpty()) return
            val style = SpanStyle(
                fontWeight = if (bold) FontWeight.Bold else null,
                fontStyle = if (italic) FontStyle.Italic else null,
                textDecoration = if (underline) TextDecoration.Underline else null,
            )
            withStyle(style) { append(buffer.toString()) }
            buffer.clear()
        }
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    flush()
                    bold = !bold
                    i += 2
                }
                text.startsWith("__", i) -> {
                    flush()
                    underline = !underline
                    i += 2
                }
                text[i] == '*' -> {
                    flush()
                    italic = !italic
                    i += 1
                }
                else -> {
                    buffer.append(text[i])
                    i += 1
                }
            }
        }
        flush()
    }
}
