package app.subshell.prototype.update

import android.Manifest
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * UPDATE08_UPDATE_DIALOG_ASKS_FOR_NOTIFICATIONS_V1 (owner): the update dialog
 * offers "Notify me next time" only while Subshell may not post notifications;
 * it asks Android while Android still asks, and opens Subshell's notification
 * settings once it does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Update08NotificationOptInTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val notify = "Notify me next time"

    private val release = UpdateManifestRelease(
        schemaVersion = 1,
        channel = "beta",
        versionCode = BuildConfig.VERSION_CODE + 1,
        versionName = "9.99-beta",
        publishedAt = "2026-10-15T00:00:00Z",
        releaseUrl = "https://github.com/brealorg/subshell/releases/tag/v9.99-beta",
        apkSha256 = "a".repeat(64),
        headline = "A newer beta",
    )

    @After
    fun clearStore() {
        composeRule.activity.getSharedPreferences(UpdateStateStore.PREFERENCES_NAME, 0).edit().clear().commit()
    }

    @Test
    fun theOfferFollowsWhatAndroidAllows() {
        fun optIn(sdk: Int = 36, granted: Boolean = false, enabled: Boolean = true, asked: Boolean = false, rationale: Boolean = false) =
            updateNotificationOptIn(sdk, granted, enabled, asked, rationale)

        assertEquals(UpdateNotificationOptIn.NONE, optIn(granted = true))
        assertEquals(UpdateNotificationOptIn.NONE, optIn(sdk = 32))
        assertEquals(UpdateNotificationOptIn.ASK, optIn())
        assertEquals(UpdateNotificationOptIn.ASK, optIn(asked = true, rationale = true))
        assertEquals(UpdateNotificationOptIn.OPEN_SETTINGS, optIn(asked = true))
        assertEquals(UpdateNotificationOptIn.OPEN_SETTINGS, optIn(granted = true, enabled = false))
        assertEquals(UpdateNotificationOptIn.OPEN_SETTINGS, optIn(sdk = 32, enabled = false))
    }

    @Test
    fun theDialogShowsTheButtonOnlyWhenOffered() {
        var tapped = 0
        composeRule.setContent {
            SubshellTheme {
                UpdateAvailableDialog(release, onDismiss = {}, onViewRelease = {}, onNotifyNextTime = { tapped++ })
            }
        }
        composeRule.onNodeWithText(notify).performScrollTo().performClick()
        assertEquals(1, tapped)
    }

    @Test
    fun withoutThePermissionTheDialogAsksAndroid() {
        shadowOf(composeRule.activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showHost()

        composeRule.onNodeWithText(notify).performScrollTo().performClick()

        val asked = shadowOf(composeRule.activity).lastRequestedPermission
        assertTrue(asked?.requestedPermissions?.contains(Manifest.permission.POST_NOTIFICATIONS) == true)
    }

    @Test
    fun afterAFinalRefusalTheButtonOpensSubshellsNotificationSettings() {
        shadowOf(composeRule.activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        UpdateStateStore(composeRule.activity).markNotificationPermissionAsked()
        showHost()

        composeRule.onNodeWithText(notify).performScrollTo().performClick()

        val opened = shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened?.action)
        assertEquals(composeRule.activity.packageName, opened?.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun withThePermissionTheDialogHasNoButton() {
        shadowOf(composeRule.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        showHost()

        composeRule.onNodeWithText("subshell update available").assertExists()
        composeRule.onAllNodesWithText(notify).assertCountEquals(0)
    }

    private fun showHost() {
        UpdateStateStore(composeRule.activity).saveAvailableRelease(release)
        composeRule.setContent {
            SubshellTheme { UpdatePresentationHost(automaticPresentationEnabled = true) }
        }
        composeRule.waitForIdle()
    }
}
