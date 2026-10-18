package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OCB581_REFUSED_LOAD_IS_NOT_AWAITED_V1: a comment-link landing asked to load
 * the "more replies" node holding its target; a refused load changed nothing,
 * yet the loop waited 2.5 s and asked again, up to eight times, with scrolling
 * locked. The refusal now ends that node for this landing at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Ocb581RefusedLoadComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p581", comments = 3)
    private val targetId = "c581target"

    private val thread = listOf(
        Comment(
            id = "c581top",
            author = "someone",
            age = "1h",
            body = "Comment c581top",
            score = 1,
            depth = 0,
            accent = 0xFF336699,
            children = listOf(
                Comment(
                    id = "more_m581",
                    author = "",
                    age = "",
                    body = "",
                    score = 0,
                    depth = 1,
                    accent = 0,
                    parentId = "c581top",
                    moreChildIds = listOf(targetId),
                    moreCount = 1,
                ),
            ),
        ),
    )

    private val noticeHost = ViewportErrorHostState()
    private var comments by mutableStateOf(RedditCommentsUiState())
    private var revision = 0L
    private val loadMoreRequests = mutableListOf<String>()

    @Test
    fun aRefusedLoadIsAskedOnceAndTheLandingEndsWithoutWaiting() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        var sort = RedditCommentSort.BEST
        fun answer() {
            revision += 1
            comments = RedditCommentsUiState(
                accountStableId = account.stableId,
                postId = post.id,
                sort = sort,
                comments = thread,
                contentRevision = revision,
            )
        }
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(
                LocalVideoPlaybackCoordinator provides coordinator,
                LocalViewportErrorHostState provides noticeHost,
            ) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    routeFocusedCommentId = targetId,
                    redditCommentsState = comments,
                    onLoadCachedRedditComments = { _, _, requestedSort ->
                        sort = requestedSort
                        answer()
                    },
                    // The focused read answers without the target, under a more node.
                    onLoadFocusedRedditComment = { _, _, requestedSort, _ ->
                        sort = requestedSort
                        answer()
                    },
                    // The load is refused: nothing starts, nothing changes.
                    onLoadMoreRedditComments = { _, _, _, nodeId ->
                        loadMoreRequests += nodeId
                        false
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        assertEquals(listOf("more_m581"), loadMoreRequests)
        assertEquals("Couldn't find this comment", noticeHost.currentRequest?.presentation?.title)
    }
}
