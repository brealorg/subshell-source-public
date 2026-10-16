package app.subshell.prototype.ui.components

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// COMPOSER232_MARKDOWN_SHOWN_AS_STYLE_V1 (#232, owner: as Google's Jetchat does):
// while the reader types, Markdown is shown as its style. The markers stay in
// the text and nothing is written back: the field still holds plain Markdown.

internal enum class ComposerMarkdownStyle { BOLD, ITALIC, STRIKETHROUGH, CODE, LINK }

internal data class ComposerMarkdownSpan(
    val style: ComposerMarkdownStyle,
    val start: Int,
    val end: Int,
)

private val CodeBlockPattern = Regex("```[\\s\\S]*?```")
private val InlineCodePattern = Regex("`[^`\\n]+`")
private val InlinePatterns = listOf(
    ComposerMarkdownStyle.BOLD to Regex("\\*\\*(?=\\S)[^\\n]*?\\S\\*\\*"),
    ComposerMarkdownStyle.BOLD to Regex("(?<!\\w)__(?=\\S)[^\\n]*?\\S__(?!\\w)"),
    ComposerMarkdownStyle.ITALIC to Regex("(?<![*\\w])\\*(?=[^\\s*])[^*\\n]*?[^\\s*]?\\*(?![*\\w])"),
    ComposerMarkdownStyle.ITALIC to Regex("(?<![_\\w])_(?=[^\\s_])[^_\\n]*?[^\\s_]?_(?![_\\w])"),
    ComposerMarkdownStyle.STRIKETHROUGH to Regex("~~(?=\\S)[^\\n]*?\\S~~"),
    ComposerMarkdownStyle.LINK to Regex("\\[[^\\]\\n]+]\\([^)\\s]+\\)"),
)

/**
 * The styled ranges of [text], markers included. Code (fenced or inline) is
 * found first and nothing inside it is styled again.
 */
internal fun composerMarkdownSpans(text: CharSequence): List<ComposerMarkdownSpan> {
    val spans = mutableListOf<ComposerMarkdownSpan>()
    val code = mutableListOf<IntRange>()
    fun inCode(range: IntRange) = code.any { range.first <= it.last && it.first <= range.last }
    CodeBlockPattern.findAll(text).forEach { match ->
        code += match.range
        spans += ComposerMarkdownSpan(ComposerMarkdownStyle.CODE, match.range.first, match.range.last + 1)
    }
    InlineCodePattern.findAll(text).filterNot { inCode(it.range) }.forEach { match ->
        code += match.range
        spans += ComposerMarkdownSpan(ComposerMarkdownStyle.CODE, match.range.first, match.range.last + 1)
    }
    InlinePatterns.forEach { (style, pattern) ->
        pattern.findAll(text).filterNot { inCode(it.range) }.forEach { match ->
            spans += ComposerMarkdownSpan(style, match.range.first, match.range.last + 1)
        }
    }
    return spans
}

@Composable
internal fun rememberComposerMarkdownStyling(): OutputTransformation {
    val colors = MaterialTheme.colorScheme
    return remember(colors.surfaceContainerHighest, colors.primary) {
        val styles = mapOf(
            ComposerMarkdownStyle.BOLD to SpanStyle(fontWeight = FontWeight.Bold),
            ComposerMarkdownStyle.ITALIC to SpanStyle(fontStyle = FontStyle.Italic),
            ComposerMarkdownStyle.STRIKETHROUGH to SpanStyle(textDecoration = TextDecoration.LineThrough),
            ComposerMarkdownStyle.CODE to SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = colors.surfaceContainerHighest,
            ),
            ComposerMarkdownStyle.LINK to SpanStyle(color = colors.primary),
        )
        OutputTransformation {
            composerMarkdownSpans(asCharSequence()).forEach { span ->
                addStyle(styles.getValue(span.style), span.start, span.end)
            }
        }
    }
}
