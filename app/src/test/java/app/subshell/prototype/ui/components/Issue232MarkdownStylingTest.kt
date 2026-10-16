package app.subshell.prototype.ui.components

import app.subshell.prototype.ui.components.ComposerMarkdownStyle.BOLD
import app.subshell.prototype.ui.components.ComposerMarkdownStyle.CODE
import app.subshell.prototype.ui.components.ComposerMarkdownStyle.ITALIC
import app.subshell.prototype.ui.components.ComposerMarkdownStyle.LINK
import app.subshell.prototype.ui.components.ComposerMarkdownStyle.STRIKETHROUGH
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #232 (COMPOSER232_MARKDOWN_SHOWN_AS_STYLE_V1): the composer shows Reddit
 * Markdown as its style while the markers stay in the text.
 */
class Issue232MarkdownStylingTest {
    private fun styled(text: String) =
        composerMarkdownSpans(text).map { span -> span.style to text.substring(span.start, span.end) }.toSet()

    @Test
    fun eachMarkerStylesItsTextMarkersIncluded() {
        assertEquals(
            setOf(
                BOLD to "**bold**",
                ITALIC to "*italic*",
                STRIKETHROUGH to "~~gone~~",
                CODE to "`code`",
                LINK to "[Reddit](https://www.reddit.com)",
            ),
            styled("**bold** *italic* ~~gone~~ `code` [Reddit](https://www.reddit.com)"),
        )
        assertEquals(setOf(BOLD to "__bold__", ITALIC to "_it_"), styled("__bold__ and _it_"))
    }

    @Test
    fun nothingInsideCodeIsStyledAgain() {
        assertEquals(setOf(CODE to "`**not bold**`"), styled("`**not bold**`"))
        val block = "```\n*not italic*\n```"
        assertEquals(setOf(CODE to block), styled(block))
    }

    @Test
    fun plainArithmeticAndNamesStayPlain() {
        assertEquals(emptySet<Pair<ComposerMarkdownStyle, String>>(), styled("2*3*4 and snake_case_name and ** spaced **"))
        assertEquals(emptySet<Pair<ComposerMarkdownStyle, String>>(), styled("an unclosed **bold"))
    }
}
