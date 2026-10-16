package app.subshell.prototype.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.network.reddit.RedditCommentDataDto
import app.subshell.prototype.data.network.reddit.bodyWithRedditMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #35: a GIF in a comment opens the media actions sheet on long press,
 * while a plain tap still reaches the comment body (tap-to-collapse).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommentMediaActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val body = RedditCommentDataDto(body = "Caption\n\n![gif](giphy|Icp7SunbILYe4)").bodyWithRedditMediaMetadata()
    private val gif = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    @Test
    fun longPressOnACommentGifOpensMediaActionsAndATapStillReachesTheBody() {
        var bodyTaps = 0
        composeRule.setContent {
            Box(Modifier.size(400.dp).clickable { bodyTaps += 1 }) {
                CommentContent(source = body)
            }
        }
        val gifNode = composeRule.onNode(gif)

        gifNode.performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(1, bodyTaps)
        assertEquals(0, composeRule.onAllNodesWithText("Media actions").fetchSemanticsNodes().size)

        gifNode.performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, bodyTaps)
        assertEquals(1, composeRule.onAllNodesWithText("Media actions").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Download").fetchSemanticsNodes().size)
    }

    @Test
    fun twoGiphyGifsGetDifferentFileNames() {
        assertNotEquals(
            commentMediaStableName("https://i.giphy.com/media/aaa/giphy.gif"),
            commentMediaStableName("https://i.giphy.com/media/bbb/giphy.gif"),
        )
    }
}
