package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Messages02C5R2MarkdownRendererPolicyTest {
    @Test
    fun canonicalRendererConsumesComposerInlineMarkdownInsteadOfShowingDelimiters() {
        val rendered = markdownStyledAnnotatedString(
            source = "**Bold** _italic_ *also italic* ~~strike~~ `code` >!secret!<",
            linkColor = Color.Blue,
            codeBackground = Color.Gray,
            codeFontFamily = FontFamily.Monospace,
        )

        // ISSUE81: a hidden inline spoiler shows its label until it is revealed.
        assertEquals("Bold italic also italic strike code Spoiler", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(rendered.spanStyles.count { it.item.fontStyle == FontStyle.Italic } >= 2)
        assertTrue(rendered.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(rendered.spanStyles.any { it.item.background == Color.Gray })
    }

    @Test
    fun wholeLineRedditSpoilerIsNotMisclassifiedAsAQuote() {
        val blocks = parseMarkdownBlocks(">!spoiler text!<")
        assertEquals(listOf(MarkdownBlock.Spoiler("spoiler text")), blocks)
        // ISSUE81: a preview keeps it a spoiler instead of printing its text.
        assertEquals(">!spoiler text!<", markdownPreviewSource(blocks))
    }
}
