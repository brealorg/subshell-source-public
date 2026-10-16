package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.CustomFeedsUiState
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.share.ShareIntoSubshellLaunch
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
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
 * ISSUE66_LANDING_IS_NOT_RE_ARMED_BY_RECREATION_V1,
 * ISSUE67_STATE_SURVIVES_THE_FULL_SCREEN_ROUTES_V1,
 * ISSUE68_LAUNCHES_ARE_HANDLED_UNDER_THE_COMPOSER_V1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue66To68SubshellAppStateComposeTest {
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:test",
        username = "connected-user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val feed = CustomFeedSummary(
        path = "/user/connected-user/m/tech",
        displayName = "Tech",
        description = "",
        visibility = "private",
        subredditNames = listOf("android", "kotlin"),
    )

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
    )

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theCustomFeedIsStillOpenAfterTheComposerClosed() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                customFeeds = CustomFeedsEntryPoints(
                    customFeedsState = CustomFeedsUiState(accountStableId = account.stableId, feeds = listOf(feed)),
                ),
            )
        }
        composeRule.waitForIdle()
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom feeds · 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tech").performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("Feed: Tech"))

        composeRule.runOnIdle { state.openPostComposer() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.closePostComposer() }
        composeRule.waitForIdle()

        assertEquals(1, count("Feed: Tech"))
    }

    @Test
    fun aShareWhileTheComposerIsOpenIsTakenAtOnce() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        var launch by mutableStateOf<ShareIntoSubshellLaunch?>(null)
        var consumed = 0
        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                shareIntoSubshellLaunch = launch,
                onShareIntoSubshellConsumed = {
                    consumed += 1
                    launch = null
                },
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.openPostComposer() }
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.PostComposer)

        composeRule.runOnIdle { launch = ShareIntoSubshellLaunch(requestId = 68L, content = null) }
        composeRule.waitForIdle()

        assertEquals(1, consumed)
    }

    @Test
    fun recreationKeepsTheRestoredFeedSourceAndSort() {
        ensureWorkManagerInitialized(context)
        val state = signedIn()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(
                    redditSubscriptionsState = RedditSubscriptionsUiState(
                        accountStableId = account.stableId,
                        communities = listOf(state.communityIdentityFor("android")),
                        loaded = true,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        // The landing after sign-in ran once.
        assertEquals(FeedSource.HOME, state.feedSource)

        composeRule.runOnIdle {
            state.selectFeedSource(FeedSource.POPULAR)
            state.updateFeedSort(FeedSort.TOP)
        }
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(FeedSort.TOP, state.feedSort)
    }
}
