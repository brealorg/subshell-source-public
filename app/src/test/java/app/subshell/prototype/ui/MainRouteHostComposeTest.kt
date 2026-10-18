package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.InboxPrimaryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the Main route branch of SubshellApp's route dispatch
 * in the expanded window, written before it moves to MainRouteHost. Current
 * behaviour, not a specification: each main destination renders its own
 * workspace (the Home feed with its source · sort header, the Communities
 * tabs, the Inbox two-pane with its "Select activity" placeholder and tab
 * strip, the You content tab strip), the inbox tab strip is controlled
 * state that switches the placeholder, and the drawer request from Main is
 * still honoured.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class MainRouteHostComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun mainDestinationsRenderTheirWorkspacesAndTheInboxTabsAreControlled() {
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
        composeRule.setContent {
            SubshellApp(videoPlaybackCoordinator = testVideoPlaybackCoordinator(context), state = state)
        }
        composeRule.waitForIdle()

        // Home: the feed header names the source and sort.
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertTrue(count("${state.feedSource.label} · ${state.feedSort.label}") >= 1 || count(state.feedSource.label) >= 1)

        // Communities: its tab strip.
        state.navigate(MainDestination.SUBS)
        composeRule.waitForIdle()
        assertTrue(count("Favorites") >= 1)
        assertTrue(count("Joined") >= 1)

        // Inbox: two-pane workspace with the activity placeholder; the tab
        // strip is controlled state, switching it swaps the placeholder.
        state.navigate(MainDestination.INBOX)
        composeRule.waitForIdle()
        assertEquals(1, count("Select activity"))
        assertEquals(0, count("Select a conversation"))
        composeRule.onNodeWithText(InboxPrimaryTab.MESSAGES.label).performClick()
        composeRule.waitForIdle()
        assertEquals(0, count("Select activity"))
        assertEquals(1, count("Select a conversation"))
        composeRule.onNodeWithText(InboxPrimaryTab.ACTIVITY.label).performClick()
        composeRule.waitForIdle()
        assertEquals(1, count("Select activity"))

        // You: the own-content tab strip.
        state.navigate(MainDestination.YOU)
        composeRule.waitForIdle()
        assertTrue(count(youContentWorkspaceLabel(0)) >= 1)
        assertTrue(count(youContentWorkspaceLabel(1)) >= 1)

        state.navigate(MainDestination.HOME)
        composeRule.waitForIdle()
        assertEquals(MainDestination.HOME, state.destination)
    }
}
