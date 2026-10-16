package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * COMPOSER18_BODY_EDIT_BY_DEFAULT_V1: the post body opens in the editor;
 * Preview lasts only while the composer is shown, and an empty body has no
 * Preview at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer18BodyEditByDefaultComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun composerWith(body: String): SubshellState {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(community = "subshell", title = "A post", body = body),
        )
        return state
    }

    private fun scrollToBody() {
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("body")
        composeRule.waitForIdle()
    }

    private fun count(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun previewIsNotKeptWhenTheComposerIsShownAgain() {
        val state = composerWith("Body text")
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        scrollToBody()

        composeRule.onNodeWithText("Preview").performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("Edit"))

        restoration.emulateSavedInstanceStateRestore()
        scrollToBody()

        assertEquals(0, count("Edit"))
        assertEquals(1, count("Preview"))
    }

    @Test
    fun anEmptyBodyHasNoPreviewAndShowsTheEditor() {
        val state = composerWith("")
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        scrollToBody()

        assertEquals(0, count("Preview"))
        assertEquals(0, count("Nothing to preview yet"))
        assertTrue(count("Share the details…") > 0)
    }
}
