package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.PostDeleteFailureKind
import app.subshell.prototype.data.account.PostDeletePhase
import app.subshell.prototype.data.account.PostDeleteUiState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * H3: an unconfirmed post delete says so and offers Check again, which only
 * reads; a confirmed "not deleted" offers Retry, which deletes again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class H3PostDeleteOutcomeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "me",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private var deletes = 0
    private var checks = 0

    private fun show(kind: PostDeleteFailureKind, retryable: Boolean) {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first().copy(id = "t3_abc", author = "me")
        state.requestPostDelete(post.id)
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    postDeleteState = PostDeleteUiState(
                        phase = PostDeletePhase.FAILED,
                        accountStableId = account.stableId,
                        postFullname = "t3_abc",
                        failureKind = kind,
                        retryable = retryable,
                    ),
                    onDeletePost = { deletes += 1 },
                    onCheckPostDelete = { checks += 1 },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUnconfirmedDeleteOffersCheckAgainWhichOnlyReads() {
        show(PostDeleteFailureKind.OUTCOME_UNKNOWN, retryable = false)

        assertTrue(composeRule.onAllNodesWithText("Deletion not confirmed").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Check again").performClick()

        assertEquals(1, checks)
        assertEquals(0, deletes)
    }

    @Test
    fun aConfirmedNotDeletedOffersRetryWhichDeletesAgain() {
        show(PostDeleteFailureKind.NOT_DELETED, retryable = true)

        composeRule.onNodeWithText("Reddit didn't delete the post. You can try again.").assertExists()
        composeRule.onNodeWithText("Retry").performClick()

        assertEquals(0, checks)
        assertEquals(1, deletes)
    }
}
