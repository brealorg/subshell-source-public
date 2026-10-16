package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import app.subshell.prototype.model.projectCommentContent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * COMMENTTEXT03 (#151, #152): a markdown link's range came from searching the
 * rendered text for its label, so "Here's the link: [link](url)" linked the
 * first "link"; URLs with parentheses (Wikipedia) were cut at '(' or ')'.
 * The renderer now places markdown link ranges itself, and balanced
 * parentheses belong to the URL.
 */
class CommentText03LinkRangesTest {
    private fun links(source: String): Pair<String, List<Pair<String, String>>> {
        val rendered = markdownStyledAnnotatedString(
            source = source,
            linkColor = Color.Blue,
            codeBackground = Color.Gray,
            codeFontFamily = FontFamily.Monospace,
        )
        val targets = markdownLinkTargetsForRenderedText(
            source = source,
            renderedText = rendered.text,
            renderedMarkdownLinks = rendered.renderedMarkdownLinks(),
        )
        return rendered.text to targets.map { target ->
            "${target.start}:" + rendered.text.substring(target.start, target.end) to target.url
        }
    }

    @Test
    fun aMarkdownLinkIsPlacedOnItsOwnLabelNotAnEarlierWord() {
        assertEquals(
            "Here's the link: link" to listOf("17:link" to "https://example.com/a"),
            links("Here's the link: [link](https://example.com/a)"),
        )
        assertEquals(
            "see here and here" to listOf("13:here" to "https://example.com/b"),
            links("see here and [here](https://example.com/b)"),
        )
    }

    @Test
    fun parenthesesInsideAUrlBelongToIt() {
        val wiki = "https://en.wikipedia.org/wiki/Python_(programming_language)"
        assertEquals(
            "Python rocks" to listOf("0:Python" to wiki),
            links("[Python]($wiki) rocks"),
        )
        assertEquals(
            "Read $wiki." to listOf("5:$wiki" to wiki),
            links("Read $wiki."),
        )
        // An unbalanced ')' closes the surrounding text, not the URL.
        assertEquals(
            "(see https://example.com/a)" to listOf("5:https://example.com/a" to "https://example.com/a"),
            links("(see https://example.com/a)"),
        )
        assertEquals("(pic )", projectCommentContent("(pic https://i.redd.it/a.jpg)").displayText)
    }
}
