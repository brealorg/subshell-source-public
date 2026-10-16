package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ISSUE79_BACKSLASH_ESCAPES_V1, ISSUE80_NESTED_INLINE_MARKUP_V1,
 * ISSUE82_LIST_ITEMS_ARE_BULLETS_V1.
 */
class Issue79And80And82MarkdownTest {
    private fun render(source: String): AnnotatedString = markdownStyledAnnotatedString(
        source = source,
        linkColor = Color.Blue,
        codeBackground = Color.Gray,
        codeFontFamily = FontFamily.Monospace,
    )

    private fun AnnotatedString.italicTexts(): List<String> = spanStyles
        .filter { it.item.fontStyle == FontStyle.Italic }
        .map { text.substring(it.start, it.end) }

    private fun AnnotatedString.boldTexts(): List<String> = spanStyles
        .filter { it.item.fontWeight == FontWeight.Bold }
        .map { text.substring(it.start, it.end) }

    @Test
    fun escapedMarkupIsShownLiterally() {
        val rendered = render("""my\_var and \*not italic\* and 1\. item""")

        assertEquals("my_var and *not italic* and 1. item", rendered.text)
        assertEquals(emptyList<String>(), rendered.italicTexts())
    }

    @Test
    fun aLinkInsideBoldIsALink() {
        val rendered = render("**[Read the rules](https://reddit.com/r/x/wiki/rules)**")

        assertEquals("Read the rules", rendered.text)
        assertEquals(listOf("Read the rules"), rendered.boldTexts())
        assertEquals(
            listOf("https://reddit.com/r/x/wiki/rules"),
            rendered.renderedMarkdownLinks().map { it.url },
        )
    }

    @Test
    fun tripleStarsAreBoldItalic() {
        val rendered = render("***wow***")

        assertEquals("wow", rendered.text)
        assertTrue(
            rendered.spanStyles.any {
                it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
            },
        )
    }

    @Test
    fun italicInsideBoldIsParsed() {
        val rendered = render("**bold and *italic* inside**")

        assertEquals("bold and italic inside", rendered.text)
        assertEquals(listOf("italic"), rendered.italicTexts())
    }

    @Test
    fun starListItemsAreBullets() {
        val rendered = render("* one\n* two\n- three\n+ four")

        assertEquals("• one\n• two\n• three\n• four", rendered.text)
        assertEquals(emptyList<String>(), rendered.italicTexts())
    }

    @Test
    fun spacedStarsAreArithmeticNotEmphasis() {
        val rendered = render("2 * 3 * 4 and *real* emphasis")

        assertEquals("2 * 3 * 4 and real emphasis", rendered.text)
        assertEquals(listOf("real"), rendered.italicTexts())
    }
}
