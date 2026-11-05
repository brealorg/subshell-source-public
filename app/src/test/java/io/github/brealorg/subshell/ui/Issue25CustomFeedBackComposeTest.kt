package io.github.brealorg.subshell.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.brealorg.subshell.data.account.AccountSessionSource
import io.github.brealorg.subshell.data.account.AccountSessionState
import io.github.brealorg.subshell.data.account.AccountSessionSummary
import io.github.brealorg.subshell.data.account.CustomFeedSummary
import io.github.brealorg.subshell.data.account.CustomFeedsUiState
import io.github.brealorg.subshell.harness.RetryOnRobolectricRendererFlake
import io.github.brealorg.subshell.harness.ensureWorkManagerInitialized
import io.github.brealorg.subshell.harness.testVideoPlaybackCoordinator
import io.github.brealorg.subshell.media.VideoPlaybackCoordinator
import io.github.brealorg.subshell.ui.app.CustomFeedsEntryPoints
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
 * Issue #25: a custom feed replaces the main feed at Home without pushing a
 * route, so Back used to reach the system and close the app. Back now leaves
 * the custom feed for the main feed; Back from the main feed still exits, and
 * an open drawer still takes Back first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue25CustomFeedBackComposeTest {
    private val composeRule = createComposeRule()

    // Display-density guard for the Robolectric `lightZ … Infinity` launch
    // flake; same whole-app setup as RootBackComposeTest, where it struck.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val feed = CustomFeedSummary(
        path = "/user/connected-user/m/tech",
        displayName = "Tech",
        description = "",
        visibility = "private",
        subredditNames = listOf("android", "kotlin"),
    )

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun setUp(): Pair<SubshellState, OnBackPressedDispatcher> {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
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
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
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

    private fun openCustomFeedFromDrawer(state: SubshellState) {
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Custom feeds · 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tech").performClick()
        composeRule.waitForIdle()
    }

    private fun pressBack(dispatcher: OnBackPressedDispatcher) {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backLeavesTheCustomFeedForTheMainFeedAndThenReachesTheSystem() {
        val (state, dispatcher) = setUp()
        openCustomFeedFromDrawer(state)
        assertEquals(1, count("Feed: Tech"))
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertFalse(state.canNavigateBack)
        assertTrue(dispatcher.hasEnabledCallbacks())

        pressBack(dispatcher)
        assertEquals(0, count("Feed: Tech"))
        assertEquals(MainDestination.HOME, state.destination)
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)

        // Main feed at the root: nothing in the app claims Back, so the
        // system finishes the activity as before.
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun anOpenDrawerTakesBackBeforeTheCustomFeed() {
        val (state, dispatcher) = setUp()
        openCustomFeedFromDrawer(state)
        assertEquals(1, count("Feed: Tech"))

        state.requestDrawer()
        composeRule.waitForIdle()
        pressBack(dispatcher)
        assertEquals(1, count("Feed: Tech"))

        pressBack(dispatcher)
        assertEquals(0, count("Feed: Tech"))
    }
}
