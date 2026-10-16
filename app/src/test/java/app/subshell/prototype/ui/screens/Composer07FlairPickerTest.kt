package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.subshell.prototype.ui.ComposerFlair
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER07 (ui review round 3 P7): the post flair picker scrolls, keeps
 * editable flair text within Reddit's 64 characters and sends it trimmed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer07FlairPickerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val editable = ComposerFlair("edit", "Default text", editable = true)

    @Test
    fun editableTextIsTrimmedCappedAndFallsBackWhenBlank() {
        assertEquals("Fixed", postFlairSelectionText(editable, "  Fixed  "))
        assertEquals("Default text", postFlairSelectionText(editable, "   "))
        assertEquals(64, postFlairSelectionText(editable, "x".repeat(100)).length)
        val fixed = ComposerFlair("fixed", "News")
        assertEquals("News", postFlairSelectionText(fixed, "ignored"))
    }

    @Test
    fun aLongListScrollsToItsLastFlairAndTypingStopsAt64() {
        val options = (1..40).map { ComposerFlair("t$it", "Flair $it") } + editable
        var chosen: ComposerFlair? = null
        composeRule.setContent {
            SubshellTheme {
                PostFlairPickerSheet(
                    required = false,
                    options = options,
                    selected = null,
                    onSelect = { chosen = it },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Flair 40").performScrollTo().assertIsDisplayed()
        val field = composeRule.onNode(hasSetTextAction())
        field.performScrollTo()
        field.performTextReplacement("y".repeat(70))
        field.performTextReplacement("  Mine  ")
        composeRule.onNodeWithText("Default text", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals("Mine", chosen?.text)
        assertEquals("edit", chosen?.templateId)
    }
}
