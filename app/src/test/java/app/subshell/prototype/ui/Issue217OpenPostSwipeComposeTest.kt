package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.screens.PostScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** #217: ISSUE217_OPEN_POST_TAKES_SWIPE_ACTIONS_V1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue217OpenPostSwipeComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val post = Post(
        id = "t3_open217",
        community = "android",
        author = "alice",
        age = "1h",
        title = "An open post to swipe",
        score = 1,
        comments = 0,
        kind = PostKind.TEXT,
    )

    private fun showOpenPost(swipeActionsOn: Boolean): SubshellState {
        ensureWorkManagerInitialized(context)
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
            initialInteractionSideConfigured = true,
        )
        state.updatePostSwipeRightAction(PostSwipeAction.UPVOTE)
        state.updatePostSwipeLeftAction(PostSwipeAction.DOWNVOTE)
        state.updateSwipeActionsEnabled(swipeActionsOn)
        val coordinator = testVideoPlaybackCoordinator(context)
        composeRule.setContent {
            CompositionLocalProvider(LocalVideoPlaybackCoordinator provides coordinator) {
                SubshellTheme { PostScreen(post = post, state = state, onBack = {}) }
            }
        }
        composeRule.waitForIdle()
        return state
    }

    @Test
    fun theOpenPostSwipesLikeAFeedCard() {
        val state = showOpenPost(swipeActionsOn = true)

        composeRule.onNodeWithText("An open post to swipe")
            .performTouchInput { swipeRight(startX = left + 4f, endX = left + 380f * density) }
        composeRule.waitForIdle()
        assertEquals(1, state.effectivePostVote(post))

        composeRule.onNodeWithText("An open post to swipe")
            .performTouchInput { swipeLeft(startX = right - 4f, endX = right - 380f * density) }
        composeRule.waitForIdle()
        assertEquals(-1, state.effectivePostVote(post))
    }

    @Test
    fun theOpenPostIgnoresSwipesWhileSwipeActionsAreOff() {
        val state = showOpenPost(swipeActionsOn = false)

        composeRule.onNodeWithText("An open post to swipe")
            .performTouchInput { swipeRight(startX = left + 4f, endX = left + 380f * density) }
        composeRule.waitForIdle()
        assertEquals(0, state.effectivePostVote(post))
    }
}
