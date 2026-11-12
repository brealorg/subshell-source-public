package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
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
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT298C_BANNER_RETRY_KEEPS_THE_LINKED_COMMENT_V1: on a thread opened from
 * a comment link, a failed refresh shows the "showing saved comments" banner;
 * its Retry reloaded the plain thread, which need not hold the linked comment
 * (it came from the link's own context), so the comment could vanish. Pull to
 * refresh and the status row already kept it; the banner now does the same.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cBannerRetryKeepsLinkComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298b", comments = 2)
    private val targetId = "c298linked"
    private fun comment(id: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
    )

    @Test
    fun bannerRetryOnALinkedThreadReloadsWithTheLinkedComment() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val noticeHost = ViewportErrorHostState()
        val plainReloads = mutableListOf<String>()
        val keptReloads = mutableListOf<String>()
        // The thread with the linked comment is shown; the last refresh failed.
        val shown = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = RedditCommentSort.BEST,
            comments = listOf(comment("c298other"), comment(targetId)),
            loading = false,
            failureKind = RedditCommentUiFailureKind.TRANSPORT,
            failureRetryable = true,
            contentRevision = 3L,
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
                    redditCommentsState = shown,
                    onRetryRedditComments = { _, postId, _ -> plainReloads += postId },
                    onRefreshRedditCommentsPreservingFocusedTarget = { _, _, _, keep -> keptReloads += keep },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val banner = noticeHost.currentRequest
        assertNotNull("the saved-comments banner is shown", banner)
        // The landing's own refresh of the linked thread is not the banner's.
        val keptBefore = keptReloads.size
        val plainBefore = plainReloads.size
        composeRule.runOnIdle { noticeHost.retryCurrent() }
        composeRule.waitForIdle()

        assertEquals(listOf(targetId), keptReloads.drop(keptBefore))
        assertEquals(emptyList<String>(), plainReloads.drop(plainBefore))
    }
}
