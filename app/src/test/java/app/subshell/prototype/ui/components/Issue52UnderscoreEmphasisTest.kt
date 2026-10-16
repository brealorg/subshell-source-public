package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ISSUE52_UNDERSCORE_EMPHASIS_AT_WORD_BOUNDARIES_V1 /
 * ISSUE52_UNDERSCORES_IN_LINKS_STAY_TEXT_V1: an underscore emphasis closed on
 * the next '_' anywhere (inside a username), opened after '/' or '.', and
 * opened before a space; the underscores vanished and mention or URL links
 * pointed at the wrong target.
 */
class Issue52UnderscoreEmphasisTest {
    private fun render(source: String): AnnotatedString = markdownStyledAnnotatedString(
        source = source,
        linkColor = Color.Blue,
        codeBackground = Color.Gray,
        codeFontFamily = FontFamily.Monospace,
    )

    private fun links(source: String): List<String> {
        val rendered = render(source)
        return markdownLinkTargetsForRenderedText(
            source = source,
            renderedText = rendered.text,
            renderedMarkdownLinks = rendered.renderedMarkdownLinks(),
        ).map { target -> target.url }
    }

    private fun italic(source: String): List<String> {
        val rendered = render(source)
        return rendered.spanStyles
            .filter { range -> range.item.fontStyle == FontStyle.Italic }
            .map { range -> rendered.text.substring(range.start, range.end) }
    }

    @Test
    fun anEmphasisDoesNotCloseInsideAUsername() {
        val source = "se _dette, og spør u/Mr_Bean"
        assertEquals(source, render(source).text)
        assertEquals(listOf("/u/Mr_Bean"), links(source))
    }

    @Test
    fun underscoresAfterASlashInLinksStay() {
        assertEquals("ask u/_foo_bar now", render("ask u/_foo_bar now").text)
        assertEquals(listOf("/u/_foo_bar"), links("ask u/_foo_bar now"))
        val url = "https://example.com/_static/a_b"
        assertEquals("see $url", render("see $url").text)
        assertEquals(listOf(url), links("see $url"))
    }

    @Test
    fun anUnderscoreBeforeASpaceIsText() {
        assertEquals("a _ b _ c", render("a _ b _ c").text)
        assertEquals(emptyList<String>(), italic("a _ b _ c"))
    }

    @Test
    fun ordinaryEmphasisStillWorks() {
        assertEquals("italic", render("_italic_").text)
        assertEquals(listOf("italic"), italic("_italic_"))
        assertEquals(listOf("very much"), italic("text _very much_ text"))
        assertEquals(listOf("a"), italic("(_a_)"))
    }
}
