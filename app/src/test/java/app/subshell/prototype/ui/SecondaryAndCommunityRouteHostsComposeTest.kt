package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.screens.SettingsDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the Secondary and Community route branches of
 * SubshellApp's route dispatch, written before they move to
 * SecondaryRouteHost and CommunityRouteHost. Current behaviour, not a
 * specification: each secondary destination shows its screen title and
 * closes through SubshellState; a community route shows the community
 * header, and from the Communities destination in the expanded window it
 * is a two-pane workspace titled Communities.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp")
class SecondaryAndCommunityRouteHostsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    /** Some titles also exist elsewhere (drawer rows, headers): count, don't assert uniqueness. */
    private fun count(text: String): Int = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun secondaryDestinationsShowTheirScreensAndCloseThroughState() {
        val state = signedInState()
        compose(state)

        val titles = mapOf(
            SecondaryDestination.SETTINGS to SettingsDestination.ROOT.title,
            SecondaryDestination.HELP to "Help & about",
            SecondaryDestination.COMMUNITY_EXPLORE to "Discover communities",
        )
        titles.forEach { (destination, title) ->
            val before = count(title)
            state.openSecondary(destination)
            composeRule.waitForIdle()
            assertEquals(destination, state.secondaryDestination)
            assertEquals(title, before + 1, count(title))
            state.closeSecondary()
            composeRule.waitForIdle()
            assertNull(state.secondaryDestination)
            assertEquals(title, before, count(title))
        }
    }

    @Test
    fun communityRouteShowsTheCommunityAndIsATwoPaneWorkspaceFromCommunities() {
        val state = signedInState()
        compose(state)

        val label = state.subredditLabel("homelab")
        val communitiesBefore = count("Communities") // the drawer row
        assertEquals(0, count(label))
        state.openSubreddit("homelab")
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Community)
        assertTrue(count(label) >= 1)
        assertEquals(communitiesBefore, count("Communities"))
        state.closeSubreddit()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
        assertEquals(0, count(label))

        state.navigate(MainDestination.SUBS)
        composeRule.waitForIdle()
        state.openSubreddit("homelab")
        composeRule.waitForIdle()
        assertEquals(communitiesBefore + 1, count("Communities"))
        assertTrue(count(label) >= 1)
        state.closeSubreddit()
        composeRule.waitForIdle()
        assertTrue(state.currentNavigationRoute is SubshellRoute.Main)
    }
}
