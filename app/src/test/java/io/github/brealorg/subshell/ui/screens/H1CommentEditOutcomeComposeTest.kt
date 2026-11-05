package io.github.brealorg.subshell.ui.screens

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CommentEditFailureKind
import io.github.brealorg.subshell.data.account.CommentEditPhase
import io.github.brealorg.subshell.data.account.CommentEditRequest
import io.github.brealorg.subshell.data.account.CommentEditUiState
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * H1: an unconfirmed comment edit says so and offers Check again, which only
 * reads; a confirmed "not saved" offers Retry, which saves again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class H1CommentEditOutcomeComposeTest {
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
    private val submitted = mutableListOf<CommentEditRequest>()
    private var checks = 0

    private fun show(kind: CommentEditFailureKind, retryable: Boolean) {
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
                    phase = CommentEditPhase.FAILED,
                    accountStableId = account.stableId,
                    postId = post.id,
                    commentFullname = "t1_abc",
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
        show(CommentEditFailureKind.OUTCOME_UNKNOWN, retryable = false)

        composeRule.onNodeWithText("Edit not confirmed").assertExists()
        composeRule.onNodeWithText("Check again").performClick()

        assertEquals(1, checks)
        assertEquals(0, submitted.size)
    }

    @Test
    fun aConfirmedNotSavedEditOffersRetryWhichSavesAgain() {
        show(CommentEditFailureKind.NOT_SAVED, retryable = true)

        composeRule.onNodeWithText("Couldn't edit comment").assertExists()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(0, checks)
        assertEquals(1, submitted.size)
    }
}
