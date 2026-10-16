package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditFailureKind
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditRequest
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * POSTEDIT01: no Save while an edit is unconfirmed, the editor can be left
 * while Reddit's authorization is pending, and a success shows the text
 * that was sent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PostEdit01ScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val submitted = mutableListOf<PostEditRequest>()
    private val saved = mutableListOf<String>()
    private var cancels = 0
    private var closes = 0

    private fun show(editState: PostEditUiState) {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first().copy(id = "t3_abc", author = "me", body = "old text")
        composeRule.setContent {
            PostEditScreen(
                post = post,
                state = state,
                editState = editState.copy(accountStableId = account.stableId, postFullname = "t3_abc"),
                onSubmitEdit = { submitted += it },
                onAuthorizeEdit = {},
                onCancelAuthorization = { cancels += 1 },
                onResetEditState = {},
                onSaved = { saved += it },
                onCheckAgain = {},
                onClose = { closes += 1 },
            )
        }
        composeRule.waitForIdle()
    }

    private fun changeBody() {
        composeRule.onNode(hasSetTextAction()).performTextReplacement("changed text")
        composeRule.waitForIdle()
    }

    @Test
    fun saveStaysOffWhileAnEditIsUnconfirmed() {
        show(PostEditUiState(phase = PostEditPhase.FAILED, failureKind = PostEditFailureKind.OUTCOME_UNKNOWN))
        changeBody()

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        assertEquals(0, submitted.size)
    }

    @Test
    fun saveIsOfferedAgainOnceTheEditIsKnownNotSaved() {
        show(PostEditUiState(phase = PostEditPhase.FAILED, failureKind = PostEditFailureKind.NOT_SAVED, retryable = true))
        changeBody()

        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun cancelAuthorizationIsOfferedWhileWaitingForReddit() {
        show(PostEditUiState(phase = PostEditPhase.AWAITING_CALLBACK))

        composeRule.onNodeWithText("Cancel authorization").performScrollTo().performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun closingWhileWaitingForAuthorizationCancelsIt() {
        show(PostEditUiState(phase = PostEditPhase.AUTHORIZING))

        composeRule.onNodeWithContentDescription("Close post editor").assertIsEnabled().performClick()

        assertEquals(1, cancels)
        assertEquals(1, closes)
    }

    @Test
    fun closingStaysOffWhileTheEditIsBeingSent() {
        show(PostEditUiState(phase = PostEditPhase.SENDING))

        composeRule.onNodeWithContentDescription("Close post editor").assertIsNotEnabled()
    }

    @Test
    fun aSuccessShowsTheTextThatWasSentNotTheEditorText() {
        show(PostEditUiState(phase = PostEditPhase.SUCCEEDED, completionRevision = 1L, savedText = "sent text"))

        assertEquals(listOf("sent text"), saved)
    }
}
