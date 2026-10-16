package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE94_FAILED_NODES_WAIT_FOR_RETRY_V1: with two "more replies" nodes on
 * screen and a failure that keeps happening, automatic loading alternated
 * A, B, A, B … with no pause; each node that failed now waits for its Retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2400dp")
// Tall enough that both "more replies" rows are on screen at once.
class Issue94FailingNodesComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(RetryOnRobolectricRendererFlake())
        .around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val post: Post = SampleData.posts.first().copy(id = "t3_p94", comments = 4)

    private fun more(id: String, parent: String) = Comment(
        id = id,
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = parent,
        moreChildIds = listOf("x_$id"),
        moreCount = 1,
    )

    private fun top(id: String, child: Comment) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
        children = listOf(child),
    )

    private var comments by mutableStateOf(RedditCommentsUiState())
    private val loads = mutableListOf<String>()

    @Test
    fun twoFailingNodesAreTriedOnceEachAndOfferRetry() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var sort = RedditCommentSort.BEST
        fun thread(failed: String?) = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = sort,
            comments = listOf(top("cA", more("mA", "cA")), top("cB", more("mB", "cB"))),
            fullDiscussionLoaded = true,
            failedMoreNodeId = failed,
            contentRevision = 1,
        )
        comments = thread(failed = null)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    // Offline: every node fails at once.
                    onLoadMoreRedditComments = { _, _, requestedSort, nodeId ->
                        sort = requestedSort
                        loads += nodeId
                        comments = thread(failed = nodeId)
                        true
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()

        assertEquals(listOf("mA", "mB"), loads.sorted())
        assertEquals(2, composeRule.onAllNodesWithText("Couldn't load replies · Retry").fetchSemanticsNodes().size)
    }
}
