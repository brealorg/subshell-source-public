package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE101_LINK_FOCUS_ONCE_V1: one Add link tap focuses the link field once;
 * a row appearing above it later does not pull focus back from the body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue101LinkFocusOnceComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val linkField = hasSetTextAction() and hasText("Destination URL", substring = true)
    private val bodyField = hasSetTextAction() and hasText("Body text", substring = true)

    // The focus effect waits (scroll, delay); waitForIdle alone does not run it.
    private fun settle() {
        repeat(5) {
            composeRule.mainClock.advanceTimeBy(200)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun aRowAppearingAboveTheLinkFieldDoesNotPullFocusBackFromTheBody() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(community = "subshell", title = "A link", body = "Body text"),
        )
        composeRule.setContent { SubshellTheme { CreatePostScreen(state = state) } }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("add-content")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add link").performClick()
        settle()
        composeRule.onNode(linkField and isFocused()).assertExists()

        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("body")
        composeRule.waitForIdle()
        composeRule.onNode(bodyField).performClick()
        settle()
        composeRule.onNode(bodyField and isFocused()).assertExists()

        // A flair makes the flair row appear above the link field (its index moves).
        state.updatePostComposerDraft(
            state.postComposerDraft.copy(flair = ComposerFlair("flair-1", "News")),
        )
        settle()

        composeRule.onNode(linkField and isFocused()).assertDoesNotExist()
        composeRule.onNode(bodyField and isFocused()).assertExists()
    }
}
