package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteUiState
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.screens.CommentComposerScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ui review round 3 C1/C7 (COMPOSER01). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Composer01ReplyDraftComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val post = SampleData.posts.first()

    // COMPOSER01_REPLY_TEXT_KEYED_ON_DRAFT_V1
    @Test
    fun discardAndReplyStartsTheNewReplyEmpty() {
        val state = SubshellState(initialCommentActionPlacement = CommentActionPlacement.RIGHT)
        val comment = state.commentsForPost(post).first()
        state.openCommentComposer(post)
        composeRule.setContent {
            SubshellTheme { CommentComposerScreen(post = post, state = state, onClose = {}) }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("discard me")
        composeRule.waitForIdle()
        assertEquals("discard me", state.commentComposerDraft?.body)

        // What "Discard and reply" does, in one click.
        composeRule.runOnIdle {
            state.discardCommentComposerDraft()
            state.openCommentComposer(post, comment)
        }
        composeRule.waitForIdle()

        assertEquals(commentReplyTarget(post, comment).stableKey, state.commentComposerDraft?.target?.stableKey)
        assertEquals("", state.commentComposerDraft?.body)
        assertEquals(0, composeRule.onAllNodesWithText("discard me").fetchSemanticsNodes().size)
    }

    // COMPOSER01_INPUT_LOCKED_WHILE_SENDING_V1
    @Test
    fun theReplyIsLockedWhileItIsBeingSent() {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice,
            initialActiveAccountId = alice.stableId,
        )
        state.openCommentComposer(post)
        val target = requireNotNull(state.commentComposerDraft).target
        val writeState = androidx.compose.runtime.mutableStateOf(CommentWriteUiState())
        composeRule.setContent {
            SubshellTheme {
                CommentComposerScreen(
                    post = post,
                    state = state,
                    onClose = {},
                    commentWriteState = writeState.value,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Image").assertIsEnabled()

        writeState.value = CommentWriteUiState(
            phase = CommentWritePhase.SENDING,
            accountStableId = alice.stableId,
            postId = post.id,
            targetStableKey = target.stableKey,
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Image").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("GIF").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("typed while sending")
        composeRule.waitForIdle()
        assertEquals("", state.commentComposerDraft?.body)
        assertEquals(0, composeRule.onAllNodesWithText("typed while sending").fetchSemanticsNodes().size)
    }
}
