package app.subshell.prototype.ui.app

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.BuildConfig
import app.subshell.prototype.data.account.AccountSessionSource
import app.subshell.prototype.data.account.AccountSessionState
import app.subshell.prototype.data.account.AccountSessionSummary
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.inbox.inboxNotificationsEnabled
import app.subshell.prototype.ui.INBOX_NOTIFICATIONS_ON_BY_DEFAULT
import app.subshell.prototype.ui.SubshellSettings
import app.subshell.prototype.ui.SubshellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * NOTIFY02_ON_BY_DEFAULT_IN_THE_MAIN_APP_V1: inbox notifications are on unless
 * the reader turned them off — in the main app only (lane builds keep them
 * off); Android 13+ asks for the permission once and a refusal turns them off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class Notify02OnByDefaultComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val account = AccountSessionSummary(
        stableId = "reddit:me",
        username = "me_user",
        source = AccountSessionSource.AUTHENTICATED,
        state = AccountSessionState.CONNECTED,
        providerLabel = "Reddit",
    )

    @Test
    fun theMainAppHasThemOnAndLaneBuildsOff() {
        assertEquals(BuildConfig.DEBUG_SLOT.isBlank(), INBOX_NOTIFICATIONS_ON_BY_DEFAULT)
        assertEquals(INBOX_NOTIFICATIONS_ON_BY_DEFAULT, SubshellSettings().inboxNotificationsEnabled)
    }

    @Test
    fun anUntouchedSettingFollowsTheDefaultAndAChoiceIsKept() {
        val preferences = application.getSharedPreferences("subshell_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        assertEquals(INBOX_NOTIFICATIONS_ON_BY_DEFAULT, inboxNotificationsEnabled(application))

        preferences.edit().putBoolean(SubshellSettings.INBOX_NOTIFICATIONS_ENABLED, false).commit()
        assertFalse(inboxNotificationsEnabled(application))
    }

    private fun host(state: SubshellState, onRequest: ((Boolean) -> Unit) -> Unit) {
        ensureWorkManagerInitialized(application)
        composeRule.setContent {
            InboxNotificationPermissionEffect(state = state, onRequestPermission = onRequest)
        }
        composeRule.waitForIdle()
    }

    private fun signedIn() = SubshellState(
        initialAccountSessions = listOf(account),
        initialActiveAccountId = account.stableId,
        initialInteractionSideConfigured = true,
        initialInboxNotificationsEnabled = true,
    )

    @Test
    fun aMissingPermissionIsAskedForOnceAndARefusalTurnsThemOff() {
        val state = signedIn()
        var asks = 0
        host(state) { answer ->
            asks += 1
            answer(false)
        }

        assertEquals(1, asks)
        assertFalse(state.inboxNotificationsEnabled)
    }

    @Test
    fun aGrantedPermissionIsNotAskedAgain() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val state = signedIn()
        var asks = 0
        host(state) { asks += 1 }

        assertEquals(0, asks)
        assertTrue(state.inboxNotificationsEnabled)
    }
}
