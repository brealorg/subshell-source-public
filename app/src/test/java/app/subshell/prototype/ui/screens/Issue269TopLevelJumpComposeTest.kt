package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE269_ROWS_UNDER_THE_HEADER_ARE_NOT_ON_SCREEN_V1 (#269, reader report):
 * jumping to the next top-level comment left the previous comment's idle
 * "more replies" row just above it, hidden under the stuck sort header. It
 * counted as on screen, loaded as soon as the jump stopped and the new
 * replies pushed the comment jumped to off the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue269TopLevelJumpComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p269", comments = 40)

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
        id = "more_m269",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 1,
        accent = 0,
        parentId = "c269x",
        moreChildIds = listOf("c269late1", "c269late2"),
        moreCount = 2,
    )

    private var comments by mutableStateOf(RedditCommentsUiState())
    private val loadMoreRequests = mutableListOf<String>()

    private fun thread(xReplies: List<Comment>) = RedditCommentsUiState(
        accountStableId = account.stableId,
        postId = post.id,
        sort = RedditCommentSort.BEST,
        comments = listOf(
            comment("c269x", children = xReplies),
            comment("c269y"),
            comment("c269z", children = (1..15).map { comment("c269z$it", depth = 1) }),
        ),
        fullDiscussionLoaded = true,
        contentRevision = if (xReplies.contains(moreNode)) 1 else 2,
    )

    @Test
    fun jumpingPastRepliesDoesNotLoadTheRowHiddenUnderTheHeader() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTopLevelCommentJumpButton(true)
        val xReplies = (1..15).map { comment("c269x$it", depth = 1) }
        comments = thread(xReplies + moreNode)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, _, nodeId ->
                        loadMoreRequests += nodeId
                        // Reddit's answer: two late replies where the node was.
                        comments = thread(xReplies + comment("c269late1", depth = 1) + comment("c269late2", depth = 1))
                        true
                    },
                )
            }
        }
        composeRule.waitForIdle()

        val next = composeRule.onNodeWithContentDescription(TOP_LEVEL_COMMENT_JUMP_NEXT_LABEL)
        next.performClick() // to X
        composeRule.waitForIdle()
        next.performClick() // past X's replies to Y
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals("the hidden row is not loaded by the jump", emptyList<String>(), loadMoreRequests)
        val y = composeRule.onNodeWithText("Comment c269y")
        y.assertIsDisplayed()
        val yTop = y.getBoundsInRoot().top
        assertTrue("Y stays near the top (top=$yTop)", yTop < 300.dp)
    }
}
