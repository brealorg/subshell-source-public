package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostEditPhase
import app.subshell.prototype.data.account.PostEditUiState
import app.subshell.prototype.ui.SubshellState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * POSTEDIT02: while an edit is authorizing, sending or checking, the body and
 * the formatting dock are locked; the text already belongs to the request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PostEdit02EditorLockComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun show(phase: PostEditPhase) {
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
                editState = PostEditUiState(phase = phase, accountStableId = account.stableId, postFullname = "t3_abc"),
                onSubmitEdit = {},
                onAuthorizeEdit = {},
                onCancelAuthorization = {},
                onResetEditState = {},
                onSaved = {},
                onCheckAgain = {},
                onClose = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun typeIntoBody() {
        composeRule.onNode(hasSetTextAction()).performTextReplacement("typed while sending")
        composeRule.waitForIdle()
    }

    @Test
    fun theBodyIgnoresTypingWhileTheEditIsSent() {
        show(PostEditPhase.SENDING)
        typeIntoBody()

        composeRule.onNode(hasSetTextAction()).assertTextContains("old text")
    }

    @Test
    fun theBodyIgnoresTypingWhileWaitingForAuthorization() {
        show(PostEditPhase.AWAITING_CALLBACK)
        typeIntoBody()

        composeRule.onNode(hasSetTextAction()).assertTextContains("old text")
    }

    @Test
    fun theFormattingDockIsOffWhileTheEditIsChecked() {
        show(PostEditPhase.CHECKING)

        composeRule.onNodeWithContentDescription("Bold").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("More formatting").assertIsNotEnabled()
    }

    @Test
    fun theEditorStaysEditableWhenIdle() {
        show(PostEditPhase.IDLE)
        typeIntoBody()

        composeRule.onNode(hasSetTextAction()).assertTextContains("typed while sending")
        composeRule.onNodeWithContentDescription("Bold").assertIsEnabled()
    }
}
