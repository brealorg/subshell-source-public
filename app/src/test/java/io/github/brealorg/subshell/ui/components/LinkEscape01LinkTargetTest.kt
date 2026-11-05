package io.github.brealorg.subshell.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import io.github.brealorg.subshell.model.markdownLinkTargetsForRenderedText
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * LINKESCAPE01_ESCAPED_CHARACTERS_LEAVE_LINK_TARGETS_V1: Reddit's editor saves
 * '_' as '\_', also inside a link target. The shown text dropped the
 * backslash (ISSUE79) but `[label](https://x.com/a\_b)` took its target raw
 * and opened an address with a '\' in it.
 */
class LinkEscape01LinkTargetTest {
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

    @Test
    fun aMarkdownLinkWithEscapedUnderscoresOpensTheRealAddress() {
        assertEquals(
            listOf("https://example.com/a_b_c"),
            links("read [the page](https://example.com/a\\_b\\_c) now"),
        )
    }

    @Test
    fun otherEscapedCharactersLeaveTheTargetToo() {
        assertEquals(
            listOf("https://example.com/p?x=1&y=2#top"),
            links("[page](https://example.com/p?x=1\\&y=2\\#top)"),
        )
    }

    @Test
    fun aLinkWithoutEscapesIsUnchanged() {
        assertEquals(
            listOf("https://example.com/a_b_c"),
            links("[page](https://example.com/a_b_c)"),
        )
    }

    @Test
    fun aBareUrlWithEscapedUnderscoresStillOpensTheRealAddress() {
        assertEquals(
            listOf("https://example.com/a_b_c"),
            links("see https://example.com/a\\_b\\_c now"),
        )
        assertEquals(
            "see https://example.com/a_b_c now",
            render("see https://example.com/a\\_b\\_c now").text,
        )
    }
}
