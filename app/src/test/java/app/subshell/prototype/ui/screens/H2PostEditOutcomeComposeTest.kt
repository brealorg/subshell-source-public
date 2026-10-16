package app.subshell.prototype.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * H2: an unconfirmed post edit says so and offers Check again, which only
 * reads; a confirmed "not saved" offers Retry, which saves again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class H2PostEditOutcomeComposeTest {
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
    private var checks = 0

    private fun show(kind: PostEditFailureKind, retryable: Boolean) {
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
                editState = PostEditUiState(
                    phase = PostEditPhase.FAILED,
                    accountStableId = account.stableId,
                    postFullname = "t3_abc",
                    failureKind = kind,
                    retryable = retryable,
                ),
                onSubmitEdit = { submitted += it },
                onAuthorizeEdit = {},
                onCancelAuthorization = {},
                onResetEditState = {},
                onSaved = {},
                onCheckAgain = { checks += 1 },
                onClose = {},
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUnconfirmedEditOffersCheckAgainWhichOnlyReads() {
        show(PostEditFailureKind.OUTCOME_UNKNOWN, retryable = false)

        composeRule.onNodeWithText("Edit not confirmed").assertExists()
        composeRule.onNodeWithText("Check again").performScrollTo().performClick()

        assertEquals(1, checks)
        assertEquals(0, submitted.size)
    }

    @Test
    fun aConfirmedNotSavedEditOffersRetryWhichSavesAgain() {
        show(PostEditFailureKind.NOT_SAVED, retryable = true)

        composeRule.onNodeWithText("Couldn't edit post").assertExists()
        composeRule.onNodeWithText("Retry").performScrollTo().performClick()

        assertEquals(0, checks)
        assertEquals(1, submitted.size)
    }
}
