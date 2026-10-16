package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.SampleData
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP3 (next01a5): on an expanded window in split mode, Communities is a
 * list-detail workspace: nothing open shows "Select a community"; a tapped
 * community opens beside the list without a back button, the row says it is
 * selected, and a second tap replaces the open community instead of stacking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class Step3SubscriptionsCommunityWorkspaceComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    private fun compose(): SubshellState {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        state.updateTabletLayoutMode(TabletLayoutMode.SPLIT)
        state.navigate(MainDestination.SUBS)
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        val subscriptions = RedditSubscriptionsUiState(
            accountStableId = account.stableId,
            communities = SampleData.communities,
            loaded = true,
        )
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions),
            )
        }
        composeRule.waitForIdle()
        return state
    }

    private fun row(name: String): SemanticsNodeInteraction =
        composeRule.onAllNodes(hasClickAction() and hasText("r/\u2060$name")).onFirst()

    private fun backButtons() =
        composeRule.onAllNodes(hasContentDescription("Back") and hasClickAction()).fetchSemanticsNodes().size

    @Test
    fun aTappedCommunityOpensBesideTheListAndASecondTapReplacesIt() {
        val state = compose()
        assertEquals(1, composeRule.onAllNodesWithText("Select a community").fetchSemanticsNodes().size)

        row("android").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val depth = state.navigationRouteDepth
        assertEquals(SubshellRoute.Community::class, state.currentNavigationRoute::class)
        assertEquals("android", state.selectedSubreddit)
        assertEquals(0, composeRule.onAllNodesWithText("Select a community").fetchSemanticsNodes().size)
        assertEquals(0, backButtons())
        assertTrue(
            composeRule.onAllNodes(isSelected() and hasText("r/\u2060android"))
                .fetchSemanticsNodes().isNotEmpty(),
        )

        row("linux").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals("linux", state.selectedSubreddit)
        assertEquals(depth, state.navigationRouteDepth)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun onAPhoneATappedCommunityOpensFullScreenWithABackButton() {
        val state = compose()
        assertEquals(0, composeRule.onAllNodesWithText("Select a community").fetchSemanticsNodes().size)

        row("android").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertEquals("android", state.selectedSubreddit)
        assertEquals(1, backButtons())
    }
}
