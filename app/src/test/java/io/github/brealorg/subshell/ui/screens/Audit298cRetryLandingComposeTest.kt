package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.RedditCommentSort
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentUiFailureKind
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.model.Post
import io.github.brealorg.subshell.ui.SubshellState
import io.github.brealorg.subshell.ui.error.LocalViewportErrorHostState
import io.github.brealorg.subshell.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT298C_RETRY_DOES_NOT_REUSE_THE_OLD_FAILURE_V1: after a landing failed
 * on a network error, Retry asked Reddit again but read the screen's last
 * state (still the old failure, the new "loading" not drawn yet) as the
 * answer and said "Couldn't find this comment" again at once; the real
 * answer that came a moment later was never used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cRetryLandingComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298r", comments = 2)
    private val targetId = "c298target"
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

    private fun answer(
        loading: Boolean,
        withTarget: Boolean,
        failure: RedditCommentUiFailureKind? = null,
        bumpRevision: Boolean = true,
    ) {
        if (bumpRevision) revision += 1
        comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = sort,
            comments = listOf(comment("c298other")) + if (withTarget) listOf(comment(targetId)) else emptyList(),
            loading = loading,
            failureKind = failure,
            failureRetryable = failure != null,
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
                    // Nothing cached for this thread.
                    onLoadCachedRedditComments = { _, _, requestedSort ->
                        sort = requestedSort
                        answer(loading = false, withTarget = false)
                    },
                    // Reddit takes its time: the request is in flight.
                    onLoadFocusedRedditComment = { _, _, requestedSort, target ->
                        sort = requestedSort
                        locatorRequests += target
                        answer(loading = true, withTarget = false, bumpRevision = false)
                    },
                    onFocusedCommentLandingVerified = { id -> landed += id },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitMs(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 50L)
            composeRule.mainClock.advanceTimeBy(step)
            composeRule.waitForIdle()
            left -= step
        }
    }

    @Test
    fun retryAfterANetworkFailureWaitsForTheNewAnswer() {
        openTheCommentLink()
        waitMs(1_000)
        // The first answer: the connection failed.
        composeRule.runOnIdle {
            answer(loading = false, withTarget = false, failure = RedditCommentUiFailureKind.TRANSPORT)
        }
        waitMs(6_000)
        assertEquals("Couldn't find this comment", noticeHost.currentRequest?.presentation?.title)
        assertEquals(listOf(targetId), locatorRequests)

        composeRule.runOnIdle { noticeHost.retryCurrent() }
        waitMs(1_000)
        assertEquals(listOf(targetId, targetId), locatorRequests)
        assertNull("Retry must wait for Reddit, not reuse the old failure", noticeHost.currentRequest)

        // Reddit answers with the comment this time.
        composeRule.runOnIdle { answer(loading = false, withTarget = true) }
        waitMs(10_000)

        assertEquals(listOf(targetId), landed)
        assertNull(noticeHost.currentRequest)
    }
}
