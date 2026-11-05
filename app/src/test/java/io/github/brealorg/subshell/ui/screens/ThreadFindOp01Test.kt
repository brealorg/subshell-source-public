package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.SampleData
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.RedditCommentsUiState
import io.github.brealorg.subshell.data.diagnostics.RedditReadDiagnostics
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.LocalVideoPlaybackCoordinator
import io.github.brealorg.subshell.model.Comment
import io.github.brealorg.subshell.ui.SubshellState
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
 * THREADFINDOP01_FIND_CAN_STEP_THROUGH_OPS_COMMENTS_V1: the OP chip in find
 * limits the matches to the post author's comments; with no text it steps
 * through all of them, and it loads the rest of the thread like a text find.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ThreadFindOp01Test {
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
    fun quietDiagnostics() {
        RedditReadDiagnostics.installForTest { _, _ -> }
    }

    @After
    fun reset() {
        RedditReadDiagnostics.installForTest(null)
    }

    private fun comment(
        id: String,
        body: String,
        isOp: Boolean = false,
        depth: Int = 0,
        children: List<Comment> = emptyList(),
    ) = Comment(
        id = id,
        author = if (isOp) "poster" else "someone",
        age = "1h",
        body = body,
        score = 1,
        depth = depth,
        accent = 0xFF336699,
        isOp = isOp,
        children = children,
    )

    private fun more(id: String) = Comment(
        id = id,
        author = "",
        age = "",
        body = "",
        score = 0,
        depth = 0,
        accent = 0L,
        moreChildIds = listOf("x1", "x2"),
        moreCount = 2,
    )

    @Test
    fun opOnlyMatchesTheAuthorsCommentsWithOrWithoutText() {
        val thread = listOf(
            comment(
                "a",
                "question about kotlin",
                children = listOf(comment("b", "OP answers about kotlin", isOp = true, depth = 1)),
            ),
            comment("c", "OP says thanks", isOp = true),
            comment("d", "more kotlin"),
        )

        assertEquals(listOf("b", "c"), threadFindMatchIds(thread, ThreadFindRequest("", opOnly = true)))
        assertEquals(listOf("b"), threadFindMatchIds(thread, ThreadFindRequest("kotlin", opOnly = true)))
        // Off, it is the text find it always was; nothing at all without text.
        assertEquals(listOf("a", "b", "d"), threadFindMatchIds(thread, ThreadFindRequest("kotlin", opOnly = false)))
        assertEquals(emptyList<String>(), threadFindMatchIds(thread, ThreadFindRequest(" ", opOnly = false)))
    }

    @Test
    fun theOpChipStepsThroughOpsCommentsAndLoadsTheRestOfTheThread() {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
            initialOpenKeyboardOnSearch = false,
        )
        val post = SampleData.posts.first().copy(id = "t3_pop", comments = 32)
        val loaded = (1..30).map { index ->
            comment("t1_c$index", "comment number $index", isOp = index == 3 || index == 20)
        }
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

        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(THREAD_FIND_OP_ONLY_DESCRIPTION)
            .assertIsNotSelected()
            .performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        // OP's first comment is stepped to at once, and the rest of the thread loads.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1/2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(THREAD_FIND_OP_ONLY_DESCRIPTION).assertIsSelected()
        composeRule.waitUntil(5_000) { loadRequests.isNotEmpty() }
        assertEquals(listOf("more_end"), loadRequests)

        // Reddit answers with the replies; one of them is OP's.
        comments = comments.copy(
            comments = loaded + comment("t1_x1", "late OP reply", isOp = true) + comment("t1_x2", "nothing"),
            loadingMoreNodeId = null,
            contentRevision = 2L,
        )
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("1/3").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Next match").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("2/3").fetchSemanticsNodes().isNotEmpty()
        }

        // Text narrows OP's comments.
        composeRule.onNode(hasSetTextAction()).performTextInput("late")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("–/1").fetchSemanticsNodes().isNotEmpty()
        }

        // Closing find forgets the chip.
        composeRule.onNodeWithContentDescription("Close find").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(THREAD_FIND_OPEN_LABEL).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(THREAD_FIND_OP_ONLY_DESCRIPTION).assertIsNotSelected()
        // No count either: find starts empty again.
        assertTrue(composeRule.onAllNodesWithText("–/1").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("1/3").fetchSemanticsNodes().isEmpty())
    }
}
