package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentWritePhase
import app.subshell.prototype.data.account.CommentWriteRequest
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

/**
 * ISSUE91_LOCKED_WHILE_OUTCOME_UNKNOWN_V1: while a comment's outcome is
 * unknown its text cannot change, so "Check again" sends the same comment
 * (which the writer reads back) and never an edited one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue91CheckAgainLockedComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // A Reddit post id, so the comment has a real parent.
    private val post = SampleData.posts.first().copy(id = "t3_abc123")

    @Test
    fun checkAgainSendsTheLostCommentUnchangedAndTheFieldIsLocked() {
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
        state.updateCommentComposerDraft(requireNotNull(state.commentComposerDraft).copy(body = "Great post"))
        val target = requireNotNull(state.commentComposerDraft).target
        val sent = mutableListOf<CommentWriteRequest>()
        composeRule.setContent {
            SubshellTheme {
                CommentComposerScreen(
                    post = post,
                    state = state,
                    onClose = {},
                    commentWriteState = CommentWriteUiState(
                        phase = CommentWritePhase.OUTCOME_UNKNOWN,
                        accountStableId = alice.stableId,
                        postId = post.id,
                        targetStableKey = target.stableKey,
                    ),
                    onSubmitComment = { sent += it },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Image").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("GIF").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("!")
        composeRule.waitForIdle()
        assertEquals("Great post", state.commentComposerDraft?.body)

        composeRule.onNodeWithText("Check again").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Great post"), sent.map(CommentWriteRequest::text))
    }
}
