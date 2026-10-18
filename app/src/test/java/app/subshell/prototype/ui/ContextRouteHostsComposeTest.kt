package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the User and Search route branches of SubshellApp's
 * route dispatch in the expanded window, written before they move to
 * UserRouteHost and SearchRouteHost. Current behaviour, not a
 * specification: a public user route is a two-pane workspace whose list
 * pane is titled u/<name> with the content-tab placeholder on the right;
 * the search route is a two-pane workspace titled Search whose detail pane
 * asks for a query until one is submitted; both close through
 * SubshellState.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class ContextRouteHostsComposeTest {
    private val composeRule = createComposeRule()

    // The search route trips a Robolectric renderer flake now and then; see the rule.
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private fun signedInState(): SubshellState {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        return SubshellState(
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
            initialInteractionSideConfigured = true,
        )
    }

    private fun compose(state: SubshellState) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun publicUserRouteIsATwoPaneWorkspaceThatClosesThroughState() {
        val state = signedInState()
        compose(state)

        state.openUser("alice")
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.User)
        // The list pane title and the profile header both show the name.
        assertEquals(2, composeRule.onAllNodesWithText("u/alice").fetchSemanticsNodes().size)
        composeRule.onNodeWithText(youContentWorkspacePlaceholder(state.userProfileTab("alice"))).assertIsDisplayed()

        state.closeUser()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }

    @Test
    fun searchRouteIsATwoPaneWorkspaceThatAsksForAQueryUntilOneIsSubmitted() {
        val state = signedInState()
        compose(state)

        state.openSearch()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Search)
        composeRule.onNodeWithText("Search").assertIsDisplayed()
        composeRule.onNodeWithText("Search for a post").assertIsDisplayed()

        assertEquals("", state.searchSubmittedQuery)
        assertTrue(state.handleSearchBack())
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }
}
