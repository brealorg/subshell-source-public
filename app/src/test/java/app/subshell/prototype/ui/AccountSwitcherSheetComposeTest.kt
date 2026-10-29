package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.data.account.localFixtureAccountSummaries
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.AccountConnectEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the quick account switcher as hosted by SubshellApp
 * today, written before AccountSwitcherSheet moves to its own file. Current
 * behaviour, not a specification: the sheet lists the authenticated
 * sessions only (local fixtures hidden), marks the active one, switching to
 * a session that needs re-authentication also resets and restarts the
 * connect flow, and "Manage accounts" opens the Settings secondary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AccountSwitcherSheetComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switcherListsProductSessionsSwitchesAndReconnectsAndOpensAccountSettings() {
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val bob = alice.copy(
            stableId = "reddit:bob",
            username = "bob",
            state = AccountSessionState.REAUTHENTICATION_REQUIRED,
        )
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = localFixtureAccountSummaries(listOf("root")) + alice + bob,
            initialActiveAccountId = alice.stableId,
        )
        val events = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                accountConnect = AccountConnectEntryPoints(
                    onResetConnectAccount = { events += "reset" },
                    onReconnectAccount = { accountId -> events += "reconnect:$accountId" },
                ),
            )
        }
        composeRule.waitForIdle()

        state.requestAccountSwitcher()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("u/alice").assertIsDisplayed()
        composeRule.onNodeWithText("u/bob").assertIsDisplayed()
        composeRule.onNodeWithText("Current account").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Current account").assertIsDisplayed()
        composeRule.onNodeWithText("u/root").assertDoesNotExist()
        composeRule.onNodeWithText("Manage accounts").assertIsDisplayed()

        composeRule.onNodeWithText("u/bob").performClick()
        composeRule.waitForIdle()
        assertEquals("reddit:bob", state.activeAccountId)
        assertEquals(listOf("reset", "reconnect:reddit:bob"), events)

        // The sheet's own dismiss is animated; re-requesting is idempotent.
        state.requestAccountSwitcher()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Current account · Sign in again").assertIsDisplayed()
        composeRule.onNodeWithText("u/alice").performClick()
        composeRule.waitForIdle()
        assertEquals("reddit:alice", state.activeAccountId)
        assertEquals(2, events.size)

        assertNull(state.secondaryDestination)
        state.requestAccountSwitcher()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Manage accounts").performClick()
        composeRule.waitForIdle()
        assertEquals(SecondaryDestination.SETTINGS, state.secondaryDestination)
    }
}
