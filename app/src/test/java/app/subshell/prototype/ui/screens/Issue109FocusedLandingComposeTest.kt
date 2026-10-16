package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.RedditCommentSort
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.model.Post
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE109_LANDING_WAITS_FOR_THE_REQUEST_IN_FLIGHT_V1 /
 * ISSUE109_FAILED_LANDING_SAYS_SO_V1: opening a comment link gave each step
 * 2.5 s; when Reddit answered later (mobile data) the landing had already
 * given up and showed the thread from the top, without saying so.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue109FocusedLandingComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p109", comments = 2)
    private val targetId = "c109target"
    private fun comment(id: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
    )

    private val noticeHost = ViewportErrorHostState()
    private var comments by mutableStateOf(RedditCommentsUiState())
    private var sort: RedditCommentSort = RedditCommentSort.BEST
    private var revision = 0L
    private val locatorRequests = mutableListOf<String>()
    private val landed = mutableListOf<String>()

    private fun answer(loading: Boolean, withTarget: Boolean) {
        revision += 1
        comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = sort,
            comments = listOf(comment("c109other")) + if (withTarget) listOf(comment(targetId)) else emptyList(),
            loading = loading,
            contentRevision = revision,
        )
    }

    private fun openTheCommentLink() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val coordinator = VideoPlaybackCoordinator(context)
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
                    // Nothing cached for this thread.
                    onLoadCachedRedditComments = { _, _, requestedSort ->
                        sort = requestedSort
                        answer(loading = false, withTarget = false)
                    },
                    // Reddit takes its time: the request stays in flight.
                    onLoadFocusedRedditComment = { _, _, requestedSort, target ->
                        sort = requestedSort
                        locatorRequests += target
                        answer(loading = true, withTarget = false)
                    },
                    onFocusedCommentLandingVerified = { id -> landed += id },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitMs(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        composeRule.waitForIdle()
    }

    @Test
    fun aSlowAnswerStillLandsOnTheComment() {
        openTheCommentLink()
        waitMs(6_000)
        assertEquals(listOf(targetId), locatorRequests)
        assertNull(noticeHost.currentRequest)

        composeRule.runOnIdle { answer(loading = false, withTarget = true) }
        // The landing then waits for the full discussion to settle (4 s at most).
        waitMs(10_000)

        assertEquals(listOf(targetId), landed)
        assertNull(noticeHost.currentRequest)
    }

    @Test
    fun aCommentThatIsNotThereSaysSoAndRetryLooksAgain() {
        openTheCommentLink()
        waitMs(3_000)
        composeRule.runOnIdle { answer(loading = false, withTarget = false) }
        waitMs(6_000)

        assertEquals("Couldn't find this comment", noticeHost.currentRequest?.presentation?.title)

        composeRule.runOnIdle { noticeHost.retryCurrent() }
        waitMs(3_000)

        assertEquals(listOf(targetId, targetId), locatorRequests)
        assertNull(noticeHost.currentRequest)
    }
}
