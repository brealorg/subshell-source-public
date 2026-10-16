package app.subshell.prototype.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.subshell.prototype.data.FakeSubshellRepository
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.model.Post
import app.subshell.prototype.model.PostKind
import app.subshell.prototype.ui.PostSwipeAction
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.VoteColorPalette
import app.subshell.prototype.ui.VoteControlMode
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (issue16): a swipe past the threshold commits its action once and the
 * row springs back, so the next swipe commits again; a disabled surface does
 * nothing; on a post card a committed swipe runs the mapped vote, and only
 * while swipe actions are on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Step3SwipeActionsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSurface(enabled: Boolean, onRight: () -> Unit) {
        val upvote = voteSwipeActionVisual(
            direction = 1,
            label = "Upvote",
            palette = VoteColorPalette.RED_BLUE,
            mode = VoteControlMode.COMFORTABLE,
            enabled = true,
        )
        composeRule.setContent {
            SwipeActionSurface(
                enabled = enabled,
                leftAction = null,
                rightAction = upvote,
                onLeft = {},
                onRight = onRight,
                modifier = Modifier.width(360.dp).height(96.dp),
            ) { contentModifier ->
                Box(contentModifier.height(96.dp).background(Color.White).testTag("card"))
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipeCardRight() {
        composeRule.onNodeWithTag("card").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
    }

    @Test
    fun aSwipePastTheThresholdCommitsOnceAndTheRowSpringsBack() {
        var commits = 0
        setSurface(enabled = true) { commits += 1 }

        swipeCardRight()
        assertEquals(1, commits)
        assertEquals(0f, composeRule.onNodeWithTag("card").getBoundsInRoot().left.value, 0.5f)

        swipeCardRight()
        assertEquals(2, commits)
    }

    @Test
    fun aDisabledSurfaceCommitsNothing() {
        var commits = 0
        setSurface(enabled = false) { commits += 1 }

        swipeCardRight()

        assertEquals(0, commits)
    }

    @Test
    fun aSwipeOnAPostCardRunsTheMappedVoteOnlyWhileSwipeActionsAreOn() {
        val accounts = localFixtureAccountSummaries(listOf("root"))
        val state = SubshellState(
            repository = FakeSubshellRepository(),
            initialAccountSessions = accounts,
            initialActiveAccountId = accounts.first().stableId,
            initialInteractionSideConfigured = true,
        )
        val post = Post(
            id = "t3_swiped",
            community = "android",
            author = "alice",
            age = "1h",
            title = "A post to swipe",
            score = 1,
            comments = 0,
            kind = PostKind.TEXT,
        )
        state.updatePostSwipeRightAction(PostSwipeAction.UPVOTE)
        state.updateSwipeActionsEnabled(false)
        composeRule.setContent { SubshellTheme { PostCard(post = post, state = state, onOpen = {}) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("A post to swipe").performTouchInput { swipeRight(startX = left + 4f, endX = left + 380f * density) }
        composeRule.waitForIdle()
        assertEquals(0, state.effectivePostVote(post))

        composeRule.runOnIdle { state.updateSwipeActionsEnabled(true) }
        composeRule.onNodeWithText("A post to swipe").performTouchInput { swipeRight(startX = left + 4f, endX = left + 380f * density) }
        composeRule.waitForIdle()
        assertEquals(1, state.effectivePostVote(post))
    }
}
