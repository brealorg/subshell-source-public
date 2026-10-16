package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditCommentsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Comment
import app.subshell.prototype.ui.screens.PostScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** LOWUX4_COMMENT_LINK_GATE_KEEPS_A_WAY_OUT_V1 (OCB-241). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LowUx4ComposeTest {
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

    private fun gatedPost(onBack: () -> Unit) {
        ensureWorkManagerInitialized(context)
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val post = SampleData.posts.first().copy(id = "t3_p241", comments = 1)
        val comments = RedditCommentsUiState(
            accountStableId = account.stableId,
            postId = post.id,
            comments = listOf(
                Comment(id = "other241", author = "someone", age = "1h", body = "Not the one", score = 1, depth = 0, accent = 0xFF336699),
            ),
            fullDiscussionLoaded = true,
            contentRevision = 1L,
        )
        val coordinator = VideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                PostScreen(
                    post = post,
                    state = state,
                    onBack = onBack,
                    // The linked comment is slow to arrive (Reddit has not answered yet).
                    routeFocusedCommentId = "slow241",
                    redditCommentsState = comments,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
    }

    @Test
    fun backWorksWhileACommentLinkLands() {
        var backs = 0
        gatedPost(onBack = { backs += 1 })
        assertEquals(1, composeRule.onAllNodesWithText("Opening comment…").fetchSemanticsNodes().size)

        val backButtons = composeRule.onAllNodesWithContentDescription("Back")
        backButtons[backButtons.fetchSemanticsNodes().size - 1].performClick()
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun showThreadLiftsTheGate() {
        gatedPost(onBack = {})
        composeRule.onNodeWithText("Show thread").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithText("Opening comment…").fetchSemanticsNodes().size)
    }
}
