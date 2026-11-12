package io.github.brealorg.subshell.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
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
import kotlin.math.abs

/**
 * The single position owner (audit 2026-11-12, B core):
 * AUDIT298C_EVERY_TREE_CHANGE_HOLDS_THE_READING_ROW_V1 — a tree change that is
 * not a hydration (sync, refresh, the stored thread) holds the row being read,
 * also when far more than a hundred rows go in above it, and waits until the
 * reader's finger is off the list;
 * AUDIT298C_POSITION_IS_SAVED_BY_KEY_V1 — the saved place is a row key, so it
 * survives a restore onto a thread that arrives later and has changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Audit298cPositionOwnerComposeTest {
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
    private val post: Post = SampleData.posts.first().copy(id = "t3_p298po", comments = 300)

    private fun comment(id: String) = Comment(
        id = id,
        author = "someone",
        age = "1h",
        body = "Comment $id",
        score = 1,
        depth = 0,
        accent = 0xFF336699,
    )

    private var revision = 0L
    private var comments by mutableStateOf(RedditCommentsUiState())

    private fun thread(ids: List<String>): RedditCommentsUiState {
        revision += 1
        return RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            sort = RedditCommentSort.BEST,
            comments = ids.map(::comment),
            fullDiscussionLoaded = true,
            contentRevision = revision,
        )
    }

    private val old = (0 until 60).map { "c$it" }
    private val sync = (0 until 150).map { "n$it" }

    private fun state(): SubshellState {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        return SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        ).also { it.updateHighlightNewComments(false) }
    }

    private fun content(state: SubshellState): @androidx.compose.runtime.Composable () -> Unit {
        val coordinator = testVideoPlaybackCoordinator(ApplicationProvider.getApplicationContext<Application>())
        return {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = {},
                    redditCommentsState = comments,
                )
            }
        }
    }

    private fun topOf(id: String): Float =
        composeRule.onNodeWithText("Comment $id").fetchSemanticsNode().boundsInRoot.top

    private fun readAt(id: String) {
        composeRule.onNode(hasScrollToKeyAction()).performScrollToKey("comment:$id")
        composeRule.waitForIdle()
        // A little further, so the row is below the stuck sort header.
        composeRule.onNode(hasScrollToKeyAction()).performTouchInput {
            down(center)
            moveBy(Offset(0f, 600f), delayMillis = 100)
            moveBy(Offset(0f, 2f), delayMillis = 400)
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    @Test
    fun aSyncThatPutsManyRowsAboveKeepsTheRowBeingRead() {
        comments = thread(old)
        composeRule.setContent(content(state()))
        composeRule.waitForIdle()
        readAt("c30")
        val before = topOf("c30")

        // Pull to refresh / a sync: 150 comments now come before the old ones.
        composeRule.runOnIdle { comments = thread(sync + old) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val after = topOf("c30")
        assertTrue("c30 stays where it was read (before=$before after=$after)", abs(after - before) < 2f)
    }

    @Test
    fun aTreeChangeWaitsUntilTheFingerIsOff() {
        comments = thread(old)
        composeRule.setContent(content(state()))
        composeRule.waitForIdle()
        readAt("c30")
        val list = composeRule.onNode(hasScrollToKeyAction())
        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, -40f), delayMillis = 100)
        }
        composeRule.waitForIdle()

        // c30 is deleted elsewhere while the reader's finger is on the list.
        composeRule.runOnIdle { comments = thread(old - "c30") }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertEquals("not swapped under the finger", 1, composeRule.onAllNodesWithText("Comment c30").fetchSemanticsNodes().size)

        list.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("applied once the list is at rest", 0, composeRule.onAllNodesWithText("Comment c30").fetchSemanticsNodes().size)
    }

    @Test
    fun aRestoredScreenFindsItsRowByKeyWhenTheThreadArrivesChanged() {
        comments = thread(old)
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content(state()))
        composeRule.waitForIdle()
        readAt("c40")
        val before = topOf("c40")

        // Process death: the thread comes back later, with 20 new comments first.
        comments = RedditCommentsUiState()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        composeRule.runOnIdle { comments = thread((0 until 20).map { "r$it" } + old) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val after = topOf("c40")
        assertTrue("back at c40 (before=$before after=$after)", abs(after - before) < 2f)
    }
}
