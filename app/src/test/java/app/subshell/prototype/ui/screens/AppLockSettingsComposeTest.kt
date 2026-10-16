package app.subshell.prototype.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.subshell.prototype.applock.AppLockEntryPoints
import app.subshell.prototype.applock.AppLockSettings
import app.subshell.prototype.applock.LocalAppLock
import app.subshell.prototype.backup.SettingsBackupRepository
import app.subshell.prototype.harness.ensureWorkManagerInitialized
import app.subshell.prototype.ui.SubshellState
import app.subshell.prototype.ui.theme.SubshellTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Issue #38: the app lock rows in Settings > Feed & content > Privacy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AppLockSettingsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private var settings by mutableStateOf(AppLockSettings())
    private val unlockAnswers = ArrayDeque<Boolean>()
    private var unlockRequests = 0
    private val turnOffUnlockTitles = mutableListOf<String>()
    private val turnOffAnswers = ArrayDeque<Boolean>()
    private var state = SubshellState()

    private fun render(deviceUnlockAvailable: Boolean = true) {
        ensureWorkManagerInitialized(context)
        val repository = SettingsBackupRepository(context, "test")
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppLock provides AppLockEntryPoints(
                    settings = settings,
                    deviceUnlockAvailable = deviceUnlockAvailable,
                    onChange = { settings = it },
                    onRequestUnlock = { onResult ->
                        unlockRequests += 1
                        onResult(unlockAnswers.removeFirst())
                    },
                    onRequestUnlockFor = { title, _, onResult ->
                        turnOffUnlockTitles += title
                        onResult(turnOffAnswers.removeFirst())
                    },
                ),
            ) {
                SubshellTheme {
                    SettingsScreen(
                        state = state,
                        backupRepository = repository,
                        onBack = {},
                        initialDestinationName = SettingsDestination.FEEDS.name,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private val lockSwitch = isToggleable() and hasText("Lock subshell", substring = true)
    private val hiddenSwitch =
        isToggleable() and hasText("Require device unlock to view hidden communities", substring = true)

    @Test
    fun turningTheLockOnAsksForTheUnlockFirst() {
        render()
        scrollTo("Lock subshell")

        unlockAnswers += false
        composeRule.onNode(lockSwitch).performClick()
        composeRule.waitForIdle()
        assertEquals(AppLockSettings(enabled = false), settings)
        composeRule.onNode(lockSwitch).assertIsOff()

        unlockAnswers += true
        composeRule.onNode(lockSwitch).performClick()
        composeRule.waitForIdle()
        assertEquals(2, unlockRequests)
        assertEquals(AppLockSettings(enabled = true), settings)
        composeRule.onNode(lockSwitch).assertIsOn()
    }

    // ISSUE115_TURNING_PROTECTION_OFF_NEEDS_UNLOCK_V1 (it was turned off with no
    // unlock: someone handed the unlocked phone could switch it off).
    @Test
    fun turningTheLockOffAsksForTheUnlockAndAStopKeepsItOn() {
        settings = AppLockSettings(enabled = true)
        turnOffAnswers += false
        turnOffAnswers += true
        render()
        scrollTo("Lock subshell")

        composeRule.onNode(lockSwitch).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("Turn off app lock"), turnOffUnlockTitles)
        assertEquals(AppLockSettings(enabled = true), settings)

        composeRule.onNode(lockSwitch).performClick()
        composeRule.waitForIdle()
        assertEquals(AppLockSettings(enabled = false), settings)
        assertEquals(0, unlockRequests)
    }

    @Test
    fun hiddenCommunityProtectionNeedsTheUnlockToTurnOffButNotOn() {
        state = SubshellState(initialRequireDeviceUnlockForHiddenSubscriptions = true)
        turnOffAnswers += false
        turnOffAnswers += true
        render()
        scrollTo("Require device unlock to view hidden communities")
        val toggle = hiddenSwitch

        composeRule.onNode(toggle).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("Show hidden communities freely"), turnOffUnlockTitles)
        assertTrue(state.requireDeviceUnlockForHiddenSubscriptions)

        composeRule.onNode(toggle).performClick()
        composeRule.waitForIdle()
        assertFalse(state.requireDeviceUnlockForHiddenSubscriptions)

        composeRule.onNode(toggle).performClick()
        composeRule.waitForIdle()
        assertTrue(state.requireDeviceUnlockForHiddenSubscriptions)
        assertEquals(2, turnOffUnlockTitles.size)
    }

    @Test
    fun withoutAScreenLockAProtectionTurnsOffDirectly() {
        state = SubshellState(initialRequireDeviceUnlockForHiddenSubscriptions = true)
        render(deviceUnlockAvailable = false)
        scrollTo("Require device unlock to view hidden communities")

        composeRule.onNode(hiddenSwitch).performClick()
        composeRule.waitForIdle()

        assertFalse(state.requireDeviceUnlockForHiddenSubscriptions)
        assertEquals(emptyList<String>(), turnOffUnlockTitles)
    }

    @Test
    fun withoutAScreenLockTheLockCannotBeTurnedOn() {
        render(deviceUnlockAvailable = false)
        scrollTo("Lock subshell")

        // The hidden-communities row says the same since OCB-273; this is the
        // lock row's own line.
        composeRule.onNode(lockSwitch and hasText("Set a screen lock on this phone first", substring = true))
            .assertExists()
        composeRule.onNode(lockSwitch).assertIsNotEnabled()
    }

    @Test
    fun lockAfterShowsTheDelayAndAppliesAPreset() {
        settings = AppLockSettings(enabled = true, delayMinutes = 1)
        render()
        scrollTo("Lock after")

        composeRule.onNodeWithText("1 minute").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Immediately").assertExists()
        composeRule.onNodeWithText("Custom").assertExists()
        composeRule.onNodeWithText("15 minutes").performClick()
        composeRule.waitForIdle()

        assertEquals(AppLockSettings(enabled = true, delayMinutes = 15), settings)
        composeRule.onNodeWithText("15 minutes").assertExists()
    }

    @Test
    fun lockAfterAndNotificationHidingAreHiddenWhileTheLockIsOff() {
        render()
        scrollTo("Lock subshell")

        composeRule.onNodeWithText("Lock after").assertDoesNotExist()
        composeRule.onNodeWithText("Hide notification content").assertDoesNotExist()
    }

    @Test
    fun notificationHidingIsOffByDefaultAndCanBeTurnedOn() {
        settings = AppLockSettings(enabled = true)
        render()
        scrollTo("Hide notification content")
        val hideSwitch = isToggleable() and hasText("Hide notification content", substring = true)

        composeRule.onNode(hideSwitch).assertIsOff()
        composeRule.onNode(hideSwitch).performClick()
        composeRule.waitForIdle()

        assertEquals(AppLockSettings(enabled = true, hideNotificationContent = true), settings)
        assertEquals(0, unlockRequests)
    }
}
