package app.subshell.prototype.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.media.VideoPlaybackCoordinator
import app.subshell.prototype.ui.app.CustomFeedsEntryPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Characterisation of the navigation drawer as hosted by SubshellApp today,
 * written before SubshellDrawer moves to its own file. Current behaviour,
 * not a specification: the drawer asks for the signed-in account's custom
 * feeds once, its fixed rows drive SubshellState, and the muted-communities
 * row appears only while a mute is active.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SubshellDrawerComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun drawerRowsDriveStateAndCustomFeedsLoadOnceForTheSignedInAccount() {
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
        )
        assertTrue(state.muteCommunity("homelab", expiresAtEpochMillis = null))
        val customFeedLoads = mutableListOf<Pair<String, Boolean>>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        ensureWorkManagerInitialized(context)

        composeRule.setContent {
            SubshellApp(
                videoPlaybackCoordinator = VideoPlaybackCoordinator(context),
                state = state,
                customFeeds = CustomFeedsEntryPoints(onLoadCustomFeeds = { accountId, force -> customFeedLoads += accountId to force }),
            )
        }
        composeRule.waitForIdle()
        assertEquals(listOf("reddit:test" to false), customFeedLoads)

        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Feeds").assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()

        composeRule.onNodeWithText("Inbox").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.INBOX, state.destination)

        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.waitForIdle()
        assertEquals(MainDestination.YOU, state.destination)

        state.requestDrawer()
        composeRule.waitForIdle()
        assertFalse(state.showNsfwPosts)
        composeRule.onNodeWithText("Show NSFW posts").performClick()
        composeRule.waitForIdle()
        assertTrue(state.showNsfwPosts)

        assertNull(state.secondaryDestination)
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        assertEquals(SecondaryDestination.SETTINGS, state.secondaryDestination)

        state.closeSecondary()
        state.requestDrawer()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Muted communities").assertIsDisplayed()
        composeRule.onNodeWithText("Muted communities").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("r/homelab").assertIsDisplayed()

        // One load per account, not one per drawer opening.
        assertEquals(listOf("reddit:test" to false), customFeedLoads)
    }
}
