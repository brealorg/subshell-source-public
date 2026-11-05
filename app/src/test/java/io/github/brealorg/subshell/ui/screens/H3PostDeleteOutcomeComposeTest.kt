package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.PostDeleteFailureKind
import io.github.brealorg.subshell.data.account.PostDeletePhase
import io.github.brealorg.subshell.data.account.PostDeleteUiState
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.SubshellState
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
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
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
