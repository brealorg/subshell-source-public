package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.data.diagnostics.RedditReadDiagnostics
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.SubshellState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE37_FIND_RESUMES_AND_RETRIES_V1 (#37, /code-review of #281): a find
 * paused by Reddit's low rate limit goes on once the limit is back, and a
 * load the screen refused is asked for again; before, both stopped for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue37ThreadFindResumesTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Before
    fun record() {
        RedditReadDiagnostics.installForTest { _, _ -> }
    }

    @After
    fun reset() {
        RedditReadDiagnostics.installForTest(null)
    }

    private fun comment(id: String, body: String) = Comment(
        id = id, author = "someone", age = "1h", body = body, score = 1, depth = 0, accent = 0xFF336699,
    )

    private fun more(id: String) = Comment(
        id = id, author = "", age = "", body = "", score = 0, depth = 0, accent = 0L,
        moreChildIds = listOf("x1", "x2"), moreCount = 2,
    )

    private fun showFind(onLoad: (String) -> Boolean) {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialOpenKeyboardOnSearch = false,
        )
        val post = SampleData.posts.first().copy(id = "t3_p37", comments = 43)
        val loaded = (1..40).map { index -> comment("t1_c$index", "comment number $index\n\nwith a second line") }
        var comments by mutableStateOf(
            RedditCommentsUiState(
                accountStableId = account.stableId,
                postId = post.id,
                comments = loaded + more("more_end"),
                fullDiscussionLoaded = true,
                contentRevision = 1L,
            ),
        )
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, _, nodeId ->
                        val started = onLoad(nodeId)
                        if (started) comments = comments.copy(loadingMoreNodeId = nodeId)
                        started
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("needle")
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun aFindPausedByTheRateLimitGoesOnWhenItIsBack() {
        RedditReadDiagnostics.observeRateLimit(remaining = 5.0, used = 995.0, resetAfterMs = 60_000L, atEpochMs = 1L)
        val loads = mutableListOf<String>()
        showFind { nodeId -> loads += nodeId; true }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        assertTrue("loaded while the limit was low: $loads", loads.isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Searched 40 of 43 comments").fetchSemanticsNodes().isNotEmpty())

        // Reddit's window resets.
        RedditReadDiagnostics.observeRateLimit(remaining = 600.0, used = 0.0, resetAfterMs = 600_000L, atEpochMs = 2L)
        composeRule.mainClock.advanceTimeBy(THREAD_FIND_RATE_LIMIT_POLL_MS + 1_000)
        composeRule.waitUntil(5_000) { loads.isNotEmpty() }
        assertEquals(listOf("more_end"), loads)
    }

    @Test
    fun aRefusedLoadIsAskedForAgain() {
        val asks = mutableListOf<String>()
        showFind { nodeId -> asks += nodeId; asks.size > 1 }
        composeRule.waitUntil(5_000) { asks.isNotEmpty() }
        composeRule.mainClock.advanceTimeBy(THREAD_FIND_REFUSED_LOAD_RETRY_MS + 500)
        composeRule.waitUntil(5_000) { asks.size >= 2 }
        assertEquals(listOf("more_end", "more_end"), asks.take(2))
    }
}
