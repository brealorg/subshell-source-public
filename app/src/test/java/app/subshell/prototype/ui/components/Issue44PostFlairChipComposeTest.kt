package app.subshell.prototype.ui.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.subshell.prototype.model.FlairRichTextSegment
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #44: the post flair chip. An emoji segment gets an image slot
 * (still loading on the JVM, so it shows as a progress indicator); the raw
 * ":upvote:" placeholder is never shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue44PostFlairChipComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val emojiSlot = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    private fun render(text: String, richText: List<FlairRichTextSegment> = emptyList()) {
        composeRule.setContent {
            SubshellTheme { PostFlairChip(text = text, richText = richText) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anEmojiFlairShowsTheEmojiAndTheTextWithoutThePlaceholder() {
        render(
            text = ":upvote:Outfit Check",
            richText = listOf(
                FlairRichTextSegment(
                    elementType = "emoji",
                    emojiRef = ":upvote:",
                    imageUrl = "https://emoji.redditmedia.com/upvote.png",
                ),
                FlairRichTextSegment(elementType = "text", text = "Outfit Check"),
            ),
        )

        composeRule.onNodeWithText("Outfit Check").assertExists()
        composeRule.onAllNodesWithText(":upvote:", substring = true).assertCountEquals(0)
        composeRule.onAllNodes(emojiSlot).assertCountEquals(1)
    }

    @Test
    fun aPlainFlairIsTheSameTextChip() {
        render(text = "Outfit Check")

        composeRule.onNodeWithText("Outfit Check").assertExists()
        composeRule.onAllNodes(emojiSlot).assertCountEquals(0)
    }

    @Test
    fun anEmojiFlairWithoutRichTextHidesThePlaceholder() {
        render(text = ":upvote:Outfit Check")

        composeRule.onNodeWithText("Outfit Check").assertExists()
        composeRule.onAllNodesWithText(":upvote:", substring = true).assertCountEquals(0)
    }
}
