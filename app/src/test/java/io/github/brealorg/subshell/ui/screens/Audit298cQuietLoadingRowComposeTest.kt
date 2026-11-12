package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
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
 * AUDIT298C_LOADING_ROW_STAYS_QUIET_V1 (owner: "veldig mye loading more
 * comments, slitsomt å lese"): an automatic load is a 1dp row for half a
 * second (most finish by then) and says "Loading replies…" only after that; a
 * Retry the reader taps says it at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cQuietLoadingRowComposeTest {
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
    private val post = SampleData.posts.first().copy(id = "t3_p300", comments = 3)
    private val node = Comment(
        id = "more_q300", author = "", age = "", body = "", score = 0, depth = 1, accent = 0,
        parentId = "c300", moreChildIds = listOf("r300"), moreCount = 1,
    )
    private fun top(children: List<Comment>) = Comment(
        id = "c300", author = "someone", age = "1h", body = "Comment c300", score = 1, depth = 0,
        accent = 0xFF336699, children = children,
    )
    private var comments by mutableStateOf(RedditCommentsUiState())

    private fun thread(loading: Boolean, failed: Boolean = false) = RedditCommentsUiState(
        accountStableId = account.stableId,
        postId = post.id,
        sort = RedditCommentSort.BEST,
        comments = listOf(top(listOf(node))),
        fullDiscussionLoaded = true,
        loadingMoreNodeId = node.id.takeIf { loading },
        failedMoreNodeId = node.id.takeIf { failed },
        contentRevision = 1,
    )

    private fun loadingRows() = composeRule.onAllNodesWithText("Loading replies…").fetchSemanticsNodes().size

    private fun show(initial: RedditCommentsUiState): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        comments = initial
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, _, _ ->
                        comments = thread(loading = true)
                        true
                    },
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        return state
    }

    @Test
    fun anAutomaticLoadSaysNothingForHalfASecond() {
        show(thread(loading = false))
        // The node is on screen and loads by itself.
        repeat(4) { composeRule.mainClock.advanceTimeBy(50); composeRule.waitForIdle() }
        assertEquals("quiet at first", 0, loadingRows())
        repeat(14) { composeRule.mainClock.advanceTimeBy(50); composeRule.waitForIdle() }
        assertEquals("a slow load says so", 1, loadingRows())
    }

    @Test
    fun aRetryTheReaderTapsSaysItAtOnce() {
        show(thread(loading = false, failed = true))
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText("Couldn't load replies · Retry").performSemanticsAction(SemanticsActions.OnClick)
        // Inside the quiet time an automatic load would keep.
        // In frames, as a phone draws them (one jump of 450 ms drew nothing between).
        repeat(9) { composeRule.mainClock.advanceTimeBy(50); composeRule.waitForIdle() }
        assertEquals(1, loadingRows())
    }
}
