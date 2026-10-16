package app.subshell.prototype.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import app.subshell.prototype.ui.app.FeedEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ROOTBACK01: Back walks to the default root (Home tab, then the landing
 * feed source, custom feed left first) before it reaches the system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RootBackComposeTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake; it struck this class once in the ROOTBACK01 negative control.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

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
        subredditNames = listOf("android"),
    )

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun setUp(
        subscriptions: RedditSubscriptionsUiState = RedditSubscriptionsUiState(),
    ): Pair<SubshellState, OnBackPressedDispatcher> {
        val state = SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                redditFeed = FeedEntryPoints(redditSubscriptionsState = subscriptions),
                customFeeds = CustomFeedsEntryPoints(
                    customFeedsState = CustomFeedsUiState(
                        accountStableId = account.stableId,
                        feeds = listOf(feed),
                    ),
                ),
            )
        }
        composeRule.waitForIdle()
        return state to dispatcher
    }

    private fun pressBack(dispatcher: OnBackPressedDispatcher) {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backFromAnotherTabGoesHomeAndThenReachesTheSystem() {
        val (state, dispatcher) = setUp()
        assertFalse(dispatcher.hasEnabledCallbacks())

        // Existing behaviour, pinned here: navigate() keeps Main(HOME) under
        // the other tabs, so the route stack pops them to Home.
        for (destination in listOf(MainDestination.SUBS, MainDestination.INBOX, MainDestination.YOU)) {
            state.navigate(destination)
            composeRule.waitForIdle()
            assertTrue(state.canNavigateBack)
            assertTrue(dispatcher.hasEnabledCallbacks())

            pressBack(dispatcher)
            assertEquals(MainDestination.HOME, state.destination)
            assertEquals(FeedSource.HOME, state.feedSource)
            assertFalse(dispatcher.hasEnabledCallbacks())
        }
    }

    @Test
    fun backFromAnotherSourceReturnsToHomeAfterTheTab() {
        val (state, dispatcher) = setUp()
        state.selectFeedSource(FeedSource.POPULAR)
        state.navigate(MainDestination.INBOX)
        composeRule.waitForIdle()

        pressBack(dispatcher)
        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(FeedSource.POPULAR, state.feedSource)

        pressBack(dispatcher)
        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun anAccountWithoutSubscriptionsTreatsPopularAsTheRoot() {
        val (state, dispatcher) = setUp(
            subscriptions = RedditSubscriptionsUiState(accountStableId = account.stableId, loaded = true),
        )
        // The authenticated landing has chosen Popular: that is the root.
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertFalse(dispatcher.hasEnabledCallbacks())

        state.selectFeedSource(FeedSource.ALL)
        composeRule.waitForIdle()
        pressBack(dispatcher)
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun aCustomFeedIsLeftBeforeTheFeedSourceIsReset() {
        val (state, dispatcher) = setUp()
        state.selectFeedSource(FeedSource.POPULAR)
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom feeds · 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tech").performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("Feed: Tech"))

        pressBack(dispatcher)
        assertEquals(0, count("Feed: Tech"))
        assertEquals(FeedSource.POPULAR, state.feedSource)

        pressBack(dispatcher)
        assertEquals(FeedSource.HOME, state.feedSource)
        assertFalse(dispatcher.hasEnabledCallbacks())
    }
}
