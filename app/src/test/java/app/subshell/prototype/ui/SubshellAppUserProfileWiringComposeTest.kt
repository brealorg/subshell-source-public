package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.RedditAccountHistoryKind
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behaviour test for the profile-host wiring that check-profile01a2-contract.sh
 * counts as SubshellApp text: both profile hosts (the You destination and
 * the user route) hand the signed-in account's entry refresh of a user's
 * submitted posts to the onRefreshRedditUserSubmitted callback.
 *
 * STEP3 (profile01a6): the own profile's Library destinations hand their
 * entry refreshes (account history, Saved, Blocked) through SubshellApp to
 * the app's callbacks, not to the profile screen's defaults.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SubshellAppUserProfileWiringComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothProfileHostsRefreshTheUsersSubmittedPostsOnEntry() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val refreshes = mutableListOf<Pair<String, String>>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                onRefreshRedditUserSubmitted = { accountId, username -> refreshes += accountId to username },
            )
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<Pair<String, String>>(), refreshes)

        // The You destination hosts the own profile.
        state.navigate(MainDestination.YOU)
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test" to "connected-user"), refreshes)

        // The user route hosts another user's profile.
        state.openUser("alice")
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test" to "connected-user", "reddit:test" to "alice"), refreshes)

        // Re-entry does not refresh again (the entry gate is per query key).
        state.navigateBack()
        composeRule.waitForIdle()
        state.openUser("alice")
        composeRule.waitForIdle()
        assertEquals(2, refreshes.size)
    }

    @Test
    fun theOwnProfileHostHandsLibraryRefreshesToTheApp() {
        val account = AccountSessionSummary(
            stableId = "reddit:test",
            username = "connected-user",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            // ISSUE271: an install that has chosen its side shows Home, not the setup flow.
            initialInteractionSideConfigured = true,
            initialAccountSessions = listOf(account),
            initialActiveAccountId = account.stableId,
        )
        val historyRefreshes = mutableListOf<Triple<String, String, RedditAccountHistoryKind>>()
        val savedRefreshes = mutableListOf<Pair<String, String>>()
        val blockedRefreshes = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                onRefreshRedditAccountHistory = { accountId, username, kind ->
                    historyRefreshes += Triple(accountId, username, kind)
                },
                onRefreshRedditSavedHistory = { accountId, username -> savedRefreshes += accountId to username },
                onRefreshBlockedAccounts = { accountId -> blockedRefreshes += accountId },
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { state.navigate(MainDestination.YOU) }
        composeRule.waitForIdle()

        composeRule.runOnIdle { state.selectUserProfileTab("connected-user", 3) }
        composeRule.waitForIdle()
        assertEquals(
            listOf(Triple("reddit:test", "connected-user", RedditAccountHistoryKind.UPVOTED)),
            historyRefreshes.filter { it.third == RedditAccountHistoryKind.UPVOTED },
        )

        composeRule.runOnIdle { state.selectUserProfileTab("connected-user", 2) }
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test" to "connected-user"), savedRefreshes)

        composeRule.runOnIdle { state.selectUserProfileTab("connected-user", 6) }
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test"), blockedRefreshes)
    }
}
