package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE298_ROWS_LOADING_ABOVE_THE_READER_STAY_ABOVE_V1 (#298, owner 2026-11-11):
 * a reply opened from the Inbox sits in its thread after a top-level "more"
 * node (the focused chain is grafted after its siblings). Scrolling up brings
 * that node on screen; it loads by itself and puts its comments, with their
 * replies, at its place. Compose keeps only the first visible row, so the
 * comments went in between the top of the screen and the reader's own comment
 * and pushed it hundreds of rows away (logcat on the Pixel: 62 comments in one
 * commit, `targetVisibleBefore=true`). While the reader scrolls up, the row
 * below the node stays where it was; the new comments go above it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue298RowsLoadAboveTheReaderComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298", comments = 400)

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

    private fun moreNode(remaining: List<String>) = Comment(
        id = "more_root298",
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 0,
        accent = 0,
        parentId = post.id,
        moreChildIds = remaining,
        moreCount = remaining.size,
    )

    private val earlier = (1..8).map { index -> comment("c298top$index") }
    private val loadedIds = (1..40).map { index -> "c298new$index" }
    // The reader's comment with the reply chain under it fills most of the
    // screen, as on the Pixel (the reply alone was 628 of 2 041 px).
    private val mine = comment(
        "c298mine",
        children = listOf(
            comment(
                "c298reply",
                depth = 1,
                children = listOf(comment("c298reply2", depth = 2, children = listOf(comment("c298reply3", depth = 3)))),
            ),
        ),
    )

    private var comments by mutableStateOf(RedditCommentsUiState())
    private val loadMoreRequests = mutableListOf<String>()

    private fun thread(revision: Long, roots: List<Comment>, loadingMore: Boolean) = RedditCommentsUiState(
        accountStableId = account.stableId,
        postId = post.id,
        sort = RedditCommentSort.BEST,
        comments = roots,
        fullDiscussionLoaded = true,
        loadingMoreNodeId = "more_root298".takeIf { loadingMore },
        contentRevision = revision,
    )

    private fun topOf(text: String): Float? = composeRule.onAllNodesWithText(text)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.boundsInRoot
        ?.top

    @Test
    fun commentsLoadedAboveWhileScrollingUpDoNotPushTheReadersCommentAway() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val beforeLoad = earlier + moreNode(loadedIds + "c298later") + mine
        comments = thread(revision = 1, roots = beforeLoad, loadingMore = false)
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
                        // One load; this test is about where its rows go, not
                        // about the node loading again (the next test).
                        if (loadMoreRequests.size == 1) {
                            comments = thread(revision = 1, roots = beforeLoad, loadingMore = true)
                        }
                        loadMoreRequests.size == 1
                    },
                )
            }
        }
        composeRule.waitForIdle()

        // Down to the reader's comment and its reply, then a short way back
        // up, so the idle "more" row above the comment is on screen.
        val list = composeRule.onAllNodes(hasScrollAction()).onFirst()
        repeat(6) {
            list.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        list.performTouchInput { swipeDown(startY = centerY - 30f, endY = centerY + 30f) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val mineBefore = topOf("Comment c298mine")
        assertTrue("the reader's comment is on screen before the load", mineBefore != null)
        assertEquals("the idle row loads by itself", listOf("more_root298"), loadMoreRequests.distinct())

        composeRule.runOnIdle {
            comments = thread(
                revision = 2,
                roots = earlier +
                    loadedIds.map { id -> comment(id, children = listOf(comment("${id}r", depth = 1))) } +
                    moreNode(listOf("c298later")) +
                    mine,
                loadingMore = false,
            )
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val mineAfter = topOf("Comment c298mine")
        assertTrue("the reader's comment is still on screen after the load", mineAfter != null)
        assertEquals("the reader's comment does not move", mineBefore!!, mineAfter!!, 2f)
    }

    /**
     * ISSUE298_MORE_LOADS_FROM_THE_END_THE_READER_COMES_FROM_V1: reached from
     * below, the node loads its last comments; they go between it and the
     * reader's comment, the node moves up off the screen and does not load
     * again until the reader reaches it (it loaded a hundred times in a row).
     */
    @Test
    fun aNodeReachedFromBelowLoadsItsLastCommentsAndStepsOutOfTheWay() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val listed = loadedIds + "c298later"
        val beforeLoad = earlier + moreNode(listed) + mine
        comments = thread(revision = 1, roots = beforeLoad, loadingMore = false)
        val fromEndRequests = mutableListOf<String>()
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
                        comments = thread(revision = comments.contentRevision, roots = comments.comments, loadingMore = true)
                        true
                    },
                    onLoadMoreRedditCommentsFromEnd = { _, _, _, nodeId ->
                        fromEndRequests += nodeId
                        comments = thread(revision = comments.contentRevision, roots = comments.comments, loadingMore = true)
                        true
                    },
                )
            }
        }
        composeRule.waitForIdle()
        val list = composeRule.onAllNodes(hasScrollAction()).onFirst()
        repeat(6) {
            list.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        list.performTouchInput { swipeDown(startY = centerY - 30f, endY = centerY + 30f) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val mineBefore = topOf("Comment c298mine")
        assertTrue(mineBefore != null)
        assertEquals("reached from below: the last comments", listOf("more_root298"), fromEndRequests)
        assertEquals("not from the start", emptyList<String>(), loadMoreRequests)

        // The answer: the last 25 listed, between the node and the reader's comment.
        val tail = listed.takeLast(25)
        composeRule.runOnIdle {
            comments = thread(
                revision = 2,
                roots = earlier +
                    moreNode(listed.dropLast(25)) +
                    tail.map { id -> comment(id) } +
                    mine,
                loadingMore = false,
            )
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        assertEquals("the reader's comment does not move", mineBefore!!, topOf("Comment c298mine")!!, 2f)
        assertTrue("the last loaded comment sits just above it", topOf("Comment c298later") != null)
        assertEquals("the node is off the screen and does not load again", 1, fromEndRequests.size + loadMoreRequests.size)
    }
}
