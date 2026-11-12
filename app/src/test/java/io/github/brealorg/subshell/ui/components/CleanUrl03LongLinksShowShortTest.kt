package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.github.brealorg.subshell.model.markdownLinkTargetsForRenderedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** CLEANURL03_LONG_LINKS_SHOW_SHORT_V1: a long bare link shows short; the link under it is the whole address. */
class CleanUrl03LongLinksShowShortTest {
    private val long =
        "https://www.etsy.com/no-en/listing/4320800571/dog-show-bow-line-pet-hair-bow-yorkie" +
            "?ls=s&ga_order=most_relevant&ref=sr_gallery-1-4&variation0=5495919706"

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
        ).map { it.url }
    }

    @Test
    fun aLongBareLinkIsShownShortAndKeepsItsFullTarget() {
        val source = "look at $long please"
        val rendered = render(source)
        assertFalse(rendered.text, rendered.text.contains("ga_order"))
        assertTrue(rendered.text, rendered.text.startsWith("look at etsy.com/no-en/listing/"))
        assertTrue(rendered.text, rendered.text.endsWith("… please"))
        assertEquals(listOf(long), links(source))
    }

    @Test
    fun trailingPunctuationStaysOutsideTheLink() {
        val rendered = render("see ($long).")
        assertTrue(rendered.text, rendered.text.endsWith("…)."))
        assertEquals(listOf(long), links("see ($long)."))
    }

    @Test
    fun aShortLinkIsShownAsItIs() {
        val source = "go to https://example.org/short now"
        assertEquals(source, render(source).text)
        assertEquals(listOf("https://example.org/short"), links(source))
    }

    @Test
    fun aMarkdownLinkKeepsItsLabelAndIsNotShortenedTwice() {
        val source = "[$long]($long)"
        val rendered = render(source)
        assertEquals(long, rendered.text)
        assertEquals(listOf(long), links(source))
    }
}
