package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.InboxEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * INBOX02: turning inbox notifications on or off asks the inbox load path
 * once, which schedules or cancels the background sync; the first
 * composition loads only once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Inbox02NotificationToggleComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun togglingNotificationsAsksTheInboxLoadPathOnce() {
        val alice = AccountSessionSummary(
            stableId = "reddit:alice",
            username = "alice",
            source = AccountSessionSource.AUTHENTICATED,
            state = AccountSessionState.CONNECTED,
            providerLabel = "Reddit",
        )
        val state = SubshellState(
            initialAccountSessions = listOf(alice),
            initialActiveAccountId = alice.stableId,
            initialInteractionSideConfigured = true,
        )
        val loads = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)
        // NOTIFY02: without the permission the app asks once and a refusal
        // turns notifications off; this test is about the load path.
        org.robolectric.Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = testVideoPlaybackCoordinator(context),
                state = state,
                redditInbox = InboxEntryPoints(onLoadRedditInbox = { id, force -> loads += "$id:$force" }),
            )
        }
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:alice:false"), loads)

        composeRule.runOnIdle { state.updateInboxNotificationsEnabled(true) }
        composeRule.waitForIdle()
        assertEquals(2, loads.size)

        composeRule.runOnIdle { state.updateInboxNotificationsEnabled(false) }
        composeRule.waitForIdle()
        assertEquals(3, loads.size)
    }
}
