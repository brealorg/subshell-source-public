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
import app.subshell.prototype.data.diagnostics.READ_EVENT_THREAD_FIND
import app.subshell.prototype.data.diagnostics.RedditReadDiagnostics
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.SubshellState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ISSUE281_FIND_LOADS_THE_REST_OF_THE_THREAD_V1 (#281: a find in a big
 * thread found nothing; most comments were not loaded yet): while a find has
 * a query, the thread's unloaded replies load, so the find reaches them, and
 * the bar says how much it has searched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue281ThreadFindLoadsTheThreadTest {
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
    private val recorded = mutableListOf<Pair<String, Map<String, Any?>>>()

    @Before
    fun record() {
        RedditReadDiagnostics.installForTest { kind, fields -> recorded += kind to fields.toMap() }
    }

    @After
    fun reset() {
        RedditReadDiagnostics.installForTest(null)
    }

    private fun comment(id: String, body: String, children: List<Comment> = emptyList()) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = 0,
        accent = 0xFF336699,
        children = children,
    )

    private fun more(id: String, parentId: String? = null, depth: Int = 0) = Comment(
        id = id,
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = depth,
        accent = 0L,
        parentId = parentId,
        moreChildIds = listOf("x1", "x2"),
        moreCount = 2,
    )

    @Test
    fun coverageCountsLoadedCommentsAndUnloadedNodesInThreadOrder() {
        val thread = listOf(
            comment("t1_a", "a", children = listOf(more("more_a", parentId = "t1_a", depth = 1))),
            comment("t1_b", "b"),
            more("more_root"),
        )

        assertEquals(ThreadFindCoverage(loadedComments = 2, unloadedNodes = 2), threadFindCoverage(thread))
        assertEquals("more_a", threadFindNextMoreNode(thread, blocked = emptySet())?.id)
        // A node that failed waits for its Retry row; the next one is taken.
        assertEquals("more_root", threadFindNextMoreNode(thread, blocked = setOf("more_a"))?.id)
        assertNull(threadFindNextMoreNode(thread, blocked = setOf("more_a", "more_root")))
        assertEquals(
            "Loading the rest of the thread · searched 2 of 17",
            threadFindCoverageLabel(loadedComments = 2, totalComments = 17, loading = true),
        )
        // Reddit's count is only the upper end; never fewer than what is loaded.
        assertEquals(
            "Searched 2 of 2 comments",
            threadFindCoverageLabel(loadedComments = 2, totalComments = 1, loading = false),
        )
    }

    @Test
    fun aFindLoadsTheUnloadedRepliesAndFindsWhatWasInThem() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialOpenKeyboardOnSearch = false,
        )
        val post = SampleData.posts.first().copy(id = "t3_p281", comments = 43)
        // Forty comments, then replies not loaded yet, well below the screen:
        // nothing loads them without a find (no scrolling here).
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
        val loadRequests = mutableListOf<String>()
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                    onLoadMoreRedditComments = { _, _, _, nodeId ->
                        loadRequests += nodeId
                        comments = comments.copy(loadingMoreNodeId = nodeId)
                        true
                    },
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue("loaded without a find: $loadRequests", loadRequests.isEmpty())

        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("needle")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitUntil(5_000) { loadRequests.isNotEmpty() }
        assertEquals(listOf("more_end"), loadRequests)
        assertTrue(
            composeRule.onAllNodesWithText("Loading the rest of the thread · searched 40 of 43")
                .fetchSemanticsNodes().isNotEmpty(),
        )

        // Reddit answers with the replies; one holds the text.
        comments = comments.copy(
            comments = loaded + comment("t1_x1", "the needle is here") + comment("t1_x2", "nothing"),
            loadingMoreNodeId = null,
            contentRevision = 2L,
        )
        // One match, none stepped to yet.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("–/1").fetchSemanticsNodes().isNotEmpty()
        }
        // The whole thread is loaded: no coverage line, and one report line.
        assertTrue(composeRule.onAllNodesWithText("Searched", substring = true).fetchSemanticsNodes().isEmpty())
        composeRule.waitUntil(5_000) { recorded.any { (kind, _) -> kind == READ_EVENT_THREAD_FIND } }
        val report = recorded.single { (kind, _) -> kind == READ_EVENT_THREAD_FIND }.second
        assertEquals("complete", report["stop"])
        assertEquals(42, report["loaded"])
        assertEquals(1, report["loads"])
        assertEquals(1, report["matches"])
        assertEquals(listOf("more_end"), loadRequests)
    }
}
