package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CommentEditPhase
import app.subshell.prototype.data.account.CommentEditUiState
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE92_COMMENT_EDIT_LOCKED_WHILE_SAVING_V1: while an edit is being sent
 * (or authorized or checked) its text and formatting are locked, like the
 * post editor, so nothing typed after Save is silently lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue92CommentEditLockedComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val comment = Comment(
        id = "t1_abc",
        author = "me",
        age = "1h",
        body = "old text",
        score = 1,
        depth = 0,
        accent = 0xFF000000,
    )

    @Test
    fun whileSendingTheEditorAndTheFormattingBarAreLocked() {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first()
        composeRule.setContent {
            CommentEditScreen(
                post = post,
                comment = comment,
                state = state,
                commentSort = RedditCommentSort.BEST,
                editState = CommentEditUiState(
                    phase = CommentEditPhase.SENDING,
                    accountStableId = account.stableId,
                    postId = post.id,
                    commentFullname = "t1_abc",
                ),
                onSubmitEdit = {},
                onAuthorizeEdit = {},
                onCancelAuthorization = {},
                onResetEditState = {},
                onSaved = {},
                onClose = {},
            )
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasContentDescription("Bold")).onFirst().assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput(" typed after Save")
        composeRule.waitForIdle()

        val text = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        assertEquals("old text", text)
    }
}
