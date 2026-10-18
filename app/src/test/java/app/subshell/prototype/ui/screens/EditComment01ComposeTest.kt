package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * EDITCOMMENT01_EDIT_ONLY_A_REDDIT_COMMENT_V1: the comment editor needs a
 * Reddit comment ID and draws nothing without one, which left an empty screen.
 * An own comment without one offers no Edit; one with a Reddit ID still does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditComment01ComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun editOfferedFor(commentId: String): Int {
        ensureWorkManagerInitialized(ApplicationProvider.getApplicationContext<Application>())
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "me",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = state.posts.first()
        val mine = Comment(id = commentId, author = "me", age = "1h", body = "My comment", score = 1, depth = 0, accent = 0L)
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = RedditCommentsUiState(
                        accountStableId = account.stableId,
                        postId = post.id,
                        sort = RedditCommentSort.BEST,
                        comments = listOf(mine),
                        fullDiscussionLoaded = true,
                    ),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("comment:$commentId")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Comment options").onFirst().performClick()
        composeRule.waitForIdle()
        return composeRule.onAllNodesWithText("Edit comment").fetchSemanticsNodes().size
    }

    @Test
    fun aCommentWithoutARedditIdOffersNoEdit() {
        assertEquals(0, editOfferedFor("local-comment-p1-1"))
    }

    @Test
    fun aRedditCommentStillOffersEdit() {
        assertEquals(1, editOfferedFor("t1_mine"))
    }
}
