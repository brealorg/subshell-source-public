package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MarkdownHighlightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val highlight = MarkdownHighlight(query = "kotlin", background = Color.Red)

    @Test
    fun rangesAreCaseInsensitiveAndDoNotOverlap() {
        assertEquals(listOf(0..5, 11..16), markdownHighlightRanges("Kotlin and kotlin", "KOTLIN"))
        assertEquals(listOf(0..1, 2..3), markdownHighlightRanges("aaaa", "aa"))
        assertEquals(emptyList<IntRange>(), markdownHighlightRanges("text", " "))
    }

    @Test
    fun noHighlightLeavesTheStringUntouched() {
        val text = AnnotatedString("kotlin")
        assertSame(text, text.withMarkdownHighlight(null))
    }

    @Test
    fun renderedMarkdownGetsTheBackgroundOnTheVisibleWords() {
        composeRule.setContent {
            MarkdownText(text = "I like **Kotlin**, see [kotlin docs](https://example.com)", highlight = highlight)
        }
        val texts = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text] }
        val rendered = texts.single { text -> text.text.startsWith("I like") }
        val highlighted = rendered.spanStyles
            .filter { span -> span.item.background == Color.Red }
            .map { span -> rendered.text.substring(span.start, span.end) }
        assertEquals(listOf("Kotlin", "kotlin"), highlighted)
    }
}
