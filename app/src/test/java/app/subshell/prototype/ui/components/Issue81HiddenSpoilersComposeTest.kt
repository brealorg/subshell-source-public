package app.subshell.prototype.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.model.markdownLinkTargetsForRenderedText
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE81_SPOILERS_HIDDEN_UNTIL_TAPPED_V1: an inline spoiler was tinted but
 * readable, and a whole-line spoiler printed its text in feed previews.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue81HiddenSpoilersComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private fun styled(source: String, revealed: Set<String> = emptySet()) = markdownStyledAnnotatedString(
        source = source,
        linkColor = Color.Blue,
        codeBackground = Color.Gray,
        codeFontFamily = FontFamily.Monospace,
        revealedSpoilers = revealed,
    )

    private fun shows(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anInlineSpoilerShowsOnlyItsLabelUntilRevealed() {
        assertEquals("test Spoiler slutt", styled("test >!hemmelig!< slutt").text)
        assertEquals("test hemmelig slutt", styled("test >!hemmelig!< slutt", setOf("hemmelig")).text)
    }

    @Test
    fun aHiddenSpoilerHidesItsLinksToo() {
        val source = "see >!u/spoiled_user!<"
        val rendered = styled(source)
        assertEquals(emptyList<String>(), markdownLinkTargetsForRenderedText(source, rendered.text).map { it.url })
    }

    @Test
    fun tappingTheLabelRevealsTheSpoiler() {
        composeRule.setContent { SubshellTheme { MarkdownText(text = "test >!hemmelig!< slutt") } }
        composeRule.waitForIdle()
        assertEquals(false, shows("hemmelig"))

        composeRule.onNodeWithText("Spoiler", substring = true, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(true, shows("test hemmelig slutt"))
    }

    @Test
    fun aWholeLineSpoilerStaysHiddenInAPreview() {
        composeRule.setContent { SubshellTheme { MarkdownText(text = ">!the ending!<", maxLines = 3) } }
        composeRule.waitForIdle()

        assertEquals(false, shows("the ending"))
        assertEquals(true, shows("Spoiler"))
    }
}
