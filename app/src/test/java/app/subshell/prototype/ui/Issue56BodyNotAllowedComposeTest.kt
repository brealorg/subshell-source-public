package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CreatePostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE56_BODY_NOT_ALLOWED_VISIBLE_V1: in a community that allows no body,
 * a body already in the draft is shown read-only with the reason and a way
 * to remove it (it used to be hidden while it blocked Post).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue56BodyNotAllowedComposeTest {
    private val composeRule = createComposeRule()

    // The Robolectric "lightZ ... Infinity" display flake hit this class once
    // (2026-10-03); the guard restores the display before the test.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val noBody = ComposerPolicySnapshot(
        community = "subshell",
        status = ComposerPolicyStatus.LOADED,
        policy = ComposerCommunityPolicy.local("subshell").copy(bodyAllowed = false),
    )

    private fun count(text: String) =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun aBodyTheCommunityDoesNotAllowIsShownWithTheReasonAndCanBeRemoved() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(
            PostComposerDraft(community = "subshell", title = "A post", body = "Shared text"),
        )
        composeRule.setContent {
            SubshellTheme { CreatePostScreen(state = state, composerPolicy = noBody) }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("body")
        composeRule.waitForIdle()

        assertEquals(1, count("Shared text"))
        assertEquals(1, count(COMPOSER_BODY_NOT_ALLOWED_MESSAGE))

        composeRule.onNodeWithText("Remove body text").performClick()
        composeRule.waitForIdle()

        assertEquals("", state.postComposerDraft.body)
        assertEquals(0, count("Shared text"))
        assertEquals(0, count("Remove body text"))
    }

    @Test
    fun withoutABodyNothingIsShownForIt() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val state = SubshellState(
            initialCommentActionPlacement = CommentActionPlacement.RIGHT,
            initialInteractionSideConfigured = true,
        )
        state.openPostComposer()
        state.updatePostComposerDraft(PostComposerDraft(community = "subshell", title = "A post"))
        composeRule.setContent {
            SubshellTheme { CreatePostScreen(state = state, composerPolicy = noBody) }
        }
        composeRule.waitForIdle()

        assertEquals(0, count("Body"))
        assertEquals(0, count("Remove body text"))
    }
}
