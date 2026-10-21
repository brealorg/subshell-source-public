package app.subshell.prototype.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.RetryOnRobolectricRendererFlake
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.harness.testVideoPlaybackCoordinator
import app.subshell.prototype.media.LocalVideoPlaybackCoordinator
import app.subshell.prototype.ui.error.LocalViewportErrorHostState
import app.subshell.prototype.ui.error.ViewportErrorHostState
import app.subshell.prototype.ui.screens.CommunityScreen
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ISSUE239_NOTIFICATIONS_ARE_ASKED_FOR_OR_OPENED_V1 (owner, phone check):
 * turning on new-post notifications when Android will not ask (a refusal
 * before) or notifications are off for subshell opens the system's
 * notification settings and turns the community on on return once they are
 * allowed; a "Don't allow" the reader just chose in Android's question is
 * only said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Issue239NotificationPermissionComposeTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RetryOnRobolectricRendererFlake()).around(composeRule)

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val notices = ViewportErrorHostState()
    private val alice = AccountSessionSummary(
        stableId = "reddit:alice",
        username = "alice",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )
    private val state = SubshellState(initialAccountSessions = listOf(alice), initialActiveAccountId = alice.stableId)

    /** Android's answer to the permission question; [dialogShown] pauses the activity first. */
    private inner class AnsweringRegistry(private val granted: Boolean, private val dialogShown: Boolean) :
        ActivityResultRegistry() {
        var asked = 0
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            asked += 1
            if (dialogShown) {
                composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
                composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            }
            dispatchResult(requestCode, granted)
        }
    }

    private fun show(registry: ActivityResultRegistry) {
        ensureWorkManagerInitialized(application)
        val coordinator = testVideoPlaybackCoordinator(application)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        composeRule.setContent {
            SubshellTheme {
                CompositionLocalProvider(
                    LocalVideoPlaybackCoordinator provides coordinator,
                    LocalViewportErrorHostState provides notices,
                    LocalActivityResultRegistryOwner provides owner,
                ) {
                    CommunityScreen(
                        communityName = "pics",
                        state = state,
                        onRefreshRedditFeed = { _, _, _, _ -> },
                        onAppendRedditFeed = { _, _, _, _ -> },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun turnOn() {
        composeRule.onAllNodes(hasContentDescription("Actions for r/pics") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("Notify about new posts") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
    }

    private fun returnToTheApp() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    private fun openedSettings(): Boolean =
        shadowOf(application).nextStartedActivity?.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS ||
            shadowOf(composeRule.activity).nextStartedActivity?.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS

    @Test
    fun whenAndroidAnswersWithoutAskingTheSettingsOpenAndTheCommunityTurnsOnOnReturn() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val registry = AnsweringRegistry(granted = false, dialogShown = false)
        show(registry)

        turnOn()

        assertEquals(1, registry.asked)
        assertTrue("the notification settings opened", openedSettings())
        assertFalse(state.isWatchingCommunityPosts("pics"))

        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        returnToTheApp()
        assertTrue(state.isWatchingCommunityPosts("pics"))
        assertEquals("Notifications on for r/pics", notices.currentRequest?.presentation?.title)
    }

    @Test
    fun aDontAllowJustChosenIsOnlySaid() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(AnsweringRegistry(granted = false, dialogShown = true))

        turnOn()

        assertFalse("no settings opened", openedSettings())
        assertFalse(state.isWatchingCommunityPosts("pics"))
        assertEquals("Notifications are not allowed", notices.currentRequest?.presentation?.title)
    }

    @Test
    fun notificationsOffForSubshellOpenTheSettingsWithoutAsking() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = application.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(false)
        val registry = AnsweringRegistry(granted = true, dialogShown = true)
        show(registry)

        turnOn()

        assertEquals(0, registry.asked)
        assertTrue(openedSettings())
        returnToTheApp()
        assertFalse("still off: not turned on", state.isWatchingCommunityPosts("pics"))
        assertEquals("Notifications are not allowed", notices.currentRequest?.presentation?.title)

        shadowOf(manager).setNotificationsEnabled(true)
        turnOn()
        assertTrue(state.isWatchingCommunityPosts("pics"))
    }
}
