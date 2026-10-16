package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #35: the fullscreen viewers offer share and download for post media and comment images. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaViewerActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anImagePostHasShareAndDownloadInTheChrome() {
        val post = SampleData.posts.first { it.kind == PostKind.IMAGE }
        composeRule.setContent {
            MediaViewerScreen(post = post, state = SubshellState(), onBack = {})
        }
        composeRule.onNodeWithContentDescription("Back").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Share media").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Download").assertIsEnabled()
    }

    @Test
    fun aCommentImageHasShareAndDownloadToo() {
        composeRule.setContent {
            CommentMediaViewerScreen(uri = "https://i.redd.it/example.jpg", onBack = {})
        }
        composeRule.onNodeWithContentDescription("Back").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Share media").assertIsEnabled()
        assertEquals(1, composeRule.onAllNodesWithContentDescription("Download").fetchSemanticsNodes().size)
    }
}
