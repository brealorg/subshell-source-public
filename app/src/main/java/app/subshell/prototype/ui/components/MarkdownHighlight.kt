package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily

// ISSUE29_THREAD_FIND_V1: find-in-thread highlights matches in the rendered
// markdown text (syntax already stripped). Code blocks and hidden spoilers
// are rendered outside this path, so they are neither highlighted nor
// searched; markdownSearchableText mirrors exactly the highlighted blocks.

/** Case-insensitive [query] to highlight with [background] in rendered markdown. */
data class MarkdownHighlight(
    val query: String,
    val background: Color,
)

/** Start/end offsets of case-insensitive, non-overlapping [query] matches in [text]. */
internal fun markdownHighlightRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = 0
    while (from <= text.length - needle.length) {
        val at = text.indexOf(needle, startIndex = from, ignoreCase = true)
        if (at < 0) break
        ranges += at until at + needle.length
        from = at + needle.length
    }
    return ranges
}

internal fun AnnotatedString.withMarkdownHighlight(highlight: MarkdownHighlight?): AnnotatedString {
    if (highlight == null) return this
    val ranges = markdownHighlightRanges(text, highlight.query)
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withMarkdownHighlight)
        ranges.forEach { range ->
            addStyle(SpanStyle(background = highlight.background), range.first, range.last + 1)
        }
    }
}

/** The text a reader sees in the highlighted blocks of [source], for matching. */
internal fun markdownSearchableText(source: String): String =
    promoteMarkdownHeadings(parseMarkdownBlocks(source))
        .mapNotNull { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> block.text
                is MarkdownBlock.Heading -> block.text
                is MarkdownBlock.Quote -> block.text
                is MarkdownBlock.Spoiler,
                is MarkdownBlock.Code,
                MarkdownBlock.HorizontalRule,
                -> null
            }
        }
        .joinToString(separator = "\n") { text ->
            markdownStyledAnnotatedString(
                source = text,
                linkColor = Color.Unspecified,
                codeBackground = Color.Unspecified,
                codeFontFamily = FontFamily.Default,
            ).withInlineContentMasked()
        }

// LOWUI8_EMOTE_ALT_TEXT_IS_NOT_SEARCHED_V1 (OCB-136): an emote is drawn as an
// image, but its alternate text (":name:") stands in the string. It is
// replaced by object-replacement characters of the same length, so offsets
// still line up with the rendered text and a search cannot match it.
private const val COMPOSE_INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"

private fun AnnotatedString.withInlineContentMasked(): String {
    val inline = getStringAnnotations(COMPOSE_INLINE_CONTENT_TAG, 0, length)
    if (inline.isEmpty()) return text
    val chars = text.toCharArray()
    inline.forEach { range -> for (i in range.start until range.end) chars[i] = '\uFFFC' }
    return String(chars)
}
