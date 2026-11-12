package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
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
import io.github.brealorg.subshell.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT298C_EVERY_SCROLL_ARMS_THE_PREFETCH_V1: a scroll without a finger drag
 * (here an accessibility scroll; a jump, Find or the next-comment button the
 * same) arms the prefetch, so the node just below the screen loads before it
 * is reached. Only a drag armed it before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cEveryScrollArmsPrefetchComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun aScrollWithoutAFingerPrefetchesTheNodeBelowTheScreen() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = SampleData.posts.first().copy(id = "t3_p301", comments = 40)
        val roots = (1..30).map { index ->
            Comment(id = "c301_$index", author = "someone", age = "1h", body = "Comment $index", score = 1, depth = 0, accent = 0xFF336699)
        } + Comment(
            id = "more_p301", author = "", age = "", body = "", score = 0, depth = 0, accent = 0,
            parentId = post.id, moreChildIds = listOf("x301"), moreCount = 1,
        )
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = RedditCommentSort.BEST,
            comments = roots,
            fullDiscussionLoaded = true,
            contentRevision = 1,
        )
        val requests = mutableListOf<String>()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, _, nodeId -> requests += nodeId; true },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("nothing near at the top", emptyList<String>(), requests)

        // Rows 20..25 on screen; the node (row 30) waits a few rows below.
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(20 + 3)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(listOf("more_p301"), requests)
    }
}
