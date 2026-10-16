package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.continueThreadNode
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue #3: a continue node is never a "Continue this thread" row to tap; once
 * it is on screen it loads by itself, like every other more-replies node
 * (owner: no load-more rows in Subshell).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Issue3ContinueThreadComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aContinueNodeOnScreenLoadsByItselfWithoutARow() {
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
        val node = continueThreadNode("t1_deep", depth = 1, accent = 0L)
        val deep = Comment(
            id = "t1_deep", author = "someone", age = "1h", body = "Deep comment", score = 1, depth = 0,
            accent = 0L, children = listOf(node),
        )
        val loads = mutableListOf<String>()
        val coordinator = VideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
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
                        comments = listOf(deep),
                        fullDiscussionLoaded = true,
                    ),
                    onLoadMoreRedditComments = { _, _, _, nodeId -> loads += nodeId; true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasScrollToKeyAction()).onFirst().performScrollToKey("more:${node.id}")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(listOf(node.id), loads.distinct())
        assertEquals(0, composeRule.onAllNodesWithText("Continue this thread").fetchSemanticsNodes().size)
    }
}
