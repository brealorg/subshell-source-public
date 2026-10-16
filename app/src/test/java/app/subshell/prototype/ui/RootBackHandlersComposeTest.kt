package app.subshell.prototype.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.CustomFeedSummary
import app.subshell.prototype.data.account.RedditSubscriptionsUiState
import app.subshell.prototype.ui.app.PredictiveRouteBackState
import app.subshell.prototype.ui.app.RootBackHandlers
import app.subshell.prototype.ui.error.ViewportErrorHostState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SubshellApp's Back handlers in their own file, hosted alone: the route
 * stack goes first, then the landing feed source at Home (ROOTBACK01),
 * a custom feed at Home is left for the main feed (ISSUE25), and at the
 * true root the optional prompt claims one Back (ISSUE26). The whole-app
 * behaviour stays covered by RootBackComposeTest, BackTwiceToExitComposeTest,
 * Issue25CustomFeedBackComposeTest and PredictiveRouteBackComposeTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class RootBackHandlersComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val feed = CustomFeedSummary(
        path = "/user/alice/m/reading",
        displayName = "reading",
        description = "",
        visibility = "private",
        subredditNames = emptyList(),
    )

    private class Host(
        val dispatcher: OnBackPressedDispatcher,
        val customFeedPath: MutableState<String?>,
        val pendingLanding: MutableState<String?>,
        val notice: ViewportErrorHostState,
    )

    private fun state(backTwiceToExit: Boolean = false) = SubshellState(
        initialAccountSessions = listOf(alice),
        initialActiveAccountId = alice.stableId,
        initialInteractionSideConfigured = true,
        initialBackTwiceToExit = backTwiceToExit,
    )

    private fun host(
        state: SubshellState,
        customFeedPath: String? = null,
        subscriptions: RedditSubscriptionsUiState = RedditSubscriptionsUiState(),
    ): Host {
        val customFeedPathState = mutableStateOf(customFeedPath)
        val pendingLanding = mutableStateOf<String?>(alice.stableId)
        val notice = ViewportErrorHostState()
        lateinit var dispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            val path by customFeedPathState
            RootBackHandlers(
                state = state,
                predictiveRouteBackState = PredictiveRouteBackState(),
                backExitNoticeState = notice,
                activeCustomFeed = feed.takeIf { it.path == path },
                activeCustomFeedPathState = customFeedPathState,
                pendingAuthenticatedLandingAccountIdState = pendingLanding,
                redditSubscriptionsState = subscriptions,
            )
        }
        composeRule.waitForIdle()
        return Host(dispatcher, customFeedPathState, pendingLanding, notice)
    }

    private fun Host.pressBack() {
        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun backWithARouteAbovePopsTheRouteStack() {
        val state = state()
        val host = host(state)
        state.navigate(MainDestination.INBOX)
        composeRule.waitForIdle()

        host.pressBack()

        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(alice.stableId, host.pendingLanding.value)
    }

    @Test
    fun backAtHomeOnAnotherSourceSelectsTheLandingSourceAndClearsThePendingLanding() {
        val state = state()
        state.selectFeedSource(FeedSource.POPULAR)
        val host = host(state)

        host.pressBack()

        assertEquals(FeedSource.HOME, state.feedSource)
        assertNull(host.pendingLanding.value)
        assertFalse(host.dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun theLandingSourceFollowsThePassedSubscriptions() {
        val state = state()
        val host = host(
            state,
            subscriptions = RedditSubscriptionsUiState(accountStableId = alice.stableId, loaded = true),
        )

        host.pressBack()

        assertEquals(FeedSource.POPULAR, state.feedSource)
    }

    @Test
    fun aCustomFeedAtHomeIsLeftFirstWithoutChangingTheFeedSource() {
        val state = state()
        state.selectFeedSource(FeedSource.POPULAR)
        val host = host(state, customFeedPath = feed.path)

        host.pressBack()

        assertNull(host.customFeedPath.value)
        assertEquals(FeedSource.POPULAR, state.feedSource)
        assertEquals(alice.stableId, host.pendingLanding.value)
    }

    @Test
    fun atTheTrueRootNothingClaimsBackUnlessThePromptIsOn() {
        assertFalse(host(state()).dispatcher.hasEnabledCallbacks())
    }

    @Test
    fun withThePromptOnTheFirstBackAtTheTrueRootPresentsItInThePassedTray() {
        val host = host(state(backTwiceToExit = true))
        assertTrue(host.dispatcher.hasEnabledCallbacks())

        host.pressBack()

        assertNotNull(host.notice.currentRequest)
        assertFalse(host.dispatcher.hasEnabledCallbacks())
    }
}
