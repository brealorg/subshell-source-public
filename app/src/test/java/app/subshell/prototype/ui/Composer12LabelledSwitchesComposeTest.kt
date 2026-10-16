package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.screens.composerMediaRemoveLabel
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER12 (ui review round 3 P10+C9): composer switches carry their label
 * (the row is the switch), and the remove action names what it removes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer12LabelledSwitchesComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aPostOptionIsOneLabelledSwitchAndTheRowToggles() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(state.postComposerDraft.copy(community = "subshell", title = "Title"))
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("options")
        composeRule.onNodeWithText("Post options").performClick()
        composeRule.waitForIdle()

        val nsfw = composeRule.onNode(hasText("Mark as NSFW") and isToggleable())
        nsfw.assertIsOff()
        nsfw.performClick()
        composeRule.waitForIdle()
        nsfw.assertIsOn()
        assertTrue(state.postComposerDraft.nsfw)
    }

    @Test
    fun theRemoveActionNamesTheAttachment() {
        val video = ComposerMediaAttachment("content://v/1", "video/mp4", ComposerMediaKind.VIDEO)
        val gif = ComposerMediaAttachment("content://g/1", "image/gif", ComposerMediaKind.GIF)
        val image = ComposerMediaAttachment("content://i/1", "image/png", ComposerMediaKind.IMAGE)
        assertEquals("Remove video", composerMediaRemoveLabel(video, 0))
        assertEquals("Remove GIF 2", composerMediaRemoveLabel(gif, 1))
        assertEquals("Remove image 3", composerMediaRemoveLabel(image, 2))
    }
}
