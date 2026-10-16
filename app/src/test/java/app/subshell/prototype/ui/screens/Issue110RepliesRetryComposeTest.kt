package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
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
 * ISSUE110_REPLIES_RETRY_PUBLISHES_IN_VIEW_V1: Retry on "Couldn't load
 * replies" took the manual hydration path, which publishes only after the row
 * has left the screen; the tapped row stayed on screen and spun on "Loading
 * replies…" with the answer withheld.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue110RepliesRetryComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p110", comments = 2)

    private fun comment(id: String, depth: Int = 0, children: List<Comment> = emptyList()) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = depth,
        accent = 0xFF336699,
        children = children,
    )

    private val moreNode = Comment(
        id = "more_m110",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = "c110top",
        moreChildIds = listOf("c110reply"),
        moreCount = 1,
    )

    private var comments by mutableStateOf(RedditCommentsUiState())
    private val loadMoreRequests = mutableListOf<String>()

    @Test
    fun retryShowsTheRepliesWhileTheRowIsOnScreen() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var sort = RedditCommentSort.BEST
        fun thread(revision: Long, replies: List<Comment>, failed: Boolean, loadingMore: Boolean) =
            RedditCommentsUiState(
                accountStableId = account.stableId,
                postId = post.id,
                sort = sort,
                comments = listOf(comment("c110top", children = replies)),
                fullDiscussionLoaded = true,
                failedMoreNodeId = moreNode.id.takeIf { failed },
                loadingMoreNodeId = moreNode.id.takeIf { loadingMore },
                contentRevision = revision,
            )
        comments = thread(revision = 1, replies = listOf(moreNode), failed = true, loadingMore = false)
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, requestedSort, nodeId ->
                        sort = requestedSort
                        loadMoreRequests += nodeId
                        comments = thread(revision = 1, replies = listOf(moreNode), failed = false, loadingMore = true)
                        true
                    },
                )
            }
        }
        composeRule.waitForIdle()

        // The comment bar overlaps the row at the bottom of this window; use
        // the row's click action instead of a pointer tap.
        // LOWUI7_TOUCH_TARGETS_ARE_48DP_V1 (OCB-244): a 48dp button.
        val retryRow = composeRule.onNodeWithText("Couldn't load replies · Retry")
        retryRow.assertHeightIsAtLeast(48.dp)
        assertEquals(Role.Button, retryRow.fetchSemanticsNode().config[SemanticsProperties.Role])
        retryRow.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(moreNode.id), loadMoreRequests)

        // Reddit answers at once; nobody scrolls.
        composeRule.runOnIdle {
            comments = thread(
                revision = 2,
                replies = listOf(comment("c110reply", depth = 1)),
                failed = false,
                loadingMore = false,
            )
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(1, composeRule.onAllNodesWithText("Comment c110reply").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Loading replies…").fetchSemanticsNodes().size)
    }
}
